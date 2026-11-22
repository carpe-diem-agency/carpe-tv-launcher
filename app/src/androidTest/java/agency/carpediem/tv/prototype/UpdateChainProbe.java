package agency.carpediem.tv.prototype;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Actual updater check/HEAD/DownloadManager/production APK verifier. Never installs the candidate. */
final class UpdateChainProbe {
    static void run(Instrumentation test,Bundle args,Bundle result)throws Exception {
        Context c=test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(c.getPackageName())&&UpdateManager.debug(c),"Disposable debug validation package required");
        String expected=args.getString("candidateSha256");long expectedVersion=Long.parseLong(args.getString("candidateVersionCode","-1"));
        require(expected!=null&&expected.matches("[a-f0-9]{64}"),"Explicit candidate SHA256 required");
        File candidate=new File(c.getFilesDir(),"update-feed-fixture/newer.apk");
        require(candidate.isFile()&&candidate.length()>=1024&&candidate.length()<=UpdateManager.MAX_BYTES,"Bounded staged candidate required");
        require(expected.equals(hash(candidate)),"Staged APK SHA256 mismatch");
        PackageInfo archive=c.getPackageManager().getPackageArchiveInfo(candidate.getPath(),0);
        require(archive!=null&&c.getPackageName().equals(archive.packageName)&&UpdateManager.version(archive)==expectedVersion
                &&expectedVersion>UpdateManager.version(UpdateManager.installed(c)),"Higher-version actual validation APK required");
        UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        SharedPreferences prefs=UpdateManager.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());
        require(!UpdateManager.busy(c)&&prefs.getLong("downloadId",-1)<0&&!prefs.getBoolean("enqueuePending",false)&&!UpdateManager.target(c).exists(),"Fresh updater ownership/artifact required");
        DownloadManager dm=c.getSystemService(DownloadManager.class);Set<Long> owned=new HashSet<>();Set<String> files=new HashSet<>();
        Feed server=new Feed(c,candidate,archive,expected);Throwable failure=null;
        try {
            require(prefs.edit().putBoolean("autoDownload",false).putString("state","idle").commit(),"Prepare manual chain");
            String wrongSignerSha = args.getString("wrongSignerSha256");
            if (wrongSignerSha != null) verifyWrongSigner(test,c,archive,wrongSignerSha,prefs,dm,owned,files,result);
            ArrayList<String> states=new ArrayList<>();
            for(boolean wrong:new boolean[]{false,true}) {
                server.wrong=wrong;
                UpdateManager.check(c,server.url("/feed"));states.add(UpdateManager.state(c));
                awaitState(c,"available",owned,files,30000);
                require(UpdateManager.metadata(c).getLong("versionCode")==expectedVersion,"Actual feed metadata not persisted");
                UpdateManager.download(c);states.add(UpdateManager.state(c));
                long deadline=SystemClock.elapsedRealtime()+90000;
                while(SystemClock.elapsedRealtime()<deadline) {
                    capture(prefs,owned,files);String state=UpdateManager.state(c);
                    if(states.isEmpty()||!states.get(states.size()-1).equals(state))states.add(state);
                    if(state.equals("downloading"))UpdateManager.refreshNow(c);
                    state=UpdateManager.state(c);
                    if(state.equals(wrong?"error":"ready"))break;
                    require(!state.equals("error")||wrong,"Actual updater failed before ready");
                    server.healthy();Thread.sleep(30);
                }
                capture(prefs,owned,files);
                require(UpdateManager.state(c).equals(wrong?"error":"ready"),"Updater chain did not reach expected terminal state");
                if(wrong) {
                    require("update_checksum".equals(prefs.getString("messageKey",null)),"Wrong SHA did not fail production checksum gate");
                    require(!UpdateManager.target(c).exists(),"Rejected APK target survived cleanup");
                    inspectUi(test,c,server.url("/feed"),true,result);
                    result.putString("updatechain_wrong_sha","PASS: actual second download rejected by production SHA256 verifier, no installation");
                } else {
                    require(UpdateManager.target(c).isFile()&&expected.equals(hash(UpdateManager.target(c))),"Ready target bytes differ");
                    inspectUi(test,c,server.url("/feed"),false,result);
                    result.putString("updatechain","PASS: actual check, metadata, HEAD, DownloadManager GET and production package/version/signer/hash verifier reached ready; no installation");
                    UpdateManager.cancel(c);
                }
            }
            require(server.feeds.get()>=2&&server.heads.get()>=2&&server.gets.get()>=2,"Real feed/HEAD/GET evidence missing");
            verifyUnavailableFeed(c,server,prefs,owned,files,expectedVersion,result);
            verifyFeedTimeoutRetry(test, c, server, prefs, owned, files, expectedVersion, result);
            verifyGetFailureRetry(test,c,server,prefs,dm,owned,files,expected,result);
            result.putString("updatechain_observed_states",states.toString());
            result.putInt("updatechain_feed_requests",server.feeds.get());result.putInt("updatechain_head_requests",server.heads.get());result.putInt("updatechain_get_requests",server.gets.get());
        }catch(Throwable error){failure=error;}
        finally {
            try {
                UpdateManager.WORKER.submit(()->{}).get(20,TimeUnit.SECONDS);capture(prefs,owned,files);
                server.close();
                for(long id:owned){dm.remove(id);try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&!row.moveToFirst(),"Owned chain row cleanup unverified");}}
                File external=c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                for(String name:files){require(name.matches("carpe-update-[a-f0-9-]{36}\\.apk"),"Refuse non-owned filename");File file=new File(external,name);require(!file.exists()||file.delete(),"Owned chain APK cleanup failed");}
                SettingsStructureProbe.restore(prefs,saved);
                result.putBoolean("updatechain_cleanup_verified",true);
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        }
        if(failure instanceof Exception)throw(Exception)failure;if(failure instanceof Error)throw(Error)failure;
    }

    private static void verifyWrongSigner(Instrumentation test, Context context, PackageInfo expected,
            String sha256, SharedPreferences prefs, DownloadManager manager,
            Set<Long> owned, Set<String> files, Bundle result) throws Exception {
        File candidate = new File(context.getFilesDir(), "update-feed-fixture/wrong-signer.apk");
        require(sha256.matches("[a-f0-9]{64}") && candidate.isFile() && candidate.length() >= 1024
                && candidate.length() <= UpdateManager.MAX_BYTES && sha256.equals(hash(candidate)),
                "Sealed wrong-signer APK was not staged");
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(candidate.getPath(), 0);
        require(info != null && context.getPackageName().equals(info.packageName)
                && UpdateManager.version(info) == UpdateManager.version(expected) && expected.versionName.equals(info.versionName),
                "Wrong-signer APK does not match the candidate identity");
        try (Feed foreign = new Feed(context, candidate, info, sha256)) {
            UpdateManager.check(context, foreign.url("/feed"));
            awaitState(context, "available", owned, files, 30000);
            UpdateManager.download(context);
            awaitDownloadTerminal(context, foreign, "error", owned, files, 45000);
            require(foreign.heads.get() > 0 && foreign.gets.get() > 0, "Wrong-signer APK was not actually downloaded");
            require("update_wrong_key".equals(prefs.getString("messageKey", null)),
                    "Wrong-signer download did not fail the production certificate gate");
            require(owned.size() == 1 && files.size() == 1, "Wrong-signer DownloadManager ID and APK were not observed");
            String filename = files.iterator().next();
            require(filename.matches("carpe-update-[a-f0-9-]{36}\\.apk"), "Wrong-signer download is missing its owned filename");
            File output = new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), filename);
            require(prefs.getLong("downloadId", -1) < 0 && !output.exists(), "Wrong-signer download left an active ID or APK");
            try (Cursor row = manager.query(new DownloadManager.Query().setFilterById(owned.iterator().next()))) {
                require(row != null && !row.moveToFirst(), "Wrong-signer DownloadManager row survived production cleanup");
            }
            inspectUi(test, context, foreign.url("/feed"), false, result, screen ->
                    SettingsStructureProbe.await(test, () -> {
                        View check = screen.findViewById(R.id.update_check);
                        View install = screen.findViewById(R.id.update_install);
                        TextView status = screen.findViewById(R.id.update_status);
                        return check.isShown() && check.isEnabled() && screen.getCurrentFocus() == check
                                && install.getVisibility() == View.GONE
                                && screen.getString(R.string.update_wrong_key).contentEquals(status.getText());
                    }, "Wrong-signer message, Check focus or hidden Install missing"));
        }
        result.putBoolean("updatechain_wrong_signer_cleanup_verified", true);
        result.putString("updatechain_wrong_signer", "PASS: valid package, version and SHA downloaded with a different certificate; signature error focused Check, hid Install and removed the exact file and DownloadManager row");
    }

    private static void verifyGetFailureRetry(Instrumentation test,Context c,Feed server,SharedPreferences prefs,DownloadManager dm,
                                              Set<Long> owned,Set<String> files,String expected,Bundle result)throws Exception {
        server.wrong=false;server.failGet=true;int heads=server.heads.get();
        try {
            UpdateManager.download(c);
            require(server.failedGetEntered.await(20,TimeUnit.SECONDS),"Actual APK GET did not reach failure gate");
            capture(prefs,owned,files);long id=prefs.getLong("downloadId",-1);File failedTarget=UpdateManager.target(c);
            require(id>=0&&server.heads.get()>heads,"Successful HEAD and published owned download missing before GET404");
            try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&row.moveToFirst(),"Owned real DM row missing before GET404");}
            server.releaseGet404.countDown();
            awaitDownloadTerminal(c,server,"error",owned,files,30000);
            require("update_download_failed".equals(prefs.getString("messageKey",null)),"GET404 did not report production download failure");
            try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&!row.moveToFirst(),"Failed APK GET row survived production cleanup");}
            require(prefs.getLong("downloadId",-1)<0&&!failedTarget.exists(),"Failed APK GET left id or file");
            server.failGet=false;
            inspectUi(test,c,server.url("/feed"),false,result,screen->{
                SettingsStructureProbe.await(test,()->"error".equals(UpdateManager.state(c))
                        &&screen.getCurrentFocus()==screen.findViewById(R.id.update_check)
                        &&screen.findViewById(R.id.update_check).isShown()
                        &&((android.widget.TextView)screen.findViewById(R.id.update_status)).getText().toString().equals(screen.getString(R.string.update_download_failed)),
                        "GET404 error status/Check focus not visible");
                // These are the only retry activations: real remote keys on verified Check and Download.
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();
                SettingsStructureProbe.await(test,()->"available".equals(UpdateManager.state(c))
                        &&screen.findViewById(R.id.update_download).isShown(),"Check OK did not expose available Download");
                boolean[] checkFocused={false};
                SettingsStructureProbe.main(test,()->checkFocused[0]=screen.getCurrentFocus()==screen.findViewById(R.id.update_check));
                if(checkFocused[0]){test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();}
                SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.update_download)
                        &&screen.findViewById(R.id.update_download).isEnabled(),"D-pad did not reach Download; refuse OK on another control"));
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();
                long deadline=SystemClock.elapsedRealtime()+90000;
                while(SystemClock.elapsedRealtime()<deadline&&!"ready".equals(UpdateManager.state(c))) {
                    capture(prefs,owned,files);require(!"error".equals(UpdateManager.state(c)),"D-pad retry failed before Ready");
                    server.healthy();Thread.sleep(30); // Production Updates tick/receiver handles verification.
                }
                require("ready".equals(UpdateManager.state(c)),"D-pad download did not reach Ready");capture(prefs,owned,files);
                require(expected.equals(hash(UpdateManager.target(c))),"D-pad GET retry Ready bytes differ from sealed candidate");
                SettingsStructureProbe.await(test,()->screen.findViewById(R.id.update_install).isShown()
                        &&screen.findViewById(R.id.update_install).isEnabled()
                        &&screen.getCurrentFocus()==screen.findViewById(R.id.update_install)
                        &&!screen.findViewById(R.id.update_download).isShown(),"Ready CTA did not render/focus after D-pad retry");
            });
            result.putString("updatechain_get404_retry","PASS: HEAD200/GET404 production failure and cleanup, real Check/Download D-pad retry verified Ready with identical bytes; no installer/settings");
            result.putString("updatechain_get404_dpad","PASS: visible GET404 error and Check focus, OK to available, optional Right to Download, OK to production Ready, sealed SHA and Back to Main; no direct retry Check/Download calls");
            UpdateManager.cancel(c);
        } finally {server.releaseGet404.countDown();server.failGet=false;}
    }
    private static void awaitDownloadTerminal(Context c,Feed server,String wanted,Set<Long> owned,Set<String> files,long timeout)throws Exception {
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline) {
            capture(UpdateManager.prefs(c),owned,files);String state=UpdateManager.state(c);
            if(state.equals("downloading"))UpdateManager.refreshNow(c);
            state=UpdateManager.state(c);if(wanted.equals(state))return;
            require(!"error".equals(state),"Unexpected production download error before "+wanted);
            server.healthy();Thread.sleep(30);
        }
        throw new AssertionError("Download terminal timeout: "+wanted);
    }
    private static void verifyUnavailableFeed(Context c,Feed server,SharedPreferences prefs,Set<Long> owned,
                                              Set<String> files,long expectedVersion,Bundle result)throws Exception {
        int downloads=server.gets.get(),checks=server.feeds.get();
        for(int status:new int[]{404,503}) {
            server.feedStatus=status;
            UpdateManager.check(c,server.url("/feed"));
            awaitState(c,"error",owned,files,10000);
            UpdateManager.WORKER.submit(()->{}).get(10,TimeUnit.SECONDS);
            String message=status==404?"update_unpublished":"update_channel_unavailable_prefix";
            require(server.feeds.get()==++checks&&message.equals(prefs.getString("messageKey",null)),"Incorrect response to real HTTP "+status);
            if(status==503)require("503)".equals(prefs.getString("messageSuffix",null)),"Unavailable-feed status not reported");
            require(prefs.getLong("downloadId",-1)<0&&!UpdateManager.target(c).exists(),"Feed error left an installable artifact");
        }
        server.feedStatus=200;server.wrong=false;
        UpdateManager.check(c,server.url("/feed"));
        awaitState(c,"available",owned,files,10000);
        require(server.feeds.get()==checks+1&&server.gets.get()==downloads
                &&UpdateManager.metadata(c).getLong("versionCode")==expectedVersion,"Retry did not recover current metadata without downloading");
        result.putString("updatechain_unavailable","PASS: real HTTP 404/503 reported, no new APK, manual retry restored available");
    }
    private interface UiRetry {void run(UpdatesActivity screen)throws Exception;}

    private static void verifyFeedTimeoutRetry(Instrumentation test, Context context, Feed server,
                                               SharedPreferences prefs, Set<Long> owned, Set<String> files,
                                               long expectedVersion, Bundle result) throws Exception {
        UpdateManager.WORKER.submit(() -> {}).get(5, TimeUnit.SECONDS);
        int checks = server.feeds.get(), downloads = server.gets.get();
        server.stallFeed = true;
        try {
            UpdateManager.check(context, server.url("/feed"));
            require(server.stalledFeedEntered.await(8, TimeUnit.SECONDS), "Feed request did not reach stall gate");
            require("checking".equals(UpdateManager.state(context)), "Stalled request did not remain checking");
            awaitState(context, "error", owned, files, 26000);
            long waited = SystemClock.elapsedRealtime() - server.stalledFeedAt;
            require(waited >= 13000 && "update_timeout".equals(prefs.getString("messageKey", null)),
                    "Stalled HTTP response did not reach the production timeout");
            result.putLong("updatechain_feed_timeout_ms", waited);
            require(server.feeds.get() == checks + 1 && server.gets.get() == downloads
                    && prefs.getLong("downloadId", -1) < 0 && !UpdateManager.target(context).exists(),
                    "Timed-out check left download or retry activity");
            UpdateManager.WORKER.submit(() -> {}).get(5, TimeUnit.SECONDS);
        } finally {
            server.stallFeed = false;
            server.releaseStalledFeed.countDown();
        }
        inspectUi(test, context, server.url("/feed"), false, result, screen -> {
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.update_check)
                    && screen.findViewById(R.id.update_check).isEnabled()
                    && screen.getString(R.string.update_timeout).contentEquals(
                    ((android.widget.TextView) screen.findViewById(R.id.update_status)).getText()),
                    "Timeout message or Check focus unavailable");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            test.waitForIdleSync();
            SettingsStructureProbe.await(test, () -> "available".equals(UpdateManager.state(context))
                    && screen.findViewById(R.id.update_download).isShown(), "D-pad Check did not recover from timeout");
        });
        require(server.feeds.get() == checks + 2 && server.gets.get() == downloads
                && UpdateManager.metadata(context).getLong("versionCode") == expectedVersion,
                "Timeout retry did not recover current metadata");
        result.putString("updatechain_feed_timeout", "PASS: actual silent HTTP server, timeout message and Check focus, D-pad retry restored available without download");
    }

    private static void inspectUi(Instrumentation test,Context context,String feed,boolean rejected,Bundle result)throws Exception {
        inspectUi(test,context,feed,rejected,result,null);
    }
    private static void inspectUi(Instrumentation test,Context context,String feed,boolean rejected,Bundle result,UiRetry retry)throws Exception {
        android.app.Application application=(android.app.Application)context.getApplicationContext();
        java.util.concurrent.atomic.AtomicReference<MainActivity> main=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<UpdatesActivity> updates=new java.util.concurrent.atomic.AtomicReference<>();
        SharedPreferences activityPrefs=context.getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(activityPrefs.getAll());Throwable primary=null;
        android.app.Application.ActivityLifecycleCallbacks lifecycle=new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity activity,Bundle state){if(activity instanceof MainActivity)main.set((MainActivity)activity);if(activity instanceof UpdatesActivity)updates.set((UpdatesActivity)activity);}
            @Override public void onActivityStarted(android.app.Activity activity){}
            @Override public void onActivityResumed(android.app.Activity activity){}
            @Override public void onActivityPaused(android.app.Activity activity){}
            @Override public void onActivityStopped(android.app.Activity activity){}
            @Override public void onActivitySaveInstanceState(android.app.Activity activity,Bundle state){}
            @Override public void onActivityDestroyed(android.app.Activity activity){}
        };
        application.registerActivityLifecycleCallbacks(lifecycle);
        try {
            require(activityPrefs.edit().putBoolean("welcome_seen",true).remove("last_opened_package").commit(),"Prepare owned Main UI");
            test.setInTouchMode(false);
            SettingsStructureProbe.main(test,()->context.startActivity(new android.content.Intent(context,MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("internal_update_feed",feed)));
            SettingsStructureProbe.await(test,()->updates.get()!=null&&updates.get().hasWindowFocus(),"Debug route did not foreground owned UpdatesActivity");
            UpdatesActivity screen=updates.get();boolean[] permitted={false};
            if(retry!=null)retry.run(screen);
            else SettingsStructureProbe.main(test,()->{
                permitted[0]=screen.getPackageManager().canRequestPackageInstalls();
                android.view.View install=screen.findViewById(R.id.update_install),check=screen.findViewById(R.id.update_check);
                android.view.View download=screen.findViewById(R.id.update_download),cancel=screen.findViewById(R.id.update_cancel);
                require(download.getVisibility()==android.view.View.GONE&&cancel.getVisibility()==android.view.View.GONE,"Unexpected download/cancel CTA");
                require(screen.findViewById(R.id.update_progress).getVisibility()==android.view.View.GONE,"Terminal UI retained progress");
                String help=screen.getString(!rejected&&!permitted[0]?R.string.permission_help:R.string.update_help);
                require(hasText(screen.getWindow().getDecorView(),help),"Permission-dependent help mismatch");
                if(rejected) {
                    require(install.getVisibility()==android.view.View.GONE&&check.isShown()&&check.isEnabled(),"Checksum rejection exposed install or lost Check");
                    require(screen.getCurrentFocus()==check,"Checksum error initial focus not on Check");
                    require(((android.widget.TextView)screen.findViewById(R.id.update_status)).getText().toString().equals(screen.getString(R.string.update_checksum)),"Checksum rejection status missing");
                } else {
                    require(install.isShown()&&install.isEnabled()&&!check.isShown(),"Ready must show only the install action");
                    require(((android.widget.TextView)install).getText().toString().equals(screen.getString(permitted[0]?R.string.install:R.string.allow_install)),"Ready permission CTA mismatch");
                    require(screen.getCurrentFocus()==install,"Ready initial focus not on install/allow CTA: "
                            + (screen.getCurrentFocus() == null ? "none" : screen.getCurrentFocus().getId()));
                }
            });
            if (retry == null && !rejected) {
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN);
                SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.update_automatic),
                        "Down from Install/Allow did not reach automatic download");
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
                SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.update_install),
                        "One Up press did not return from automatic download to Install/Allow");
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_UP);
                SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.update_back),
                        "One Up press did not move focus from Install/Allow to Back");
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_CENTER);
            } else {
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            }
            test.waitForIdleSync();
            SettingsStructureProbe.await(test,()->screen.isDestroyed()&&main.get()!=null&&main.get().hasWindowFocus(),"Updates Back did not restore owned Main");
            if(retry==null) {
                result.putBoolean("updatechain_ui_install_permission",permitted[0]);
                result.putString(rejected?"updatechain_ui_checksum":"updatechain_ui_ready","PASS: live debug Main route, permission-dependent help/CTA, terminal focus and Back; no install/settings activation");
            }
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            application.unregisterActivityLifecycleCallbacks(lifecycle);
            try {
                UpdatesActivity screen=updates.get();if(screen!=null&&!screen.isDestroyed()) {
                    SettingsStructureProbe.main(test,screen::finish);
                    SettingsStructureProbe.await(test,screen::isDestroyed,"Owned Updates did not finish");
                }
                MainActivity home=main.get();if(home!=null){SettingsStructureProbe.finish(test,home);SettingsStructureProbe.quiescent(test,home);}
                SettingsStructureProbe.restore(activityPrefs,saved);
            }catch(Exception|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    private static boolean hasText(android.view.View view,String text) {
        if(view.isShown()&&view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return true;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),text))return true;}
        return false;
    }

    private static void awaitState(Context c,String wanted,Set<Long> ids,Set<String> files,long timeout)throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){capture(UpdateManager.prefs(c),ids,files);String state=UpdateManager.state(c);if(wanted.equals(state))return;require(!state.equals("error"),"Feed check error");Thread.sleep(20);}
        throw new AssertionError("State timeout: "+wanted);
    }
    private static void capture(SharedPreferences prefs,Set<Long> ids,Set<String> files){long id=prefs.getLong("downloadId",-1);if(id>=0)ids.add(id);String file=prefs.getString("downloadFile",null);if(file!=null)files.add(file);}
    private static String hash(File file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[32768];int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();}
    private static final class Feed implements AutoCloseable {
        final ServerSocket socket;final ExecutorService worker=Executors.newSingleThreadExecutor();final Future<?> task;
        final File apk;final PackageInfo info;final String sha;final Context context;volatile boolean closed,wrong,failGet;
        final CountDownLatch failedGetEntered=new CountDownLatch(1),releaseGet404=new CountDownLatch(1);
        final CountDownLatch stalledFeedEntered = new CountDownLatch(1), releaseStalledFeed = new CountDownLatch(1);
        volatile boolean stallFeed;
        long stalledFeedAt;
        volatile int feedStatus=200;
        final AtomicInteger feeds=new AtomicInteger(),heads=new AtomicInteger(),gets=new AtomicInteger();
        Feed(Context context,File apk,PackageInfo info,String sha)throws Exception {
            this.context=context;this.apk=apk;this.info=info;this.sha=sha;
            socket=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));socket.setSoTimeout(500);
            task=worker.submit(()->{serve();return null;});
        }
        String url(String path){return "http://127.0.0.1:"+socket.getLocalPort()+path;}
        void healthy()throws Exception {if(task.isDone())task.get();}
        void serve()throws Exception {
            while(!closed)try(Socket client=socket.accept()) {
                client.setSoTimeout(5000);InputStream input=client.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int matched=0;byte[] end={'\r','\n','\r','\n'};
                while(matched<4&&header.size()<8192){int b=input.read();if(b<0)throw new IOException("Incomplete request");header.write(b);matched=b==end[matched]?matched+1:(b=='\r'?1:0);}
                require(matched==4,"Bounded HTTP request required");String first=header.toString("US-ASCII").split("\\r\\n",2)[0];
                String[] request=first.split(" ");require(request.length==3,"HTTP request line");boolean head=request[0].equals("HEAD");require(head||request[0].equals("GET"),"Only HEAD/GET allowed");
                OutputStream output=client.getOutputStream();
                if(request[1].equals("/feed")) {
                    feeds.incrementAndGet();
                    if (stallFeed) {
                        stalledFeedAt = SystemClock.elapsedRealtime();
                        stalledFeedEntered.countDown();
                        require(releaseStalledFeed.await(35, TimeUnit.SECONDS), "Owned feed stall was not released");
                        continue;
                    }
                    int status=feedStatus;
                    if(status!=200){output.write(("HTTP/1.1 "+status+" Unavailable\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.flush();continue;}
                    byte[] json=new JSONObject().put("schemaVersion",1).put("applicationId",context.getPackageName()).put("channel","stable")
                            .put("versionCode",UpdateManager.version(info)).put("versionName",info.versionName).put("minSdk",26).put("byteSize",apk.length())
                            .put("sha256",wrong?(sha.charAt(0)=='0'?"1":"0")+sha.substring(1):sha).put("apkUrl",url("/newer.apk")).put("releaseNotes","Owned validation fixture").toString().getBytes(StandardCharsets.UTF_8);
                    respond(output,json.length);if(!head)output.write(json);
                } else {
                    require(request[1].equals("/newer.apk"),"Unexpected owned server path");
                    if(head){heads.incrementAndGet();respond(output,apk.length());}
                    else {
                        gets.incrementAndGet();
                        if(failGet) {
                            failedGetEntered.countDown();require(releaseGet404.await(25,TimeUnit.SECONDS),"Owned GET404 gate timeout");
                            output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                        } else {
                            respond(output,apk.length());
                            try(InputStream file=new FileInputStream(apk)){byte[] buffer=new byte[32768];int count;while((count=file.read(buffer))!=-1)output.write(buffer,0,count);}
                        }
                    }
                }
                output.flush();
            }catch(SocketTimeoutException timeout){}catch(SocketException stopped){if(!closed)throw stopped;}
        }
        void respond(OutputStream output,long size)throws IOException {output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "+size+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));}
        @Override public void close()throws Exception {closed=true;releaseGet404.countDown();releaseStalledFeed.countDown();socket.close();worker.shutdownNow();require(worker.awaitTermination(8,TimeUnit.SECONDS),"HTTP fixture worker did not stop");task.get(1,TimeUnit.SECONDS);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

package agency.carpediem.tv.prototype;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Opt-in retained Ready fixture. No Activity, installer, permission or settings action. */
final class UpdateInstallRehearsalProbe {
    private static final String DIRECTORY="update-install-rehearsal";
    static void run(Instrumentation test,Bundle args,Bundle result)throws Exception {
        Context c=test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(c.getPackageName())&&UpdateManager.debug(c)
                &&c.getPackageName().equals(android.app.Application.getProcessName()),"Exact isolated debug process required");
        String nonce=args.getString("nonce"),phase=args.getString("phase");
        require(nonce!=null&&nonce.equals(UUID.fromString(nonce).toString()),"Explicit canonical nonce required");
        UpdateManager.WORKER.submit(()->{}).get(20,TimeUnit.SECONDS);
        File dir=new File(c.getFilesDir(),DIRECTORY);
        if("prepare".equals(phase)) prepare(c,args,result,nonce,dir);
        else {
            JSONObject witness=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(dir,"witness.json").toPath()),StandardCharsets.UTF_8));
            require(witness.getInt("schemaVersion")==1&&nonce.equals(witness.getString("nonce"))&&c.getPackageName().equals(witness.getString("package")),"Witness ownership mismatch");
            if("inspect".equals(phase)) inspect(c,result,witness);
            else if("cleanup".equals(phase)) cleanup(c,result,witness,dir);
            else throw new AssertionError("Explicit prepare/inspect/cleanup phase required");
        }
    }
    private static void prepare(Context c,Bundle args,Bundle result,String nonce,File dir)throws Exception {
        requireNoInstaller(c);
        SharedPreferences prefs=UpdateManager.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());
        require(!UpdateManager.busy(c)&&prefs.getLong("downloadId",-1)<0&&!prefs.getBoolean("enqueuePending",false)&&!UpdateManager.target(c).exists(),"Fresh updater required");
        File candidate=new File(c.getFilesDir(),"update-feed-fixture/newer.apk");String expected=args.getString("candidateSha256");
        long version=Long.parseLong(args.getString("candidateVersionCode","-1"));
        require(expected!=null&&expected.matches("[a-f0-9]{64}")&&candidate.isFile()&&candidate.length()>=1024&&candidate.length()<=UpdateManager.MAX_BYTES&&expected.equals(hash(candidate)),"Sealed bounded candidate required");
        PackageInfo archive=c.getPackageManager().getPackageArchiveInfo(candidate.getPath(),0);
        long oldVersion=UpdateManager.version(UpdateManager.installed(c));
        require(archive!=null&&c.getPackageName().equals(archive.packageName)&&UpdateManager.version(archive)==version&&version>oldVersion,"Higher actual validation APK required");
        String oldSha=hash(new File(c.getApplicationInfo().sourceDir));require(dir.mkdir(),"Fresh rehearsal directory required");
        try(FileOutputStream file=new FileOutputStream(new File(dir,"updates.snapshot"));ObjectOutputStream output=new ObjectOutputStream(file)){output.writeObject(saved);output.flush();file.getFD().sync();}
        boolean retained=false;Throwable failure=null;Feed server=null;long id=-1;String name=null,operation=null;
        try {
            server=new Feed(c,candidate,archive,expected);
            require(prefs.edit().putBoolean("autoDownload",false).putString("state","idle").commit(),"Prepare manual feed");
            UpdateManager.check(c,server.url("/feed"));await(c,"available",30000);
            UpdateManager.download(c);operation=prefs.getString("downloadOperation",null);long deadline=SystemClock.elapsedRealtime()+90000;
            while(SystemClock.elapsedRealtime()<deadline) {
                id=prefs.getLong("downloadId",id);name=prefs.getString("downloadFile",name);
                if("downloading".equals(UpdateManager.state(c)))UpdateManager.refreshNow(c);
                if("ready".equals(UpdateManager.state(c)))break;
                require(!"error".equals(UpdateManager.state(c)),"Actual updater rejected rehearsal candidate");server.healthy();Thread.sleep(30);
            }
            require("ready".equals(UpdateManager.state(c))&&id>=0&&expected.equals(hash(UpdateManager.target(c))),"Production verifier did not reach Ready");
            String transport;
            try(Cursor row=c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&row.moveToFirst(),"Owned ready row missing");transport=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));}
            JSONObject witness=new JSONObject().put("schemaVersion",1).put("nonce",nonce).put("package",c.getPackageName())
                    .put("oldVersion",oldVersion).put("newVersion",version).put("oldApkSha256",oldSha).put("candidateSha256",expected)
                    .put("downloadId",id).put("downloadOperation",prefs.getString("downloadOperation",null)).put("downloadFile",name).put("downloadUri",transport);
            server.close();server=null;
            try(FileOutputStream output=new FileOutputStream(new File(dir,"witness.json"))){output.write(witness.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
            retained=true;result.putString("updaterehearsal_prepare","PASS: actual verified Ready retained with sealed nonce/witness; no installer/settings action");
        }catch(Throwable error){failure=error;}
        finally {
            try {
                if(server!=null)server.close();
                if(!retained) {
                    UpdateManager.WORKER.submit(()->{}).get(20,TimeUnit.SECONDS);
                    requireNoInstaller(c);
                    String current=prefs.getString("downloadOperation",null);
                    require(operation==null||operation.equals(current),"Retain snapshot: updater ownership changed");
                    if(prefs.getBoolean("enqueuePending",false)) {
                        require(operation!=null&&operation.equals(prefs.getString("enqueueOperation",null)),"Retain unowned enqueue journal");
                        UpdateManager.recoverNow(c);
                        require(!prefs.getBoolean("enqueuePending",false),"Retain interrupted enqueue journal for recovery");
                    }
                    if(operation!=null){id=prefs.getLong("downloadId",id);name=prefs.getString("downloadFile",name);removeOwned(c,id,name,null);}
                    SettingsStructureProbe.restore(prefs,saved);deleteDirectory(dir);
                }
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        }
        if(failure instanceof Exception)throw(Exception)failure;if(failure instanceof Error)throw(Error)failure;
    }
    private static void inspect(Context c,Bundle result,JSONObject witness)throws Exception {
        long installed=UpdateManager.version(UpdateManager.installed(c));
        String installedHash=hash(new File(c.getApplicationInfo().sourceDir));
        boolean old=installed==witness.getLong("oldVersion")&&installedHash.equals(witness.getString("oldApkSha256"));
        boolean newer=installed==witness.getLong("newVersion")&&installedHash.equals(witness.getString("candidateSha256"));
        require(old||newer,"Installed APK is neither sealed original nor rehearsal candidate");
        File previous=new File(c.getFilesDir(),"update-rollback/previous.apk");
        boolean backup=previous.isFile()&&hash(previous).equals(witness.getString("oldApkSha256"));
        if(newer)require(backup,"Rollback APK does not match prior installed source");
        result.putString("updaterehearsal_stage",newer?"installed":"original");
        result.putBoolean("updaterehearsal_backup_verified",backup);
        int id=UpdateManager.prefs(c).getInt("sessionId",-1);
        result.putBoolean("updaterehearsal_session_present",!c.getPackageManager().getPackageInstaller().getMySessions().isEmpty()
                ||(id>=0&&c.getPackageManager().getPackageInstaller().getSessionInfo(id)!=null));
        result.putLong("updaterehearsal_installed_version",UpdateManager.version(UpdateManager.installed(c)));
        result.putBoolean("updaterehearsal_can_request_package_installs",c.getPackageManager().canRequestPackageInstalls());
        result.putString("updaterehearsal_state",UpdateManager.state(c));
        result.putString("updaterehearsal_inspect","PASS: app-UID sealed original/candidate version/hash; installed candidate requires exact backup; read-only session observation");
    }
    @SuppressWarnings("unchecked") private static void cleanup(Context c,Bundle result,JSONObject witness,File dir)throws Exception {
        requireNoInstaller(c);
        SharedPreferences prefs=UpdateManager.prefs(c);String current=prefs.getString("downloadOperation",null);
        require(current==null||current.equals(witness.getString("downloadOperation")),"Refuse newer unrelated updater operation");
        require(!UpdateManager.busy(c),"Do not clean an active installer/download");
        Map<String,?> saved;try(ObjectInputStream input=new ObjectInputStream(new FileInputStream(new File(dir,"updates.snapshot")))){saved=(Map<String,?>)input.readObject();}
        removeOwned(c,witness.getLong("downloadId"),witness.getString("downloadFile"),witness.getString("downloadUri"));
        SettingsStructureProbe.restore(prefs,saved);deleteDirectory(dir);
        result.putString("updaterehearsal_cleanup","PASS: owned rehearsal row/target removed, previous updater prefs restored, witness/snapshot deleted");result.putBoolean("updaterehearsal_cleanup_verified",true);
    }
    private static void requireNoInstaller(Context c) {
        String state=UpdateManager.state(c);
        require(!state.equals("installing")&&!state.equals("confirming")
                &&c.getPackageManager().getPackageInstaller().getMySessions().isEmpty(),
                "Retain rehearsal artifacts: active or uncommitted installer session requires owner resolution");
    }
    private static void removeOwned(Context c,long id,String name,String expectedUri)throws Exception {
        if(name!=null)require(name.matches("carpe-update-[a-f0-9-]{36}\\.apk"),"Refuse unrelated filename");
        DownloadManager manager=c.getSystemService(DownloadManager.class);
        if(id>=0) {
            try(Cursor row=manager.query(new DownloadManager.Query().setFilterById(id))){require(row!=null,"Owned query unavailable");if(row.moveToFirst()&&expectedUri!=null)require(expectedUri.equals(row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))),"Row ownership changed");}
            manager.remove(id);try(Cursor row=manager.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&!row.moveToFirst(),"Owned row removal unverified");}
        }
        if(name!=null){File external=c.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);require(external!=null,"Storage unavailable");File file=new File(external,name);require(!file.exists()||file.delete(),"Owned target removal failed");}
    }
    private static void deleteDirectory(File dir){File[] files=dir.listFiles();require(files!=null,"Private directory unavailable");for(File file:files)require(file.isFile()&&file.delete(),"Private fixture file cleanup");require(dir.delete(),"Private fixture directory cleanup");}
    private static void await(Context c,String wanted,long timeout)throws Exception {long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(wanted.equals(UpdateManager.state(c)))return;require(!"error".equals(UpdateManager.state(c)),"Feed check failed");Thread.sleep(20);}throw new AssertionError("State timeout "+wanted);}
    private static String hash(File file)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[32768];int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",value&255));return hex.toString();}
    private static final class Feed implements AutoCloseable {
        final ServerSocket socket;final ExecutorService worker=Executors.newSingleThreadExecutor();final Future<?> task;
        final File apk;final PackageInfo info;final String sha;final Context context;volatile boolean closed,wrong;
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
                    feeds.incrementAndGet();byte[] json=new JSONObject().put("schemaVersion",1).put("applicationId",context.getPackageName()).put("channel","stable")
                            .put("versionCode",UpdateManager.version(info)).put("versionName",info.versionName).put("minSdk",26).put("byteSize",apk.length())
                            .put("sha256",wrong?(sha.charAt(0)=='0'?"1":"0")+sha.substring(1):sha).put("apkUrl",url("/newer.apk")).put("releaseNotes","Owned validation fixture").toString().getBytes(StandardCharsets.UTF_8);
                    respond(output,json.length);if(!head)output.write(json);
                } else {
                    require(request[1].equals("/newer.apk"),"Unexpected owned server path");respond(output,apk.length());
                    if(head)heads.incrementAndGet();else {gets.incrementAndGet();try(InputStream file=new FileInputStream(apk)){byte[] buffer=new byte[32768];int count;while((count=file.read(buffer))!=-1)output.write(buffer,0,count);}}
                }
                output.flush();
            }catch(SocketTimeoutException timeout){}catch(SocketException stopped){if(!closed)throw stopped;}
        }
        void respond(OutputStream output,long size)throws IOException {output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "+size+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));}
        @Override public void close()throws Exception {closed=true;socket.close();worker.shutdownNow();require(worker.awaitTermination(8,TimeUnit.SECONDS),"HTTP fixture worker did not stop");task.get(1,TimeUnit.SECONDS);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

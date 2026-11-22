package agency.carpediem.tv.prototype;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.*;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Prepared durable journal plus real owned DM rows; not an actual enqueue/process-crash test. */
final class UpdateOrphanProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {run(t,result,false);}
    static void run(Instrumentation t,Bundle result,boolean pendingOnly)throws Exception {
        Context base=t.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(base.getPackageName()),"Disposable validation required");
        result.putBoolean("update_can_request_package_installs",base.getPackageManager().canRequestPackageInstalls());
        UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        SharedPreferences prefs=UpdateManager.prefs(base);Map<String,?> saved=new HashMap<>(prefs.getAll());
        require(!UpdateManager.busy(base)&&prefs.getLong("downloadId",-1)<0&&!prefs.getBoolean("enqueuePending",false),"INPUT_UNVERIFIED: existing updater operation/journal");
        File external=base.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);require(external!=null,"INPUT_UNVERIFIED: external fixture storage unavailable");
        File directory=new File(external,"orphan-probe-"+UUID.randomUUID());require(directory.mkdirs(),"Create private orphan fixture");
        File files=new File(directory,"files");require(files.mkdir(),"Create private files fixture");
        Context scoped=new ContextWrapper(base){
            @Override public File getExternalFilesDir(String type){return directory;}
            @Override public File getFilesDir(){return files;}
            @Override public Context getApplicationContext(){return this;}
        };
        DownloadManager dm=base.getSystemService(DownloadManager.class);OwnedDownloadFixture transport=null;Throwable failure=null;
        try {
            transport=new OwnedDownloadFixture(base,directory);
            if(pendingOnly)pendingCase(scoped,prefs,dm,transport,directory,result);
            else {
            File unrelated=new File(directory,"unrelated.bin");long unrelatedId=transport.completedAt(unrelated);
            File marker=new File(directory,"preserve-marker.bin");write(marker);
            String identity=UUID.randomUUID().toString();File orphan=new File(directory,"carpe-update-"+identity+".apk");
            long orphanId=transport.completedAt(orphan,"carpe-update-"+identity);
            seed(prefs,identity,orphan.getName(),transport);
            UpdateManager.recoverNow(scoped);
            require(!exists(dm,orphanId)&&!orphan.exists(),"Prepared journal did not remove actual owned orphan row/file");
            recovered(prefs);preserved(dm,unrelatedId,unrelated,marker);
            commitFailureCase(scoped,prefs,dm,transport,directory,result);
            preserved(dm,unrelatedId,unrelated,marker);
            redirectCase(scoped,prefs,dm,transport,directory,result);
            retryingRedirectCase(scoped,prefs,dm,transport,directory,result);
            preserved(dm,unrelatedId,unrelated,marker);
            Map<String,?> recovered=new HashMap<>(prefs.getAll());
            UpdateManager.recoverNow(scoped);
            require(recovered.equals(prefs.getAll()),"Second orphan recovery was not idempotent");preserved(dm,unrelatedId,unrelated,marker);

            // Journal for A cannot mutate a newer operation B, even if A's row exists.
            String a=UUID.randomUUID().toString();File old=new File(directory,"carpe-update-"+a+".apk");long oldId=transport.completedAt(old,"carpe-update-"+a);
            seed(prefs,a,old.getName(),transport);String b=UUID.randomUUID().toString();
            require(prefs.edit().putString("downloadOperation",b).putString("downloadFile",unrelated.getName()).putLong("downloadId",unrelatedId).putString("state","downloading").commit(),"Prepare newer-operation ownership");
            Map<String,?> newer=new HashMap<>(prefs.getAll());UpdateManager.recoverNow(scoped);
            require(refused(newer,prefs)&&exists(dm,oldId)&&old.isFile(),"Mismatched journal mutated newer operation or old row");preserved(dm,unrelatedId,unrelated,marker);
            require(prefs.edit().putString("downloadOperation",a).putString("enqueueFile","../invalid.apk").commit(),"Prepare malformed journal");
            Map<String,?> malformed=new HashMap<>(prefs.getAll());UpdateManager.recoverNow(scoped);
            require(refused(malformed,prefs)&&exists(dm,oldId)&&old.isFile(),"Malformed journal mutated state or owned row");preserved(dm,unrelatedId,unrelated,marker);

            seed(prefs,a,old.getName(),transport);
            File wrongPath=new File(directory,"different-owned-path.bin");long wrongId=transport.completedAt(wrongPath,"carpe-update-"+a);
            Map<String,?> wrongDestination=new HashMap<>(prefs.getAll());UpdateManager.recoverNow(scoped);
            require(refused(wrongDestination,prefs)&&exists(dm,oldId)&&exists(dm,wrongId)&&old.isFile()&&wrongPath.isFile(),"Matching request URI with different destination was not refused atomically");
            dm.remove(wrongId);require(!exists(dm,wrongId),"Remove own mismatched-destination fixture");

            // Actual file deletion failure must retain journal for retry, not silently lose ownership.
            seed(prefs,a,old.getName(),transport);require(old.delete()&&old.mkdir(),"Prepare blocked cleanup directory");
            File blocker=new File(old,"owned-blocker.bin");write(blocker);
            UpdateManager.recoverNow(scoped);
            require(prefs.getBoolean("enqueuePending",false)&&old.getName().equals(prefs.getString("enqueueFile",null))&&a.equals(prefs.getString("enqueueOperation",null))&&"error".equals(UpdateManager.state(scoped))&&blocker.isFile(),"Failed cleanup lost durable journal/state");
            preserved(dm,unrelatedId,unrelated,marker);
            require(blocker.delete(),"Release own cleanup blocker");
            UpdateManager.recoverNow(scoped);
            require(!old.exists()&&!exists(dm,oldId),"Retry did not finish orphan cleanup");recovered(prefs);preserved(dm,unrelatedId,unrelated,marker);
            result.putString("updateorphan","PASS: prepared journal removes exact real orphan; unrelated row/file survive; idempotent; mismatch/malformed/destination refused; failed delete retains journal and retries");
            result.putString("updateorphan_scope","Real owned loopback DM rows and prepared durable preferences; not actual process death/enqueue crash, HTTP/APK authenticity, installation or system settings evidence");
            }
        }catch(Throwable error){failure=error;}
        finally {
            boolean quiescent=false;
            try{UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);quiescent=true;}catch(Throwable error){failure=append(failure,error);}
            if(transport!=null)try{transport.close();}catch(Throwable error){failure=append(failure,error);}
            if(quiescent){
                try{deleteOwned(directory);}catch(Throwable error){failure=append(failure,error);}
                try{SettingsStructureProbe.restore(prefs,saved);}catch(Throwable error){failure=append(failure,error);}
            }
        }
        if(failure instanceof Exception)throw(Exception)failure;if(failure instanceof Error)throw(Error)failure;
    }
    private static void commitFailureCase(Context scoped,SharedPreferences prefs,DownloadManager dm,OwnedDownloadFixture transport,File directory,Bundle result)throws Exception {
        String identity=UUID.randomUUID().toString();File target=new File(directory,"carpe-update-"+identity+".apk");
        long id=transport.completedAt(target,"carpe-update-"+identity);seed(prefs,identity,target.getName(),transport);
        require(prefs.edit().putLong("downloadId",id).putLong("downloaded",32L).commit(),"Prepare published-id restoration fields");
        FailingRecoveryPreferences failing=new FailingRecoveryPreferences(prefs);
        Context injected=new ContextWrapper(scoped) {
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return "updates".equals(name)?failing:super.getSharedPreferences(name,mode);}
            @Override public Context getApplicationContext(){return this;}
        };
        UpdateManager.recoverNow(injected);
        require(failing.failures==1,"Final recovery commit(false) was not exercised");
        for(SharedPreferences snapshot:new SharedPreferences[]{failing,prefs}) {
            require(snapshot.getBoolean("enqueuePending",false)&&identity.equals(snapshot.getString("enqueueOperation",null))
                    &&target.getName().equals(snapshot.getString("enqueueFile",null))
                    &&target.getName().equals(snapshot.getString("downloadFile",null))
                    &&snapshot.getLong("downloadId",-1)==id&&snapshot.getLong("downloaded",-1)==32L
                    &&"error".equals(snapshot.getString("state",null)),"Failed durability lost retry journal or ownership fields");
        }
        require(!exists(dm,id)&&!target.exists(),"Cleanup did not precede injected final commit failure");
        // The actual Retry entry must enqueue recovery only, not a new HTTP download.
        UpdateManager.download(injected);UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        recovered(failing);recovered(prefs);
        require(!exists(dm,id)&&!target.exists(),"Retry recreated old artifact");
        result.putString("updateorphan_commit_failure","PASS: final commit(false) mutated memory without durable write; journal/id restored and real Retry recovery completed. Injected storage failure, not actual disk fault.");
    }

    private static void redirectCase(Context scoped,SharedPreferences prefs,DownloadManager dm,OwnedDownloadFixture transport,File directory,Bundle result)throws Exception {
        String identity=UUID.randomUUID().toString(),fragment="carpe-update-"+identity;
        File target=new File(directory,"carpe-update-"+identity+".apk");
        int before=transport.redirectHits();long id=transport.completedRedirectAt(target,fragment);
        require(transport.redirectHits()>before,"INPUT_UNVERIFIED: HTTP301 endpoint not requested");
        String stored;
        try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))) {
            require(row!=null&&row.moveToFirst(),"Redirect fixture row missing");
            stored=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
            String destination=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            require(android.net.Uri.fromFile(target).toString().equals(destination),"INPUT_UNVERIFIED: redirect exact destination unavailable");
        }
        String original=transport.redirectUri(fragment).toString();
        seed(prefs,identity,target.getName(),transport);
        require(prefs.edit().putString("enqueueUri",original).commit(),"Prepare redirect journal URI");
        UpdateManager.recoverNow(scoped);
        require(!exists(dm,id)&&!target.exists(),"HTTP301 orphan recovery left owned row/file");recovered(prefs);
        result.putBoolean("updateorphan_redirect_provider_rewrote_uri",!original.equals(stored));
        result.putString("updateorphan_redirect",original.equals(stored)
                ? "INPUT_UNVERIFIED: real HTTP301 completed, but provider retained original URI; fallback precondition unavailable"
                : "PASS: real HTTP301 changed provider URI; exact journal destination recovered owned row/file");
    }

    private static void retryingRedirectCase(Context scoped,SharedPreferences prefs,DownloadManager dm,OwnedDownloadFixture transport,File directory,Bundle result)throws Exception {
        String identity=UUID.randomUUID().toString(),fragment="carpe-update-"+identity;
        File target=new File(directory,fragment+".apk");
        String original=transport.retryingRedirectUri(fragment).toString();
        long id=transport.retryingRedirectAt(target,fragment);
        long deadline=android.os.SystemClock.uptimeMillis()+15000;
        while(android.os.SystemClock.uptimeMillis()<deadline) {
            try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))) {
                require(row!=null&&row.moveToFirst(),"Retrying redirect row missing");
                int status=row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if(transport.retryEndpointHit()&&(status==DownloadManager.STATUS_PAUSED||status==DownloadManager.STATUS_FAILED)) {
                    String uri=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
                    String destination=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                    boolean rewritten=!original.equals(uri);
                    boolean localUriPresent=destination!=null&&!destination.isEmpty();
                    String condition="status="+status+" rewritten="+rewritten+" localUriPresent="+localUriPresent+" filePresent="+target.exists();
                    require(status==DownloadManager.STATUS_PAUSED&&rewritten&&!localUriPresent&&!target.exists(),
                            "INPUT_UNVERIFIED: retrying redirect ownership conditions: "+condition);
                    String description=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION));
                    require(description!=null&&description.endsWith(UpdateDestinationMatch.descriptionSuffix(fragment)),
                            "INPUT_UNVERIFIED: provider lost download description marker");
                    seed(prefs,identity,target.getName(),transport);
                    require(prefs.edit().putString("enqueueUri",original).commit(),"Prepare retrying redirect journal URI");
                    UpdateManager.recoverNow(scoped);
                    require(!exists(dm,id)&&!target.exists(),"HTTP301→503 orphan recovery left owned row/file: "+condition);
                    recovered(prefs);
                    result.putString("updateorphan_retrying_redirect","PASS: real HTTP301→503 orphan removed: "+condition);
                    return;
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("INPUT_UNVERIFIED: retrying redirect did not settle");
    }

    private static void pendingCase(Context scoped,SharedPreferences prefs,DownloadManager dm,OwnedDownloadFixture transport,File directory,Bundle result)throws Exception {
        String identity=UUID.randomUUID().toString();File target=new File(directory,"carpe-update-"+identity+".apk");
        long id=transport.queuedAt(target,"carpe-update-"+identity);
        try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))) {
            require(row!=null&&row.moveToFirst(),"INPUT_UNVERIFIED: own queued row unavailable");
            int status=row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            String local=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            String uri=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
            require(transport.requestUri("carpe-update-"+identity).toString().equals(uri),"INPUT_UNVERIFIED: queued provider URI fragment not preserved");
            require((status==DownloadManager.STATUS_PENDING||status==DownloadManager.STATUS_PAUSED)&&(local==null||local.isEmpty()),"INPUT_UNVERIFIED: queued-null-LOCAL_URI condition unavailable status="+status+" localUriPresent="+(local!=null&&!local.isEmpty()));
        }
        seed(prefs,identity,target.getName(),transport);UpdateManager.recoverNow(scoped);
        require(!exists(dm,id)&&!target.exists(),"Queued URI-correlated orphan not removed");recovered(prefs);
        result.putString("updateorphanpending","PASS: real queued/paused owned row with actual null LOCAL_URI and preserved UUID request URI removed by prepared journal");
        result.putString("updateorphanpending_scope","Own request requiresDeviceIdle only; no device idle/system settings changes; no actual enqueue crash/process death claim");
    }
    private static boolean refused(Map<String,?> before,SharedPreferences prefs){
        Map<String,Object> previous=new HashMap<>(before),current=new HashMap<>(prefs.getAll());
        for(String key:new String[]{"state","messageKey","messageSuffix","message","operationPid","stateAt"}){previous.remove(key);current.remove(key);}
        return previous.equals(current)&&prefs.getBoolean("enqueuePending",false)&&"error".equals(prefs.getString("state",null));
    }
    private static void seed(SharedPreferences p,String operation,String name,OwnedDownloadFixture transport){require(p.edit().putBoolean("enqueuePending",true).putString("enqueueUri",transport.requestUri("carpe-update-"+operation).toString()).putString("enqueueFile",name).putString("enqueueOperation",operation).putString("downloadOperation",operation).putString("downloadFile",name).remove("downloadId").remove("downloaded").putString("state","preparing").putInt("operationPid",android.os.Process.myPid()).commit(),"Prepare durable enqueue journal");}
    private static void recovered(SharedPreferences p){require(!p.contains("enqueuePending")&&!p.contains("enqueueFile")&&!p.contains("enqueueOperation")&&!p.contains("enqueueUri")&&!p.contains("downloadFile")&&!p.contains("downloadId")&&"available".equals(p.getString("state",null)),"Recovered journal/state not durably cleared");}
    private static void preserved(DownloadManager dm,long id,File file,File marker){require(exists(dm,id)&&file.isFile()&&file.length()==OwnedDownloadFixture.BYTES&&marker.isFile()&&marker.length()==32,"Orphan recovery touched unrelated owned row/file/marker");}
    private static boolean exists(DownloadManager dm,long id){try(Cursor cursor=dm.query(new DownloadManager.Query().setFilterById(id))){return cursor!=null&&cursor.moveToFirst();}}
    private static void write(File file)throws Exception{try(FileOutputStream output=new FileOutputStream(file)){output.write(new byte[32]);}}
    private static void deleteOwned(File file){if(file.isDirectory()){File[] children=file.listFiles();require(children!=null,"Cannot enumerate own fixture cleanup");for(File child:children)deleteOwned(child);}require(file.delete(),"Cannot delete own orphan fixture");}
    private static Throwable append(Throwable first,Throwable next){if(first==null)return next;first.addSuppressed(next);return first;}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

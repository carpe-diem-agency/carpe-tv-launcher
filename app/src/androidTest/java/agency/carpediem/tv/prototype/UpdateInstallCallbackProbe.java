package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Synthetic callback coordination only: no installer session, installation or real broadcast. */
final class UpdateInstallCallbackProbe {
    @SuppressWarnings("deprecation") static void run(Instrumentation t,Bundle result)throws Exception {
        Context base=t.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(base.getPackageName()),"Isolated validation package required");
        UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        SharedPreferences prefs=UpdateManager.prefs(base);
        Map<String,?> saved=prefs.getAll();
        require(prefs.getLong("downloadId",-1)<0&&!UpdateManager.busy(base),"INPUT_UNVERIFIED: existing update operation");
        File external=base.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        require(external!=null,"INPUT_UNVERIFIED: external fixture unavailable");
        File dir=new File(external,"callback-probe-"+UUID.randomUUID());
        require(dir.mkdirs(),"Fixture directory creation failed");
        Context scoped=new ContextWrapper(base){
            @Override public File getExternalFilesDir(String type){return dir;}
            @Override public Context getApplicationContext(){return this;}
        };
        long ownedId=-1;Throwable failure=null;
        try {
            int oldSession=Integer.MAX_VALUE;
            require(base.getPackageManager().getPackageInstaller().getSessionInfo(oldSession)==null,"INPUT_UNVERIFIED: reserved missing-session fixture exists");
            require(!UpdateManager.target(scoped).exists(),"Recovery target must be absent");
            require(prefs.edit().putInt("sessionId",oldSession).putString("state","installing").putInt("operationPid",-1).commit(),"Recovery fixture commit failed");
            UpdateManager.recoverNow(scoped);
            require("idle".equals(UpdateManager.state(scoped)),"Missing session/target did not recover idle");
            // No real DM row needed: -1 is the production no-row sentinel, never a foreign ID.
            String identity=UUID.randomUUID().toString();
            String metadata=new JSONObject().put("byteSize",32).put("versionName","validation-fixture").toString();
            require(prefs.edit().putString("downloadOperation",identity).putLong("downloadId",ownedId).putString("metadata",metadata).putString("state","downloading").putInt("operationPid",android.os.Process.myPid()).commit(),"New operation fixture commit failed");
            File target=UpdateManager.target(scoped);write(target);
            // Captured old session ID matches a retained legacy preference if recovery failed to retire it.
            Intent stale=new Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,oldSession).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_SUCCESS);
            SettingsStructureProbe.main(t,()->new UpdateInstallReceiver().onReceive(scoped,stale));
            require("downloading".equals(UpdateManager.state(scoped))&&prefs.getLong("downloadId",-1)==ownedId&&identity.equals(prefs.getString("downloadOperation",null))&&metadata.equals(prefs.getString("metadata",null))&&target.isFile()&&target.length()==32,"Stale install success changed new operation state, metadata, target");
            // Same receiver path for a currently bound synthetic session; never create/install a session.
            for(boolean legacy:new boolean[]{false,true}) {
                for(int status:new int[]{PackageInstaller.STATUS_SUCCESS,PackageInstaller.STATUS_FAILURE,PackageInstaller.STATUS_FAILURE_ABORTED}) {
                    seedSession(prefs,oldSession,identity,metadata,legacy);
                    write(target);
                    callback(t,scoped,oldSession,status);
                    require(!prefs.contains("sessionId")&&!prefs.contains("sessionDownloadOperation")&&!prefs.contains("sessionDownloadId"),"Terminal callback retained session binding");
                    if(status==PackageInstaller.STATUS_SUCCESS) {
                        require("idle".equals(UpdateManager.state(scoped)),"Current success did not become idle");
                        if(legacy)require(target.isFile()&&metadata.equals(prefs.getString("metadata",null))&&identity.equals(prefs.getString("downloadOperation",null)),"Legacy success deleted unbound artifact");
                        else require(!target.exists()&&!prefs.contains("metadata")&&!prefs.contains("downloadOperation")&&!prefs.contains("downloadId"),"Bound success did not clean current artifact");
                    } else {
                        require("ready".equals(UpdateManager.state(scoped))&&target.isFile()&&target.length()==32&&metadata.equals(prefs.getString("metadata",null))&&identity.equals(prefs.getString("downloadOperation",null)),"Current failure lost retry artifact");
                        String expected=status==PackageInstaller.STATUS_FAILURE_ABORTED?"update_install_cancelled":"update_installer_failed";
                        require(expected.equals(prefs.getString("messageKey",null)),"Terminal failure message mismatch");
                    }
                }
            }
            seedSession(prefs,oldSession,identity,metadata,true);write(target);
            callback(t,scoped,oldSession,PackageInstaller.STATUS_PENDING_USER_ACTION);
            require("installing".equals(UpdateManager.state(scoped))&&target.isFile()&&prefs.getInt("sessionId",-1)==oldSession,"Legacy pending callback changed active artifact/state");
            seedSession(prefs,oldSession,identity,metadata,false);
            require(prefs.edit().putString("sessionDownloadOperation",UUID.randomUUID().toString()).commit(),"Mismatch fixture commit failed");
            callback(t,scoped,oldSession,PackageInstaller.STATUS_SUCCESS);
            require("installing".equals(UpdateManager.state(scoped))&&target.isFile()&&metadata.equals(prefs.getString("metadata",null))&&!prefs.contains("sessionId"),"Mismatched binding callback changed current operation");
            result.putString("updatecallback","PASS: original stale recovery regression; bound success/failure/abort; legacy terminal/pending; mismatched binding");
            result.putString("updatecallbackScope","No DM row (downloadId=-1); direct synthetic receiver callbacks; no real broadcast delivery, installer session, APK installation or authenticity claim");
        }catch(Throwable error){failure=error;}
        finally {
            try {
                UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
                File[] files=dir.listFiles();if(files!=null)for(File file:files)require(file.delete(),"Owned file cleanup failed");
                require(dir.delete(),"Owned directory cleanup failed");
                SettingsStructureProbe.restore(prefs,saved);
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        }
        if(failure instanceof Exception)throw (Exception)failure;
        if(failure instanceof Error)throw (Error)failure;
    }
    private static void seedSession(SharedPreferences prefs,int session,String identity,String metadata,boolean legacy) {
        SharedPreferences.Editor editor=prefs.edit().putInt("sessionId",session).putString("state","installing").putString("downloadOperation",identity).putLong("downloadId",-1).putString("metadata",metadata);
        if(legacy)editor.remove("sessionDownloadOperation").remove("sessionDownloadId");
        else editor.putString("sessionDownloadOperation",identity).putLong("sessionDownloadId",-1);
        require(editor.commit(),"Session fixture commit failed");
    }
    private static void callback(Instrumentation t,Context context,int session,int status)throws Exception {
        Intent callback=new Intent().putExtra(PackageInstaller.EXTRA_SESSION_ID,session).putExtra(PackageInstaller.EXTRA_STATUS,status);
        SettingsStructureProbe.main(t,()->new UpdateInstallReceiver().onReceive(context,callback));
    }
    private static void write(File file)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[32]);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

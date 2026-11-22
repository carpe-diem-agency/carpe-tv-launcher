package agency.carpediem.tv.prototype;

import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Real owned DM rows; synthetic verifier tests coordination, never APK authenticity or HTTP. */
final class UpdateCancelProbe {
    static void run(Instrumentation t, Bundle result) throws Exception {
        Context base=t.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(base.getPackageName()),"Isolated validation package required");
        UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        SharedPreferences prefs=UpdateManager.prefs(base);
        Map<String,?> saved=prefs.getAll();
        require(prefs.getLong("downloadId",-1)<0&&!UpdateManager.busy(base),"INPUT_UNVERIFIED: existing update operation");
        File external=base.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        require(external!=null,"INPUT_UNVERIFIED: external fixture unavailable");
        File dir=new File(external,"cancel-probe-"+UUID.randomUUID());
        require(dir.mkdirs(),"Fixture creation failed");
        Context scoped=new ContextWrapper(base) {
            @Override public File getExternalFilesDir(String type){return dir;}
            @Override public Context getApplicationContext(){return this;}
        };
        DownloadManager dm=base.getSystemService(DownloadManager.class);
        OwnedDownloadFixture transport=new OwnedDownloadFixture(base,dir);
        ExecutorService workers=Executors.newSingleThreadExecutor();
        ArrayList<CountDownLatch> gates=new ArrayList<>();
        Throwable failure=null;
        try {
            for(boolean staleError:new boolean[]{false,true}) {
                long a=transport.completed();
                seed(prefs,a);
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
                gates.add(release);
                Future<?> old=workers.submit(()->UpdateManager.refreshNow(scoped,a,(c,file,metadata)->{
                    require(metadata.getLong("byteSize")==32,"Captured metadata mismatch");
                    entered.countDown();
                    if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Verifier gate timeout");
                    if(staleError)throw new IOException("Owned stale verifier error");
                }));
                require(entered.await(8,TimeUnit.SECONDS),"INPUT_UNVERIFIED: owned completed row did not enter verifier");
                require("verifying".equals(UpdateManager.state(scoped)),"Expected verifying gate");
                UpdateManager.cancel(scoped);
                require(!exists(dm,a)&&"available".equals(UpdateManager.state(scoped)),"Cancel did not remove owned operation");
                long b=transport.completed();
                String identity=seed(prefs,b);
                File target=UpdateManager.target(scoped);write(target);
                AtomicInteger rejected=new AtomicInteger();
                UpdateManager.refreshNow(scoped,a,(c,f,m)->rejected.incrementAndGet());
                require(rejected.get()==0,"Old receiver ID reached verifier");
                unchanged(scoped,dm,b,identity,target,"downloading");
                // Adopt the retry token while A is still gated, using a separate synchronous seam.
                AtomicInteger verified=new AtomicInteger();
                UpdateManager.refreshNow(scoped,b,(c,f,m)->verified.incrementAndGet());
                require(verified.get()==1,"Retry verifier did not execute exactly once");
                unchanged(scoped,dm,b,identity,target,"ready");
                release.countDown();old.get(12,TimeUnit.SECONDS);
                unchanged(scoped,dm,b,identity,target,"ready");
                UpdateManager.cancel(scoped);
                require(!exists(dm,b),"Retry cleanup left owned row");
            }
            for(String state:new String[]{"checking","preparing"}) {
                require(prefs.edit().putString("state",state).putInt("operationPid",-1).commit(),"Recovery fixture commit failed");
                UpdateManager.recoverNow(scoped);
                require("idle".equals(UpdateManager.state(scoped)),"Interrupted preparation did not recover to idle");
            }
            result.putString("updatecancel","PASS: stale success/error, retry token, old receiver expected-ID seam, interrupted preparation recovery");
            result.putString("updatecancelScope","Owned real completed DM rows; synthetic verification; no APK authenticity, HTTP, broadcast delivery or installation claim");
        } catch(Throwable e){failure=e;}
        finally {
            for(CountDownLatch gate:gates)gate.countDown();
            workers.shutdown();
            try {
                if(!workers.awaitTermination(15,TimeUnit.SECONDS)){workers.shutdownNow();require(workers.awaitTermination(5,TimeUnit.SECONDS),"Verifier workers did not stop");}
                UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
                transport.close();
                for(File f:dir.listFiles()==null?new File[0]:dir.listFiles())require(f.delete(),"Owned fixture file cleanup failed");
                require(dir.delete(),"Owned fixture directory cleanup failed");
                SettingsStructureProbe.restore(prefs,saved);
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        }
        if(failure instanceof Exception)throw (Exception)failure;
        if(failure instanceof Error)throw (Error)failure;
    }
    private static String seed(SharedPreferences p,long id)throws Exception {
        String identity=UUID.randomUUID().toString();
        require(p.edit().putString("metadata",new JSONObject().put("byteSize",32).toString()).putString("downloadOperation",identity).putLong("downloadId",id).putString("state","downloading").putInt("operationPid",android.os.Process.myPid()).commit(),"Operation fixture commit failed");
        return identity;
    }
    private static void write(File f)throws Exception {try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[32]);}}
    private static boolean exists(DownloadManager dm,long id){try(Cursor c=dm.query(new DownloadManager.Query().setFilterById(id))){return c!=null&&c.moveToFirst();}}
    private static void unchanged(Context c,DownloadManager dm,long id,String identity,File target,String state){
        SharedPreferences p=UpdateManager.prefs(c);
        require(state.equals(UpdateManager.state(c))&&p.getLong("downloadId",-1)==id&&identity.equals(p.getString("downloadOperation",null))&&target.isFile()&&target.length()==32&&exists(dm,id),"Stale operation changed retry state, row or file");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

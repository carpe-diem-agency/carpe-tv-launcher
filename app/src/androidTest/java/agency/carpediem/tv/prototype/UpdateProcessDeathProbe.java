package agency.carpediem.tv.prototype;

import android.app.Application;
import android.app.DownloadManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Explicit two-process enqueue interruption. Never launches an Activity or installs an APK. */
final class UpdateProcessDeathProbe {
    static final String DIRECTORY="update-process-death-fixture";
    static void run(Instrumentation test,Bundle args,Bundle result)throws Exception {
        String nonce=args.getString("nonce"),phase=args.getString("phase");
        require(nonce!=null&&nonce.equals(UUID.fromString(nonce).toString()),"Canonical host UUID nonce required");
        if("prepare".equals(phase)) phaseA(test,result,nonce);
        else if("recover".equals(phase)) phaseB(test,result,nonce);
        else throw new AssertionError("Explicit prepare/recover phase required");
    }
    private static Context guarded(Instrumentation test) {
        Context c=test.getTargetContext();String expected="agency.carpediem.tv.validation.publicpreview";
        require(expected.equals(c.getPackageName())&&expected.equals(Application.getProcessName()),"Exact isolated target process required");return c;
    }
    static void phaseA(Instrumentation test,Bundle result,String nonce)throws Exception {
        Context base=guarded(test);UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
        SharedPreferences prefs=UpdateManager.prefs(base);
        require(!UpdateManager.busy(base)&&prefs.getLong("downloadId",-1)<0&&!prefs.getBoolean("enqueuePending",false),"Existing update operation");
        require(!UpdateManager.target(base).exists(),"Existing updater artifact must be preserved");
        File dir=new File(base.getFilesDir(),DIRECTORY);require(dir.mkdir(),"Fresh private phase fixture required");
        try(FileOutputStream file=new FileOutputStream(new File(dir,"preferences.snapshot"));ObjectOutputStream out=new ObjectOutputStream(file)) {
            out.writeObject(new HashMap<>(prefs.getAll()));out.flush();file.getFD().sync();
        }
        ServerSocket server=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
        Thread head=new Thread(()->{
            try {while(true)try(Socket socket=server.accept()) {
                socket.setSoTimeout(3000);InputStream in=socket.getInputStream();int matched=0,count=0;byte[] end={'\r','\n','\r','\n'};
                while(matched<4&&count++<8192){int b=in.read();if(b<0)break;matched=b==end[matched]?matched+1:(b=='\r'?1:0);}
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            }}catch(IOException stopped){}
        },"owned-enqueue-head");head.setDaemon(true);head.start();
        String url="http://127.0.0.1:"+server.getLocalPort()+"/enqueue-only";
        require(prefs.edit().putString("metadata",new JSONObject().put("byteSize",1024).put("versionName","fixture").put("apkUrl",url).toString())
                .putString("state","available").commit(),"Prepare actual download metadata");
        SharedPreferences intercepted=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            if(!method.getName().equals("edit"))return invoke(method,prefs,args);
            SharedPreferences.Editor delegate=prefs.edit();Map<String,Object> writes=new HashMap<>();
            return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(editor,m,a)->{
                if(m.getName().startsWith("put")&&a!=null&&a.length==2)writes.put((String)a[0],a[1]);
                if(m.getName().equals("commit")&&writes.containsKey("downloadId")&&Boolean.FALSE.equals(writes.get("enqueuePending"))) {
                    long id=(Long)writes.get("downloadId");
                    require(prefs.getBoolean("enqueuePending",false)&&!prefs.contains("downloadId"),"Publish unexpectedly delegated before kill");
                    DownloadManager dm=base.getSystemService(DownloadManager.class);
                    requireOwnedRow(base,dm,id,prefs.getString("enqueueUri",null),prefs.getString("enqueueFile",null));
                    JSONObject witness=new JSONObject().put("schemaVersion",1).put("nonce",nonce).put("package",base.getPackageName()).put("pid",Process.myPid()).put("downloadId",id)
                            .put("operation",prefs.getString("enqueueOperation",null)).put("uri",prefs.getString("enqueueUri",null))
                            .put("file",prefs.getString("enqueueFile",null));
                    try(FileOutputStream output=new FileOutputStream(new File(dir,"witness.json"))){output.write(witness.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
                    Process.killProcess(Process.myPid());throw new AssertionError("Process kill returned");
                }
                Object value=invoke(m,delegate,a);return value==delegate?editor:value;
            });
        });
        Context wrapped=new ContextWrapper(base) {
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return "updates".equals(name)?intercepted:super.getSharedPreferences(name,mode);}
        };
        UpdateManager.download(wrapped);
        UpdateManager.WORKER.submit(()->{}).get(20,TimeUnit.SECONDS);
        server.close();throw new AssertionError("Phase A did not reach enqueue publication kill seam");
    }
    @SuppressWarnings("unchecked") static void phaseB(Instrumentation test,Bundle result,String nonce)throws Exception {
        Context base=guarded(test);File dir=new File(base.getFilesDir(),DIRECTORY);
        JSONObject witness=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(dir,"witness.json").toPath()),StandardCharsets.UTF_8));
        require(witness.getInt("schemaVersion")==1&&nonce.equals(witness.getString("nonce"))&&base.getPackageName().equals(witness.getString("package"))&&witness.getInt("pid")!=Process.myPid(),"Independent replacement process required");
        SharedPreferences prefs=UpdateManager.prefs(base);long id=witness.getLong("downloadId");
        require(prefs.getBoolean("enqueuePending",false)&&!prefs.contains("downloadId")
                &&witness.getString("operation").equals(prefs.getString("enqueueOperation",null))
                &&witness.getString("uri").equals(prefs.getString("enqueueUri",null))
                &&witness.getString("file").equals(prefs.getString("enqueueFile",null)),"Durable pre-publish journal missing before recovery");
        DownloadManager dm=base.getSystemService(DownloadManager.class);
        requireOwnedRow(base,dm,id,witness.getString("uri"),witness.getString("file"));
        Map<String,?> saved;
        try(ObjectInputStream in=new ObjectInputStream(new FileInputStream(new File(dir,"preferences.snapshot")))){saved=(Map<String,?>)in.readObject();}
        Throwable failure=null;
        try {
            UpdateManager.recoverNow(base);
            require(!prefs.getBoolean("enqueuePending",false)&&"available".equals(UpdateManager.state(base)),"Actual interrupted enqueue did not recover");
            try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&!row.moveToFirst(),"Actual orphan row survived recovery");}
            File external=base.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            require(external!=null&&!new File(external,witness.getString("file")).exists(),"Owned orphan target survived");
            result.putString("updateprocessdeath","PASS: actual enqueue killed before id publication; new process read durable journal/owned row then recovered. HTTP GET completion excluded.");
            result.putString("updateprocessdeath_nonce",nonce);
            result.putBoolean("updateprocessdeath_row_cleanup_verified",true);
            result.putInt("updateprocessdeath_old_pid",witness.getInt("pid"));result.putInt("updateprocessdeath_new_pid",Process.myPid());
        }catch(Throwable error){failure=error;}
        finally {
            try {
                dm.remove(id);
                try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){require(row!=null&&!row.moveToFirst(),"Owned row cleanup unverified");}
                UpdateManager.WORKER.submit(()->{}).get(15,TimeUnit.SECONDS);
                SettingsStructureProbe.restore(prefs,saved);
                for(File file:Objects.requireNonNull(dir.listFiles()))require(file.delete(),"Delete private phase fixture");require(dir.delete(),"Delete phase directory");
            }catch(Throwable cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}
        }
        if(failure==null)result.putBoolean("updateprocessdeath_row_cleanup_verified",true);
        if(failure instanceof Exception)throw(Exception)failure;if(failure instanceof Error)throw(Error)failure;
    }
    private static void requireOwnedRow(Context base,DownloadManager manager,long id,String source,String filename) {
        require(id>=0&&source!=null&&filename!=null&&filename.matches("carpe-update-[0-9a-f-]{36}\\.apk"),"Invalid enqueue witness");
        File directory=base.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        require(directory!=null,"Download directory unavailable");
        try(Cursor row=manager.query(new DownloadManager.Query().setFilterById(id))) {
            require(row!=null&&row.moveToFirst(),"Actual enqueued row unavailable");
            require(source.equals(row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))),"Enqueued request URI mismatch");
            String local=row.getString(row.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
            require(local==null||local.isEmpty()||android.net.Uri.fromFile(new File(directory,filename)).toString().equals(local),"Enqueued destination mismatch");
        }
    }
    private static Object invoke(Method method,Object target,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException error){throw error.getCause();}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

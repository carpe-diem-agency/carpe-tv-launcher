package agency.carpediem.tv.prototype;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Validation-only real DM transport over loopback, not remote HTTP or APK verification. */
final class OwnedDownloadFixture implements AutoCloseable {
    static final int BYTES=32;
    private final DownloadManager manager;
    private final File directory;
    private final ServerSocket server;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ArrayList<Long> ids=new ArrayList<>();
    private final Future<?> serving;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicInteger redirectHits=new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean retryEndpointHit;
    boolean retryEndpointHit(){return retryEndpointHit;}
    int redirectHits(){return redirectHits.get();}

    OwnedDownloadFixture(Context context,File parent)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),"Isolated validation package required");
        manager=context.getSystemService(DownloadManager.class);
        directory=new File(parent,"loopback-dm-"+UUID.randomUUID());
        require(directory.mkdirs(),"Owned transport directory creation failed");
        ServerSocket opened=null;
        try {
            opened=new ServerSocket(0,4,InetAddress.getByName("127.0.0.1"));
            opened.setSoTimeout(500);
            server=opened;
            serving=executor.submit(()->{serve();return null;});
        }catch(Exception failure){if(opened!=null)opened.close();executor.shutdownNow();directory.delete();throw failure;}
    }

    /** ID is recorded immediately after enqueue, including timeout/failure paths. */
    long completed()throws Exception {
        return completedAt(new File(directory,"row-"+UUID.randomUUID()+".bin"));
    }
    /** Explicit owned destination for updater-journal integration; restricted to fixture parent. */
    long completedAt(File destination)throws Exception {return completedAt(destination,null);}
    long completedAt(File destination,String fragment)throws Exception {
        return completedAt(destination,fragment,false);
    }
    long completedRedirectAt(File destination,String fragment)throws Exception {return completedAt(destination,fragment,true);}
    private long completedAt(File destination,String fragment,boolean redirect)throws Exception {
        long id=enqueueAt(destination,redirect?redirectUri(fragment):requestUri(fragment),false);
        long deadline=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<deadline) {
            if(serving.isDone())serving.get();
            try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))) {
                if(cursor!=null&&cursor.moveToFirst()) {
                    int status=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    int reason=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    if(status==DownloadManager.STATUS_SUCCESSFUL) {
                        long bytes=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        require(bytes==BYTES&&destination.isFile()&&destination.length()==BYTES,"Owned loopback body length mismatch");
                        if(fragment!=null&&!redirect)require(requestUri(fragment).toString().equals(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))),"INPUT_UNVERIFIED: provider did not preserve exact request URI fragment");
                        return id;
                    }
                    if(status==DownloadManager.STATUS_FAILED)throw new AssertionError("INPUT_UNVERIFIED: loopback DM failed status="+status+" reason="+reason);
                }
            }
            Thread.sleep(100);
        }
        throw new AssertionError("INPUT_UNVERIFIED: loopback DM success timed out");
    }

    long queuedAt(File destination,String fragment)throws Exception {return enqueueAt(destination,requestUri(fragment),true);}
    long retryingRedirectAt(File destination,String fragment)throws Exception {return enqueueAt(destination,retryingRedirectUri(fragment),false,
            "Validation fixture"+UpdateDestinationMatch.descriptionSuffix(fragment));}
    private long enqueueAt(File destination,Uri uri,boolean requireIdle)throws Exception {
        return enqueueAt(destination,uri,requireIdle,null);
    }

    private long enqueueAt(File destination,Uri uri,boolean requireIdle,String description)throws Exception {
        require(!closed,"Transport fixture already closed");
        String root=directory.getParentFile().getCanonicalPath()+File.separator;
        require(destination.getCanonicalPath().startsWith(root)&&!destination.exists(),"Destination must be new and inside owned fixture parent");
        DownloadManager.Request request=new DownloadManager.Request(uri);
        request.setDestinationUri(Uri.fromFile(destination));
        request.setTitle("Validation fixture");
        if(description!=null)request.setDescription(description);
        request.setMimeType("application/octet-stream");
        // No hidden-notification permission is requested; only the owned completed row is removed.
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        if(requireIdle)request.setRequiresDeviceIdle(true);
        long id=manager.enqueue(request);ids.add(id);return id;
    }

    Uri requestUri(String fragment){return Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/fixture").buildUpon().fragment(fragment).build();}

    Uri redirectUri(String fragment){return requestUri(fragment).buildUpon().path("/redirect").build();}

    Uri retryingRedirectUri(String fragment){return requestUri(fragment).buildUpon().path("/redirect-retry").build();}

    private void serve()throws IOException {
        while(!closed) {
            try(Socket socket=server.accept()) {
                socket.setSoTimeout(3000);
                InputStream input=socket.getInputStream();
                int count=0,matched=0;StringBuilder headers=new StringBuilder();
                byte[] ending={'\r','\n','\r','\n'};
                while(matched<4&&count++<8192) {
                    int value=input.read();if(value<0)throw new IOException("Incomplete loopback request");
                    headers.append((char)value);
                    matched=value==ending[matched]?matched+1:(value=='\r'?1:0);
                }
                if(matched!=4)throw new IOException("Loopback request headers exceeded bound");
                OutputStream output=socket.getOutputStream();
                String request=headers.toString();
                if(request.startsWith("GET /fixture-retry ")) {
                    retryEndpointHit=true;
                    output.write("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 120\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();continue;
                }
                boolean retry=request.startsWith("GET /redirect-retry ");
                if(retry||request.startsWith("GET /redirect ")) {
                    redirectHits.incrementAndGet();
                    Uri location=retry?requestUri(null).buildUpon().path("/fixture-retry").build():requestUri(null);
                    output.write(("HTTP/1.1 301 Moved Permanently\r\nLocation: "+location+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.flush();continue;
                }
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: "+BYTES+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(new byte[BYTES]);output.flush();
            }catch(SocketTimeoutException timeout){/* Recheck close at bounded intervals. */}
            catch(SocketException stopped){if(!closed)throw stopped;}
        }
    }

    @Override public void close()throws Exception {
        closed=true;Throwable failure=null;
        try{server.close();serving.get(5,TimeUnit.SECONDS);}catch(Throwable error){failure=error;}
        executor.shutdownNow();
        try{require(executor.awaitTermination(5,TimeUnit.SECONDS),"Loopback server did not stop");}catch(Throwable error){failure=append(failure,error);}
        for(long id:ids)try {
            manager.remove(id);
            try(Cursor cursor=manager.query(new DownloadManager.Query().setFilterById(id))){require(cursor==null||!cursor.moveToFirst(),"Owned loopback row cleanup failed");}
        }catch(Throwable error){failure=append(failure,error);}
        try{File[] files=directory.listFiles();if(files!=null)for(File file:files)require(file.delete(),"Owned loopback file cleanup failed");require(directory.delete(),"Owned loopback directory cleanup failed");}
        catch(Throwable error){failure=append(failure,error);}
        if(failure instanceof Exception)throw (Exception)failure;
        if(failure instanceof Error)throw (Error)failure;
    }
    private static Throwable append(Throwable first,Throwable next){if(first==null)return next;first.addSuppressed(next);return first;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

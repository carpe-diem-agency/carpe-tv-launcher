package agency.carpediem.tv.prototype;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** At most one transfer; starts only with an active screensaver and stops on leaving it. */
final class SaverSync {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static void start(Context context,BooleanSupplier active,Runnable changed) {
        Context app=context.getApplicationContext();
        worker.execute(()-> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            if(!active.getAsBoolean()||!Screensaver.prefs(app).getBoolean("saverOnline",true)) return;
            SaverCache cache=SaverCache.get(app);
            if(!cache.makeRoom(SaverCache.MIB)) return;
            try {
                List<SaverMedia> items=SaverCatalog.load(app,active);int downloaded=0,attempts=0;
                for(SaverMedia item:items) {
                    if(!active.getAsBoolean()||downloaded>=3||attempts>=6) break;
                    if(cache.file(item).isFile()) continue;
                    if(!cache.makeRoom(SaverCache.MIB)) break;
                    attempts++;HttpURLConnection connection=null;
                    try {
                        connection=SaverCatalog.open(item.url,active);
                        long max=(item.kind.equals("video")?80:12)*SaverCache.MIB;
                        try(InputStream input=connection.getInputStream()) {
                            if(cache.store(item,input,connection.getContentLengthLong(),max,active,file->valid(file,item.kind))) {downloaded++;changed.run();}
                        }
                    } catch(Exception unavailable) { /* Retain known-good cache and offline slides. */ }
                    finally {if(connection!=null) connection.disconnect();}
                }
            } catch(Exception unavailable) { /* Offline fallback is always available. */ }
        });
    }
    private static boolean valid(File file,String kind) throws Exception {
        if(kind.equals("video")) {
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();
            try {
                metadata.setDataSource(file.getAbsolutePath());
                int width=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height=Integer.parseInt(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                long duration=Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                return width>=640&&height>=360&&width<=1920&&height<=1080&&duration>=1000&&duration<=240000;
            } finally {metadata.release();}
        }
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        if(options.outWidth<1280||options.outHeight<720||options.outWidth>8192||options.outHeight>8192||options.outWidth<options.outHeight*1.3) return false;
        options.inJustDecodeBounds=false;options.inSampleSize=8;
        Bitmap check=BitmapFactory.decodeFile(file.getAbsolutePath(),options);if(check==null) return false;check.recycle();return true;
    }
}

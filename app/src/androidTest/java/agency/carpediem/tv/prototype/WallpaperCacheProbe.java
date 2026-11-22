package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Real Android decode/publication attempts, not a forced deterministic race. */
final class WallpaperCacheProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),"Disposable validation package required");
        File root=new File(test.getTargetContext().getCacheDir(),"wallpaper-cache-probe-"+Long.toHexString(System.nanoTime()));require(root.mkdir(),"Create isolated cache fixture");
        Context context=new ContextWrapper(test.getTargetContext()){@Override public File getCacheDir(){return root;}};
        ExecutorService workers=Executors.newFixedThreadPool(2);List<Bitmap> owned=Collections.synchronizedList(new ArrayList<>());Throwable primary=null;
        try {
            String expected=null;
            for(int round=0;round<4;round++){
                String key="cold-"+round;File cache=new File(root,"wallpaper-"+key+"-blur-v4.png");
                CountDownLatch start=new CountDownLatch(1),ready=new CountDownLatch(2);
                List<Future<Bitmap>> futures=new ArrayList<>();
                try {
                    for(int i=0;i<2;i++)futures.add(workers.submit(()->{
                        ready.countDown();require(start.await(10,TimeUnit.SECONDS),"Concurrent start gate timed out");
                        Bitmap bitmap=WallpaperBlur.loadOrCreate(context,R.drawable.wallpaper_alpine,key);owned.add(bitmap);return bitmap;
                    }));
                    require(ready.await(10,TimeUnit.SECONDS),"Workers did not reach concurrent gate");start.countDown();
                    Bitmap first=futures.get(0).get(25,TimeUnit.SECONDS),second=futures.get(1).get(25,TimeUnit.SECONDS);
                    validate(first);validate(second);String hash=hash(first);
                    if(expected==null)expected=hash;require(expected.equals(hash)&&expected.equals(hash(second)),"Concurrent cold outputs differ in pixels");
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(cache.getPath(),bounds);
                    require(bounds.outWidth==first.getWidth()&&bounds.outHeight==first.getHeight(),"Final PNG bounds differ from generated image");
                    Bitmap published=BitmapFactory.decodeFile(cache.getPath());owned.add(published);validate(published);require(expected.equals(hash(published)),"Final PNG pixels differ");
                    Bitmap warm=WallpaperBlur.loadOrCreate(context,R.drawable.wallpaper_alpine,key);owned.add(warm);validate(warm);require(expected.equals(hash(warm)),"Warm cache pixels differ");
                    try(FileOutputStream out=new FileOutputStream(cache)){out.write(new byte[]{1,2,3,4});}
                    Bitmap repaired=WallpaperBlur.loadOrCreate(context,R.drawable.wallpaper_alpine,key);owned.add(repaired);validate(repaired);require(expected.equals(hash(repaired)),"Corrupt cache fallback changed pixels");
                    Bitmap finalDecode=BitmapFactory.decodeFile(cache.getPath());owned.add(finalDecode);validate(finalDecode);require(expected.equals(hash(finalDecode)),"Regenerated final PNG is invalid");
                    // Preserve a valid PNG header while removing image data. Some
                    // platform decoders can return a partial non-null bitmap.
                    long completeLength=cache.length();require(completeLength>64,"PNG fixture too small for partial-content case");
                    try(RandomAccessFile partial=new RandomAccessFile(cache,"rw")){partial.setLength(completeLength/2);}
                    Bitmap partialRepair=WallpaperBlur.loadOrCreate(context,R.drawable.wallpaper_alpine,key);owned.add(partialRepair);validate(partialRepair);
                    require(expected.equals(hash(partialRepair)),"Truncated valid-header PNG returned partial pixels instead of recovery");
                    Bitmap partialFinal=BitmapFactory.decodeFile(cache.getPath());owned.add(partialFinal);validate(partialFinal);
                    require(expected.equals(hash(partialFinal)),"Truncated valid-header PNG was not regenerated completely");
                    // Both futures have completed; no caller can still use these round-owned images.
                    synchronized(owned){for(Bitmap bitmap:owned)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();owned.clear();}
                    File[] files=root.listFiles();require(files!=null,"Fixture directory unavailable");for(File file:files)require(file.getName().endsWith(".png"),"Cache left temporary publication artifacts");
                }finally{start.countDown();}
            }
            result.putString("wallpaper_cache","PASS: four two-thread cold-cache attempts, equal output pixels, final PNG bounds/content, warm decode, invalid-header recovery and truncated valid-header PNG recovery");
            result.putString("wallpaper_cache_scope","Actual Android concurrency attempts and real resource decoding in unique cache wrapper; not forced interleaving, production-cache soak or disk-full proof");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            workers.shutdownNow();Throwable cleanup=null;boolean stopped=false;
            try{stopped=workers.awaitTermination(30,TimeUnit.SECONDS);require(stopped,"Cache workers did not terminate; scoped files retained");}
            catch(Exception|Error failure){cleanup=failure;}
            if(stopped)try{synchronized(owned){for(Bitmap bitmap:owned)if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();owned.clear();}File[] files=root.listFiles();if(files!=null)for(File file:files)require(file.isFile()&&file.delete(),"Delete scoped cache file");require(root.delete(),"Delete scoped fixture directory");}
            catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static void validate(Bitmap bitmap){require(bitmap!=null&&!bitmap.isRecycled()&&bitmap.getWidth()>0&&bitmap.getHeight()>0&&bitmap.getWidth()<=512&&bitmap.getHeight()<=512,"Invalid bounded wallpaper bitmap");}
    private static String hash(Bitmap bitmap)throws Exception{int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int p:pixels){digest.update((byte)(p>>>24));digest.update((byte)(p>>>16));digest.update((byte)(p>>>8));digest.update((byte)p);}return Arrays.toString(digest.digest());}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

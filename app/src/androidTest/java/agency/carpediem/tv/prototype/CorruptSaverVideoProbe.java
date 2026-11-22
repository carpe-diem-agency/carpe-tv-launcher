package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.VideoView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Real VideoView decoder error, private disposable cache; never injects onError or a timer. */
final class CorruptSaverVideoProbe {
    private static Field field(String name)throws Exception {Field f=ScreensaverActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    static void run(Instrumentation test,Bundle result)throws Exception {
        Context c=test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(c.getPackageName()),"Disposable validation package only");
        android.content.SharedPreferences prefs=Screensaver.prefs(c);Map<String,?> saved=new HashMap<>(prefs.getAll());
        Field singleton=SaverCache.class.getDeclaredField("instance");singleton.setAccessible(true);
        SaverCache previous; synchronized(SaverCache.class){previous=(SaverCache)singleton.get(null);}
        File root=new File(c.getCacheDir(),"corrupt-video-probe-"+java.util.UUID.randomUUID());require(root.mkdir(),"Fresh private cache root");
        SaverCache fixture=new SaverCache(root,1024*1024,0,()->Long.MAX_VALUE);
        Activity screen=null;Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("saverOnline",false).putBoolean("animations",false).commit(),"Disable only app-local network/motion");
            synchronized(SaverCache.class){singleton.set(null,fixture);}
            screen=test.startActivitySync(new Intent(c,ScreensaverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity saver=screen;
            ExecutorService decoder=(ExecutorService)field("decoder").get(saver);decoder.submit(()->{}).get(15,TimeUnit.SECONDS);test.waitForIdleSync();
            SaverMedia media=new SaverMedia("https://images-assets.nasa.gov/corrupt-probe-"+java.util.UUID.randomUUID()+".mp4","Corrupt probe","Test fixture","https://example.invalid/source","Test fixture","video");
            try(FileOutputStream out=new FileOutputStream(fixture.file(media))){out.write("Not a video container. Real decoder must reject this nonempty cached file.".getBytes(StandardCharsets.US_ASCII));}
            try(FileOutputStream out=new FileOutputStream(new File(root,media.id+".json"))){out.write(media.json().toString().getBytes(StandardCharsets.UTF_8));}
            require(fixture.hasComplete(media),"Valid metadata/nonempty file must reach decoder");
            ObservedVideo[] observed={null};
            SettingsStructureProbe.main(test,()->{
                VideoView old=(VideoView)field("video").get(saver);old.stopPlayback();
                ViewGroup parent=(ViewGroup)old.getParent();int position=parent.indexOfChild(old);ViewGroup.LayoutParams size=old.getLayoutParams();parent.removeView(old);
                ObservedVideo video=new ObservedVideo(saver);video.setId(R.id.saver_video);video.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);parent.addView(video,position,size);field("video").set(saver,video);observed[0]=video;
                ((ImageView)field("first").get(saver)).setImageDrawable(null);
                Method rebuild=ScreensaverActivity.class.getDeclaredMethod("rebuild");rebuild.setAccessible(true);rebuild.invoke(saver);
                field("index").setInt(saver,0);
                Method show=ScreensaverActivity.class.getDeclaredMethod("showSlide");show.setAccessible(true);show.invoke(saver);
                SaverMedia pinned=(SaverMedia)field("pinnedVideo").get(saver);require(pinned!=null&&media.id.equals(pinned.id),"Actual corrupt video was not acquired");
            });
            long start=SystemClock.elapsedRealtime();boolean[] fallback={false};
            for(int i=0;i<80&&!fallback[0]&&SystemClock.elapsedRealtime()-start<8000;i++) {
                SettingsStructureProbe.main(test,()->{
                    ImageView image=(ImageView)field("first").get(saver);
                    fallback[0]=observed[0].errors>0&&field("pinnedVideo").get(saver)==null&&observed[0].getVisibility()==View.GONE
                            &&image.getDrawable() instanceof android.graphics.drawable.BitmapDrawable
                            &&((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap().getWidth()>100;
                });
                if(!fallback[0])SystemClock.sleep(100);
            }
            require(fallback[0],"Real corrupt-video error did not reach decoded offline fallback within8s (timeout is10s)");
            long fallbackMs=SystemClock.elapsedRealtime()-start;require(fallbackMs<9000,"Fallback was not earlier than production timeout");
            Field pins=SaverCache.class.getDeclaredField("pinned");pins.setAccessible(true);
            synchronized(fixture){require(!((Map<?,?>)pins.get(fixture)).containsKey(media.id),"Corrupt video pin retained");}
            require(!fixture.hasComplete(media) && !fixture.file(media).exists()
                    && !new File(root, media.id + ".json").exists(), "Rejected video remained eligible in the cache");
            test.setInTouchMode(false);test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test,()->saver.isFinishing()||saver.isDestroyed(),"Back did not dismiss fallback");
            result.putString("saver_corrupt_video","PASS: actual framework decoder error, offline photo fallback, rejected video pair removed and Back dismissal");
            result.putInt("saver_corrupt_video_errors",observed[0].errors);result.putLong("saver_corrupt_video_fallback_ms",fallbackMs);
            result.putString("saver_corrupt_video_scope","Observed VideoView delegates real framework error to production; covers a corrupt container");
        }catch(Exception|Error error){primary=error;throw error;}
        finally {
            try {
                if(screen!=null){final Activity saver=screen;SettingsStructureProbe.main(test,()->{if(!saver.isDestroyed())saver.finish();});
                    SettingsStructureProbe.await(test,saver::isDestroyed,"Saver teardown pending");
                    ExecutorService decoder=(ExecutorService)field("decoder").get(saver);
                    require(decoder.awaitTermination(15,TimeUnit.SECONDS),"Decoder not quiescent");}
                synchronized(fixture){Field pins=SaverCache.class.getDeclaredField("pinned");pins.setAccessible(true);require(((Map<?,?>)pins.get(fixture)).isEmpty(),"Fixture pins remain");}
                synchronized(SaverCache.class){singleton.set(null,previous);}
                SettingsStructureProbe.restore(prefs,saved);
                File[] files=root.listFiles();require(files!=null,"Fixture root unavailable");for(File f:files)require(f.isFile()&&f.delete(),"Fixture file cleanup failed");require(root.delete(),"Fixture root cleanup failed");
            }catch(Exception|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    private static final class ObservedVideo extends VideoView {
        volatile int errors;
        ObservedVideo(Context c){super(c);}
        @Override public void setOnErrorListener(MediaPlayer.OnErrorListener listener){super.setOnErrorListener(listener==null?null:(player,what,extra)->{errors++;return listener.onError(player,what,extra);});}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Silent cached media and offline fallback; timers, decoding and transfers stop on pause. */
public final class ScreensaverActivity extends Activity {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final List<Slide> slides=new ArrayList<>();
    private AtomicBoolean networkActive;
    private ImageView first,second;
    private VideoView video;
    private TextClock clock;
    private TextView credit;
    private SaverCache cache;
    private SaverMedia pinnedVideo,pinnedPhoto;
    private int index;
    private volatile int ticket;
    private volatile boolean resumed;
    private boolean videoVisible;
    private final Runnable advance=()->{index=(index+1)%slides.size();showSlide();};
    private final Runnable videoTimeout=()->{if(resumed&&!videoVisible) advance.run();};
    private static final class Slide {
        final String key;final int resource;final SaverMedia media;
        Slide(WallpaperCatalog.Entry entry) {key="offline:"+entry.key;resource=entry.photo;media=null;}
        Slide(SaverMedia item) {key=item.id;resource=0;media=item;}
    }
    @Override protected void attachBaseContext(Context base) {super.attachBaseContext(AppLanguage.wrap(base));}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);cache=SaverCache.get(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        video=new VideoView(this);video.setId(R.id.saver_video);video.setAudioFocusRequest(android.media.AudioManager.AUDIOFOCUS_NONE);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));video.setVisibility(View.GONE);
        first=image();second=image();second.setAlpha(0f);root.addView(first);root.addView(second);
        // Retain a small offline image until the first full photo or video frame is ready.
        first.setImageResource(R.drawable.wallpaper_alpine_thumb);
        Typeface font=Typeface.DEFAULT;
        try {font=new Typeface.Builder(getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build();}catch(RuntimeException ignored) { }
        clock=new TextClock(this);clock.setId(R.id.saver_clock);clock.setTextColor(Color.WHITE);clock.setTextSize(36);clock.setTypeface(font);
        clock.setFormat24Hour("HH:mm");clock.setFormat12Hour("HH:mm");clock.setShadowLayer(12,0,2,0xaa000000);root.addView(clock);
        credit=new TextView(this);credit.setId(R.id.saver_credit);credit.setTextColor(Color.WHITE);credit.setTypeface(font);credit.setTextSize(12);credit.setMaxLines(2);
        credit.setShadowLayer(8,0,1,Color.BLACK);credit.setBackgroundColor(0x55000000);credit.setPadding(dp(8),dp(5),dp(8),dp(5));
        FrameLayout.LayoutParams caption=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);caption.setMargins(dp(32),0,dp(32),dp(24));root.addView(credit,caption);credit.setVisibility(View.GONE);
        setContentView(root);rebuild();positionClock(false);transition();
    }
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
    private ImageView image() {ImageView v=new ImageView(this);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));return v;}
    private void rebuild() {
        String current=slides.isEmpty()?"":slides.get(index).key;slides.clear();
        for(SaverMedia item:cache.entries()) slides.add(new Slide(item));
        String selected=Screensaver.prefs(this).getString("wallpaper","alpine");
        List<WallpaperCatalog.Entry> offline=WallpaperCatalog.entries(this);
        for(WallpaperCatalog.Entry e:offline) if(e.photo!=0&&e.key.equals(selected)) slides.add(new Slide(e));
        for(WallpaperCatalog.Entry e:offline) if(e.photo!=0&&!e.key.equals(selected)) slides.add(new Slide(e));
        index=0;for(int i=0;i<slides.size();i++) if(slides.get(i).key.equals(current)) index=i;
    }
    @Override protected void onResume() {
        super.onResume();resumed=true;rebuild();showSlide();networkActive=new AtomicBoolean(true);AtomicBoolean session=networkActive;
        boolean hadRemote=!cache.entries().isEmpty();
        SaverSync.start(this,session::get,()->runOnUiThread(()->{
            if(!resumed||!session.get()) return;
            rebuild();if(!hadRemote&&!videoVisible&&pinnedVideo==null) {index=slides.size()-1;timer.removeCallbacks(advance);timer.postDelayed(advance,2000);}
        }));
    }
    private void showSlide() {
        timer.removeCallbacks(advance);timer.removeCallbacks(videoTimeout);
        if(!resumed||slides.isEmpty()) return;
        stopVideo();Slide slide=slides.get(index);final int request=++ticket;
        if(slide.media!=null&&slide.media.kind.equals("video")) {showVideo(slide.media,request);return;}
        decoder.execute(()->{
            if(!resumed||request!=ticket) return;
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            File file=null;Bitmap photo=null;
            try {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inSampleSize=1;options.inPreferredConfig=Bitmap.Config.RGB_565;
                if(slide.media==null) photo=BitmapFactory.decodeResource(getResources(),slide.resource,options);
                else {
                    file=cache.acquire(slide.media);
                    if(file!=null) {
                        options.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),options);
                        while(options.outWidth/options.inSampleSize>2560||options.outHeight/options.inSampleSize>1600) options.inSampleSize*=2;
                        options.inJustDecodeBounds=false;photo=BitmapFactory.decodeFile(file.getAbsolutePath(),options);
                    }
                }
            } catch(RuntimeException badMedia) { }
            finally {if(file!=null&&photo==null) cache.release(slide.media);}
            Bitmap ready=photo;SaverMedia decodedMedia=file!=null&&photo!=null?slide.media:null;
            runOnUiThread(()->{
                if(!resumed||request!=ticket||isDestroyed()) {if(ready!=null) ready.recycle();cache.release(decodedMedia);return;}
                if(ready!=null) {
                    cache.release(pinnedPhoto);pinnedPhoto=decodedMedia;
                    second.animate().cancel();first.animate().cancel();second.setImageDrawable(new BitmapDrawable(getResources(),ready));second.setAlpha(0f);
                    if(motion()) second.animate().alpha(1f).setDuration(800).setInterpolator(new android.view.animation.PathInterpolator(.22f,.61f,.36f,1f)).withEndAction(this::finishSwap).start();
                    else {second.setAlpha(1f);finishSwap();}
                    showCredit(slide.media);timer.postDelayed(advance,60000);
                } else timer.postDelayed(advance,500);
            });
        });
    }
    private void showVideo(SaverMedia item,int request) {
        File file=cache.acquire(item);if(file==null) {timer.postDelayed(advance,500);return;}
        pinnedVideo=item;videoVisible=false;video.setVisibility(View.VISIBLE);
        video.setOnPreparedListener(player->{if(!resumed||request!=ticket)return;player.setVolume(0,0);player.setScreenOnWhilePlaying(false);video.start();});
        video.setOnInfoListener((player,what,extra)->{
            if(what==android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START&&resumed&&request==ticket) {
                cache.release(pinnedPhoto);pinnedPhoto=null;
                videoVisible=true;first.animate().cancel();second.animate().cancel();first.setAlpha(0f);second.setAlpha(0f);second.setImageDrawable(null);
                showCredit(item);timer.removeCallbacks(videoTimeout);timer.postDelayed(advance,180000);
            }return false;
        });
        video.setOnCompletionListener(player->{if(resumed&&request==ticket)advance.run();});
        video.setOnErrorListener((player,what,extra)->{if(resumed&&request==ticket)timer.postDelayed(advance,500);return true;});
        video.setVideoPath(file.getAbsolutePath());timer.postDelayed(videoTimeout,10000);
    }
    private void stopVideo() {
        videoVisible=false;video.setOnPreparedListener(null);video.setOnCompletionListener(null);video.setOnErrorListener(null);video.setOnInfoListener(null);
        video.stopPlayback();video.setVisibility(View.GONE);cache.release(pinnedVideo);pinnedVideo=null;first.setAlpha(1f);
    }
    private void showCredit(SaverMedia item) {
        credit.setText(item==null?"":item.displayTitle()+" · "+item.credit);credit.setVisibility(item==null?View.GONE:View.VISIBLE);positionClock(item!=null);
    }
    private void positionClock(boolean remote) {
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,(remote||index%2!=0)?Gravity.TOP|Gravity.END:Gravity.BOTTOM|Gravity.START);
        p.setMargins(dp(32),dp(32),dp(32),dp(32));clock.setLayoutParams(p);
    }
    private void finishSwap() {first.setImageDrawable(second.getDrawable());first.setAlpha(1f);second.setAlpha(0f);second.setImageDrawable(null);}
    @Override protected void onPause() {
        resumed=false;++ticket;if(networkActive!=null) networkActive.set(false);timer.removeCallbacks(advance);timer.removeCallbacks(videoTimeout);
        first.animate().cancel();second.animate().cancel();stopVideo();cache.release(pinnedPhoto);pinnedPhoto=null;super.onPause();
    }
    @Override protected void onDestroy() {decoder.shutdownNow();first.setImageDrawable(null);second.setImageDrawable(null);super.onDestroy();}
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_VOLUME_UP||key==KeyEvent.KEYCODE_VOLUME_DOWN||key==KeyEvent.KEYCODE_VOLUME_MUTE)return super.dispatchKeyEvent(event);
        if(event.getAction()==KeyEvent.ACTION_UP)finish();return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {if(event.getAction()==MotionEvent.ACTION_UP)finish();return true;}
    private boolean motion() {return Screensaver.prefs(this).getBoolean("animations",true)&&android.animation.ValueAnimator.areAnimatorsEnabled();}
    private void transition() {overridePendingTransition(motion()?android.R.anim.fade_in:0,motion()?android.R.anim.fade_out:0);}
    @Override public void finish() {super.finish();transition();}
}

package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.VideoView;
import android.widget.TextView;
import java.util.List;

/** Hardware playback and photo decode using real downloaded cache, not mock media. */
final class OnlinePlaybackProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        test.setInTouchMode(false);
        android.content.SharedPreferences prefs=Screensaver.prefs(test.getTargetContext());
        boolean had=prefs.contains("saverOnline"),previous=prefs.getBoolean("saverOnline",true);prefs.edit().putBoolean("saverOnline",false).commit();
        Activity screen=test.startActivitySync(new Intent(test.getTargetContext(),ScreensaverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            SystemClock.sleep(1000);
            java.lang.reflect.Field slides=ScreensaverActivity.class.getDeclaredField("slides"),index=ScreensaverActivity.class.getDeclaredField("index"),visible=ScreensaverActivity.class.getDeclaredField("videoVisible");
            java.lang.reflect.Method show=ScreensaverActivity.class.getDeclaredMethod("showSlide");
            slides.setAccessible(true);index.setAccessible(true);visible.setAccessible(true);show.setAccessible(true);
            java.util.List<String> targets=new java.util.ArrayList<>();
            test.runOnMainSync(()->{try {
                List<?> list=(List<?>)slides.get(screen);String image="";
                for(Object slide:list) {java.lang.reflect.Field media=slide.getClass().getDeclaredField("media");media.setAccessible(true);SaverMedia item=(SaverMedia)media.get(slide);
                    if(item!=null) {if(item.kind.equals("video"))targets.add(item.id);else if(image.isEmpty())image=item.id;}}
                if(!image.isEmpty()) targets.add(image);
            }catch(Exception e){throw new RuntimeException(e);}});
            require(targets.size()>=2,"Cached photo and video available");
            for(String target:targets) {
                SaverMedia[] selected={null};
                test.runOnMainSync(()->{try {
                    List<?> list=(List<?>)slides.get(screen);
                    for(int i=0;i<list.size();i++) {
                        java.lang.reflect.Field media=list.get(i).getClass().getDeclaredField("media");media.setAccessible(true);SaverMedia item=(SaverMedia)media.get(list.get(i));
                        if(item!=null&&item.id.equals(target)){index.setInt(screen,i);selected[0]=item;show.invoke(screen);break;}
                    }
                }catch(Exception e){throw new RuntimeException(e);}});
                require(selected[0]!=null,"Downloaded item exists");String kind=selected[0].kind;
                boolean[] ready={false};
                for(int retry=0;retry<100&&!ready[0];retry++) {
                    test.runOnMainSync(()->{try {
                        ready[0]=kind.equals("video")?visible.getBoolean(screen):((TextView)screen.findViewById(R.id.saver_credit)).getText().toString().contains(selected[0].credit);
                    }catch(Exception e){throw new RuntimeException(e);}});
                    if(!ready[0])SystemClock.sleep(100);
                }
                require(ready[0],"Real "+kind+" rendered with credit");
                if(kind.equals("video")) {
                    int[] position={0,0};test.runOnMainSync(()->position[0]=((VideoView)screen.findViewById(R.id.saver_video)).getCurrentPosition());
                    SystemClock.sleep(500);test.runOnMainSync(()->position[1]=((VideoView)screen.findViewById(R.id.saver_video)).getCurrentPosition());
                    require(position[1]>position[0],"Video playback advances");
                    android.graphics.Bitmap shot=test.getUiAutomation().takeScreenshot();
                    if(shot!=null) {try(java.io.FileOutputStream output=test.getTargetContext().openFileOutput("saver-probe-video.png",0)){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}shot.recycle();}
                }
            }
            result.putString("online_playback","PASS: "+targets.size()+" cached items render offline; all cached videos advance; photo credited (network downloads disabled)");
        } finally {test.runOnMainSync(screen::finish);android.content.SharedPreferences.Editor edit=prefs.edit();if(had)edit.putBoolean("saverOnline",previous);else edit.remove("saverOnline");edit.commit();}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import java.lang.reflect.Field;

/** Device-only gallery, async-loading and persistence checks. */
final class WallpaperProbe {
    static void run(Instrumentation probe, Bundle result) throws Exception {
        android.content.Context context=probe.getTargetContext();
        android.content.SharedPreferences prefs=context.getSharedPreferences(MainActivity.class.getName(),0);
        String original=prefs.getString("wallpaper",null);
        boolean originalWelcome=prefs.getBoolean("welcome_seen",false);
        if(!context.getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Wallpaper probe requires the isolated preview package");
        prefs.edit().putBoolean("welcome_seen",true).putString("wallpaper","blue").commit();
        probe.setInTouchMode(false);
        Activity screen=probe.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            SystemClock.sleep(1200);
            probe.runOnMainSync(() -> screen.findViewById(R.id.nav_settings).requestFocus());
            SystemClock.sleep(500);
            probe.runOnMainSync(() -> {
                android.widget.HorizontalScrollView gallery = screen.findViewById(R.id.wallpaper_scroll);
                View active = screen.findViewById(R.id.wallpaper_blue);
                View card = (View) active.getParent();
                if (!active.isSelected() || card.getLeft() < gallery.getScrollX()
                        || card.getRight() > gallery.getScrollX() + gallery.getWidth())
                    throw new AssertionError("Selected wallpaper is not fully visible in the gallery");
            });
            java.util.List<WallpaperCatalog.Entry> entries=WallpaperCatalog.entries(context);
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(200);
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(200);
            if (screen.getCurrentFocus() == null || screen.getCurrentFocus().getId() != R.id.wallpaper_blue)
                throw new AssertionError("Gallery entry did not focus the selected wallpaper");
            for (int i=0;i<3;i++) { probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(120); }
            for(int i=0;i<entries.size();i++) {
                WallpaperCatalog.Entry entry=entries.get(i);
                if(i>0) { probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(120); }
                int[] focus={View.NO_ID};
                probe.runOnMainSync(() -> {if(screen.getCurrentFocus()!=null) focus[0]=screen.getCurrentFocus().getId();});
                if(focus[0]!=entry.viewId) throw new AssertionError("Gallery focus: "+entry.key+", actual="+focus[0]);
                probe.runOnMainSync(() -> screen.findViewById(entry.viewId).performClick());
                awaitLoaded(probe,screen,entry.key);
                if(!entry.key.equals(prefs.getString("wallpaper",""))) throw new AssertionError("Preference: "+entry.key);
                probe.runOnMainSync(() -> { if(!screen.findViewById(entry.viewId).isSelected()) throw new AssertionError("Selection: "+entry.key); });
            }
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); SystemClock.sleep(150);
            probe.runOnMainSync(() -> { if(screen.getCurrentFocus().getId()!=R.id.dynamic_wallpapers) throw new AssertionError("Down to dynamic mode"); });
            // In-flight results must never replace the user's last choice.
            probe.runOnMainSync(() -> {
                for(String key:new String[]{"alpine","aurora","como","alpine","aurora"})
                    screen.findViewById(WallpaperCatalog.find(context,key).viewId).performClick();
            });
            awaitLoaded(probe,screen,"aurora");
            screen.finish(); SystemClock.sleep(300);
            Activity reopened=probe.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitLoaded(probe,reopened,"aurora");
            result.putString("wallpapers","PASS: "+entries.size()+" choices, D-pad scrolling, active marker, loading, rapid switches and restart persistence");
            result.putLong("native_heap_bytes",android.os.Debug.getNativeHeapAllocatedSize());
        } finally {
            android.content.SharedPreferences.Editor edit=prefs.edit(); if(original==null) edit.remove("wallpaper"); else edit.putString("wallpaper",original); edit.putBoolean("welcome_seen",originalWelcome).commit();
        }
    }
    private static void awaitLoaded(Instrumentation probe,Activity activity,String key) throws Exception {
        Field field=MainActivity.class.getDeclaredField("loadedWallpaper");field.setAccessible(true);
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end) {
            boolean[] ready={false};probe.runOnMainSync(() -> {try {ready[0]=key.equals(field.get(activity));}catch(Exception error){throw new AssertionError(error);}});
            if(ready[0]) return;SystemClock.sleep(60);
        }
        throw new AssertionError("Wallpaper did not finish: "+key);
    }
}

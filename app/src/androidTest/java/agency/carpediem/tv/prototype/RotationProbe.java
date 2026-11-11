package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Device-only synthetic-clock boundaries plus a real Activity pause/resume check. */
@android.annotation.TargetApi(29)
final class RotationProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        Context context=test.getTargetContext();
        SharedPreferences p=context.getSharedPreferences("rotationProbe",0);
        long now=1_000_000_000L, interval=WallpaperRotation.INTERVAL_MS;
        p.edit().clear().putString("wallpaper","alpine").commit();
        require(!WallpaperRotation.advanceIfDue(context,p,now),"disabled mode");
        p.edit().putBoolean(WallpaperRotation.ENABLED,true).putLong(WallpaperRotation.CHANGED_AT,now).commit();
        require(!WallpaperRotation.advanceIfDue(context,p,now+interval-1),"no early rotation");
        require(WallpaperRotation.advanceIfDue(context,p,now+interval),"exact 30-minute boundary");
        require("aurora".equals(p.getString("wallpaper","")),"next photo");
        require(!WallpaperRotation.advanceIfDue(context,p,now+interval),"no duplicate advance");
        require(WallpaperRotation.advanceIfDue(context,p,now+10*interval),"resume after a long gap");
        require(!WallpaperRotation.advanceIfDue(context,p,now+10*interval),"no catch-up burst");
        require(!WallpaperRotation.advanceIfDue(context,p,now),"clock rollback");
        require(WallpaperRotation.delay(p,now)==interval,"fresh interval after clock rollback");
        p.edit().putString("wallpaper","alpine").putLong(WallpaperRotation.CHANGED_AT,now).commit();
        Set<String> seen=new HashSet<>(); int photos=0;
        for(WallpaperCatalog.Entry entry:WallpaperCatalog.entries(context)) if(entry.photo!=0) photos++;
        for(int i=1;i<=photos;i++) {
            require(WallpaperRotation.advanceIfDue(context,p,now+i*interval),"cycle boundary");
            String key=p.getString("wallpaper",""); require(WallpaperCatalog.find(context,key).photo!=0,"no flat backgrounds");seen.add(key);
        }
        require(seen.size()==photos,"all photos in cycle");
        context.deleteSharedPreferences("rotationProbe");

        SharedPreferences live=context.getSharedPreferences("MainActivity",0);Map<String,?> before=live.getAll();
        Activity screen=null;
        try {
            live.edit().putBoolean(WallpaperRotation.ENABLED,true).putString("wallpaper","alpine").putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()).commit();
            test.setInTouchMode(false);
            screen=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1000);
            Handler handler=(Handler)field(screen,"wallpaperTimer");Runnable tick=(Runnable)field(screen,"wallpaperRotationTick");
            require(handler.hasCallbacks(tick),"one foreground timer scheduled");
            Activity active=screen;
            test.runOnMainSync(() -> active.startActivity(new Intent(Settings.ACTION_SETTINGS)));
            SystemClock.sleep(800);
            require(!handler.hasCallbacks(tick),"no timer while another app is foreground");
            live.edit().putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()-interval-100).commit();
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(1200);
            require("aurora".equals(live.getString("wallpaper","")),"overdue rotation on return");
            require(handler.hasCallbacks(tick),"timer resumed");
            live.edit().putBoolean(WallpaperRotation.ENABLED,false).commit();
            test.runOnMainSync(tick);require(!handler.hasCallbacks(tick),"off cancels timer");
            result.putString("rotation","PASS: exact 30m boundary, persistence, full photo cycle, no flat backgrounds, no catch-up burst, clock rollback, background cancellation and resume");
        } finally {
            SharedPreferences.Editor edit=live.edit();
            for(String key:new String[]{"wallpaper",WallpaperRotation.ENABLED,WallpaperRotation.CHANGED_AT}) {
                Object value=before.get(key);if(value==null)edit.remove(key);else if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
            }
            edit.commit();
        }
    }
    private static Object field(Object target,String name) throws Exception {Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}

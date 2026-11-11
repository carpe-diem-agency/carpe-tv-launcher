package agency.carpediem.tv.prototype;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Persistent interval bookkeeping. No alarms, polling, service or background wakeups. */
final class WallpaperRotation {
    static final long INTERVAL_MS=30L*60*1000;
    static final String ENABLED="dynamicWallpapers", CHANGED_AT="wallpaperChangedAt";
    static boolean advanceIfDue(Context context,SharedPreferences prefs,long now) {
        if(!prefs.getBoolean(ENABLED,false)) return false;
        long changed=prefs.getLong(CHANGED_AT,0);
        if(changed<=0 || now<changed) {prefs.edit().putLong(CHANGED_AT,now).apply();return false;}
        if(now-changed<INTERVAL_MS) return false;
        List<WallpaperCatalog.Entry> photos=new ArrayList<>();
        for(WallpaperCatalog.Entry entry:WallpaperCatalog.entries(context)) if(entry.photo!=0) photos.add(entry);
        if(photos.size()<2) {prefs.edit().putLong(CHANGED_AT,now).apply();return false;}
        String current=WallpaperCatalog.find(context,prefs.getString("wallpaper","alpine")).key;
        int index=-1;
        for(int i=0;i<photos.size();i++) if(photos.get(i).key.equals(current)) index=i;
        prefs.edit().putString("wallpaper",photos.get((index+1)%photos.size()).key).putLong(CHANGED_AT,now).apply();
        return true;
    }
    static long delay(SharedPreferences prefs,long now) {
        long changed=prefs.getLong(CHANGED_AT,now);
        if(now<changed) return INTERVAL_MS;
        return Math.max(1,INTERVAL_MS-Math.min(INTERVAL_MS,now-changed));
    }
}

package agency.carpediem.tv.prototype;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.LinkedHashMap;
import java.util.Map;

/** Package state is observable; it is deliberately not presented as running-process state. */
final class AppAudit {
    static final String[] PACKAGES={"com.miui.tv.analytics","com.xiaomi.statistic","com.mitv.tvhome.mitvplus","com.google.android.apps.tv.launcherx"};
    static final class Snapshot {
        final Map<String,Integer> states=new LinkedHashMap<>(); // 0 unavailable, 1 enabled, 2 disabled
    }
    static Snapshot read(Context context) {
        Snapshot result=new Snapshot();PackageManager pm=context.getPackageManager();
        for(String pkg:PACKAGES) {
            int state=0;
            try {
                ApplicationInfo app=pm.getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS);
                state=app.enabled?1:2;
            } catch(PackageManager.NameNotFoundException|SecurityException unavailable) { /* No claim of absence when visibility is restricted. */ }
            result.states.put(pkg,state);
        }
        return result;
    }
}

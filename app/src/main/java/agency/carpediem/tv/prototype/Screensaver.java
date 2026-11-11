package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.widget.Toast;

/** Optional external provider; built-in offline slideshow is always available. */
final class Screensaver {
    static final String TIMEOUT = "screensaverMinutes", PROVIDER = "screensaverProvider";
    static final int DEFAULT_MINUTES = 5;
    static final int[] MINUTES = {0, 1, 3, 5, 10, 15, 30};
    static final ComponentName AERIAL = new ComponentName("com.neilturner.aerialviews", "com.neilturner.aerialviews.ui.screensaver.TestActivity");
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("MainActivity", Context.MODE_PRIVATE); }
    static int minutes(Context c) {
        int value = prefs(c).getInt(TIMEOUT, DEFAULT_MINUTES);
        for (int allowed : MINUTES) if (value == allowed) return value;
        return DEFAULT_MINUTES;
    }
    static boolean aerialAvailable(Context c) {
        return available(c, AERIAL);
    }
    private static boolean available(Context c, ComponentName provider) {
        try { return c.getPackageManager().getActivityInfo(provider, 0).exported; }
        catch (PackageManager.NameNotFoundException | SecurityException error) { return false; }
    }
    static boolean tryExternal(Activity activity, ComponentName provider) {
        try {
            if (available(activity, provider)) { activity.startActivity(new Intent().setComponent(provider)); return true; }
        } catch (RuntimeException unavailable) { /* An update/uninstall can race the availability check. */ }
        return false;
    }
    static void launch(Activity activity) {
        if ("aerial".equals(prefs(activity).getString(PROVIDER, "builtin"))) {
            if (tryExternal(activity, AERIAL)) return;
            Toast.makeText(activity, R.string.saver_aerial_fallback, Toast.LENGTH_LONG).show();
        }
        activity.startActivity(new Intent(activity, ScreensaverActivity.class));
    }
}

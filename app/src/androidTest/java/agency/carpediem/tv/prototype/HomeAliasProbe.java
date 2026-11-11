package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

/** A Home alias launch must read the same saved last-app tile as the launcher icon. */
final class HomeAliasProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        String packageName = test.getTargetContext().getPackageName();
        if (!packageName.endsWith(".publicpreview"))
            throw new IllegalStateException("Home alias probe requires the isolated preview package");

        PackageManager pm = test.getTargetContext().getPackageManager();
        ComponentName alias = new ComponentName(packageName,
                MainActivity.class.getPackage().getName() + ".HomeEntry");
        int originalState = pm.getComponentEnabledSetting(alias);
        SharedPreferences primary = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadLast = primary.contains("last_opened_package");
        String previousLast = primary.getString("last_opened_package", null);
        boolean hadWelcome = primary.contains("welcome_seen");
        boolean previousWelcome = primary.getBoolean("welcome_seen", false);
        String expected = "com.google.android.youtube.tv";
        try {
            primary.edit().putString("last_opened_package", expected)
                    .putBoolean("welcome_seen", true).commit();
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(alias).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            MainActivity screen = (MainActivity) test.startActivitySync(home);
            SystemClock.sleep(1200);
            test.runOnMainSync(() -> {
                View focused = screen.getCurrentFocus();
                if (focused == null || !expected.equals(focused.getTag()))
                    throw new AssertionError("Home alias lost the last opened application focus");
            });
            result.putString("home_alias", "PASS: Home alias restores the saved YouTube tile");
        } finally {
            SharedPreferences.Editor restore = primary.edit();
            if (hadLast) restore.putString("last_opened_package", previousLast);
            else restore.remove("last_opened_package");
            if (hadWelcome) restore.putBoolean("welcome_seen", previousWelcome);
            else restore.remove("welcome_seen");
            restore.commit();
            pm.setComponentEnabledSetting(alias, originalState, PackageManager.DONT_KILL_APP);
        }
    }

    private HomeAliasProbe() { }
}

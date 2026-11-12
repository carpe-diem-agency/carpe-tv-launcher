package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import java.util.concurrent.atomic.AtomicReference;

/** Checks the three settings focus regions without changing device settings. */
final class SettingsFocusProbe {
    private SettingsFocusProbe() { }

    static void run(Instrumentation probe, Bundle result) {
        SharedPreferences prefs = probe.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = prefs.contains("welcome_seen");
        boolean welcome = prefs.getBoolean("welcome_seen", false);
        prefs.edit().putBoolean("welcome_seen", true).commit();
        try {
            probe.setInTouchMode(false);
            MainActivity screen = (MainActivity) probe.startActivitySync(
                    new Intent(probe.getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(300);
            probe.runOnMainSync(() -> screen.findViewById(R.id.nav_settings).requestFocus());
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            requireRegion(screen, R.id.settings_categories);
            View selectedCategory = screen.getCurrentFocus();
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            if (screen.getCurrentFocus() != selectedCategory)
                throw new AssertionError("Up escaped the first settings category");
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            requireRegion(screen, R.id.settings_detail);
            View selectedDetail = screen.getCurrentFocus();
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            if (screen.getCurrentFocus() != screen.findViewById(R.id.settings_power))
                throw new AssertionError("Up from the first detail did not reach the header");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() != selectedDetail)
                throw new AssertionError("Down from the header did not restore the detail focus");
            key(probe, KeyEvent.KEYCODE_BACK);
            if (screen.getCurrentFocus() != selectedCategory)
                throw new AssertionError("Back did not restore the selected settings category");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            requireRegion(screen, R.id.settings_categories);
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            requireRegion(screen, R.id.settings_detail);
            key(probe, KeyEvent.KEYCODE_DPAD_LEFT);
            requireRegion(screen, R.id.settings_categories);
            key(probe, KeyEvent.KEYCODE_DPAD_LEFT);
            if (screen.getCurrentFocus() != screen.findViewById(R.id.nav_settings))
                throw new AssertionError("Left from settings categories did not reach the rail");
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            requireRegion(screen, R.id.settings_categories);
            key(probe, KeyEvent.KEYCODE_BACK);
            if (!screen.findViewById(R.id.nav_home).isSelected()
                    || screen.findViewById(R.id.nav_settings).isSelected())
                throw new AssertionError("Back from settings categories did not reach Home");
            probe.runOnMainSync(() -> {
                screen.findViewById(R.id.nav_settings).requestFocus();
                View category = ((android.view.ViewGroup) screen.findViewById(R.id.settings_categories)).getChildAt(1);
                category.performClick();
            });
            requireRegion(screen, R.id.settings_detail);
            android.view.ViewGroup categories = screen.findViewById(R.id.settings_categories);
            View selectedCategoryDirectly = categories.getChildAt(1);
            if (categories.getChildAt(0).isSelected() || !selectedCategoryDirectly.isSelected())
                throw new AssertionError("Direct category activation did not update selection");
            key(probe, KeyEvent.KEYCODE_BACK);
            if (screen.getCurrentFocus() != selectedCategoryDirectly)
                throw new AssertionError("Back did not restore a category activated without D-pad focus");
            AtomicReference<MainActivity> recreated = new AtomicReference<>();
            Application application=screen.getApplication();
            Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity activity, Bundle state) {
                    if (activity instanceof MainActivity && activity != screen) recreated.set((MainActivity)activity);
                }
                @Override public void onActivityStarted(Activity activity) { }
                @Override public void onActivityResumed(Activity activity) { }
                @Override public void onActivityPaused(Activity activity) { }
                @Override public void onActivityStopped(Activity activity) { }
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                @Override public void onActivityDestroyed(Activity activity) { }
            };
            application.registerActivityLifecycleCallbacks(callbacks);
            try {
                probe.runOnMainSync(() -> {
                    View source=screen.findViewById(R.id.saver_provider);
                    if (source == null || !source.requestFocus())
                        throw new AssertionError("Cannot focus the screensaver source before recreation");
                    screen.recreate();
                });
                for (int i=0;i<40 && recreated.get()==null;i++) SystemClock.sleep(100);
                MainActivity after=recreated.get();
                if (after==null) throw new AssertionError("Settings Activity did not recreate");
                probe.waitForIdleSync();
                probe.runOnMainSync(() -> {
                    if (!after.findViewById(R.id.nav_settings).isSelected()
                            || !after.findViewById(R.id.saver_provider).isShown()
                            || after.getCurrentFocus()!=after.findViewById(R.id.saver_provider))
                        throw new AssertionError("Recreation lost screensaver source focus");
                });
            } finally { application.unregisterActivityLifecycleCallbacks(callbacks); }
            result.putString("settings_focus", "PASS: rail, category boundary, detail/header round trip, Back and Left routes, direct activation and recreation");
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadWelcome) restore.putBoolean("welcome_seen", welcome);
            else restore.remove("welcome_seen");
            restore.commit();
        }
    }

    private static void key(Instrumentation probe, int code) {
        probe.sendKeyDownUpSync(code);
        SystemClock.sleep(180);
    }

    private static void requireRegion(MainActivity screen, int id) {
        View target = screen.findViewById(id);
        View focused = screen.getCurrentFocus();
        for (View node = focused; node != null; ) {
            if (node == target) return;
            ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        throw new AssertionError("Focus is outside region " + id + ": " + focused);
    }
}

package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/** A language-triggered recreation must keep Settings even after an app was opened. */
final class LanguageStateProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        android.content.SharedPreferences launcherPrefs = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = launcherPrefs.contains("welcome_seen");
        boolean previousWelcome = launcherPrefs.getBoolean("welcome_seen", false);
        launcherPrefs.edit().putBoolean("welcome_seen", true).commit();
        MainActivity original = (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        boolean hadLast = launcherPrefs.contains("last_opened_package");
        String previous = launcherPrefs.getString("last_opened_package", null);
        AtomicReference<MainActivity> recreated = new AtomicReference<>();
        Application application = original.getApplication();
        Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity && activity != original) recreated.set((MainActivity) activity);
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
            test.runOnMainSync(() -> {
                launcherPrefs.edit().putString("last_opened_package", "com.google.android.youtube.tv").commit();
                original.findViewById(R.id.nav_settings).requestFocus();
            });
            SystemClock.sleep(350);
            test.runOnMainSync(() -> {
                View choice = original.findViewById(R.id.language_choice);
                require(choice != null && choice.isShown(), "Language choice is missing from Settings");
                choice.requestFocus();
                try {
                    Field changed = MainActivity.class.getDeclaredField("languageChange");
                    changed.setAccessible(true);
                    changed.setBoolean(original, true);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                original.recreate();
            });
            for (int i = 0; i < 40 && recreated.get() == null; i++) SystemClock.sleep(100);
            MainActivity screen = recreated.get();
            require(screen != null, "Activity did not recreate");
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                View choice = screen.findViewById(R.id.language_choice);
                require(choice != null && choice.isShown(), "Recreation lost Settings/Appearance");
                require(screen.getCurrentFocus() == choice, "Recreation lost language focus");
            });
            result.putString("language_state", "PASS: Settings and language focus survive recreation after an app launch");
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks);
            if (hadLast) launcherPrefs.edit().putString("last_opened_package", previous).commit();
            else launcherPrefs.edit().remove("last_opened_package").commit();
            android.content.SharedPreferences.Editor welcome = launcherPrefs.edit();
            if (hadWelcome) welcome.putBoolean("welcome_seen", previousWelcome);
            else welcome.remove("welcome_seen");
            welcome.commit();
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

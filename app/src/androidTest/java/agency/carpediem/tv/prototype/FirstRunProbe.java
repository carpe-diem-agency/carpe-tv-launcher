package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Runs only against the isolated preview package, never a selected Home app. */
final class FirstRunProbe {
    static void run(Instrumentation test, Bundle result) {
        test.setInTouchMode(false);
        SharedPreferences preferences = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean previous = preferences.getBoolean("welcome_seen", false);
        try {
            preferences.edit().remove("welcome_seen").commit();
            MainActivity first = launch(test);
            require(!hasFocus(test, first), "Welcome dialog did not take window focus");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(250);
            require(!preferences.getBoolean("welcome_seen", false), "Back marked welcome complete");
            test.runOnMainSync(first::finish);
            MainActivity second = launch(test);
            require(!hasFocus(test, second), "Welcome dialog did not reappear after Back");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            SystemClock.sleep(250);
            require(preferences.getBoolean("welcome_seen", false), "Explore choice was not persisted");
            test.runOnMainSync(second::finish);
            MainActivity third = launch(test);
            require(hasFocus(test, third), "Welcome dialog reappeared after explicit choice");
            result.putString("first_run", "PASS: Back re-prompts; explicit choice persists across cold launch");
            test.runOnMainSync(third::finish);
        } finally {
            preferences.edit().putBoolean("welcome_seen", previous).commit();
        }
    }

    private static MainActivity launch(Instrumentation test) {
        MainActivity screen = (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        SystemClock.sleep(600);
        return screen;
    }

    private static boolean hasFocus(Instrumentation test, MainActivity screen) {
        boolean[] value = {false};
        test.runOnMainSync(() -> value[0] = screen.hasWindowFocus());
        return value[0];
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

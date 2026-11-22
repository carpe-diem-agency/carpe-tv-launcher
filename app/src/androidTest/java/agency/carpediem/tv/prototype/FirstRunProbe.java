package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import java.util.HashMap;
import java.util.Map;

/** First-run dismissal and opt-in on the isolated validation package. */
final class FirstRunProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName())) {
            throw new IllegalStateException("First run requires the disposable validation package");
        }
        test.setInTouchMode(false);
        SharedPreferences preferences = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(preferences.getAll());
        MainActivity screen = null;
        try {
            require(preferences.edit().remove("welcome_seen").commit(), "Prepare first-run preference");
            screen = launch(test);
            awaitExplore(test, screen);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            awaitMainWindow(test, screen);
            require(!preferences.contains("welcome_seen"), "Back marked welcome complete");
            close(test, screen);
            screen = launch(test);
            awaitExplore(test, screen);
            // Only the verified Explore button may receive the activation key.
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            awaitMainWindow(test, screen);
            require(preferences.getBoolean("welcome_seen", false), "Explore first was not persisted");
            close(test, screen);

            screen = launch(test);
            awaitMainWindow(test, screen);
            MainActivity relaunched = screen;
            View home = relaunched.findViewById(R.id.nav_home);
            SettingsStructureProbe.await(test, () -> home.isSelected() && home.hasFocus(),
                    "Relaunch did not focus the internal Home rail");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.await(test, () -> relaunched.findViewById(R.id.nav_apps).isSelected()
                    && relaunched.findViewById(R.id.apps_grid) != null, "Down did not open the app library");
            result.putString("first_run",
                    "PASS: Back leaves welcome pending; verified Explore persists across Activity relaunch, Down opens Apps");
        } finally {
            try {
                if (screen != null) close(test, screen);
            } finally {
                SettingsStructureProbe.restore(preferences, saved);
            }
        }
    }

    private static MainActivity launch(Instrumentation test) {
        return (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private static void awaitExplore(Instrumentation test, MainActivity screen) throws Exception {
        String explore = screen.getString(R.string.welcome_explore);
        SettingsStructureProbe.await(test, () -> {
            View modal = welcome(screen);
            View focus = modal == null ? null : modal.findFocus();
            return modal != null && modal.hasWindowFocus() && focus instanceof Button && focus.isEnabled()
                    && focus.isShown() && explore.contentEquals(((Button) focus).getText());
        }, "Welcome did not focus Explore first");
    }

    private static void awaitMainWindow(Instrumentation test, MainActivity screen) throws Exception {
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && welcome(screen) == null,
                "Main did not own focus after welcome");
    }

    private static View welcome(MainActivity screen) {
        return ModalFocusProbe.ownedModal(screen, screen.getString(R.string.welcome_title),
                screen.getString(R.string.welcome_explore));
    }

    private static void close(Instrumentation test, MainActivity screen) throws Exception {
        SettingsStructureProbe.finish(test, screen);
        SettingsStructureProbe.quiescent(test, screen);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

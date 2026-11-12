package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import java.lang.reflect.Field;

/** Read-only check of Home role, resolver and the warning rendered in Device settings. */
final class HomeRouteProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        if (!test.getTargetContext().getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Home route probe requires the isolated preview package");
        boolean selected = HomeChoice.isSelected(test.getTargetContext());
        boolean conflict = HomeChoice.hasResolverConflict(test.getTargetContext());
        result.putString("home_role_selected", String.valueOf(selected));
        result.putString("home_resolver_conflict", String.valueOf(conflict));
        SharedPreferences preferences = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean welcomeWasSet = preferences.contains("welcome_seen");
        boolean previousWelcome = preferences.getBoolean("welcome_seen", false);
        if (!preferences.edit().putBoolean("welcome_seen", true).commit())
            throw new AssertionError("Could not prepare onboarding state");
        MainActivity screen = null;
        try {
            screen = (MainActivity) test.startActivitySync(
                    new Intent(test.getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity activeScreen = screen;
            test.runOnMainSync(() -> {
                try {
                    Field category = MainActivity.class.getDeclaredField("settingsCategory");
                    category.setAccessible(true);
                    category.set(activeScreen, "device");
                    activeScreen.findViewById(R.id.nav_settings).performClick();
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
            });
            SystemClock.sleep(250);
            test.runOnMainSync(() -> {
                TextView warning = activeScreen.findViewById(R.id.home_remote_warning);
                if (warning == null) throw new AssertionError("Device Home warning is missing");
                if (selected) {
                    int expected = conflict ? R.string.home_route_conflict : R.string.home_remote_check;
                    if (!activeScreen.getString(expected).contentEquals(warning.getText()))
                        throw new AssertionError("Device Home warning does not match platform routing");
                }
                if (warning.getVisibility() != (selected ? android.view.View.VISIBLE : android.view.View.GONE))
                    throw new AssertionError("Device Home warning visibility is wrong");
            });
            result.putString("home_route", "PASS: Device warning reflects Home role and resolver");
        } finally {
            if (screen != null) screen.finish();
            SharedPreferences.Editor restore = preferences.edit();
            if (welcomeWasSet) restore.putBoolean("welcome_seen", previousWelcome);
            else restore.remove("welcome_seen");
            if (!restore.commit()) throw new AssertionError("Could not restore onboarding state");
        }
    }
    private HomeRouteProbe() { }
}

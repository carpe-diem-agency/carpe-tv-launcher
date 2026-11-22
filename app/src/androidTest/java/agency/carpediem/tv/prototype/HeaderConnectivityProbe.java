package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Xiaomi navigation into the Android connectivity screens and back. */
final class HeaderConnectivityProbe {
    private record Header(String name, int rail, int wifi, int bluetooth, int timer) { }
    private static final Header[] HEADERS = {
            new Header("home", R.id.nav_home, R.id.home_wifi, R.id.home_bluetooth, R.id.home_timer),
            new Header("apps", R.id.nav_apps, R.id.apps_wifi, R.id.apps_bluetooth, R.id.apps_timer),
            new Header("settings", R.id.nav_settings, R.id.settings_wifi, R.id.settings_bluetooth, R.id.settings_timer)
    };

    static void verifyEdges(Instrumentation test, Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Header edge probe requires the disposable validation package");
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences timerPrefs = test.getTargetContext().getSharedPreferences("sleep_timer", 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        Map<String, ?> savedTimer = new HashMap<>(timerPrefs.getAll());
        require(prefs.edit().putBoolean("welcome_seen", true).remove("last_opened_package").commit(),
                "Could not prepare isolated Home");
        test.setInTouchMode(false);
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            int boot = Settings.Global.getInt(screen.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            for (boolean countdown : new boolean[]{false, true}) {
                SharedPreferences.Editor timer = timerPrefs.edit().clear();
                if (countdown) timer.putInt("boot", boot).putLong("deadline", SystemClock.elapsedRealtime() + 118 * 60_000L);
                require(timer.commit(), "Could not prepare simulated timer");
                for (Header header : HEADERS) {
                    SettingsStructureProbe.main(test, () -> {
                        screen.findViewById(header.rail()).requestFocus();
                        screen.updateSleepTimerIndicator();
                        View wifi = screen.findViewById(header.wifi());
                        require(wifi != null && wifi.requestFocus(), "Wi-Fi action unavailable on " + header.name());
                    });
                    test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
                    awaitFocus(test, screen, header, header.bluetooth());
                    test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
                    awaitFocus(test, screen, header, header.timer());
                    SettingsStructureProbe.main(test, () -> {
                        TextView view = screen.findViewById(header.timer());
                        require(countdown == (view.getText().length() > 0),
                                "Timer display did not match fixture on " + header.name());
                    });
                    UiSounds sound = UiSounds.get(screen);
                    UiSounds.Stats before = sound.stats();
                    for (int press = 0; press < 3; press++) {
                        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
                        SettingsStructureProbe.main(test, () -> require(screen.hasWindowFocus()
                                && screen.findViewById(header.rail()).isSelected()
                                && screen.getCurrentFocus() == screen.findViewById(header.timer()),
                                "Right left the timer boundary on " + header.name()));
                    }
                    require(sound.stats().focusEvents == before.focusEvents && sound.stats().clickEvents == before.clickEvents,
                            "Right at the edge generated a navigation or action sound on " + header.name());
                    test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
                    awaitFocus(test, screen, header, header.bluetooth());
                    result.putString("header_" + header.name() + (countdown ? "_countdown" : "_idle"),
                            "PASS: Right boundary and Left to Bluetooth");
                }
            }
            result.putString("headeredge", "PASS: three header edges with idle and simulated countdown timer");
        } finally {
            try {
                SettingsStructureProbe.finish(test, screen);
                SettingsStructureProbe.quiescent(test, screen);
            } finally {
                SettingsStructureProbe.restore(timerPrefs, savedTimer);
                SettingsStructureProbe.restore(prefs, saved);
            }
        }
    }

    static void run(Instrumentation test, Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Connectivity probe requires the disposable validation package");
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        require(prefs.edit().putBoolean("welcome_seen", true).commit(), "Could not prepare onboarding state");
        test.setInTouchMode(false);
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            SettingsStructureProbe.main(test, () -> require(!SystemAccess.ready(),
                    "The validation package must use the ordinary non-privileged routes"));
            String wifi = Settings.Global.getString(screen.getContentResolver(), "wifi_on");
            String bluetooth = Settings.Global.getString(screen.getContentResolver(), "bluetooth_on");
            require(wifi != null && bluetooth != null, "Device connectivity baseline unavailable");
            for (Header header : HEADERS) {
                SettingsStructureProbe.main(test, () -> {
                    screen.findViewById(header.rail()).requestFocus();
                    require(screen.findViewById(header.rail()).isSelected(), "Header page did not open");
                    View action = screen.findViewById(header.wifi());
                    require(action != null && action.isShown() && action.requestFocus(), "Wi-Fi action unavailable");
                });
                AppAuditProbe.verifyAndroidDestination(test, ".connectivity.NetworkActivity", null,
                        header.name() + "_wifi_destination", result);
                awaitFocus(test, screen, header, header.wifi());
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
                awaitFocus(test, screen, header, header.bluetooth());
                // On this firmware the accessories slice requires WRITE_SECURE_SETTINGS.
                AppAuditProbe.verifyAndroidDestination(test, ".MainSettings", null,
                        header.name() + "_bluetooth_destination", result);
                awaitFocus(test, screen, header, header.bluetooth());
            }
            require(Objects.equals(wifi, Settings.Global.getString(screen.getContentResolver(), "wifi_on"))
                    && Objects.equals(bluetooth, Settings.Global.getString(screen.getContentResolver(), "bluetooth_on")),
                    "Connectivity settings changed during read-only navigation");
            result.putString("header_connectivity",
                    "PASS: Wi-Fi and Bluetooth fallback from all three headers with exact focus on Back");
        } finally {
            SettingsStructureProbe.finish(test, screen);
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    private static void awaitFocus(Instrumentation test, MainActivity screen, Header header, int id) throws Exception {
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus()
                && screen.findViewById(header.rail()).isSelected() && screen.getCurrentFocus() != null
                && screen.getCurrentFocus().getId() == id,
                "Header focus did not return to " + screen.getResources().getResourceEntryName(id));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

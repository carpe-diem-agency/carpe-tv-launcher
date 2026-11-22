package agency.carpediem.tv.prototype;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Actual timer window, with the font and locale changed only for its own views. */
final class SleepTimerFontProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        Context target = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(target.getPackageName()), "Disposable validation required");
        SharedPreferences prefs = target.getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences timer = target.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        Map<String, ?> before = new HashMap<>(prefs.getAll());
        Map<String, ?> beforeTimer = new HashMap<>(timer.getAll());
        require(prefs.edit().putBoolean("welcome_seen", true).commit(), "Prepare isolated launcher");
        MainActivity screen = null;
        try {
            test.setInTouchMode(false);
            screen = (MainActivity) test.startActivitySync(new Intent(target, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            require(!SystemAccess.ready() && SleepTimer.remaining(screen) <= 0,
                    "INPUT_UNVERIFIED: timer must show the access hint without an active timer");
            int compactHeight = Math.round(200 * screen.getResources().getDisplayMetrics().density);
            for (String locale : new String[]{"uk", "en"}) {
                verifyWindow(test, screen, locale, ViewGroup.LayoutParams.WRAP_CONTENT, false);
                verifyWindow(test, screen, locale, compactHeight, false);
            }
            int boot = Settings.Global.getInt(target.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            // Render the active state from app-private preferences, without registering an alarm.
            long deadline = SystemClock.elapsedRealtime() + 420000;
            require(timer.edit().putInt("boot", boot).putLong("deadline", deadline).commit()
                    && SleepTimer.remaining(screen) > 0, "Prepare active timer display");
            for (String locale : new String[]{"uk", "en"}) verifyWindow(test, screen, locale, compactHeight, true);
            require(timer.getLong("deadline", 0) == deadline, "Closing the active timer changed its deadline");
            result.putString("sleep_timer_font",
                    "PASS: real timer access sheet at width 320dp, natural and 200dp height, local font scale 2, English/Ukrainian; full text and visible D-pad Close");
            result.putString("sleep_timer_active_footer",
                    "PASS: seeded active state kept countdown and separate Cancel/Close actions legible and D-pad reachable; Close preserved the deadline");
        } finally {
            SettingsStructureProbe.restore(timer, beforeTimer);
            if (screen != null) {
                SettingsStructureProbe.finish(test, screen);
                SettingsStructureProbe.quiescent(test, screen);
            }
            SettingsStructureProbe.restore(prefs, before);
        }
    }

    private static void verifyWindow(Instrumentation test, MainActivity screen, String locale, int height, boolean active) throws Exception {
        Configuration config = new Configuration(screen.getResources().getConfiguration());
        config.fontScale = 2f;
        config.setLocales(new LocaleList(Locale.forLanguageTag(locale)));
        Context local = new ContextThemeWrapper(screen.createConfigurationContext(config), screen.getTheme());
        int width = Math.round(320 * local.getResources().getDisplayMetrics().density);
        Dialog[] owned = new Dialog[1];
        try {
            SettingsStructureProbe.main(test, () -> {
                owned[0] = new SleepTimerDialog(screen, local, false).show();
                owned[0].getWindow().setLayout(width, height);
            });
            View decor = owned[0].getWindow().getDecorView();
            SettingsStructureProbe.await(test, () -> decor.hasWindowFocus() && decor.getWidth() == width
                            && (height < 0 || decor.getHeight() == height),
                    "Compact timer window or focus unavailable: " + locale);
            SettingsStructureProbe.main(test, () -> {
                TextView setup = text(decor, local.getString(R.string.sleep_setup));
                TextView close = text(decor, local.getString(R.string.sleep_close));
                fits(setup, locale);
                fits(close, locale);
                if (active) {
                    fits(text(decor, local.getString(R.string.sleep_cancel)), locale);
                    long remaining = SleepTimer.remaining(screen);
                    TextView countdown = text(decor, local.getString(R.string.sleep_countdown, SleepTimer.countdown(remaining)));
                    if (countdown == null) {
                        countdown = text(decor, local.getString(R.string.sleep_countdown, SleepTimer.countdown(remaining + 1000)));
                    }
                    fits(countdown, locale);
                }
                require(setup.hasFocus(), "Timer did not start on its setup action");
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            if (active) {
                String cancel = local.getString(R.string.sleep_cancel);
                String close = local.getString(R.string.sleep_close);
                SettingsStructureProbe.await(test, () -> focused(decor, cancel) || focused(decor, close),
                        "D-pad Down did not reach the active timer footer: " + locale);
                boolean[] cancelFocused = new boolean[1];
                SettingsStructureProbe.main(test, () -> cancelFocused[0] = focused(decor, cancel));
                if (!cancelFocused[0]) test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
                SettingsStructureProbe.await(test, () -> focused(decor, cancel), "Cancel is unreachable: " + locale);
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            }
            SettingsStructureProbe.await(test, () -> focused(decor, local.getString(R.string.sleep_close)),
                    "D-pad did not reach Close at font scale 2: " + locale);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> !owned[0].isShowing() && screen.hasWindowFocus(),
                    "Timer Close did not return to the launcher: " + locale);
        } finally {
            if (owned[0] != null) SettingsStructureProbe.main(test, owned[0]::dismiss);
        }
    }

    private static boolean focused(View window, String label) {
        TextView control = text(window, label);
        return control != null && control.hasFocus() && entirelyVisible(control);
    }

    private static boolean entirelyVisible(View control) {
        Rect visible = new Rect();
        return control.getGlobalVisibleRect(visible)
                && visible.width() == control.getWidth() && visible.height() == control.getHeight();
    }

    private static TextView text(View root, String label) {
        if (root instanceof TextView && label.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView match = text(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void fits(TextView control, String locale) {
        require(control != null && control.isShown() && control.getLayout() != null, "Timer control unavailable: " + locale);
        int width = control.getWidth() - control.getCompoundPaddingLeft() - control.getCompoundPaddingRight();
        int height = control.getHeight() - control.getCompoundPaddingTop() - control.getCompoundPaddingBottom();
        for (int line = 0; line < control.getLayout().getLineCount(); line++) {
            require(control.getLayout().getLineWidth(line) <= width + 1 && control.getLayout().getEllipsisCount(line) == 0,
                    "Timer control clips horizontally at " + locale + ": " + control.getText());
        }
        require(control.getLayout().getHeight() <= height, "Timer control clips vertically at " + locale + ": " + control.getText());
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

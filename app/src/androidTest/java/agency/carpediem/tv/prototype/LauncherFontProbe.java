package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class LauncherFontProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package required");
        SharedPreferences main = context.getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences language = context.getSharedPreferences("MainActivity", 0);
        Map<String, ?> savedMain = new HashMap<>(main.getAll());
        Map<String, ?> savedLanguage = new HashMap<>(language.getAll());
        Locale savedLocale = Locale.getDefault();
        try {
            require(main.edit().putBoolean("welcome_seen", true).putBoolean("animations", false)
                            .remove("last_opened_package").commit(),
                    "Could not prepare launcher preferences");
            test.setInTouchMode(false);
            for (String locale : new String[]{"en", "uk"}) {
                require(language.edit().putString("language", locale).commit(), "Could not set app locale");
                for (float scale : new float[]{1f, 2f}) verify(test, context, locale, scale);
            }
            result.putString("launcherfont",
                    "PASS: live Home and Apps headers and library search focus at EN/UK scale 1/2, compact app bounds");
        } finally {
            SettingsStructureProbe.restore(main, savedMain);
            SettingsStructureProbe.restore(language, savedLanguage);
            Locale.setDefault(savedLocale);
        }
    }

    private static void verify(Instrumentation test, Context context, String language, float scale)
            throws Exception {
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("probe_locale", language)
                .putExtra("probe_font_scale", scale));
        String configuration = language + "/" + scale;
        try {
            SettingsStructureProbe.await(test, () -> screen.hasWindowFocus()
                    && screen.findViewById(R.id.home_time) != null, "Home did not open: " + configuration);
            require(screen.getResources().getConfiguration().fontScale == scale
                            && screen.getResources().getConfiguration().getLocales().get(0).getLanguage().equals(language),
                    "App-local resource configuration did not reach MainActivity: " + configuration
                            + " actual=" + screen.getResources().getConfiguration().getLocales()
                            + "/" + screen.getResources().getConfiguration().fontScale);
            ViewGroup content = screen.findViewById(android.R.id.content);
            View shell = content.getChildAt(0);
            SettingsStructureProbe.main(test, () -> {
                float density = screen.getResources().getDisplayMetrics().density;
                ViewGroup.LayoutParams bounds = shell.getLayoutParams();
                bounds.width = Math.min(content.getWidth(), Math.round(820 * density));
                bounds.height = Math.min(content.getHeight(), Math.round(400 * density));
                shell.setLayoutParams(bounds);
            });
            SettingsStructureProbe.await(test, () -> shell.getWidth() == shell.getLayoutParams().width
                    && shell.getHeight() == shell.getLayoutParams().height, "Compact launcher was not laid out");
            SettingsStructureProbe.main(test, () -> {
                for (int id : new int[]{R.id.home_time, R.id.home_date, R.id.home_greeting, R.id.home_timer}) {
                    assertText(screen, shell, id, configuration);
                }
                assertControls(screen, shell, R.id.home_power, R.id.home_wifi, R.id.home_bluetooth, R.id.home_timer);
                Rect toolbar = new Rect();
                View power = screen.findViewById(R.id.home_power);
                require(((View) power.getParent()).getGlobalVisibleRect(toolbar), "Home toolbar not visible");
                for (int id : new int[]{R.id.home_time, R.id.home_date, R.id.home_greeting}) {
                    Rect text = new Rect();
                    require(screen.findViewById(id).getGlobalVisibleRect(text) && !Rect.intersects(toolbar, text),
                            "Home header text overlaps actions: " + configuration);
                }
                require(screen.findViewById(R.id.nav_home).requestFocus(), "Cannot place focus on Home rail");
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.nav_apps)
                    && screen.findViewById(R.id.apps_search) != null, "Down did not open Apps from Home rail");
            SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.app_name) != null
                    && screen.findViewById(R.id.apps_title).isLaidOut()
                    && !screen.findViewById(R.id.apps_title).isLayoutRequested(),
                    "Library and header did not finish layout");
            SettingsStructureProbe.main(test, () -> {
                for (int id : new int[]{R.id.apps_title, R.id.apps_search, R.id.apps_clock, R.id.apps_timer}) {
                    assertText(screen, shell, id, configuration);
                }
                assertControls(screen, shell, R.id.apps_power, R.id.apps_wifi, R.id.apps_bluetooth, R.id.apps_timer);
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() != null
                    && screen.getCurrentFocus().findViewById(R.id.app_name) != null,
                    "Right from Apps rail did not focus a tile");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            EditText search = screen.findViewById(R.id.apps_search);
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == search, "Up did not reach search");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() != null
                    && screen.getCurrentFocus().findViewById(R.id.app_name) != null,
                    "Down did not restore tile focus from search");
        } finally {
            SettingsStructureProbe.finish(test, screen);
            SettingsStructureProbe.quiescent(test, screen);
        }
    }

    private static void assertText(MainActivity screen, View shell, int id, String configuration) {
        TextView text = screen.findViewById(id);
        String label = screen.getResources().getResourceEntryName(id) + " " + configuration;
        require(text != null && inside(shell, text), "Text outside compact viewport: " + label);
        Layout layout = text.getLayout();
        int space = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        require(layout != null && layout.getHeight() <= space + 1,
                "Clipped text height: " + label + " layout=" + (layout == null ? 0 : layout.getHeight())
                        + " available=" + space);
        for (int line = 0; line < layout.getLineCount(); line++) {
            require(layout.getEllipsisCount(line) == 0, "Ellipsized header text: " + label);
        }
    }

    private static void assertControls(MainActivity screen, View shell, int... ids) {
        int previousRight = -1;
        for (int id : ids) {
            View control = screen.findViewById(id);
            Rect rect = new Rect();
            require(control != null && inside(shell, control) && control.getGlobalVisibleRect(rect)
                    && rect.left >= previousRight && control.isFocusable(), "Top-row control clipped or overlapping");
            previousRight = rect.right;
        }
    }

    private static boolean inside(View shell, View view) {
        Rect bounds = new Rect();
        Rect visible = new Rect();
        return shell.getGlobalVisibleRect(bounds) && view.getGlobalVisibleRect(visible)
                && visible.width() == view.getWidth() && visible.height() == view.getHeight()
                && bounds.contains(visible);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Checks the three settings focus regions without changing device settings. */
final class SettingsFocusProbe {
    private SettingsFocusProbe() { }

    static void run(Instrumentation probe, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(probe.getTargetContext().getPackageName())) {
            throw new IllegalStateException("Settings focus requires the disposable validation package");
        }
        SharedPreferences sounds = UiSounds.preferences(probe.getTargetContext());
        Map<String, ?> savedSounds = new HashMap<>(sounds.getAll());
        SharedPreferences prefs = probe.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = prefs.contains("welcome_seen");
        boolean welcome = prefs.getBoolean("welcome_seen", false);
        try {
            if (!prefs.edit().putBoolean("welcome_seen", true).commit()
                    || !sounds.edit().remove(UiSounds.ENABLED).remove(UiSounds.VOLUME).commit()) {
                throw new AssertionError("Cannot prepare settings focus fixture");
            }
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
                verifyDetailBoundaries(probe, after, result, false);
            } finally { application.unregisterActivityLifecycleCallbacks(callbacks); }
            result.putString("settings_focus", "PASS: rail, category boundary, detail/header round trip, Back and Left routes, direct activation and recreation");
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadWelcome) restore.putBoolean("welcome_seen", welcome);
            else restore.remove("welcome_seen");
            restore.commit();
            SettingsStructureProbe.restore(sounds, savedSounds);
        }
    }

    @SuppressWarnings("deprecation")
    static void runCompact(Instrumentation probe, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(probe.getTargetContext().getPackageName())) {
            throw new IllegalStateException("Compact settings requires the disposable validation package");
        }
        SharedPreferences prefs = probe.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences language = probe.getTargetContext().getSharedPreferences("MainActivity", 0);
        SharedPreferences sounds = UiSounds.preferences(probe.getTargetContext());
        Map<String, ?> saved = new HashMap<>(prefs.getAll()), savedLanguage = new HashMap<>(language.getAll());
        Map<String, ?> savedSounds = new HashMap<>(sounds.getAll());
        probe.setInTouchMode(false);
        try {
            if (!prefs.edit().putBoolean("welcome_seen", true).remove("wallpaper").commit()
                    || !sounds.edit().remove(UiSounds.ENABLED).remove(UiSounds.VOLUME).commit()) {
                throw new AssertionError("Cannot prepare compact settings defaults");
            }
            for (String locale : new String[]{"uk", "en"}) {
                if (!language.edit().putString("language", locale).commit()) {
                    throw new AssertionError("Cannot set isolated locale " + locale);
                }
                MainActivity screen = (MainActivity) probe.startActivitySync(new Intent(probe.getTargetContext(),
                        MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Resources resources = screen.getResources();
                Configuration previous = new Configuration(resources.getConfiguration());
                try {
                    int height = Math.round(360 * resources.getDisplayMetrics().density);
                    SettingsStructureProbe.main(probe, () -> {
                        Configuration local = new Configuration(resources.getConfiguration());
                        local.fontScale = 2f;
                        resources.updateConfiguration(local, null);
                        View host = screen.findViewById(R.id.content_host);
                        ViewGroup.LayoutParams size = host.getLayoutParams();
                        size.height = height;
                        host.setLayoutParams(size);
                    });
                    SettingsStructureProbe.await(probe, () -> screen.hasWindowFocus()
                            && screen.findViewById(R.id.content_host).getHeight() == height
                            && resources.getConfiguration().fontScale == 2f
                            && locale.equals(resources.getConfiguration().getLocales().get(0).getLanguage()),
                            "App-local compact settings fixture did not apply: " + locale);
                    verifyDetailBoundaries(probe, screen, result, true);
                    result.putString("settings_compact_" + locale,
                            height + "px local host, 360dp and font 2x: " + result.getString("settings_detail_boundaries"));
                } finally {
                    try {
                        SettingsStructureProbe.finish(probe, screen);
                    } finally {
                        SettingsStructureProbe.main(probe, () -> resources.updateConfiguration(previous, null));
                    }
                }
            }
        } finally {
            SettingsStructureProbe.restore(sounds, savedSounds);
            SettingsStructureProbe.restore(language, savedLanguage);
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    private static void verifyDetailBoundaries(Instrumentation probe, MainActivity screen, Bundle result,
            boolean compact) throws Exception {
        int[] entries = {R.id.wallpaper_blue, R.id.saver_timeout, View.NO_ID, View.NO_ID,
                R.id.device_diagnostics, R.id.about_company};
        int[] lastIds = {R.id.ui_sounds_preview, R.id.saver_clear, View.NO_ID,
                R.id.audit_manage_apps, View.NO_ID, R.id.about_authors};
        SettingsStructureProbe.main(probe, () -> screen.findViewById(R.id.nav_settings).requestFocus());
        SettingsStructureProbe.await(probe, () -> screen.findViewById(R.id.settings_categories) != null,
                "Settings did not open");
        ViewGroup categories = screen.findViewById(R.id.settings_categories);
        if (categories.getChildCount() != entries.length) throw new AssertionError("Settings categories changed");
        key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(probe, KeyEvent.KEYCODE_DPAD_UP);
        StringBuilder routes = new StringBuilder();
        for (int index = 0; index < entries.length; index++) {
            View category = categories.getChildAt(index);
            String title = String.valueOf(((TextView) category).getText());
            expectFocus(probe, screen, category, title + ": enter category");
            if (compact) expectReadable(probe, category, title + ": selected category");
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            requireRegion(screen, R.id.settings_detail);
            View entry = screen.getCurrentFocus();
            if (entries[index] != View.NO_ID) {
                expectFocus(probe, screen, screen.findViewById(entries[index]), title + ": enter detail");
            }
            if (compact) expectReadable(probe, entry, title + ": entry action");
            if (index == 2 && !screen.getString(R.string.open_library).contentEquals(((TextView) entry).getText())) {
                throw new AssertionError(title + ": expected Open library");
            }
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            expectFocus(probe, screen, screen.findViewById(R.id.settings_power), title + ": Up to header");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            expectFocus(probe, screen, entry, title + ": Down from header");

            ViewGroup detail = screen.findViewById(R.id.settings_detail);
            View last = lastIds[index] != View.NO_ID ? detail.findViewById(lastIds[index])
                    : detail.getChildAt(detail.getChildCount() - 1);
            if (last == null || !last.isEnabled() || !last.isFocusable()) {
                throw new AssertionError(title + ": last control is unavailable");
            }
            if (index == 4 && !screen.getString(R.string.android_settings).contentEquals(((TextView) last).getText())) {
                throw new AssertionError(title + ": expected Android settings");
            }
            int steps = 0;
            while (screen.getCurrentFocus() != last && steps < 16) {
                View before = screen.getCurrentFocus();
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                requireRegion(screen, R.id.settings_detail);
                if (screen.getCurrentFocus() == before) {
                    throw new AssertionError(title + ": Down got stuck before the last control at " + before);
                }
                steps++;
                if (compact) expectReadable(probe, screen.getCurrentFocus(), title + ": Down step " + steps);
            }
            expectFocus(probe, screen, last, title + ": reach last action");
            if (compact) expectReadable(probe, last, title + ": last action");
            SettingsStructureProbe.await(probe, () -> {
                Rect visible = new Rect();
                return last.getLocalVisibleRect(visible) && visible.width() == last.getWidth()
                        && visible.height() == last.getHeight();
            }, title + ": last action is clipped");
            for (int repeat = 0; repeat < 2; repeat++) {
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                expectFocus(probe, screen, last, title + ": bottom boundary");
            }
            key(probe, KeyEvent.KEYCODE_DPAD_LEFT);
            expectFocus(probe, screen, category, title + ": Left from the last action");
            if (routes.length() > 0) routes.append(" | ");
            routes.append(title).append(": ").append(steps).append(" Down steps");
            if (index < entries.length - 1) key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
        }
        key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
        expectFocus(probe, screen, categories.getChildAt(entries.length - 1), "Last category boundary");
        result.putString("settings_detail_boundaries", routes.toString());
        if (compact) {
            ScrollView categoryScroll = screen.findViewById(R.id.settings_category_scroll);
            SettingsStructureProbe.await(probe, () -> categoryScroll != null && categoryScroll.getScrollY() > 0,
                    "Short layout did not scroll to reveal the selected last category");
        }

        for (int index = entries.length - 1; index > 0; index--) key(probe, KeyEvent.KEYCODE_DPAD_UP);
        key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
        View sounds = screen.findViewById(R.id.ui_sounds_enabled);
        for (int index = 0; screen.getCurrentFocus() != sounds && index < 16; index++) {
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
        }
        expectFocus(probe, screen, sounds, "Find UI sounds switch");
        key(probe, KeyEvent.KEYCODE_DPAD_CENTER);
        try {
            View volume = screen.findViewById(R.id.ui_sounds_volume);
            View preview = screen.findViewById(R.id.ui_sounds_preview);
            SettingsStructureProbe.await(probe, () -> !volume.isEnabled() && !preview.isEnabled(),
                    "Disabling sounds did not disable the remaining controls");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            expectFocus(probe, screen, sounds, "Bottom boundary with disabled sound controls");
            result.putString("settings_disabled_bottom", "PASS: Down retains the sounds switch when the last two controls are disabled");
        } finally {
            SettingsStructureProbe.main(probe, () -> {
                if (!((android.widget.Switch) sounds).isChecked()) sounds.performClick();
            });
        }
    }

    private static void expectReadable(Instrumentation probe, View control, String route) throws Exception {
        SettingsStructureProbe.await(probe, () -> {
            Rect visible = new Rect();
            return control.getWidth() > 0 && control.getHeight() > 0 && control.getLocalVisibleRect(visible)
                    && visible.width() == control.getWidth() && visible.height() == control.getHeight();
        }, route + " is clipped in the local viewport");
        SettingsStructureProbe.main(probe, () -> {
            if (!(control instanceof TextView)) return;
            TextView label = (TextView) control;
            Layout layout = label.getLayout();
            if (layout == null || layout.getLineCount() < 1 || layout.getHeight() + label.getCompoundPaddingTop()
                    + label.getCompoundPaddingBottom() > label.getHeight()
                    || layout.getLineEnd(layout.getLineCount() - 1) < label.length()) {
                throw new AssertionError(route + " clips its label: " + label.getText());
            }
            for (int line = 0; line < layout.getLineCount(); line++) {
                if (layout.getEllipsisCount(line) != 0) throw new AssertionError(route + " truncates its label");
            }
        });
    }

    private static void expectFocus(Instrumentation probe, MainActivity screen, View target, String route)
            throws Exception {
        SettingsStructureProbe.main(probe, () -> {
            if (target == null || screen.getCurrentFocus() != target || !target.isShown()) {
                throw new AssertionError(route + " did not focus the expected control; actual="
                        + screen.getCurrentFocus());
            }
        });
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

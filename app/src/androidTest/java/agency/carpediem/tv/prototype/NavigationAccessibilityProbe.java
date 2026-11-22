package agency.carpediem.tv.prototype;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks nodes exported by Android on the visible page, without activating controls. */
final class NavigationAccessibilityProbe {
    private record Page(int rail, int power, int wifi, int bluetooth, int timer) { }
    private static final Page[] PAGES = {
            new Page(R.id.nav_home, R.id.home_power, R.id.home_wifi, R.id.home_bluetooth, R.id.home_timer),
            new Page(R.id.nav_apps, R.id.apps_power, R.id.apps_wifi, R.id.apps_bluetooth, R.id.apps_timer),
            new Page(R.id.nav_settings, R.id.settings_power, R.id.settings_wifi, R.id.settings_bluetooth, R.id.settings_timer)
    };
    private static final int[] RAIL_NAMES = {R.string.home, R.string.all_apps, R.string.settings};

    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package required");
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        SharedPreferences languagePrefs = context.getSharedPreferences("MainActivity", 0);
        Map<String, ?> savedLanguage = new HashMap<>(languagePrefs.getAll());
        Locale previousLocale = Locale.getDefault();
        UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = automation.getServiceInfo();
        int flags = info.flags;
        try {
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            require(prefs.edit().putBoolean("welcome_seen", true).remove("last_opened_package").commit(),
                    "Prepare isolated startup");
            test.setInTouchMode(false);
            for (String language : List.of("en", "uk")) {
                AppLanguage.choose(context, language);
                MainActivity screen = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                try {
                    require(language.equals(screen.getResources().getConfiguration().getLocales().get(0).getLanguage()),
                            "Activity language mismatch");
                    for (int page = 0; page < PAGES.length; page++) {
                        verifyPage(test, automation, screen, page);
                        if (page + 1 < PAGES.length) test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
                    }
                } finally {
                    SettingsStructureProbe.finish(test, screen);
                    SettingsStructureProbe.quiescent(test, screen);
                }
                verifyUpdateStatus(test, automation, context);
                result.putString("accessibility_" + language,
                        "PASS: rail, headers, timer, search, tile labels and updater status");
            }
            result.putString("navigationaccessibility", "PASS: English and Ukrainian navigation and updater status");
        } finally {
            info.flags = flags;
            automation.setServiceInfo(info);
            SettingsStructureProbe.restore(prefs, saved);
            SettingsStructureProbe.restore(languagePrefs, savedLanguage);
            Locale.setDefault(previousLocale);
        }
    }

    private static void verifyUpdateStatus(Instrumentation test, UiAutomation automation, Context context)
            throws Exception {
        UpdateManager.WORKER.submit(() -> {}).get(15, TimeUnit.SECONDS);
        SharedPreferences prefs = UpdateManager.prefs(context);
        require(!UpdateManager.busy(context) && !prefs.getBoolean("enqueuePending", false)
                        && !prefs.contains("downloadId") && !prefs.contains("sessionId"),
                "Validation updater has an existing operation");
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        UpdatesActivity screen = null;
        try {
            require(prefs.edit().clear().putString("state", "idle")
                    .putString("messageKey", "update_current").commit(), "Prepare isolated update status");
            UpdatesActivity updates = (UpdatesActivity) test.startActivitySync(new Intent(context, UpdatesActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            screen = updates;
            TextView status = updates.findViewById(R.id.update_status);
            AtomicInteger textChanges = new AtomicInteger();
            SettingsStructureProbe.await(test, updates::hasWindowFocus, "Updates is not foreground");
            SettingsStructureProbe.main(test, () -> {
                ImageView icon = (ImageView) ((ViewGroup) status.getParent()).getChildAt(0);
                require(icon.isShown() && icon.getContentDescription() == null
                                && icon.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                        "Decorative success icon repeats the status");
                status.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        textChanges.incrementAndGet();
                    }
                    @Override public void afterTextChanged(Editable value) { }
                });
            });
            verifyStatusNode(automation, updates, R.string.update_current);
            SystemClock.sleep(1200);
            require(textChanges.get() == 0, "Polling rewrote an unchanged status");
            require(prefs.edit().putString("state", "error").putString("messageKey", "update_download_failed")
                    .commit(), "Set isolated download error");
            SettingsStructureProbe.await(test, () -> textChanges.get() == 1
                            && updates.getString(R.string.update_download_failed).contentEquals(status.getText()),
                    "Status transition did not render once");
            verifyStatusNode(automation, updates, R.string.update_download_failed);
        } finally {
            if (screen != null) {
                SettingsStructureProbe.main(test, screen::finish);
                SettingsStructureProbe.await(test, screen::isDestroyed, "Updates did not close");
                UpdateManager.WORKER.submit(() -> {}).get(15, TimeUnit.SECONDS);
            }
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    private static void verifyStatusNode(UiAutomation automation, UpdatesActivity screen, int label) {
        AccessibilityNodeInfo root = ownedRoot(automation, screen.getPackageName());
        try {
            AccessibilityNodeInfo status = visibleNode(root, screen, R.id.update_status);
            try {
                require(status.getLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_POLITE
                                && status.getText() != null && screen.getString(label).contentEquals(status.getText()),
                        "Update status must expose localized text and a polite live region");
            } finally {
                status.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private static void verifyPage(Instrumentation test, UiAutomation automation, MainActivity screen, int index)
            throws Exception {
        Page page = PAGES[index];
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus()
                && screen.findViewById(page.rail()).isSelected()
                && screen.findViewById(page.timer()) != null
                && (index == 2 || screen.findViewById(R.id.app_name) != null), "Page or catalog did not settle");
        AccessibilityNodeInfo root = ownedRoot(automation, screen.getPackageName());
        try {
            for (int rail = 0; rail < PAGES.length; rail++) {
                AccessibilityNodeInfo node = namedAction(root, screen, PAGES[rail].rail(), RAIL_NAMES[rail]);
                require(node.isSelected() == (rail == index) && node.isFocused() == (rail == index),
                        "Incorrect selected or focused rail");
                node.recycle();
            }
            namedAction(root, screen, page.power(), R.string.sleep_now).recycle();
            namedAction(root, screen, page.wifi(), R.string.wifi).recycle();
            namedAction(root, screen, page.bluetooth(), R.string.bluetooth).recycle();
            namedAction(root, screen, page.timer(), R.string.sleep_timer).recycle();
            if (index == 1) {
                AccessibilityNodeInfo search = visibleNode(root, screen, R.id.apps_search);
                require(search.isEnabled() && search.isEditable()
                                && search.getHintText() != null
                                && screen.getString(R.string.search).contentEquals(search.getHintText()),
                        "Search edit field lacks its localized hint");
                search.recycle();
            }
            if (index < 2) verifyTile(root, screen);
        } finally {
            root.recycle();
        }
    }

    private static AccessibilityNodeInfo ownedRoot(UiAutomation automation, String pkg) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                if (root.getPackageName() != null && pkg.contentEquals(root.getPackageName())) return root;
                root.recycle();
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Owned accessibility window not available");
    }

    private static AccessibilityNodeInfo namedAction(AccessibilityNodeInfo root, MainActivity screen, int id, int label) {
        AccessibilityNodeInfo node = visibleNode(root, screen, id);
        require(node.isEnabled() && node.isFocusable() && node.isClickable()
                        && node.getContentDescription() != null
                        && screen.getString(label).contentEquals(node.getContentDescription()),
                "Missing named action: " + screen.getResources().getResourceEntryName(id));
        return node;
    }

    private static AccessibilityNodeInfo visibleNode(AccessibilityNodeInfo root, Context screen, int id) {
        String resource = screen.getResources().getResourceName(id);
        AccessibilityNodeInfo visible = null;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(resource)) {
            if (node.isVisibleToUser()) {
                require(visible == null, "Duplicate visible node: " + resource);
                visible = node;
            } else node.recycle();
        }
        require(visible != null, "Visible accessibility node missing: " + resource);
        return visible;
    }

    private static void verifyTile(AccessibilityNodeInfo root, MainActivity screen) {
        boolean matched = false;
        for (AccessibilityNodeInfo label : root.findAccessibilityNodeInfosByViewId(
                screen.getResources().getResourceName(R.id.app_name))) {
            AccessibilityNodeInfo tile = label.isVisibleToUser() ? label.getParent() : null;
            if (tile != null) {
                CharSequence name = label.getText();
                CharSequence description = tile.getContentDescription();
                matched |= name != null && name.length() > 0 && description != null
                        && name.toString().contentEquals(description)
                        && tile.isEnabled() && tile.isFocusable() && tile.isClickable() && tile.isLongClickable();
                tile.recycle();
            }
            label.recycle();
        }
        require(matched, "No visible app tile with a matching accessible name and long-click");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

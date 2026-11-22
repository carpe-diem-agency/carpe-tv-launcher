package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Requires an isolated APK that throws inside the production scan try block. */
final class CatalogScanErrorProbe {
    private static final String FAIL_SCAN = "validation_catalog_fail";

    static void run(Instrumentation test, Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Catalog failure probe requires the isolated validation package");
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        require(prefs.edit().remove("apps").putBoolean("welcome_seen", true).putBoolean(FAIL_SCAN, true).commit(),
                "Could not prepare a cold catalog fault");
        test.setInTouchMode(false);
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            SettingsStructureProbe.main(test, () -> screen.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(test, () -> failed(screen) && retry(screen) != null && tiles(screen).isEmpty(),
                    "Cold catalog fault did not show Retry");
            String query = "a";
            SettingsStructureProbe.main(test, () -> {
                EditText search = screen.findViewById(R.id.apps_search);
                require(search.requestFocus(), "Cannot reach search in the error state");
                search.setText(query);
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.main(test, () -> require(screen.getCurrentFocus() == retry(screen),
                    "Down from failed search did not focus Retry"));
            result.putString("catalog_fault_cold", "PASS: failure reached Retry and its D-pad route");
            Button[] previousRetry = {null};
            SettingsStructureProbe.main(test, () -> previousRetry[0] = retry(screen));
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> failed(screen) && retry(screen) != null && retry(screen) != previousRetry[0],
                    "A failed retry did not restore the Retry action");
            SettingsStructureProbe.main(test, () -> {
                EditText search = screen.findViewById(R.id.apps_search);
                require(screen.getCurrentFocus() == search && query.contentEquals(search.getText()),
                        "A failed retry lost the query or its search focus");
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.main(test, () -> require(screen.getCurrentFocus() == retry(screen),
                    "Retry became unreachable after the second failure"));
            result.putString("catalog_fault_failed_retry", "PASS: repeated failure kept search focus and Retry");
            require(prefs.edit().remove(FAIL_SCAN).commit(), "Could not release the catalog fault before Retry");
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> !failed(screen) && !tiles(screen).isEmpty() && retry(screen) == null,
                    "Successful D-pad Retry did not show real filtered packages");
            SettingsStructureProbe.main(test, () -> {
                EditText search = screen.findViewById(R.id.apps_search);
                require(query.contentEquals(search.getText()), "Retry changed the query");
                View current = screen.getCurrentFocus();
                result.putString("catalog_fault_focus_after_retry", current == search ? "search"
                        : current == screen.findViewById(R.id.nav_apps) ? "apps rail"
                        : "id=" + (current == null ? View.NO_ID : current.getId()));
                require(current == search, "Retry recovery did not keep focus at the search editor");
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            List<String> expected = new ArrayList<>();
            View[] focused = {null};
            SettingsStructureProbe.main(test, () -> {
                expected.addAll(tiles(screen));
                focused[0] = screen.getCurrentFocus();
                require(focused[0] != null && expected.contains(focused[0].getTag()),
                        "Down after Retry did not focus a matching tile");
            });
            result.putInt("catalog_fault_recovered_matches", expected.size());
            result.putString("catalog_fault_retry", "PASS: production scan recovered and D-pad reached current results");

            require(prefs.edit().putBoolean(FAIL_SCAN, true).commit(), "Could not arm warm catalog fault");
            SettingsStructureProbe.main(test, () -> scheduler(screen).request(false));
            SettingsStructureProbe.await(test, () -> failed(screen), "Warm scan fault did not reach catch");
            SettingsStructureProbe.main(test, () -> {
                EditText search = screen.findViewById(R.id.apps_search);
                require(expected.equals(tiles(screen)) && retry(screen) == null
                        && screen.getCurrentFocus() == focused[0] && query.contentEquals(search.getText()),
                        "Warm scan failure lost cached results, query or tile focus");
            });
            require(prefs.edit().remove(FAIL_SCAN).commit(), "Could not release the warm catalog fault");
            SettingsStructureProbe.main(test, () -> scheduler(screen).request(false));
            SettingsStructureProbe.await(test, () -> !failed(screen), "Warm scan did not recover");
            SettingsStructureProbe.main(test, () -> require(expected.equals(tiles(screen)) && screen.getCurrentFocus() == focused[0],
                    "Warm recovery replaced the unchanged catalog or tile focus"));
            result.putString("catalog_fault_warm", "PASS: cached results and exact tile focus survived failure and fresh scan");
        } finally {
            SettingsStructureProbe.finish(test, screen);
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    private static boolean failed(MainActivity screen) throws Exception {
        Field field = MainActivity.class.getDeclaredField("appsLoadFailed");
        field.setAccessible(true);
        return field.getBoolean(screen);
    }

    private static CatalogRefresh scheduler(MainActivity screen) throws Exception {
        Field field = MainActivity.class.getDeclaredField("catalogRefresh");
        field.setAccessible(true);
        return (CatalogRefresh) field.get(screen);
    }

    private static Button retry(MainActivity screen) {
        ViewGroup grid = screen.findViewById(R.id.apps_grid);
        for (int i = 0; grid != null && i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof Button && screen.getString(R.string.apps_retry).contentEquals(((Button) child).getText())) {
                return (Button) child;
            }
        }
        return null;
    }

    private static List<String> tiles(MainActivity screen) {
        List<String> packages = new ArrayList<>();
        ViewGroup grid = screen.findViewById(R.id.apps_grid);
        for (int i = 0; grid != null && i < grid.getChildCount(); i++) {
            if (!(grid.getChildAt(i) instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) grid.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                Object tag = row.getChildAt(j).getTag();
                if (tag instanceof String) packages.add((String) tag);
            }
        }
        return packages;
    }

    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        test.waitForIdleSync();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

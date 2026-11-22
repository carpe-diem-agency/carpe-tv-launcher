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
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;

/** Deterministic catalog presentation states; no production hooks or system changes.
 * The error flag is injected: this does not exercise PackageManager throwing in refreshApps.
 */
final class CatalogStateProbe {
    private static final long TIMEOUT_SECONDS = 15;
    private CatalogStateProbe() { }

    static void run(Instrumentation test, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()))
            throw new IllegalStateException("Catalog state probe requires the isolated preview package");
        SharedPreferences preferences = test.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> savedPreferences = new HashMap<>(preferences.getAll());
        CountDownLatch releaseWorker = new CountDownLatch(1);
        MainActivity screen = null;
        ExecutorService worker = null;
        Future<?> blockedTask = null;
        try {
            require(preferences.edit().putBoolean("welcome_seen", true).commit(), "Cannot bypass preview welcome");
            test.setInTouchMode(false);
            screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final MainActivity activity = screen;
            test.waitForIdleSync();
            worker = (ExecutorService) read(activity, "worker");
            drain(test, worker);
            main(test, () -> {
                require(Boolean.TRUE.equals(read(activity, "appsReady")), "Initial catalog scan did not complete");
                require(!((List<?>) read(activity, "allApps")).isEmpty(), "Recovery requires at least one installed app");
            });
            CountDownLatch entered = new CountDownLatch(1);
            blockedTask = worker.submit(() -> {
                entered.countDown();
                try {
                    if (!releaseWorker.await(45, TimeUnit.SECONDS))
                        throw new AssertionError("Catalog worker gate exceeded its safety timeout");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Catalog worker gate interrupted", error);
                }
            });
            require(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Catalog worker gate did not start");
            test.waitForIdleSync();
            CachedCatalogStatusProbe.run(test,activity);
            main(test, () -> {
                write(activity, "allApps", new ArrayList<>());
                write(activity, "displayedApps", new ArrayList<>());
                write(activity, "appsReady", false);
                write(activity, "appsLoadFailed", false);
                write(activity, "query", "");
                activity.findViewById(R.id.nav_apps).requestFocus();
                invoke(activity, "renderPage");
            });
            test.waitForIdleSync();
            main(test, () -> {
                assertLoading(grid(activity));
                EditText search = activity.findViewById(R.id.apps_search);
                search.requestFocus();
                search.setText("catalog-state-no-match-9876");
                assertLoading(grid(activity));
                require(search.hasFocus(), "Typing during loading lost search focus; " + focusState(activity));
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            main(test, () -> {
                require(activity.getCurrentFocus() == activity.findViewById(R.id.apps_search),
                        "Down during loading escaped search; " + focusState(activity));
                write(activity, "appsLoadFailed", true);
                renderLibrary(activity);
                assertMessage(grid(activity), activity.getString(R.string.apps_load_failed));
                require(findButton(grid(activity), activity.getString(R.string.apps_retry)) != null,
                        "Catalog error has no retry action");
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            main(test, () -> {
                require(activity.getCurrentFocus() == findButton(grid(activity), activity.getString(R.string.apps_retry)),
                        "Search did not enter Retry with D-pad Down; " + focusState(activity));
                // Same empty result set, different catalog status: the message must change.
                activity.findViewById(R.id.apps_search).requestFocus();
                write(activity, "appsReady", true);
                write(activity, "appsLoadFailed", false);
                renderLibrary(activity);
                assertMessage(grid(activity), activity.getString(R.string.search_empty));
                require(findButton(grid(activity), activity.getString(R.string.apps_retry)) == null,
                        "Ready empty results retained stale Retry");
                ((EditText) activity.findViewById(R.id.apps_search)).setText("");
                assertMessage(grid(activity), activity.getString(R.string.apps_empty));
                activity.findViewById(R.id.nav_home).requestFocus();
                invoke(activity, "renderPage");
                assertMessage(homeGrid(activity), activity.getString(R.string.favorites_empty));
                write(activity, "appsReady", false);
                invoke(activity, "renderPage");
                assertLoading(homeGrid(activity));
                write(activity, "appsLoadFailed", true);
                invoke(activity, "renderPage");
                assertMessage(homeGrid(activity), activity.getString(R.string.apps_load_failed));
                // Re-enter the library error state and activate the real retry callback.
                activity.findViewById(R.id.nav_apps).requestFocus();
                invoke(activity, "renderPage");
                activity.findViewById(R.id.nav_apps).requestFocus();
            });
            test.waitForIdleSync();
            key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
            main(test, () -> require(activity.getCurrentFocus()
                    == findButton(grid(activity), activity.getString(R.string.apps_retry)),
                    "Apps rail did not enter Retry; " + focusState(activity)));
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            main(test, () -> {
                require(Boolean.FALSE.equals(read(activity, "appsReady"))
                                && Boolean.FALSE.equals(read(activity, "appsLoadFailed")),
                        "Retry did not reset catalog status");
                assertLoading(grid(activity));
            });
            releaseWorker.countDown();
            blockedTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            drain(test, worker);
            main(test, () -> {
                require(Boolean.TRUE.equals(read(activity, "appsReady"))
                                && Boolean.FALSE.equals(read(activity, "appsLoadFailed")),
                        "Retry did not recover after the real package scan");
                require(firstTile(grid(activity)) != null, "Retry did not restore app tiles");
                activity.findViewById(R.id.apps_search).requestFocus();
            });
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            main(test, () -> require(activity.getCurrentFocus() != null
                    && activity.getCurrentFocus().getTag() instanceof String,
                    "Recovered results are unreachable from search; " + focusState(activity)));
            result.putString("catalog_state", "PASS: loading, error, Retry focus, empty/search states, Home states and real retry recovery");
            result.putString("catalog_state_scope", "Injected catalog flags plus exact production failure UI callback; real PackageManager exception throw is not covered; cached loading/error and same-empty recovery use real rail navigation");
        } finally {
            releaseWorker.countDown();
            try {
                if (blockedTask != null) blockedTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                try {
                    if (worker != null && !worker.isShutdown()) drain(test, worker);
                } finally {
                    try {
                        final MainActivity finishing = screen;
                        if (finishing != null) {
                            ExecutorService wallpaperWorker = (ExecutorService) read(finishing, "wallpaperWorker");
                            main(test, finishing::finish);
                            test.waitForIdleSync();
                            if (worker != null)
                                require(worker.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                        "Catalog worker did not terminate after Activity finish");
                            require(wallpaperWorker.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                    "Wallpaper worker did not terminate after Activity finish");
                        }
                    } finally {
                        restorePreferences(preferences, savedPreferences);
                    }
                }
            }
        }
    }

    static void verifyCorruptPreferences(Instrumentation test, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName())) {
            throw new IllegalStateException("Catalog recovery requires the isolated preview package");
        }
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        String corruptApps = "[{";
        String corruptFavorites = "[\"";
        String unknownWallpaper = "validation-unknown-wallpaper";
        MainActivity screen = null;
        try {
            require(prefs.edit().putString("apps", corruptApps)
                    .putString("favorite_order", corruptFavorites)
                    .putString("wallpaper", unknownWallpaper)
                    .putBoolean("welcome_seen", true)
                    .putBoolean(WallpaperRotation.ENABLED, false)
                    .remove("last_opened_package").commit(), "Prepare invalid isolated catalog state");
            test.setInTouchMode(false);
            MainActivity app = screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.await(test, () -> app.hasWindowFocus()
                    && app.findViewById(R.id.nav_home).isSelected()
                    && Boolean.TRUE.equals(read(app, "appsReady"))
                    && !corruptApps.equals(prefs.getString("apps", corruptApps)), "Catalog did not recover after startup");
            main(test, () -> assertMessage(homeGrid(app), app.getString(R.string.favorites_empty)));

            JSONArray repaired = new JSONArray(prefs.getString("apps", "[]"));
            require(repaired.length() > 0, "Catalog was rewritten without installed apps");
            String label = repaired.getJSONObject(0).getString("label");
            require(!label.isBlank(), "Recovered catalog has no searchable label");
            main(test, () -> require(app.findViewById(R.id.nav_apps).requestFocus(), "Cannot enter recovered library"));
            SettingsStructureProbe.await(test, () -> grid(app) != null && firstTile(grid(app)) != null,
                    "Recovered catalog did not show an app tile");
            main(test, () -> {
                EditText search = app.findViewById(R.id.apps_search);
                require(search.requestFocus(), "Cannot focus search after recovery");
                search.setText(label);
            });
            SettingsStructureProbe.await(test, () -> firstTile(grid(app)) != null, "Recovered search has no result");
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            main(test, () -> {
                View tile = app.getCurrentFocus();
                TextView title = tile == null ? null : tile.findViewById(R.id.app_name);
                require(title != null && title.getText().toString().toLowerCase(java.util.Locale.ROOT)
                        .contains(label.toLowerCase(java.util.Locale.ROOT)), "Search did not focus a recovered result");
                require(app.findViewById(R.id.nav_settings).requestFocus(), "Cannot open settings after recovery");
            });
            SettingsStructureProbe.await(test, () -> app.findViewById(R.id.wallpaper_blue) != null
                    && app.findViewById(R.id.wallpaper_blue).isSelected(), "Unknown wallpaper did not fall back to Blue");
            require(unknownWallpaper.equals(prefs.getString("wallpaper", ""))
                    && corruptFavorites.equals(prefs.getString("favorite_order", "")),
                    "Recovery rewrote the saved preferences before an explicit user choice");
            result.putInt("catalog_recovered_count", repaired.length());
            result.putString("catalog_corrupt_preferences", "PASS: truncated catalog and favorites, unknown wallpaper, searchable rebuilt catalog and accessible settings");
        } finally {
            if (screen != null) SettingsStructureProbe.finish(test, screen);
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    /** Never let a probe assertion escape on the app main thread: Android can
     * terminate the process there before this probe's cleanup finally executes.
     */
    private static void main(Instrumentation test, Runnable action) {
        Throwable[] failure = {null};
        test.runOnMainSync(() -> {
            try { action.run(); }
            catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] instanceof Error) throw (Error) failure[0];
        if (failure[0] instanceof RuntimeException) throw (RuntimeException) failure[0];
        if (failure[0] != null) throw new AssertionError("Catalog main-thread callback failed", failure[0]);
    }
    /** Structural IDs and widget types only; never app labels, queries or package tags. */
    private static String focusState(MainActivity activity) {
        View focus = activity.getCurrentFocus();
        View search = activity.findViewById(R.id.apps_search);
        ViewGroup results = grid(activity);
        Button retry = results == null ? null : findButton(results, activity.getString(R.string.apps_retry));
        return "focusId=" + (focus == null ? View.NO_ID : focus.getId())
                + ", focusType=" + (focus == null ? "none" : focus.getClass().getSimpleName())
                + ", searchNextDown=" + (search == null ? View.NO_ID : search.getNextFocusDownId())
                + ", retryId=" + (retry == null ? View.NO_ID : retry.getId());
    }

    private static void drain(Instrumentation test, ExecutorService worker) throws Exception {
        // A scan posts UI updates which can enqueue artwork. Drain both legs without
        // waiting on the executor from the main thread.
        worker.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        test.waitForIdleSync();
        worker.submit(() -> { }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        test.waitForIdleSync();
    }
    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        test.waitForIdleSync();
    }
    private static LinearLayout grid(MainActivity activity) {
        return activity.findViewById(R.id.apps_grid);
    }
    private static LinearLayout homeGrid(MainActivity activity) {
        return (LinearLayout) read(activity, "homeGrid");
    }
    private static void renderLibrary(MainActivity activity) {
        try {
            Method method = MainActivity.class.getDeclaredMethod("renderList", LinearLayout.class, List.class, boolean.class);
            method.setAccessible(true);
            method.invoke(activity, grid(activity), read(activity, "allApps"), false);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object read(MainActivity activity, String name) {
        try { Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void write(MainActivity activity, String name, Object value) {
        try { Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(activity, value); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void invoke(MainActivity activity, String name) {
        try { Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static void assertLoading(ViewGroup grid) {
        require(grid != null && grid.getChildCount() == 1 && grid.getChildAt(0) instanceof ViewGroup,
                "Loading placeholders are missing");
        ViewGroup row = (ViewGroup) grid.getChildAt(0);
        require(row.getChildCount() == 5 && firstTile(grid) == null, "Loading did not show five placeholders");
        require(!hasFocusable(row), "Loading placeholder takes remote focus");
    }
    private static boolean hasFocusable(View view) {
        if (view.isFocusable()) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (hasFocusable(group.getChildAt(i))) return true;
        }
        return false;
    }
    private static View firstTile(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable() && child.getTag() instanceof String) return child;
            if (child instanceof ViewGroup) {
                View found = firstTile((ViewGroup) child); if (found != null) return found;
            }
        }
        return null;
    }
    private static Button findButton(ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Button && text.contentEquals(((Button) child).getText())) return (Button) child;
        }
        return null;
    }
    private static void assertMessage(ViewGroup grid, String text) {
        require(grid != null && firstTile(grid) == null, "Empty catalog retained app tiles");
        require(grid.getChildCount() > 0 && grid.getChildAt(0) instanceof TextView
                && text.contentEquals(((TextView) grid.getChildAt(0)).getText()), "Wrong catalog state message: " + text);
    }
    @SuppressWarnings("unchecked")
    private static void restorePreferences(SharedPreferences preferences, Map<String, ?> saved) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof Set) editor.putStringSet(key, new HashSet<>((Set<String>) value));
            else throw new IllegalStateException("Unexpected preference type for " + key);
        }
        require(editor.commit(), "Cannot restore preview preferences");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

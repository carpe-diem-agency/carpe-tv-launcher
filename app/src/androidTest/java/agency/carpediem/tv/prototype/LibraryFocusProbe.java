package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/** Exercises library search and focus with the installed app list. */
final class LibraryFocusProbe {
    private LibraryFocusProbe() { }

    static void run(Instrumentation probe, Bundle result) {
        if (!probe.getTargetContext().getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Library focus probe requires the isolated preview package");
        SharedPreferences prefs = probe.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = prefs.contains("welcome_seen");
        boolean welcome = prefs.getBoolean("welcome_seen", false);
        prefs.edit().putBoolean("welcome_seen", true).commit();
        try {
            probe.setInTouchMode(false);
            MainActivity screen = (MainActivity) probe.startActivitySync(
                    new Intent(probe.getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(500);
            probe.runOnMainSync(() -> screen.findViewById(R.id.nav_apps).requestFocus());
            key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
            View first = firstTile(screen);
            if (first == null || screen.getCurrentFocus() != first)
                throw new AssertionError("Apps rail did not enter the first library tile");
            String packageName = (String) first.getTag();
            String label = ((TextView) first.findViewById(R.id.app_name)).getText().toString();
            EditText search = screen.findViewById(R.id.apps_search);
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            if (screen.getCurrentFocus() != search)
                throw new AssertionError("Up from the first tile did not reach search");
            probe.runOnMainSync(() -> search.setText(label));
            SystemClock.sleep(150);
            if (!hasTile(screen, packageName))
                throw new AssertionError("A matching app disappeared from search results");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() == search || screen.getCurrentFocus().getTag() == null)
                throw new AssertionError("Down from a matching search did not reach a result");
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            if (screen.getCurrentFocus() != search)
                throw new AssertionError("Up from a search result did not return to search");
            probe.runOnMainSync(() -> search.setText("carpe-tv-no-such-application-9876"));
            SystemClock.sleep(150);
            if (firstTile(screen) != null || screen.getCurrentFocus() != search)
                throw new AssertionError("Empty results lost search focus");
            ViewGroup grid = screen.findViewById(R.id.apps_grid);
            if (grid.getChildCount() != 1 || !(grid.getChildAt(0) instanceof TextView)
                    || !probe.getTargetContext().getString(R.string.search_empty)
                    .contentEquals(((TextView) grid.getChildAt(0)).getText()))
                throw new AssertionError("Empty results did not show the search message");
            probe.runOnMainSync(() -> search.setText(""));
            SystemClock.sleep(150);
            if (firstTile(screen) == null) throw new AssertionError("Clearing search did not restore the app grid");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() != firstTile(screen))
                throw new AssertionError("Down from cleared search did not reach the first app");
            key(probe, KeyEvent.KEYCODE_DPAD_LEFT);
            if (screen.getCurrentFocus() != screen.findViewById(R.id.nav_apps))
                throw new AssertionError("Left from first app did not return to Apps rail");
            result.putString("library_focus", "PASS: rail, matching search, empty results, restored grid and tile navigation");
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadWelcome) restore.putBoolean("welcome_seen", welcome);
            else restore.remove("welcome_seen");
            restore.commit();
        }
    }

    private static void key(Instrumentation probe, int code) {
        probe.sendKeyDownUpSync(code);
        SystemClock.sleep(180);
    }

    private static View firstTile(MainActivity screen) {
        ViewGroup grid = screen.findViewById(R.id.apps_grid);
        if (grid == null) return null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup row = (ViewGroup) child;
                for (int n = 0; n < row.getChildCount(); n++) {
                    View candidate = row.getChildAt(n);
                    if (candidate.getTag() instanceof String && candidate.isFocusable()) return candidate;
                }
            }
        }
        return null;
    }

    private static boolean hasTile(MainActivity screen, String pkg) {
        ViewGroup grid = screen.findViewById(R.id.apps_grid);
        if (grid == null) return false;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) child;
            for (int n = 0; n < row.getChildCount(); n++)
                if (pkg.equals(row.getChildAt(n).getTag())) return true;
        }
        return false;
    }
}

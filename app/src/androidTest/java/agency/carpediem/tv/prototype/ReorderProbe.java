package agency.carpediem.tv.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.*;
import org.json.JSONArray;

/** Actual app menus and D-pad reorder; never launches apps or app details. */
final class ReorderProbe {
    static void verifyCatalogInterruption(Instrumentation test, MainActivity screen, SharedPreferences prefs,
            List<String> favorites, SettingsStructureProbe.Action updateCatalog) throws Exception {
        require(favorites.size() == 6, "Six real favorites required for the short-row move");
        require(prefs.edit().putString("favorite_order", new JSONArray(favorites).toString()).commit(),
                "Could not prepare isolated favorites");
        SettingsStructureProbe.main(test, () -> screen.findViewById(R.id.nav_home).requestFocus());
        SettingsStructureProbe.await(test, () -> rendered(screen).equals(favorites), "Six Home tiles not ready");
        String moving = favorites.get(5);
        focus(test, screen, moving);
        menu(test, screen, R.string.move_app);
        awaitMove(test, screen, moving);
        moveKey(test, screen, KeyEvent.KEYCODE_DPAD_LEFT);
        List<String> preview = new ArrayList<>(favorites);
        Collections.swap(preview, 4, 5);
        SettingsStructureProbe.await(test, () -> rendered(screen).equals(preview), "Move preview missing");
        require(order(prefs).equals(favorites), "Preview changed persisted order");

        long down = SystemClock.uptimeMillis();
        int keyCode = KeyEvent.KEYCODE_DPAD_UP;
        test.sendKeySync(moveEvent(down, keyCode, KeyEvent.ACTION_DOWN, 0));
        try {
            updateCatalog.run();
            SettingsStructureProbe.await(test, () -> field(screen, "movingPackage") == null
                    && rendered(screen).equals(favorites) && order(prefs).equals(favorites)
                    && tiles((View) field(screen, "homeGrid")).stream().noneMatch(tile -> tile.isActivated()
                        || tile.getTranslationX() != 0f || tile.getTranslationY() != 0f),
                    "Catalog change did not cancel the move and restore the original order");
            SettingsStructureProbe.main(test, () -> require(focusedOn(screen, moving),
                    "Cancellation did not restore the moving tile's focus"));
            test.sendKeySync(moveEvent(down, keyCode, KeyEvent.ACTION_DOWN, 1));
        } finally {
            test.sendKeySync(moveEvent(down, keyCode, KeyEvent.ACTION_UP, 0));
        }
        test.waitForIdleSync();
        SettingsStructureProbe.main(test, () -> require(focusedOn(screen, moving),
                "Held D-pad Up escaped into ordinary navigation after catalog cancellation"));
        key(test, keyCode);
        SettingsStructureProbe.await(test, () -> focusedOn(screen, favorites.get(0)),
                "Fresh D-pad Up did not navigate from the restored short row");
    }

    private static KeyEvent moveEvent(long down, int code, int action, int repeat) {
        return new KeyEvent(down, SystemClock.uptimeMillis(), action, code, repeat, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD);
    }

    private static boolean focusedOn(MainActivity screen, String pkg) {
        return screen.hasWindowFocus() && screen.findViewById(R.id.nav_home).isSelected()
                && screen.getCurrentFocus() != null && pkg.equals(screen.getCurrentFocus().getTag());
    }

    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()), "Exact disposable validation package required");
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        MainActivity screen = null;
        Instrumentation.ActivityMonitor monitor = null;
        Throwable primary = null;
        String stage = "prepare";
        try {
            require(prefs.edit().putBoolean("welcome_seen", true).putString("favorite_order", "[]").commit(), "Prepare isolated empty favorites");
            test.setInTouchMode(false);
            screen = (MainActivity)test.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity initial = screen;
            SettingsStructureProbe.main(test, () -> initial.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(test, () -> tiles(initial.findViewById(R.id.apps_grid)).size()>=3, "INPUT_UNVERIFIED: three real installed apps required");
            List<String> selected = new ArrayList<>();
            SettingsStructureProbe.main(test, () ->  {
                List<View> installedTiles = tiles(initial.findViewById(R.id.apps_grid));
                int count = installedTiles.size() >= 6 ? 6 : 3;
                for (int index = 0; index < count; index++) {
                    selected.add((String) installedTiles.get(index).getTag());
                }
            });
            stage = "add actual favorites";
            for (String pkg:selected) {
                focus(test, initial, pkg);
                menu(test, initial, R.string.add_favorite);
                SettingsStructureProbe.await(test, () -> order(prefs).contains(pkg), "Add action did not persist actual package");
            }
            require(order(prefs).equals(selected), "Actual Add menu order differs");
            String moving = selected.get(selected.size()-1);
            stage = "move last boundary and cancel";
            focus(test, initial, moving);
            menu(test, initial, R.string.move_app);
            awaitMove(test, initial, moving);
            moveKey(test, initial, KeyEvent.KEYCODE_DPAD_RIGHT);
            SettingsStructureProbe.main(test, () -> require(rendered(initial).equals(selected)&&moving.equals(initial.getCurrentFocus().getTag()), "Last boundary changed order or selected focus"));
            moveKey(test, initial, KeyEvent.KEYCODE_DPAD_LEFT);
            List<String> changed = new ArrayList<>(selected);
            Collections.swap(changed, changed.size()-2, changed.size()-1);
            SettingsStructureProbe.main(test, () -> require(rendered(initial).equals(changed)&&moving.equals(initial.getCurrentFocus().getTag()), "D-pad move did not update rendered order/focus"));
            require(order(prefs).equals(selected), "Uncommitted move changed stored order");
            key(test, KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test, () -> rendered(initial).equals(selected)&&order(prefs).equals(selected), "Back did not cancel move");
            stage = "commit move";
            focus(test, initial, moving);
            menu(test, initial, R.string.move_app);
            awaitMove(test, initial, moving);
            moveKey(test, initial, KeyEvent.KEYCODE_DPAD_LEFT);
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> order(prefs).equals(changed)&&rendered(initial).equals(changed)&&field(initial, "movingPackage")==null, "OK did not commit rendered/stored order");
            stage = "short row navigation after commit";
            if (changed.size()==6) {
                key(test, KeyEvent.KEYCODE_DPAD_DOWN);
                SettingsStructureProbe.main(test, () -> require(initial.getCurrentFocus()!=null&&changed.get(5).equals(initial.getCurrentFocus().getTag()), "Committed move left stale top-row Down link instead of nearest sixth favorite"));
                result.putString("reorder_short_row_after_commit", "PASS: top-row Down after Move commit reaches nearest sixth tile");
            }
            else result.putString("reorder_short_row_after_commit", "INPUT_UNVERIFIED: six actual installed apps unavailable");
            stage = "recreate committed order";
            monitor = test.addMonitor(MainActivity.class.getName(), null, false);
            SettingsStructureProbe.main(test, initial::recreate);
            Activity replacement = test.waitForMonitorWithTimeout(monitor, 8000);
            require(replacement instanceof MainActivity&&replacement!=initial, "Recreate did not replace Main");
            screen = (MainActivity)replacement;
            MainActivity fresh = screen;
            SettingsStructureProbe.await(test, () -> fresh.hasWindowFocus()&&rendered(fresh).equals(changed)&&order(prefs).equals(changed), "Recreation lost committed order");
            stage = "first boundary";
            String firstPackage = changed.get(0);
            focus(test, fresh, firstPackage);
            menu(test, fresh, R.string.move_app);
            awaitMove(test, fresh, firstPackage);
            moveKey(test, fresh, KeyEvent.KEYCODE_DPAD_LEFT);
            SettingsStructureProbe.main(test, () -> require(rendered(fresh).equals(changed)&&firstPackage.equals(fresh.getCurrentFocus().getTag()), "First boundary changed order or focus"));
            key(test, KeyEvent.KEYCODE_BACK);
            stage = "remove favorites and final empty Home";
            for (String pkg:new ArrayList<>(changed)) {
                focus(test, fresh, pkg);
                menu(test, fresh, R.string.remove_favorite);
                SettingsStructureProbe.await(test, () -> !order(prefs).contains(pkg)&&!rendered(fresh).contains(pkg), "Remove menu did not update stored/rendered favorites");
            }
            require(order(prefs).isEmpty(), "Final favorite removal did not persist empty order");
            SettingsStructureProbe.await(test, () -> fresh.hasWindowFocus()&&fresh.findViewById(R.id.nav_home).isSelected()&&fresh.getCurrentFocus()==fresh.findViewById(R.id.nav_home)&&rendered(fresh).isEmpty(), "Final empty Home lost rail focus/navigation");
            result.putString("reorder", "PASS: actual Add/Move/Remove menus, D-pad reorder, Back cancel without persistence, OK commit, Activity recreation and final favorite removal");
            result.putString("reorder_scope", "Programmatic initial tile focus, real long OK/menu direction/OK and move keys; actual installed app identities; no app launch, app-details or system action");
        }
        catch (Exception|Error error) {
            AssertionError diagnostic = new AssertionError("Favorites stage: " + stage + ": " + error, error);
            primary = diagnostic;
            throw diagnostic;
        }
        finally {
            if (monitor!=null) {
                Activity last = monitor.getLastActivity();
                if (last instanceof MainActivity)screen = (MainActivity)last;
                test.removeMonitor(monitor);
            }
            try {
                if (screen!=null) {
                    SettingsStructureProbe.finish(test, screen);
                    SettingsStructureProbe.quiescent(test, screen);
                }
                SettingsStructureProbe.restore(prefs, saved);
            }
            catch (Exception|Error cleanup) {
                if (primary!=null)primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }
    private static void moveKey(Instrumentation test, MainActivity screen, int code) throws Exception {
        boolean[] escaped = {false};
        View root = screen.getWindow().getDecorView();
        ViewTreeObserver.OnGlobalFocusChangeListener listener = (oldFocus, newFocus) -> {
            if (newFocus == null) return;
            int id = newFocus.getId();
            if (id == R.id.nav_home || id == R.id.nav_apps || id == R.id.nav_settings
                    || id == R.id.home_wifi || id == R.id.home_bluetooth) escaped[0] = true;
        };
        SettingsStructureProbe.main(test, () -> root.getViewTreeObserver().addOnGlobalFocusChangeListener(listener));
        try {
            key(test, code);
        }
        finally {
            SettingsStructureProbe.main(test, () -> {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalFocusChangeListener(listener);
            });
        }
        require(!escaped[0], "Move transiently focused toolbar or rail");
    }
    private static void focus(Instrumentation test, MainActivity screen, String pkg) throws Exception {
        SettingsStructureProbe.main(test, () -> {
            for (View candidate : tiles((ViewGroup) field(screen, "content"))) {
                if (pkg.equals(candidate.getTag())) {
                    require(candidate.requestFocus(), "Actual package tile did not take focus");
                    return;
                }
            }
            throw new AssertionError("Actual package tile unavailable");
        });
    }
    private static void menu(Instrumentation test, MainActivity screen, int action) throws Exception {
        long down = SystemClock.uptimeMillis();
        test.sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        try {
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout()+120);
        }
        finally {
            test.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        }
        SettingsStructureProbe.await(test, () -> modal(screen)!=null, "Real long OK did not open app actions");
        for (int step = 0;step<5;step++) {
            boolean[] reached = {false};
            SettingsStructureProbe.main(test, () -> {
                ViewGroup root = modal(screen);
                View focus = root == null ? null : root.findFocus();
                reached[0] = focus instanceof TextView
                        && screen.getString(action).contentEquals(((TextView) focus).getText());
            });
            if (reached[0]) {
                key(test, KeyEvent.KEYCODE_DPAD_CENTER);
                SettingsStructureProbe.await(test, () -> screen.hasWindowFocus()&&modal(screen)==null, "Action menu did not close");
                return;
            }
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        }
        throw new AssertionError("Requested safe app action not reached");
    }
    private static void awaitMove(Instrumentation test, MainActivity screen, String pkg) throws Exception {
        SettingsStructureProbe.await(test, () -> pkg.equals(field(screen, "movingPackage"))&&screen.hasWindowFocus()&&screen.getCurrentFocus()!=null&&pkg.equals(screen.getCurrentFocus().getTag()), "Actual menu Move did not retain package focus");
    }
    private static ViewGroup modal(MainActivity screen) {
        for (View root:WindowInspector.getGlobalWindowViews())if (root!=screen.getWindow().getDecorView()&&root.isShown()&&root.hasWindowFocus()&&root instanceof ViewGroup)return(ViewGroup)root;
        return null;
    }
    private static List<String> order(SharedPreferences prefs) {
        try {
            JSONArray values = new JSONArray(prefs.getString("favorite_order", "[]"));
            List<String> list = new ArrayList<>();
            for (int i = 0;i<values.length();i++)list.add(values.getString(i));
            return list;
        }
        catch (Exception error) {
            throw new AssertionError(error);
        }
    }
    private static List<String> rendered(MainActivity screen) {
        ViewGroup grid = (ViewGroup)field(screen, "homeGrid");
        List<String> list = new ArrayList<>();
        for (View tile:tiles(grid))list.add((String)tile.getTag());
        return list;
    }
    private static List<View> tiles(View root) {
        List<View> list = new ArrayList<>();
        if (root==null)return list;
        if (root.isFocusable()&&root.getTag() instanceof String)list.add(root);
        else if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0;i<group.getChildCount();i++)list.addAll(tiles(group.getChildAt(i)));
        }
        return list;
    }
    private static Object field(MainActivity screen, String name) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(screen);
        }
        catch (Exception error) {
            throw new AssertionError(error);
        }
    }
    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        test.waitForIdleSync();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

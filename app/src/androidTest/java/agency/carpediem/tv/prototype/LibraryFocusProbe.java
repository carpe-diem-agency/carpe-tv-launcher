package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exercises library search and focus with the installed app list. */
final class LibraryFocusProbe {
    private LibraryFocusProbe() { }

    static void run(Instrumentation probe, Bundle result) throws Exception {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(probe.getTargetContext().getPackageName()))
            throw new IllegalStateException("Library focus probe requires the isolated preview package");
        SharedPreferences prefs = probe.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = prefs.contains("welcome_seen");
        boolean welcome = prefs.getBoolean("welcome_seen", false);
        prefs.edit().putBoolean("welcome_seen", true).commit();
        MainActivity activity = null;
        try {
            probe.setInTouchMode(false);
            MainActivity screen = activity = (MainActivity) probe.startActivitySync(
                    new Intent(probe.getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(500);
            main(probe, () -> screen.findViewById(R.id.nav_apps).requestFocus());
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
            // Check every committed edit rather than hiding intermediate results in one UI task.
            editAndCheck(probe, screen, search, label);
            main(probe, () -> {
                if (!tiles(screen).contains(first))
                    throw new AssertionError("Narrowing search recreated the surviving first app tile");
            });
            // Case changes preserve the exact result set, so neither rows nor tiles should rebuild.
            ViewGroup grid = screen.findViewById(R.id.apps_grid);
            List<View> sameRows = new ArrayList<>();
            main(probe, () -> {
                for (int i = 0; i < grid.getChildCount(); i++) sameRows.add(grid.getChildAt(i));
            });
            editAndCheck(probe, screen, search, label.toLowerCase(Locale.ROOT));
            main(probe, () -> {
                if (sameRows.size() != grid.getChildCount())
                    throw new AssertionError("Equivalent search changed row count");
                for (int i = 0; i < sameRows.size(); i++)
                    if (sameRows.get(i) != grid.getChildAt(i))
                        throw new AssertionError("Equivalent search rebuilt a row");
            });
            editAndCheck(probe, screen, search, label.substring(0, 1));
            editAndCheck(probe, screen, search, label);
            if (!hasTile(screen, packageName))
                throw new AssertionError("A matching app disappeared from search results");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() == search || screen.getCurrentFocus().getTag() == null)
                throw new AssertionError("Down from a matching search did not reach a result");
            key(probe, KeyEvent.KEYCODE_DPAD_UP);
            if (screen.getCurrentFocus() != search)
                throw new AssertionError("Up from a search result did not return to search");
            editAndCheck(probe, screen, search, "carpe-tv-no-such-application-9876");
            if (firstTile(screen) != null || screen.getCurrentFocus() != search)
                throw new AssertionError("Empty results lost search focus");
            if (grid.getChildCount() != 1 || !(grid.getChildAt(0) instanceof TextView)
                    || !probe.getTargetContext().getString(R.string.search_empty)
                    .contentEquals(((TextView) grid.getChildAt(0)).getText()))
                throw new AssertionError("Empty results did not show the search message");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() != search)
                throw new AssertionError("Down from empty results escaped the search field");
            editAndCheck(probe, screen, search, "");
            if (firstTile(screen) == null) throw new AssertionError("Clearing search did not restore the app grid");
            key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
            if (screen.getCurrentFocus() != firstTile(screen))
                throw new AssertionError("Down from cleared search did not reach the first app");
            key(probe, KeyEvent.KEYCODE_DPAD_LEFT);
            if (screen.getCurrentFocus() != screen.findViewById(R.id.nav_apps))
                throw new AssertionError("Left from first app did not return to Apps rail");
            checkDirectionalRepeats(probe, screen, result);
            pressMotion(probe,screen,result);
            // pressMotion finishes on Home; return to the actual library before
            // the independent synthetic short-row navigation fixture.
            main(probe,()->screen.findViewById(R.id.nav_apps).requestFocus());
            checkShortRows(probe, screen);
            result.putString("library_focus", "PASS: rail, sequential edits, stable equivalent results, surviving tile reuse, detached removals, empty results, short-row D-pad navigation and focus links");
        } finally {
            if (activity != null) {
                MainActivity finished = activity;
                main(probe, finished::finishAndRemoveTask);
                probe.waitForIdleSync();
            }
            SharedPreferences.Editor restore = prefs.edit();
            if (hadWelcome) restore.putBoolean("welcome_seen", welcome);
            else restore.remove("welcome_seen");
            restore.commit();
        }
    }

    private static void checkDirectionalRepeats(Instrumentation probe, MainActivity screen, Bundle result) throws Exception {
        List<View> catalog = new ArrayList<>();
        main(probe, () -> catalog.addAll(tiles(screen)));
        if (catalog.size() < 6) {
            result.putString("library_direction_repeats", "INPUT_UNVERIFIED: holding Down needs at least two actual rows");
            return;
        }
        key(probe, KeyEvent.KEYCODE_DPAD_RIGHT);
        awaitTile(probe, screen, catalog.get(0), "Right from Apps rail did not enter the first catalog tile");
        List<View> right = new ArrayList<>();
        for (int step = 1; step <= 8; step++) right.add(catalog.get(Math.min(step, 4)));
        sendHeldDirection(probe, screen, KeyEvent.KEYCODE_DPAD_RIGHT, right);

        List<View> down = new ArrayList<>();
        int index = 4;
        int last = catalog.size() - 1;
        for (int step = 0; step < last / 5 + 3; step++) {
            if (index / 5 < last / 5) index = Math.min(index + 5, last);
            down.add(catalog.get(index));
        }
        sendHeldDirection(probe, screen, KeyEvent.KEYCODE_DPAD_DOWN, down);
        key(probe, KeyEvent.KEYCODE_DPAD_UP);
        awaitTile(probe, screen, catalog.get(index - 5), "Up after releasing Down did not return one row");
        main(probe, () -> {
            EditText search = screen.findViewById(R.id.apps_search);
            if (search == null || search.length() != 0 || !screen.findViewById(R.id.nav_apps).isSelected()) {
                throw new AssertionError("Held direction left the catalog or changed its query");
            }
        });
        result.putString("library_direction_repeats", "PASS: ACTION_DOWN and increasing repeatCount for Right and Down moved through real tiles, stayed at the edges, and released before Up");
        result.putInt("library_direction_repeat_tiles", catalog.size());
    }

    private static void sendHeldDirection(Instrumentation probe, MainActivity screen, int code, List<View> expected) throws Exception {
        long down = SystemClock.uptimeMillis();
        try {
            for (int repeat = 0; repeat < expected.size(); repeat++) {
                if (repeat > 0) SystemClock.sleep(repeat == 1 ? 350 : 55);
                int flags = repeat == 1 ? KeyEvent.FLAG_LONG_PRESS : 0;
                probe.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                        code, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags, InputDevice.SOURCE_DPAD));
                awaitTile(probe, screen, expected.get(repeat), "Held D-pad " + KeyEvent.keyCodeToString(code)
                        + " had unexpected focus at repeat " + repeat);
            }
        } finally {
            probe.sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                    code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD));
        }
        View edge = expected.get(expected.size() - 1);
        SettingsStructureProbe.await(probe, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == edge
                && edge.getGlobalVisibleRect(new Rect()), "Direction release lost the visible edge tile");
    }

    private static void awaitTile(Instrumentation probe, MainActivity screen, View expected, String message) throws Exception {
        SettingsStructureProbe.await(probe, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == expected, message);
    }

    private static void checkShortRows(Instrumentation probe, MainActivity screen) throws Exception {
        java.lang.reflect.Field workerField = MainActivity.class.getDeclaredField("worker");
        workerField.setAccessible(true);
        java.util.concurrent.ExecutorService worker = (java.util.concurrent.ExecutorService) workerField.get(screen);
        worker.submit(() -> { }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        probe.waitForIdleSync();
        Class<?> appType = Class.forName(MainActivity.class.getName() + "$AppItem");
        java.lang.reflect.Constructor<?> constructor = appType.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        java.lang.reflect.Method render = MainActivity.class.getDeclaredMethod("renderList",
                android.widget.LinearLayout.class, List.class, boolean.class);
        render.setAccessible(true);
        // Synthetic, app-local catalog entries only; never launch or install an app.
        for (int count : new int[]{6, 7, 9, 10, 11}) {
            List<Object> catalog = new ArrayList<>();
            for (int i = 0; i < count; i++) catalog.add(constructor.newInstance("test.carpe.shortrow." + i, "Short row " + i));
            main(probe, () -> {
                // Match the real filtering route: edits originate from the search
                // field. Catalog-refresh lifecycle is covered by separate probes.
                screen.findViewById(R.id.apps_search).requestFocus();
                try { render.invoke(screen, screen.findViewById(R.id.apps_grid), catalog, false); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
            probe.waitForIdleSync();
            List<View> shown = new ArrayList<>();
            main(probe, () -> shown.addAll(tiles(screen)));
            if (shown.size() != count) throw new AssertionError("Short-row fixture result count changed");
            int finalRow = (count - 1) / 5;
            int previousStart = (finalRow - 1) * 5;
            for (int col = 0; col < 5; col++) {
                final int column = col;
                View origin = shown.get(previousStart + column);
                View nearest = shown.get(Math.min(previousStart + column + 5, count - 1));
                main(probe, origin::requestFocus);
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                main(probe, () -> {
                    if (screen.getCurrentFocus() != nearest)
                        throw new AssertionError("Down could not reach the short final row: count=" + count + " column=" + column);
                });
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                main(probe, () -> {
                    if (screen.getCurrentFocus() != nearest) throw new AssertionError("Down escaped the final row");
                });
                key(probe, KeyEvent.KEYCODE_DPAD_UP);
                View above = shown.get(shown.indexOf(nearest) - 5);
                main(probe, () -> {
                    if (screen.getCurrentFocus() != above) throw new AssertionError("Up did not follow the visible column");
                });
            }
        }
    }

    private static void main(Instrumentation probe, Runnable task) {
        Throwable[] failure = {null};
        probe.runOnMainSync(() -> { try { task.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError("Library UI assertion: " + failure[0], failure[0]);
    }

    private static void editAndCheck(Instrumentation probe, MainActivity screen,
            EditText search, String query) {
        List<View> before = new ArrayList<>();
        main(probe, () -> {
            before.addAll(tiles(screen));
            search.setText(query);
        });
        probe.waitForIdleSync();
        main(probe, () -> {
            List<View> after = tiles(screen);
            if (screen.getCurrentFocus() != search)
                throw new AssertionError("Search edit stole input focus: " + query);
            String normalized = query.toLowerCase(Locale.ROOT);
            for (View old : before) {
                String name = ((TextView) old.findViewById(R.id.app_name)).getText().toString();
                if (name.toLowerCase(Locale.ROOT).contains(normalized)) {
                    if (!after.contains(old))
                        throw new AssertionError("Surviving search tile was recreated: " + old.getTag());
                } else if (old.isAttachedToWindow()) {
                    throw new AssertionError("Removed search tile is still attached: " + old.getTag());
                }
            }
            for (int i = 0; i < after.size(); i++) {
                View tile = after.get(i);
                String name = ((TextView) tile.findViewById(R.id.app_name)).getText().toString();
                if (!name.toLowerCase(Locale.ROOT).contains(normalized))
                    throw new AssertionError("Stale search result: " + tile.getTag());
                int left = i % 5 == 0 ? R.id.nav_apps : after.get(i - 1).getId();
                int right = i % 5 == 4 || i + 1 == after.size() ? tile.getId() : after.get(i + 1).getId();
                int up = i < 5 ? R.id.apps_search : after.get(i - 5).getId();
                int down = i / 5 < (after.size() - 1) / 5
                        ? after.get(Math.min(i + 5, after.size() - 1)).getId() : tile.getId();
                if (tile.getNextFocusLeftId() != left || tile.getNextFocusRightId() != right
                        || tile.getNextFocusUpId() != up || tile.getNextFocusDownId() != down)
                    throw new AssertionError("Search left stale grid focus links: " + tile.getTag());
            }
            if (!after.isEmpty()) {
                int down = search.getNextFocusDownId();
                boolean linked = false;
                for (View tile : after) if (tile.getId() == down) linked = true;
                if (!linked) throw new AssertionError("Search points to a removed result");
            }
        });
    }

    private static List<View> tiles(MainActivity screen) {
        List<View> result = new ArrayList<>();
        ViewGroup grid = screen.findViewById(R.id.apps_grid);
        if (grid == null) return result;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) child;
            for (int n = 0; n < row.getChildCount(); n++) {
                View tile = row.getChildAt(n);
                if (tile.getTag() instanceof String && tile.isFocusable()) result.add(tile);
            }
        }
        return result;
    }

    private static void pressMotion(Instrumentation probe,MainActivity screen,Bundle result)throws Exception {
        SharedPreferences prefs=screen.getPreferences(android.content.Context.MODE_PRIVATE);
        boolean had=prefs.contains("animations"),previous=prefs.getBoolean("animations",true);
        java.lang.reflect.Method configure=MainActivity.class.getDeclaredMethod("configureStateFade",View.class);configure.setAccessible(true);
        View[] tile={null},art={null};
        try {
            SettingsStructureProbe.main(probe,()->{
                prefs.edit().putBoolean("animations",true).commit();tile[0]=firstTile(screen);art[0]=tile[0].findViewById(R.id.app_artwork);
                configure.invoke(screen,screen.getWindow().getDecorView());
                if(android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    if(!Integer.valueOf(R.animator.library_app_press).equals(art[0].getTag(R.id.press_motion_enabled)))throw new AssertionError("Apps uses wrong press resource");
                }
            });
            if(android.animation.ValueAnimator.areAnimatorsEnabled()) {
                java.util.concurrent.CountDownLatch frames=new java.util.concurrent.CountDownLatch(1);
                java.util.List<Float> samples=new java.util.ArrayList<>();
                SettingsStructureProbe.main(probe,()->{
                    tile[0].setPressed(true);
                    android.view.Choreographer.getInstance().postFrameCallback(new android.view.Choreographer.FrameCallback(){int n;
                        public void doFrame(long time){samples.add(art[0].getAlpha());if(++n<12)android.view.Choreographer.getInstance().postFrameCallback(this);else frames.countDown();}
                    });
                });
                if(!frames.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Press sampling frames timed out");
                boolean intermediate=false;for(float value:samples)if(value>.941f&&value<.999f)intermediate=true;
                if(!intermediate)throw new AssertionError("INPUT_UNVERIFIED: no intermediate Apps press frame observed");
                SettingsStructureProbe.await(probe,()->Math.abs(art[0].getAlpha()-.94f)<.002f,"Apps press did not reach subtle endpoint");
                SettingsStructureProbe.main(probe,()->tile[0].setPressed(false));android.os.SystemClock.sleep(60);
                SettingsStructureProbe.main(probe,()->tile[0].setPressed(true));android.os.SystemClock.sleep(40);
                SettingsStructureProbe.main(probe,()->tile[0].setPressed(false));
                SettingsStructureProbe.await(probe,()->Math.abs(art[0].getAlpha()-1f)<.001f,"Interrupted press failed to settle alpha one");
            }
            // Interrupt while the same card leaves the grid and is pooled.
            SettingsStructureProbe.main(probe,()->tile[0].setPressed(true));android.os.SystemClock.sleep(50);
            SettingsStructureProbe.main(probe,()->{
                EditText search=screen.findViewById(R.id.apps_search);search.requestFocus();
                search.setText("carpe-press-pool-no-match-987654321");
                if(tile[0].getParent()!=null)throw new AssertionError("Pooled pressed tile retains a row");
                tile[0].setPressed(false); // Corresponds to release of the physical key.
                search.setText("");
                if(firstTile(screen)!=tile[0])throw new AssertionError("Press pool scenario did not reuse the first card");
            });
            SettingsStructureProbe.await(probe,()->Math.abs(art[0].getAlpha()-1f)<.001f,"Reinserted pressed card retained dim alpha after release");
            SettingsStructureProbe.main(probe,()->{
                EditText search=screen.findViewById(R.id.apps_search);search.setText("carpe-press-pool-no-match-987654321");
                prefs.edit().putBoolean("animations",false).commit();configure.invoke(screen,screen.getWindow().getDecorView());
                search.setText("");
                if(firstTile(screen)!=tile[0]||art[0].getStateListAnimator()!=null||art[0].getAlpha()!=1f)
                    throw new AssertionError("Animation toggle while pooled failed to reset the reused card");
                prefs.edit().putBoolean("animations",true).commit();configure.invoke(screen,screen.getWindow().getDecorView());
            });
            SettingsStructureProbe.main(probe,()->{
                tile[0].setPressed(true);prefs.edit().putBoolean("animations",false).commit();configure.invoke(screen,art[0]);
                if(art[0].getStateListAnimator()!=null||art[0].getAlpha()!=1f)throw new AssertionError("Animations off did not remove/reset press state");
                tile[0].setPressed(false);screen.findViewById(R.id.nav_home).requestFocus();
            });
            SettingsStructureProbe.main(probe,()->{prefs.edit().putBoolean("animations",true).commit();configure.invoke(screen,screen.getWindow().getDecorView());});
            if(android.animation.ValueAnimator.areAnimatorsEnabled())SettingsStructureProbe.main(probe,()->{
                java.lang.reflect.Field content=MainActivity.class.getDeclaredField("content");content.setAccessible(true);
                View home=((View)content.get(screen)).findViewById(R.id.app_artwork);
                if(home!=null&&!Integer.valueOf(R.animator.app_press).equals(home.getTag(R.id.press_motion_enabled)))throw new AssertionError("Home press resource changed with Apps");
            });
            result.putString("apps_press","PASS: Apps resource scope, sampled intermediate press, interruption/release and animations-off reset; programmatic pressed state, not actual app launch or perceived smoothness");
        } finally {
            SettingsStructureProbe.main(probe,()->{if(tile[0]!=null)tile[0].setPressed(false);if(had)prefs.edit().putBoolean("animations",previous).commit();else prefs.edit().remove("animations").commit();configure.invoke(screen,screen.getWindow().getDecorView());});
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

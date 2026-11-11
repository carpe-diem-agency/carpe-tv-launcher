package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** Regression: favorite tiles must enter the toolbar, never the focus-activated sidebar Settings. */
final class HomeFocusProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        test.setInTouchMode(false);
        // This probe tests navigation after setup, so skip the fresh-install
        // welcome dialog in its isolated test package.
        test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0)
                .edit().putBoolean("welcome_seen",true).commit();
        MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        SystemClock.sleep(1500);
        // The preceding probe may have left a cached Settings page in the task.
        // Enter Home through the same rail path a viewer uses before checking it.
        test.runOnMainSync(() -> screen.findViewById(R.id.nav_home).requestFocus());
        SystemClock.sleep(350);
        List<View> tiles=awaitFavorites(test,screen);
        require(tiles.size()>=2,"At least two favorites needed");
        for(int i=0;i<Math.min(5,tiles.size());i++) {
            // App discovery can replace the grid after Activity startup. Resolve
            // the tile from the currently attached page before each key test.
            final int column=i;
            View[] selected={null};
            test.runOnMainSync(() -> {
                List<View> current=new ArrayList<>();
                collect((ViewGroup)screen.findViewById(R.id.content),current);
                require(current.size()>column,"Favorite tile disappeared before focus check");
                selected[0]=current.get(column);
                require(selected[0].requestFocus(),"Cannot focus attached favorite tile " + column);
                require(screen.getCurrentFocus()==selected[0],"Favorite focus lost before D-pad Up " + column);
            });
            View tile=selected[0];
            test.runOnMainSync(() -> require(screen.findViewById(R.id.home_time)!=null
                    && screen.findViewById(R.id.home_time).isShown(),
                    "Home page hidden before D-pad Up from favorite " + column));
            boolean[] windowFocused={false};
            test.runOnMainSync(() -> windowFocused[0]=screen.hasWindowFocus());
            require(windowFocused[0],"Activity window not focused before D-pad Up from favorite " + column);
            int header = i == 0 ? R.id.home_power : i < 3 ? R.id.home_wifi
                    : i == 3 ? R.id.home_bluetooth : R.id.home_timer;
            key(test,KeyEvent.KEYCODE_DPAD_UP);focus(test,screen,header);
            key(test,KeyEvent.KEYCODE_DPAD_UP);focus(test,screen,header);
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);focus(test,screen,tile.getId());
        }
        tiles=awaitFavorites(test,screen);
        String homeTile = (String) tiles.get(Math.min(4, tiles.size() - 1)).getTag();
        test.runOnMainSync(() -> screen.findViewById(R.id.nav_apps).requestFocus());
        SystemClock.sleep(300);
        test.runOnMainSync(screen::onBackPressed);
        SystemClock.sleep(300);
        test.runOnMainSync(() -> require(screen.getCurrentFocus() != null
                && homeTile.equals(screen.getCurrentFocus().getTag()),
                "Back from library must return to the last Home tile"));
        View artwork=tiles.get(0).findViewById(R.id.app_artwork);
        boolean[] animated={false},reduced={false};test.runOnMainSync(() -> {
            animated[0]=AppLaunchMotion.options(artwork,true)!=null;
            reduced[0]=AppLaunchMotion.options(artwork,false)==null && AppLaunchMotion.options(null,true)==null;
        });
        require(animated[0]&&reduced[0],"Visible launch origin and reduced-motion fallback");
        String previous=screen.getPreferences(0).getString("last_opened_package",null);
        try {
            String last=(String)tiles.get(0).getTag();
            screen.getPreferences(0).edit().putString("last_opened_package",last).commit();
            test.runOnMainSync(()->screen.onNewIntent(new Intent(screen,MainActivity.class).putExtra("from_home",true)));
            SystemClock.sleep(350);
            test.runOnMainSync(()->require(screen.getCurrentFocus()!=null && last.equals(screen.getCurrentFocus().getTag()),"Home restores last opened tile"));
            // Exercise the real pause/resume path through another Android app.
            // The public preview is not the device's Home role, so this checks
            // lifecycle return; physical Home routing is a separate release gate.
            test.runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field awaiting=MainActivity.class.getDeclaredField("launchedAppAwaitingReturn");
                    awaiting.setAccessible(true); awaiting.set(screen,last);
                    screen.startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(700);
            key(test,KeyEvent.KEYCODE_BACK);
            SystemClock.sleep(700);
            test.runOnMainSync(() -> require(screen.hasWindowFocus() && screen.getCurrentFocus()!=null
                    && last.equals(screen.getCurrentFocus().getTag()),
                    "Returning from Android Settings must restore the last opened tile"));
            // A background app-list refresh can land just after Home and must not
            // override the restored tile with the old rail focus.
            test.runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("pendingHomePackage");pending.setAccessible(true);pending.set(screen,last);
                    java.lang.reflect.Field displayed=MainActivity.class.getDeclaredField("displayedApps");displayed.setAccessible(true);displayed.set(screen,null);
                    java.lang.reflect.Field all=MainActivity.class.getDeclaredField("allApps");all.setAccessible(true);
                    screen.findViewById(R.id.nav_home).requestFocus();
                    java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("showApps",List.class);refresh.setAccessible(true);
                    refresh.invoke(screen,all.get(screen));
                    require(screen.getCurrentFocus()!=null && last.equals(screen.getCurrentFocus().getTag()),"Refresh must retain last opened tile");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            // A package update must keep the currently focused tile, not jump
            // to the first favorite or to the sidebar.
            test.runOnMainSync(() -> {
                try {
                    List<View> currentTiles = new ArrayList<>();
                    collect((ViewGroup) screen.findViewById(R.id.content), currentTiles);
                    View second = currentTiles.get(1);
                    String focused = (String) second.getTag();
                    second.requestFocus();
                    java.lang.reflect.Field displayed = MainActivity.class.getDeclaredField("displayedApps");
                    displayed.setAccessible(true); displayed.set(screen, null);
                    java.lang.reflect.Field all = MainActivity.class.getDeclaredField("allApps");
                    all.setAccessible(true);
                    java.lang.reflect.Method refresh = MainActivity.class.getDeclaredMethod("showApps", List.class);
                    refresh.setAccessible(true); refresh.invoke(screen, all.get(screen));
                    require(screen.getCurrentFocus() != null && focused.equals(screen.getCurrentFocus().getTag()),
                            "App-list refresh must retain the focused tile");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            test.runOnMainSync(()->screen.findViewById(R.id.nav_settings).requestFocus());
            SystemClock.sleep(350);
            test.runOnMainSync(()->screen.findViewById(R.id.settings_power).performClick());
            SystemClock.sleep(350);
            focus(test,screen,R.id.nav_home);
            test.runOnMainSync(() -> {
                View activeSettings = screen.findViewById(R.id.content).findViewById(R.id.settings_detail);
                View cachedSettings = screen.findViewById(R.id.settings_detail);
                require(activeSettings == null && (cachedSettings == null || !cachedSettings.isShown()),
                        "Settings content must be hidden when Power is activated");
            });
            checkToolbar(test, screen, R.id.nav_home, R.id.home_power, R.id.home_wifi,
                    R.id.home_bluetooth, R.id.home_timer);
            checkToolbar(test, screen, R.id.nav_apps, R.id.apps_power, R.id.apps_wifi,
                    R.id.apps_bluetooth, R.id.apps_timer);
            checkToolbar(test, screen, R.id.nav_settings, R.id.settings_power, R.id.settings_wifi,
                    R.id.settings_bluetooth, R.id.settings_timer);
        } finally {
            if(previous==null)screen.getPreferences(0).edit().remove("last_opened_package").commit();
            else screen.getPreferences(0).edit().putString("last_opened_package",previous).commit();
        }
        result.putString("home_focus","PASS: toolbar order, Back/Home and external-app return retain tile focus, app refresh, Power and launch motion valid");
    }
    private static void checkToolbar(Instrumentation test, MainActivity screen, int page, int power,
                                     int wifi, int bluetooth, int timer) {
        test.runOnMainSync(() -> screen.findViewById(page).requestFocus());
        SystemClock.sleep(250);
        test.runOnMainSync(() -> screen.findViewById(power).requestFocus());
        for (int expected : new int[]{wifi, bluetooth, timer}) {
            key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
            int[] actual={View.NO_ID};
            test.runOnMainSync(() -> {
                View focused=screen.getCurrentFocus();
                if (focused != null) actual[0]=focused.getId();
            });
            require(actual[0] == expected, "Toolbar focus " + actual[0] + " expected " + expected);
        }
    }
    private static void collect(ViewGroup group,List<View> tiles) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child.isFocusable() && child.getTag() instanceof String && child.findViewById(R.id.app_artwork)!=null) tiles.add(child);
            else if(child instanceof ViewGroup) collect((ViewGroup)child,tiles);
        }
    }
    private static List<View> awaitFavorites(Instrumentation test,MainActivity screen) {
        List<View> tiles=new ArrayList<>();
        long deadline=SystemClock.uptimeMillis()+10000;
        do {
            tiles.clear();
            test.runOnMainSync(() -> collect((ViewGroup)screen.findViewById(R.id.content),tiles));
            if(tiles.size()>=2) {
                SystemClock.sleep(350);
                List<View> stable=new ArrayList<>();
                test.runOnMainSync(() -> collect((ViewGroup)screen.findViewById(R.id.content),stable));
                if(stable.size()==tiles.size() && stable.get(0)==tiles.get(0)) return stable;
            }
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<deadline);
        return tiles;
    }
    private static void key(Instrumentation test,int key) {test.sendKeyDownUpSync(key);SystemClock.sleep(150);}
    private static void focus(Instrumentation test,Activity screen,int id) {
        int[] actual={View.NO_ID};boolean[] home={false};test.runOnMainSync(() -> {if(screen.getCurrentFocus()!=null)actual[0]=screen.getCurrentFocus().getId();View heading=screen.findViewById(R.id.home_time);home[0]=heading!=null&&heading.isShown();});
        require(actual[0]==id && home[0],"Unexpected focus "+actual[0]+" expected "+id+"; home="+home[0]);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
}

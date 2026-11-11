package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.ImageView;

/** Deterministic idle boundaries plus real screen launch and remote dismissal. */
final class ScreensaverProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        test.setInTouchMode(false);
        SharedPreferences prefs = Screensaver.prefs(test.getTargetContext());
        boolean hadDelay = prefs.contains(Screensaver.TIMEOUT), hadProvider = prefs.contains(Screensaver.PROVIDER);
        boolean hadOnline=prefs.contains("saverOnline"),oldOnline=prefs.getBoolean("saverOnline",true);
        boolean hadReserve=prefs.contains("saverReserveMb");int oldReserve=prefs.getInt("saverReserveMb",1024);
        int oldDelay = prefs.getInt(Screensaver.TIMEOUT, 5);
        String oldProvider = prefs.getString(Screensaver.PROVIDER,"builtin");
        try {
            prefs.edit().putBoolean("saverOnline",false).putInt(Screensaver.TIMEOUT, 1).putString(Screensaver.PROVIDER,"builtin").commit();
            int[] fired = {0};
            test.runOnMainSync(() -> {
                IdleTimer timer = new IdleTimer(() -> fired[0]++);
                timer.activate(60000); timer.expireAt(SystemClock.uptimeMillis()+1000);
                timer.reset(); timer.expireAt(SystemClock.uptimeMillis()+1000);
                require(fired[0]==0,"No early launch after input");
                timer.stop(); timer.expireAt(SystemClock.uptimeMillis()+120000);
                require(fired[0]==0,"No launch while inactive");
                timer.activate(0); timer.expireAt(SystemClock.uptimeMillis()+120000);
                require(fired[0]==0,"Off remains off");
                timer.activate(60000); timer.expireAt(SystemClock.uptimeMillis()+60001);
                timer.expireAt(SystemClock.uptimeMillis()+120000);
                require(fired[0]==1,"Exactly one launch at expiry"); timer.stop();
            });
            Activity home = test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1500);
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("idleTimer"); field.setAccessible(true);
            IdleTimer idle = (IdleTimer) field.get(home);
            boolean[] missing = {false};
            test.runOnMainSync(() -> missing[0] = !Screensaver.tryExternal(home,new ComponentName("invalid.missing.saver","invalid.missing.saver.Main")));
            require(missing[0],"Missing optional provider handled");
            Instrumentation.ActivityMonitor monitor = test.addMonitor(ScreensaverActivity.class.getName(),null,false);
            test.runOnMainSync(() -> idle.expireAt(SystemClock.uptimeMillis()+61000));
            Activity saver = test.waitForMonitorWithTimeout(monitor,4000); test.removeMonitor(monitor);
            require(saver!=null,"Idle launches built-in saver");
            // Explicitly select an offline slide even when the device already has downloaded media.
            test.runOnMainSync(()->{try {
                java.lang.reflect.Field slides=ScreensaverActivity.class.getDeclaredField("slides"),index=ScreensaverActivity.class.getDeclaredField("index");
                java.lang.reflect.Method show=ScreensaverActivity.class.getDeclaredMethod("showSlide");slides.setAccessible(true);index.setAccessible(true);show.setAccessible(true);
                java.util.List<?> list=(java.util.List<?>)slides.get(saver);
                for(int i=0;i<list.size();i++) {java.lang.reflect.Field resource=list.get(i).getClass().getDeclaredField("resource");resource.setAccessible(true);if(resource.getInt(list.get(i))!=0){index.setInt(saver,i);show.invoke(saver);break;}}
            }catch(Exception e){throw new RuntimeException(e);}});
            boolean[] photo = {false};
            for (int i=0;i<40&&!photo[0];i++) {
                test.runOnMainSync(() -> {
                    android.view.ViewGroup root = (android.view.ViewGroup)saver.findViewById(android.R.id.content);
                    photo[0] = hasPhoto(root);
                });
                if(!photo[0]) SystemClock.sleep(100);
            }
            require(photo[0],"Offline full-screen photo decoded");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); SystemClock.sleep(400);
            boolean[] dismissed = {false}; test.runOnMainSync(() -> dismissed[0]=saver.isFinishing()||saver.isDestroyed());
            require(dismissed[0],"Remote dismisses without opening another page");
            // Changing a setting must not let automatic focus escape into the focus-activated sidebar.
            prefs.edit().putInt("saverReserveMb",512).commit();
            java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("openPage",String.class);open.setAccessible(true);
            test.runOnMainSync(()->{try {open.invoke(home,"settings");}catch(Exception e){throw new RuntimeException(e);}});
            SystemClock.sleep(350);
            test.runOnMainSync(()->{
                android.view.ViewGroup categories=home.findViewById(R.id.settings_categories);
                for(int i=0;i<categories.getChildCount();i++) if(((android.widget.Button)categories.getChildAt(i)).getText().toString().equals(home.getString(R.string.saver_title))) categories.getChildAt(i).requestFocus();
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(250);
            int[] focus={0};test.runOnMainSync(()->focus[0]=home.getCurrentFocus()==null?0:home.getCurrentFocus().getId());
            require(focus[0]==R.id.saver_timeout,"Category Right enters first setting even after scrolling");
            for(int i=0;i<5;i++){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);SystemClock.sleep(100);}
            test.runOnMainSync(()->focus[0]=home.getCurrentFocus()==null?0:home.getCurrentFocus().getId());
            require(focus[0]==R.id.saver_reserve,"Sequential settings navigation reaches reserve");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(150);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);SystemClock.sleep(250);
            boolean[] stayed={false};test.runOnMainSync(()->{
                android.view.View heading=home.findViewById(R.id.settings_heading);
                stayed[0]=heading!=null&&heading.isShown()&&home.getCurrentFocus()!=null&&home.getCurrentFocus().getId()==R.id.saver_online;
            });
            require(stayed[0],"Selection keeps Settings open and restores control focus");
            // Losing focus must stop the launcher timer even if another app/dialog is on screen.
            test.runOnMainSync(() -> { home.onWindowFocusChanged(false); idle.expireAt(SystemClock.uptimeMillis()+120000); });
            SystemClock.sleep(200);
            result.putString("screensaver","PASS: early/reset/off/inactive/one-shot idle; missing provider; real offline photo launch; remote dismiss");
        } finally {
            SharedPreferences.Editor edit = prefs.edit();
            if(hadDelay) edit.putInt(Screensaver.TIMEOUT,oldDelay); else edit.remove(Screensaver.TIMEOUT);
            if(hadProvider) edit.putString(Screensaver.PROVIDER,oldProvider); else edit.remove(Screensaver.PROVIDER);
            if(hadOnline) edit.putBoolean("saverOnline",oldOnline);else edit.remove("saverOnline");
            if(hadReserve) edit.putInt("saverReserveMb",oldReserve);else edit.remove("saverReserveMb");
            edit.commit();
        }
    }
    private static boolean hasPhoto(android.view.ViewGroup group) {
        for(int i=0;i<group.getChildCount();i++) {
            android.view.View v=group.getChildAt(i);
            if(v instanceof ImageView && ((ImageView)v).getDrawable()!=null) return true;
            if(v instanceof android.view.ViewGroup && hasPhoto((android.view.ViewGroup)v)) return true;
        }
        return false;
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}

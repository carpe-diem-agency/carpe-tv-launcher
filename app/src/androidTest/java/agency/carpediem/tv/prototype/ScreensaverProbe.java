package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Deterministic idle boundaries plus real screen launch and remote dismissal. */
final class ScreensaverProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),"Screensaver probe requires the disposable validation package");
        CorruptSaverVideoProbe.run(test,result);
        test.setInTouchMode(false);
        SharedPreferences prefs = Screensaver.prefs(test.getTargetContext());
        SharedPreferences activityPrefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll()),activitySaved=new HashMap<>(activityPrefs.getAll());
        Activity[] sessions=new Activity[2];Throwable primary=null;
        try {
            require(activityPrefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare isolated welcome state");
            require(prefs.edit().putBoolean("saverOnline",false).putInt(Screensaver.TIMEOUT, 1).putString(Screensaver.PROVIDER,"builtin").commit(),"Prepare offline screensaver fixture");
            int[] fired = {0};
            SettingsStructureProbe.main(test, () -> {
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
            Activity home = sessions[0] = test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1500);
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("idleTimer"); field.setAccessible(true);
            IdleTimer idle = (IdleTimer) field.get(home);
            boolean[] missing = {false};
            SettingsStructureProbe.main(test, () -> missing[0] = !Screensaver.tryExternal(home,new ComponentName("invalid.missing.saver","invalid.missing.saver.Main")));
            require(missing[0],"Missing optional provider handled");
            Instrumentation.ActivityMonitor monitor = test.addMonitor(ScreensaverActivity.class.getName(),null,false);
            SettingsStructureProbe.main(test, () -> idle.expireAt(SystemClock.uptimeMillis()+61000));
            Activity saver = sessions[1] = test.waitForMonitorWithTimeout(monitor,4000); test.removeMonitor(monitor);
            require(saver!=null,"Idle launches built-in saver");
            // Explicitly select an offline slide even when the device already has downloaded media.
            SettingsStructureProbe.main(test, ()->{try {
                java.lang.reflect.Field slides=ScreensaverActivity.class.getDeclaredField("slides"),index=ScreensaverActivity.class.getDeclaredField("index");
                java.lang.reflect.Method show=ScreensaverActivity.class.getDeclaredMethod("showSlide");slides.setAccessible(true);index.setAccessible(true);show.setAccessible(true);
                java.util.List<?> list=(java.util.List<?>)slides.get(saver);
                for(int i=0;i<list.size();i++) {java.lang.reflect.Field resource=list.get(i).getClass().getDeclaredField("resource");resource.setAccessible(true);if(resource.getInt(list.get(i))!=0){index.setInt(saver,i);show.invoke(saver);break;}}
            }catch(Exception e){throw new RuntimeException(e);}});
            boolean[] photo = {false};
            for (int i=0;i<40&&!photo[0];i++) {
                SettingsStructureProbe.main(test, () -> {
                    android.view.ViewGroup root = (android.view.ViewGroup)saver.findViewById(android.R.id.content);
                    photo[0] = hasPhoto(root);
                });
                if(!photo[0]) SystemClock.sleep(100);
            }
            require(photo[0],"Offline full-screen photo decoded");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); SystemClock.sleep(400);
            boolean[] dismissed = {false}; SettingsStructureProbe.main(test, () -> dismissed[0]=saver.isFinishing()||saver.isDestroyed());
            require(dismissed[0],"Remote dismisses without opening another page");
            // The old reserve preference is ignored by the automatic 1 GiB policy.
            require(prefs.edit().putInt("saverReserveMb",512).commit(),"Prepare legacy reserve preference");
            require(SaverCache.reserveMb(home)==1024,"Legacy reserve value must not reduce automatic reserve");
            SettingsStructureProbe.select(test,(MainActivity)home,1);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{
                require(home.getCurrentFocus()!=null&&home.getCurrentFocus().getId()==R.id.saver_timeout,"Category Right enters first setting after scrolling");
                require(home.findViewById(R.id.saver_reserve)==null,"Removed reserve selector is still visible");
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->require(home.getCurrentFocus()!=null&&home.getCurrentFocus().getId()==R.id.saver_provider,"Down reaches source choice after timeout"));
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();
            SettingsStructureProbe.await(test,()->{
                for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.view.View focused=root.findFocus();
                    if(root!=home.getWindow().getDecorView()&&root.hasWindowFocus()&&focused instanceof android.widget.TextView
                            &&home.getString(R.string.saver_builtin).contentEquals(((android.widget.TextView)focused).getText()))return true;
                }
                return false;
            },"Built-in source sheet did not establish focus");
            // Built-in is already selected: choose the same safe app-only option.
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();
            SettingsStructureProbe.await(test,()->home.hasWindowFocus()&&home.getCurrentFocus()!=null
                    &&home.getCurrentFocus().getId()==R.id.saver_provider,"Source choice did not restore Settings focus");
            require("builtin".equals(prefs.getString(Screensaver.PROVIDER,"builtin")),"Safe source choice changed provider");
            checkSettingsChoices(test, (MainActivity) home, prefs, result);
            // Losing focus must stop the launcher timer even if another app/dialog is on screen.
            SettingsStructureProbe.main(test, () -> { home.onWindowFocusChanged(false); idle.expireAt(SystemClock.uptimeMillis()+120000); });
            SystemClock.sleep(200);
            result.putString("screensaver","PASS: early/reset/off/inactive/one-shot idle; missing provider; real offline photo launch; remote dismiss");
        } catch(Exception|Error failure) {primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(sessions[1]!=null) try {
                Activity saver=sessions[1];SettingsStructureProbe.main(test,()->{if(!saver.isDestroyed())saver.finish();});
                java.lang.reflect.Field decoder=ScreensaverActivity.class.getDeclaredField("decoder");decoder.setAccessible(true);
                ExecutorService worker=(ExecutorService)decoder.get(saver);worker.shutdownNow();
                require(worker.awaitTermination(15,TimeUnit.SECONDS),"Screensaver decoder did not terminate");
                SettingsStructureProbe.await(test,saver::isDestroyed,"Screensaver fixture did not finish");
            } catch(Exception|Error failure) {cleanup=failure;}
            if(sessions[0]!=null) try {SettingsStructureProbe.finish(test,(MainActivity)sessions[0]);}
            catch(Exception|Error failure) {if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try {
                if(sessions[0]!=null)SettingsStructureProbe.quiescent(test,(MainActivity)sessions[0]);
                SettingsStructureProbe.restore(prefs,saved);SettingsStructureProbe.restore(activityPrefs,activitySaved);
            } catch(Exception|Error failure) {if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }

    private static void checkSettingsChoices(Instrumentation test, MainActivity home,
            SharedPreferences prefs, Bundle result) throws Exception {
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        awaitSetting(test, home, R.id.saver_timeout);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 1), true);
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        awaitChoice(test, home, delayLabel(home, 0), false);
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        awaitChoice(test, home, delayLabel(home, 0), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDelay(test, home, 0);

        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 0), true);
        for (int minutes : new int[]{1, 3, 5, 10, 15, 30}) {
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            awaitChoice(test, home, delayLabel(home, minutes), false);
        }
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitChoice(test, home, home.getString(R.string.cancel), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDelay(test, home, 0);

        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 0), true);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitChoice(test, home, delayLabel(home, 3), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDelay(test, home, 3);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 3), true);
        key(test, KeyEvent.KEYCODE_BACK);
        awaitDelay(test, home, 3);

        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitSetting(test, home, R.id.saver_provider);
        boolean aerialAvailable = Screensaver.aerialAvailable(home);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, home.getString(R.string.saver_builtin), true);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitChoice(test, home, home.getString(R.string.saver_aerial), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitSetting(test, home, R.id.saver_provider);
        String expectedProvider = aerialAvailable ? "aerial" : "builtin";
        int expectedLabel = aerialAvailable ? R.string.saver_aerial : R.string.saver_builtin;
        SettingsStructureProbe.main(test, () -> {
            require(expectedProvider.equals(prefs.getString(Screensaver.PROVIDER, null)), "Unexpected provider stored after choosing Aerial Views");
            TextView source = home.findViewById(R.id.saver_provider);
            require(home.getString(R.string.saver_source, home.getString(expectedLabel)).contentEquals(source.getText()),
                    "Source row label did not match the stored provider");
        });
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, home.getString(expectedLabel), true);
        key(test, KeyEvent.KEYCODE_BACK);
        awaitSetting(test, home, R.id.saver_provider);
        require(expectedProvider.equals(prefs.getString(Screensaver.PROVIDER, null)), "Closing provider choice changed the stored provider");
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        awaitSetting(test, home, R.id.saver_timeout);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 3), true);
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        awaitChoice(test, home, delayLabel(home, 0), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDelay(test, home, 0);
        result.putString("saver_settings_choices", "PASS: all timeout rows, Off and 3-minute selections, Cancel and Back, selected markers on reopen, provider focus and stored choice");
        result.putString("saver_aerial_selection", aerialAvailable ? "PASS: available provider selected" : "PASS: unavailable provider kept built-in source");
        if (aerialAvailable) {
            UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            checkAerialPreview(test, home, automation, result);
            checkAerialIdle(test, home, automation, result);
        } else {
            result.putString("saver_aerial_preview", "INPUT_UNVERIFIED: Aerial Views activity is unavailable");
            result.putString("saver_aerial_idle", "INPUT_UNVERIFIED: Aerial Views activity is unavailable");
        }
    }

    private static void checkAerialPreview(Instrumentation test, MainActivity home,
            UiAutomation automation, Bundle result) throws Exception {
        for (int id : new int[]{R.id.saver_provider, R.id.saver_online, R.id.saver_preview}) {
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            awaitSetting(test, home, id);
        }
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitAerialFocus(automation, SystemClock.uptimeMillis() + 8000);
        backFromAerial(test, home, automation, R.id.saver_preview, 0);
        result.putString("saver_aerial_preview", "PASS: Preview OK focused exact Aerial Views TestActivity; Back restored the same Carpe TV Settings Activity and Preview focus");
    }

    private static void checkAerialIdle(Instrumentation test, MainActivity home,
            UiAutomation automation, Bundle result) throws Exception {
        for (int id : new int[]{R.id.saver_online, R.id.saver_provider, R.id.saver_timeout}) {
            key(test, KeyEvent.KEYCODE_DPAD_UP);
            awaitSetting(test, home, id);
        }
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitChoice(test, home, delayLabel(home, 0), true);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitChoice(test, home, delayLabel(home, 1), false);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDelay(test, home, 1);
        SystemClock.sleep(8000);
        awaitSetting(test, home, R.id.saver_timeout);
        long lastInput = SystemClock.uptimeMillis();
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        awaitSetting(test, home, R.id.saver_provider);

        // Observe past the original deadline after an actual D-pad action resets it.
        long beforeDue = lastInput + 59_000;
        while (SystemClock.uptimeMillis() < beforeDue) {
            SettingsStructureProbe.main(test, () -> require(home.hasWindowFocus()
                    && home.getCurrentFocus() == home.findViewById(R.id.saver_provider),
                    "Launcher lost focus before the reset idle deadline"));
            long remaining = beforeDue - SystemClock.uptimeMillis();
            if (remaining > 0) SystemClock.sleep(Math.min(500, remaining));
        }
        awaitAerialFocus(automation, lastInput + 70_000);
        long observedMs = SystemClock.uptimeMillis() - lastInput;
        require(observedMs >= 60_000, "Aerial Views received focus early after D-pad input");
        backFromAerial(test, home, automation, R.id.saver_provider, 1);
        result.putString("saver_aerial_idle", "PASS: real one-minute idle after D-pad reset; Aerial Views focused and Back restored provider focus");
        result.putLong("saver_aerial_idle_observed_ms", observedMs);
    }

    private static void awaitAerialFocus(UiAutomation automation, long deadline) throws Exception {
        do {
            if (isAerialFocused(automation)) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("External Aerial Views TestActivity did not receive focus");
    }

    private static void backFromAerial(Instrumentation test, MainActivity home,
            UiAutomation automation, int originId, int expectedMinutes) throws Exception {
        long now = SystemClock.uptimeMillis();
        require(automation.injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true),
                "Aerial Back down rejected");
        require(automation.injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true),
                "Aerial Back up rejected");
        awaitSetting(test, home, originId);
        SettingsStructureProbe.main(test, () -> {
            require(home.findViewById(R.id.nav_settings).isSelected() && home.findViewById(originId).isShown(),
                    "Back from Aerial did not restore the visible control in Settings");
            require("aerial".equals(Screensaver.prefs(home).getString(Screensaver.PROVIDER, null))
                    && Screensaver.minutes(home) == expectedMinutes, "Aerial changed the source or timeout");
        });
    }

    private static boolean isAerialFocused(UiAutomation automation) throws Exception {
        ParcelFileDescriptor output = automation.executeShellCommand("dumpsys window displays");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(output), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.trim().startsWith("mCurrentFocus=Window{")
                        && (line.endsWith(" " + Screensaver.AERIAL.flattenToString() + "}")
                        || line.endsWith(" " + Screensaver.AERIAL.flattenToShortString() + "}"))) return true;
            }
            return false;
        }
    }

    private static String delayLabel(MainActivity home, int minutes) {
        if (minutes == 0) return home.getString(R.string.saver_off);
        String label = home.getString(R.string.saver_minutes, minutes);
        return minutes == Screensaver.DEFAULT_MINUTES ? label + home.getString(R.string.saver_recommended) : label;
    }

    private static void awaitDelay(Instrumentation test, MainActivity home, int minutes) throws Exception {
        awaitSetting(test, home, R.id.saver_timeout);
        SettingsStructureProbe.main(test, () -> {
            TextView setting = home.findViewById(R.id.saver_timeout);
            require(Screensaver.prefs(home).getInt(Screensaver.TIMEOUT, -1) == minutes, "Unexpected saved timeout");
            require(home.getString(R.string.saver_after, delayLabel(home, minutes)).contentEquals(setting.getText()),
                    "Timeout row label did not match the stored value");
        });
    }

    private static void awaitSetting(Instrumentation test, MainActivity home, int id) throws Exception {
        SettingsStructureProbe.await(test, () -> home.hasWindowFocus() && home.getCurrentFocus() != null
                && home.getCurrentFocus() == home.findViewById(id), "Focus did not reach screensaver setting " + id);
    }

    private static void awaitChoice(Instrumentation test, MainActivity home, String label, boolean selected) throws Exception {
        SettingsStructureProbe.await(test, () -> {
            for (View root : WindowInspector.getGlobalWindowViews()) {
                if (!(root instanceof ViewGroup) || root == home.getWindow().getDecorView() || !root.hasWindowFocus()) continue;
                View focused = root.findFocus();
                if (focused instanceof TextView && label.contentEquals(((TextView) focused).getText())
                        && focused.isSelected() == selected && focused.isShown()) return true;
            }
            return false;
        }, "Choice did not receive dialog focus with the expected selected state: " + label);
    }

    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        test.waitForIdleSync();
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

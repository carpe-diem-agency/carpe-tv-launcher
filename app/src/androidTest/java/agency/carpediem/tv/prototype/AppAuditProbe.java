package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Read-only package states and row navigation; no package/Home/system action is invoked. */
final class AppAuditProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        run(test, result, false);
    }

    static void run(Instrumentation test,Bundle result, boolean appManagementRoute) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Audit probe requires the disposable validation package");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity activity=null;Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare isolated welcome state");
            test.setInTouchMode(false);
            MainActivity screen=activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.select(test,screen,3);
            SettingsStructureProbe.await(test,()->screen.auditRefreshStats().applied>0&&!screen.auditRefreshStats().inFlight,
                    "Audit package states did not load");
            AppAudit.Snapshot snapshot=AppAudit.read(screen);
            require(snapshot.states.size()==4,"Audit must report exactly four known package states");
            View[] category={null};
            SettingsStructureProbe.main(test,()->{
                View root=screen.findViewById(R.id.settings_detail);
                require(!hasText(root,screen.getString(R.string.choose_home)),"Home chooser must be grouped under Device");
                require(!hasText(root,screen.getString(R.string.audit_refresh)),"Audit retains a removed manual refresh action");
                require(screen.findViewById(R.id.shizuku_status)==null,"Audit retains removed system-access controls");
                require(root.findViewById(R.id.audit_manage_apps)!=null,"Manage all apps is missing");
                for(String pkg:AppAudit.PACKAGES) {
                    TextView state=root.findViewWithTag("audit-state:"+pkg);require(state!=null,"Audit package row is missing");
                    View row=(View)state.getParent();TextView name=row.findViewById(R.id.audit_app_name);
                    require(name!=null&&name.getRight()<=state.getLeft(),"Audit name/status columns overlap");
                    require(name.getBottom()<=row.getHeight()&&state.getBottom()<=row.getHeight(),"Audit row text is clipped");
                    require(!name.isFocusable()&&!state.isFocusable()&&row.isFocusable(),"Audit row has more than one remote focus stop");
                    Integer value=snapshot.states.get(pkg);int expected=value==null||value==0?R.string.audit_unavailable:value==1?R.string.audit_enabled:R.string.audit_disabled;
                    require(screen.getString(expected).contentEquals(state.getText()),"Audit row does not match actual package state");
                }
                ViewGroup categories=screen.findViewById(R.id.settings_categories);category[0]=categories.getChildAt(3);category[0].requestFocus();
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{
                View focus=screen.getCurrentFocus();
                require(focus!=null&&("audit:"+AppAudit.PACKAGES[0]).equals(focus.getTag()),"Right must enter the first package row instead of a system-access action");
                Field displayed=MainActivity.class.getDeclaredField("displayedApps");displayed.setAccessible(true);displayed.set(screen,null);
                Field apps=MainActivity.class.getDeclaredField("allApps");apps.setAccessible(true);
                Method show=MainActivity.class.getDeclaredMethod("showApps",java.util.List.class);show.setAccessible(true);
                View tree=screen.findViewById(R.id.settings_detail);show.invoke(screen,apps.get(screen));
                require(tree==screen.findViewById(R.id.settings_detail)&&focus==screen.getCurrentFocus(),"Catalog refresh rebuilt Audit or stole focus");
            });
            result.putString("audit","PASS: four actual package states, non-overlapping row layout, one focus per row, simplified actions and catalog-refresh focus preservation; no mutations");
            if (appManagementRoute) {
                verifyPackageDetails(test, screen, snapshot, result);
                verifyAppManagement(test, screen, snapshot, result);
            }
        } catch(Exception|Error failure) {primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(activity!=null)try{SettingsStructureProbe.finish(test,activity);}catch(Exception|Error failure){cleanup=failure;}
            try {if(activity!=null)SettingsStructureProbe.quiescent(test,activity);SettingsStructureProbe.restore(prefs,saved);}
            catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static void verifyPackageDetails(Instrumentation test, MainActivity screen,
            AppAudit.Snapshot before, Bundle result) throws Exception {
        String packageName = AppAudit.PACKAGES[0];
        require(before.states.get(packageName) != 0, "Xiaomi Analytics unavailable");
        View row = screen.getCurrentFocus();
        require(row != null && ("audit:" + packageName).equals(row.getTag()), "Xiaomi Analytics row not focused");
        String toggle = screen.getString(before.states.get(packageName) == 1 ? R.string.system_disable : R.string.system_enable);
        String manage = screen.getString(R.string.system_app_settings);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> choiceFocused(screen, toggle), "Audit choices did not open");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == row,
                "Back from audit choices did not restore the row");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> choiceFocused(screen, toggle), "Audit choices did not reopen");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test, () -> choiceFocused(screen, manage), "Down did not focus Manage in Android");
        PackageManager pm = screen.getPackageManager();
        String appLabel = pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                .loadLabel(pm).toString();
        verifyAndroidDestination(test, ".device.apps.AppManagementActivity", appLabel, "audit_package_details_destination", result);
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == row
                && screen.findViewById(R.id.nav_settings).isSelected() && screen.auditRefreshStats().active,
                "Back from Android app details did not restore the audited row");
        require(before.states.equals(AppAudit.read(screen).states), "Audited program states changed");
        result.putString("audit_package_details_route", "PASS: choice Back, Android details for Xiaomi Analytics, Back to exact row");
    }

    private static boolean choiceFocused(MainActivity screen, String expected) {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            if (root == screen.getWindow().getDecorView() || !root.hasWindowFocus()) continue;
            View focus = root.findFocus();
            if (focus instanceof TextView && expected.contentEquals(((TextView) focus).getText())) return true;
        }
        return false;
    }

    private static void verifyAppManagement(Instrumentation test, MainActivity screen,
            AppAudit.Snapshot before, Bundle result) throws Exception {
        for (int i = 0; i < AppAudit.PACKAGES.length; i++) {
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            test.waitForIdleSync();
        }
        View action = screen.findViewById(R.id.audit_manage_apps);
        SettingsStructureProbe.main(test, () -> require(screen.getCurrentFocus() == action
                && action instanceof TextView && screen.getString(R.string.audit_all_apps)
                .contentEquals(((TextView) action).getText()), "D-pad did not focus Manage all apps"));
        verifyAndroidDestination(test, ".device.apps.AllAppsActivity", null, "audit_manage_apps_destination", result);
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == action
                && screen.findViewById(R.id.nav_settings).isSelected() && screen.auditRefreshStats().active,
                "Back did not restore the same Manage all apps action and category");
        require(before.states.equals(AppAudit.read(screen).states), "Audited program states changed");
        result.putString("audit_manage_apps_route", "PASS: D-pad opened Android All Apps, Back restored the same action");
    }

    static void verifyAndroidDestination(Instrumentation test, String component, String appLabel,
            String witnessKey, Bundle result) throws Exception {
        UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        long deadline = SystemClock.uptimeMillis() + 8000;
        boolean settingsVisible = false;
        boolean screenReady = false;
        String resumed = "unavailable";
        String destination = "com.android.tv.settings/" + component + " ";
        try {
            while (SystemClock.uptimeMillis() < deadline && !screenReady) {
                AccessibilityNodeInfo root = automation.getRootInActiveWindow();
                if (root != null) {
                    try {
                        settingsVisible = "com.android.tv.settings".contentEquals(root.getPackageName());
                        screenReady = settingsVisible && (appLabel == null
                                || !root.findAccessibilityNodeInfosByText(appLabel).isEmpty());
                    } finally { root.recycle(); }
                }
                if (!screenReady) SystemClock.sleep(100);
            }
            require(screenReady, "Expected system settings window or app label did not appear");
            do {
                resumed = resumedSettingsActivity(automation);
                if (resumed.contains(destination)) break;
                SystemClock.sleep(100);
            } while (SystemClock.uptimeMillis() < deadline);
            result.putString(witnessKey, resumed);
            require(resumed.contains(destination),
                    "Unexpected system settings destination: " + resumed);
        } finally {
            if (settingsVisible) {
                long now = SystemClock.uptimeMillis();
                for (int actionType : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                    KeyEvent back = new KeyEvent(now, SystemClock.uptimeMillis(), actionType, KeyEvent.KEYCODE_BACK, 0);
                    back.setSource(InputDevice.SOURCE_DPAD);
                    require(automation.injectInputEvent(back, true), "System Settings Back was rejected");
                }
            }
        }
    }

    private static String resumedSettingsActivity(UiAutomation automation) throws Exception {
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("dumpsys activity activities"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String activity = "unavailable";
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ResumedActivity") && line.contains("com.android.tv.settings/")) {
                    activity = line.trim();
                }
            }
            return activity;
        }
    }
    private static boolean hasText(View root,String text) {
        if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return true;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),text))return true;}return false;
    }
    static Object readField(Object target,String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception error){return error;}}
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

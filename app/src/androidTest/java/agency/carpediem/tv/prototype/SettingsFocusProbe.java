package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/** Checks remote focus transitions between the settings rail, detail and header. */
final class SettingsFocusProbe {
    static void run(Instrumentation test, Bundle result) {
        test.setInTouchMode(false);
        test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0)
                .edit().putBoolean("welcome_seen", true).commit();
        MainActivity activity = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        test.runOnMainSync(() -> activity.findViewById(R.id.nav_settings).requestFocus());
        SystemClock.sleep(400);
        ViewGroup categories = activity.findViewById(R.id.settings_categories);
        View appearance = categories.getChildAt(0);
        test.runOnMainSync(appearance::requestFocus);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        require(activity.getCurrentFocus() == categories.getChildAt(1), "Down leaves the settings category rail");
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        require(activity.getCurrentFocus() == appearance, "Up does not return to the category");
        key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
        View detail = activity.findViewById(R.id.settings_detail);
        require(isWithin(activity.getCurrentFocus(), detail), "Right does not enter detail");
        View first = activity.getCurrentFocus();
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        require(activity.getCurrentFocus().getId() == R.id.settings_power, "Up from first detail misses header: first="
                + first.getId() + " next=" + first.getNextFocusUpId() + " actual=" + activity.getCurrentFocus().getId());
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        require(activity.getCurrentFocus() == first, "Down from header misses detail");
        key(test, KeyEvent.KEYCODE_DPAD_LEFT);
        require(activity.getCurrentFocus() == appearance, "Left from detail misses category");
        key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
        key(test, KeyEvent.KEYCODE_BACK);
        require(activity.getCurrentFocus() == appearance, "Back from detail misses category");
        result.putString("settings_focus", "PASS: categories, detail, header and Back use predictable D-pad lanes");
    }
    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        SystemClock.sleep(140);
    }
    private static boolean isWithin(View focus, View ancestor) {
        for (View current = focus; current != null; current = current.getParent() instanceof View
                ? (View) current.getParent() : null) if (current == ancestor) return true;
        return false;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

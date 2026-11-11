package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Exercises the setup wizard with D-pad keys without starting a pairing session. */
final class SystemSetupFocusProbe {
    static void run(Instrumentation test, Bundle result) {
        test.setInTouchMode(false);
        SystemSetupActivity activity = (SystemSetupActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), SystemSetupActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            SystemClock.sleep(250);
            List<Button> actions = new ArrayList<>();
            collectButtons(activity.getWindow().getDecorView(), actions);
            require(actions.size() >= 2, "Visible setup actions missing");
            require(activity.getString(R.string.setup_pair).contentEquals(actions.get(0).getText()),
                    "First action is not phone pairing");
            require(activity.getString(R.string.close).contentEquals(actions.get(actions.size() - 1).getText()),
                    "Last action is not Close");
            require(activity.getCurrentFocus() == actions.get(0), "Pairing action did not receive initial focus");
            for (int i = 1; i < actions.size(); i++) {
                key(test, KeyEvent.KEYCODE_DPAD_DOWN);
                require(activity.getCurrentFocus() == actions.get(i), "Down missed visible action " + i);
            }
            for (int i = actions.size() - 2; i >= 0; i--) {
                key(test, KeyEvent.KEYCODE_DPAD_UP);
                require(activity.getCurrentFocus() == actions.get(i), "Up missed visible action " + i);
            }
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            SystemClock.sleep(350);
            View root = activity.getWindow().getDecorView();
            for (int labelId : new int[] {R.string.setup_guide_scan,
                    R.string.setup_guide_settings, R.string.setup_guide_enter}) {
                TextView label = findText(root, activity.getString(labelId));
                require(label != null && label.isShown(), "Pairing instruction missing: " + labelId);
                android.graphics.Rect visible = new android.graphics.Rect();
                require(label.getGlobalVisibleRect(visible) && visible.height() == label.getHeight(),
                        "Pairing instruction is off screen: " + labelId);
            }
            TextView settingsAction = findText(root, activity.getString(R.string.setup_open_settings));
            require(settingsAction != null && settingsAction.isShown(),
                    "TV settings action missing beside the guide");
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            require(activity.getCurrentFocus() == settingsAction,
                    "Down from QR did not reach TV settings action");
            result.putString("system_setup_focus", "PASS: all visible actions reached in order and D-pad returned to first");
        } finally {
            test.runOnMainSync(activity::finish);
        }
    }

    private static TextView findText(View root, String wanted) {
        if (root instanceof TextView && wanted.contentEquals(((TextView) root).getText()))
            return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectButtons(View root, List<Button> result) {
        if (root instanceof Button && root.isShown() && root.isEnabled()) result.add((Button) root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectButtons(group.getChildAt(i), result);
            }
        }
    }

    private static void key(Instrumentation test, int code) {
        test.sendKeyDownUpSync(code);
        SystemClock.sleep(140);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

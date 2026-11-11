package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Method;

/** Verify the issue QR dialog is readable to accessibility and Back restores focus. */
final class FeedbackProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        test.setInTouchMode(false);
        test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0)
                .edit().putBoolean("welcome_seen", true).commit();
        MainActivity screen = (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        test.runOnMainSync(() -> screen.findViewById(R.id.nav_settings).requestFocus());
        SystemClock.sleep(350);
        test.runOnMainSync(() -> {
            try {
                Method open = MainActivity.class.getDeclaredMethod("showFeedback");
                open.setAccessible(true); open.invoke(screen);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        AccessibilityNodeInfo dialog = null;
        for (int i = 0; i < 20; i++) {
            SystemClock.sleep(100);
            dialog = test.getUiAutomation().getRootInActiveWindow();
            if (dialog != null && contains(dialog, screen.getString(R.string.issue_qr))) break;
        }
        require(dialog != null && contains(dialog, screen.getString(R.string.issue_qr)),
                "Issue QR code is missing from the active dialog");
        require(contains(dialog, screen.getString(R.string.open_issue)),
                "Issue browser fallback is missing");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        SystemClock.sleep(250);
        test.runOnMainSync(() -> {
            View focus = screen.getCurrentFocus();
            require(focus != null && focus.getId() == R.id.nav_settings,
                    "Closing issue dialog did not restore previous focus");
        });
        result.putString("feedback", "PASS: GitHub Issue QR, browser fallback and Back focus");
    }
    private static boolean contains(AccessibilityNodeInfo node, String value) {
        if ((node.getText() != null && value.contentEquals(node.getText()))
                || (node.getContentDescription() != null && value.contentEquals(node.getContentDescription()))) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && contains(child, value)) return true;
        }
        return false;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

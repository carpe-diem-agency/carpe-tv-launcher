package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
        String[] templates = {"bug.yml", "idea.yml", "other.yml"};
        int[] labels = {R.string.feedback_bug, R.string.feedback_idea, R.string.feedback_other};
        for (int i = 0; i < templates.length; i++) {
            AccessibilityNodeInfo choice = find(dialog, screen.getString(labels[i]));
            require(choice != null, "Feedback type is missing: " + templates[i]);
            require(choice.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    "Feedback type cannot be selected: " + templates[i]);
            SystemClock.sleep(180);
            dialog = test.getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo qr = findDescription(dialog, screen.getString(R.string.issue_qr));
            require(qr != null, "QR is missing after selecting " + templates[i]);
            Rect bounds = new Rect(); qr.getBoundsInScreen(bounds);
            Bitmap shot = test.getUiAutomation().takeScreenshot();
            require(shot != null && bounds.width() > 0 && bounds.height() > 0,
                    "Could not capture feedback QR");
            Bitmap region = Bitmap.createBitmap(shot, bounds.left, bounds.top,
                    bounds.width(), bounds.height());
            int[] pixels = new int[region.getWidth() * region.getHeight()];
            region.getPixels(pixels, 0, region.getWidth(), 0, 0, region.getWidth(), region.getHeight());
            String decoded = new com.google.zxing.MultiFormatReader().decode(
                    new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                            new com.google.zxing.RGBLuminanceSource(region.getWidth(), region.getHeight(), pixels))))
                    .getText();
            require(decoded.equals("https://github.com/carpe-diem-agency/carpe-tv-launcher/issues/new?template=" + templates[i]),
                    "Feedback QR opens wrong template: " + decoded);
            region.recycle(); shot.recycle();
        }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        SystemClock.sleep(250);
        test.runOnMainSync(() -> {
            View focus = screen.getCurrentFocus();
            require(focus != null && focus.getId() == R.id.nav_settings,
                    "Closing issue dialog did not restore previous focus");
        });
        result.putString("feedback", "PASS: three rendered GitHub Issue QRs, browser fallback and Back focus");
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String value) {
        if (node == null) return null;
        if (node.getText() != null && value.contentEquals(node.getText())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = find(node.getChild(i), value);
            if (child != null) return child;
        }
        return null;
    }
    private static AccessibilityNodeInfo findDescription(AccessibilityNodeInfo node, String value) {
        if (node == null) return null;
        if (node.getContentDescription() != null && value.contentEquals(node.getContentDescription())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = findDescription(node.getChild(i), value);
            if (child != null) return child;
        }
        return null;
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

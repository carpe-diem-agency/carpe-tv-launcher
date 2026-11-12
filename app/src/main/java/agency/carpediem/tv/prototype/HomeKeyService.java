package agency.carpediem.tv.prototype;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Optional, user-enabled shortcut for OEM remotes that send a Home key event. */
public final class HomeKeyService extends AccessibilityService {
    private boolean launchFailed;
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (launchFailed || event.getKeyCode() != KeyEvent.KEYCODE_HOME || !HomeChoice.isSelected(this)) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(new Intent(this, MainActivity.class)
                        .putExtra("from_home", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            } catch (RuntimeException ignored) {
                launchFailed = true;
                return false;
            }
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}

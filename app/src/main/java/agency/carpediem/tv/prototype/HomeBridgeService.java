package agency.carpediem.tv.prototype;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/** Optional OEM Home fallback. It observes only Google TV window changes. */
public final class HomeBridgeService extends AccessibilityService {
    private long lastLaunch;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getPackageName() == null || !HomeChoice.isSelected(this)
                || !"com.google.android.apps.tv.launcherx".contentEquals(event.getPackageName())) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 800) return;
        lastLaunch = now;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .putExtra("from_home", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        } catch (RuntimeException ignored) { }
    }

    @Override public void onInterrupt() { }
}

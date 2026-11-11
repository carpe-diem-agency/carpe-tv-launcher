package agency.carpediem.tv.prototype;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** One foreground-only callback. Never polls or runs while another window has focus. */
final class IdleTimer {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable action;
    private long timeout, lastInput;
    private boolean active;
    private final Runnable tick = () -> expireAt(SystemClock.uptimeMillis());
    IdleTimer(Runnable action) { this.action = action; }
    void activate(long timeoutMillis) {
        timeout = timeoutMillis; active = timeout > 0; reset();
    }
    void stop() { active = false; handler.removeCallbacks(tick); }
    void reset() {
        handler.removeCallbacks(tick);
        lastInput = SystemClock.uptimeMillis();
        if (active) handler.postDelayed(tick, timeout);
    }
    void expireAt(long now) {
        handler.removeCallbacks(tick);
        if (!active) return;
        long remaining = timeout - (now - lastInput);
        if (remaining > 0) handler.postDelayed(tick, remaining);
        else { active = false; action.run(); }
    }
}

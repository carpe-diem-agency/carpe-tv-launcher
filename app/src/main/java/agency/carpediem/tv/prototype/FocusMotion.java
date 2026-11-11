package agency.carpediem.tv.prototype;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Interruptible property animations; no per-frame application callbacks or bitmap work. */
final class FocusMotion {
    private static final Interpolator ENTER = new PathInterpolator(.22f, .61f, .36f, 1);
    private static final Interpolator EXIT = new PathInterpolator(.25f, .1f, .25f, 1);
    private final View card, ring;
    FocusMotion(View tile, View artwork) {
        card = artwork; ring = artwork.findViewById(R.id.focus_outline); ring.setAlpha(0);
        tile.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) {}
            public void onViewDetachedFromWindow(View view) { cancel(); }
        });
    }
    private void cancel() { card.animate().cancel(); ring.animate().cancel(); }
    void setFocused(boolean focused, boolean enabled) {
        cancel();
        float scale = focused ? 1.025f : 1f;
        float alpha = focused ? 1 : 0;
        if (!enabled || !ValueAnimator.areAnimatorsEnabled()) {
            card.setScaleX(scale); card.setScaleY(scale); ring.setAlpha(alpha); return;
        }
        // Cross-fade the old and new focus while the label stays on its baseline.
        // Retarget from the current value on rapid presses; never reset to an endpoint.
        Interpolator curve = focused ? ENTER : EXIT;
        card.animate().withLayer().scaleX(scale).scaleY(scale).setInterpolator(curve)
                .setDuration(focused ? 240 : 200).start();
        ring.animate().alpha(alpha).setInterpolator(curve)
                .setDuration(focused ? 200 : 180).start();
    }
}

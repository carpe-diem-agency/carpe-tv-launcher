package agency.carpediem.tv.prototype;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

/** A subtle alpha pulse; no video, downloads, or per-frame bitmap decoding. */
final class BlueAtmosphere {
    private final View layer;
    private ObjectAnimator pulse;
    BlueAtmosphere(View layer) {
        this.layer = layer;
        layer.setTranslationZ(1.5f);
    }
    void show(boolean enabled) {
        if (!enabled) {
            if (pulse != null) { pulse.cancel(); pulse = null; }
            layer.setVisibility(View.GONE);
            return;
        }
        layer.setVisibility(View.VISIBLE);
        if (pulse != null) return;
        layer.setAlpha(.18f);
        pulse = ObjectAnimator.ofFloat(layer, View.ALPHA, .18f, .52f);
        pulse.setDuration(11000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new PathInterpolator(.42f, 0f, .58f, 1f));
        pulse.start();
    }
}

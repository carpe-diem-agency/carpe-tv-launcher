package agency.carpediem.tv.prototype;

import android.graphics.drawable.Drawable;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.view.View;

/** Two plain image nodes: opacity runs on RenderThread, without root invalidation. */
final class BackgroundMotion {
    private ImageView front, back;
    private boolean initialized;
    BackgroundMotion(ImageView first, ImageView second, View shell) {
        front = first; back = second;
        shell.setTranslationZ(2); front.setTranslationZ(1); back.setTranslationZ(0);
    }
    void show(Drawable drawable, boolean animate) {
        front.animate().cancel(); back.animate().cancel();
        if (!initialized || !animate) {
            front.setImageDrawable(drawable); front.setAlpha(1f);
            back.setAlpha(0f); back.setImageDrawable(null); initialized = true; return;
        }
        // During rapid input retain whichever scene is already most visible.
        if (front.getAlpha() < .5f && back.getDrawable() != null) {
            ImageView temporary = front; front = back; back = temporary;
        }
        front.setAlpha(1f);
        ImageView previous = front, next = back;
        previous.setTranslationZ(0); next.setTranslationZ(1);
        next.setImageDrawable(drawable); next.setAlpha(0f);
        front = next; back = previous;
        next.animate().alpha(1).setStartDelay(0).setDuration(200)
                .setInterpolator(new PathInterpolator(.22f, .61f, .36f, 1))
                .withEndAction(() -> { if (back == previous) { previous.setAlpha(0f); previous.setImageDrawable(null); } }).start();
    }
    void cancel() { front.animate().cancel(); back.animate().cancel(); }
}

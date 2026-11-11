package agency.carpediem.tv.prototype;

import android.app.ActivityOptions;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

/** Framework window animation, anchored to the visible artwork; no bitmap snapshot or launch delay. */
final class AppLaunchMotion {
    static Bundle options(View artwork, boolean enabled) {
        if(!enabled || artwork==null || !artwork.isAttachedToWindow()) return null;
        Rect visible=new Rect();
        if(!artwork.getLocalVisibleRect(visible) || visible.isEmpty()) return null;
        return ActivityOptions.makeScaleUpAnimation(artwork,visible.left,visible.top,visible.width(),visible.height()).toBundle();
    }
}

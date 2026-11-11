package agency.carpediem.tv.prototype;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/** Page opacity can be applied to its individual cards without a full-screen offscreen buffer. */
public final class PageContent extends LinearLayout {
    public PageContent(Context context) { super(context); }
    public PageContent(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override public boolean hasOverlappingRendering() { return false; }
}

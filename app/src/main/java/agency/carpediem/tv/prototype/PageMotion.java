package agency.carpediem.tv.prototype;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Three cached page trees; transition drawing is limited to the incoming page. */
final class PageMotion {
    private final FrameLayout host;
    private LinearLayout outgoing;
    private View incoming;
    PageMotion(FrameLayout host) { this.host = host; }

    LinearLayout prepare(LinearLayout current, LinearLayout next, boolean enabled, int direction) {
        current.animate().cancel();
        // During rapid D-pad navigation, finish the previous transition before
        // showing the new page. Drawing two animated page trees stalls on TV GPUs.
        boolean animate = enabled && outgoing == null && current.getWidth() > 0 && current.getHeight() > 0;
        if (next != null && next == outgoing) { outgoing.animate().cancel(); outgoing = null; }
        if (outgoing == null) outgoing = current;
        else if (current != outgoing) current.setVisibility(View.INVISIBLE);
        outgoing.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (next == null) {
            next = new PageContent(current.getContext());
            next.setId(R.id.content); next.setOrientation(LinearLayout.VERTICAL);
            next.setPadding(current.getPaddingLeft(), current.getPaddingTop(), current.getPaddingRight(), current.getPaddingBottom());
            next.setClipChildren(false); next.setClipToPadding(false);
            host.addView(next, new FrameLayout.LayoutParams(-1, -1));
        }
        next.animate().cancel();
        next.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        next.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        next.setVisibility(View.VISIBLE);
        next.setAlpha(animate ? .72f : 1); next.setTranslationY(animate ? direction * dp(4) : 0);
        outgoing.setTranslationZ(0); next.setTranslationZ(1);
        incoming = next;
        if (animate) outgoing.setVisibility(View.INVISIBLE);
        else { clearOutgoing(); incoming = null; }
        return next;
    }
    void discardInactive(LinearLayout current) {
        clearOutgoing(); current.animate().cancel(); current.setAlpha(1); current.setTranslationY(0);
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i); if (child != current) host.removeViewAt(i);
        }
        current.setVisibility(View.VISIBLE);
    }
    void discardCached(View page) {
        if (page == outgoing) clearOutgoing();
        if (page.getParent() == host) host.removeView(page);
    }

    void play(LinearLayout current, int direction) {
        if (outgoing == null) return;
        View previous = outgoing;
        current.animate().alpha(1).translationY(0).setStartDelay(0).setDuration(180)
                .setInterpolator(new PathInterpolator(.22f, .61f, .36f, 1))
                .withEndAction(() -> { if (outgoing == previous) clearOutgoing(); }).start();
    }
    private float dp(int value) { return value * host.getResources().getDisplayMetrics().density; }
    private void clearOutgoing() {
        if (outgoing == null) return;
        View old = outgoing; outgoing = null;
        old.animate().cancel(); old.setVisibility(View.INVISIBLE);
    }
    void cancel() {
        clearOutgoing();
        if (incoming != null) { incoming.animate().cancel(); incoming = null; }
    }
}

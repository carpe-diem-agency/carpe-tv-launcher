package agency.carpediem.tv.prototype;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

/** Moves only the translucent glow above the static wallpaper. */
public final class BlueWallpaperMotion extends ImageView {
    private static final long HALF_CYCLE_MS = 6000;
    private static final float TRAVEL = .035f;
    // The glow artwork includes 7% extra height at each edge so the moving
    // texture still covers the entire viewport at either end of its travel.
    private static final float ARTWORK_SCALE_Y = 1.14f;
    private final AccelerateDecelerateInterpolator easing = new AccelerateDecelerateInterpolator();
    private boolean resumed;
    private boolean motionEnabled;
    private boolean blueVisible;
    private int direction = -1;
    private Bitmap stillScene;

    public BlueWallpaperMotion(Context context, AttributeSet attributes) {
        super(context, attributes);
        setScaleY(ARTWORK_SCALE_Y);
    }

    boolean canUseStillWallpaper() {
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
        return width > 0 && height > 0 && Math.abs(width * 9 - height * 16) <= 16;
    }

    Drawable stillWallpaper() {
        if (stillScene == null) {
            // Compose only when the viewer uses a still wallpaper. Both original
            // layers fill their bounds, so the compact texture can stretch to
            // the viewport without a crop or a second blended layer.
            int width = 800;
            int height = 450;
            Bitmap scene = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scene);
            Drawable gradient = getContext().getDrawable(R.drawable.blue_wallpaper).mutate();
            Drawable glow = getContext().getDrawable(R.drawable.blue_wallpaper_glow).mutate();
            gradient.setBounds(0, 0, width, height);
            gradient.draw(canvas);
            canvas.scale(1f, ARTWORK_SCALE_Y, width / 2f, height / 2f);
            glow.setBounds(0, 0, width, height);
            glow.draw(canvas);
            scene.setHasAlpha(false);
            stillScene = scene;
        }
        BitmapDrawable drawable = new BitmapDrawable(getResources(), stillScene) {
            @Override public int getIntrinsicWidth() { return -1; }
            @Override public int getIntrinsicHeight() { return -1; }
        };
        drawable.setGravity(Gravity.FILL);
        drawable.setFilterBitmap(true);
        return drawable;
    }

    void setScene(boolean blueVisible, boolean motionEnabled) {
        if (this.blueVisible == blueVisible && this.motionEnabled == motionEnabled) return;
        this.blueVisible = blueVisible;
        this.motionEnabled = motionEnabled;
        setVisibility(blueVisible ? VISIBLE : INVISIBLE);
        animate().cancel();
        if (!blueVisible || !motionEnabled) resetPosition();
        move();
    }

    void setResumed(boolean resumed) {
        if (this.resumed == resumed) return;
        this.resumed = resumed;
        animate().cancel();
        move();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        move();
    }

    @Override protected void onDetachedFromWindow() {
        animate().cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        animate().cancel();
        resetPosition();
        move();
    }

    private void resetPosition() {
        direction = -1;
        setTranslationY(0f);
    }

    private void move() {
        if (!resumed || !blueVisible || !motionEnabled || !isAttachedToWindow() || getHeight() == 0) return;
        float amplitude = getHeight() * TRAVEL;
        float target = direction * amplitude;
        float distance = Math.abs(target - getTranslationY());
        long duration = Math.max(1, Math.round(HALF_CYCLE_MS * distance / (2 * amplitude)));
        animate().translationY(target).setStartDelay(0).setDuration(duration).setInterpolator(easing)
                .withEndAction(() -> {
                    direction = -direction;
                    move();
                }).start();
    }
}

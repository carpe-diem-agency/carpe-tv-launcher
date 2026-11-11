package agency.carpediem.tv.prototype;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Composes square icons once on the artwork worker, keeping logos undistorted. */
final class AppArtwork {
    static Drawable prepare(Resources resources, Drawable source) {
        int available = resources.getDisplayMetrics().widthPixels
                - resources.getDimensionPixelSize(R.dimen.cd_sidebar_width)
                - resources.getDimensionPixelSize(R.dimen.cd_content_gap)
                - resources.getDimensionPixelSize(R.dimen.cd_safe_horizontal);
        int cardWidth = Math.max(320, Math.min(768, Math.round(available / 5f - 16 * resources.getDisplayMetrics().density)));
        int cardHeight = Math.round(cardWidth * 9f / 16);
        if (source.getIntrinsicWidth() > source.getIntrinsicHeight() * 1.3f) {
            // The on-screen card is ~300×168 px at 1080p. Bound large native
            // banners/complex drawables once instead of retaining full-size textures.
            Rect bounds = new Rect(source.getBounds());
            Bitmap banner = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888);
            float scale = Math.max((float) cardWidth / source.getIntrinsicWidth(), (float) cardHeight / source.getIntrinsicHeight());
            int w = Math.round(source.getIntrinsicWidth() * scale), h = Math.round(source.getIntrinsicHeight() * scale);
            source.setBounds((cardWidth - w) / 2, (cardHeight - h) / 2, (cardWidth + w) / 2, (cardHeight + h) / 2);
            source.draw(new Canvas(banner)); source.setBounds(bounds);
            return new BitmapDrawable(resources, banner);
        }
        Rect original = new Rect(source.getBounds());
        Bitmap sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Drawable backdrop = source instanceof android.graphics.drawable.AdaptiveIconDrawable
                ? ((android.graphics.drawable.AdaptiveIconDrawable) source).getBackground() : source;
        if (backdrop == null) backdrop = source;
        Rect backdropBounds = new Rect(backdrop.getBounds());
        backdrop.setBounds(0, 0, 32, 32); backdrop.draw(new Canvas(sample));
        backdrop.setBounds(backdropBounds);
        int background = background(sample);
        Bitmap tile = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(tile); canvas.drawColor(background);
        if (background == 0xff172238) extendTwoTone(canvas, sample);
        sample.recycle();
        float ratio = source.getIntrinsicWidth() > 0 && source.getIntrinsicHeight() > 0
                ? (float) source.getIntrinsicWidth() / source.getIntrinsicHeight() : 1f;
        int height = Math.round(cardHeight * 148f / 180), width = Math.round(height * ratio);
        int maxWidth = Math.round(cardWidth * 280f / 320);
        if (width > maxWidth) { width = maxWidth; height = Math.round(width / ratio); }
        source.setBounds((cardWidth - width) / 2, (cardHeight - height) / 2,
                (cardWidth + width) / 2, (cardHeight + height) / 2);
        source.draw(canvas); source.setBounds(original);
        return new BitmapDrawable(resources, tile);
    }

    private static void extendTwoTone(Canvas canvas, Bitmap sample) {
        int top = edgeColor(sample, 5), bottom = edgeColor(sample, 26);
        if (top == 0 || bottom == 0 || similar(top, bottom)) return;
        int known = 0, split = -1; boolean bottomSeen = false;
        for (int y = 5; y <= 26; y++) {
            int row = edgeColor(sample, y);
            if (row == 0) continue;
            if (similar(row, top)) { if (bottomSeen) return; known++; }
            else if (similar(row, bottom)) { if (!bottomSeen) split = y; bottomSeen = true; known++; }
            else return;
        }
        if (known < 17 || split < 10 || split > 22) return;
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(top); canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
        paint.setColor(bottom); canvas.drawRect(0, canvas.getHeight() * (16 + split * 148f / 32) / 180, canvas.getWidth(), canvas.getHeight(), paint);
    }
    private static int edgeColor(Bitmap sample, int y) {
        int[] colors = {sample.getPixel(3, y), sample.getPixel(5, y), sample.getPixel(26, y), sample.getPixel(28, y)};
        for (int candidate : colors) {
            if (Color.alpha(candidate) < 240) continue;
            int matching = 0;
            for (int color : colors) if (Color.alpha(color) >= 240 && similar(candidate, color)) matching++;
            if (matching >= 3) return candidate;
        }
        return 0;
    }

    private static int background(Bitmap sample) {
        int[] colors = new int[64]; int count = 0;
        // Insets skip transparent rounded corners of adaptive app icons.
        for (int i = 8; i < 24; i++) {
            int[] edge = {sample.getPixel(i, 6), sample.getPixel(i, 25),
                    sample.getPixel(6, i), sample.getPixel(25, i)};
            for (int color : edge) if (Color.alpha(color) >= 240) colors[count++] = color;
        }
        if (count < 32) return 0xff172238;
        int best = 0, matches = 0;
        for (int i = 0; i < count; i++) {
            int nearby = 0;
            for (int j = 0; j < count; j++) if (similar(colors[i], colors[j])) nearby++;
            if (nearby > matches) { matches = nearby; best = colors[i]; }
        }
        if (matches < count * .75f) return 0xff172238;
        long r = 0, g = 0, b = 0; int n = 0;
        for (int i = 0; i < count; i++) if (similar(best, colors[i])) {
            r += Color.red(colors[i]); g += Color.green(colors[i]); b += Color.blue(colors[i]); n++;
        }
        return Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }
    private static boolean similar(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) <= 20
                && Math.abs(Color.green(a) - Color.green(b)) <= 20
                && Math.abs(Color.blue(a) - Color.blue(b)) <= 20;
    }
}

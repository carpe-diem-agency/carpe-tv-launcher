package agency.carpediem.tv.prototype;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Small, bounded wallpaper-only blur. Never processes full-size frames. */
final class WallpaperBlur {
    /** Precompose page tint once off the UI thread instead of drawing a second full-screen layer every frame. */
    static Bitmap tint(Bitmap source, int alpha) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        canvas.drawBitmap(source, 0, 0, null);
        canvas.drawColor(android.graphics.Color.argb(alpha, 8, 15, 30));
        return result;
    }
    static Bitmap loadOrCreate(android.content.Context context, int resource, String key) {
        java.io.File cache = new java.io.File(context.getCacheDir(), "wallpaper-" + key + "-blur-v4.png");
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(cache.getPath(), bounds);
        if (bounds.outWidth > 0 && bounds.outWidth <= 512 && bounds.outHeight > 0 && bounds.outHeight <= 512) {
            Bitmap cached = BitmapFactory.decodeFile(cache.getPath()); if (cached != null) return cached;
        }
        Bitmap bitmap = create(context.getResources(), resource);
        java.io.File temporary = new java.io.File(context.getCacheDir(), "wallpaper-" + key + "-blur-v4.tmp");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(temporary)) {
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { output.flush(); if (!temporary.renameTo(cache)) temporary.delete(); }
        } catch (java.io.IOException ignored) { temporary.delete(); }
        return bitmap;
    }
    static Bitmap create(Resources resources, int resource) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 8;
        Bitmap source = BitmapFactory.decodeResource(resources, resource, options);
        int w = source.getWidth(), h = source.getHeight();
        int[] pixels = new int[w * h], next = new int[w * h];
        source.getPixels(pixels, 0, w, 0, 0, w, h); source.recycle();
        int radius = 8;
        for (int pass = 0; pass < 4; pass++) {
            boolean horizontal = pass % 2 == 0;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int red = 0, green = 0, blue = 0;
                for (int k = -radius; k <= radius; k++) {
                    int xx = horizontal ? Math.max(0, Math.min(w - 1, x + k)) : x;
                    int yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + k));
                    int c = pixels[yy * w + xx]; red += (c >> 16) & 255; green += (c >> 8) & 255; blue += c & 255;
                }
                int n = radius * 2 + 1; next[y * w + x] = 0xff000000 | (red / n << 16) | (green / n << 8) | blue / n;
            }
            int[] swap = pixels; pixels = next; next = swap;
        }
        Bitmap blurred = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        Bitmap result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.ColorMatrix saturation = new android.graphics.ColorMatrix(); saturation.setSaturation(1.35f);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(saturation));
        canvas.drawBitmap(blurred, 0, 0, paint); paint.setColorFilter(null);
        paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0x34110a24, 0x18140d24, 0x70210e35}, null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint); blurred.recycle(); return result;
    }
}

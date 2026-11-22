package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.PixelCopy;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Measures contrast over the rendered background at the actual glyph positions. */
final class SettingsContrastProbe {
    private SettingsContrastProbe() { }

    static void runFocusRing(Instrumentation test, Bundle result) throws Exception {
        requireValidation(test);
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        MainActivity screen = null;
        double minimum = Double.POSITIVE_INFINITY;
        try {
            if (!prefs.edit().putBoolean("welcome_seen", true).putBoolean("animations", false)
                    .remove("last_opened_package").putString("wallpaper", "blue")
                    .putBoolean(WallpaperRotation.ENABLED, false).commit())
                throw new IllegalStateException("Cannot prepare focus fixture");
            test.setInTouchMode(false);
            MainActivity app = screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitHome(test, app, "blue");
            for (int rail : new int[]{R.id.nav_home, R.id.nav_apps}) {
                SettingsStructureProbe.main(test, () -> app.findViewById(rail).requestFocus());
                SettingsStructureProbe.await(test, () -> app.findViewById(rail).isSelected()
                        && firstLabel(app, rail) != null, "Focus fixture has no app tile");
                java.lang.reflect.Field worker = MainActivity.class.getDeclaredField("worker");
                worker.setAccessible(true);
                ((java.util.concurrent.ExecutorService) worker.get(app)).submit(() -> {}).get(12, TimeUnit.SECONDS);
                test.waitForIdleSync();
                View tile = (View) firstLabel(app, rail).getParent();
                View art = tile.findViewById(R.id.app_artwork);
                ImageView icon = tile.findViewById(R.id.app_icon);
                View outline = tile.findViewById(R.id.focus_outline);
                Rect bounds = new Rect();
                SettingsStructureProbe.main(test, () -> {
                    if (!tile.requestFocus() || outline.getAlpha() != 1f) throw new AssertionError("Focus ring unavailable");
                    icon.setPadding(0, 0, 0, 0);
                    int[] position = new int[2];
                    art.getLocationInWindow(position);
                    bounds.set(position[0], position[1], position[0] + Math.round(art.getWidth() * art.getScaleX()),
                            position[1] + Math.round(art.getHeight() * art.getScaleY()));
                });
                for (int color : new int[]{Color.WHITE, Color.BLACK, 0xff808080}) {
                    SettingsStructureProbe.main(test, () -> {
                        icon.setImageDrawable(new android.graphics.drawable.ColorDrawable(color));
                        outline.setAlpha(0f);
                    });
                    int[] background = capture(app, bounds);
                    SettingsStructureProbe.main(test, () -> outline.setAlpha(1f));
                    int[] focused = capture(app, bounds);
                    double contrast = focusEdges(background, focused, bounds, color,
                            (int) Math.ceil(7 * app.getResources().getDisplayMetrics().density));
                    String page = rail == R.id.nav_home ? "home" : "apps";
                    result.putString("focusring_" + page + "_" + Integer.toHexString(color),
                            String.format(Locale.ROOT, "%.3f:1 weakest edge", contrast));
                    minimum = Math.min(minimum, contrast);
                }
            }
        } finally {
            try {
                if (screen != null) SettingsStructureProbe.finish(test, screen);
            } finally {
                SettingsStructureProbe.restore(prefs, saved);
            }
        }
        result.putString("focusring", String.format(Locale.ROOT, "minimum %.3f:1", minimum));
        if (minimum < 3.0) throw new AssertionError("Focused app outline below 3:1 over artwork");
    }

    private static View firstLabel(MainActivity app, int rail) {
        View group = rail == R.id.nav_home ? app.getWindow().getDecorView() : app.findViewById(R.id.apps_grid);
        return group == null ? null : group.findViewById(R.id.app_name);
    }

    private static double focusEdges(int[] background, int[] focused, Rect bounds, int color, int depth) {
        int w = bounds.width(), h = bounds.height();
        double[] edge = new double[4];
        int center = (h / 2) * w + w / 2;
        if (background[center] != color || focused[center] != color)
            throw new AssertionError("Controlled artwork was not rendered unchanged in the tile center");
        for (int i = 0; i < depth; i++) {
            int[] indices = {i * w + w / 2, (h - 1 - i) * w + w / 2, (h / 2) * w + i, (h / 2) * w + w - 1 - i};
            for (int side = 0; side < indices.length; side++) {
                int at = indices[side];
                if (background[at] == color) {
                    double before = luminance(color), after = luminance(focused[at]);
                    edge[side] = Math.max(edge[side], (Math.max(before, after) + .05) / (Math.min(before, after) + .05));
                }
            }
        }
        return Math.min(Math.min(edge[0], edge[1]), Math.min(edge[2], edge[3]));
    }

    static void runHome(Instrumentation test, Bundle result) throws Exception {
        requireValidation(test);
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        test.setInTouchMode(false);
        double minimum = Double.POSITIVE_INFINITY;
        try {
            for (WallpaperCatalog.Entry wallpaper : WallpaperCatalog.entries(test.getTargetContext())) {
                for (int dim : wallpaper.photo == 0 ? new int[]{0} : new int[]{0, 30, 60}) {
                    minimum = Math.min(minimum, measureHome(test, result, prefs, wallpaper, dim));
                }
            }
            minimum = Math.min(minimum, verifyLiveHome(test, result, prefs));
        } finally {
            SettingsStructureProbe.restore(prefs, saved);
        }
        result.putString("homecontrast", String.format(Locale.ROOT, "minimum %.3f:1", minimum));
        if (minimum < 4.5) throw new AssertionError("Home text below 4.5:1 over the active wallpaper");
    }

    private static double verifyLiveHome(Instrumentation test, Bundle result, SharedPreferences prefs)
            throws Exception {
        if (!prefs.edit().putString("wallpaper", "blue").putInt("dim", 0).commit())
            throw new IllegalStateException("Cannot prepare Blue wallpaper");
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            awaitHome(test, screen, "blue");
            TextView greeting = screen.findViewById(R.id.home_greeting);
            TextView label = screen.findViewById(R.id.app_name);
            View date = screen.findViewById(R.id.home_date);
            View tile = (View) label.getParent();
            SettingsStructureProbe.main(test, () -> {
                requireBacking(false, greeting, label, date);
                if (!tile.requestFocus()) throw new AssertionError("Could not focus starting favorite");
            });

            chooseAppearanceControl(test, screen, R.id.wallpaper_alpine);
            awaitHome(test, screen, "home:alpine:0:true");
            SettingsStructureProbe.main(test, () -> {
                requireSameHome(screen, greeting, label, tile);
                requireBacking(true, greeting, label, date);
            });
            double greetingRatio = measure(test, result, "live_alpine_0_greeting", screen, greeting, false);
            double labelRatio = measure(test, result, "live_alpine_0_tile", screen, label, false);

            chooseAppearanceControl(test, screen, R.id.dim_reset);
            awaitHome(test, screen, "home:alpine:60:true");
            SettingsStructureProbe.main(test, () -> {
                requireSameHome(screen, greeting, label, tile);
                requireBacking(false, greeting, label);
                requireBacking(true, date);
            });

            chooseAppearanceControl(test, screen, R.id.wallpaper_blue);
            awaitHome(test, screen, "blue");
            SettingsStructureProbe.main(test, () -> {
                requireSameHome(screen, greeting, label, tile);
                requireBacking(false, greeting, label, date);
            });
            if (!"blue".equals(prefs.getString("wallpaper", "")) || prefs.getInt("dim", -1) != 60)
                throw new AssertionError("Live wallpaper choice or dim reset not saved");
            result.putString("homecontrast_live",
                    "PASS: Blue to Alpine, D-pad dim reset and back to Blue on the same Home views with original tile focus");
            return Math.min(greetingRatio, labelRatio);
        } finally {
            SettingsStructureProbe.finish(test, screen);
        }
    }

    private static void chooseAppearanceControl(Instrumentation test, MainActivity screen, int id) throws Exception {
        SettingsStructureProbe.main(test, () -> screen.findViewById(R.id.nav_settings).requestFocus());
        SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.nav_settings).isSelected()
                && screen.findViewById(id) != null, "Appearance did not open");
        SettingsStructureProbe.main(test, () -> {
            if (!screen.findViewById(id).requestFocus()) throw new AssertionError("Appearance action could not take focus");
        });
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    private static void requireSameHome(MainActivity screen, TextView greeting, TextView label, View tile) {
        if (screen.findViewById(R.id.home_greeting) != greeting || screen.findViewById(R.id.app_name) != label
                || screen.getCurrentFocus() != tile)
            throw new AssertionError("Wallpaper change did not retain the cached Home views and original favorite focus");
    }

    private static void requireBacking(boolean expected, View... labels) {
        for (View label : labels) {
            if ((label.getBackground() != null) != expected)
                throw new AssertionError("Home label backing did not follow the current wallpaper and brightness");
        }
    }

    private static void awaitHome(Instrumentation test, MainActivity screen, String expected) throws Exception {
        java.lang.reflect.Field background = MainActivity.class.getDeclaredField("backgroundKey");
        background.setAccessible(true);
        SettingsStructureProbe.await(test, () -> {
            Object rendered = background.get(screen);
            boolean matches = expected.equals(rendered)
                    || ("blue".equals(expected) && ("blue:still".equals(rendered) || "blue:layers".equals(rendered)));
            return screen.hasWindowFocus() && screen.findViewById(R.id.nav_home).isSelected()
                    && matches && screen.findViewById(R.id.app_name) != null;
        }, "Home wallpaper or real favorite did not settle: " + expected);
    }

    private static double measureHome(Instrumentation test, Bundle result, SharedPreferences prefs,
                                      WallpaperCatalog.Entry wallpaper, int dim) throws Exception {
        if (!prefs.edit().putBoolean("welcome_seen", true).putBoolean("animations", false)
                .remove("last_opened_package").putInt("dim", dim).putString("wallpaper", wallpaper.key)
                .putBoolean(WallpaperRotation.ENABLED, false).commit()) {
            throw new IllegalStateException("Cannot prepare Home wallpaper");
        }
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            String expected = wallpaper.photo == 0 ? wallpaper.key : "home:" + wallpaper.key + ":" + dim + ":true";
            awaitHome(test, screen, expected);
            TextView greeting = screen.findViewById(R.id.home_greeting);
            TextView label = screen.findViewById(R.id.app_name);
            View tile = (View) label.getParent();
            View rail = screen.findViewById(R.id.nav_home);
            String sample = wallpaper.key + "_" + dim + "_";
            SettingsStructureProbe.main(test, () -> {
                if (!rail.requestFocus()) throw new AssertionError("Home rail cannot take focus");
                if (tile.findViewById(R.id.focus_outline).getAlpha() != 0f) {
                    throw new AssertionError("Home focus outline stays visible with rail focus");
                }
            });
            double greetingRatio = measure(test, result, sample + "greeting", screen, greeting, false);
            double dateRatio = measure(test, result, sample + "date", screen, screen.findViewById(R.id.home_date), false);
            double clockRatio = measure(test, result, sample + "clock", screen, screen.findViewById(R.id.home_time), false);
            double idleRatio = measure(test, result, sample + "tile_idle", screen, label, false);
            SettingsStructureProbe.main(test, () -> {
                if (!tile.requestFocus()) throw new AssertionError("Home tile cannot take focus");
                if (tile.findViewById(R.id.focus_outline).getAlpha() != 1f) {
                    throw new AssertionError("Home focus outline unavailable on focused tile");
                }
            });
            double focused = measure(test, result, sample + "tile_focus", screen, label, false);
            return Math.min(Math.min(greetingRatio, dateRatio), Math.min(clockRatio, Math.min(idleRatio, focused)));
        } finally {
            SettingsStructureProbe.finish(test, screen);
        }
    }

    static void run(Instrumentation test, Bundle result) throws Exception {
        requireValidation(test);
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences languagePrefs = test.getTargetContext().getSharedPreferences("MainActivity", 0);
        Map<String, ?> savedPrefs = new HashMap<>(prefs.getAll());
        Map<String, ?> savedLanguage = new HashMap<>(languagePrefs.getAll());
        Locale previousLocale = Locale.getDefault();
        test.setInTouchMode(false);
        double minimum = Double.POSITIVE_INFINITY;
        try {
            for (String language : new String[]{"en", "uk"}) {
                AppLanguage.choose(test.getTargetContext(), language);
                for (WallpaperCatalog.Entry wallpaper : WallpaperCatalog.entries(test.getTargetContext())) {
                    minimum = Math.min(minimum, measureWallpaper(test, result, prefs, language, wallpaper));
                }
            }
        } finally {
            SettingsStructureProbe.restore(prefs, savedPrefs);
            SettingsStructureProbe.restore(languagePrefs, savedLanguage);
            Locale.setDefault(previousLocale);
        }
        result.putString("settingscontrast", String.format(Locale.ROOT, "minimum %.3f:1", minimum));
        if (minimum < 4.5) throw new AssertionError("Active appearance button below 4.5:1");
    }

    private static void requireValidation(Instrumentation test) {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName())) {
            throw new IllegalStateException("Requires the disposable validation preview");
        }
    }

    private static double measureWallpaper(Instrumentation test, Bundle result, SharedPreferences prefs,
                                           String language, WallpaperCatalog.Entry wallpaper) throws Exception {
        if (!prefs.edit().putBoolean("welcome_seen", true).putBoolean("animations", false)
                .putString("wallpaper", wallpaper.key).putBoolean(WallpaperRotation.ENABLED, false).commit()) {
            throw new IllegalStateException("Cannot prepare wallpaper");
        }
        MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try {
            View rail = screen.findViewById(R.id.nav_settings);
            test.runOnMainSync(rail::requestFocus);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            TextView reset = screen.findViewById(R.id.dim_reset);
            TextView preview = screen.findViewById(R.id.ui_sounds_preview);
            if (reset == null || preview == null
                    || !language.equals(screen.getResources().getConfiguration().getLocales().get(0).getLanguage())) {
                throw new AssertionError("Localized appearance controls unavailable");
            }
            awaitBackground(test, screen, wallpaper);
            double minimum = Double.POSITIVE_INFINITY;
            boolean blue = "blue".equals(wallpaper.key);
            BlueWallpaperMotion glow = screen.findViewById(R.id.blue_wallpaper_motion);
            ImageView[] staticLayer = {null};
            android.graphics.drawable.Drawable[] still = {null};
            if (blue) {
                test.runOnMainSync(() -> {
                    if (!glow.isShown()) {
                        ImageView first = screen.findViewById(R.id.scene_a);
                        ImageView second = screen.findViewById(R.id.scene_b);
                        staticLayer[0] = first.getAlpha() > .99f ? first : second;
                        still[0] = staticLayer[0].getDrawable();
                        staticLayer[0].setImageResource(R.drawable.blue_wallpaper);
                        glow.setVisibility(View.VISIBLE);
                    }
                });
            }
            int[] phases = !blue ? new int[]{0} : still[0] == null ? new int[]{-1, 0, 1} : new int[]{-1, 0, 1, 2};
            for (int phase : phases) {
                if (blue) {
                    test.runOnMainSync(() -> {
                        if (phase == 2) {
                            staticLayer[0].setImageDrawable(still[0]);
                            glow.setTranslationY(0f);
                            glow.setVisibility(View.INVISIBLE);
                        } else {
                            glow.setTranslationY(phase * glow.getHeight() * .035f);
                        }
                    });
                }
                for (TextView control : new TextView[]{reset, preview}) {
                    String name = control == reset ? "reset" : "preview";
                    for (boolean focused : new boolean[]{false, true}) {
                        test.runOnMainSync(() -> {
                            (focused ? control : rail).requestFocus();
                            control.requestRectangleOnScreen(new Rect(0, 0, control.getWidth(), control.getHeight()), true);
                        });
                        String sample = language + "_" + wallpaper.key + "_" + (phase == 2 ? "still" : phase) + "_" + name
                                + (focused ? "_focus" : "_idle");
                        minimum = Math.min(minimum, measure(test, result, sample, screen, control, focused));
                    }
                }
            }
            return minimum;
        } finally {
            SettingsStructureProbe.finish(test, screen);
        }
    }

    private static void awaitBackground(Instrumentation test, MainActivity screen, WallpaperCatalog.Entry wallpaper) {
        long deadline = SystemClock.uptimeMillis() + 12000;
        boolean[] ready = {false};
        do {
            test.runOnMainSync(() -> {
                View glow = screen.findViewById(R.id.blue_wallpaper_motion);
                View choice = screen.findViewById(wallpaper.viewId);
                ImageView front = screen.findViewById(R.id.scene_a);
                ImageView back = screen.findViewById(R.id.scene_b);
                ImageView active = front.getAlpha() > .99f ? front : back;
                ready[0] = choice != null && choice.isSelected() && active.getAlpha() > .99f
                        && ("blue".equals(wallpaper.key) ? glow.getHeight() > 0
                                && (glow.isShown() || active.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
                        : !glow.isShown() && (wallpaper.photo == 0
                            ? active.getDrawable() instanceof android.graphics.drawable.ColorDrawable
                            : active.getDrawable() instanceof android.graphics.drawable.BitmapDrawable));
            });
            if (ready[0]) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Wallpaper did not settle: " + wallpaper.key);
    }

    private static double measure(Instrumentation test, Bundle result, String sample,
                                  MainActivity screen, TextView control, boolean focused) throws Exception {
        Rect bounds = new Rect();
        int[] fg = {0};
        android.content.res.ColorStateList[] colors = {null};
        test.runOnMainSync(() -> {
            Rect visible = new Rect();
            int width = control.getWidth(), height = control.getHeight();
            if (!control.isEnabled() || control.isFocused() != focused || !control.getGlobalVisibleRect(visible)
                    || visible.height() != height || visible.width() != width) return;
            int[] position = new int[2];
            control.getLocationInWindow(position);
            bounds.set(position[0], position[1], position[0] + width, position[1] + height);
            fg[0] = control.getCurrentTextColor();
            colors[0] = control.getTextColors();
        });
        if (bounds.isEmpty()) throw new AssertionError("Control state or full visibility unavailable: " + sample);
        if (Color.alpha(fg[0]) != 255) throw new AssertionError("Expected opaque text");
        int[] shown = capture(screen, bounds);
        int[] blank;
        int[] blankRepeat;
        try {
            test.runOnMainSync(() -> control.setTextColor(Color.TRANSPARENT));
            blank = capture(screen, bounds);
            blankRepeat = capture(screen, bounds);
        } finally {
            test.runOnMainSync(() -> control.setTextColor(colors[0]));
        }
        int[] shownRepeat = capture(screen, bounds);
        double minimum = Double.POSITIVE_INFINITY;
        int glyphPixels = 0;
        double text = luminance(fg[0]);
        for (int i = 0; i < shown.length; i++) {
            if (shown[i] == blank[i]) continue;
            // Exact interior glyph pixels identify text without using antialiased edges.
            if (shown[i] != fg[0]) continue;
            if (shownRepeat[i] != shown[i] || blankRepeat[i] != blank[i]) {
                throw new AssertionError("Glyph or its background moved between captures: " + sample);
            }
            glyphPixels++;
            double background = luminance(blank[i]);
            minimum = Math.min(minimum, (Math.max(text, background) + .05) / (Math.min(text, background) + .05));
        }
        if (glyphPixels < 20) throw new AssertionError("Too few fully opaque glyph pixels: " + glyphPixels);
        result.putString(sample, String.format(Locale.ROOT, "%.3f:1 (%d glyph pixels, #%06X)",
                minimum, glyphPixels, fg[0] & 0xffffff));
        return minimum;
    }

    private static int[] capture(MainActivity screen, Rect bounds) throws Exception {
        SystemClock.sleep(100);
        Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        try {
            CountDownLatch completed = new CountDownLatch(1);
            int[] status = {-1};
            PixelCopy.request(screen.getWindow(), new Rect(bounds), bitmap, code -> {
                status[0] = code;
                completed.countDown();
            }, new Handler(Looper.getMainLooper()));
            if (!completed.await(4, TimeUnit.SECONDS) || status[0] != PixelCopy.SUCCESS) {
                throw new AssertionError("Window PixelCopy failed: " + status[0]);
            }
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            return pixels;
        } finally {
            bitmap.recycle();
        }
    }

    private static double luminance(int color) {
        return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color));
    }

    private static double linear(int value) {
        double s = value / 255.0;
        return s <= .04045 ? s / 12.92 : Math.pow((s + .055) / 1.055, 2.4);
    }
}

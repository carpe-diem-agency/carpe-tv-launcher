package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** A cached PNG with real pixel data missing, accepted as partial by BitmapFactory. */
final class CorruptSaverPhotoProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package only");
        SharedPreferences prefs = Screensaver.prefs(context);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "corrupt-photo-").toFile();
        SaverCache fixture = new SaverCache(root, 1024 * 1024, 0, () -> Long.MAX_VALUE);
        Field singleton = field(SaverCache.class, "instance");
        SaverCache previous;
        synchronized (SaverCache.class) {
            previous = (SaverCache) singleton.get(null);
        }
        Activity screen = null;
        Throwable primary = null;
        try {
            require(prefs.edit().putBoolean("saverOnline", false).putBoolean("animations", false).commit(),
                    "Set isolated offline motion preferences");
            SaverMedia media = new SaverMedia("https://images-assets.nasa.gov/probe.png",
                    "Truncated test photo", "Test fixture", "https://example.invalid/source", "Test fixture", "image");
            File file = fixture.file(media);
            int prefix = writeTruncatedPhoto(file);
            Files.write(new File(root, media.id + ".json").toPath(),
                    media.json().toString().getBytes(StandardCharsets.UTF_8));
            require(fixture.hasComplete(media), "Pair must enter the photo decoder");
            result.putInt("partial_png_prefix_percent", prefix);
            synchronized (SaverCache.class) {
                singleton.set(null, fixture);
            }
            screen = test.startActivitySync(new Intent(context, ScreensaverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity saver = screen;
            long fallbackStart = android.os.SystemClock.elapsedRealtime();
            ImageView first = (ImageView) field(ScreensaverActivity.class, "first").get(saver);
            Field pinnedPhoto = field(ScreensaverActivity.class, "pinnedPhoto");
            View credit = saver.findViewById(R.id.saver_credit);
            SettingsStructureProbe.await(test, () -> credit.getVisibility() == View.VISIBLE
                    || (!file.exists() && first.getDrawable() instanceof BitmapDrawable
                    && ((BitmapDrawable) first.getDrawable()).getBitmap().getWidth() > 640),
                    "No offline photo after decode failure");
            SettingsStructureProbe.main(test, () -> {
                require(pinnedPhoto.get(saver) == null && credit.getVisibility() == View.GONE,
                        "A partial PNG was rendered as a cached photo");
            });
            require(!fixture.hasComplete(media) && !file.exists() && !new File(root, media.id + ".json").exists(),
                    "Corrupt pair retained");
            result.putLong("saver_corrupt_photo_fallback_after_start_ms",
                    android.os.SystemClock.elapsedRealtime() - fallbackStart);
            test.setInTouchMode(false);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test, saver::isDestroyed, "Back did not close the saver");
            result.putString("saver_corrupt_photo", "PASS: partial PNG rejected, pair removed, bundled photo shown, Back closes");

            SaverMedia intact = new SaverMedia("https://images-assets.nasa.gov/probe-valid.jpg",
                    "Intact test photo", "Test fixture", "https://example.invalid/source", "Test fixture", "image");
            Bitmap jpeg = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            try (java.io.OutputStream output = Files.newOutputStream(fixture.file(intact).toPath())) {
                jpeg.eraseColor(0xff367ab0);
                require(jpeg.compress(Bitmap.CompressFormat.JPEG, 90, output), "Encode complete cached JPEG");
            } finally {
                jpeg.recycle();
            }
            Files.write(new File(root, intact.id + ".json").toPath(),
                    intact.json().toString().getBytes(StandardCharsets.UTF_8));
            screen = test.startActivitySync(new Intent(context, ScreensaverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity validSaver = screen;
            ImageView validImage = (ImageView) field(ScreensaverActivity.class, "first").get(validSaver);
            SettingsStructureProbe.await(test, () -> {
                SaverMedia pinned = (SaverMedia) pinnedPhoto.get(validSaver);
                return pinned != null && intact.id.equals(pinned.id)
                        && validSaver.findViewById(R.id.saver_credit).getVisibility() == View.VISIBLE
                        && validImage.getDrawable() instanceof BitmapDrawable
                        && ((BitmapDrawable) validImage.getDrawable()).getBitmap().getWidth() == 1280;
            }, "Complete JPEG did not display with attribution");
            verifyQueuedDecodeLifecycle(test, validSaver, fixture.file(intact), validImage, pinnedPhoto, result);
            require(fixture.hasComplete(intact), "Good cache pair was lost");
            result.putString("saver_intact_photo", "PASS: cached JPEG shown at 1280px with credit and retained after Back");

            if (android.os.Build.VERSION.SDK_INT >= 29 && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                require(prefs.edit().putBoolean("animations", true).commit(), "Enable isolated photo motion");
                screen = test.startActivitySync(new Intent(context, ScreensaverActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                verifyInterruptedFade(test, screen, result);
            } else {
                result.putString("saver_interrupted_fade", "INPUT_UNVERIFIED: needs API 29+ and enabled animations");
            }
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                if (screen != null) {
                    Activity saver = screen;
                    SettingsStructureProbe.main(test, () -> { if (!saver.isDestroyed()) saver.finish(); });
                    SettingsStructureProbe.await(test, saver::isDestroyed, "Saver teardown pending");
                    ExecutorService decoder = (ExecutorService) field(ScreensaverActivity.class, "decoder").get(saver);
                    require(decoder.awaitTermination(15, TimeUnit.SECONDS), "Decoder still active");
                }
                synchronized (fixture) {
                    Map<?, ?> pins = (Map<?, ?>) field(SaverCache.class, "pinned").get(fixture);
                    require(pins.isEmpty(), "Photo cache pins remain after dismissal");
                }
                synchronized (SaverCache.class) {
                    singleton.set(null, previous);
                }
                SettingsStructureProbe.restore(prefs, saved);
                File[] files = root.listFiles();
                require(files != null, "Private test directory missing");
                for (File file : files) Files.delete(file.toPath());
                Files.delete(root.toPath());
            } catch (Exception | Error cleanup) {
                if (primary != null) primary.addSuppressed(cleanup);
                else throw cleanup;
            }
        }
    }

    private static void verifyInterruptedFade(Instrumentation test, Activity saver, Bundle result)
            throws Exception {
        ImageView first = (ImageView) field(ScreensaverActivity.class, "first").get(saver);
        ImageView second = (ImageView) field(ScreensaverActivity.class, "second").get(saver);
        Field resumed = field(ScreensaverActivity.class, "resumed");
        ExecutorService decoder = (ExecutorService) field(ScreensaverActivity.class, "decoder").get(saver);
        Drawable[] incoming = {null};
        float[] pausedAlpha = {-1f};
        Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPrePaused(Activity activity) {
                if (activity == saver) {
                    pausedAlpha[0] = second.getAlpha();
                    incoming[0] = second.getDrawable();
                }
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        Application app = saver.getApplication();
        app.registerActivityLifecycleCallbacks(callback);
        Activity cover = null;
        CountDownLatch release = null;
        try {
            SettingsStructureProbe.await(test, () -> saver.hasWindowFocus()
                            && second.getDrawable() == null
                            && saver.findViewById(R.id.saver_credit).isShown(),
                    "Initial cached photo did not settle");
            Runnable advance = (Runnable) field(ScreensaverActivity.class, "advance").get(saver);
            SettingsStructureProbe.main(test, advance::run);
            SettingsStructureProbe.await(test, () -> second.getDrawable() != null
                            && second.getAlpha() > 0f && second.getAlpha() < .8f,
                    "No active fade to the next photo");
            cover = test.startActivitySync(new Intent(test.getTargetContext(), UpdatesActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SettingsStructureProbe.await(test, () -> !resumed.getBoolean(saver) && !saver.isDestroyed(),
                    "Updates did not pause the saver");
            SettingsStructureProbe.main(test, () -> {
                require(pausedAlpha[0] > 0f && pausedAlpha[0] < 1f && incoming[0] != null,
                        "Pause did not interrupt a partially visible photo");
                result.putFloat("saver_fade_interrupted_alpha", pausedAlpha[0]);
                require(first.getDrawable() == incoming[0] && first.getAlpha() == 1f
                                && second.getDrawable() == null && second.getAlpha() == 0f,
                        "Pause left an unfinished photo blend");
            });
            release = holdDecoder(decoder);
            SettingsStructureProbe.main(test, cover::finish);
            SettingsStructureProbe.await(test, () -> resumed.getBoolean(saver) && saver.hasWindowFocus(),
                    "Saver did not regain focus");
            SettingsStructureProbe.main(test, () -> require(first.getDrawable() == incoming[0]
                            && first.getAlpha() == 1f && second.getDrawable() == null,
                    "Returning with decoding queued did not retain the settled photo"));
            result.putString("saver_interrupted_fade",
                    "PASS: real mid-fade pause settled the incoming photo and retained it during queued resume");
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback);
            if (release != null) release.countDown();
            if (cover != null) {
                Activity updates = cover;
                SettingsStructureProbe.main(test, () -> { if (!updates.isFinishing()) updates.finish(); });
            }
        }
    }

    private static void verifyQueuedDecodeLifecycle(
            Instrumentation test, Activity saver, File media, ImageView image, Field pinnedPhoto, Bundle result)
            throws Exception {
        Field resumed = field(ScreensaverActivity.class, "resumed");
        Field ticket = field(ScreensaverActivity.class, "ticket");
        Method showSlide = ScreensaverActivity.class.getDeclaredMethod("showSlide");
        showSlide.setAccessible(true);
        ExecutorService decoder = (ExecutorService) field(ScreensaverActivity.class, "decoder").get(saver);
        Drawable[] displayed = new Drawable[1];
        long pauseSentinel = 1600000000000L;
        CountDownLatch releasePause = holdDecoder(decoder);
        Activity cover = null;
        try {
            queuePhoto(test, saver, image, displayed, showSlide, ticket);
            cover = test.startActivitySync(new Intent(test.getTargetContext(), UpdatesActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity updates = cover;
            SettingsStructureProbe.await(test, () -> !resumed.getBoolean(saver) && !saver.isDestroyed()
                    && !saver.hasWindowFocus() && updates.hasWindowFocus() && pinnedPhoto.get(saver) == null,
                    "Opening app updates did not pause the same saver instance or release its photo");
            require(media.setLastModified(pauseSentinel) && media.lastModified() == pauseSentinel,
                    "Set cache access sentinel");
            releasePause.countDown();
            decoder.submit(() -> { }).get(8, TimeUnit.SECONDS);
            test.waitForIdleSync();
            SettingsStructureProbe.main(test, () -> require(!resumed.getBoolean(saver)
                    && !saver.isDestroyed() && image.getDrawable() == displayed[0]
                    && pinnedPhoto.get(saver) == null, "Paused saver applied a stale frame or pin"));
            require(media.lastModified() == pauseSentinel, "Queued request acquired media while paused");
            SettingsStructureProbe.main(test, updates::finish);
            SettingsStructureProbe.await(test, () -> resumed.getBoolean(saver) && saver.hasWindowFocus()
                    && image.getDrawable() != displayed[0] && pinnedPhoto.get(saver) != null,
                    "Resuming the same saver did not render a fresh photo");
            require(media.lastModified() > pauseSentinel, "Resumed request did not acquire the media");
        } finally {
            releasePause.countDown();
            if (cover != null) {
                Activity updates = cover;
                SettingsStructureProbe.main(test, () -> { if (!updates.isFinishing()) updates.finish(); });
            }
        }

        long exitSentinel = pauseSentinel + 2000;
        require(media.setLastModified(exitSentinel) && media.lastModified() == exitSentinel,
                "Set cache access sentinel before Back");
        CountDownLatch releaseExit = holdDecoder(decoder);
        try {
            queuePhoto(test, saver, image, displayed, showSlide, ticket);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test, saver::isDestroyed, "Back did not destroy the saver");
            require(decoder.awaitTermination(8, TimeUnit.SECONDS), "Decoder did not stop on destruction");
            require(media.lastModified() == exitSentinel, "Destroyed saver acquired media from the queue");
            SettingsStructureProbe.main(test, () -> require(image.getDrawable() == null
                    && pinnedPhoto.get(saver) == null, "Destroyed saver retained its frame or pin"));
        } finally {
            releaseExit.countDown();
        }
        result.putString("saver_queued_photo", "PASS: queue skipped while paused, fresh frame after resume, queue stopped on Back");
    }

    private static CountDownLatch holdDecoder(ExecutorService decoder) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        decoder.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
        });
        if (!entered.await(8, TimeUnit.SECONDS)) {
            release.countDown();
            throw new AssertionError("Decoder gate did not start");
        }
        return release;
    }

    private static void queuePhoto(Instrumentation test, Activity saver, ImageView image,
            Drawable[] displayed, Method showSlide, Field ticket) throws Exception {
        SettingsStructureProbe.main(test, () -> {
            displayed[0] = image.getDrawable();
            int before = ticket.getInt(saver);
            showSlide.invoke(saver);
            require(ticket.getInt(saver) == before + 1, "Saver did not schedule a photo request");
        });
    }

    private static int writeTruncatedPhoto(File file) throws Exception {
        int width = 640, height = 360;
        Bitmap original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Random random = new Random(164);
            int[] pixels = new int[width * height];
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | random.nextInt(1 << 24);
            original.setPixels(pixels, 0, width, 0, 0, width, height);
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            require(original.compress(Bitmap.CompressFormat.PNG, 100, encoded), "Encode full PNG");
            byte[] complete = encoded.toByteArray();
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap control = BitmapFactory.decodeByteArray(complete, 0, complete.length, options);
            require(control != null, "Full PNG failed control decode");
            try {
                for (int percent : new int[]{50, 75, 90}) {
                    Files.write(file.toPath(), Arrays.copyOf(complete, complete.length * percent / 100));
                    Bitmap partial = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (partial == null) continue;
                    try {
                        if (partial.getWidth() == width && partial.getHeight() == height
                                && partial.getPixel(10, 10) == control.getPixel(10, 10)
                                && partial.getPixel(10, height - 10) != control.getPixel(10, height - 10)) return percent;
                    } finally {
                        partial.recycle();
                    }
                }
                throw new AssertionError("Fixture could not demonstrate partial pixel decode on this device");
            } finally {
                control.recycle();
            }
        } finally {
            original.recycle();
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}

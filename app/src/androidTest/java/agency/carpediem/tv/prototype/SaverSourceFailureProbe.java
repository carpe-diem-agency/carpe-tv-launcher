package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Disposable process only: injects HTTP replies below the production catalog and downloader. */
final class SaverSourceFailureProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package required");
        File catalog = new File(context.getFilesDir(), "saver-catalog-v2.json");
        File catalogPart = new File(catalog.getPath() + ".tmp");
        require(!catalog.exists() && !catalogPart.exists(), "Refuse existing catalog or catalog transfer");
        Field singleton = field(SaverCache.class, "instance");
        require(singleton.get(null) == null, "Fresh validation process required for network injection");
        ExecutorService worker = (ExecutorService) field(SaverSync.class, "worker").get(null);
        SharedPreferences prefs = Screensaver.prefs(context);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        byte[] photo;
        try (InputStream file = context.getResources().openRawResource(R.drawable.wallpaper_alpine)) {
            photo = file.readAllBytes();
        }
        SaverMedia cached = media("retained", "Retained credit");
        SaverMedia incoming = media("returning", "Returning credit");
        Source source = new Source(cached.url, incoming.url, photo);
        File root = new File(context.getCacheDir(), "saver-source-failure-probe");
        require(root.mkdir(), "Cannot create private cache fixture");
        SaverCache cache = new SaverCache(root, 8 * SaverCache.MIB, 0, () -> Long.MAX_VALUE);
        byte[] stale = new JSONObject().put("updated", 1)
                .put("items", new JSONArray().put(cached.json()).put(incoming.json()))
                .toString().getBytes(StandardCharsets.UTF_8);
        Activity screen = null;
        Throwable primary = null;
        try {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? source : null);
            Files.write(catalog.toPath(), stale);
            require(prefs.edit().putBoolean("saverOnline", true).putBoolean("animations", false)
                    .putString("wallpaper", "alpine").commit(), "Cannot set isolated online preferences");
            synchronized (SaverCache.class) { singleton.set(null, cache); }
            test.setInTouchMode(false);
            screen = open(test);
            Activity offline = screen;
            SettingsStructureProbe.await(test, () -> visiblePhoto(offline) && credit(offline).isEmpty(),
                    "No decoded bundled photo while sources were unavailable");
            drain(worker);
            require(source.catalogFailures == 3 && source.mediaFailures == 2 && source.closed == 5,
                    "Expected three failed catalog calls and two failed media calls, all disconnected");
            require(cache.entries().isEmpty(), "Failed response entered the media cache");
            close(test, screen);
            screen = null;
            drain(worker);

            require(cache.store(cached, new ByteArrayInputStream(photo), photo.length, photo.length,
                    () -> true, file -> true), "Cannot seed retained media");
            screen = open(test);
            Activity retained = screen;
            SettingsStructureProbe.await(test, () -> visiblePhoto(retained) && credit(retained).contains(cached.credit),
                    "Cached photo did not render during the outage");
            drain(worker);
            require(source.catalogFailures == 6 && source.mediaFailures == 3 && source.closed == 9,
                    "Stale entries were not reused on retry");
            require(Arrays.equals(photo, Files.readAllBytes(cache.file(cached).toPath())),
                    "Outage replaced the existing cached media");
            close(test, screen);
            screen = null;
            drain(worker);

            source.recovered = true;
            screen = open(test);
            drain(worker);
            require(source.catalogFailures == 9 && source.successes == 1 && source.closed == 13,
                    "Recovered media response was not consumed and disconnected");
            require(cache.hasComplete(incoming)
                    && Arrays.equals(photo, Files.readAllBytes(cache.file(incoming).toPath())),
                    "Production downloader did not publish recovered media");
            require(cache.hasComplete(cached) && Arrays.equals(photo, Files.readAllBytes(cache.file(cached).toPath())),
                    "Recovery did not retain the original file");
            require(Arrays.equals(stale, Files.readAllBytes(catalog.toPath())) && !catalogPart.exists(),
                    "Failed catalog requests replaced usable stale metadata");
            require(source.unexpected == 0, "Saver attempted an unexpected request");
            close(test, screen);
            screen = null;
            drain(worker);
            result.putString("saversourcefailure",
                    "PASS: injected HTTP 503, bundled and cached photo fallback, preserved catalog, recovered media and Back");
            result.putInt("saversourcefailure_closed_connections", source.closed);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                if (screen != null) {
                    Activity active = screen;
                    SettingsStructureProbe.main(test, () -> { if (!active.isDestroyed()) active.finish(); });
                    awaitClosed(test, active);
                }
                drain(worker);
                synchronized (SaverCache.class) { singleton.set(null, null); }
                SettingsStructureProbe.restore(prefs, saved);
                Files.deleteIfExists(catalog.toPath());
                Files.deleteIfExists(catalogPart.toPath());
                File[] files = root.listFiles();
                require(files != null, "Fixture cache is inaccessible");
                for (File file : files) Files.delete(file.toPath());
                Files.delete(root.toPath());
            } catch (Exception | Error cleanup) {
                if (primary == null) throw cleanup;
                primary.addSuppressed(cleanup);
            }
        }
    }

    private static SaverMedia media(String name, String credit) {
        return new SaverMedia("https://images-assets.nasa.gov/carpe-saver-" + name + ".jpg", name, credit,
                "https://example.invalid/media", "Test fixture", "image");
    }

    private static Activity open(Instrumentation test) {
        return test.startActivitySync(new Intent(test.getTargetContext(), ScreensaverActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static void close(Instrumentation test, Activity activity) throws Exception {
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        awaitClosed(test, activity);
    }

    private static void awaitClosed(Instrumentation test, Activity activity) throws Exception {
        SettingsStructureProbe.await(test, activity::isDestroyed, "Back did not exit the saver");
        ExecutorService decoder = (ExecutorService) field(ScreensaverActivity.class, "decoder").get(activity);
        require(decoder.awaitTermination(10, TimeUnit.SECONDS), "Saver decoder did not terminate");
    }

    private static boolean visiblePhoto(Activity activity) throws Exception {
        ImageView photo = (ImageView) field(ScreensaverActivity.class, "first").get(activity);
        return activity.hasWindowFocus() && photo.isShown() && photo.getAlpha() == 1f
                && photo.getDrawable() instanceof BitmapDrawable bitmap && bitmap.getBitmap().getWidth() >= 1280;
    }

    private static String credit(Activity activity) {
        TextView text = activity.findViewById(R.id.saver_credit);
        return text.getText().toString();
    }

    private static void drain(ExecutorService worker) throws Exception {
        worker.submit(() -> { }).get(10, TimeUnit.SECONDS);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class Source extends URLStreamHandler {
        final String cached, incoming;
        final byte[] photo;
        volatile boolean recovered;
        int catalogFailures, mediaFailures, successes, closed, unexpected;

        Source(String cached, String incoming, byte[] photo) {
            this.cached = cached;
            this.incoming = incoming;
            this.photo = photo;
        }

        @Override protected HttpURLConnection openConnection(URL url) throws IOException {
            String address = url.toString();
            boolean catalog = address.startsWith("https://commons.wikimedia.org/w/api.php?")
                    || address.startsWith("https://images-api.nasa.gov/search?");
            if (!catalog && !address.equals(cached) && !address.equals(incoming)) {
                unexpected++;
                throw new IOException("Unexpected test request: " + url.getHost());
            }
            boolean mediaRestored = recovered && address.equals(incoming);
            return new HttpURLConnection(url) {
                @Override public void connect() {}
                @Override public boolean usingProxy() { return false; }
                @Override public void disconnect() { closed++; }
                @Override public int getResponseCode() {
                    if (catalog) catalogFailures++;
                    else if (mediaRestored) successes++;
                    else mediaFailures++;
                    return mediaRestored ? HTTP_OK : HTTP_UNAVAILABLE;
                }
                @Override public long getContentLengthLong() { return photo.length; }
                @Override public InputStream getInputStream() throws IOException {
                    if (!mediaRestored) throw new IOException("Test media unavailable");
                    return new ByteArrayInputStream(photo);
                }
            };
        }
    }
}

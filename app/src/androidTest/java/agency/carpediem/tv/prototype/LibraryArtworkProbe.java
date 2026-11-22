package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Counts retained artwork by bitmap identity across the actual launcher owners. */
final class LibraryArtworkProbe {
    private static final String NO_MATCH = "carpe-artwork-no-match-93715463";

    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package required");
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        MainActivity screen = null;
        try {
            require(prefs.edit().putBoolean("welcome_seen", true).remove("last_opened_package").commit(),
                    "Could not prepare isolated first launch");
            test.setInTouchMode(false);
            screen = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity app = screen;
            SettingsStructureProbe.await(test, () -> app.findViewById(R.id.app_icon) != null,
                    "Owned Home artwork tile unavailable");
            focus(test, app, R.id.nav_apps);
            SettingsStructureProbe.await(test, () -> app.findViewById(R.id.app_icon) != null
                    && Boolean.TRUE.equals(field(app, "appsReady")), "Real catalog unavailable");
            LruCache<String, Drawable.ConstantState> cache = cache(app);
            awaitArtwork(test, app);
            long loaded = checkpoint(test, app, cache, result, "warm_apps");
            setQuery(test, app, NO_MATCH);
            SettingsStructureProbe.main(test, () -> {
                cache.evictAll();
                require(cache.size() == 0, "LRU eviction control did not empty the cache");
            });
            long retained = checkpoint(test, app, cache, result, "no_match_after_lru_eviction");
            require(retained > 0, "No live/pooled artwork witness after LRU eviction");
            for (int cycle = 1; cycle <= 3; cycle++) {
                setQuery(test, app, "");
                awaitArtwork(test, app);
                focus(test, app, R.id.nav_settings);
                checkpoint(test, app, cache, result, "cached_apps_" + cycle);
                focus(test, app, R.id.nav_apps);
                awaitArtwork(test, app);
                checkpoint(test, app, cache, result, "active_apps_" + cycle);
                if (cycle < 3) setQuery(test, app, NO_MATCH);
            }
            result.putString("libraryartwork", "PASS: identity-based bitmap accounting, cache/pool bounds and detached ownership");
            result.putString("artwork_eviction_witness_bytes", loaded + " before, " + retained + " retained");
        } finally {
            try {
                if (screen != null) {
                    SettingsStructureProbe.finish(test, screen);
                    SettingsStructureProbe.quiescent(test, screen);
                }
            } finally {
                SettingsStructureProbe.restore(prefs, saved);
            }
        }
    }

    private static void focus(Instrumentation test, MainActivity app, int id) throws Exception {
        SettingsStructureProbe.main(test, () -> require(app.findViewById(id).requestFocus(), "Rail focus unavailable"));
        SettingsStructureProbe.await(test, () -> app.findViewById(id).isSelected(), "Rail page not selected");
    }

    private static void setQuery(Instrumentation test, MainActivity app, String value) throws Exception {
        SettingsStructureProbe.main(test, () -> {
            EditText search = app.findViewById(R.id.apps_search);
            require(search.requestFocus(), "Search focus unavailable");
            search.setText(value);
        });
        SettingsStructureProbe.await(test, () -> (app.findViewById(R.id.apps_grid).findViewById(R.id.app_icon) == null)
                == !value.isEmpty(), "Query did not settle on expected catalog state");
    }

    private static void awaitArtwork(Instrumentation test, MainActivity app) throws Exception {
        ((ExecutorService) field(app, "worker")).submit(() -> {}).get(12, TimeUnit.SECONDS);
        test.waitForIdleSync();
        SettingsStructureProbe.await(test, () -> {
            Set<Bitmap> images = bitmaps();
            collect(app.findViewById(R.id.apps_grid), images);
            return images.size() >= 2;
        }, "Two distinct actual catalog bitmaps unavailable");
    }

    private static long checkpoint(Instrumentation test, MainActivity app,
            LruCache<String, Drawable.ConstantState> cache, Bundle result, String label) throws Exception {
        long[] value = new long[1];
        SettingsStructureProbe.main(test, () -> {
            Set<Bitmap> render = bitmaps(), dormant = bitmaps(), lru = bitmaps(), detached = bitmaps();
            Map<?, ?> pages = (Map<?, ?>) field(app, "pageViews");
            View active = (View) field(app, "content");
            for (Object page : pages.values()) collect((View) page, page == active ? render : dormant);
            require(cache.size() <= cache.maxSize(), "Artwork LRU exceeds its byte budget");
            for (Drawable.ConstantState icon : cache.snapshot().values()) add(icon.newDrawable(), lru);
            View grid = ((View) pages.get("apps")).findViewById(R.id.apps_grid);
            LibraryTilePool pool = (LibraryTilePool) field(grid.getTag(), "pool");
            require(pool.size() <= LibraryTilePool.MAX_COUNT && pool.bytes() <= LibraryTilePool.MAX_BYTES,
                    "Detached tile pool exceeds its budget");
            Map<?, ?> entries = (Map<?, ?>) field(pool, "entries");
            for (Object entry : entries.values()) {
                View tile = (View) field(entry, "tile");
                require(tile.getParent() == null, "Pooled tile retained a row");
                collect(tile, detached);
            }
            Set<Bitmap> union = bitmaps();
            union.addAll(render);
            union.addAll(dormant);
            union.addAll(lru);
            union.addAll(detached);
            value[0] = bytes(union);
            result.putString("artwork_" + label, "active_tree=" + bytes(render) + " inactive_trees=" + bytes(dormant)
                    + " lru=" + bytes(lru) + " detached=" + bytes(detached) + " unique=" + value[0]
                    + " bitmaps=" + union.size());
        });
        return value[0];
    }

    @SuppressWarnings("unchecked")
    private static LruCache<String, Drawable.ConstantState> cache(MainActivity app) throws Exception {
        return (LruCache<String, Drawable.ConstantState>) field(app, "icons");
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Set<Bitmap> bitmaps() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void collect(View root, Set<Bitmap> images) {
        if (root instanceof ImageView && root.getId() == R.id.app_icon)
            add(((ImageView) root).getDrawable(), images);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) collect(group.getChildAt(index), images);
        }
    }

    private static void add(Drawable drawable, Set<Bitmap> images) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null && !bitmap.isRecycled()) images.add(bitmap);
        }
    }

    private static long bytes(Set<Bitmap> images) {
        return images.stream().mapToLong(Bitmap::getAllocationByteCount).sum();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

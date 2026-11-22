package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
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
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Device-only gallery, async-loading and persistence checks. */
final class WallpaperProbe {
    static void run(Instrumentation probe, Bundle result) throws Exception {
        android.content.Context context=probe.getTargetContext();
        android.content.SharedPreferences prefs=context.getSharedPreferences(MainActivity.class.getName(),0);
        String original=prefs.getString("wallpaper",null);
        boolean hadWelcome=prefs.contains("welcome_seen");
        boolean originalWelcome=prefs.getBoolean("welcome_seen",false);
        boolean hadRotation=prefs.contains(WallpaperRotation.ENABLED);
        boolean originalRotation=prefs.getBoolean(WallpaperRotation.ENABLED,false);
        boolean hadChangedAt=prefs.contains(WallpaperRotation.CHANGED_AT);
        long originalChangedAt=prefs.getLong(WallpaperRotation.CHANGED_AT,0);
        boolean hadAnimations=prefs.contains("animations");
        boolean originalAnimations=prefs.getBoolean("animations",true);
        if(!"agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()))
            throw new IllegalStateException("Wallpaper probe requires the isolated preview package");
        Activity[] sessions=new Activity[2];
        Throwable primaryFailure=null;
        try {
            if(!prefs.edit().putBoolean("welcome_seen",true).putBoolean("animations",true).putBoolean(WallpaperRotation.ENABLED,false).remove("wallpaper").commit())
                throw new AssertionError("Cannot prepare wallpaper probe preferences");
            probe.setInTouchMode(false);
            Activity screen=sessions[0]=probe.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1200);
            probe.runOnMainSync(() -> screen.findViewById(R.id.nav_settings).requestFocus());
            SystemClock.sleep(500);
            probe.runOnMainSync(() -> {
                android.widget.HorizontalScrollView gallery = screen.findViewById(R.id.wallpaper_scroll);
                View active = screen.findViewById(R.id.wallpaper_blue);
                View card = (View) active.getParent();
                if (!active.isSelected() || card.getLeft() < gallery.getScrollX()
                        || card.getRight() > gallery.getScrollX() + gallery.getWidth())
                    throw new AssertionError("Selected wallpaper is not fully visible in the gallery");
            });
            if(prefs.contains("wallpaper")) throw new AssertionError("Implicit blue default overwrote the saved wallpaper preference");
            result.putString("wallpaper_default","PASS: no saved choice shows Blue wallpaper as selected without writing the preference");
            verifyBlueMotion(probe,screen,result);
            java.util.List<WallpaperCatalog.Entry> entries=WallpaperCatalog.entries(context);
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(200);
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(200);
            if (screen.getCurrentFocus() == null || screen.getCurrentFocus().getId() != R.id.wallpaper_blue)
                throw new AssertionError("Gallery entry did not focus the selected wallpaper");
            for (int i=0;i<3;i++) { probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT); SystemClock.sleep(120); }
            for(int i=0;i<entries.size();i++) {
                WallpaperCatalog.Entry entry=entries.get(i);
                if(i>0) { probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); SystemClock.sleep(120); }
                int[] focus={View.NO_ID};
                probe.runOnMainSync(() -> {if(screen.getCurrentFocus()!=null) focus[0]=screen.getCurrentFocus().getId();});
                if(focus[0]!=entry.viewId) throw new AssertionError("Gallery focus: "+entry.key+", actual="+focus[0]);
                probe.runOnMainSync(() -> screen.findViewById(entry.viewId).performClick());
                awaitLoaded(probe,screen,entry.key);
                if(!entry.key.equals(prefs.getString("wallpaper",""))) throw new AssertionError("Preference: "+entry.key);
                probe.runOnMainSync(() -> { if(!screen.findViewById(entry.viewId).isSelected()) throw new AssertionError("Selection: "+entry.key); });
                SettingsStructureProbe.await(probe, () -> screen.findViewById(R.id.blue_wallpaper_motion).isShown()==entry.key.equals("blue"),
                        "Blue glow visibility is wrong for "+entry.key);
            }
            probe.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); SystemClock.sleep(150);
            probe.runOnMainSync(() -> { if(screen.getCurrentFocus().getId()!=R.id.dynamic_wallpapers) throw new AssertionError("Down to dynamic mode"); });
            verifyGalleryCorruptCache(probe,screen,result);
            // In-flight results must never replace the user's last choice.
            probe.runOnMainSync(() -> {
                for(String key:new String[]{"alpine","aurora","como","alpine","aurora"})
                    screen.findViewById(WallpaperCatalog.find(context,key).viewId).performClick();
            });
            awaitLoaded(probe,screen,"aurora");
            verifyReturnToLoadedPhoto(probe,screen);
            finishSession(probe,screen);
            Activity reopened=sessions[1]=probe.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            awaitLoaded(probe,reopened,"aurora");
            result.putString("wallpapers","PASS: "+entries.size()+" choices, D-pad scrolling, active marker, loading, rapid switches, return to loaded photo and restart persistence");
            result.putLong("native_heap_bytes",android.os.Debug.getNativeHeapAllocatedSize());
        } catch(Exception|Error failure) {
            primaryFailure=failure;
            throw failure;
        } finally {
            Throwable cleanupFailure=null;
            for(Activity session:sessions) if(session!=null) {
                try {finishSession(probe,session);}
                catch(Exception|Error failure) {cleanupFailure=appendFailure(cleanupFailure,failure);}
            }
            try {
                // Do not race preference restoration against a worker that failed
                // to stop. Report cleanup failure instead of claiming restoration.
                for(Activity session:sessions) if(session!=null) requireQuiescent(probe,session);
                android.content.SharedPreferences.Editor edit=prefs.edit();
                if(original==null) edit.remove("wallpaper"); else edit.putString("wallpaper",original);
                if(hadWelcome) edit.putBoolean("welcome_seen",originalWelcome); else edit.remove("welcome_seen");
                if(hadRotation) edit.putBoolean(WallpaperRotation.ENABLED,originalRotation); else edit.remove(WallpaperRotation.ENABLED);
                if(hadChangedAt) edit.putLong(WallpaperRotation.CHANGED_AT,originalChangedAt); else edit.remove(WallpaperRotation.CHANGED_AT);
                if(hadAnimations) edit.putBoolean("animations",originalAnimations); else edit.remove("animations");
                if(!edit.commit()) throw new AssertionError("Cannot restore wallpaper probe preferences");
            } catch(Exception|Error failure) {cleanupFailure=appendFailure(cleanupFailure,failure);}
            if(cleanupFailure!=null) {
                if(primaryFailure!=null) primaryFailure.addSuppressed(cleanupFailure);
                else rethrow(cleanupFailure);
            }
        }
    }
    private static void verifyBlueMotion(Instrumentation probe, Activity screen, Bundle result) throws Exception {
        View glow=screen.findViewById(R.id.blue_wallpaper_motion);
        if(!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            result.putString("blue_wallpaper_motion","INPUT_UNVERIFIED: platform animations are disabled");
            return;
        }
        SettingsStructureProbe.await(probe,()->glow.isShown()&&Math.abs(glow.getTranslationY())>1f,
                "Selected blue glow did not move");
        measureBlueEdges(probe, screen, glow, -1, "upper", result);
        measureBlueEdges(probe, screen, glow, 0, "middle", result);
        measureBlueEdges(probe, screen, glow, 1, "lower", result);
        float[] position={0};
        SettingsStructureProbe.main(probe,()->position[0]=glow.getTranslationY());
        SettingsStructureProbe.await(probe,()->Math.abs(glow.getTranslationY()-position[0])>2f,
                "Blue glow did not continue moving");
        SettingsStructureProbe.main(probe,()->((android.widget.Switch)screen.findViewById(R.id.animations)).setChecked(false));
        SystemClock.sleep(400);
        SettingsStructureProbe.main(probe,()->{
            if (glow.getTranslationY() != 0f)
                throw new AssertionError("Disabling animations did not stop the blue glow");
        });
        verifyStillBluePixels(probe, screen, (BlueWallpaperMotion) glow, result);
        SettingsStructureProbe.main(probe,()->((android.widget.Switch)screen.findViewById(R.id.animations)).setChecked(true));
        SettingsStructureProbe.await(probe,()->Math.abs(glow.getTranslationY())>1f,"Enabled blue glow did not resume");
        Activity updates=probe.startActivitySync(new Intent(probe.getTargetContext(),UpdatesActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            SettingsStructureProbe.await(probe,()->updates.hasWindowFocus()&&!screen.hasWindowFocus(),
                    "Updates did not take the foreground for the wallpaper pause");
            SettingsStructureProbe.main(probe,()->position[0]=glow.getTranslationY());
            SystemClock.sleep(650);
            SettingsStructureProbe.main(probe,()->{
                if(glow.getTranslationY()!=position[0]) throw new AssertionError("Blue glow kept moving with another Activity in front");
            });
        } finally {
            SettingsStructureProbe.main(probe,updates::finish);
        }
        SettingsStructureProbe.await(probe,()->screen.hasWindowFocus()&&Math.abs(glow.getTranslationY()-position[0])>1f,
                "Blue glow did not restart after returning from Updates");
        result.putString("blue_wallpaper_motion","PASS: toggle keeps a still scene, foreground switch pauses motion, returning resumes it");
    }

    private static void verifyStillBluePixels(Instrumentation probe, Activity screen, BlueWallpaperMotion glow,
                                             Bundle result) throws Exception {
        if (!glow.canUseStillWallpaper()) {
            SettingsStructureProbe.main(probe, () -> requireVisibleStillLayers(glow));
            result.putString("blue_still_pixels", "Uses the original layers outside the still texture aspect ratio");
            return;
        }
        android.widget.ImageView[] active = {null};
        android.graphics.drawable.Drawable[] still = {null};
        SettingsStructureProbe.main(probe, () -> {
            for (int id : new int[]{R.id.scene_a, R.id.scene_b}) {
                android.widget.ImageView scene = screen.findViewById(id);
                if (scene.getAlpha() == 1f && scene.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
                    active[0] = scene;
                    still[0] = scene.getDrawable();
                }
            }
            if (active[0] == null || glow.isShown()) {
                throw new AssertionError("Still Blue did not use a single opaque scene");
            }
        });
        int[] composed = captureBlueStrip(screen);
        int[] layered;
        try {
            SettingsStructureProbe.main(probe, () -> {
                active[0].setImageResource(R.drawable.blue_wallpaper);
                glow.setVisibility(View.VISIBLE);
            });
            SystemClock.sleep(120);
            layered = captureBlueStrip(screen);
        } finally {
            SettingsStructureProbe.main(probe, () -> {
                active[0].setImageDrawable(still[0]);
                glow.setVisibility(View.INVISIBLE);
            });
        }
        long total = 0;
        int maximum = 0;
        for (int i = 0; i < composed.length; i++) {
            for (int shift : new int[]{16, 8, 0}) {
                int diff = Math.abs(((composed[i] >> shift) & 255) - ((layered[i] >> shift) & 255));
                total += diff;
                maximum = Math.max(maximum, diff);
            }
        }
        double mean = total / (3.0 * composed.length);
        result.putString("blue_still_pixels", String.format(Locale.ROOT, "maximum channel delta %d; mean %.3f", maximum, mean));
        if (maximum > 6 || mean > 1.2) throw new AssertionError("Still Blue pixels exceed the allowed difference");
    }

    private static void requireVisibleStillLayers(BlueWallpaperMotion glow) {
        if (!glow.isShown() || glow.getTranslationY() != 0f) {
            throw new AssertionError("Nonstandard aspect ratio must keep the original still layers");
        }
    }

    private static int[] captureBlueStrip(Activity screen) throws InterruptedException {
        View decor = screen.getWindow().getDecorView();
        int width = decor.getWidth();
        int height = decor.getHeight();
        Rect strip = new Rect(width - 40, 0, width - 16, height);
        Bitmap bitmap = Bitmap.createBitmap(strip.width(), height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[strip.width() * height];
        CountDownLatch complete = new CountDownLatch(1);
        int[] copied = {-1};
        try {
            PixelCopy.request(screen.getWindow(), strip, bitmap, status -> {
                try {
                    if (status == PixelCopy.SUCCESS) {
                        bitmap.getPixels(pixels, 0, strip.width(), 0, 0, strip.width(), height);
                    }
                    copied[0] = status;
                } finally {
                    bitmap.recycle();
                    complete.countDown();
                }
            }, new Handler(Looper.getMainLooper()));
        } catch (RuntimeException error) {
            bitmap.recycle();
            throw error;
        }
        if (!complete.await(4, TimeUnit.SECONDS) || copied[0] != PixelCopy.SUCCESS) {
            throw new AssertionError("Cannot sample rendered still Blue wallpaper: " + copied[0]);
        }
        return pixels;
    }

    private static void measureBlueEdges(Instrumentation probe, Activity screen, View glow, int direction,
                                         String sample, Bundle result) throws Exception {
        SettingsStructureProbe.await(probe, () -> {
            float fraction = glow.getTranslationY() / glow.getHeight();
            return direction == 0 ? Math.abs(fraction) < .002f : direction * fraction > .033f;
        }, "Blue glow did not reach the " + sample + " sample");
        float[] geometry = new float[3];
        Rect strip = new Rect();
        SettingsStructureProbe.main(probe, () -> {
            int height = glow.getHeight();
            View decor = screen.getWindow().getDecorView();
            if (glow.getTop() != 0 || height != decor.getHeight()) {
                throw new AssertionError("Blue edge sample requires a full-height wallpaper");
            }
            float delta = glow.getScaleY() - 1f;
            float translated = glow.getTranslationY();
            geometry[0] = translated / height;
            geometry[1] = delta * glow.getPivotY() - translated;
            geometry[2] = delta * (height - glow.getPivotY()) + translated;
            int[] position = new int[2];
            glow.getLocationInWindow(position);
            // The right margin of Settings contains no controls and stays outside the sidebar.
            int right = position[0] + glow.getWidth();
            strip.set(right - 40, 0, right - 16, height);
        });
        if (Math.abs(geometry[0]) > .036f || geometry[1] < glow.getHeight() * .03f
                || geometry[2] < glow.getHeight() * .03f) {
            throw new AssertionError("Blue texture did not cover both frame edges at " + sample);
        }
        double edgeJump = blueEdgeJump(screen, strip);
        if (edgeJump > 8.0) throw new AssertionError("Abrupt horizontal seam at " + sample + ": " + edgeJump);
        result.putString("blue_wallpaper_edge_" + sample, String.format(Locale.ROOT,
                "offset %.2f%%; top %.1f px, bottom %.1f px; peak adjacent-row RGB delta %.3f",
                geometry[0] * 100, geometry[1], geometry[2], edgeJump));
    }

    private static double blueEdgeJump(Activity screen, Rect bounds) throws InterruptedException {
        Bitmap bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
        CountDownLatch complete = new CountDownLatch(1);
        boolean requested = false;
        try {
            int[] status = {-1};
            PixelCopy.request(screen.getWindow(), bounds, bitmap, code -> {
                status[0] = code;
                complete.countDown();
            }, new Handler(Looper.getMainLooper()));
            requested = true;
            if (!complete.await(4, TimeUnit.SECONDS) || status[0] != PixelCopy.SUCCESS) {
                throw new AssertionError("Cannot sample rendered Blue wallpaper: " + status[0]);
            }
            double peak = 0;
            int height = bitmap.getHeight();
            for (int y = 1; y < height; y++) {
                if (y > height / 10 && y < height - height / 10) continue;
                int red = 0, green = 0, blue = 0;
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    int before = bitmap.getPixel(x, y - 1);
                    int after = bitmap.getPixel(x, y);
                    red += Math.abs(Color.red(after) - Color.red(before));
                    green += Math.abs(Color.green(after) - Color.green(before));
                    blue += Math.abs(Color.blue(after) - Color.blue(before));
                }
                peak = Math.max(peak, (double) Math.max(red, Math.max(green, blue)) / bitmap.getWidth());
            }
            return peak;
        } finally {
            if (!requested || complete.getCount() == 0) bitmap.recycle();
        }
    }
    private static void finishSession(Instrumentation probe,Activity activity) throws Exception {
        Field mainField=MainActivity.class.getDeclaredField("worker");mainField.setAccessible(true);
        Field wallpaperField=MainActivity.class.getDeclaredField("wallpaperWorker");wallpaperField.setAccessible(true);
        ExecutorService[] workers={(ExecutorService)mainField.get(activity),(ExecutorService)wallpaperField.get(activity)};
        Throwable failure=null;
        try {
            probe.runOnMainSync(()->{if(!activity.isDestroyed()) activity.finish();});
            probe.waitForIdleSync();
        } catch(Exception|Error error) {failure=appendFailure(failure,error);}
        // Finish normally invokes onDestroy/shutdownNow. Force cancellation as a
        // fallback if a failed lifecycle callback left either executor running.
        for(ExecutorService worker:workers) {
            try {
                worker.shutdownNow();
                if(!worker.awaitTermination(15,TimeUnit.SECONDS))
                    throw new AssertionError("Wallpaper probe worker did not terminate after finish");
            } catch(Exception|Error error) {failure=appendFailure(failure,error);}
        }
        // A completed decode can already have posted an Activity callback. Drain
        // these after executor termination, before restoring preferences.
        try {
            probe.waitForIdleSync();
            long until=SystemClock.uptimeMillis()+5000;
            boolean[] destroyed={false};
            while(SystemClock.uptimeMillis()<until) {
                probe.runOnMainSync(()->destroyed[0]=activity.isDestroyed());
                if(destroyed[0]) break;
                SystemClock.sleep(20);
            }
            if(!destroyed[0]) throw new AssertionError("Wallpaper probe Activity did not finish");
            probe.waitForIdleSync();
        } catch(Exception|Error error) {failure=appendFailure(failure,error);}
        if(failure!=null) rethrow(failure);
    }
    private static void requireQuiescent(Instrumentation probe,Activity activity) throws Exception {
        boolean[] destroyed={false};
        probe.runOnMainSync(()->destroyed[0]=activity.isDestroyed());
        for(String name:new String[]{"worker","wallpaperWorker"}) {
            Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
            if(!((ExecutorService)field.get(activity)).isTerminated())
                throw new AssertionError("Preferences were not restored: wallpaper probe worker remains active");
        }
        if(!destroyed[0]) throw new AssertionError("Preferences were not restored: wallpaper probe Activity remains active");
    }
    private static Throwable appendFailure(Throwable first,Throwable next) {
        if(first==null) return next;
        first.addSuppressed(next);return first;
    }
    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Error) throw (Error)failure;
        if(failure instanceof Exception) throw (Exception)failure;
        throw new AssertionError(failure);
    }
    private static void verifyReturnToLoadedPhoto(Instrumentation probe,Activity screen) throws Exception {
        // Aurora is already decoded. Block the worker so Alpine cannot finish
        // before the user returns to Aurora in the same UI turn.
        Field workerField=MainActivity.class.getDeclaredField("wallpaperWorker");workerField.setAccessible(true);
        Field loadedField=MainActivity.class.getDeclaredField("loadedWallpaper");loadedField.setAccessible(true);
        Field sharpField=MainActivity.class.getDeclaredField("sharpWallpaper");sharpField.setAccessible(true);
        ExecutorService worker=(ExecutorService)workerField.get(screen);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<Boolean> blocker=worker.submit(()->{entered.countDown();return release.await(10,TimeUnit.SECONDS);});
        Throwable primaryFailure=null;
        try {
            if(!entered.await(5,TimeUnit.SECONDS)) throw new AssertionError("Wallpaper worker did not reach the test barrier");
            Object[] originalBitmap={null};
            probe.runOnMainSync(()->{
                try {
                    if(!"aurora".equals(loadedField.get(screen))) throw new AssertionError("Aurora was not loaded before reversal");
                    originalBitmap[0]=sharpField.get(screen);
                    if(originalBitmap[0]==null) throw new AssertionError("Loaded Aurora bitmap is missing");
                    screen.findViewById(R.id.wallpaper_alpine).performClick();
                    screen.findViewById(R.id.wallpaper_aurora).performClick();
                } catch(IllegalAccessException error) {throw new AssertionError(error);}
            });
            release.countDown();
            if(!blocker.get(5,TimeUnit.SECONDS)) throw new AssertionError("Wallpaper barrier timed out before reversal completed");
            // Drain queued decoding, then its UI callback. Merely awaiting Aurora
            // here would falsely pass while the stale Alpine task was still queued.
            worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            probe.waitForIdleSync();
            probe.runOnMainSync(()->{
                try {
                    if(!"aurora".equals(loadedField.get(screen)) || sharpField.get(screen)!=originalBitmap[0])
                        throw new AssertionError("Pending Alpine replaced the returned-to Aurora photo");
                    if(!screen.getPreferences(0).getString("wallpaper","").equals("aurora")
                            || !screen.findViewById(R.id.wallpaper_aurora).isSelected())
                        throw new AssertionError("Wallpaper preference or active marker disagrees after reversal");
                } catch(IllegalAccessException error) {throw new AssertionError(error);}
            });
        } catch(Exception|Error failure) {
            primaryFailure=failure;
            throw failure;
        } finally {
            release.countDown();
            try {
                blocker.get(5,TimeUnit.SECONDS);
                worker.submit(()->{}).get(5,TimeUnit.SECONDS);
                probe.waitForIdleSync();
            } catch(Exception|Error failure) {
                // The outer cleanup still finishes the Activity and terminates
                // both executors if this barrier itself times out or fails.
                if(primaryFailure!=null) primaryFailure.addSuppressed(failure);
                else rethrow(failure);
            }
        }
    }
    private static void verifyGalleryCorruptCache(Instrumentation test,Activity screen,Bundle result)throws Exception {
        test.runOnMainSync(()->screen.findViewById(R.id.wallpaper_aurora).requestFocus());
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);awaitLoaded(test,screen,"aurora");
        SettingsStructureProbe.await(test,()->visibleBitmap(screen,wallpaperField(screen,"settingsWallpaper")),
                "Prior Settings photo did not become visible");
        java.io.File cache=new java.io.File(screen.getCacheDir(),"wallpaper-alpine-blur-v4.png");
        if(!cache.isFile()||cache.length()>PngCacheIntegrity.MAX_BYTES)throw new AssertionError("Expected bounded actual alpine cache");
        byte[] original=java.nio.file.Files.readAllBytes(cache.toPath());
        if(!PngCacheIntegrity.valid(cache))throw new AssertionError("Gallery did not produce initial valid alpine PNG");
        Field workerField=MainActivity.class.getDeclaredField("wallpaperWorker");workerField.setAccessible(true);
        ExecutorService worker=(ExecutorService)workerField.get(screen);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> gate=worker.submit(()->{entered.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Gallery decode gate timeout");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}});
        Throwable primary=null;
        try {
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("Gallery decode gate unavailable");
            java.nio.file.Files.write(cache.toPath(),java.util.Arrays.copyOf(original,Math.max(8,original.length/2)));
            if(PngCacheIntegrity.valid(cache))throw new AssertionError("Truncated actual cache unexpectedly valid");
            Object[] old=new Object[4];String[] names={"sharpWallpaper","blurredWallpaper","appsWallpaper","settingsWallpaper"};
            SettingsStructureProbe.main(test,()->{for(int i=0;i<names.length;i++)old[i]=wallpaperField(screen,names[i]);screen.findViewById(R.id.wallpaper_alpine).requestFocus();});
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{
                if(!"alpine".equals(screen.getPreferences(0).getString("wallpaper",null))||!screen.findViewById(R.id.wallpaper_alpine).isSelected()
                        ||!"aurora".equals(wallpaperField(screen,"loadedWallpaper")))throw new AssertionError("Gallery selection/loading key contract failed");
                for(int i=0;i<names.length;i++)if(old[i]!=wallpaperField(screen,names[i]))throw new AssertionError("Pending decode replaced prior layer");
                if(!visibleBitmap(screen,old[3]))throw new AssertionError("Prior Settings photo is not the visible scene while decode blocked");
            });
            release.countDown();gate.get(5,TimeUnit.SECONDS);awaitLoaded(test,screen,"alpine");
            SettingsStructureProbe.await(test,()->{
                Object current=wallpaperField(screen,"settingsWallpaper");
                return current!=old[3]&&visibleBitmap(screen,current);
            },"Recovered Settings photo did not become visible");
            if(!PngCacheIntegrity.valid(cache))throw new AssertionError("Actual gallery route did not repair corrupt PNG");
            SettingsStructureProbe.main(test,()->{
                for(String name:names)if(wallpaperField(screen,name)==null)throw new AssertionError("Recovered scene layer missing");
                Object current=wallpaperField(screen,"settingsWallpaper");
                if(current==old[3]||!visibleBitmap(screen,current))throw new AssertionError("Recovered Settings photo not visibly bound");
                screen.findViewById(R.id.dynamic_wallpapers).requestFocus();
            });
            result.putString("wallpaper_gallery_recovery","PASS: real gallery OK, prior visible photo retained behind blocked decode, actual truncated private PNG regenerated and recovered scene visibly bound");
        }catch(Exception|Error error){primary=error;throw error;}
        finally {
            release.countDown();
            try{gate.get(5,TimeUnit.SECONDS);worker.submit(()->{}).get(15,TimeUnit.SECONDS);test.waitForIdleSync();java.nio.file.Files.write(cache.toPath(),original);}
            catch(Exception|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    private static Object wallpaperField(Activity activity,String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}catch(Exception error){throw new AssertionError(error);}}
    private static boolean visibleBitmap(Activity activity,Object bitmap){
        for(int id:new int[]{R.id.scene_a,R.id.scene_b}) {
            android.widget.ImageView layer=activity.findViewById(id);
            if(layer.isShown()&&layer.getAlpha()>.9f&&layer.getDrawable() instanceof android.graphics.drawable.BitmapDrawable
                    &&((android.graphics.drawable.BitmapDrawable)layer.getDrawable()).getBitmap()==bitmap)return true;
        }
        return false;
    }
    private static void awaitLoaded(Instrumentation probe,Activity activity,String key) throws Exception {
        Field field=MainActivity.class.getDeclaredField("loadedWallpaper");field.setAccessible(true);
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end) {
            boolean[] ready={false};probe.runOnMainSync(() -> {try {ready[0]=key.equals(field.get(activity));}catch(Exception error){throw new AssertionError(error);}});
            if(ready[0]) return;SystemClock.sleep(60);
        }
        throw new AssertionError("Wallpaper did not finish: "+key);
    }
}

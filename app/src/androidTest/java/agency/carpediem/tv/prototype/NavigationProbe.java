package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.view.FrameMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Device-only navigation/frames probe. No dependency or code is shipped in the launcher APK. */
public final class NavigationProbe extends Instrumentation {
    private Bundle args;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args=arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        if (args != null && "yes".equals(args.getString("pairinglimit"))) {
            try { PairingLimitProbe.run(getTargetContext(), result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("firstrun"))) {
            try {
                if (!getTargetContext().getPackageName().endsWith(".publicpreview"))
                    throw new IllegalStateException("First-run probe requires the isolated preview package");
                FirstRunProbe.run(this,result); finish(Activity.RESULT_OK,result);
            } catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("sleeplive"))) {
            try { SleepTimerProbe.armLiveTest(getTargetContext(),result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("sleeptimer"))) {
            try { SleepTimerProbe.run(getTargetContext(),result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("onlineplayback"))) {
            try { OnlinePlaybackProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("cachepolicy"))) {
            try { SaverCacheProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("screensaver"))) {
            try { ScreensaverProbe.run(this, result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updates"))) {
            try { UpdateProbe.run(getTargetContext(),args,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("rotation"))) {
            try { RotationProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("wallpapers"))) {
            try { WallpaperProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("homefocus"))) {
            try { HomeFocusProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("languagestate"))) {
            try { LanguageStateProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("feedback"))) {
            try { FeedbackProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("reorder"))) {
            try { ReorderProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("appaudit"))) {
            try { AppAuditProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("systemaccess"))) {
            try { SystemAccessProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("systemmutation"))) {
            try { SystemMutationProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        HandlerThread metricsThread = new HandlerThread("luma-frame-probe");
        metricsThread.start();
        List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        List<long[]> stages = Collections.synchronizedList(new ArrayList<>());
        Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0) {
                durations.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
                stages.add(new long[]{
                        metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                        metrics.getMetric(FrameMetrics.ANIMATION_DURATION),
                        metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                        metrics.getMetric(FrameMetrics.DRAW_DURATION),
                        metrics.getMetric(FrameMetrics.SYNC_DURATION),
                        metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
                        metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)});
            }
        };
        Activity activity = null;
        boolean[] listenerAdded = {false};
        int statusCode = Activity.RESULT_CANCELED;
        SharedPreferences previewPreferences = null;
        boolean originalWelcomeSeen = false;
        boolean originalAnimations = true;
        boolean compareWithoutMotion = args != null && "yes".equals(args.getString("noMotion"));
        try {
            if (getTargetContext().getPackageName().endsWith(".publicpreview")) {
                previewPreferences = getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
                originalWelcomeSeen = previewPreferences.getBoolean("welcome_seen", false);
                originalAnimations = previewPreferences.getBoolean("animations", true);
                if (!previewPreferences.edit().putBoolean("welcome_seen", true).commit())
                    throw new IllegalStateException("Cannot prepare isolated navigation probe");
                if (compareWithoutMotion && !previewPreferences.edit().putBoolean("animations", false).commit())
                    throw new IllegalStateException("Cannot prepare reduced-motion probe");
            }
            setInTouchMode(false);
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final Activity screen = activity;
            SystemClock.sleep(1500);
            boolean[] backgroundReady = {false};
            long backgroundDeadline = SystemClock.uptimeMillis() + 5000;
            do {
                runOnMainSync(() -> {
                    android.widget.ImageView a = screen.findViewById(R.id.scene_a);
                    android.widget.ImageView b = screen.findViewById(R.id.scene_b);
                    backgroundReady[0] = (a.getDrawable() != null && a.getImageAlpha() == 255)
                            || (b.getDrawable() != null && b.getImageAlpha() == 255);
                });
                if (!backgroundReady[0]) SystemClock.sleep(100);
            } while (!backgroundReady[0] && SystemClock.uptimeMillis() < backgroundDeadline);
            if (!backgroundReady[0]) throw new AssertionError("Neither wallpaper layer is ready");
            runOnMainSync(() -> screen.findViewById(R.id.nav_home).requestFocus());
            key(KeyEvent.KEYCODE_DPAD_DOWN, 300);
            requireView(screen, R.id.apps_title);
            requireFocus(screen, R.id.nav_apps);
            key(KeyEvent.KEYCODE_DPAD_DOWN, 300);
            requireView(screen, R.id.settings_heading);
            requireFocus(screen, R.id.nav_settings);
            key(KeyEvent.KEYCODE_DPAD_UP, 300);
            requireView(screen, R.id.apps_title);
            requireFocus(screen, R.id.nav_apps);
            key(KeyEvent.KEYCODE_DPAD_RIGHT, 350);
            runOnMainSync(() -> {
                screen.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(metricsThread.getLooper()));
                listenerAdded[0] = true;
            });
            for (int i = 0; i < 12; i++) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT, 300); key(KeyEvent.KEYCODE_DPAD_RIGHT, 300);
                key(KeyEvent.KEYCODE_DPAD_LEFT, 300); key(KeyEvent.KEYCODE_DPAD_LEFT, 300);
            }
            for (int i = 0; i < 8; i++) {
                key(KeyEvent.KEYCODE_DPAD_RIGHT, 70); key(KeyEvent.KEYCODE_DPAD_LEFT, 70);
            }
            SystemClock.sleep(350);
            runOnMainSync(() -> { screen.getWindow().removeOnFrameMetricsAvailableListener(listener); listenerAdded[0] = false; });
            key(KeyEvent.KEYCODE_DPAD_LEFT, 250);
            requireFocus(screen, R.id.nav_apps);
            key(KeyEvent.KEYCODE_DPAD_UP, 300);
            requireView(screen, R.id.home_time);
            requireFocus(screen, R.id.nav_home);
            List<Long> samples;
            synchronized (durations) { samples = new ArrayList<>(durations); }
            Collections.sort(samples);
            if (samples.isEmpty()) throw new AssertionError("No frame metrics collected");
            result.putString("navigation", "PASS: focus-only sidebar, right/left content, rapid input, Home");
            result.putInt("frame_count", samples.size());
            result.putDouble("frame_median_ms", samples.get(samples.size() / 2) / 1e6);
            result.putDouble("frame_p95_ms", samples.get((int) ((samples.size() - 1) * .95)) / 1e6);
            result.putDouble("frame_max_ms", samples.get(samples.size() - 1) / 1e6);
            durations.clear();
            stages.clear();
            runOnMainSync(() -> {
                screen.getWindow().addOnFrameMetricsAvailableListener(listener, new Handler(metricsThread.getLooper()));
                listenerAdded[0] = true;
            });
            for (int i = 0; i < 4; i++) {
                key(KeyEvent.KEYCODE_DPAD_DOWN, 300); key(KeyEvent.KEYCODE_DPAD_DOWN, 300);
                key(KeyEvent.KEYCODE_DPAD_UP, 300); key(KeyEvent.KEYCODE_DPAD_UP, 300);
            }
            for (int i = 0; i < 5; i++) {
                key(KeyEvent.KEYCODE_DPAD_DOWN, 50); key(KeyEvent.KEYCODE_DPAD_DOWN, 50);
                key(KeyEvent.KEYCODE_DPAD_UP, 50); key(KeyEvent.KEYCODE_DPAD_UP, 50);
            }
            SystemClock.sleep(300);
            requireView(screen, R.id.home_time); requireFocus(screen, R.id.nav_home);
            runOnMainSync(() -> { screen.getWindow().removeOnFrameMetricsAvailableListener(listener); listenerAdded[0] = false; });
            synchronized (durations) { samples = new ArrayList<>(durations); }
            Collections.sort(samples);
            if (samples.isEmpty()) throw new AssertionError("No page transition frames collected");
            result.putInt("page_frame_count", samples.size());
            result.putDouble("page_frame_median_ms", samples.get(samples.size() / 2) / 1e6);
            result.putDouble("page_frame_p95_ms", samples.get((int) ((samples.size() - 1) * .95)) / 1e6);
            result.putDouble("page_frame_max_ms", samples.get(samples.size() - 1) / 1e6);
            String[] names = {"input", "animation", "layout", "draw", "sync", "commands", "swap"};
            synchronized (stages) {
                for (int stage = 0; stage < names.length; stage++) {
                    List<Long> stageSamples = new ArrayList<>();
                    for (long[] frame : stages) if (frame[stage] >= 0) stageSamples.add(frame[stage]);
                    if (!stageSamples.isEmpty()) {
                        Collections.sort(stageSamples);
                        result.putDouble("page_" + names[stage] + "_p95_ms",
                                stageSamples.get((int) ((stageSamples.size() - 1) * .95)) / 1e6);
                    }
                }
            }
            result.putString("page_navigation", "PASS: 36 normal/rapid page changes; focus retained");
            statusCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("failure", error.toString());
        } finally {
            if (previewPreferences != null)
                previewPreferences.edit().putBoolean("welcome_seen", originalWelcomeSeen)
                        .putBoolean("animations", originalAnimations).commit();
            if (activity != null && listenerAdded[0]) {
                Activity screen = activity;
                runOnMainSync(() -> screen.getWindow().removeOnFrameMetricsAvailableListener(listener));
            }
            metricsThread.quitSafely();
        }
        finish(statusCode, result);
    }
    private void key(int code, long settleMs) { sendKeyDownUpSync(code); SystemClock.sleep(settleMs); }
    private void requireView(Activity activity, int id) {
        boolean[] found = {false};
        runOnMainSync(() -> { View view = activity.findViewById(id); found[0] = view != null && view.isShown(); });
        if (!found[0]) throw new AssertionError("Missing view " + id);
    }
    private void requireFocus(Activity activity, int id) {
        boolean[] matched = {false}; runOnMainSync(() -> {
            View focus = activity.getCurrentFocus();
            matched[0] = focus != null && focus.getId() == id;
        });
        if (!matched[0]) throw new AssertionError("Unexpected focus; expected " + id);
    }
}

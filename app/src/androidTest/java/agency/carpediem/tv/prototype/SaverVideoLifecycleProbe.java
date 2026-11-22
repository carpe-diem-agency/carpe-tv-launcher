package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

final class SaverVideoLifecycleProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation package required");
        require(UpdateManager.prefs(context).getAll().isEmpty() && UpdateManager.source().isEmpty(),
                "The cover screen requires a fresh updater with no configured feed");
        SharedPreferences prefs = Screensaver.prefs(context);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        File root = new File(context.getCacheDir(), "saver-lifecycle-" + UUID.randomUUID());
        require(root.mkdir(), "Cannot create private media fixture");
        SaverCache fixture = new SaverCache(root, 1024 * 1024, 0, () -> Long.MAX_VALUE);
        SaverMedia item = new SaverMedia("https://images-assets.nasa.gov/saver-lifecycle.mp4",
                "Playback fixture", "Playback fixture", "https://example.invalid/source", "Probe credit", "video");
        File videoFile = fixture.file(item);
        File metadata = new File(root, item.id + ".json");
        Field singleton = field(SaverCache.class, "instance");
        Field pin = field(ScreensaverActivity.class, "pinnedVideo");
        Field rendered = field(ScreensaverActivity.class, "videoVisible");
        Field ticket = field(ScreensaverActivity.class, "ticket");
        Field resumed = field(ScreensaverActivity.class, "resumed");
        Field pins = field(SaverCache.class, "pinned");
        SaverCache previous;
        synchronized (SaverCache.class) { previous = (SaverCache) singleton.get(null); }
        Activity screen = null;
        Activity cover = null;
        Throwable primary = null;
        try {
            try (InputStream input = test.getContext().getAssets().open("audio-focus-saver.mp4")) {
                Files.copy(input, videoFile.toPath());
            }
            Files.write(metadata.toPath(), item.json().toString().getBytes(StandardCharsets.UTF_8));
            require(fixture.hasComplete(item), "Missing cached media fixture");
            require(prefs.edit().putBoolean("saverOnline", false).commit(), "Cannot disable fixture network");
            synchronized (SaverCache.class) { singleton.set(null, fixture); }
            test.setInTouchMode(false);
            screen = test.startActivitySync(new Intent(context, ScreensaverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity saver = screen;
            VideoView video = saver.findViewById(R.id.saver_video);
            SettingsStructureProbe.await(test, () -> rendered.getBoolean(saver) && video.isPlaying()
                    && video.getCurrentPosition() > 100, "Cached video never rendered and advanced");
            int playingTicket = ticket.getInt(saver);
            cover = test.startActivitySync(new Intent(context, UpdatesActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity coveringActivity = cover;
            SettingsStructureProbe.await(test, () -> coveringActivity.hasWindowFocus() && !resumed.getBoolean(saver)
                    && !saver.isDestroyed(), "Opening another activity did not pause the saver");
            Handler timer = (Handler) field(ScreensaverActivity.class, "timer").get(saver);
            Runnable playbackTimeout = (Runnable) field(ScreensaverActivity.class, "videoTimeout").get(saver);
            Runnable advance = (Runnable) field(ScreensaverActivity.class, "advance").get(saver);
            SettingsStructureProbe.main(test, () -> {
                require(ticket.getInt(saver) > playingTicket && !rendered.getBoolean(saver)
                        && video.getVisibility() == View.GONE && !video.isPlaying() && pin.get(saver) == null,
                        "Pause left an active playback request");
                if (Build.VERSION.SDK_INT >= 29) {
                    require(!timer.hasCallbacks(playbackTimeout) && !timer.hasCallbacks(advance),
                            "Pause left a playback timer queued");
                }
            });
            synchronized (fixture) {
                require(((Map<?, ?>) pins.get(fixture)).isEmpty(), "Pause retained a cache pin");
            }
            require(fixture.hasComplete(item), "Pause discarded a valid video");
            int pausedTicket = ticket.getInt(saver);
            SettingsStructureProbe.main(test, coveringActivity::finish);
            SettingsStructureProbe.await(test, () -> saver.hasWindowFocus() && resumed.getBoolean(saver)
                    && ticket.getInt(saver) > pausedTicket && rendered.getBoolean(saver) && video.isPlaying()
                    && pin.get(saver) instanceof SaverMedia active && item.id.equals(active.id),
                    "The original saver did not render the same cached video on resume");
            int[] positions = new int[2];
            SettingsStructureProbe.main(test, () -> positions[0] = video.getCurrentPosition());
            SystemClock.sleep(400);
            SettingsStructureProbe.main(test, () -> {
                positions[1] = video.getCurrentPosition();
                TextView credit = saver.findViewById(R.id.saver_credit);
                require(credit.getText().toString().contains(item.credit), "Credit missing after resume");
            });
            require(positions[1] > positions[0], "Video did not advance after resume");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test, saver::isDestroyed, "Back did not dismiss the resumed saver");
            result.putString("savervideolifecycle",
                    "PASS: cached video rendered, paused without pins or timers, then resumed and dismissed with Back");
            result.putInt("savervideolifecycle_resumed_advance_ms", positions[1] - positions[0]);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                try {
                    finish(test, cover);
                } finally {
                    finish(test, screen);
                }
                if (screen != null) {
                    ExecutorService decoder = (ExecutorService) field(ScreensaverActivity.class, "decoder").get(screen);
                    require(decoder.awaitTermination(10, TimeUnit.SECONDS), "Saver decoder still active after Back");
                }
                synchronized (fixture) {
                    require(((Map<?, ?>) pins.get(fixture)).isEmpty(), "Fixture retained a cache pin after teardown");
                }
                synchronized (SaverCache.class) { singleton.set(null, previous); }
                SettingsStructureProbe.restore(prefs, saved);
                Files.deleteIfExists(videoFile.toPath());
                Files.deleteIfExists(metadata.toPath());
                Files.delete(root.toPath());
            } catch (Exception | Error cleanup) {
                if (primary == null) throw cleanup;
                primary.addSuppressed(cleanup);
            }
        }
    }

    private static void finish(Instrumentation test, Activity activity) throws Exception {
        if (activity == null) return;
        SettingsStructureProbe.main(test, () -> { if (!activity.isDestroyed()) activity.finish(); });
        SettingsStructureProbe.await(test, activity::isDestroyed, "Fixture activity did not finish");
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

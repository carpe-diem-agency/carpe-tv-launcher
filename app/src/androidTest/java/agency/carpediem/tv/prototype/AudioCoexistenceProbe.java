package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Uses two owned audio clients and a private video fixture on the disposable APK. */
final class AudioCoexistenceProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        run(test, result, null);
    }

    static void run(Instrumentation test, Bundle result, String witness) throws Exception {
        require(witness == null || witness.matches("[a-f0-9]{32}"), "Invalid external audio witness");
        boolean external = witness != null;
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()), "Disposable validation required");
        SharedPreferences prefs = Screensaver.prefs(context);
        SharedPreferences launcherPrefs = context.getSharedPreferences(MainActivity.class.getName(), Context.MODE_PRIVATE);
        SharedPreferences soundPrefs = UiSounds.preferences(context);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        Map<String, ?> savedLauncher = new HashMap<>(launcherPrefs.getAll());
        Map<String, ?> savedSounds = new HashMap<>(soundPrefs.getAll());
        Field singleton = field(SaverCache.class, "instance");
        SaverCache previous;
        synchronized (SaverCache.class) { previous = (SaverCache) singleton.get(null); }
        File root = new File(context.getCacheDir(), "saver-audio-" + UUID.randomUUID());
        require(root.mkdir(), "Create private saver cache");
        SaverCache fixture = new SaverCache(root, 1024 * 1024, 0, () -> Long.MAX_VALUE);
        AudioManager audio = context.getSystemService(AudioManager.class);
        AudioAttributes media = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        List<Integer> changes = new CopyOnWriteArrayList<>();
        AudioFocusRequest primary = focus(media, changes::add);
        AudioFocusRequest challenger = focus(media, event -> {});
        MainActivity launcher = null;
        ScreensaverActivity saver = null;
        AudioTrack track = null;
        Throwable primaryFailure = null;
        try {
            require(launcherPrefs.edit().putBoolean("welcome_seen", true).commit(), "Prepare isolated launcher");
            require(prefs.edit().putBoolean("saverOnline", false).commit(), "Disable network for the isolated saver");
            require(soundPrefs.edit().putBoolean(UiSounds.ENABLED, true).putInt(UiSounds.VOLUME, 1).commit(), "Prepare quiet interface sounds");
            seedVideo(test, fixture, root);
            synchronized (SaverCache.class) { singleton.set(null, fixture); }
            test.setInTouchMode(false);
            MainActivity screen = launcher = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            UiSounds sounds = UiSounds.get(context);
            SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && sounds.ready(), "Launcher and sounds unavailable");
            if (!external)
                require(audio.requestAudioFocus(primary) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED, "Own media focus not granted");
            phase(witness, "control_begin");
            require(audio.requestAudioFocus(challenger) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED, "Focus control not granted");
            if (external) SystemClock.sleep(650);
            else SettingsStructureProbe.await(test, () -> changes.contains(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT),
                        "Focus observer did not detect the competing test request");
            phase(witness, "control_release");
            audio.abandonAudioFocusRequest(challenger);
            if (external) SystemClock.sleep(650);
            else SettingsStructureProbe.await(test, () -> changes.contains(AudioManager.AUDIOFOCUS_GAIN), "Focus observer did not detect restoration");
            int eventCount = changes.size();

            int initialHead = 0;
            if (!external) {
                short[] silence = new short[16000];
                track = new AudioTrack.Builder().setAudioAttributes(media)
                        .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                        .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(silence.length * 2).build();
                require(track.write(silence, 0, silence.length) == silence.length, "Silent media buffer unavailable");
                require(track.setLoopPoints(0, silence.length, -1) == AudioTrack.SUCCESS, "Silent media could not loop");
                track.setVolume(0f);
                track.play();
                AudioTrack playback = track;
                SettingsStructureProbe.await(test, () -> playback.getPlaybackHeadPosition() > 0, "Silent media did not start");
                initialHead = track.getPlaybackHeadPosition();
            }

            phase(witness, "sounds_begin");
            SettingsStructureProbe.main(test, () -> screen.findViewById(R.id.nav_home).requestFocus());
            UiSounds.Stats before = sounds.stats();
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.await(test, () -> sounds.stats().focusPlays > before.focusPlays, "Focus sound did not start");
            SettingsStructureProbe.main(test, () -> require(screen.findViewById(R.id.nav_apps).performClick(), "Apps click unavailable"));
            SettingsStructureProbe.await(test, () -> sounds.stats().clickPlays > before.clickPlays, "Click sound did not start");
            require(changes.size() == eventCount, "Interface sounds interrupted owned media focus");
            if (!external) require(track.getPlaybackHeadPosition() > initialHead, "Silent media did not advance through interface sounds");
            if (external) SystemClock.sleep(650);
            phase(witness, "sounds_end");

            phase(witness, "video_begin");
            ScreensaverActivity active = saver = (ScreensaverActivity) test.startActivitySync(
                    new Intent(context, ScreensaverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            VideoView video = active.findViewById(R.id.saver_video);
            SettingsStructureProbe.await(test, () -> video.isPlaying()
                    && ((TextView) active.findViewById(R.id.saver_credit)).getText().toString().contains("Focus fixture"),
                    "The actual cached video did not render");
            int beforeVideoHead = external ? 0 : track.getPlaybackHeadPosition();
            int[] videoPosition = new int[1];
            SettingsStructureProbe.main(test, () -> videoPosition[0] = video.getCurrentPosition());
            SystemClock.sleep(650);
            SettingsStructureProbe.main(test, () -> require(video.getCurrentPosition() > videoPosition[0], "Cached video did not advance"));
            require(changes.size() == eventCount, "Cached saver video interrupted owned media focus");
            if (external) {
                phase(witness, "video_end");
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                SettingsStructureProbe.await(test, () -> active.isDestroyed() && screen.hasWindowFocus(),
                        "Back did not finish the saver and return to Carpe TV");
                SystemClock.sleep(650);
                phase(witness, "back_end");
                result.putString("audioexternal", "PASS: real focus/click sounds, advancing cached video, and Back");
            } else {
                require(track.getPlaybackHeadPosition() > beforeVideoHead, "Silent media did not advance alongside the video");
                result.putString("audio_coexistence",
                        "PASS: competing control generated loss/gain; silent media advanced through focus/click sounds and actual cached video without another focus event");
            }
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanup = clean(null, () -> audio.abandonAudioFocusRequest(challenger));
            cleanup = clean(cleanup, () -> audio.abandonAudioFocusRequest(primary));
            if (track != null) cleanup = clean(cleanup, track::release);
            if (saver != null) {
                ScreensaverActivity active = saver;
                cleanup = clean(cleanup, () -> {
                    SettingsStructureProbe.main(test, active::finish);
                    SettingsStructureProbe.await(test, active::isDestroyed, "Saver did not finish");
                    require(((ExecutorService) field(ScreensaverActivity.class, "decoder").get(active)).awaitTermination(15, TimeUnit.SECONDS),
                            "Saver decoder did not stop");
                });
            }
            if (launcher != null) {
                MainActivity owned = launcher;
                cleanup = clean(cleanup, () -> {
                    SettingsStructureProbe.finish(test, owned);
                    SettingsStructureProbe.quiescent(test, owned);
                });
            }
            cleanup = clean(cleanup, () -> { synchronized (SaverCache.class) { singleton.set(null, previous); } });
            cleanup = clean(cleanup, () -> SettingsStructureProbe.restore(prefs, saved));
            cleanup = clean(cleanup, () -> SettingsStructureProbe.restore(launcherPrefs, savedLauncher));
            cleanup = clean(cleanup, () -> SettingsStructureProbe.restore(soundPrefs, savedSounds));
            cleanup = clean(cleanup, () -> {
                File[] entries = root.listFiles();
                require(entries != null, "Private cache inaccessible");
                for (File entry : entries) require(entry.isFile() && entry.delete(), "Private cache entry cleanup failed");
                require(root.delete(), "Private cache cleanup failed");
            });
            if (cleanup != null) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanup);
                else if (cleanup instanceof Error) throw (Error) cleanup;
                else throw (Exception) cleanup;
            }
        }
    }

    private static Throwable clean(Throwable previous, SettingsStructureProbe.Action operation) {
        try {
            operation.run();
        } catch (Exception | Error failure) {
            if (previous == null) return failure;
            previous.addSuppressed(failure);
        }
        return previous;
    }

    private static void phase(String witness, String name) {
        if (witness != null) android.util.Log.i("CarpeAudioProbe",
                witness + " phase " + android.os.Process.myUid() + " " + name);
    }

    private static AudioFocusRequest focus(AudioAttributes media, AudioManager.OnAudioFocusChangeListener listener) {
        return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(media).setOnAudioFocusChangeListener(listener).build();
    }

    private static void seedVideo(Instrumentation test, SaverCache fixture, File root) throws Exception {
        SaverMedia media = new SaverMedia("https://images-assets.nasa.gov/focus-probe.mp4", "Focus fixture",
                "Focus fixture", "https://example.invalid/source", "Test fixture", "video");
        try (InputStream input = test.getContext().getAssets().open("audio-focus-saver.mp4");
             FileOutputStream output = new FileOutputStream(fixture.file(media))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        try (FileOutputStream output = new FileOutputStream(new File(root, media.id + ".json"))) {
            output.write(media.json().toString().getBytes(StandardCharsets.UTF_8));
        }
        require(fixture.hasComplete(media), "Cached video fixture unavailable");
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

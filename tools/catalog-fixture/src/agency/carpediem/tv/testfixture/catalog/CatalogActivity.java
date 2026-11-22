package agency.carpediem.tv.testfixture.catalog;

/** A catalog fixture; optional silent, token-scoped audio-focus witness for isolated QA. */
public final class CatalogActivity extends android.app.Activity {
    private final android.os.Handler telemetry = new android.os.Handler(android.os.Looper.getMainLooper());
    private android.media.AudioTrack track;
    private android.media.AudioManager audio;
    private android.media.AudioFocusRequest focusRequest;
    private String token;
    private int losses, gains, samples;
    private final android.media.AudioManager.OnAudioFocusChangeListener focusListener = new android.media.AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) {
            if (change > 0) gains++;
            else if (change < 0) losses++;
        }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (track == null || token == null) return;
            android.util.Log.i("CarpeAudioProbe", token + " player " + android.os.Process.myUid()
                    + " " + Integer.toUnsignedLong(track.getPlaybackHeadPosition()) + " " + losses + " " + gains);
            if (++samples < 300) telemetry.postDelayed(this, 200);
            else releaseAudio(); // Hard one-minute bound even if host cleanup fails.
        }
    };

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("Carpe QA Калина");
        label.setTextColor(android.graphics.Color.WHITE);
        label.setBackgroundColor(android.graphics.Color.BLACK);
        label.setGravity(android.view.Gravity.CENTER);
        setContentView(label);
        String requested = getIntent().getStringExtra("carpe_audio_witness");
        if (requested != null && requested.matches("[a-f0-9]{32}")) startAudio(requested);
    }

    private void startAudio(String requested) {
        token = requested;
        audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        android.media.AudioAttributes attributes = new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build();
        try {
            focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener, telemetry).build();
            if (audio.requestAudioFocus(focusRequest) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new IllegalStateException("Witness audio focus unavailable");
            int bytes = 48000 * 2; // One second of signed PCM16 mono zeros.
            track = new android.media.AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(new android.media.AudioFormat.Builder().setSampleRate(48000)
                            .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(android.media.AudioTrack.MODE_STATIC).setBufferSizeInBytes(bytes).build();
            if (track.write(new short[48000], 0, 48000) != 48000
                    || track.getState() != android.media.AudioTrack.STATE_INITIALIZED
                    || track.setLoopPoints(0, 48000, -1) != android.media.AudioTrack.SUCCESS)
                throw new IllegalStateException("Witness track unavailable");
            track.play();
            telemetry.post(tick);
        } catch (RuntimeException unavailable) {
            android.util.Log.w("CarpeAudioProbe", requested + " error " + unavailable.getClass().getSimpleName());
            releaseAudio();
        }
    }

    private void releaseAudio() {
        telemetry.removeCallbacks(tick);
        if (track != null) {
            track.release();
            track = null;
        }
        if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        focusRequest = null;
        token = null;
    }

    @Override protected void onDestroy() {
        releaseAudio();
        super.onDestroy();
    }
}

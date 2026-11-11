package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** A phone browser enters the one-time code; later restarts work with the TV remote. */
public final class SystemSetupActivity extends Activity {
    private static final String TAG = "CarpeSystemSetup";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger pairingPort = new AtomicInteger();
    private LocalAdbDiscovery discovery;
    private LocalPairingServer server;
    private TextView status, address;
    private ImageView qr;
    private Button openSettings, startButton, grantButton;
    private Typeface font;
    private boolean busy, destroyed;

    @Override protected void attachBaseContext(Context base) {
        base = AppLanguage.wrap(base);
        android.content.res.Configuration cfg = new android.content.res.Configuration(base.getResources().getConfiguration());
        int width = base.getResources().getDisplayMetrics().widthPixels;
        if (width > 0) cfg.densityDpi = Math.max(120, Math.min(640, Math.round(width / 6f)));
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try { font = new Typeface.Builder(getAssets(), "montserrat.ttf").setFontVariationSettings("'wght' 600").build(); }
        catch (RuntimeException error) { font = Typeface.DEFAULT; }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff101b2c);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(56), dp(24), dp(56), dp(24));
        scroll.addView(panel);
        setContentView(scroll);
        panel.addView(label(getString(R.string.setup_title), 27, Color.WHITE));
        TextView explanation = label(getString(R.string.setup_what_is_shizuku), 15, 0xffdce8fa);
        explanation.setPadding(dp(18), dp(14), dp(18), dp(14));
        explanation.setBackgroundResource(R.drawable.control_background);
        LinearLayout.LayoutParams explanationSize = new LinearLayout.LayoutParams(-1, -2);
        explanationSize.topMargin = dp(12);
        panel.addView(explanation, explanationSize);
        panel.addView(label(getString(R.string.setup_phone_intro), 15, 0xffbdcce2));
        status = label("", 15, 0xffa8d6ff);
        status.setPadding(0, dp(8), 0, dp(8));
        panel.addView(status);
        Button first = button(getString(R.string.setup_pair), this::beginPairing);
        panel.addView(first, buttonSize());
        qr = new ImageView(this);
        qr.setVisibility(View.GONE);
        LinearLayout.LayoutParams qrSize = new LinearLayout.LayoutParams(dp(190), dp(190));
        qrSize.topMargin = dp(12);
        panel.addView(qr, qrSize);
        address = label("", 14, Color.WHITE);
        address.setVisibility(View.GONE);
        panel.addView(address);
        TextView codeExample = label(getString(R.string.setup_code_example), 14, 0xffc4d2e8);
        codeExample.setPadding(dp(18), dp(12), dp(18), dp(12));
        codeExample.setBackgroundResource(R.drawable.control_background);
        LinearLayout.LayoutParams exampleSize = new LinearLayout.LayoutParams(-1, -2);
        exampleSize.topMargin = dp(12);
        panel.addView(codeExample, exampleSize);
        openSettings = button(getString(R.string.setup_open_settings), this::openDeveloperSettings);
        panel.addView(openSettings, buttonSize());
        startButton = button(getString(R.string.setup_start), this::startServiceOnDevice);
        panel.addView(startButton, buttonSize());
        grantButton = button(getString(R.string.shizuku_grant), () -> {
            try { SystemAccess.request(this); }
            catch (RuntimeException error) { status.setText(R.string.system_access_failed); }
        });
        panel.addView(grantButton, buttonSize());
        panel.addView(button(getString(R.string.close), this::finish), buttonSize());
        first.requestFocus();
        refreshStatus();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextSize(size); text.setTextColor(color); text.setTypeface(font);
        return text;
    }
    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(Color.WHITE); button.setTypeface(font);
        button.setBackgroundResource(R.drawable.nav_background);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private LinearLayout.LayoutParams buttonSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(48));
        size.topMargin = dp(10);
        return size;
    }
    private void refreshStatus() {
        if (busy) return;
        boolean paired = getSharedPreferences("system_setup", 0).getBoolean("paired", false);
        openSettings.setEnabled(server != null);
        startButton.setEnabled(paired && !SystemAccess.ready());
        startButton.setBackgroundResource(SystemAccess.ready() ? R.drawable.system_access_on : R.drawable.system_access_off);
        grantButton.setEnabled(SystemAccess.running() && !SystemAccess.ready());
        status.setText(SystemAccess.ready() ? R.string.system_access_ready
                : SystemAccess.running() ? R.string.system_access_permission
                : paired
                        ? R.string.setup_paired : R.string.shizuku_stopped_short);
    }
    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private void beginPairing() {
        if (busy) return;
        if (Build.VERSION.SDK_INT < 30) { status.setText(R.string.setup_android11); return; }
        stopSession();
        try {
            server = new LocalPairingServer(code -> {
                int port = pairingPort.get();
                if (port == 0) return false;
                boolean paired = false;
                try (LocalAdbAccess adb = new LocalAdbAccess(this)) { paired = adb.pairLocal(port, code); }
                catch (Exception | LinkageError ignored) { }
                if (paired) {
                    getSharedPreferences("system_setup", 0).edit().putBoolean("paired", true).apply();
                    main.post(() -> {
                        if (destroyed) return;
                        busy = false;
                        status.setText(R.string.setup_paired);
                        stopDiscovery();
                        returnToSetup();
                    });
                }
                return paired;
            });
            showQr(server.address());
        } catch (IOException | com.google.zxing.WriterException error) {
            Log.w(TAG, "Local pairing page unavailable: " + error.getClass().getSimpleName());
            stopSession(); status.setText(R.string.setup_phone_unavailable); return;
        }
        busy = true;
        status.setText(R.string.setup_scan_first);
        openSettings.setEnabled(true);
        discovery = new LocalAdbDiscovery(this);
        discovery.start(true, new LocalAdbDiscovery.Result() {
            public void found(int port) { pairingPort.set(port); }
            public void failed() {
                if (!destroyed) { busy = false; status.setText(R.string.setup_no_code); stopSession(); }
            }
        });
    }
    private void showQr(String url) throws com.google.zxing.WriterException {
        BitMatrix bits = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300);
        Bitmap image = Bitmap.createBitmap(bits.getWidth(), bits.getHeight(), Bitmap.Config.ARGB_8888);
        for (int y = 0; y < bits.getHeight(); y++)
            for (int x = 0; x < bits.getWidth(); x++) image.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE);
        qr.setImageBitmap(image); qr.setVisibility(View.VISIBLE);
        address.setText(url); address.setVisibility(View.VISIBLE);
    }
    private void openDeveloperSettings() {
        if (server == null) { status.setText(R.string.setup_phone_unavailable); return; }
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
        catch (RuntimeException error) { status.setText(R.string.settings_unavailable); }
    }
    private void startServiceOnDevice() {
        if (busy) return;
        if (SystemAccess.running()) { refreshStatus(); return; }
        if (!getSharedPreferences("system_setup", 0).getBoolean("paired", false)) {
            status.setText(R.string.setup_pair_first); return;
        }
        busy = true; status.setText(R.string.setup_searching); stopDiscovery();
        discovery = new LocalAdbDiscovery(this);
        discovery.start(false, new LocalAdbDiscovery.Result() {
            public void failed() { busy = false; if (!destroyed) status.setText(R.string.setup_no_connection); }
            public void found(int port) { worker.execute(() -> {
                boolean started = false;
                try (LocalAdbAccess adb = new LocalAdbAccess(SystemSetupActivity.this)) {
                    adb.startShizuku(SystemSetupActivity.this, port); started = true;
                } catch (Exception | LinkageError ignored) { }
                final boolean result = started;
                main.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    status.setText(result ? R.string.setup_started : R.string.setup_start_failed);
                    if (result) main.postDelayed(SystemSetupActivity.this::refreshStatus, 1500);
                });
            }); }
        });
    }
    private void returnToSetup() {
        try { startActivity(new Intent(this, SystemSetupActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
        catch (RuntimeException ignored) { }
    }
    private void stopDiscovery() { if (discovery != null) { discovery.stop(); discovery = null; } }
    private void stopSession() {
        stopDiscovery();
        if (server != null) { server.close(); server = null; }
        pairingPort.set(0);
    }
    @Override protected void onDestroy() {
        destroyed = true; stopSession(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy();
    }
}

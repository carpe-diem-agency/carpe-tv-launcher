package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private final TextView[] stepNumbers = new TextView[3];
    private final TextView[] stepTitles = new TextView[3];
    private final View[] introduction = new View[4];
    private ImageView qr;
    private LinearLayout pairingGuide;
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
        TextView whatIsShizuku = label(getString(R.string.setup_shizuku_heading), 17, Color.WHITE);
        LinearLayout.LayoutParams headingSize = new LinearLayout.LayoutParams(-1, -2);
        headingSize.topMargin = dp(18);
        panel.addView(whatIsShizuku, headingSize);
        introduction[0] = whatIsShizuku;
        TextView explanation = label(getString(R.string.setup_what_is_shizuku), 15, 0xffdce8fa);
        explanation.setPadding(dp(18), dp(14), dp(18), dp(14));
        explanation.setBackgroundResource(R.drawable.control_background);
        LinearLayout.LayoutParams explanationSize = new LinearLayout.LayoutParams(-1, -2);
        explanationSize.topMargin = dp(12);
        panel.addView(explanation, explanationSize);
        introduction[1] = explanation;
        TextView intro = label(getString(R.string.setup_phone_intro), 15, 0xffbdcce2);
        intro.setPadding(0, dp(15), 0, dp(8));
        panel.addView(intro);
        introduction[2] = intro;
        introduction[3] = addStepRow(panel);
        status = label("", 15, 0xffa8d6ff);
        status.setPadding(0, dp(8), 0, dp(8));
        panel.addView(status);
        Button first = button(getString(R.string.setup_pair), this::beginPairing);
        panel.addView(first, buttonSize());
        pairingGuide = new LinearLayout(this);
        pairingGuide.setOrientation(LinearLayout.HORIZONTAL);
        pairingGuide.setPadding(dp(18), dp(14), dp(18), dp(14));
        pairingGuide.setBackgroundResource(R.drawable.control_background);
        pairingGuide.setVisibility(View.GONE);
        LinearLayout.LayoutParams guideSize = new LinearLayout.LayoutParams(-1, -2);
        guideSize.topMargin = dp(12);
        panel.addView(pairingGuide, guideSize);
        LinearLayout qrColumn = new LinearLayout(this);
        qrColumn.setOrientation(LinearLayout.VERTICAL);
        pairingGuide.addView(qrColumn, new LinearLayout.LayoutParams(dp(212), -2));
        qr = new ImageView(this);
        qrColumn.addView(qr, new LinearLayout.LayoutParams(dp(190), dp(190)));
        address = label("", 11, 0xffaebdd3);
        address.setMaxLines(2);
        qrColumn.addView(address);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(0, -2, 1f);
        actionSize.setMarginStart(dp(20));
        pairingGuide.addView(actions, actionSize);
        addGuideStep(actions, 1, R.string.setup_guide_scan);
        addGuideStep(actions, 2, R.string.setup_guide_settings);
        openSettings = button(getString(R.string.setup_open_settings), this::openDeveloperSettings);
        LinearLayout.LayoutParams settingsSize = buttonSize();
        settingsSize.setMarginStart(dp(40));
        actions.addView(openSettings, settingsSize);
        addGuideStep(actions, 3, R.string.setup_guide_enter);
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
    private View addStepRow(LinearLayout panel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, -2);
        rowSize.topMargin = dp(8);
        rowSize.bottomMargin = dp(8);
        panel.addView(row, rowSize);
        int[] titles = {R.string.setup_step_phone, R.string.setup_step_code, R.string.setup_step_ready};
        for (int i = 0; i < titles.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) cell.setMarginStart(dp(8));
            row.addView(item, cell);
            TextView number = label(String.valueOf(i + 1), 17, Color.WHITE);
            number.setGravity(android.view.Gravity.CENTER);
            item.addView(number, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView title = label(getString(titles[i]), 13, 0xffcbd8ec);
            title.setPadding(0, dp(6), 0, 0);
            item.addView(title);
            stepNumbers[i] = number;
            stepTitles[i] = title;
        }
        setStep(0);
        return row;
    }
    private void addGuideStep(LinearLayout parent, int number, int labelId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, -2);
        rowSize.bottomMargin = dp(10);
        parent.addView(row, rowSize);
        TextView badge = label(String.valueOf(number), 14, Color.WHITE);
        badge.setGravity(android.view.Gravity.CENTER);
        GradientDrawable disk = new GradientDrawable();
        disk.setShape(GradientDrawable.OVAL);
        disk.setColor(0xff315fa8);
        badge.setBackground(disk);
        row.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView title = label(getString(labelId), 14, Color.WHITE);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0, -2, 1f);
        titleSize.setMarginStart(dp(10));
        row.addView(title, titleSize);
    }
    private void setStep(int current) {
        for (int i = 0; i < stepNumbers.length; i++) {
            TextView number = stepNumbers[i];
            TextView title = stepTitles[i];
            if (number == null || title == null) continue;
            boolean active = i == current;
            boolean complete = i < current;
            GradientDrawable disk = new GradientDrawable();
            disk.setShape(GradientDrawable.OVAL);
            disk.setColor(active ? 0xff3f83e9 : complete ? 0xff2f6659 : 0xff26364f);
            number.setBackground(disk);
            number.setTextColor(active || complete ? Color.WHITE : 0xffaebdd3);
            title.setTextColor(active ? Color.WHITE : complete ? 0xffb9e5cc : 0xffaebdd3);
        }
    }
    private LinearLayout.LayoutParams buttonSize() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(48));
        size.topMargin = dp(10);
        return size;
    }
    private void refreshStatus() {
        if (busy) return;
        boolean paired = getSharedPreferences("system_setup", 0).getBoolean("paired", false);
        boolean ready = SystemAccess.ready();
        boolean running = SystemAccess.running();
        boolean pairing = server != null && !paired;
        for (View view : introduction) view.setVisibility(pairing || paired ? View.GONE : View.VISIBLE);
        setStep(paired ? 2 : pairing ? 1 : 0);
        pairingGuide.setVisibility(pairing ? View.VISIBLE : View.GONE);
        startButton.setVisibility(paired && !running ? View.VISIBLE : View.GONE);
        grantButton.setVisibility(running && !ready ? View.VISIBLE : View.GONE);
        startButton.setEnabled(paired && !ready);
        startButton.setBackgroundResource(SystemAccess.ready() ? R.drawable.system_access_on : R.drawable.system_access_off);
        grantButton.setEnabled(running && !ready);
        status.setText(ready ? R.string.system_access_ready
                : running ? R.string.system_access_permission
                : pairing ? R.string.setup_scan_first
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
                        refreshStatus();
                        returnToSetup();
                    });
                }
                return paired;
            }, getResources().getConfiguration().getLocales().get(0));
            showQr(server.address());
        } catch (IOException | com.google.zxing.WriterException error) {
            Log.w(TAG, "Local pairing page unavailable: " + error.getClass().getSimpleName());
            stopSession(); status.setText(R.string.setup_phone_unavailable); return;
        }
        busy = true;
        for (View view : introduction) view.setVisibility(View.GONE);
        setStep(1);
        status.setText(R.string.setup_scan_first);
        pairingGuide.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.GONE);
        grantButton.setVisibility(View.GONE);
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
        qr.setImageBitmap(image);
        address.setText(url);
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

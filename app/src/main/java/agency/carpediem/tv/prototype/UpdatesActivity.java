package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;

/** One native TV panel: background download is owned by Android, never this Activity. */
public final class UpdatesActivity extends Activity {
    private TextView status, details, version, hint;
    private ImageView resultIcon;
    private Button check, download, install, cancel, automatic;
    private ProgressBar progress;
    private String feed;
    private Typeface font;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { UpdateManager.refresh(UpdatesActivity.this); render(); handler.postDelayed(this,1000); }
    };
    @Override protected void attachBaseContext(Context base) {
        base = AppLanguage.wrap(base);
        android.content.res.Configuration cfg = new android.content.res.Configuration(base.getResources().getConfiguration());
        int width = base.getResources().getDisplayMetrics().widthPixels;
        if (width > 0) {
            cfg.densityDpi = Math.max(120,Math.min(640,Math.round(width/6f)));
            cfg.screenWidthDp = Math.round(width*160f/cfg.densityDpi);
            cfg.screenHeightDp = Math.round(base.getResources().getDisplayMetrics().heightPixels*160f/cfg.densityDpi);
            cfg.smallestScreenWidthDp = Math.min(cfg.screenWidthDp,cfg.screenHeightDp);
        }
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try { font = new Typeface.Builder(getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build(); }
        catch (RuntimeException e) { font = Typeface.DEFAULT; }
        feed = UpdateManager.source();
        if (UpdateManager.debug(this) && getIntent().hasExtra("test_feed")) feed = getIntent().getStringExtra("test_feed");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(52),dp(28),dp(52),dp(24)); root.setBackgroundResource(R.drawable.blue_wallpaper);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setFocusable(false); scroll.addView(root); setContentView(scroll);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button(getString(R.string.back), 0, () -> finish()); top.addView(back,new LinearLayout.LayoutParams(dp(140),dp(48)));
        TextView title = text(getString(R.string.updates),30); LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0,-2,1); titleSize.setMarginStart(dp(24)); top.addView(title,titleSize); root.addView(top);
        version = text(getString(R.string.installed_prefix) + UpdateManager.appVersion(this),16); version.setTextColor(0xffb9c7dc); version.setPadding(0,dp(24),0,dp(20)); root.addView(version);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundResource(R.drawable.control_background); panel.setPadding(dp(24),dp(18),dp(24),dp(18)); panel.setMinimumHeight(dp(130));
        LinearLayout stateRow = new LinearLayout(this); stateRow.setGravity(Gravity.CENTER_VERTICAL);
        resultIcon = new ImageView(this); resultIcon.setImageResource(R.drawable.ic_update_ok);
        resultIcon.setContentDescription(getString(R.string.update_current));
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(36),dp(36)); iconSize.setMarginEnd(dp(14));
        stateRow.addView(resultIcon,iconSize);
        status = text("",24); status.setId(R.id.update_status); status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stateRow.addView(status,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(stateRow);
        details = text("",15); details.setMaxLines(3); details.setEllipsize(android.text.TextUtils.TruncateAt.END); details.setTextColor(0xffc5d1e3); details.setPadding(0,dp(12),0,dp(12)); panel.addView(details);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setId(R.id.update_progress); progress.setMax(100); panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(8))); root.addView(panel);
        LinearLayout actions = new LinearLayout(this); actions.setPadding(0,dp(20),0,dp(16));
        check = button(getString(R.string.check_updates), R.id.update_check, () -> UpdateManager.check(this,feed));
        download = button(getString(R.string.download),R.id.update_download, () -> UpdateManager.download(this));
        install = button(getString(R.string.install),R.id.update_install, () -> UpdateManager.install(this));
        cancel = button(getString(R.string.cancel),R.id.update_cancel, () -> UpdateManager.cancel(this));
        for (Button b : new Button[]{check,download,install,cancel}) {
            int width = b == install ? 320 : b == check ? 220 : 260;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(width),dp(76));
            p.setMarginEnd(dp(12));
            actions.addView(b,p);
        }
        root.addView(actions);
        automatic = button("",R.id.update_automatic, () -> {
            boolean on = !UpdateManager.prefs(this).getBoolean("autoDownload",false);
            UpdateManager.prefs(this).edit().putBoolean("autoDownload",on).apply(); render();
            if (on && "available".equals(UpdateManager.state(this))) UpdateManager.download(this);
        }); root.addView(automatic,new LinearLayout.LayoutParams(dp(520),dp(48)));
        hint = text(getString(R.string.update_help),13); hint.setTextColor(0xffaabbd4); hint.setPadding(0,dp(16),0,0); root.addView(hint);
        check.requestFocus(); render();
    }
    private Button button(String title,int id,Runnable action) {
        Button b = new Button(this); if (id != 0) b.setId(id); b.setText(title); b.setAllCaps(false); b.setTypeface(font); b.setTextSize(15); b.setTextColor(Color.WHITE); b.setGravity(Gravity.CENTER); b.setPadding(dp(12),dp(8),dp(12),dp(8)); b.setSingleLine(true); b.setBackgroundResource(R.drawable.control_background); b.setOnClickListener(v -> { try { action.run(); } catch (RuntimeException e) { Toast.makeText(this,getString(R.string.action_unavailable),Toast.LENGTH_LONG).show(); } }); return b;
    }
    private TextView text(String s,int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setTypeface(font); return t; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void visible(View view,boolean show) { view.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void render() {
        if (status == null) return;
        String state = UpdateManager.state(this);
        status.setText(UpdateManager.message(this,feed.isEmpty()));
        String messageKey = UpdateManager.prefs(this).getString("messageKey", "");
        visible(resultIcon, state.equals("idle") && (messageKey.equals("update_current")
                || messageKey.equals("update_success") || messageKey.equals("update_success_prefix")));
        String info = ""; long total=0;
        try { JSONObject m=UpdateManager.metadata(this); total=m.optLong("byteSize"); if(m.has("versionName")) info=getString(R.string.version_prefix)+m.getString("versionName")+" · "+String.format(java.util.Locale.ROOT,getString(R.string.megabytes),total/1048576.0)+"\n"+m.optString("releaseNotes"); }
        catch(Exception ignored) { }
        if (!info.isEmpty() && UpdateManager.debug(this) && !feed.isEmpty() && feed.startsWith("http:")) info = getString(R.string.private_test_prefix)+info;
        details.setText(info); visible(details,!info.isEmpty());
        visible(progress,state.equals("downloading") || state.equals("checking") || state.equals("preparing") || state.equals("verifying") || state.equals("installing"));
        progress.setIndeterminate(!state.equals("downloading"));
        if (state.equals("downloading") && total>0) progress.setProgress((int)Math.min(100,UpdateManager.prefs(this).getLong("downloaded",0)*100/total));
        check.setEnabled(!UpdateManager.busy(this) && !state.equals("ready"));
        visible(download,state.equals("available")); visible(install,state.equals("ready")); visible(cancel,state.equals("downloading"));
        install.setText(getPackageManager().canRequestPackageInstalls() ? getString(R.string.install) : getString(R.string.allow_install));
        hint.setText(getPackageManager().canRequestPackageInstalls()
                ? getString(R.string.update_help)
                : getString(R.string.permission_help));
        automatic.setText(getString(R.string.auto_download_prefix) + (UpdateManager.prefs(this).getBoolean("autoDownload",false) ? getString(R.string.on) : getString(R.string.off)));
        View focus = getCurrentFocus();
        if (focus == null || !focus.isShown() || !focus.isEnabled()) {
            if (state.equals("ready")) install.requestFocus(); else if(state.equals("available")) download.requestFocus(); else if(state.equals("downloading")) cancel.requestFocus(); else if(check.isEnabled()) check.requestFocus();
        }
    }
    @Override protected void onResume() { super.onResume(); UpdateManager.recover(this); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
}

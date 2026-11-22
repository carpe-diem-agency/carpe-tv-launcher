package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;

/** One native TV panel: background download is owned by Android, never this Activity. */
public final class UpdatesActivity extends Activity {
    private TextView status, details, version, hint;
    private ImageView resultIcon;
    private Button back, check, download, install, cancel, automatic;
    private ProgressBar progress;
    private boolean initialFocusPending;
    private boolean awaitingUpdateActionFocus;
    private View updateFocusFallback;
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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(52),dp(28),dp(52),dp(24));
        root.setBackground(new LayerDrawable(new Drawable[]{
                getDrawable(R.drawable.blue_wallpaper), new ColorDrawable(0x59000000)
        }));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setFocusable(false); scroll.addView(root); setContentView(scroll);
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        back = button(getString(R.string.back), R.id.update_back, 56, this::finish);
        top.addView(back,new LinearLayout.LayoutParams(dp(152),-2));
        TextView title = text(getString(R.string.updates),30); LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(0,-2,1); titleSize.setMarginStart(dp(24)); top.addView(title,titleSize); root.addView(top);
        version = text(getString(R.string.installed_prefix) + UpdateManager.appVersion(this),16); version.setTextColor(getColor(R.color.cd_text_secondary)); version.setPadding(0,dp(24),0,dp(20)); root.addView(version);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundResource(R.drawable.control_background); panel.setPadding(dp(24),dp(18),dp(24),dp(18)); panel.setMinimumHeight(dp(130));
        LinearLayout stateRow = new LinearLayout(this); stateRow.setGravity(Gravity.CENTER_VERTICAL);
        resultIcon = new ImageView(this); resultIcon.setImageResource(R.drawable.ic_update_ok);
        resultIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(36),dp(36)); iconSize.setMarginEnd(dp(14));
        stateRow.addView(resultIcon,iconSize);
        status = text("",24); status.setId(R.id.update_status); status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        stateRow.addView(status,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(stateRow);
        details = text("",18); details.setMaxLines(3); details.setEllipsize(android.text.TextUtils.TruncateAt.END); details.setTextColor(0xffc5d1e3); details.setPadding(0,dp(12),0,dp(12)); panel.addView(details);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setId(R.id.update_progress); progress.setMax(100); panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(8))); root.addView(panel);
        int controlsWidth = Math.min(dp(520), dp(getResources().getConfiguration().screenWidthDp - 104));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0,dp(20),0,0);
        check = button(getString(R.string.check_updates), R.id.update_check, 72, () -> UpdateManager.check(this,feed));
        download = button(getString(R.string.download),R.id.update_download, 72, () -> UpdateManager.download(this));
        install = button(getString(R.string.install),R.id.update_install, 72, () -> UpdateManager.install(this));
        install.setTextSize(21);
        cancel = button(getString(R.string.cancel),R.id.update_cancel, 72, () -> UpdateManager.cancel(this));
        for (Button b : new Button[]{check,download,install,cancel}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1);
            if (b != check && b != install) p.setMarginStart(dp(12));
            b.setNextFocusUpId(R.id.update_back);
            b.setNextFocusDownId(R.id.update_automatic);
            actions.addView(b,p);
        }
        root.addView(actions,new LinearLayout.LayoutParams(controlsWidth,-2));
        hint = text("",18);
        hint.setPadding(0,dp(12),0,dp(16));
        root.addView(hint);
        automatic = button("",R.id.update_automatic, 56, () -> {
            boolean on = !UpdateManager.prefs(this).getBoolean("autoDownload",false);
            UpdateManager.prefs(this).edit().putBoolean("autoDownload",on).apply(); render();
            if (on && "available".equals(UpdateManager.state(this))) UpdateManager.download(this);
        }); root.addView(automatic,new LinearLayout.LayoutParams(controlsWidth,-2));
        initialFocusPending = state == null;
        render();
    }
    static Button createUpdateButton(Context context,Typeface font,String title,int minimumDp) {
        float density=context.getResources().getDisplayMetrics().density;
        int horizontal=Math.round(12*density),vertical=Math.round(6*density),minimum=Math.round(minimumDp*density);
        Button b=new Button(context);b.setText(title);b.setAllCaps(false);b.setTypeface(font);b.setTextSize(18);
        b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setPadding(horizontal,vertical,horizontal,vertical);
        b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);b.setMinHeight(minimum);b.setMinimumHeight(minimum);
        b.setBackgroundResource(R.drawable.control_background);return b;
    }
    private Button button(String title,int id,int minimumDp,Runnable action) {
        Button b=createUpdateButton(this,font,title,minimumDp);if(id!=0)b.setId(id);
        UiSounds.click(b, v -> { try { action.run(); } catch (RuntimeException e) { Toast.makeText(this,getString(R.string.action_unavailable),Toast.LENGTH_LONG).show(); } });
        return b;
    }
    private TextView text(String s,int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.WHITE); t.setTypeface(font); return t; }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private void visible(View view,boolean show) { view.setVisibility(show ? View.VISIBLE : View.GONE); }
    private void updateText(TextView target, CharSequence text) {
        if (!TextUtils.equals(target.getText(), text)) target.setText(text);
    }
    private void render() {
        if (status == null) return;
        View previousFocus = getCurrentFocus();
        boolean continuingAction = awaitingUpdateActionFocus && previousFocus == updateFocusFallback;
        String state = UpdateManager.state(this);
        String message = UpdateManager.message(this, feed.isEmpty());
        updateText(status, message);
        String messageKey = UpdateManager.prefs(this).getString("messageKey", "");
        visible(resultIcon, state.equals("idle") && (messageKey.equals("update_current")
                || messageKey.equals("update_success") || messageKey.equals("update_success_prefix")));
        String info = ""; long total=0;
        try { JSONObject m=UpdateManager.metadata(this); total=m.optLong("byteSize"); if(m.has("versionName")) info=getString(R.string.version_prefix)+m.getString("versionName")+" · "+String.format(java.util.Locale.ROOT,getString(R.string.megabytes),total/1048576.0)+"\n"+m.optString("releaseNotes"); }
        catch(Exception ignored) { }
        if (!info.isEmpty() && UpdateManager.debug(this) && !feed.isEmpty() && feed.startsWith("http:")) info = getString(R.string.private_test_prefix)+info;
        updateText(details, info); visible(details,!info.isEmpty());
        visible(progress,state.equals("downloading") || state.equals("checking") || state.equals("preparing") || state.equals("verifying") || state.equals("installing"));
        progress.setIndeterminate(!state.equals("downloading"));
        if (state.equals("downloading") && total>0) progress.setProgress((int)Math.min(100,UpdateManager.prefs(this).getLong("downloaded",0)*100/total));
        boolean ready = state.equals("ready");
        check.setEnabled(!UpdateManager.busy(this) && !ready);
        visible(check, !ready);
        boolean recovery=UpdateManager.prefs(this).getBoolean("enqueuePending",false);
        updateText(download, getString(recovery?R.string.retry:R.string.download));
        visible(download,state.equals("available")||(state.equals("error")&&recovery)); visible(install,ready); visible(cancel,state.equals("downloading"));
        boolean canInstall = getPackageManager().canRequestPackageInstalls();
        updateText(install, getString(canInstall ? R.string.install : R.string.allow_install));
        updateText(hint, getString(ready && !canInstall ? R.string.permission_help : R.string.update_help));
        updateText(automatic, getString(R.string.auto_download_prefix) + (UpdateManager.prefs(this).getBoolean("autoDownload",false) ? getString(R.string.on) : getString(R.string.off)));
        Button action = switch (state) {
            case "ready" -> install;
            case "available" -> download;
            case "downloading" -> cancel;
            default -> check;
        };
        back.setNextFocusDownId(canFocus(action) ? action.getId() : R.id.update_automatic);
        automatic.setNextFocusUpId(canFocus(action) ? action.getId() : R.id.update_back);
        boolean previousAction = previousFocus == check || previousFocus == download
                || previousFocus == install || previousFocus == cancel;
        boolean followAction = continuingAction || !canFocus(getCurrentFocus())
                || (previousAction && !canFocus(previousFocus));
        awaitingUpdateActionFocus = false;
        updateFocusFallback = null;
        if (!followAction) return;

        if (canFocus(action) && action.requestFocus()) return;
        // No update action is available during verification. Remember where Android
        // moved focus, then follow the next action only if the viewer stays there.
        awaitingUpdateActionFocus = true;
        updateFocusFallback = getCurrentFocus();
    }
    private static boolean canFocus(View view) {
        return view != null && view.isShown() && view.isEnabled();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || !initialFocusPending) return;
        initialFocusPending = false;
        // The first layout can give Back focus before the visible action has been measured.
        Button initialAction = install.isShown() ? install : check;
        if (canFocus(initialAction)) initialAction.requestFocus();
    }
    @Override protected void onResume() { super.onResume(); UpdateManager.recover(this); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }
}

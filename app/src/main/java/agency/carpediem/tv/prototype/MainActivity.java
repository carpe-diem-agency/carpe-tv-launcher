package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.Switch;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Carpe TV launcher; Home role and optional OEM bridge are enabled explicitly. */
public final class MainActivity extends Activity {
    @Override protected void attachBaseContext(Context base) {
        base = AppLanguage.wrap(base);
        android.content.res.Configuration configuration =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        // A stable 960dp TV canvas: some firmware doubles density after a 4K HDMI
        // handshake while retaining a 1080p wm size override. Never modify global
        // display settings from the launcher; normalize only our Activity resources.
        int width = base.getResources().getDisplayMetrics().widthPixels;
        if (width > 0) {
            configuration.densityDpi = Math.max(120, Math.min(640, Math.round(width / 6f)));
            configuration.screenWidthDp = Math.round(width * 160f / configuration.densityDpi);
            configuration.screenHeightDp = Math.round(base.getResources().getDisplayMetrics().heightPixels * 160f / configuration.densityDpi);
            configuration.smallestScreenWidthDp = Math.min(configuration.screenWidthDp, configuration.screenHeightDp);
        }
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService wallpaperWorker = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private PageMotion pageMotion;
    private BackgroundMotion backgroundMotion;
    private final Map<String, LinearLayout> pageViews = new LinkedHashMap<>();
    private volatile boolean destroyed;
    private String focusedPackage;
    private String pendingHomePackage;
    private String launchedAppAwaitingReturn;
    private String movingPackage;
    private int moveLayoutGeneration;
    private boolean moveConfirmArmed;
    private List<String> movingOrder;
    private LinearLayout homeGrid;
    private List<AppItem> displayedApps;
    private List<AppItem> allApps = new ArrayList<>();
    private String page = "home";
    private String query = "";
    private Typeface font;
    private final Map<String, String> pageFocus = new java.util.HashMap<>();
    private volatile int generation;
    private View libraryEntry;
    private static final int RECOMMENDED_DIM = 60;
    private String settingsCategory = "appearance";
    private View settingsCategoryView;
    private final Map<String, TextView> auditLabels=new LinkedHashMap<>();
    private TextView auditMemory;
    private int auditGeneration;
    private TextView systemAccessStatus;
    private boolean systemOperation;
    private AlertDialog systemConfirmation;
    private void refreshSystemAccessUi(){runOnUiThread(()->{if(!destroyed)refreshApplicationAudit();});}
    private final rikka.shizuku.Shizuku.OnRequestPermissionResultListener systemPermissionResult=(code,result)->{if(code==508)refreshSystemAccessUi();};
    private final rikka.shizuku.Shizuku.OnBinderReceivedListener systemBinderReady=this::refreshSystemAccessUi;
    private final rikka.shizuku.Shizuku.OnBinderDeadListener systemBinderDead=this::refreshSystemAccessUi;
    private android.graphics.Bitmap blurredWallpaper;
    private android.graphics.Bitmap appsWallpaper;
    private android.graphics.Bitmap settingsWallpaper;
    private android.graphics.Bitmap sharpWallpaper;
    private String loadedWallpaper;
    private volatile int wallpaperGeneration;
    private final android.os.Handler wallpaperTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean wallpaperResumed;
    private final Runnable wallpaperRotationTick=this::scheduleWallpaperRotation;
    private final Runnable sleepStatusTick=this::updateSleepTimerIndicator;
    private final IdleTimer idleTimer = new IdleTimer(() -> {
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (!destroyed && wallpaperResumed && hasWindowFocus() && power.isInteractive()) Screensaver.launch(this);
    });
    private String backgroundKey;
    private boolean renderingPage;
    private boolean languageChange, restoreLanguageFocus;
    private final LruCache<String, Drawable.ConstantState> icons = new LruCache<>(64);
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!destroyed) { icons.evictAll(); displayedApps = null; worker.execute(MainActivity.this::refreshApps); }
        }
    };
    private static final class AppItem {
        final String pkg, label;
        AppItem(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openTestUpdates(intent);
        if (BuildConfig.DEBUG && intent.getBooleanExtra("internal_welcome_preview", false))
            content.post(() -> showWelcome(false));
        if (intent.getBooleanExtra("from_home", false) || intent.hasCategory(Intent.CATEGORY_HOME)) {
            launchedAppAwaitingReturn = null;
            restoreLastOpenedApp();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(systemPermissionResult);
        rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(systemBinderReady);
        rikka.shizuku.Shizuku.addBinderDeadListener(systemBinderDead);
        if (state != null) { page=state.getString("page","home"); settingsCategory=state.getString("category","appearance"); }
        try { font = new Typeface.Builder(getAssets(), "montserrat.ttf").setFontVariationSettings("'wght' 600").build(); } catch (RuntimeException ignored) { font = Typeface.DEFAULT; }
        setContentView(R.layout.activity_launcher);
        content = findViewById(R.id.content);
        pageMotion = new PageMotion(findViewById(R.id.content_host));
        backgroundMotion = new BackgroundMotion(findViewById(R.id.scene_a), findViewById(R.id.scene_b), (View) findViewById(R.id.sidebar).getParent());
        TextView version = findViewById(R.id.launcher_version);
        try { version.setText("v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName); }
        catch (PackageManager.NameNotFoundException ignored) { version.setText(""); }
        version.setTypeface(font);
        bindNavigation(R.id.nav_home, "home");
        bindNavigation(R.id.nav_apps, "apps");
        bindNavigation(R.id.nav_settings, "settings");
        // The rail is stable across pages; configure its state fades once.
        configureStateFade(findViewById(R.id.sidebar));
        android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        boolean welcome = state == null && !preferences.getBoolean("welcome_seen", false)
                && !HomeChoice.isSelected(this);
        renderPage();
        if (welcome) content.post(() -> showWelcome(true));
        if (state == null && !welcome && (preferences.contains("last_opened_package")
                || getIntent().getBooleanExtra("from_home", false)
                || getIntent().hasCategory(Intent.CATEGORY_HOME))) restoreLastOpenedApp();
        openTestUpdates(getIntent());
        restoreLanguageFocus = state != null && state.getBoolean("languageFocus",false);
        worker.execute(() -> {
            List<AppItem> cached = readCache();
            if (!cached.isEmpty()) runOnUiThread(() -> showApps(cached));
            prefetchArtwork(cached);
        });
        requestWallpaper();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page",page); state.putString("category",settingsCategory);
        state.putBoolean("languageFocus",languageChange || (getCurrentFocus()!=null && getCurrentFocus().getId()==R.id.language_choice));
        super.onSaveInstanceState(state);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && wallpaperResumed) scheduleScreensaver(); else idleTimer.stop();
        if (focused) focusPendingHomeApp();
        if(focused && restoreLanguageFocus) {
            View choice=findViewById(R.id.language_choice);
            if(choice!=null && choice.requestFocus()) restoreLanguageFocus=false;
        }
    }
    private void openTestUpdates(Intent intent) {
        if (UpdateManager.debug(this) && intent.hasExtra("internal_update_feed")) {
            String feed = intent.getStringExtra("internal_update_feed"); intent.removeExtra("internal_update_feed");
            startActivity(new Intent(this, UpdatesActivity.class).putExtra("test_feed", feed));
        }
    }
    private void bindNavigation(int id, String target) {
        View button = findViewById(id);
        button.setOnFocusChangeListener((view, focused) -> {
            if (focused && !renderingPage) openPage(target);
        });
        button.setOnClickListener(view -> {
            openPage(target);
            View entry = findViewById(view.getNextFocusRightId());
            if (entry != null) entry.requestFocus();
        });
        button.setOnKeyListener((view, key, event) -> {
            if (key != android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                View entry = findViewById(view.getNextFocusRightId());
                if (entry != null) return entry.requestFocus();
            }
            return event.getAction() == android.view.KeyEvent.ACTION_UP;
        });
    }
    private boolean isSidebarFocused() {
        View view = getCurrentFocus();
        return view != null && (view.getId() == R.id.nav_home
                || view.getId() == R.id.nav_apps || view.getId() == R.id.nav_settings);
    }
    @Override protected void onResume() {
        super.onResume();
        if (launchedAppAwaitingReturn != null) {
            launchedAppAwaitingReturn = null;
            restoreLastOpenedApp();
        }
        wallpaperResumed=true; scheduleWallpaperRotation(); scheduleScreensaver();
        updateHomeGreeting(); updateSaverCacheStatus(); updateSleepTimerIndicator();
        if("settings".equals(page) && "audit".equals(settingsCategory)) refreshApplicationAudit();
        UpdateManager.maybeCheck(this);
        TextView status = content.findViewById(R.id.home_status);
        if (status != null) status.setText(HomeChoice.status(this));
        TextView homeWarning = content.findViewById(R.id.home_remote_warning);
        if (homeWarning != null) homeWarning.setVisibility(HomeChoice.needsRemoteHomeCheck(this) ? View.VISIBLE : View.GONE);
        updateShizukuStatus();
        worker.execute(this::refreshApps);
    }
    @Override protected void onPause() {
        if (movingPackage != null) finishMove(false);
        wallpaperResumed=false; wallpaperTimer.removeCallbacks(sleepStatusTick); wallpaperTimer.removeCallbacks(wallpaperRotationTick); idleTimer.stop();
        super.onPause();
    }
    private void scheduleScreensaver() {
        if (wallpaperResumed && hasWindowFocus()) idleTimer.activate(Screensaver.minutes(this) * 60000L);
        else idleTimer.stop();
    }
    @Override public void onUserInteraction() { super.onUserInteraction(); idleTimer.reset(); }
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, filter);
    }
    @Override protected void onStop() {
        unregisterReceiver(packagesChanged);
        super.onStop();
    }
    private void refreshApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppItem> items = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (!isCarpeLauncher(pkg) && !items.containsKey(pkg)) {
                    items.put(pkg, new AppItem(pkg, info.loadLabel(pm).toString()));
                }
            }
        }
        List<AppItem> apps = new ArrayList<>(items.values());
        apps.sort(Comparator.comparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
        seedFirstRunFavorites(apps);
        // Home resumes after every app launch. Keep the persisted catalog quiet
        // when the PackageManager result has not actually changed.
        if (!sameApps(readCache(), apps)) writeCache(apps);
        runOnUiThread(() -> showApps(apps));
        prefetchArtwork(apps);
    }
    private boolean isCarpeLauncher(String pkg) {
        return pkg.equals(getPackageName())
                || pkg.equals("agency.carpediem.tv")
                || pkg.equals("agency.carpediem.tv.prototype")
                || pkg.equals("agency.carpediem.tv.publicpreview");
    }
    private void prefetchArtwork(List<AppItem> apps) {
        List<AppItem> ordered = new ArrayList<>(); List<String> favorites = getFavorites();
        for (String pkg : favorites) for (AppItem app : apps) if (pkg.equals(app.pkg)) ordered.add(app);
        for (AppItem app : apps) if (!favorites.contains(app.pkg)) ordered.add(app);
        int count = 0;
        for (AppItem app : ordered) {
            if (destroyed || Thread.currentThread().isInterrupted() || count++ >= 48) break;
            loadArtwork(app.pkg);
        }
    }
    private Drawable.ConstantState loadArtwork(String pkg) {
        Drawable.ConstantState state = icons.get(pkg);
        if (state == null) try {
            Drawable drawable = getPackageManager().getApplicationBanner(pkg);
            if (drawable == null) drawable = getPackageManager().getApplicationIcon(pkg);
            state = AppArtwork.prepare(getResources(), drawable).getConstantState();
            if (state != null) icons.put(pkg, state);
        } catch (PackageManager.NameNotFoundException ignored) { }
        return state;
    }
    private List<AppItem> readCache() {
        List<AppItem> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(getPreferences(MODE_PRIVATE).getString("apps", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String pkg = item.getString("package");
                if (!isCarpeLauncher(pkg)) result.add(new AppItem(pkg, item.getString("label")));
            }
        } catch (Exception ignored) { result.clear(); }
        return result;
    }
    private void writeCache(List<AppItem> apps) {
        JSONArray data = new JSONArray();
        try {
            for (AppItem app : apps) data.put(new JSONObject().put("package", app.pkg).put("label", app.label));
            getPreferences(MODE_PRIVATE).edit().putString("apps", data.toString()).apply();
        } catch (Exception ignored) { /* A cache failure must not prevent displaying apps. */ }
    }
    private void seedFirstRunFavorites(List<AppItem> apps) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        if (prefs.contains("apps") || prefs.contains("favorite_order") || prefs.contains("favorites") || apps.isEmpty()) return;
        List<String> selected = new ArrayList<>();
        String[] preferred = {
                "com.google.android.youtube.tv", "com.phlox.tvwebbrowser",
                "com.kyivstar.tv.androidtv", "com.netflix.ninja", "com.valvesoftware.steamlink"
        };
        for (String pkg : preferred) for (AppItem app : apps) {
            if (pkg.equals(app.pkg) && !selected.contains(pkg)) selected.add(pkg);
        }
        for (AppItem app : apps) {
            if (selected.size() >= 5) break;
            if (!selected.contains(app.pkg)) selected.add(app.pkg);
        }
        prefs.edit().putString("favorite_order", new JSONArray(selected).toString()).apply();
    }
    private void showApps(List<AppItem> apps) {
        if (destroyed || isFinishing()) return;
        if (sameApps(displayedApps, apps)) return;
        if (movingPackage != null) finishMove(false);
        displayedApps = new ArrayList<>(apps);
        allApps = new ArrayList<>(apps);
        if ("settings".equals(page)) {
            // Package enable/disable broadcasts must not rebuild the settings tree or steal focus.
            if(pageMotion!=null)pageMotion.discardInactive(content);
            pageViews.clear();pageViews.put(page,content);
            if("audit".equals(settingsCategory))refreshApplicationAudit();
            return;
        }
        View previous = getCurrentFocus();
        int previousId = previous != null ? previous.getId() : View.NO_ID;
        String previousPackage = previous != null && previous.getTag() instanceof String
                ? (String) previous.getTag() : null;
        renderPage();
        String pendingPackage = pendingHomePackage;
        focusPendingHomeApp();
        boolean restoredAppFocus = pendingPackage != null && pendingHomePackage == null;
        if (!restoredAppFocus && previousPackage != null) {
            View tile = findPageTile(content, previousPackage);
            if (tile == null) tile = findPageTile(content, null);
            if (tile != null) restoredAppFocus = tile.requestFocus();
        }
        if (!restoredAppFocus && (previousId == R.id.nav_home || previousId == R.id.nav_apps || previousId == R.id.nav_settings || previousId == R.id.home_wifi || previousId == R.id.apps_search)) {
            View restored = findViewById(previousId); if (restored != null) restored.requestFocus();
        }
    }
    private void restoreLastOpenedApp() {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String pkg = prefs.getString("last_opened_package", null);
        if (pkg == null) {
            openPage("home");
            findViewById(R.id.nav_home).requestFocus();
            return;
        }
        String target = getFavorites().contains(pkg) ? "home" : "apps";
        openPage(target);
        focusedPackage = pkg;
        pendingHomePackage = pkg;
        focusPendingHomeApp();
    }
    private void focusPendingHomeApp() {
        if (pendingHomePackage == null || !hasWindowFocus()) return;
        View tile = findPageTile(content, pendingHomePackage);
        if (tile == null && displayedApps != null) tile = findPageTile(content, null);
        if (tile != null && tile.isAttachedToWindow() && tile.requestFocus()) pendingHomePackage = null;
    }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    private GradientDrawable tileSurface(boolean focused) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[]{0xff3869b4, 0xff244a88} : new int[]{0xff263959, 0xff192945});
        d.setCornerRadius(dp(16));
        d.setStroke(dp(focused ? 2 : 1), focused ? 0xffc1ddff : 0xff425878);
        return d;
    }
    private TextView label(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setTypeface(Typeface.create(font, Typeface.BOLD)); return v;
    }
    private Button action(String value, Runnable callback) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTypeface(Typeface.create(font, Typeface.BOLD)); b.setTextColor(Color.WHITE);
        b.setTextSize(16); b.setPadding(dp(16), 0, dp(16), 0); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setBackgroundResource(R.drawable.control_background);
        configureStateFade(b);
        b.setOnClickListener(v -> callback.run()); return b;
    }
    private void showWelcome(boolean markSeen) {
        if (destroyed || isFinishing()) return;
        Runnable rememberChoice = () -> {
            if (markSeen) getPreferences(MODE_PRIVATE).edit().putBoolean("welcome_seen", true).commit();
        };
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28)); panel.setBackground(surface(0xff172337, 20));
        TextView title = label(getString(R.string.welcome_title), 24, Color.WHITE);
        panel.addView(title);
        TextView message = label(getString(R.string.welcome_message), 15, 0xffb9c7dc);
        message.setTypeface(font); message.setPadding(0, dp(12), 0, dp(24));
        panel.addView(message);
        Button explore = action(getString(R.string.welcome_explore), () -> {
            rememberChoice.run(); dialog.dismiss();
        });
        panel.addView(explore, new LinearLayout.LayoutParams(-1, dp(52)));
        Button choose = action(getString(R.string.make_home), () -> {
            // Keep onboarding available if Android's Home chooser is dismissed.
            dialog.dismiss(); HomeChoice.request(this);
        });
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(-1, dp(52)); second.topMargin = dp(10);
        panel.addView(choose, second);
        dialog.setContentView(panel);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams window = dialog.getWindow().getAttributes();
        window.dimAmount = .55f; dialog.getWindow().setAttributes(window);
        dialog.show(); dialog.getWindow().setLayout(dp(510), -2); explore.requestFocus();
    }
    private void renderSettings() {
        View settings = getLayoutInflater().inflate(R.layout.page_settings, content, false); content.addView(settings);
        ((TextView) settings.findViewById(R.id.settings_heading)).setTypeface(font);
        ((TextView) settings.findViewById(R.id.settings_clock)).setTypeface(font);
        settings.findViewById(R.id.settings_power).setOnClickListener(v -> showSleepNow());
        settings.findViewById(R.id.settings_timer).setOnClickListener(v -> SleepTimer.show(this));
        updateSleepTimerIndicator();
        settings.findViewById(R.id.settings_wifi).setOnClickListener(v -> openWifi());
        settings.findViewById(R.id.settings_bluetooth).setOnClickListener(v -> openBluetooth());
        LinearLayout categories = settings.findViewById(R.id.settings_categories);
        LinearLayout detail = settings.findViewById(R.id.settings_detail);
        String[][] entries = {{"appearance", getString(R.string.appearance)}, {"screensaver", getString(R.string.saver_title)}, {"favorites", getString(R.string.favorites)}, {"audit", getString(R.string.audit_title)}, {"device", getString(R.string.device)}, {"about", getString(R.string.about)}};
        for (String[] entry : entries) {
            Button category = action(entry[1], () -> { settingsCategory = entry[0]; settingsCategoryView = getCurrentFocus(); showSettingsDetail(detail, entry[0]); View target = firstFocusable(detail); if (target != null) target.requestFocus(); });
            int symbol = entry[0].equals("audit") ? R.drawable.ic_apps : entry[0].equals("screensaver") ? R.drawable.ic_screensaver : entry[0].equals("appearance") ? R.drawable.ic_monitor : entry[0].equals("favorites") ? R.drawable.ic_heart : entry[0].equals("device") ? R.drawable.ic_settings : R.drawable.ic_info;
            android.graphics.drawable.Drawable categoryIcon=getDrawable(symbol).mutate();
            categoryIcon.setBounds(0,0,dp(22),dp(22));
            category.setCompoundDrawablesRelative(categoryIcon,null,null,null); category.setCompoundDrawablePadding(dp(12));
            category.setMaxLines(2); category.setEllipsize(android.text.TextUtils.TruncateAt.END);
            category.setTextSize(14);
            category.setId(View.generateViewId()); category.setSelected(entry[0].equals(settingsCategory));
            category.setNextFocusLeftId(R.id.nav_settings);
            category.setOnKeyListener((v,key,event)->{
                if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT && event.getAction()==android.view.KeyEvent.ACTION_DOWN) {
                    ((ScrollView) settings.findViewById(R.id.settings_scroll)).scrollTo(0,0);
                    View target=firstFocusable(detail);if(target!=null) target.requestFocus();return true;
                }return false;
            });
            category.setOnFocusChangeListener((v, focused) -> {
                if (focused && !renderingPage) {
                    settingsCategoryView = category;
                    findViewById(R.id.nav_settings).setNextFocusRightId(category.getId());
                    if (!settingsCategory.equals(entry[0])) { settingsCategory = entry[0]; showSettingsDetail(detail, entry[0]); ((ScrollView) settings.findViewById(R.id.settings_scroll)).scrollTo(0,0); }
                    for (int i = 0; i < categories.getChildCount(); i++) categories.getChildAt(i).setSelected(categories.getChildAt(i) == category);
                    View target = firstFocusable(detail); if (target != null) category.setNextFocusRightId(target.getId());
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56)); params.bottomMargin = dp(12); categories.addView(category, params);
            if (entry[0].equals(settingsCategory)) settingsCategoryView = category;
        }
        for (int i = 0; i < categories.getChildCount(); i++) {
            View category = categories.getChildAt(i);
            category.setNextFocusUpId(categories.getChildAt(Math.max(0, i - 1)).getId());
            category.setNextFocusDownId(categories.getChildAt(Math.min(categories.getChildCount() - 1, i + 1)).getId());
        }
        showSettingsDetail(detail, settingsCategory);
        if (settingsCategoryView != null) {
            View first = firstFocusable(detail); if (first != null) settingsCategoryView.setNextFocusRightId(first.getId());
            findViewById(R.id.nav_settings).setNextFocusRightId(settingsCategoryView.getId());
        }
    }
    private void renderApplicationAudit(LinearLayout detail) {
        detail.addView(label(getString(R.string.audit_title),24,Color.WHITE));
        auditMemory=label("",13,0xffb9c7dc);auditMemory.setPadding(0,dp(8),0,dp(8));detail.addView(auditMemory);
        LinearLayout accessRow=new LinearLayout(this);accessRow.setGravity(Gravity.CENTER_VERTICAL);
        systemAccessStatus=label("",12,0xffb9c7dc);
        accessRow.addView(systemAccessStatus,new LinearLayout.LayoutParams(0,-2,1));
        Button accessButton=action(getString(R.string.audit_access_action),()->{
            try{SystemAccess.request(this);}catch(RuntimeException error){Toast.makeText(this,R.string.system_access_failed,Toast.LENGTH_LONG).show();}
        });accessButton.setTextSize(12);accessButton.setId(View.generateViewId());
        LinearLayout.LayoutParams accessSize=new LinearLayout.LayoutParams(dp(132),dp(38));accessSize.setMarginStart(dp(12));accessRow.addView(accessButton,accessSize);
        detail.addView(accessRow,new LinearLayout.LayoutParams(-1,-2));
        TextView heading=label(getString(R.string.audit_packages_heading),13,0xffb9c7dc);
        heading.setPadding(0,dp(12),0,dp(8));detail.addView(heading);
        String[] packages=AppAudit.PACKAGES;
        int[] names={R.string.audit_xiaomi_analytics,R.string.audit_xiaomi_statistics,R.string.audit_xiaomi_tvplus,R.string.audit_google_tv};
        for(int i=0;i<packages.length;i++) {
            final String pkg=packages[i];final String name=getString(names[i]);
            View row=getLayoutInflater().inflate(R.layout.audit_app_row,detail,false);
            row.setTag("audit:"+pkg);row.setOnClickListener(v->showAuditActions(pkg));
            TextView title=row.findViewById(R.id.audit_app_name);title.setText(name);title.setTypeface(font);
            TextView state=row.findViewById(R.id.audit_app_state);state.setTypeface(font);
            state.setText(R.string.audit_checking);state.setTag("audit-state:"+pkg);
            row.setContentDescription(name+". "+getString(R.string.audit_checking));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=dp(8);detail.addView(row,size);
            auditLabels.put(pkg,state);configureStateFade(row);
        }
        TextView limitation=label(getString(R.string.audit_limit),12,0xffaab9ce);
        limitation.setPadding(0,dp(4),0,dp(6));detail.addView(limitation);
        addSettingAction(detail,getString(R.string.audit_refresh),this::refreshApplicationAudit);
        addSettingAction(detail,getString(R.string.audit_all_apps),()->{
            try {startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));}
            catch(RuntimeException unavailable) {try {startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(RuntimeException error){Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_SHORT).show();}}
        });
        addSettingAction(detail,getString(R.string.choose_home),()->HomeChoice.chooseOther(this));
        try {
            getPackageManager().getApplicationInfo("com.google.android.apps.tv.launcherx",PackageManager.MATCH_DISABLED_COMPONENTS);
            addSettingAction(detail,getString(R.string.stock_restore),()->confirmSystemAction("stock",null));
        } catch(PackageManager.NameNotFoundException ignored) { /* Other OEMs use Android's generic Home chooser above. */ }
        refreshApplicationAudit();
    }
    private void showAuditActions(String pkg) {
        if ("com.google.android.apps.tv.launcherx".equals(pkg)) {
            showStockLauncherActions();
            return;
        }
        if(!PackagePolicy.telemetry(pkg)){openApplicationDetails(pkg);return;}
        boolean enabled=false;
        try{enabled=getPackageManager().getApplicationInfo(pkg,PackageManager.MATCH_DISABLED_COMPONENTS).enabled;}
        catch(PackageManager.NameNotFoundException unavailable){Toast.makeText(this,R.string.audit_unavailable,Toast.LENGTH_LONG).show();return;}
        final boolean disable=enabled;
        String[] options={getString(disable?R.string.system_disable:R.string.system_enable),getString(R.string.system_app_settings)};
        showSaverChoices(R.string.audit_title,options,-1,choice->{if(choice==0)confirmSystemAction(disable?"disable":"restore",pkg);else if(choice==1)openApplicationDetails(pkg);});
    }
    private android.app.Dialog showStockLauncherActions() {
        final String pkg = "com.google.android.apps.tv.launcherx";
        boolean enabled;
        try { enabled = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS).enabled; }
        catch (PackageManager.NameNotFoundException unavailable) {
            Toast.makeText(this, R.string.audit_unavailable, Toast.LENGTH_LONG).show();
            return null;
        }
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(26), dp(28), dp(24));
        panel.setBackgroundResource(R.drawable.dialog_background);
        TextView title = label(getString(R.string.audit_google_tv), 24, Color.WHITE);
        panel.addView(title);
        TextView state = label(getString(enabled ? R.string.audit_enabled : R.string.audit_disabled), 13,
                enabled ? 0xff9de7d5 : 0xffffd68a);
        state.setPadding(0, dp(7), 0, 0);
        panel.addView(state);
        TextView message = label(getString(enabled ? R.string.stock_manage_enabled : R.string.stock_manage_disabled), 15, 0xffc7d4e5);
        message.setId(R.id.stock_warning_message);
        message.setTypeface(font);
        message.setPadding(0, dp(20), 0, dp(22));
        panel.addView(message);
        Button primary = action(getString(enabled ? R.string.choose_home : R.string.stock_restore), () -> {
            dialog.dismiss();
            if (enabled) HomeChoice.chooseOther(this);
            else confirmSystemAction("stock", null);
        });
        primary.setId(R.id.stock_action_primary);
        panel.addView(primary, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout footer = new LinearLayout(this);
        LinearLayout.LayoutParams footerSize = new LinearLayout.LayoutParams(-1, dp(50));
        footerSize.topMargin = dp(10);
        panel.addView(footer, footerSize);
        Button details = action(getString(R.string.system_app_settings), () -> {
            dialog.dismiss(); openApplicationDetails(pkg);
        });
        details.setId(R.id.stock_action_android);
        footer.addView(details, new LinearLayout.LayoutParams(0, -1, 1));
        Button close = action(getString(R.string.close), dialog::dismiss);
        close.setId(R.id.stock_action_close);
        LinearLayout.LayoutParams closeSize = new LinearLayout.LayoutParams(0, -1, 1);
        closeSize.leftMargin = dp(10);
        footer.addView(close, closeSize);
        dialog.setContentView(panel);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams window = dialog.getWindow().getAttributes();
        window.dimAmount = .58f; dialog.getWindow().setAttributes(window);
        dialog.show();
        dialog.getWindow().setLayout(dp(600), -2);
        primary.requestFocus();
        return dialog;
    }
    private void confirmSystemAction(String operation,String pkg) {
        if(systemOperation || (systemConfirmation!=null && systemConfirmation.isShowing()))return;
        int text=operation.equals("stock")?R.string.stock_restore_warning:operation.equals("disable")?R.string.system_disable_warning:R.string.system_restore_warning;
        systemConfirmation=new AlertDialog.Builder(this).setTitle(operation.equals("stock")?R.string.stock_restore:R.string.system_confirm_title)
            .setMessage(getString(text)+(pkg==null?"":"\n\n"+pkg)).setNegativeButton(R.string.cancel,null)
            .setPositiveButton(R.string.system_continue,(dialog,which)->runSystemAction(operation,pkg)).create();
        systemConfirmation.setOnDismissListener(dialog->{if(systemConfirmation==dialog)systemConfirmation=null;});
        systemConfirmation.show();
    }
    private void runSystemAction(String operation,String pkg) {
        if(systemOperation)return;
        if(!SystemAccess.ready()){Toast.makeText(this,R.string.system_access_required,Toast.LENGTH_LONG).show();return;}
        systemOperation=true;
        worker.execute(()->{
            boolean success=false;
            try{
                if(operation.equals("stock"))SystemAccess.enableStockLauncher(this);
                else if(operation.equals("disable"))SystemAccess.disableAnalytics(this,pkg);
                else SystemAccess.restoreAnalytics(this,pkg);
                success=true;
            }catch(Exception error){android.util.Log.w("CarpeSystemAccess","Package operation failed",error);}
            final boolean completed=success;
            runOnUiThread(()->{
                systemOperation=false;if(destroyed)return;
                Toast.makeText(this,completed?R.string.system_operation_done:R.string.system_operation_failed,Toast.LENGTH_LONG).show();
                refreshApplicationAudit();
                if(completed&&operation.equals("stock")){
                    HomeChoice.chooseOther(this);
                }
            });
        });
    }
    private void openApplicationDetails(String pkg) {
        try {startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg)));}
        catch(RuntimeException error){Toast.makeText(this,R.string.info_unavailable,Toast.LENGTH_SHORT).show();}
    }
    private void refreshApplicationAudit() {
        if(auditMemory==null || !"audit".equals(settingsCategory)) return;
        if(systemAccessStatus!=null) systemAccessStatus.setText(SystemAccess.ready()?R.string.system_access_ready:SystemAccess.running()?R.string.system_access_permission:R.string.system_access_off);
        final int ticket=++auditGeneration;
        worker.execute(()->{
            AppAudit.Snapshot snapshot=AppAudit.read(this);
            runOnUiThread(()->{
                if(destroyed || ticket!=auditGeneration || auditMemory==null || !"audit".equals(settingsCategory)) return;
                auditMemory.setText(getString(R.string.audit_memory,snapshot.availableRam/1048576,snapshot.totalRam/1048576,snapshot.freeDisk/1048576));
                for(String pkg:AppAudit.PACKAGES) {
                    TextView status=auditLabels.get(pkg);if(status==null) continue;
                    int state=snapshot.states.get(pkg);
                    status.setText(state==0?R.string.audit_unavailable:state==1?R.string.audit_enabled:R.string.audit_disabled);
                    status.setTextColor(state==2?0xff9adfc6:0xffb9c7dc);
                    View row=(View)status.getParent();TextView title=row.findViewById(R.id.audit_app_name);
                    row.setContentDescription(title.getText()+". "+status.getText());
                }
            });
        });
    }
    private void showSettingsDetail(LinearLayout detail, String category) {
        boolean previousRendering=renderingPage;renderingPage=true;
        try {
        detail.removeAllViews(); auditGeneration++; auditLabels.clear(); auditMemory=null;
        if ("audit".equals(category)) { renderApplicationAudit(detail);
        } else if ("appearance".equals(category)) {
            View appearance = getLayoutInflater().inflate(R.layout.appearance_controls, detail, false); detail.addView(appearance);
            ((TextView) appearance.findViewById(R.id.appearance_heading)).setTypeface(font);
            TextView chosen = appearance.findViewById(R.id.wallpaper_label); chosen.setTypeface(font); chosen.setText(getString(R.string.selected_prefix) + wallpaperName());
            LinearLayout choices=appearance.findViewById(R.id.wallpaper_choices);
            for (WallpaperCatalog.Entry entry:WallpaperCatalog.entries(this)) {
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(dp(170),-2); cardSize.setMarginEnd(dp(10)); choices.addView(card,cardSize);
                android.widget.ImageButton preview=new android.widget.ImageButton(this); preview.setId(entry.viewId); preview.setTag(entry);
                preview.setBackgroundResource(R.drawable.control_background); preview.setPadding(0,0,0,0);
                if (entry.thumbnail!=0) preview.setImageResource(entry.thumbnail); else preview.setImageDrawable(new android.graphics.drawable.ColorDrawable(getColor(R.color.cd_background)));
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP); preview.setForeground(getDrawable(R.drawable.wallpaper_foreground));
                preview.setContentDescription(getString(entry.title)); card.addView(preview,new LinearLayout.LayoutParams(-1,dp(96)));
                preview.setOutlineProvider(new android.view.ViewOutlineProvider() { @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(12)); } });
                preview.setClipToOutline(true); preview.setSelected(entry.key.equals(wallpaper()));
                TextView caption=label(getString(entry.title),12,0xffdbe3f1); caption.setSingleLine(true); caption.setEllipsize(android.text.TextUtils.TruncateAt.END); caption.setPadding(0,dp(8),0,0); card.addView(caption);
                preview.setOnClickListener(v -> {
                    getPreferences(MODE_PRIVATE).edit().putString("wallpaper",entry.key).putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()).apply();
                    for (WallpaperCatalog.Entry other:WallpaperCatalog.entries(this)) appearance.findViewById(other.viewId).setSelected(other.key.equals(entry.key));
                    chosen.setText(getString(R.string.selected_prefix)+wallpaperName()); requestWallpaper(); updateBackground(); scheduleWallpaperRotation();
                });
            }
            Switch dynamic=appearance.findViewById(R.id.dynamic_wallpapers); dynamic.setTypeface(font);
            dynamic.setChecked(getPreferences(MODE_PRIVATE).getBoolean(WallpaperRotation.ENABLED,false));
            dynamic.setOnCheckedChangeListener((view,enabled) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean(WallpaperRotation.ENABLED,enabled).putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()).apply();
                scheduleWallpaperRotation();
            });
            TextView dimLabel = appearance.findViewById(R.id.dim_label); dimLabel.setTypeface(font);
            TextView dimValue = appearance.findViewById(R.id.dim_value); dimValue.setTypeface(font);
            SeekBar dim = appearance.findViewById(R.id.wallpaper_dim); dim.setProgress(getPreferences(MODE_PRIVATE).getInt("dim", RECOMMENDED_DIM));
            dimLabel.setText(getString(R.string.wallpaper_dim)); dimValue.setText(dim.getProgress() + "%");
            dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int value, boolean user) { dimValue.setText(value + "%"); if (user) { getPreferences(MODE_PRIVATE).edit().putInt("dim", value).apply(); updateBackground(); } }
                public void onStartTrackingTouch(SeekBar bar) {}
                public void onStopTrackingTouch(SeekBar bar) {}
            });
            Button resetDim=appearance.findViewById(R.id.dim_reset); resetDim.setTypeface(font);
            resetDim.setText(getString(R.string.dim_reset_recommended,RECOMMENDED_DIM));
            resetDim.setOnClickListener(v -> {
                getPreferences(MODE_PRIVATE).edit().putInt("dim",RECOMMENDED_DIM).apply();
                dim.setProgress(RECOMMENDED_DIM); updateBackground();
            });
            Switch animations = appearance.findViewById(R.id.animations); animations.setTypeface(font);
            animations.setChecked(getPreferences(MODE_PRIVATE).getBoolean("animations", true));
            animations.setOnCheckedChangeListener((button, checked) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("animations", checked).apply();
                configureStateFade(findViewById(android.R.id.content));
            });
            Button language = action(getString(R.string.language) + " · " + AppLanguage.label(this), () -> {
                String[] tags={"","uk","en"}; String selected=AppLanguage.selected(this); int index=selected.equals("uk")?1:selected.equals("en")?2:0;
                new AlertDialog.Builder(this).setTitle(R.string.language)
                        .setSingleChoiceItems(new String[]{getString(R.string.language_system),"Українська","English"},index,(dialog,which)->{
                            dialog.dismiss(); if(!tags[which].equals(AppLanguage.selected(this))) { AppLanguage.choose(this,tags[which]); languageChange=true; recreate(); }
                        }).setNegativeButton(R.string.cancel,null).show();
            });
            language.setId(R.id.language_choice);
            LinearLayout.LayoutParams languageSize=new LinearLayout.LayoutParams(-1,dp(52)); languageSize.topMargin=dp(12); detail.addView(language,languageSize);
        } else if ("screensaver".equals(category)) {
            detail.addView(label(getString(R.string.saver_title), 24, Color.WHITE));
            TextView help = label(getString(R.string.saver_help), 14, 0xffb9c7dc);
            help.setPadding(0, dp(14), 0, dp(14)); detail.addView(help);
            int minutes = Screensaver.minutes(this);
            addSettingAction(detail, getString(R.string.saver_after, minutes == 0 ? getString(R.string.saver_off) : getString(R.string.saver_minutes, minutes)), () -> {
                String[] options = new String[Screensaver.MINUTES.length]; int selected = 0;
                for (int i = 0; i < options.length; i++) {
                    int value = Screensaver.MINUTES[i];
                    options[i] = value == 0 ? getString(R.string.saver_off) : getString(R.string.saver_minutes, value);
                    if (value == Screensaver.DEFAULT_MINUTES) options[i] += getString(R.string.saver_recommended);
                    if (value == Screensaver.minutes(this)) selected = i;
                }
                showSaverChoices(R.string.saver_delay, options, selected, which -> {
                            Screensaver.prefs(this).edit().putInt(Screensaver.TIMEOUT, Screensaver.MINUTES[which]).apply();
                            showSettingsDetail(detail, category);
                            View target = detail.findViewById(R.id.saver_timeout); if (target != null) target.requestFocus();
                            scheduleScreensaver();
                        });
            });
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_timeout);
            boolean aerial = "aerial".equals(Screensaver.prefs(this).getString(Screensaver.PROVIDER, "builtin"));
            addSettingAction(detail, getString(R.string.saver_source, getString(aerial ? R.string.saver_aerial : R.string.saver_builtin)), () -> {
                showSaverChoices(R.string.saver_source_title,
                        new String[]{getString(R.string.saver_builtin),getString(R.string.saver_aerial)}, aerial ? 1 : 0, which -> {
                            if (which == 1 && !Screensaver.aerialAvailable(this)) {
                                Toast.makeText(this, R.string.saver_aerial_fallback, Toast.LENGTH_LONG).show(); return;
                            }
                            Screensaver.prefs(this).edit().putString(Screensaver.PROVIDER, which == 0 ? "builtin" : "aerial").apply();
                            showSettingsDetail(detail, category);
                            detail.findViewById(R.id.saver_provider).requestFocus();
                        });
            });
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_provider);
            Switch online = new Switch(this); online.setId(R.id.saver_online); online.setTypeface(font);
            online.setText(R.string.saver_online); online.setTextColor(Color.WHITE); online.setTextSize(14);
            online.setMinHeight(dp(52)); online.setChecked(Screensaver.prefs(this).getBoolean("saverOnline",true));
            online.setOnCheckedChangeListener((button,enabled)->Screensaver.prefs(this).edit().putBoolean("saverOnline",enabled).apply());
            detail.addView(online,new LinearLayout.LayoutParams(-1,dp(62)));
            TextView usage=label(getString(R.string.saver_cache_policy,SaverCache.reserveMb(this)),12,0xffb9c7dc); usage.setId(R.id.saver_cache_status);
            usage.setPadding(0,dp(8),0,dp(8));detail.addView(usage);updateSaverCacheStatus();
            addSettingAction(detail, getString(R.string.saver_preview), () -> Screensaver.launch(this));
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_preview);
            addSettingAction(detail, getString(R.string.saver_system), () -> {
                try { startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS)); }
                catch (RuntimeException unavailable) {
                    try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                    catch (RuntimeException missing) { Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_LONG).show(); }
                }
            });
            addSettingAction(detail,getString(R.string.saver_reserve,SaverCache.reserveMb(this)),()->
                showSaverChoices(R.string.saver_reserve_title,new String[]{getString(R.string.saver_reserve_recommended),getString(R.string.saver_reserve_small)},SaverCache.reserveMb(this)==512?1:0,which->{
                    Screensaver.prefs(this).edit().putInt("saverReserveMb",which==0?1024:512).apply();
                    showSettingsDetail(detail,category);detail.findViewById(R.id.saver_online).requestFocus();
                }));
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_reserve);
            addSettingAction(detail,getString(R.string.saver_clear),()->worker.execute(()->{
                SaverCache.get(this).clear();runOnUiThread(this::updateSaverCacheStatus);
            }));
            addSettingAction(detail,getString(R.string.saver_credits),()->worker.execute(()->{
                StringBuilder text=new StringBuilder();
                for(SaverMedia item:SaverCache.get(this).entries()) text.append(item.title).append("\n").append(item.credit).append("\n").append(item.source).append("\n").append(item.license).append("\n\n");
                runOnUiThread(()->{if(!destroyed&&!isFinishing()) new AlertDialog.Builder(this).setTitle(R.string.saver_credits)
                        .setMessage(text.length()==0?getString(R.string.saver_credits_empty):text.toString()).setPositiveButton(R.string.close,null).show();});
            }));
            TextView systemHint = label(getString(R.string.saver_system_help), 12, 0xffb9c7dc);
            systemHint.setPadding(0, dp(16), 0, 0); detail.addView(systemHint);
        } else if ("favorites".equals(category)) {
            detail.addView(label(getString(R.string.favorites), 24, Color.WHITE));
            TextView explanation = label(getString(R.string.favorites_help), 15, 0xffb9c7dc);
            explanation.setPadding(0, dp(18), 0, dp(24)); detail.addView(explanation);
            addSettingAction(detail, getString(R.string.open_library), () -> openPage("apps"));
        } else if ("device".equals(category)) {
            detail.addView(label(getString(R.string.device), 24, Color.WHITE));
            TextView home = label(HomeChoice.status(this), 14, 0xffb9c7dc); home.setId(R.id.home_status); home.setPadding(0, dp(16), 0, dp(16)); detail.addView(home);
            TextView warning = label(getString(R.string.home_remote_check), 14, 0xffffd68a);
            warning.setId(R.id.home_remote_warning);
            warning.setPadding(0, 0, 0, dp(12));
            warning.setVisibility(HomeChoice.needsRemoteHomeCheck(this) ? View.VISIBLE : View.GONE);
            detail.addView(warning);
            boolean available = false;
            try { getPackageManager().getActivityInfo(new android.content.ComponentName(this, MainActivity.class.getPackage().getName() + ".HomeEntry"), PackageManager.MATCH_DISABLED_COMPONENTS); available = true; }
            catch (PackageManager.NameNotFoundException ignored) { }
            if (available) { addSettingAction(detail, getString(R.string.make_home), () -> HomeChoice.request(this)); addSettingAction(detail, getString(R.string.choose_home), () -> HomeChoice.chooseOther(this)); }
            addSettingAction(detail, getString(R.string.android_settings), () -> {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
                catch (RuntimeException error) { Toast.makeText(this, getString(R.string.settings_unavailable), Toast.LENGTH_SHORT).show(); }
            });
            TextView extra = label(getString(R.string.optional_system_controls), 16, 0xffb9c7dc);
            extra.setPadding(0, dp(24), 0, dp(8)); detail.addView(extra);
            addSettingAction(detail, getString(R.string.setup_title), () -> startActivity(new Intent(this, SystemSetupActivity.class)));
            TextView shizuku = label("", 14, 0xffb9c7dc);
            shizuku.setId(R.id.shizuku_status); shizuku.setPadding(0, dp(16), 0, dp(8));
            detail.addView(shizuku); updateShizukuStatus();
            addSettingAction(detail, getString(R.string.shizuku_open), () -> {
                try { SystemAccess.openManager(this); }
                catch (RuntimeException unavailable) { Toast.makeText(this, R.string.system_access_failed, Toast.LENGTH_LONG).show(); }
            });
            addSettingAction(detail, getString(R.string.shizuku_grant), () -> {
                try { SystemAccess.request(this); }
                catch (RuntimeException unavailable) { Toast.makeText(this, R.string.system_access_failed, Toast.LENGTH_LONG).show(); }
            });
            TextView shizukuHint = label(getString(R.string.shizuku_start_hint), 12, 0xffb9c7dc);
            shizukuHint.setPadding(0, dp(8), 0, dp(16)); detail.addView(shizukuHint);
        } else {
            LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
            ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.brand_icon);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams markSize = new LinearLayout.LayoutParams(dp(52), dp(52)); markSize.setMarginEnd(dp(16));
            brand.addView(mark, markSize); brand.addView(label(getString(R.string.app_name), 28, Color.WHITE)); detail.addView(brand);
            TextView info = label(getString(R.string.version_prefix) + UpdateManager.appVersion(this) + getString(R.string.developer_info), 16, 0xffb9c7dc); info.setPadding(0, dp(20), 0, dp(24)); detail.addView(info);
            addSettingAction(detail, getString(R.string.report_problem), this::showFeedback);
            addSettingAction(detail, getString(R.string.developer_site), () -> openExternalPage("https://carpediem.agency/"));
            addSettingAction(detail, getString(R.string.updates), () -> startActivity(new Intent(this, UpdatesActivity.class)));
            addSettingAction(detail, getString(R.string.code_license), () -> showAssetNotice(
                    getString(R.string.code_license), "licenses/Apache-2.0.txt"));
            addSettingAction(detail, getString(R.string.third_party_notices), () -> showAssetNotice(
                    getString(R.string.third_party_notices), "licenses/THIRD-PARTY-NOTICES.md"));
            addSettingAction(detail, getString(R.string.font_license), () -> {
                showAssetNotice("Montserrat · SIL OFL", "licenses/Montserrat-OFL.txt");
            });
        }
        linkSettingsFocus(detail);
        configureStateFade(detail);
        } finally {renderingPage=previousRendering;}
    }
    private void openExternalPage(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show(); }
    }
    private void showFeedback() {
        final String issueUrl = "https://github.com/carpe-diem-agency/carpe-tv-launcher/issues/new?template=bug.yml";
        View previousFocus = getCurrentFocus();
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(surface(0xff172337, 20));
        TextView title = label(getString(R.string.report_problem), 24, Color.WHITE);
        panel.addView(title);
        TextView hint = label(getString(R.string.report_problem_hint), 14, 0xffb9c7dc);
        hint.setTypeface(font); hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, dp(18)); panel.addView(hint);
        try {
            com.google.zxing.common.BitMatrix bits = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(issueUrl, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(256, 256, android.graphics.Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++)
                bitmap.setPixel(x, y, bits.get(x, y) ? Color.BLACK : Color.WHITE);
            ImageView qr = new ImageView(this); qr.setImageBitmap(bitmap);
            qr.setBackgroundColor(Color.WHITE); qr.setPadding(dp(8), dp(8), dp(8), dp(8));
            panel.addView(qr, new LinearLayout.LayoutParams(dp(204), dp(204)));
        } catch (com.google.zxing.WriterException error) {
            TextView fallback = label(issueUrl, 12, 0xffb9c7dc); panel.addView(fallback);
        }
        Button open = action(getString(R.string.open_issue), () -> { dialog.dismiss(); openExternalPage(issueUrl); });
        LinearLayout.LayoutParams openSize = new LinearLayout.LayoutParams(-1, dp(52)); openSize.topMargin = dp(18);
        panel.addView(open, openSize);
        Button close = action(getString(R.string.close), dialog::dismiss);
        LinearLayout.LayoutParams closeSize = new LinearLayout.LayoutParams(-1, dp(52)); closeSize.topMargin = dp(8);
        panel.addView(close, closeSize);
        dialog.setContentView(panel);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
        dialog.setOnDismissListener(ignored -> { if (previousFocus != null && previousFocus.isAttachedToWindow()) previousFocus.requestFocus(); });
        dialog.show(); dialog.getWindow().setLayout(dp(430), -2); open.requestFocus();
    }
    private void showAssetNotice(String title, String assetPath) {
        String notice;
        try (java.io.InputStream input = getAssets().open(assetPath)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int length;
            while ((length = input.read(buffer)) != -1) bytes.write(buffer, 0, length);
            notice = new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            notice = getString(R.string.license_failed);
        }
        if (assetPath.endsWith("THIRD-PARTY-NOTICES.md")) notice = readableThirdPartyNotice(notice);
        TextView body = new TextView(this);
        body.setText(notice);
        body.setTextSize(15);
        body.setTextColor(Color.WHITE);
        body.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        body.setLineSpacing(dp(4), 1f);
        body.setPadding(dp(24), dp(16), dp(24), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setFocusable(true);
        scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setPositiveButton(getString(R.string.close), null).create();
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(surface(0xff17253e, dp(18)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xffb9d5ff);
    }
    private String readableThirdPartyNotice(String markdown) {
        StringBuilder text = new StringBuilder();
        boolean tableStarted = false;
        boolean footerStarted = false;
        for (String raw : markdown.split("\\n")) {
            String line = raw.trim();
            if (line.startsWith("# ")) continue;
            if (line.isEmpty()) {
                if (footerStarted) text.append("\n\n");
                continue;
            }
            if (line.startsWith("|")) {
                tableStarted = true;
                String[] columns = line.split("\\|", -1);
                if (columns.length < 6 || columns[1].trim().equals("Component")
                        || columns[1].trim().startsWith("---")) continue;
                text.append(columns[1].trim()).append(" · ").append(columns[2].trim())
                        .append(" · ").append(columns[3].trim()).append("\n")
                        .append(columns[4].trim()).append("\n\n");
            } else if (tableStarted) {
                if (!footerStarted) { text.append("\n"); footerStarted = true; }
                text.append(line.replace("`", "")).append(' ');
            }
        }
        return text.toString().trim();
    }
    private void updateSaverCacheStatus() {
        if(destroyed||findViewById(R.id.saver_cache_status)==null) return;
        worker.execute(()->{
            SaverCache cache=SaverCache.get(this);long used=cache.bytes()/SaverCache.MIB,free=cache.freeBytes()/SaverCache.MIB;int reserve=SaverCache.reserveMb(this);
            runOnUiThread(()->{TextView status=findViewById(R.id.saver_cache_status);if(!destroyed&&status!=null)
                status.setText(getString(R.string.saver_cache_usage,used,free)+"\n"+getString(R.string.saver_cache_policy,reserve)
                        +(free<reserve&&Screensaver.prefs(this).getBoolean("saverOnline",true)?"\n"+getString(R.string.saver_cache_waiting):""));});
        });
    }
    private void showSaverChoices(int title, String[] choices, int selected, java.util.function.IntConsumer choose) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(20),dp(20),dp(20)); panel.setBackground(surface(0xff172337,20));
        TextView heading = label(getString(title),22,Color.WHITE); heading.setPadding(dp(8),0,0,dp(18)); panel.addView(heading);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(350),dp(50)*choices.length)));
        Button selectedView = null;
        for(int i=0;i<choices.length;i++) {
            final int option=i;
            Button button=action(choices[i],()->{ dialog.dismiss(); choose.accept(option); });
            button.setTextSize(15); button.setSelected(i==selected); button.setCompoundDrawablePadding(dp(10));
            if(i==selected) button.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_badge,0,0,0);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(44)); size.bottomMargin=dp(6); list.addView(button,size);
            if(i==selected) selectedView=button;
        }
        Button cancel=action(getString(R.string.cancel),dialog::dismiss);
        LinearLayout.LayoutParams cancelSize=new LinearLayout.LayoutParams(-1,dp(44)); cancelSize.topMargin=dp(16); panel.addView(cancel,cancelSize);
        dialog.setContentView(panel); dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams window=dialog.getWindow().getAttributes(); window.dimAmount=.55f; dialog.getWindow().setAttributes(window);
        dialog.show(); dialog.getWindow().setLayout(dp(550),-2);
        if(selectedView!=null) selectedView.requestFocus();
    }
    private String wallpaper() { return WallpaperCatalog.find(this,getPreferences(MODE_PRIVATE).getString("wallpaper", "alpine")).key; }
    private String wallpaperName() { return getString(WallpaperCatalog.find(this,wallpaper()).title); }
    private void scheduleWallpaperRotation() {
        wallpaperTimer.removeCallbacks(wallpaperRotationTick);
        if(destroyed || !wallpaperResumed) return;
        long now=System.currentTimeMillis();
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        if(WallpaperRotation.advanceIfDue(this,prefs,now)) {
            requestWallpaper(); updateBackground();
            TextView label=findViewById(R.id.wallpaper_label);
            if(label!=null) label.setText(getString(R.string.selected_prefix)+wallpaperName());
            for(WallpaperCatalog.Entry entry:WallpaperCatalog.entries(this)) {
                View preview=findViewById(entry.viewId); if(preview!=null) preview.setSelected(entry.key.equals(wallpaper()));
            }
        }
        if(prefs.getBoolean(WallpaperRotation.ENABLED,false)) wallpaperTimer.postDelayed(wallpaperRotationTick,WallpaperRotation.delay(prefs,now));
    }
    private void requestWallpaper() {
        final WallpaperCatalog.Entry entry=WallpaperCatalog.find(this,wallpaper());
        if (entry.key.equals(loadedWallpaper) && sharpWallpaper!=null) return;
        final int ticket=++wallpaperGeneration;
        if (entry.photo==0) { loadedWallpaper=entry.key; sharpWallpaper=null; blurredWallpaper=null; appsWallpaper=null; settingsWallpaper=null; return; }
        wallpaperWorker.execute(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            if (ticket!=wallpaperGeneration || destroyed) return;
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
            options.inScaled=false; options.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
            android.graphics.Bitmap sharp=android.graphics.BitmapFactory.decodeResource(getResources(),entry.photo,options);
            android.graphics.Bitmap blurred=WallpaperBlur.loadOrCreate(this,entry.photo,entry.key);
            android.graphics.Bitmap apps=WallpaperBlur.tint(blurred,35);
            android.graphics.Bitmap settings=WallpaperBlur.tint(blurred,153);
            runOnUiThread(() -> {
                if (ticket!=wallpaperGeneration || destroyed) { if(sharp!=null) sharp.recycle(); if(blurred!=null) blurred.recycle(); if(apps!=null) apps.recycle(); if(settings!=null) settings.recycle(); return; }
                sharpWallpaper=sharp; blurredWallpaper=blurred; appsWallpaper=apps; settingsWallpaper=settings; loadedWallpaper=entry.key; updateBackground();
            });
        });
    }
    private void addSettingAction(LinearLayout detail, String text, Runnable callback) {
        Button button = action(text, callback); button.setId(View.generateViewId());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52)); params.topMargin = dp(10); detail.addView(button, params);
    }
    private View firstFocusable(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view.isFocusable()) return view;
            if (view instanceof android.view.ViewGroup) { View nested = firstFocusable((android.view.ViewGroup) view); if (nested != null) return nested; }
        }
        return null;
    }
    private void linkSettingsFocus(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view.isFocusable()) {
                if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
                if (settingsCategoryView != null) view.setNextFocusLeftId(settingsCategoryView.getId());
                if (view.getTag() instanceof WallpaperCatalog.Entry) {
                    java.util.List<WallpaperCatalog.Entry> entries=WallpaperCatalog.entries(this);
                    for (int n=0;n<entries.size();n++) if (entries.get(n).viewId==view.getId()) {
                        if(n>0) view.setNextFocusLeftId(entries.get(n-1).viewId);
                        view.setNextFocusRightId(entries.get(Math.min(n+1,entries.size()-1)).viewId);
                        view.setNextFocusDownId(R.id.dynamic_wallpapers);
                    }
                }
            }
            if (view instanceof android.view.ViewGroup) linkSettingsFocus((android.view.ViewGroup) view);
        }
    }

    private void renderPage() {
        if (pageMotion != null) pageMotion.discardInactive(content);
        pageViews.clear();
        pageViews.put(page, content);
        renderCurrentPage();
    }
    private void renderCurrentPage() {
        renderingPage = true;
        try { renderPageContent(); }
        finally { renderingPage = false; configureStateFade(content); }
    }
    private void configureStateFade(View view) {
        if(view.getId()==R.id.app_artwork) {
            boolean enabled=motionEnabled();
            if(!Boolean.valueOf(enabled).equals(view.getTag(R.id.press_motion_enabled))) {
                view.setTag(R.id.press_motion_enabled,enabled);
                view.setStateListAnimator(enabled ? android.animation.AnimatorInflater.loadStateListAnimator(this,R.animator.app_press) : null);
                view.setAlpha(1f);
            }
        }
        if (view.getBackground() instanceof android.graphics.drawable.StateListDrawable) {
            android.graphics.drawable.StateListDrawable states =
                    (android.graphics.drawable.StateListDrawable) view.getBackground().mutate();
            boolean enabled = motionEnabled();
            if (!enabled) states.jumpToCurrentState();
            states.setEnterFadeDuration(enabled ? 160 : 0);
            states.setExitFadeDuration(enabled ? 140 : 0);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) configureStateFade(group.getChildAt(i));
        }
    }
    private void renderPageContent() {
        generation++;
        libraryEntry = null;
        content.removeAllViews();
        updateBackground();
        findViewById(R.id.nav_home).setSelected("home".equals(page));
        findViewById(R.id.nav_apps).setSelected("apps".equals(page));
        findViewById(R.id.nav_settings).setSelected("settings".equals(page));
        if ("home".equals(page)) {
            View header = getLayoutInflater().inflate(R.layout.home_header, content, false);
            ((TextView) header.findViewById(R.id.home_time)).setTypeface(Typeface.create(font, Typeface.BOLD));
            ((TextView) header.findViewById(R.id.home_date)).setTypeface(font);
            TextView greeting = header.findViewById(R.id.home_greeting);
            greeting.setTypeface(font);
            int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            greeting.setText(hour < 6 ? getString(R.string.greeting_night) : hour < 12 ? getString(R.string.greeting_morning) : hour < 18 ? getString(R.string.greeting_day) : getString(R.string.greeting_evening));
            content.addView(header);
            header.findViewById(R.id.home_power).setOnClickListener(v -> showSleepNow());
            header.findViewById(R.id.home_timer).setOnClickListener(v -> SleepTimer.show(this));
            updateSleepTimerIndicator();
            header.findViewById(R.id.home_wifi).setOnClickListener(v -> openWifi());
        header.findViewById(R.id.home_bluetooth).setOnClickListener(v -> openBluetooth());
        } else if (!"apps".equals(page) && !"settings".equals(page)) {
            TextView title = label("home".equals(page) ? getString(R.string.your_apps) : getString(R.string.settings), 28, Color.WHITE);
            title.setPadding(8, 24, 8, 12);
            content.addView(title);
        }
        if ("settings".equals(page)) { renderSettings(); return; }
        if ("apps".equals(page)) {
            View layout = getLayoutInflater().inflate(R.layout.page_apps, content, false);
            content.addView(layout);
            TextView title = layout.findViewById(R.id.apps_title); title.setTypeface(Typeface.create(font, Typeface.BOLD));
            ((TextView) layout.findViewById(R.id.apps_clock)).setTypeface(font);
            layout.findViewById(R.id.apps_power).setOnClickListener(v -> showSleepNow());
            layout.findViewById(R.id.apps_timer).setOnClickListener(v -> SleepTimer.show(this));
            updateSleepTimerIndicator();
            layout.findViewById(R.id.apps_wifi).setOnClickListener(v -> {
                try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
                catch (RuntimeException error) { Toast.makeText(this, getString(R.string.network_unavailable), Toast.LENGTH_SHORT).show(); }
            });
            layout.findViewById(R.id.apps_bluetooth).setOnClickListener(v -> openBluetooth());
            EditText search = layout.findViewById(R.id.apps_search); search.setTypeface(font); search.setText(query);
            LinearLayout list = layout.findViewById(R.id.apps_grid);
            search.setShowSoftInputOnFocus(false);
            search.setOnClickListener(v -> { search.requestFocus(); ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); });
            search.setOnEditorActionListener((v, actionId, event) -> { hideKeyboard(); if (libraryEntry != null) libraryEntry.requestFocus(); return true; });
            search.setOnKeyListener((v, key, event) -> {
                if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == android.view.KeyEvent.ACTION_DOWN && libraryEntry != null) { hideKeyboard(); libraryEntry.requestFocus(); return true; }
                return false;
            });
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) { query = s.toString(); renderList(list, allApps, false); }
                public void afterTextChanged(Editable e) {}
            });
            renderList(list, allApps, !isSidebarFocused());
        } else {
            ScrollView scroll = new ScrollView(this); scroll.setFocusable(false); scroll.setClipToPadding(false);
            content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
            homeGrid = list;
            renderList(list, allApps, !isSidebarFocused());
        }
    }
    private void openPage(String target) {
        if (page.equals(target)) { updateHomeGreeting(); return; }
        hideKeyboard();
        int direction = pageOrder(target) > pageOrder(page) ? 1 : -1;
        page = target; query = ""; focusedPackage = pageFocus.get(target);
        findViewById("home".equals(target) ? R.id.nav_home : "apps".equals(target) ? R.id.nav_apps : R.id.nav_settings).requestFocus();
        LinearLayout cached = pageViews.get(target);
        content = pageMotion.prepare(content, cached, motionEnabled(), direction);
        pageViews.put(target, content);
        if (cached == null) renderCurrentPage();
        else {
            updateBackground();
            findViewById(R.id.nav_home).setSelected("home".equals(page));
            findViewById(R.id.nav_apps).setSelected("apps".equals(page));
            findViewById(R.id.nav_settings).setSelected("settings".equals(page));
            EditText search = content.findViewById(R.id.apps_search);
            if (search != null && search.length() > 0) search.setText("");
            View entry = findPageTile(content, focusedPackage);
            if (entry == null) entry = findPageTile(content, null);
            if (entry != null) {
                findViewById("home".equals(page) ? R.id.nav_home : R.id.nav_apps).setNextFocusRightId(entry.getId());
                if (search != null) { libraryEntry = entry; search.setNextFocusDownId(entry.getId()); }
                if("home".equals(page)) linkHomeHeaderDown(entry.getId());
            }
            restoreCachedArtwork(content);
            updateHomeGreeting();
        }
        pageMotion.play(content, direction);
        updateSleepTimerIndicator();
    }
    private View findPageTile(android.view.ViewGroup root, String pkg) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View view = root.getChildAt(i);
            if (view.isFocusable() && view.getTag() instanceof String && (pkg == null || pkg.equals(view.getTag()))) return view;
            if (view instanceof android.view.ViewGroup) {
                View found = findPageTile((android.view.ViewGroup) view, pkg); if (found != null) return found;
            }
        }
        return null;
    }
    private void restoreCachedArtwork(android.view.ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getId() == R.id.app_icon && child instanceof ImageView && ((ImageView) child).getDrawable() == null) {
                ImageView icon = (ImageView) child;
                Object tag = ((View) icon.getParent()).getTag();
                if (tag instanceof String) {
                    String pkg = (String) tag;
                    Drawable.ConstantState state = icons.get(pkg);
                    if (state != null) bindArtwork(icon, state);
                    else worker.execute(() -> {
                        Drawable.ConstantState loaded = loadArtwork(pkg);
                        runOnUiThread(() -> { if (!destroyed && icon.isAttachedToWindow()) bindArtwork(icon, loaded); });
                    });
                }
            }
            if (child instanceof android.view.ViewGroup) restoreCachedArtwork((android.view.ViewGroup) child);
        }
    }
    private void linkHomeHeaderDown(int tileId) {
        View timer=findViewById(R.id.home_power);
        if(timer!=null) timer.setNextFocusDownId(tileId);
        View wifi=findViewById(R.id.home_wifi);
        if(wifi!=null) wifi.setNextFocusDownId(tileId);
        View bluetooth=findViewById(R.id.home_bluetooth);
        if(bluetooth!=null) bluetooth.setNextFocusDownId(tileId);
        View sleepTimer=findViewById(R.id.home_timer);
        if(sleepTimer!=null) sleepTimer.setNextFocusDownId(tileId);
    }
    private int homeHeaderForColumn(int column) {
        if (column == 0) return R.id.home_power;
        if (column < 3) return R.id.home_wifi;
        if (column == 3) return R.id.home_bluetooth;
        return R.id.home_timer;
    }
    private int pageOrder(String value) { return "home".equals(value) ? 0 : "apps".equals(value) ? 1 : 2; }
    private void updateHomeGreeting() {
        if (content == null) return;
        TextView greeting = content.findViewById(R.id.home_greeting);
        if (greeting == null) return;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 6 ? getString(R.string.greeting_night) : hour < 12 ? getString(R.string.greeting_morning) : hour < 18 ? getString(R.string.greeting_day) : getString(R.string.greeting_evening));
    }
    private void renderList(LinearLayout list, List<AppItem> apps, boolean takeFocus) {
        list.removeAllViews();
        // Card shadows and focused scale extend beyond a cell's layout bounds.
        // Keep clipping only at the scrolling viewport, not at each grid cell/row.
        list.setClipChildren(false);
        list.setClipToPadding(false);
        final int renderGeneration = generation;
        List<String> favorites = getFavorites();
        if ("home".equals(page)) {
            Map<String, AppItem> available = new LinkedHashMap<>();
            for (AppItem app : apps) available.put(app.pkg, app);
            List<AppItem> ordered = new ArrayList<>();
            for (String pkg : favorites) if (available.containsKey(pkg)) ordered.add(available.get(pkg));
            apps = ordered;
        }
        View first = null, restore = null;
        LinearLayout row = null;
        List<View> tiles = new ArrayList<>();
        int count = 0;
        for (AppItem app : apps) {
            if ("home".equals(page) && !favorites.contains(app.pkg)) continue;
            if ("apps".equals(page) && !app.label.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) continue;
            if (count % 5 == 0) {
                row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false); row.setClipToPadding(false); list.addView(row);
            }
            LinearLayout tile = (LinearLayout) getLayoutInflater().inflate(R.layout.item_app, row, false);
            tile.setClipChildren(false);
            tile.setClipToPadding(false);
            tile.setId(View.generateViewId());
            tile.setContentDescription(app.label);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(124), 1);
            tileParams.setMargins(dp(5), dp(8), dp(5), dp(8)); row.addView(tile, tileParams);
            ImageView icon = tile.findViewById(R.id.app_icon);
            View artworkFrame = tile.findViewById(R.id.app_artwork);
            artworkFrame.setTag(app.pkg);
            icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12)); }
            });
            icon.setClipToOutline(true);
            artworkFrame.setOutlineProvider(icon.getOutlineProvider());
            artworkFrame.setClipToOutline(true); artworkFrame.setElevation(dp(2));
            TextView name = tile.findViewById(R.id.app_name); name.setText(app.label); name.setTypeface(Typeface.create(font, Typeface.BOLD));
            tile.setTag(app.pkg);
            Drawable.ConstantState cachedIcon = icons.get(app.pkg);
            if (cachedIcon != null) bindArtwork(icon, cachedIcon);
            else worker.execute(() -> {
                if (destroyed || generation != renderGeneration) return;
                Drawable.ConstantState state = loadArtwork(app.pkg);
                Drawable.ConstantState loaded = state;
                runOnUiThread(() -> { if (!destroyed && generation == renderGeneration) icon.post(() -> { if (!destroyed && generation == renderGeneration) bindArtwork(icon, loaded); }); });
            });
            FocusMotion focusMotion = new FocusMotion(tile, artworkFrame);
            tile.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    focusedPackage = app.pkg; pageFocus.put(page, app.pkg);
                    if("home".equals(page)) linkHomeHeaderDown(view.getId());
                    int nav = "home".equals(page) ? R.id.nav_home : R.id.nav_apps;
                    findViewById(nav).setNextFocusRightId(tile.getId());
                    if ("apps".equals(page)) { libraryEntry = tile; View search = findViewById(R.id.apps_search); if (search != null) search.setNextFocusDownId(tile.getId()); }
                }
                focusMotion.setFocused(hasFocus, motionEnabled());
            });
            tile.setOnClickListener(v -> openApp(app.pkg,artworkFrame));
            tile.setOnLongClickListener(v -> { showFavoriteMenu(app, tile); return true; });
            if (first == null) first = tile;
            if (app.pkg.equals(focusedPackage)) restore = tile;
            tiles.add(tile);
            count++;
        }
        for (int i = 0; i < tiles.size(); i++) {
            View tile = tiles.get(i);
            tile.setNextFocusLeftId(i % 5 == 0 ? ("home".equals(page) ? R.id.nav_home : R.id.nav_apps) : tiles.get(i - 1).getId());
            tile.setNextFocusRightId(i % 5 == 4 || i + 1 == tiles.size() ? tile.getId() : tiles.get(i + 1).getId());
            if (i >= 5) tile.setNextFocusUpId(tiles.get(i - 5).getId());
            else if ("apps".equals(page)) tile.setNextFocusUpId(R.id.apps_search);
            else if ("home".equals(page)) tile.setNextFocusUpId(homeHeaderForColumn(i % 5));
            tile.setNextFocusDownId(i + 5 < tiles.size() ? tiles.get(i + 5).getId() : tile.getId());
        }
        if (row != null && count % 5 != 0) {
            for (int i = count % 5; i < 5; i++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(0, 1, 1);
                space.setMargins(dp(5), 0, dp(5), 0); row.addView(spacer, space);
            }
        }
        if ("apps".equals(page)) libraryEntry = restore != null ? restore : first;
        if (first != null) {
            int navId = "home".equals(page) ? R.id.nav_home : R.id.nav_apps;
            View target = restore != null ? restore : first;
            findViewById(navId).setNextFocusRightId(target.getId());
            if("home".equals(page)) linkHomeHeaderDown(target.getId());
        }
        if (first == null && !isSidebarFocused()) findViewById("home".equals(page) ? R.id.nav_home : R.id.apps_search).requestFocus();
        if (takeFocus && restore != null) restore.requestFocus();
        else if (takeFocus && first != null) first.requestFocus();
        else {
            TextView empty = new TextView(this);
            empty.setText("home".equals(page) ? getString(R.string.favorites_empty) : getString(R.string.search_empty));
            empty.setTypeface(font); empty.setTextSize(16);
            empty.setTextColor(0xffd8e1ef); empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            if (first == null) list.addView(empty);
        }
    }
    private void updateBackground() {
        String selected=wallpaper();
        WallpaperCatalog.Entry entry=WallpaperCatalog.find(this,selected);
        boolean ready=selected.equals(loadedWallpaper) && sharpWallpaper!=null;
        // Keep the previous scene during asynchronous decoding instead of flashing a placeholder.
        if(entry.photo!=0 && !ready && sharpWallpaper!=null) return;
        String key=page+":"+selected+":"+("home".equals(page) ? getPreferences(MODE_PRIVATE).getInt("dim",RECOMMENDED_DIM) : 0)+":"+ready;
        if(key.equals(backgroundKey)) return;
        Drawable next;
        if(ready) {
            android.graphics.Bitmap image="home".equals(page) ? sharpWallpaper : "settings".equals(page) ? settingsWallpaper : appsWallpaper;
            android.graphics.drawable.BitmapDrawable photo=new android.graphics.drawable.BitmapDrawable(getResources(),image);
            photo.setGravity(Gravity.FILL); photo.setFilterBitmap(true);
            if("home".equals(page)) {
                int alpha=Math.round(getPreferences(MODE_PRIVATE).getInt("dim",RECOMMENDED_DIM)*2.55f);
                next=new android.graphics.drawable.LayerDrawable(new Drawable[]{photo,new android.graphics.drawable.ColorDrawable(Color.argb(alpha,8,15,30))});
            } else next=photo;
        } else if("plain".equals(selected)) next=new android.graphics.drawable.ColorDrawable(getColor(R.color.cd_background));
        else if(entry.photo!=0) {
            next=new android.graphics.drawable.LayerDrawable(new Drawable[]{getDrawable(entry.thumbnail),new android.graphics.drawable.ColorDrawable(0x99080f1e)});
        } else next=getDrawable(R.drawable.blue_wallpaper).mutate();
        backgroundMotion.show(next,motionEnabled()); backgroundKey=key;
    }
    private List<String> getFavorites() {
        if (movingOrder != null) return new ArrayList<>(movingOrder);
        List<String> items = new ArrayList<>();
        String saved = getPreferences(MODE_PRIVATE).getString("favorite_order", null);
        if (saved == null) {
            items.addAll(getPreferences(MODE_PRIVATE).getStringSet("favorites", java.util.Collections.emptySet()));
            java.util.Collections.sort(items);
            return items;
        }
        try { JSONArray array = new JSONArray(saved); for (int i = 0; i < array.length(); i++) items.add(array.getString(i)); }
        catch (Exception ignored) { items.clear(); }
        return items;
    }
    private void saveFavorites(List<String> items) {
        getPreferences(MODE_PRIVATE).edit().putString("favorite_order", new JSONArray(items).toString()).apply();
        if (!"home".equals(page)) {
            LinearLayout old = pageViews.remove("home"); if (old != null) pageMotion.discardCached(old);
        }
    }
    private void beginMove(String pkg) {
        if (!"home".equals(page)) openPage("home");
        movingOrder = getFavorites(); movingPackage = pkg; moveConfirmArmed=false;
        focusedPackage = pkg;
        for (View tile : moveTiles()) if (pkg.equals(tile.getTag())) {
            tile.requestFocus(); tile.setActivated(true);
            TextView name=tile.findViewById(R.id.app_name);
            name.setTag(name.getText()); name.setText("‹  " + name.getText() + "  ›");
        }
        Toast.makeText(this, R.string.move_help, Toast.LENGTH_LONG).show();
    }
    private List<View> moveTiles() {
        List<View> result = new ArrayList<>();
        if (homeGrid != null) for (int r=0; r<homeGrid.getChildCount(); r++) {
            View row = homeGrid.getChildAt(r);
            if (row instanceof LinearLayout) for (int c=0; c<((LinearLayout)row).getChildCount(); c++) {
                View tile=((LinearLayout)row).getChildAt(c);
                if (tile.getTag() instanceof String) result.add(tile);
            }
        }
        return result;
    }
    private void finishMove(boolean commit) {
        List<String> order=movingOrder;
        movingOrder=null; movingPackage=null; moveLayoutGeneration++;
        for(View tile:moveTiles()) {
            tile.animate().cancel(); tile.setTranslationX(0); tile.setTranslationY(0);
            tile.setActivated(false);
            TextView name=tile.findViewById(R.id.app_name);
            if(name.getTag() instanceof CharSequence) { name.setText((CharSequence)name.getTag()); name.setTag(null); }
        }
        if(commit && order!=null) saveFavorites(order);
        else if(homeGrid!=null && "home".equals(page)) renderList(homeGrid,allApps,true);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if(movingPackage==null) return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        boolean direction=key==21 || key==22 || key==19 || key==20;
        boolean done=key==23 || key==66, cancel=key==4;
        if(!direction && !done && !cancel) return super.dispatchKeyEvent(event);
        if(event.getAction()==android.view.KeyEvent.ACTION_UP) {
            if(done && moveConfirmArmed) finishMove(true);
            else if(cancel) finishMove(false);
        } else if(done) { moveConfirmArmed=true;
        } else if(direction) {
            int delta=key==21?-1:key==22?1:key==19?-5:5;
            moveSelected(delta);
        }
        return true;
    }
    private void moveSelected(int delta) {
        List<View> tiles=moveTiles();
        int from=-1;
        for(int i=0;i<tiles.size();i++) if(movingPackage.equals(tiles.get(i).getTag())) from=i;
        int to=from+delta;
        if(from<0 || to<0 || to>=tiles.size()) return;
        Map<View,int[]> positions=new LinkedHashMap<>();
        for(View tile:tiles) { int[] xy=new int[2]; tile.getLocationOnScreen(xy); positions.put(tile,xy); tile.animate().cancel(); }
        View selected=tiles.get(from);
        java.util.Collections.swap(tiles,from,to);
        int a=movingOrder.indexOf(selected.getTag()), b=movingOrder.indexOf(tiles.get(from).getTag());
        java.util.Collections.swap(movingOrder,a,b);
        renderingPage=true;
        // Park focus on the undecorated page while the selected view is detached.
        // Otherwise Android briefly chooses a toolbar/sidebar control and fades its ring.
        boolean pageFocusable=content.isFocusable();
        int descendants=content.getDescendantFocusability();
        content.setFocusable(true);
        content.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        content.requestFocus();
        try {
        for(View tile:tiles) ((LinearLayout)tile.getParent()).removeView(tile);
        homeGrid.removeAllViews();
        LinearLayout row=null;
        for(int i=0;i<tiles.size();i++) {
            if(i%5==0) { row=new LinearLayout(this); row.setClipChildren(false); row.setClipToPadding(false); homeGrid.addView(row); }
            View tile=tiles.get(i); row.addView(tile);
            tile.setNextFocusRightId(i%5==4 || i+1==tiles.size()?tile.getId():tiles.get(i+1).getId());
            tile.setNextFocusLeftId(i%5==0?R.id.nav_home:tiles.get(i-1).getId());
            tile.setNextFocusUpId(i>=5?tiles.get(i-5).getId():homeHeaderForColumn(i%5));
            tile.setNextFocusDownId(i+5<tiles.size()?tiles.get(i+5).getId():tile.getId());
        }
        if(row!=null) for(int i=tiles.size()%5;i>0 && i<5;i++) {
            View spacer=new View(this); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,1,1); p.setMargins(dp(5),0,dp(5),0); row.addView(spacer,p);
        }
        selected.requestFocus(); linkHomeHeaderDown(selected.getId());
        } finally {
            content.setDescendantFocusability(descendants);
            content.setFocusable(pageFocusable);
            renderingPage=false;
        }
        final int layoutGeneration=++moveLayoutGeneration;
        homeGrid.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                homeGrid.getViewTreeObserver().removeOnPreDrawListener(this);
                if(layoutGeneration!=moveLayoutGeneration) return true;
                for(View tile:tiles) {
                    tile.setTranslationX(0); tile.setTranslationY(0);
                    int[] now=new int[2]; tile.getLocationOnScreen(now); int[] old=positions.get(tile);
                    if(motionEnabled()) {
                        tile.setTranslationX(old[0]-now[0]); tile.setTranslationY(old[1]-now[1]);
                        tile.animate().translationX(0).translationY(0).setDuration(240)
                            .setInterpolator(new android.view.animation.PathInterpolator(.22f,.61f,.36f,1)).start();
                    }
                }
                return true;
            }
        });
    }
    private void showFavoriteMenu(AppItem app, View anchor) {
        List<String> favorites = getFavorites(); int index = favorites.indexOf(app.pkg);
        android.app.Dialog dialog = new android.app.Dialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_app_actions, null);
        TextView title = sheet.findViewById(R.id.action_title); title.setText(app.label); title.setTypeface(font);
        ImageView actionIcon = sheet.findViewById(R.id.action_icon);
        bindArtwork(actionIcon, icons.get(app.pkg));
        // A banner must fit inside the compact menu thumbnail, never crop its logo.
        actionIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        actionIcon.setPadding(0, 0, 0, 0);
        worker.execute(() -> {
            try {
                Drawable nativeIcon = getPackageManager().getApplicationIcon(app.pkg);
                runOnUiThread(() -> { if (!destroyed && dialog.isShowing()) actionIcon.setImageDrawable(nativeIcon); });
            } catch (PackageManager.NameNotFoundException ignored) { }
        });
        View artwork = anchor.findViewById(R.id.app_artwork);
        if (artwork != null) artwork.findViewById(R.id.focus_outline).setAlpha(0);
        LinearLayout rows = sheet.findViewById(R.id.action_rows);
        List<Integer> choices = new ArrayList<>();
        choices.add(index < 0 ? R.string.add_favorite : R.string.remove_favorite);
        if (index >= 0 && favorites.size() > 1) choices.add(R.string.move_app);
        choices.add(R.string.app_info); choices.add(R.string.cancel);
        for (int choice : choices) {
            Button button = action(getString(choice), () -> {
                dialog.dismiss();
                if (choice == R.string.app_info) {
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + app.pkg))); }
                    catch (RuntimeException error) { Toast.makeText(this, getString(R.string.info_unavailable), Toast.LENGTH_SHORT).show(); }
                    return;
                }
                if (choice == R.string.cancel) return;
                if (choice == R.string.move_app) { anchor.post(() -> beginMove(app.pkg)); return; }
                if (choice == R.string.add_favorite) favorites.add(app.pkg);
                else if (choice == R.string.remove_favorite) favorites.remove(app.pkg);

                saveFavorites(favorites); focusedPackage = app.pkg; pageFocus.put(page, app.pkg);
                if ("home".equals(page)) renderPage();
            });
            int symbol = (choice == R.string.add_favorite || choice == R.string.remove_favorite) ? R.drawable.ic_heart : (choice == R.string.move_app) ? R.drawable.ic_move : choice == R.string.app_info ? R.drawable.ic_info : R.drawable.ic_close;
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(symbol, 0, 0, 0); button.setCompoundDrawablePadding(dp(12));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48)); params.bottomMargin = dp(6); rows.addView(button, params);
        }
        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> { if (anchor.isAttachedToWindow()) { anchor.requestFocus(); if (artwork != null) artwork.findViewById(R.id.focus_outline).setAlpha(1); } });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); params.dimAmount = .45f; dialog.getWindow().setAttributes(params);
        }
        dialog.show(); dialog.getWindow().setLayout(dp(300), -2); rows.getChildAt(0).requestFocus();
    }
    private boolean motionEnabled() { return getPreferences(MODE_PRIVATE).getBoolean("animations", true) && android.animation.ValueAnimator.areAnimatorsEnabled(); }
    private void showSleepNow() {
        // Power is global. Dismiss the previous page before its confirmation appears.
        if (!"home".equals(page)) openPage("home");
        if (!SystemAccess.ready()) {
            Toast.makeText(this, R.string.sleep_access, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_now)
                .setMessage(R.string.sleep_now_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.sleep_now, (dialog, which) -> worker.execute(() -> {
                    try {
                        if (!SystemAccess.sleepDevice(this)) throw new IllegalStateException("Sleep rejected");
                    } catch (Exception error) {
                        runOnUiThread(() -> Toast.makeText(this, R.string.sleep_failed, Toast.LENGTH_LONG).show());
                    }
                }))
                .show();
    }
    void updateSleepTimerIndicator() {
        wallpaperTimer.removeCallbacks(sleepStatusTick);
        long remaining = SleepTimer.remaining(this);
        if (content != null) for (int id : new int[]{R.id.home_timer, R.id.apps_timer, R.id.settings_timer}) {
            TextView value = content.findViewById(id);
            if (value == null) continue;
            if (remaining > 0) {
                String countdown = SleepTimer.countdown(remaining);
                value.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                value.setText(countdown);
                value.setContentDescription(getString(R.string.sleep_countdown, countdown));
            } else {
                value.setText("");
                value.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_sleep_timer, 0, 0, 0);
                value.setContentDescription(getString(R.string.sleep_timer));
            }
        }
        if (remaining > 0 && wallpaperResumed && !destroyed) wallpaperTimer.postDelayed(sleepStatusTick, 1000);
    }
    private void updateShizukuStatus() {
        TextView status = findViewById(R.id.shizuku_status);
        if (status != null) status.setText(SystemAccess.ready() ? R.string.system_access_ready
                : SystemAccess.running() ? R.string.system_access_permission : R.string.shizuku_stopped_short);
    }
    private void openBluetooth() {
        // Prefer the platform contract; several TV OEMs do not implement it.
        if (tryBluetoothSettings(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) return;
        // AOSP TV's exported accessories screen, also verified on Xiaomi MiTV-AFMU1.
        // Restrict the OEM route to the known system Settings package.
        try {
            android.content.pm.ApplicationInfo tvSettings = getPackageManager()
                    .getApplicationInfo("com.android.tv.settings", 0);
            if ((tvSettings.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                Intent accessories = new Intent("android.settings.SLICE_SETTINGS")
                        .setClassName("com.android.tv.settings", "com.android.tv.settings.slice.SliceActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("slice_uri", "content://com.android.tv.settings.accessories.sliceprovider/general");
                if (tryBluetoothSettings(accessories)) return;
                if (SystemAccess.ready()) {
                    worker.execute(() -> {
                        boolean opened;
                        try { opened = SystemAccess.openBluetoothSettings(this); }
                        catch (Exception unavailable) { opened = false; }
                        if (!opened) runOnUiThread(() -> { if (!destroyed) openBluetoothFallback(); });
                    });
                    return;
                }
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) { }
        openBluetoothFallback();
    }
    private void openBluetoothFallback() {
        if (tryBluetoothSettings(new Intent(Settings.ACTION_SETTINGS))) {
            Toast.makeText(this, R.string.bluetooth_settings_hint, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.bluetooth_unavailable, Toast.LENGTH_SHORT).show();
    }
    private boolean tryBluetoothSettings(Intent intent) {
        try { startActivity(intent); return true; }
        catch (android.content.ActivityNotFoundException | SecurityException unavailable) { return false; }
    }
    private void openWifi() {
        try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
        catch (RuntimeException error) { Toast.makeText(this, getString(R.string.network_unavailable), Toast.LENGTH_SHORT).show(); }
    }
    private void hideKeyboard() { View view = getCurrentFocus(); if (view != null) ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0); }
    private void bindArtwork(ImageView icon, Drawable.ConstantState state) {
        Drawable artwork = state != null ? state.newDrawable(getResources()).mutate() : getPackageManager().getDefaultActivityIcon();
        boolean banner = artwork.getIntrinsicWidth() > artwork.getIntrinsicHeight() * 1.3f;
        icon.setPadding(banner ? 0 : dp(12), banner ? 0 : dp(8), banner ? 0 : dp(12), banner ? 0 : dp(8));
        icon.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER); icon.setImageDrawable(artwork);
    }
    @Override public void onBackPressed() {
        if (!"home".equals(page)) {
            openPage("home");
            View lastTile = findPageTile(content, pageFocus.get("home"));
            if (lastTile == null) lastTile = findPageTile(content, null);
            if (lastTile != null) lastTile.requestFocus();
            else findViewById(R.id.nav_home).requestFocus();
            return;
        }
        findViewById(R.id.nav_home).requestFocus();
    }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private static boolean sameApps(List<AppItem> left, List<AppItem> right) {
        if (left == null || left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!left.get(i).pkg.equals(right.get(i).pkg) || !left.get(i).label.equals(right.get(i).label)) return false;
        }
        return true;
    }
    private void openApp(String pkg, View artwork) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        if (intent == null) { Toast.makeText(this, getString(R.string.app_missing), Toast.LENGTH_SHORT).show(); return; }
        try {
            boolean animated=motionEnabled();
            if(!animated) intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent,AppLaunchMotion.options(artwork,animated));
            getPreferences(MODE_PRIVATE).edit().putString("last_opened_package",pkg).apply();
            launchedAppAwaitingReturn = pkg;
        }
        catch (RuntimeException error) { Toast.makeText(this, getString(R.string.app_launch_failed), Toast.LENGTH_SHORT).show(); }
    }
    @Override protected void onDestroy() {
        destroyed = true;
        wallpaperTimer.removeCallbacks(wallpaperRotationTick);
        if (pageMotion != null) pageMotion.cancel();
        if (backgroundMotion != null) backgroundMotion.cancel();
        worker.shutdownNow();
        wallpaperWorker.shutdownNow();
        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(systemPermissionResult);
        rikka.shizuku.Shizuku.removeBinderReceivedListener(systemBinderReady);
        rikka.shizuku.Shizuku.removeBinderDeadListener(systemBinderDead);
        super.onDestroy();
    }
}

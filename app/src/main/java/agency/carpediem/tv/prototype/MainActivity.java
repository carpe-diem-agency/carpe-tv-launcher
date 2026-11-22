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
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;

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
    private final CatalogRefresh catalogRefresh = new CatalogRefresh(worker, this::refreshApps);
    private final ExecutorService wallpaperWorker = Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private PageMotion pageMotion;
    private BackgroundMotion backgroundMotion;
    private BlueWallpaperMotion blueWallpaperMotion;
    private final Map<String, LinearLayout> pageViews = new LinkedHashMap<>();
    private volatile boolean destroyed;
    private String focusedPackage;
    private String pendingHomePackage;
    private String restoredHomeFavorite;
    private String launchedAppAwaitingReturn;
    private String movingPackage;
    private record MoveKey(int deviceId, int keyCode) { }
    private final Map<MoveKey, Long> moveKeyDownTimes = new HashMap<>();
    private int moveLayoutGeneration;
    private boolean moveConfirmArmed;
    private List<String> movingOrder;
    private LinearLayout homeGrid;
    private List<AppItem> displayedApps;
    private List<AppItem> allApps = new ArrayList<>();
    private boolean appsReady;
    private boolean appsLoadFailed;
    private String page = "home";
    private String query = "";
    private int restoredAppsScrollY = -1;
    private String restoredAppsFocusPackage;
    private ScrollView pendingAppsScroll;
    private android.view.ViewTreeObserver appsRestoreObserver;
    private android.view.ViewTreeObserver.OnPreDrawListener appsRestoreLayout;
    private Typeface font;
    private final Map<String, String> pageFocus = new java.util.HashMap<>();
    private volatile int generation;
    private View libraryEntry;
    private static final int RECOMMENDED_DIM = 60;
    private String settingsCategory = "appearance";
    private View settingsCategoryView;
    private String restoredSettingsRegion;
    private int restoredSettingsDetailIndex = -1;
    private final Map<String, TextView> auditLabels=new LinkedHashMap<>();
    private int auditGeneration,settingsDetailGeneration,settingsDialogRequest;
    private boolean authorsOpen;
    private final android.os.Handler auditTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean auditInFlight,auditRerunRequested,auditTickPending;
    private final Runnable auditTick=()->{auditTickPending=false;refreshApplicationAudit();};
    private long auditStarted,auditCompleted,auditApplied;
    private boolean systemOperation;
    private AlertDialog systemConfirmation;
    private android.app.Dialog settingsDialog;
    private View pendingModalFocus;
    private Runnable pendingFeedbackLaunch;
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
    // Artwork grows with display resolution; an entry-count limit can retain
    // tens of megabytes of 4K-sized banners even when few tiles are visible.
    private final LruCache<String, Drawable.ConstantState> icons =
            new LruCache<String, Drawable.ConstantState>(24 * 1024 * 1024) {
                @Override protected int sizeOf(String key, Drawable.ConstantState state) {
                    Drawable drawable = state.newDrawable();
                    if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        android.graphics.Bitmap bitmap =
                                ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                        return bitmap == null ? 1 : bitmap.getAllocationByteCount();
                    }
                    return 1;
                }
            };
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            catalogRefresh.request(true);
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
        if (state != null) {
            page=state.getString("page","home");
            settingsCategory=state.getString("category","appearance");
            query=state.getString("query", "");
            authorsOpen=state.getBoolean("authorsOpen",false);
            pageFocus.put("home", state.getString("homeFocus"));
            pageFocus.put("apps", state.getString("appsFocus"));
            focusedPackage=pageFocus.get(page);
            if ("home".equals(page)) restoredHomeFavorite = state.getString("homeFocusedTile");
            restoredAppsScrollY=state.getInt("appsScrollY", -1);
            restoredAppsFocusPackage=state.getString("appsFocusedTile");
            restoredSettingsRegion=state.getString("settingsFocusRegion");
            restoredSettingsDetailIndex=state.getInt("settingsDetailIndex", -1);
            if ("apps".equals(page)) {
                // The search editor can regain focus without reopening the keyboard.
                int mode = getWindow().getAttributes().softInputMode;
                getWindow().setSoftInputMode((mode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        }
        try { font = new Typeface.Builder(getAssets(), "montserrat.ttf").setFontVariationSettings("'wght' 600").build(); } catch (RuntimeException ignored) { font = Typeface.DEFAULT; }
        setContentView(R.layout.activity_launcher);
        content = findViewById(R.id.content);
        pageMotion = new PageMotion(findViewById(R.id.content_host));
        blueWallpaperMotion = findViewById(R.id.blue_wallpaper_motion);
        backgroundMotion = new BackgroundMotion(findViewById(R.id.scene_a), findViewById(R.id.scene_b),
                blueWallpaperMotion, (View) findViewById(R.id.sidebar).getParent());
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
        state.putString("query",query);
        state.putBoolean("authorsOpen",authorsOpen);
        state.putString("homeFocus",pageFocus.get("home"));
        state.putString("appsFocus",pageFocus.get("apps"));
        View current=getCurrentFocus();
        if ("home".equals(page)) {
            String favorite = restoredHomeFavorite;
            if (favorite == null && current != null && current.getTag() instanceof String && isInside(current, homeGrid)) {
                favorite = (String) current.getTag();
            }
            state.putString("homeFocusedTile", favorite);
        }
        state.putString("appsFocusedTile","apps".equals(page)
                ? (restoredAppsFocusPackage!=null ? restoredAppsFocusPackage
                        : current!=null && current.getTag() instanceof String ? (String)current.getTag() : null)
                : null);
        LinearLayout appsPage="apps".equals(page)?content:pageViews.get("apps");
        ScrollView appsScroll=appsPage!=null?appsPage.findViewById(R.id.apps_scroll):null;
        state.putInt("appsScrollY",restoredAppsScrollY>=0 ? restoredAppsScrollY
                : appsScroll==null ? -1 : appsScroll.getScrollY());
        state.putBoolean("languageFocus",languageChange || (getCurrentFocus()!=null && getCurrentFocus().getId()==R.id.language_choice));
        if ("settings".equals(page) && current != null) {
            View detail=content.findViewById(R.id.settings_detail);
            View categories=content.findViewById(R.id.settings_categories);
            if (isInside(current, detail)) {
                state.putString("settingsFocusRegion", "detail");
                state.putInt("settingsDetailIndex", focusableIndex(detail, current));
            } else if (isInside(current, categories)) {
                state.putString("settingsFocusRegion", "category");
            }
        }
        super.onSaveInstanceState(state);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused && wallpaperResumed) scheduleScreensaver(); else idleTimer.stop();
        if (focused) restorePendingModalFocus();
        if(focused) launchPendingFeedback();
        if (focused) {
            focusPendingHomeApp();
            restoreHomeFavoriteFocus();
        }
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
            if (focused && !renderingPage && settingsDialog == null && pendingModalFocus == null) openPage(target);
        });
        UiSounds.click(button, view -> {
            prepareTouchActivationFocus(view);
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
    private void prepareTouchActivationFocus(View view) {
        if(!view.isInTouchMode()) return;
        // Touch activation must establish the intended remote-focus anchor before
        // navigation or a modal changes the tree. Ignore transient default focus.
        boolean previousRendering=renderingPage;renderingPage=true;
        try {view.requestFocusFromTouch();}
        finally {renderingPage=previousRendering;}
    }
    private boolean isSidebarFocused() {
        View view = getCurrentFocus();
        return view != null && (view.getId() == R.id.nav_home
                || view.getId() == R.id.nav_apps || view.getId() == R.id.nav_settings);
    }
    @Override protected void onResume() {
        super.onResume();
        if (launchedAppAwaitingReturn != null) {
            String returnedPackage = launchedAppAwaitingReturn;
            launchedAppAwaitingReturn = null;
            // Returning to this Activity keeps the page and query used to launch the app.
            restoreOpenedTile(returnedPackage);
        }
        wallpaperResumed=true; scheduleWallpaperRotation(); scheduleScreensaver();
        updateBackground();
        blueWallpaperMotion.setResumed(true);
        updateHomeGreeting(); updateSaverCacheStatus(); updateSleepTimerIndicator();
        if("settings".equals(page) && "audit".equals(settingsCategory)) refreshApplicationAudit();
        UpdateManager.maybeCheck(this);
        TextView status = content.findViewById(R.id.home_status);
        if (status != null) status.setText(HomeChoice.status(this));
        TextView homeWarning = content.findViewById(R.id.home_remote_warning);
        if (homeWarning != null) {
            homeWarning.setText(HomeChoice.homeWarning(this));
            homeWarning.setVisibility(HomeChoice.needsRemoteHomeCheck(this) ? View.VISIBLE : View.GONE);
        }
        updateShizukuStatus();refreshDeviceMetrics();restorePendingAppsState();
        catalogRefresh.request(false);
    }
    @Override protected void onPause() {
        blueWallpaperMotion.setResumed(false);
        if (movingPackage != null) finishMove(false);
        wallpaperResumed=false;pendingFeedbackLaunch=null;cancelPendingAppsLayout();stopAuditRefresh();settingsDialogRequest++;wallpaperTimer.removeCallbacks(sleepStatusTick); wallpaperTimer.removeCallbacks(wallpaperRotationTick); idleTimer.stop();
        super.onPause();
    }
    private void scheduleScreensaver() {
        if (wallpaperResumed && hasWindowFocus()) idleTimer.activate(Screensaver.minutes(this) * 60000L);
        else idleTimer.stop();
    }
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        restoredHomeFavorite = null;
        idleTimer.reset();
    }
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
    private void refreshApps(boolean invalidateArtwork) {
        if (destroyed) return;
        if (invalidateArtwork) icons.evictAll();
        List<AppItem> apps;
        try {
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
            apps = new ArrayList<>(items.values());
            apps.sort(Comparator.comparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
            seedFirstRunFavorites(apps);
            // Home resumes after every app launch. Keep the persisted catalog quiet
            // when the PackageManager result has not actually changed.
            if (!sameApps(readCache(), apps)) writeCache(apps);
            runOnUiThread(() -> {
                if (invalidateArtwork && !destroyed) displayedApps = null;
                showApps(apps);
                // The on-disk catalog may be stale. Only the refreshed list can
                // rule out a favorite that was active in the saved task.
                if (!destroyed && restoredHomeFavorite != null && findPageTile(content, restoredHomeFavorite) == null) {
                    restoredHomeFavorite = null;
                }
            });
        } catch (RuntimeException error) {
            android.util.Log.w("CarpeTV", "Unable to refresh app catalog", error);
            runOnUiThread(() -> {
                if (invalidateArtwork && !destroyed) displayedApps = null;
                catalogLoadFailed();
            });
            return;
        }
        prefetchArtwork(apps);
    }
    private void catalogLoadFailed() {
        if(destroyed) return;
        appsLoadFailed=true;
        if(allApps.isEmpty()) {
            if("settings".equals(page)) invalidateInactiveCatalogPages();
            else {
                View search = content.findViewById(R.id.apps_search);
                renderCatalogState(search != null && search.hasFocus());
            }
        }
    }
    private void renderCatalogState(boolean focusSearch) {
        renderPage();
        if (focusSearch) {
            View search = content.findViewById(R.id.apps_search);
            if (search != null) search.requestFocus();
        }
    }
    private void invalidateInactiveCatalogPages() {
        LinearLayout cachedApps=pageViews.get("apps");
        ScrollView cachedScroll=cachedApps!=null?cachedApps.findViewById(R.id.apps_scroll):null;
        if(cachedScroll!=null&&restoredAppsScrollY<0) restoredAppsScrollY=cachedScroll.getScrollY();
        for(String name:new String[]{"home","apps"}) {
            if(name.equals(page)) continue;
            LinearLayout cached=pageViews.remove(name);
            if(cached!=null&&pageMotion!=null) pageMotion.discardCached(cached);
        }
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
            if (destroyed || Thread.currentThread().isInterrupted() || count++ >= 48
                    || icons.size() >= icons.maxSize() * 3 / 4) break;
            loadArtwork(app.pkg);
        }
    }
    private Drawable.ConstantState loadArtwork(String pkg) {
        Drawable.ConstantState state = icons.get(pkg);
        if (state == null) {
            PackageManager pm = getPackageManager();
            state = AppArtwork.load(getResources(), () -> pm.getApplicationBanner(pkg),
                    () -> pm.getApplicationIcon(pkg), pm::getDefaultActivityIcon);
            if (state != null) icons.put(pkg, state);
        }
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
        boolean pending = !appsReady || appsLoadFailed;
        appsReady = true;
        appsLoadFailed = false;
        if (sameApps(displayedApps, apps)) {
            if(pending&&apps.isEmpty()) {
                if("settings".equals(page)) invalidateInactiveCatalogPages();
                else renderPage();
            }
            return;
        }
        if (movingPackage != null) finishMove(false);
        displayedApps = new ArrayList<>(apps);
        allApps = new ArrayList<>(apps);
        // Either rendering Home or retaining Settings invalidates inactive pages.
        // Capture library context first; an outstanding restore takes precedence.
        LinearLayout cachedApps="apps".equals(page)?content:pageViews.get("apps");
        ScrollView cachedScroll=cachedApps!=null?cachedApps.findViewById(R.id.apps_scroll):null;
        if(cachedScroll!=null&&restoredAppsScrollY<0) restoredAppsScrollY=cachedScroll.getScrollY();
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
        ScrollView previousAppsScroll = "apps".equals(page)
                ? content.findViewById(R.id.apps_scroll) : null;
        if (previousAppsScroll != null && restoredAppsScrollY < 0)
            restoredAppsScrollY = previousAppsScroll.getScrollY();
        renderPage();
        String pendingPackage = pendingHomePackage;
        focusPendingHomeApp();
        boolean restoredAppFocus = restoreHomeFavoriteFocus() || (pendingPackage != null && pendingHomePackage == null);
        if (!restoredAppFocus && previousPackage != null) {
            View tile = findPageTile(content, previousPackage);
            if (tile == null) tile = findPageTile(content, null);
            if (tile != null) restoredAppFocus = tile.requestFocus();
            else if ("apps".equals(page)) {
                View search = content.findViewById(R.id.apps_search);
                if (search != null) restoredAppFocus = search.requestFocus();
            }
        }
        if (!restoredAppFocus && previousPackage == null && previousId != View.NO_ID) {
            View restored = findViewById(previousId);
            if (restored != null && restored.isShown() && restored.isFocusable()) restored.requestFocus();
        }
        restorePendingAppsState();
    }
    private void cancelPendingAppsLayout() {
        if(appsRestoreObserver!=null&&appsRestoreObserver.isAlive()&&appsRestoreLayout!=null)
            appsRestoreObserver.removeOnPreDrawListener(appsRestoreLayout);
        appsRestoreObserver=null;appsRestoreLayout=null;pendingAppsScroll=null;
    }
    private void restorePendingAppsState() {
        if(destroyed||!"apps".equals(page)||(!appsReady&&allApps.isEmpty())
                ||(restoredAppsScrollY<0&&restoredAppsFocusPackage==null)) return;
        ScrollView scroll=content.findViewById(R.id.apps_scroll);
        if(scroll==null||pendingAppsScroll==scroll) return;
        cancelPendingAppsLayout();pendingAppsScroll=scroll;
        appsRestoreObserver=scroll.getViewTreeObserver();
        appsRestoreLayout=()->{
            if(destroyed||!"apps".equals(page)||content.findViewById(R.id.apps_scroll)!=scroll) {
                cancelPendingAppsLayout();return true;
            }
            if(!scroll.isAttachedToWindow()||scroll.getHeight()==0||scroll.getChildCount()==0
                    ||scroll.getChildAt(0).getHeight()==0) return true;
            int y=restoredAppsScrollY;String pkg=restoredAppsFocusPackage;
            cancelPendingAppsLayout();
            if(pkg!=null) {View tile=findPageTile(content,pkg);if(tile!=null) tile.requestFocus();}
            if(y>=0) scroll.scrollTo(0,y);
            restoredAppsScrollY=-1;restoredAppsFocusPackage=null;
            return true;
        };
        appsRestoreObserver.addOnPreDrawListener(appsRestoreLayout);
        scroll.invalidate();
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
        restoreOpenedTile(pkg);
    }
    private void restoreOpenedTile(String pkg) {
        restoredHomeFavorite = null;
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

    private boolean restoreHomeFavoriteFocus() {
        if (restoredHomeFavorite == null || !"home".equals(page) || !hasWindowFocus()) return false;
        View tile = findPageTile(content, restoredHomeFavorite);
        if (tile != null && tile.isAttachedToWindow() && tile.requestFocus()) {
            restoredHomeFavorite = null;
            return true;
        }
        return false;
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
    static Button createActionButton(Context context, Typeface font, String value) {
        Button b = new Button(context); b.setText(value); b.setAllCaps(false); b.setTypeface(Typeface.create(font, Typeface.BOLD)); b.setTextColor(Color.WHITE);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        b.setTextSize(16); b.setPadding(padding, 0, padding, 0); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setBackgroundResource(R.drawable.control_background);
        return b;
    }
    private Button action(String value, Runnable callback) {
        Button b = createActionButton(this, font, value);
        configureStateFade(b);
        if (callback != null) UiSounds.click(b,v->{prepareTouchActivationFocus(v);callback.run();});
        return b;
    }
    private void showWelcome(boolean markSeen) {
        if (destroyed || isFinishing()) return;
        Runnable rememberChoice = () -> {
            if (markSeen) getPreferences(MODE_PRIVATE).edit().putBoolean("welcome_seen", true).commit();
        };
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28)); panel.setBackground(surface(getColor(R.color.cd_modal_surface), 20));
        TextView title = label(getString(R.string.welcome_title), 24, Color.WHITE);
        panel.addView(title);
        TextView message = label(getString(R.string.welcome_message), 15, getColor(R.color.cd_text_secondary));
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
        dialog.show(); UiSounds.watch(dialog); dialog.getWindow().setLayout(dialogWidth(510), -2); explore.requestFocus();
    }
    private void renderSettings() {
        View settings = getLayoutInflater().inflate(R.layout.page_settings, content, false); content.addView(settings);
        ((TextView) settings.findViewById(R.id.settings_heading)).setTypeface(font);
        ((TextView) settings.findViewById(R.id.settings_clock)).setTypeface(font);
        UiSounds.click(settings.findViewById(R.id.settings_power), v -> showSleepNow());
        UiSounds.click(settings.findViewById(R.id.settings_timer), v -> showSleepTimer());
        updateSleepTimerIndicator();
        UiSounds.click(settings.findViewById(R.id.settings_wifi), v -> openWifi());
        UiSounds.click(settings.findViewById(R.id.settings_bluetooth), v -> openBluetooth());
        LinearLayout categories = settings.findViewById(R.id.settings_categories);
        LinearLayout detail = settings.findViewById(R.id.settings_detail);
        String[][] entries = {{"appearance", getString(R.string.appearance)}, {"screensaver", getString(R.string.saver_title)}, {"favorites", getString(R.string.favorites)}, {"audit", getString(R.string.audit_title)}, {"device", getString(R.string.device)}, {"about", getString(R.string.about)}};
        for (String[] entry : entries) {
            Button category = action(entry[1], null);
            UiSounds.click(category, v -> {
                prepareTouchActivationFocus(v);
                settingsCategory = entry[0]; settingsCategoryView = v;
                for (int i = 0; i < categories.getChildCount(); i++)
                    categories.getChildAt(i).setSelected(categories.getChildAt(i) == v);
                showSettingsDetail(detail, entry[0]);
                findViewById(R.id.nav_settings).setNextFocusRightId(category.getId());
                View target=settingsEntryFocus(detail,entry[0]);
                if(target!=null) {category.setNextFocusRightId(target.getId());target.requestFocus();}
            });
            int symbol = entry[0].equals("audit") ? R.drawable.ic_apps : entry[0].equals("screensaver") ? R.drawable.ic_screensaver : entry[0].equals("appearance") ? R.drawable.ic_monitor : entry[0].equals("favorites") ? R.drawable.ic_heart : entry[0].equals("device") ? R.drawable.ic_tv_device : R.drawable.ic_info;
            android.graphics.drawable.Drawable categoryIcon=getDrawable(symbol).mutate();
            categoryIcon.setBounds(0,0,dp(22),dp(22));
            category.setCompoundDrawablesRelative(categoryIcon,null,null,null); category.setCompoundDrawablePadding(dp(12));
            category.setSingleLine(false);
            category.setMinHeight(dp(56));
            category.setTextSize(14);
            category.setId(View.generateViewId()); category.setSelected(entry[0].equals(settingsCategory));
            category.setNextFocusLeftId(R.id.nav_settings);
            category.setOnKeyListener((v,key,event)->{
                if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT && event.getAction()==android.view.KeyEvent.ACTION_DOWN) {
                    ((ScrollView) settings.findViewById(R.id.settings_scroll)).scrollTo(0,0);
                    View target=settingsEntryFocus(detail, entry[0]);if(target!=null) target.requestFocus();return true;
                }return false;
            });
            category.setOnFocusChangeListener((v, focused) -> {
                if (focused && !renderingPage) {
                    settingsCategoryView = category;
                    findViewById(R.id.nav_settings).setNextFocusRightId(category.getId());
                    if (!settingsCategory.equals(entry[0])) { settingsCategory = entry[0]; showSettingsDetail(detail, entry[0]); ((ScrollView) settings.findViewById(R.id.settings_scroll)).scrollTo(0,0); }
                    for (int i = 0; i < categories.getChildCount(); i++) categories.getChildAt(i).setSelected(categories.getChildAt(i) == category);
                    View target = settingsEntryFocus(detail, entry[0]); if (target != null) category.setNextFocusRightId(target.getId());
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT); params.bottomMargin = dp(12); categories.addView(category, params);
            if (entry[0].equals(settingsCategory)) settingsCategoryView = category;
        }
        for (int i = 0; i < categories.getChildCount(); i++) {
            View category = categories.getChildAt(i);
            category.setNextFocusUpId(categories.getChildAt(Math.max(0, i - 1)).getId());
            category.setNextFocusDownId(categories.getChildAt(Math.min(categories.getChildCount() - 1, i + 1)).getId());
        }
        showSettingsDetail(detail, settingsCategory);
        if (settingsCategoryView != null) {
            View first = settingsEntryFocus(detail, settingsCategory); if (first != null) settingsCategoryView.setNextFocusRightId(first.getId());
            findViewById(R.id.nav_settings).setNextFocusRightId(settingsCategoryView.getId());
        }
        if (restoredSettingsRegion != null) {
            String region=restoredSettingsRegion;
            int index=restoredSettingsDetailIndex;
            restoredSettingsRegion=null;
            restoredSettingsDetailIndex=-1;
            settings.post(() -> {
                if (!"settings".equals(page) || !settings.isAttachedToWindow() || restoreLanguageFocus) return;
                View target="detail".equals(region) ? focusableAt(detail,index) : settingsCategoryView;
                if (target == null && "detail".equals(region)) target=settingsEntryFocus(detail,settingsCategory);
                if (target != null) target.requestFocus();
            });
        }
    }
    private void renderApplicationAudit(LinearLayout detail) {
        detail.addView(label(getString(R.string.audit_title),24,Color.WHITE));
        int[] names={R.string.audit_xiaomi_analytics,R.string.audit_xiaomi_statistics,R.string.audit_xiaomi_tvplus,R.string.audit_google_tv};
        for(int i=0;i<AppAudit.PACKAGES.length;i++) {
            final String pkg=AppAudit.PACKAGES[i],name=getString(names[i]);
            View row=getLayoutInflater().inflate(R.layout.audit_app_row,detail,false);
            row.setTag("audit:"+pkg);UiSounds.click(row,v->showAuditActions(pkg));
            TextView title=row.findViewById(R.id.audit_app_name);title.setText(name);title.setTypeface(font);
            TextView state=row.findViewById(R.id.audit_app_state);state.setTypeface(font);
            state.setText(R.string.audit_checking);state.setTag("audit-state:"+pkg);
            row.setContentDescription(name+". "+getString(R.string.audit_checking));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(10);detail.addView(row,size);
            auditLabels.put(pkg,state);configureStateFade(row);
        }
        addSettingAction(detail,getString(R.string.audit_all_apps),this::openAppManagement);
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.audit_manage_apps);
        refreshApplicationAudit();
    }
    private void openAppManagement() {
        for (String action : new String[]{Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS,
                Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (RuntimeException unavailable) {
                // TV firmware varies in which settings actions it exposes.
            }
        }
        Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show();
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
        showSettingChoices(R.string.audit_title,options,-1,choice->{if(choice==0)confirmSystemAction(disable?"disable":"restore",pkg);else if(choice==1)openApplicationDetails(pkg);});
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
        dialog.show(); UiSounds.watch(dialog);
        dialog.getWindow().setLayout(dialogWidth(600), -2);
        primary.requestFocus();
        return dialog;
    }
    private void confirmSystemAction(String operation,String pkg) {
        if(systemOperation || (systemConfirmation!=null && systemConfirmation.isShowing()))return;
        int text=operation.equals("stock")?R.string.stock_restore_warning:operation.equals("disable")?R.string.system_disable_warning:R.string.system_restore_warning;
        systemConfirmation=new AlertDialog.Builder(this).setTitle(operation.equals("stock")?R.string.stock_restore:R.string.system_confirm_title)
            .setMessage(getString(text)+(pkg==null?"":"\n\n"+pkg)).setNegativeButton(R.string.cancel,UiSounds.dialogClick(this, null))
            .setPositiveButton(R.string.system_continue,UiSounds.dialogClick(this, (dialog,which)->runSystemAction(operation,pkg))).create();
        systemConfirmation.setOnDismissListener(dialog->{if(systemConfirmation==dialog)systemConfirmation=null;});
        systemConfirmation.show(); UiSounds.watch(systemConfirmation);
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
    private boolean auditActive() {
        return !destroyed&&wallpaperResumed&&"settings".equals(page)&&"audit".equals(settingsCategory)&&!auditLabels.isEmpty();
    }
    private void stopAuditRefresh() {
        auditTimer.removeCallbacks(auditTick);auditTickPending=false;auditRerunRequested=false;auditGeneration++;
    }
    private void refreshApplicationAudit() {
        if(!auditActive()) return;
        auditTimer.removeCallbacks(auditTick);auditTickPending=false;
        if(auditInFlight) {auditRerunRequested=true;return;}
        auditInFlight=true;auditRerunRequested=false;auditStarted++;
        final int ticket=auditGeneration;
        worker.execute(()->{
            AppAudit.Snapshot read;
            try {read=AppAudit.read(this);}catch(RuntimeException unavailable) {read=null;}
            final AppAudit.Snapshot snapshot=read;
            runOnUiThread(()->{
                auditInFlight=false;auditCompleted++;
                if(snapshot!=null&&ticket==auditGeneration&&auditActive()) {
                    for(String pkg:AppAudit.PACKAGES) {
                        TextView status=auditLabels.get(pkg);if(status==null||!status.isAttachedToWindow()) continue;
                        Integer state=snapshot.states.get(pkg);
                        String text=getString(state==null||state==0?R.string.audit_unavailable:state==1?R.string.audit_enabled:R.string.audit_disabled);
                        if(!text.contentEquals(status.getText())) status.setText(text);
                        status.setTextColor(state!=null&&state==2?0xff9adfc6:getColor(R.color.cd_text_secondary));
                        View row=(View)status.getParent();TextView title=row.findViewById(R.id.audit_app_name);
                        String description=title.getText()+". "+text;
                        if(!description.contentEquals(row.getContentDescription()==null?"":row.getContentDescription())) row.setContentDescription(description);
                    }
                    auditApplied++;
                }
                if(!auditActive()) return;
                if(auditRerunRequested||ticket!=auditGeneration) {auditRerunRequested=false;refreshApplicationAudit();}
                else {auditTickPending=true;auditTimer.postDelayed(auditTick,5000);}
            });
        });
    }
    static final class AuditRefreshStats {
        final long started,completed,applied;
        final boolean active,inFlight,pendingTick,rerunRequested;
        AuditRefreshStats(MainActivity activity) {
            started=activity.auditStarted;completed=activity.auditCompleted;applied=activity.auditApplied;
            active=activity.auditActive();inFlight=activity.auditInFlight;pendingTick=activity.auditTickPending;rerunRequested=activity.auditRerunRequested;
        }
    }
    AuditRefreshStats auditRefreshStats() {return new AuditRefreshStats(this);}
    private void showSettingsDetail(LinearLayout detail, String category) {
        boolean previousRendering=renderingPage;renderingPage=true;
        try {
        stopAuditRefresh();settingsDetailGeneration++;settingsDialogRequest++;
        if(!"about".equals(category)) authorsOpen=false;
        detail.removeAllViews();auditLabels.clear();
        if ("audit".equals(category)) { renderApplicationAudit(detail);
        } else if ("appearance".equals(category)) {
            View appearance = getLayoutInflater().inflate(R.layout.appearance_controls, detail, false); detail.addView(appearance);
            for(int heading:new int[]{R.id.appearance_heading,R.id.interface_heading,R.id.sounds_heading})
                ((TextView)appearance.findViewById(heading)).setTypeface(font);
            TextView chosen = appearance.findViewById(R.id.wallpaper_label); chosen.setTypeface(font); chosen.setText(getString(R.string.selected_prefix) + wallpaperName());
            LinearLayout choices=appearance.findViewById(R.id.wallpaper_choices);
            for (WallpaperCatalog.Entry entry:WallpaperCatalog.entries(this)) {
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cardSize=new LinearLayout.LayoutParams(dp(205),-2); cardSize.setMarginEnd(dp(12)); choices.addView(card,cardSize);
                android.widget.ImageButton preview=new android.widget.ImageButton(this); preview.setId(entry.viewId); preview.setTag(entry);
                preview.setBackgroundResource(R.drawable.control_background); preview.setPadding(0,0,0,0);
                if (entry.thumbnail!=0) preview.setImageResource(entry.thumbnail); else preview.setImageDrawable(new android.graphics.drawable.ColorDrawable(getColor(R.color.cd_background)));
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP); preview.setForeground(getDrawable(R.drawable.wallpaper_foreground));
                preview.setContentDescription(getString(entry.title)); card.addView(preview,new LinearLayout.LayoutParams(-1,dp(116)));
                preview.setOutlineProvider(new android.view.ViewOutlineProvider() { @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(12)); } });
                preview.setClipToOutline(true); preview.setSelected(entry.key.equals(wallpaper()));
                TextView caption=label(getString(entry.title),12,0xffdbe3f1); caption.setSingleLine(true); caption.setEllipsize(android.text.TextUtils.TruncateAt.END); caption.setPadding(0,dp(8),0,0); card.addView(caption);
                UiSounds.click(preview, v -> {
                    getPreferences(MODE_PRIVATE).edit().putString("wallpaper",entry.key).putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()).apply();
                    for (WallpaperCatalog.Entry other:WallpaperCatalog.entries(this)) appearance.findViewById(other.viewId).setSelected(other.key.equals(entry.key));
                    chosen.setText(getString(R.string.selected_prefix)+wallpaperName()); requestWallpaper(); updateBackground(); scheduleWallpaperRotation();
                });
            }
            android.widget.HorizontalScrollView gallery = appearance.findViewById(R.id.wallpaper_scroll);
            View activePreview = appearance.findViewById(WallpaperCatalog.find(this, wallpaper()).viewId);
            gallery.post(() -> {
                if (!gallery.isAttachedToWindow() || activePreview == null) return;
                View card = (View) activePreview.getParent();
                int index = choices.indexOfChild(card);
                View leftCard = index > 0 ? choices.getChildAt(index - 1) : card;
                gallery.scrollTo(leftCard.getLeft(), 0);
            });
            Switch dynamic=appearance.findViewById(R.id.dynamic_wallpapers); dynamic.setTypeface(font);
            dynamic.setChecked(getPreferences(MODE_PRIVATE).getBoolean(WallpaperRotation.ENABLED,false));
            dynamic.setOnCheckedChangeListener((view,enabled) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean(WallpaperRotation.ENABLED,enabled).putLong(WallpaperRotation.CHANGED_AT,System.currentTimeMillis()).apply();
                scheduleWallpaperRotation();
            });
            TextView dimLabel = appearance.findViewById(R.id.dim_label); dimLabel.setTypeface(font);
            TextView dimValue = appearance.findViewById(R.id.dim_value); dimValue.setTypeface(font);
            SeekBar dim = appearance.findViewById(R.id.wallpaper_dim); dim.setProgress(getPreferences(MODE_PRIVATE).getInt("dim", RECOMMENDED_DIM));
            reserveSliderValueWidth(dimValue, dim.getMax(), level -> level + "%");
            dimLabel.setText(getString(R.string.wallpaper_dim)); dimValue.setText(dim.getProgress() + "%");
            dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar bar, int value, boolean user) { dimValue.setText(value + "%"); if (user) { getPreferences(MODE_PRIVATE).edit().putInt("dim", value).apply(); updateBackground(); } }
                public void onStartTrackingTouch(SeekBar bar) {}
                public void onStopTrackingTouch(SeekBar bar) {}
            });
            Button resetDim=appearance.findViewById(R.id.dim_reset); resetDim.setTypeface(font);
            resetDim.setText(R.string.dim_reset);
            resetDim.setContentDescription(getString(R.string.dim_reset_recommended,RECOMMENDED_DIM));
            UiSounds.click(resetDim, v -> {
                getPreferences(MODE_PRIVATE).edit().putInt("dim",RECOMMENDED_DIM).apply();
                dim.setProgress(RECOMMENDED_DIM); updateBackground();
            });
            Switch animations = appearance.findViewById(R.id.animations); animations.setTypeface(font);
            animations.setChecked(getPreferences(MODE_PRIVATE).getBoolean("animations", true));
            animations.setOnCheckedChangeListener((button, checked) -> {
                getPreferences(MODE_PRIVATE).edit().putBoolean("animations", checked).apply();
                configureStateFade(findViewById(android.R.id.content));
                updateBackground();
            });
            UiSounds.click(dynamic, v -> { });
            UiSounds.click(animations, v -> { });
            bindSoundControls(appearance);
            Button language=appearance.findViewById(R.id.language_choice);language.setTypeface(font);
            language.setText(getString(R.string.language)+" · "+AppLanguage.label(this));
            UiSounds.click(language,v->{
                String[] tags={"","uk","en"}; String selected=AppLanguage.selected(this); int index=selected.equals("uk")?1:selected.equals("en")?2:0;
                showSettingChoices(R.string.language,
                        new String[]{getString(R.string.language_system),"Українська","English"},index,which->{
                            if(!tags[which].equals(AppLanguage.selected(this))) {
                                AppLanguage.choose(this,tags[which]); languageChange=true; recreate();
                            }
                        });
            });

        } else if ("screensaver".equals(category)) {
            detail.addView(label(getString(R.string.saver_title), 24, Color.WHITE));
            TextView help = label(getString(R.string.saver_help), 14, getColor(R.color.cd_text_secondary));
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
                showSettingChoices(R.string.saver_delay, options, selected, which -> {
                            Screensaver.prefs(this).edit().putInt(Screensaver.TIMEOUT, Screensaver.MINUTES[which]).apply();
                            showSettingsDetail(detail, category);
                            View target = detail.findViewById(R.id.saver_timeout); if (target != null) target.requestFocus();
                            scheduleScreensaver();
                        });
            });
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_timeout);
            boolean aerial = "aerial".equals(Screensaver.prefs(this).getString(Screensaver.PROVIDER, "builtin"));
            addSettingAction(detail, getString(R.string.saver_source, getString(aerial ? R.string.saver_aerial : R.string.saver_builtin)), () -> {
                showSettingChoices(R.string.saver_source_title,
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
            online.setSingleLine(false); online.setMinHeight(dp(62)); online.setChecked(Screensaver.prefs(this).getBoolean("saverOnline",true));
            online.setOnCheckedChangeListener((button,enabled)->{
                Screensaver.prefs(this).edit().putBoolean("saverOnline",enabled).apply();
                updateSaverCacheStatus();
            });
            UiSounds.click(online, v -> { });
            detail.addView(online,new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView usage=label(getString(R.string.saver_cache_checking),12,getColor(R.color.cd_text_secondary)); usage.setId(R.id.saver_cache_status);
            usage.setPadding(0,dp(8),0,dp(8));detail.addView(usage);updateSaverCacheStatus();
            addSettingAction(detail, getString(R.string.saver_preview), () -> Screensaver.launch(this));
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_preview);
            addSettingAction(detail,getString(R.string.saver_clear,0),()->worker.execute(()->{
                SaverCache.get(this).clear();runOnUiThread(this::updateSaverCacheStatus);
            }));
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.saver_clear);
        } else if ("favorites".equals(category)) {
            detail.addView(label(getString(R.string.favorites), 24, Color.WHITE));
            TextView explanation = label(getString(R.string.favorites_help), 15, getColor(R.color.cd_text_secondary));
            explanation.setPadding(0, dp(18), 0, dp(24)); detail.addView(explanation);
            addSettingAction(detail, getString(R.string.open_library), () -> openPage("apps"));
        } else if("device".equals(category)) {
            renderDeviceDetail(detail);
        } else if(authorsOpen) {
            renderAuthors(detail);
        } else {
            LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
            ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.brand_icon);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams markSize=new LinearLayout.LayoutParams(dp(52),dp(52));markSize.setMarginEnd(dp(16));
            brand.addView(mark,markSize);brand.addView(label(getString(R.string.app_name),28,Color.WHITE));detail.addView(brand);
            TextView version=label(getString(R.string.version_prefix)+UpdateManager.appVersion(this),16,getColor(R.color.cd_text_secondary));
            version.setPadding(0,dp(14),0,dp(8));detail.addView(version);
            TextView company=label(getString(R.string.about_company),16,getColor(R.color.cd_text_link));
            company.setId(R.id.about_company);company.setFocusable(true);company.setBackgroundResource(R.drawable.control_background);
            company.setPadding(dp(12),dp(10),dp(12),dp(10));
            UiSounds.click(company,v->openExternalPage("https://carpediem.agency/"));detail.addView(company);
            addSettingAction(detail,getString(R.string.about_check_updates),()->startActivity(new Intent(this,UpdatesActivity.class)));
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.about_updates);
            addSettingAction(detail,getString(R.string.report_problem),this::showFeedback);
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.about_feedback);
            addSettingAction(detail,getString(R.string.about_authors),()->{
                authorsOpen=true;showSettingsDetail(detail,"about");
                View entry=firstFocusable(detail);if(entry!=null) entry.requestFocus();
            });
            detail.getChildAt(detail.getChildCount()-1).setId(R.id.about_authors);
        }
        linkSettingsFocus(detail);
        View firstDetail = firstFocusable(detail);
        if (firstDetail != null) {
            firstDetail.setNextFocusUpId(R.id.settings_power);
            View entry = settingsEntryFocus(detail, category);
            if (entry != null) entry.setNextFocusUpId(R.id.settings_power);
            findViewById(R.id.settings_power).setNextFocusDownId(
                    entry != null ? entry.getId() : firstDetail.getId());
        }
        configureStateFade(detail);
        } finally {renderingPage=previousRendering;}
    }
    private void renderDeviceDetail(LinearLayout detail) {
        detail.addView(label(getString(R.string.device),24,Color.WHITE));
        TextView specs=label(getString(R.string.device_metrics_loading),14,getColor(R.color.cd_text_body));specs.setId(R.id.device_specs);
        specs.setLineSpacing(dp(4),1f);specs.setPadding(dp(16),dp(12),dp(16),dp(12));
        specs.setBackgroundResource(R.drawable.control_background);detail.addView(specs,new LinearLayout.LayoutParams(-1,-2));
        addUsageBar(detail,R.id.device_ram_label,R.id.device_ram_bar);
        addUsageBar(detail,R.id.device_storage_label,R.id.device_storage_bar);
        addSettingAction(detail,getString(R.string.device_diagnostics),this::showDiagnosticReport);
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.device_diagnostics);
        addSettingAction(detail,getString(R.string.device_home_actions),this::showHomeActions);
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.device_home_actions);
        addSettingAction(detail,getString(R.string.optional_system_controls),this::showSystemActions);
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.device_system_actions);
        addSettingAction(detail,getString(R.string.android_settings),()->{
            try {startActivity(new Intent(Settings.ACTION_SETTINGS));}
            catch(RuntimeException error) {Toast.makeText(this,R.string.settings_unavailable,Toast.LENGTH_SHORT).show();}
        });
        refreshDeviceMetrics();
    }
    private void addUsageBar(LinearLayout detail,int labelId,int barId) {
        boolean ram=barId==R.id.device_ram_bar;
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(0,dp(14),0,dp(6));
        TextView title=label(getString(ram?R.string.device_ram:R.string.device_storage),14,getColor(R.color.cd_text_secondary));
        title.setId(ram?R.id.device_ram_title:R.id.device_storage_title);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView text=label(getString(R.string.device_metrics_loading),14,getColor(R.color.cd_text_body));text.setId(labelId);text.setGravity(Gravity.END);
        heading.addView(text,new LinearLayout.LayoutParams(-2,-2));detail.addView(heading);
        android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setId(barId);bar.setMax(1000);bar.setFocusable(false);bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff243a53));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,dp(6));size.bottomMargin=dp(8);detail.addView(bar,size);
        bar.setVisibility(View.INVISIBLE);
        TextView free=label("",12,0xff9eafc8);free.setId(ram?R.id.device_ram_free:R.id.device_storage_free);
        free.setPadding(0,dp(2),0,dp(6));detail.addView(free);
    }
    private void refreshDeviceMetrics() {
        if(destroyed||!"settings".equals(page)||!"device".equals(settingsCategory)) return;
        final int ticket=settingsDetailGeneration;
        worker.execute(()->{
            DeviceMetrics.Snapshot usage=DeviceMetrics.capture(this);
            DiagnosticReport.Report report=DiagnosticReport.capture(this);
            runOnUiThread(()->{
                if(destroyed||ticket!=settingsDetailGeneration||!"settings".equals(page)||!"device".equals(settingsCategory)) return;
                TextView specs=content.findViewById(R.id.device_specs);if(specs!=null) specs.setText(report.displayText());
                bindUsageBar(R.id.device_ram_label,R.id.device_ram_bar,R.string.device_ram,usage.usedRam(),usage.totalRam);
                bindUsageBar(R.id.device_storage_label,R.id.device_storage_bar,R.string.device_storage,usage.usedStorage(),usage.totalStorage);
            });
        });
    }
    private void bindUsageBar(int labelId,int barId,int textId,long used,long total) {
        TextView text=content.findViewById(labelId);android.widget.ProgressBar bar=content.findViewById(barId);
        if(text==null||bar==null) return;
        TextView free=content.findViewById(barId==R.id.device_ram_bar?R.id.device_ram_free:R.id.device_storage_free);
        bar.setTag(new long[]{used,total});
        if(total<=0) {text.setText(R.string.diagnostic_unknown);bar.setVisibility(View.GONE);if(free!=null) free.setText(R.string.device_metrics_unavailable);return;}
        double fraction=Math.max(0,Math.min(1,(double)used/total));
        text.setText(DeviceUsageText.pair(this,used,total));
        if(free!=null) free.setText(DeviceUsageText.available(this,Math.max(0,total-used)));
        bar.setProgress((int)Math.round(fraction*1000));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(fraction>=.85?0xffed7b78:fraction>=.65?0xffe8be6b:0xff75c7a0));
        bar.setVisibility(View.VISIBLE);
    }
    private LinearLayout sheetBody() {
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);return body;
    }
    private android.app.Dialog showSettingsPanel(int title,View body,View origin,int widthDp) {
        return showSettingsPanel(getString(title),body,origin,widthDp);
    }
    private android.app.Dialog showSettingsPanel(String title,View body,View origin,int widthDp) {
        return showSettingsPanel(title, body, origin, widthDp, false);
    }
    private android.app.Dialog showSettingsPanel(String title, View body, View origin, int widthDp, boolean readFromTop) {
        if(destroyed||isFinishing()||settingsDialog != null||!"settings".equals(page)||origin==null||!origin.isAttachedToWindow()||!origin.isShown()) return null;
        android.app.Dialog dialog=new android.app.Dialog(this);dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel=sheetBody();panel.setPadding(dp(20),dp(20),dp(20),dp(20));panel.setBackground(surface(getColor(R.color.cd_modal_surface),dp(20)));
        TextView heading=label(title,22,Color.WHITE);heading.setPadding(0,0,0,dp(14));panel.addView(heading);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setFocusable(true);scroll.setId(View.generateViewId());scroll.addView(body);
        if (readFromTop) scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        int width=dialogWidth(widthDp);
        Button close=action(getString(R.string.close),dialog::dismiss);close.setId(View.generateViewId());
        close.setSingleLine(false);
        close.setMinHeight(dp(44));
        LinearLayout.LayoutParams closeSize=new LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT);
        closeSize.topMargin=dp(14);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(dp(100),width-dp(40)),View.MeasureSpec.EXACTLY);
        int looseHeight = View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        body.measure(widthSpec, looseHeight);
        heading.measure(widthSpec, looseHeight);
        close.measure(widthSpec, looseHeight);
        int fixedHeight = panel.getPaddingTop() + panel.getPaddingBottom() + heading.getMeasuredHeight()
                + closeSize.topMargin + close.getMeasuredHeight();
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maximum = Math.max(1, screenHeight - Math.max(dp(190), fixedHeight + dp(32)));
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(body.getMeasuredHeight(),maximum)));
        panel.addView(close, closeSize);
        close.setNextFocusUpId(scroll.getId());scroll.setNextFocusDownId(close.getId());
        dialog.setContentView(panel);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled()?R.style.LumaDialogMotion:0);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams attributes=dialog.getWindow().getAttributes();attributes.dimAmount=.55f;dialog.getWindow().setAttributes(attributes);
        settingsDialogRequest++;
        settingsDialog = dialog;
        dialog.setOnDismissListener(ignored->{
            if (!releaseSettingsDialog(dialog)) return;
            if(origin.isAttachedToWindow()&&origin.isShown()) {pendingModalFocus=origin;restorePendingModalFocus();}
        });
        dialog.show();UiSounds.watch(dialog);dialog.getWindow().setLayout(width,-2);
        if (readFromTop) {
            scroll.requestFocus();
        } else {
            View entry=body instanceof android.view.ViewGroup?firstFocusable((android.view.ViewGroup)body):body.isFocusable()?body:null;
            if(entry!=null) entry.requestFocus();else close.requestFocus();
        }
        return dialog;
    }
    private void sheetAction(LinearLayout body,String title,android.app.Dialog[] owner,Runnable callback) {
        addSettingAction(body,title,()->{if(owner[0]!=null) owner[0].dismiss();callback.run();});
    }
    private void showHomeActions() {
        View origin=content.findViewById(R.id.device_home_actions);LinearLayout body=sheetBody();android.app.Dialog[] owner={null};
        TextView status=label(HomeChoice.status(this),14,getColor(R.color.cd_text_secondary));status.setId(R.id.home_status);body.addView(status);
        TextView warning=label(getString(HomeChoice.homeWarning(this)),14,0xffffd68a);warning.setId(R.id.home_remote_warning);
        warning.setVisibility(HomeChoice.needsRemoteHomeCheck(this)?View.VISIBLE:View.GONE);body.addView(warning);
        boolean available=false;
        try {getPackageManager().getActivityInfo(new android.content.ComponentName(this,MainActivity.class.getPackage().getName()+".HomeEntry"),PackageManager.MATCH_DISABLED_COMPONENTS);available=true;}
        catch(PackageManager.NameNotFoundException ignored) { }
        if(available) sheetAction(body,getString(R.string.make_home),owner,()->HomeChoice.request(this));
        sheetAction(body,getString(R.string.choose_home),owner,()->HomeChoice.chooseOther(this));
        try {
            getPackageManager().getApplicationInfo("com.google.android.apps.tv.launcherx",PackageManager.MATCH_DISABLED_COMPONENTS);
            sheetAction(body,getString(R.string.stock_restore),owner,()->confirmSystemAction("stock",null));
        } catch(PackageManager.NameNotFoundException ignored) { }
        owner[0]=showSettingsPanel(R.string.device_home_actions,body,origin,550);
    }
    private void showSystemActions() {
        View origin=content.findViewById(R.id.device_system_actions);LinearLayout body=sheetBody();android.app.Dialog[] owner={null};
        TextView status=label(getString(SystemAccess.ready()?R.string.system_access_ready:SystemAccess.running()?R.string.system_access_permission:R.string.shizuku_stopped_short),14,getColor(R.color.cd_text_secondary));
        status.setId(R.id.shizuku_status);body.addView(status);
        sheetAction(body,getString(R.string.setup_title),owner,()->startActivity(new Intent(this,SystemSetupActivity.class)));
        sheetAction(body,getString(R.string.shizuku_open),owner,()->{
            try {SystemAccess.openManager(this);}catch(RuntimeException unavailable) {Toast.makeText(this,R.string.system_access_failed,Toast.LENGTH_LONG).show();}
        });
        sheetAction(body,getString(R.string.shizuku_grant),owner,()->{
            try {SystemAccess.request(this);}catch(RuntimeException unavailable) {Toast.makeText(this,R.string.system_access_failed,Toast.LENGTH_LONG).show();}
        });
        TextView hint=label(getString(R.string.shizuku_start_hint),12,getColor(R.color.cd_text_secondary));hint.setPadding(0,dp(12),0,0);body.addView(hint);
        owner[0]=showSettingsPanel(R.string.optional_system_controls,body,origin,550);
    }
    private void renderAuthors(LinearLayout detail) {
        detail.addView(label(getString(R.string.about_authors),24,Color.WHITE));
        addSettingAction(detail,getString(R.string.authors_back),this::closeAuthors);detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_back);
        addSettingAction(detail,getString(R.string.code_license),()->showAssetNotice(getString(R.string.code_license),"licenses/Apache-2.0.txt"));
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_code_license);
        addSettingAction(detail,getString(R.string.third_party_notices),()->showAssetNotice(getString(R.string.third_party_notices),"licenses/THIRD-PARTY-NOTICES.md"));
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_third_party);
        addSettingAction(detail,getString(R.string.font_license),()->showAssetNotice("Montserrat · SIL OFL","licenses/Montserrat-OFL.txt"));
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_font_license);
        addSettingAction(detail,getString(R.string.saver_credits),this::showSaverCredits);detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_media_credits);
        addSettingAction(detail,getString(R.string.source_code),()->openExternalPage("https://github.com/carpe-diem-agency/carpe-tv-launcher"));
        detail.getChildAt(detail.getChildCount()-1).setId(R.id.authors_github);
    }
    private void closeAuthors() {
        authorsOpen=false;LinearLayout detail=content.findViewById(R.id.settings_detail);if(detail==null) return;
        showSettingsDetail(detail,"about");View action=detail.findViewById(R.id.about_authors);if(action!=null) action.requestFocus();
    }
    private boolean currentSettingsRequest(View origin,int generation,int request,String category) {
        return !destroyed&&!isFinishing()&&generation==settingsDetailGeneration&&request==settingsDialogRequest
                &&"settings".equals(page)&&category.equals(settingsCategory)&&origin!=null&&origin.isAttachedToWindow()&&origin.isShown();
    }
    private void showSaverCredits() {
        final View origin=content.findViewById(R.id.authors_media_credits);
        final int generation=settingsDetailGeneration,request=++settingsDialogRequest;
        worker.execute(()->{
            StringBuilder text=new StringBuilder();
            for(SaverMedia item:SaverCache.get(this).entries()) text.append(item.title).append("\n").append(item.credit).append("\n").append(item.source).append("\n").append(item.license).append("\n\n");
            runOnUiThread(()->{
                if(!authorsOpen||!currentSettingsRequest(origin,generation,request,"about")) return;
                TextView body=label(text.length()==0?getString(R.string.saver_credits_empty):text.toString(),14,getColor(R.color.cd_text_body));
                showSettingsPanel(R.string.saver_credits,body,origin,550);
            });
        });
    }
    private void showDiagnosticReport() {
        final View origin=content.findViewById(R.id.device_diagnostics);
        final int generation=settingsDetailGeneration,request=++settingsDialogRequest;
        worker.execute(()->{
            DiagnosticReport.Report report=DiagnosticReport.capture(this);
            android.graphics.Bitmap qr=null;
            try {
                com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter()
                        .encode(report.text(),com.google.zxing.BarcodeFormat.QR_CODE,384,384,
                                java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8"));
                qr=android.graphics.Bitmap.createBitmap(384,384,android.graphics.Bitmap.Config.ARGB_8888);
                int[] pixels=new int[384*384];for(int y=0;y<384;y++) for(int x=0;x<384;x++) pixels[y*384+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
                qr.setPixels(pixels,0,384,0,0,384,384);
            } catch(com.google.zxing.WriterException unavailable) { }
            final android.graphics.Bitmap readyQr=qr;
            runOnUiThread(()->{
                if(!currentSettingsRequest(origin,generation,request,"device")) {if(readyQr!=null) readyQr.recycle();return;}
                int bodyWidth = dialogWidth(640) - dp(40);
                boolean stacked = bodyWidth < dp(440);
                LinearLayout body = new LinearLayout(this);
                body.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                TextView text = label(report.displayText(),14,getColor(R.color.cd_text_body));
                text.setId(R.id.diagnostic_text);
                text.setLineSpacing(dp(3),1f);
                if (!stacked) body.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                if (readyQr != null) {
                    ImageView code = new ImageView(this);
                    code.setId(R.id.diagnostic_qr);
                    code.setImageBitmap(readyQr);
                    code.setBackgroundColor(Color.WHITE);
                    code.setContentDescription(getString(R.string.diagnostic_qr));
                    int side = Math.min(dp(170), bodyWidth);
                    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(side, side);
                    if (stacked) {
                        size.gravity = Gravity.CENTER_HORIZONTAL;
                        size.bottomMargin = dp(14);
                    } else size.setMarginStart(dp(16));
                    body.addView(code,size);
                } else {
                    TextView unavailable=label(getString(R.string.diagnostic_qr_unavailable),13,getColor(R.color.cd_text_secondary));
                    body.addView(unavailable);
                }
                if (stacked) body.addView(text,new LinearLayout.LayoutParams(-1,-2));
                LinearLayout panel=sheetBody();panel.addView(body,new LinearLayout.LayoutParams(-1,-2));
                TextView hint=label(getString(R.string.diagnostic_qr_help),13,getColor(R.color.cd_text_secondary));hint.setId(R.id.diagnostic_qr_help);
                hint.setPadding(0,dp(12),0,dp(4));panel.addView(hint);
                android.app.Dialog[] owner={null};
                Button reportBug=action(getString(R.string.diagnostic_report_bug),()->{
                    pendingFeedbackLaunch=()->{
                        if(!destroyed&&wallpaperResumed&&"settings".equals(page)&&"device".equals(settingsCategory)
                                &&origin.isAttachedToWindow()&&origin.isShown()) showFeedback(origin);
                    };
                    if(owner[0]!=null) owner[0].dismiss();
                    getWindow().getDecorView().post(this::launchPendingFeedback);
                });
                reportBug.setId(R.id.diagnostic_report_bug);
                reportBug.setSingleLine(false);
                reportBug.setMinHeight(dp(48));
                LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
                actionSize.topMargin=dp(8);panel.addView(reportBug,actionSize);
                owner[0]=showSettingsPanel(getString(R.string.device_diagnostics),panel,origin,640,stacked);
            });
        });
    }
    private void openExternalPage(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
        catch (RuntimeException unavailable) { Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show(); }
    }
    private void launchPendingFeedback() {
        if(pendingFeedbackLaunch==null||settingsDialog != null||!hasWindowFocus()) return;
        Runnable launch=pendingFeedbackLaunch;pendingFeedbackLaunch=null;launch.run();
    }
    private void showFeedback() {showFeedback(null);}
    private void showFeedback(View explicitOrigin) {
        if (destroyed || isFinishing() || settingsDialog != null) return;
        settingsDialogRequest++;
        final String[] templates = {"bug.yml", "idea.yml", "other.yml"};
        final int[] choices = {R.string.feedback_bug, R.string.feedback_idea, R.string.feedback_other};
        final String[] issueUrl = {feedbackUrl(templates[0])};
        final int[] selectedTopic = {0};
        View feedbackAction = findSettingAction(getString(R.string.report_problem));
        View previousFocus=explicitOrigin!=null?explicitOrigin:feedbackAction!=null?feedbackAction:getCurrentFocus();
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(16), dp(28), dp(16));
        panel.setBackground(surface(getColor(R.color.cd_modal_surface), 20));
        TextView title = label(getString(R.string.feedback_title), 24, Color.WHITE);
        panel.addView(title);
        TextView hint = label(getString(R.string.report_problem_hint), 16, 0xffe0e8f6);
        hint.setTypeface(font); hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(4)); panel.addView(hint);
        TextView privacy = label(getString(R.string.feedback_privacy), 13, 0xffaebed4);
        privacy.setTypeface(font); privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, 0, 0, dp(10)); panel.addView(privacy);
        LinearLayout types = new LinearLayout(this);
        types.setOrientation(LinearLayout.HORIZONTAL);
        Button[] typeButtons = new Button[choices.length];
        android.graphics.Bitmap[] qrBitmaps = new android.graphics.Bitmap[choices.length];
        ImageView qr = new ImageView(this);
        qr.setContentDescription(getString(R.string.issue_qr));
        // The writer supplies its own four-module quiet zone. Keep the raster at
        // physical pixel size instead of shrinking a dense code into image padding.
        qr.setBackgroundColor(Color.WHITE);qr.setScaleType(ImageView.ScaleType.CENTER);
        TextView fallback = label("", 12, getColor(R.color.cd_text_secondary));
        for (int i = 0; i < choices.length; i++) {
            final int index = i;
            Button button = action(getString(choices[i]), () -> {
                selectedTopic[0] = index;
                issueUrl[0] = feedbackUrl(templates[index]);
                for (int j = 0; j < typeButtons.length; j++) {
                    typeButtons[j].setSelected(j == index);
                    if (android.os.Build.VERSION.SDK_INT >= 30)
                        typeButtons[j].setStateDescription(j == index ? getString(R.string.feedback_selected) : null);
                }
                showFeedbackQr(qr, fallback, issueUrl[0], qrBitmaps, index);
            });
            button.setTextSize(14); button.setGravity(Gravity.CENTER);
            button.setPadding(dp(4), 0, dp(4), 0);
            button.setSelected(i == 0);
            if (i == 0 && android.os.Build.VERSION.SDK_INT >= 30)
                button.setStateDescription(getString(R.string.feedback_selected));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) size.leftMargin = dp(6);
            types.addView(button, size); typeButtons[i] = button;
        }
        panel.addView(types, new LinearLayout.LayoutParams(-1, dp(48)));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int qrSide=Math.min(dp(192),Math.max(1,dialogWidth(430)-dp(56)));
        LinearLayout.LayoutParams qrSize = new LinearLayout.LayoutParams(qrSide, qrSide); qrSize.topMargin = dp(8);
        panel.addView(qr, qrSize);
        panel.addView(fallback);
        showFeedbackQr(qr, fallback, issueUrl[0], qrBitmaps, 0);
        Button open = action(getString(R.string.open_issue), () -> { dialog.dismiss(); openExternalPage(issueUrl[0]); });
        LinearLayout.LayoutParams openSize = new LinearLayout.LayoutParams(-1, dp(48)); openSize.topMargin = dp(10);
        panel.addView(open, openSize);
        Button close = action(getString(R.string.close), dialog::dismiss);
        LinearLayout.LayoutParams closeSize = new LinearLayout.LayoutParams(-1, dp(48)); closeSize.topMargin = dp(6);
        panel.addView(close, closeSize);
        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(false);
        dialogScroll.setVerticalScrollBarEnabled(false);
        dialogScroll.addView(panel);
        dialogScroll.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom) -> {
            int availableHeight = dialogScroll.getHeight() - panel.getTop() - qr.getTop() - dp(8);
            int availableWidth = panel.getWidth() - panel.getPaddingLeft() - panel.getPaddingRight();
            int side = Math.max(1, Math.min(qrSide, Math.min(availableHeight, availableWidth)));
            if (qrSize.width == side) return;
            qrSize.width = side;
            qrSize.height = side;
            qr.setLayoutParams(qrSize);
            // Re-encode at integer module size for the actual window.
            java.util.Arrays.fill(qrBitmaps, null);
            showFeedbackQr(qr, fallback, issueUrl[0], qrBitmaps, selectedTopic[0]);
        });
        dialog.setContentView(dialogScroll);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
        settingsDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (!releaseSettingsDialog(dialog)) return;
            if (previousFocus != null && previousFocus.isAttachedToWindow()) {
                pendingModalFocus = previousFocus;
                restorePendingModalFocus();
            }
        });
        dialog.show(); UiSounds.watch(dialog);
        int width = dialogWidth(430);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        dialog.getWindow().setLayout(width, Math.min(panel.getMeasuredHeight(), screenHeight - dp(32)));
        typeButtons[0].requestFocus();
    }
    private String feedbackUrl(String template) {
        return DiagnosticReport.issueUrl(this,template);
    }
    private void showFeedbackQr(ImageView qr, TextView fallback, String url,
                                android.graphics.Bitmap[] cache, int index) {
        try {
            if (cache[index] == null) {
                int side=qr.getLayoutParams()!=null&&qr.getLayoutParams().width>0?qr.getLayoutParams().width:dp(192);
                side=Math.max(1,side-qr.getPaddingLeft()-qr.getPaddingRight());
                com.google.zxing.common.BitMatrix bits=new com.google.zxing.qrcode.QRCodeWriter()
                        .encode(url,com.google.zxing.BarcodeFormat.QR_CODE,side,side,
                                java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8"));
                int width=bits.getWidth(),height=bits.getHeight();
                if (width > side || height > side) throw new com.google.zxing.WriterException("QR does not fit the view");
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
                bitmap.setDensity(android.graphics.Bitmap.DENSITY_NONE);
                int[] pixels=new int[width*height];
                for(int y=0;y<height;y++) for(int x=0;x<width;x++) pixels[y*width+x]=bits.get(x,y)?Color.BLACK:Color.WHITE;
                bitmap.setPixels(pixels,0,width,0,0,width,height);cache[index]=bitmap;
            }
            android.graphics.drawable.BitmapDrawable image=new android.graphics.drawable.BitmapDrawable(getResources(),cache[index]);
            image.setFilterBitmap(false);image.setDither(false);
            qr.setImageDrawable(image);qr.setVisibility(View.VISIBLE);fallback.setVisibility(View.GONE);
        } catch (com.google.zxing.WriterException error) {
            qr.setVisibility(View.GONE); fallback.setText(url); fallback.setVisibility(View.VISIBLE);
        }
    }
    private void showAssetNotice(String title, String assetPath) {
        String focusLabel=assetPath.endsWith("Montserrat-OFL.txt") ? getString(R.string.font_license) : title;
        View origin=findSettingAction(focusLabel);
        View focused=getCurrentFocus();
        if(focused!=null&&focused==origin&&focused.isAttachedToWindow()&&focused.isShown()) origin=focused;
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
        TextView body=label(notice,15,Color.WHITE);
        body.setTypeface(font);
        body.setTag("asset-notice-body");
        body.setLineSpacing(dp(4),1f);
        showSettingsPanel(title,body,origin,550);
    }
    private boolean releaseSettingsDialog(android.app.Dialog dialog) {
        if (settingsDialog != dialog) return false;
        settingsDialog = null;
        return !destroyed;
    }

    private void restorePendingModalFocus() {
        View target = pendingModalFocus;
        if (target == null) return;
        if (!"settings".equals(page) || !target.isAttachedToWindow()) {
            pendingModalFocus = null;
            return;
        }
        if (!hasWindowFocus()) return;
        boolean restored = target.isInTouchMode() ? target.requestFocusFromTouch() : target.requestFocus();
        pendingModalFocus = null;
        if (!restored) target.post(() -> {
            if (hasWindowFocus() && target.isAttachedToWindow() && getCurrentFocus() == null)
                target.requestFocusFromTouch();
        });
    }
    private View findSettingAction(String label) {
        LinearLayout detail = content.findViewById(R.id.settings_detail);
        if (detail == null) return null;
        for (int i = 0; i < detail.getChildCount(); i++) {
            View item = detail.getChildAt(i);
            if (item instanceof Button && label.contentEquals(((Button) item).getText())) return item;
        }
        return null;
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
        if(destroyed||!"settings".equals(page)||!"screensaver".equals(settingsCategory)) return;
        final int ticket=settingsDetailGeneration;
        worker.execute(()->{
            SaverCache cache=SaverCache.get(this);long used=cache.bytes()/SaverCache.MIB,free=cache.freeBytes()/SaverCache.MIB;
            runOnUiThread(()->{
                if(destroyed||ticket!=settingsDetailGeneration||!"settings".equals(page)||!"screensaver".equals(settingsCategory)) return;
                TextView status=content.findViewById(R.id.saver_cache_status);
                Button clear=content.findViewById(R.id.saver_clear);
                if(status!=null) status.setText(getString(R.string.saver_cache_usage,used)
                        +(free<1024&&Screensaver.prefs(this).getBoolean("saverOnline",true)?getString(R.string.saver_cache_waiting,1024):""));
                if(clear!=null) clear.setText(getString(R.string.saver_clear,used));
            });
        });
    }
    private void bindSoundControls(View appearance) {
        UiSounds sound=UiSounds.get(this);
        Switch enabled=appearance.findViewById(R.id.ui_sounds_enabled);
        SeekBar volume=appearance.findViewById(R.id.ui_sounds_volume);
        TextView value=appearance.findViewById(R.id.ui_sounds_value);
        TextView label=appearance.findViewById(R.id.ui_sounds_label);
        Button preview=appearance.findViewById(R.id.ui_sounds_preview);
        enabled.setTypeface(font);value.setTypeface(font);label.setTypeface(font);preview.setTypeface(font);
        reserveSoundSliderValueWidth(label, value, volume.getMax());
        enabled.setChecked(sound.enabled());
        volume.setSoundEffectsEnabled(false);
        volume.setProgress(sound.volume());
        value.setText(getString(R.string.ui_sounds_level,sound.volume()));
        volume.setEnabled(sound.enabled());
        preview.setEnabled(sound.enabled()&&sound.volume()>0);
        enabled.setOnCheckedChangeListener((button,checked)->{
            sound.setEnabled(checked);
            volume.setEnabled(checked);
            preview.setEnabled(checked&&sound.volume()>0);
        });
        UiSounds.click(enabled,v->{ });
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int level,boolean user) {
                value.setText(getString(R.string.ui_sounds_level,level));
                if(user) sound.setVolume(level);
                preview.setEnabled(sound.enabled()&&level>0);
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        UiSounds.previewButton(preview,v->sound.preview(preview));
    }

    static void reserveSliderValueWidth(TextView value, int max, IntFunction<String> format) {
        android.view.ViewGroup.LayoutParams params = value.getLayoutParams();
        float widestText = 0;
        for (int level = 0; level <= max; level++) {
            widestText = Math.max(widestText, Layout.getDesiredWidth(format.apply(level), value.getPaint()));
        }
        int requiredWidth = (int) Math.ceil(widestText) + value.getCompoundPaddingLeft() + value.getCompoundPaddingRight();
        if (requiredWidth > params.width) {
            params.width = requiredWidth;
            value.setLayoutParams(params);
        }
    }

    static void reserveSoundSliderValueWidth(TextView label, TextView value, int max) {
        int originalWidth = value.getLayoutParams().width;
        reserveSliderValueWidth(value, max, level -> value.getContext().getString(R.string.ui_sounds_level, level));
        int addedWidth = value.getLayoutParams().width - originalWidth;
        if (addedWidth == 0) return;
        // The label can wrap. Use its space before shortening the adjustable track.
        android.view.ViewGroup.LayoutParams labelParams = label.getLayoutParams();
        labelParams.width -= Math.min(addedWidth, labelParams.width / 2);
        label.setLayoutParams(labelParams);
    }
    private void showSettingChoices(int title, String[] choices, int selected, java.util.function.IntConsumer choose) {
        if (destroyed || isFinishing() || settingsDialog != null) return;
        settingsDialogRequest++;
        final String originPage=page,originCategory=settingsCategory;
        int actionId=title==R.string.language?R.id.language_choice
                :title==R.string.saver_delay?R.id.saver_timeout
                :title==R.string.saver_source_title?R.id.saver_provider:View.NO_ID;
        View source=actionId!=View.NO_ID?content.findViewById(actionId):getCurrentFocus();
        View detail=content.findViewById(R.id.settings_detail);
        if(source==null||!isInside(source,detail)) source=settingsCategoryView;
        final View origin=source;
        final int originId=origin!=null?origin.getId():View.NO_ID;
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        ChoiceSheetLayout layout=new ChoiceSheetLayout(this,font,getString(title),choices,selected,
                (text,callback)->action(text,callback),choose,dialog::dismiss);
        dialog.setContentView(layout.panel); dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
        dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams window=dialog.getWindow().getAttributes(); window.dimAmount=.55f; dialog.getWindow().setAttributes(window);
        settingsDialog = dialog;
        dialog.setOnDismissListener(ignored->{
            if (!releaseSettingsDialog(dialog)) return;
            if(!originPage.equals(page)||!originCategory.equals(settingsCategory)) return;
            // Hold the rail guard until the selection callback has rebuilt its
            // controls. Resolve the replacement by ID, after window focus returns.
            pendingModalFocus=origin;
            getWindow().getDecorView().post(()->{
                if(destroyed||languageChange||settingsDialog != null
                        ||!originPage.equals(page)||!originCategory.equals(settingsCategory)) {
                    if(pendingModalFocus==origin) pendingModalFocus=null;
                    return;
                }
                View target=originId!=View.NO_ID?content.findViewById(originId):origin;
                if(target!=null&&target.isAttachedToWindow()&&target.isShown()&&target.isEnabled()&&target.isFocusable()) {
                    pendingModalFocus=target;restorePendingModalFocus();
                } else if(pendingModalFocus==origin) pendingModalFocus=null;
            });
        });
        int width=dialogWidth(550);
        int height=layout.measure(width,getResources().getDisplayMetrics().heightPixels);
        dialog.show(); UiSounds.watch(dialog); dialog.getWindow().setLayout(width,height);
        layout.initial.requestFocus();
    }
    private String wallpaper() { return WallpaperCatalog.find(this,getPreferences(MODE_PRIVATE).getString("wallpaper",null)).key; }
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
        // Returning to the already loaded photo still cancels a pending different choice.
        final int ticket=++wallpaperGeneration;
        if (entry.key.equals(loadedWallpaper) && sharpWallpaper!=null) return;
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
        Button button = action(text, callback);
        button.setId(View.generateViewId());
        button.setSingleLine(false);
        button.setMinHeight(dp(52));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        detail.addView(button, params);
    }
    private View firstFocusable(android.view.ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view.isFocusable()) return view;
            if (view instanceof android.view.ViewGroup) { View nested = firstFocusable((android.view.ViewGroup) view); if (nested != null) return nested; }
        }
        return null;
    }
    private View lastEnabledSettingsControl(View root) {
        if (!root.isShown()) return null;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                View control = lastEnabledSettingsControl(group.getChildAt(index));
                if (control != null) return control;
            }
        }
        return root.isFocusable() && root.isEnabled() ? root : null;
    }

    private boolean isInside(View child, View parent) {
        if (parent == null) return false;
        for (View node=child; node!=null; ) {
            if (node==parent) return true;
            android.view.ViewParent ancestor=node.getParent();
            node=ancestor instanceof View ? (View)ancestor : null;
        }
        return false;
    }
    private int focusableIndex(View root, View target) {
        ArrayList<View> controls=new ArrayList<>();
        collectFocusable(root,controls);
        return controls.indexOf(target);
    }
    private View focusableAt(View root, int index) {
        if (index < 0) return null;
        ArrayList<View> controls=new ArrayList<>();
        collectFocusable(root,controls);
        return index < controls.size() ? controls.get(index) : null;
    }
    private void collectFocusable(View root, List<View> controls) {
        if (root.isFocusable()) { controls.add(root); return; }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for (int i=0;i<group.getChildCount();i++) collectFocusable(group.getChildAt(i),controls);
        }
    }
    private View settingsEntryFocus(android.view.ViewGroup detail, String category) {
        if ("appearance".equals(category)) {
            View selected = detail.findViewById(WallpaperCatalog.find(this, wallpaper()).viewId);
            if (selected != null && selected.isFocusable()) return selected;
        }
        return firstFocusable(detail);
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
        try {
            renderPageContent();
            if ("home".equals(page)) updateHomeTextContrast();
        }
        finally { renderingPage = false; configureStateFade(content); }
    }
    private void configureStateFade(View view) {
        if(view.getId()==R.id.app_artwork) {
            boolean enabled=motionEnabled();
            boolean libraryArtwork=false;
            for(View ancestor=view;ancestor!=null;) {
                if(ancestor.getId()==R.id.apps_grid) {libraryArtwork=true;break;}
                android.view.ViewParent parent=ancestor.getParent();ancestor=parent instanceof View?(View)parent:null;
            }
            int animator=enabled?(libraryArtwork?R.animator.library_app_press:R.animator.app_press):0;
            if(!Integer.valueOf(animator).equals(view.getTag(R.id.press_motion_enabled))) {
                view.setTag(R.id.press_motion_enabled,animator);
                view.setStateListAnimator(animator!=0?android.animation.AnimatorInflater.loadStateListAnimator(this,animator):null);
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
            UiSounds.click(header.findViewById(R.id.home_power), v -> showSleepNow());
            UiSounds.click(header.findViewById(R.id.home_timer), v -> showSleepTimer());
            updateSleepTimerIndicator();
            UiSounds.click(header.findViewById(R.id.home_wifi), v -> openWifi());
        UiSounds.click(header.findViewById(R.id.home_bluetooth), v -> openBluetooth());
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
            UiSounds.click(layout.findViewById(R.id.apps_power), v -> showSleepNow());
            UiSounds.click(layout.findViewById(R.id.apps_timer), v -> showSleepTimer());
            updateSleepTimerIndicator();
            UiSounds.click(layout.findViewById(R.id.apps_wifi), v -> {
                try { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }
                catch (RuntimeException error) { Toast.makeText(this, getString(R.string.network_unavailable), Toast.LENGTH_SHORT).show(); }
            });
            UiSounds.click(layout.findViewById(R.id.apps_bluetooth), v -> openBluetooth());
            EditText search = layout.findViewById(R.id.apps_search); search.setTypeface(font); search.setText(query);
            LinearLayout list = layout.findViewById(R.id.apps_grid);
            ScrollView appScroll = layout.findViewById(R.id.apps_scroll);
            search.setShowSoftInputOnFocus(false);
            UiSounds.click(search, v -> { search.requestFocus(); ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT); });
            search.setOnEditorActionListener((v, actionId, event) -> { focusLibraryEntry(search); return true; });
            search.setOnKeyListener((v, key, event) -> {
                if (key != android.view.KeyEvent.KEYCODE_DPAD_DOWN) return false;
                // EditText can consume Down itself. Route it explicitly for both
                // app tiles and Retry, and keep empty/loading searches in place.
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) focusLibraryEntry(search);
                return true;
            });
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(!query.contentEquals(s)) {
                        cancelPendingAppsLayout();restoredAppsScrollY=-1;restoredAppsFocusPackage=null;
                    }
                    query = s.toString();
                    renderList(list, allApps, false);
                    appScroll.scrollTo(0, 0);
                    appScroll.post(() -> {
                        appScroll.scrollTo(0, 0);
                        list.invalidate();
                        appScroll.invalidate();
                    });
                }
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
    private void focusLibraryEntry(EditText search) {
        if (!"apps".equals(page) || content.findViewById(R.id.apps_search) != search) return;
        hideKeyboard();
        // Resolve within this page rather than relying on libraryEntry, which is
        // a selected app tile and is null for Retry or can belong to a cached page.
        View entry = content.findViewById(search.getNextFocusDownId());
        if (entry != null && entry != search && entry.isShown() && entry.isFocusable()) entry.requestFocus();
    }
    private void openPage(String target) {
        if (page.equals(target)) { updateHomeGreeting(); return; }
        if ("home".equals(page)) restoredHomeFavorite = null;
        hideKeyboard();
        int direction = pageOrder(target) > pageOrder(page) ? 1 : -1;
        if("settings".equals(page)&&!"settings".equals(target)) {settingsDialogRequest++;pendingFeedbackLaunch=null;}
        cancelPendingAppsLayout();
        page = target;focusedPackage=pageFocus.get(target);
        if(!"settings".equals(target)||!"audit".equals(settingsCategory)) stopAuditRefresh();
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
            View entry = findPageTile(content, focusedPackage);
            if (entry == null) entry = findPageTile(content, null);
            if (entry != null) {
                findViewById("home".equals(page) ? R.id.nav_home : R.id.nav_apps).setNextFocusRightId(entry.getId());
                if("home".equals(page)) linkHomeHeaderDown(entry.getId());
            }
            if (search != null) {
                libraryEntry = entry;
                if (entry != null) search.setNextFocusDownId(entry.getId());
                View linked = content.findViewById(search.getNextFocusDownId());
                findViewById(R.id.nav_apps).setNextFocusRightId(linked != null && linked.isFocusable()
                        ? linked.getId() : search.getId());
            }
            restoreCachedArtwork(content);
            updateHomeGreeting();
        }
        restorePendingAppsState();
        pageMotion.play(content,direction);
        if(auditActive()) refreshApplicationAudit();
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
    private static final class LibraryRenderState {
        final List<AppItem> apps;
        final String emptyState;
        final LibraryTilePool pool;
        LibraryRenderState(List<AppItem> apps,String emptyState,LibraryTilePool pool) {
            this.apps=new ArrayList<>(apps);this.emptyState=emptyState;this.pool=pool;
        }
    }
    private void renderList(LinearLayout list, List<AppItem> apps, boolean takeFocus) {
        List<AppItem> catalog=apps;
        List<String> favorites = getFavorites();
        boolean library = "apps".equals(page);
        if ("home".equals(page)) {
            Map<String, AppItem> available = new LinkedHashMap<>();
            for (AppItem app : apps) available.put(app.pkg, app);
            List<AppItem> ordered = new ArrayList<>();
            for (String pkg : favorites) if (available.containsKey(pkg)) ordered.add(available.get(pkg));
            apps = ordered;
        } else if (library) {
            String normalizedQuery = query.toLowerCase(java.util.Locale.ROOT);
            List<AppItem> matching = new ArrayList<>();
            for (AppItem app : apps)
                if (app.label.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)) matching.add(app);
            apps = matching;
        }
        String emptyState = !apps.isEmpty() ? "results"
                : !appsReady && !appsLoadFailed && allApps.isEmpty() ? "loading"
                : appsLoadFailed && allApps.isEmpty() ? "error"
                : query.isEmpty() ? "empty" : "no-match";
        LibraryRenderState previous = list.getTag() instanceof LibraryRenderState
                ? (LibraryRenderState) list.getTag() : null;
        // Query changes can leave the exact same results (for example case changes).
        // Keep their views and pending artwork work instead of invalidating the grid.
        if (library && !takeFocus && previous != null
                && previous.emptyState.equals(emptyState) && sameApps(previous.apps, apps)) return;
        LibraryTilePool pool=library?(previous!=null?previous.pool:new LibraryTilePool(LibraryTilePool.MAX_COUNT,LibraryTilePool.MAX_BYTES)):null;
        Map<String,String> poolLabels=pool!=null?new LinkedHashMap<>():null;
        if(pool!=null) {
            for(AppItem app:catalog) poolLabels.put(app.pkg,app.label);
            pool.retain(poolLabels);
        }
        Map<String, LinearLayout> reusable = new LinkedHashMap<>();
        View priorFocus = getCurrentFocus();
        boolean hadTileFocus = false;
        if (library) for (int r = 0; r < list.getChildCount(); r++) {
            View child = list.getChildAt(r);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout oldRow = (LinearLayout) child;
            for (int c = 0; c < oldRow.getChildCount(); c++) {
                View tile = oldRow.getChildAt(c);
                if (tile instanceof LinearLayout && tile.getTag() instanceof String) {
                    reusable.put((String) tile.getTag(), (LinearLayout) tile);
                    if (tile == priorFocus) hadTileFocus = true;
                }
            }
        }
        list.removeAllViews();
        // Card shadows and focused scale extend beyond a cell's layout bounds.
        list.setClipChildren(false);
        list.setClipToPadding(false);
        // Removed tiles must not receive old artwork. Reused tiles without artwork
        // are rescheduled below under the new generation.
        final int renderGeneration = ++generation;
        View first = null, restore = null;
        LinearLayout row = null;
        List<View> tiles = new ArrayList<>();
        int count = 0;
        for (AppItem app : apps) {
            if (count % 5 == 0) {
                row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                // Cards have no shared text baseline; skip the weighted baseline premeasure.
                if(library) row.setBaselineAligned(false);
                row.setClipChildren(false); row.setClipToPadding(false); list.addView(row);
            }
            LinearLayout reused = reusable.remove(app.pkg);
            if(reused==null&&pool!=null) reused=pool.take(app.pkg,app.label);
            // A changed catalog label must also replace listeners capturing AppItem.
            if (reused != null && !app.label.contentEquals(((TextView) reused.findViewById(R.id.app_name)).getText())) reused = null;
            final LinearLayout tile = reused != null ? reused
                    : (LinearLayout) getLayoutInflater().inflate(R.layout.item_app, row, false);
            boolean newTile = reused == null;
            if (tile.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) tile.getParent()).removeView(tile);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(124), 1);
            tileParams.setMargins(dp(5), dp(8), dp(5), dp(8)); row.addView(tile, tileParams);
            ImageView icon = tile.findViewById(R.id.app_icon);
            View artworkFrame = tile.findViewById(R.id.app_artwork);
            if (newTile) {
                tile.setClipChildren(false);
                tile.setClipToPadding(false);
                tile.setId(View.generateViewId());
                tile.setContentDescription(app.label);
                artworkFrame.setTag(app.pkg);
                icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View view, android.graphics.Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12)); }
                });
                icon.setClipToOutline(true);
                artworkFrame.setOutlineProvider(icon.getOutlineProvider());
                artworkFrame.setClipToOutline(true); artworkFrame.setElevation(dp(2));
                TextView name = tile.findViewById(R.id.app_name); name.setText(app.label); name.setTypeface(Typeface.create(font, Typeface.BOLD));
                tile.setTag(app.pkg);
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
                UiSounds.click(tile, v -> openApp(app.pkg,artworkFrame));
                UiSounds.longClick(tile, v -> { showFavoriteMenu(app, tile); return true; });
                configureStateFade(tile);
            } else {
                // Detached pooled cards may have missed an animation-setting change.
                configureStateFade(artworkFrame);
                // Detaching a reused tile cancels FocusMotion mid-animation.
                // Settle its visuals so an unfocused result cannot keep a stale ring.
                artworkFrame.animate().cancel();
                View ring = artworkFrame.findViewById(R.id.focus_outline);
                ring.animate().cancel();
                artworkFrame.setScaleX(tile.hasFocus() ? 1.025f : 1f);
                artworkFrame.setScaleY(tile.hasFocus() ? 1.025f : 1f);
                ring.setAlpha(tile.hasFocus() ? 1f : 0f);
            }
            if (icon.getDrawable() == null) {
                Drawable.ConstantState cachedIcon = icons.get(app.pkg);
                if (cachedIcon != null) bindArtwork(icon, cachedIcon);
                else worker.execute(() -> {
                    if (destroyed || generation != renderGeneration) return;
                    Drawable.ConstantState loaded = loadArtwork(app.pkg);
                    runOnUiThread(() -> { if (!destroyed && generation == renderGeneration) icon.post(() -> { if (!destroyed && generation == renderGeneration) bindArtwork(icon, loaded); }); });
                });
            }
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
            tile.setNextFocusDownId(nextTileDownId(tiles, i));
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
        View emptyAction = null;
        if (first == null && !appsReady && !appsLoadFailed && allApps.isEmpty()) {
            addLoadingTiles(list);
        } else if (first == null && appsLoadFailed && allApps.isEmpty()) {
            TextView message = label(getString(R.string.apps_load_failed), 16, 0xffd8e1ef);
            message.setPadding(dp(8), dp(12), dp(8), dp(12));
            list.addView(message);
            Button retry = action(getString(R.string.apps_retry), () -> {
                appsLoadFailed = false;
                appsReady = false;
                // Retry replaces the focused button with loading. Keep a stable
                // editor anchor so Android does not move focus to the sidebar.
                renderCatalogState(library);
                catalogRefresh.request(false);
            });
            retry.setId(View.generateViewId());
            if (library) {
                retry.setNextFocusUpId(R.id.apps_search);
                retry.setNextFocusLeftId(R.id.nav_apps);
                retry.setNextFocusRightId(retry.getId());
                retry.setNextFocusDownId(retry.getId());
            }
            list.addView(retry, new LinearLayout.LayoutParams(dp(220), dp(52)));
            emptyAction = retry;
        } else if (first == null) {
            TextView empty = new TextView(this);
            empty.setText("home".equals(page) ? getString(R.string.favorites_empty)
                    : query.isEmpty() ? getString(R.string.apps_empty) : getString(R.string.search_empty));
            empty.setTypeface(font); empty.setTextSize(16);
            empty.setTextColor(0xffd8e1ef); empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            list.addView(empty);
        }
        if (emptyAction != null) {
            findViewById("home".equals(page) ? R.id.nav_home : R.id.nav_apps).setNextFocusRightId(emptyAction.getId());
        }
        if (library) {
            View search = findViewById(R.id.apps_search);
            View entry = libraryEntry != null ? libraryEntry : emptyAction;
            if (search != null) {
                search.setNextFocusDownId(entry != null ? entry.getId() : search.getId());
                findViewById(R.id.nav_apps).setNextFocusRightId(entry != null ? entry.getId() : search.getId());
            }
            for(Map.Entry<String,LinearLayout> removed:reusable.entrySet())
                if(poolLabels.containsKey(removed.getKey())) pool.put(removed.getKey(),removed.getValue());
            list.setTag(new LibraryRenderState(apps,emptyState,pool));
        }
        if ((takeFocus || hadTileFocus) && restore != null) restore.requestFocus();
        else if ((takeFocus || hadTileFocus) && first != null) first.requestFocus();
        else if (takeFocus && emptyAction != null) emptyAction.requestFocus();
        else if (first == null && !isSidebarFocused())
            findViewById("home".equals(page) ? R.id.nav_home : R.id.apps_search).requestFocus();
    }
    private void addLoadingTiles(LinearLayout list) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        list.addView(row);
        for (int i = 0; i < 5; i++) {
            LinearLayout placeholder = new LinearLayout(this);
            placeholder.setOrientation(LinearLayout.VERTICAL);
            placeholder.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(124), 1);
            cell.setMargins(dp(5), dp(8), dp(5), dp(8));
            row.addView(placeholder, cell);
            View art = new View(this);
            art.setBackground(surface(0x662c4162, dp(16)));
            placeholder.addView(art, new LinearLayout.LayoutParams(-1, dp(84)));
            View caption = new View(this);
            caption.setBackground(surface(0x66546a88, dp(5)));
            LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(dp(72), dp(10));
            captionParams.topMargin = dp(12);
            placeholder.addView(caption, captionParams);
        }
    }
    private void updateBackground() {
        String selected=wallpaper();
        WallpaperCatalog.Entry entry=WallpaperCatalog.find(this,selected);
        if ("home".equals(page)) updateHomeTextContrast(entry.photo != 0);
        boolean ready=selected.equals(loadedWallpaper) && sharpWallpaper!=null;
        // Keep the previous scene during asynchronous decoding instead of flashing a placeholder.
        if(entry.photo!=0 && !ready && sharpWallpaper!=null) return;
        // The built-in blue and plain wallpapers are identical on every page.
        // Do not crossfade a full-screen drawable with itself on each rail move.
        boolean animate = motionEnabled();
        boolean blue = "blue".equals(selected);
        boolean stillBlue = blue && !animate && blueWallpaperMotion.canUseStillWallpaper();
        String key=entry.photo==0 ? (stillBlue ? "blue:still" : blue && !animate ? "blue:layers" : selected)
                : page+":"+selected+":"+("home".equals(page) ? getPreferences(MODE_PRIVATE).getInt("dim",RECOMMENDED_DIM) : 0)+":"+ready;
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
        } else if (stillBlue) next = blueWallpaperMotion.stillWallpaper();
        else next=getDrawable(R.drawable.blue_wallpaper).mutate();
        boolean sameBlueScene = blue && backgroundKey != null && backgroundKey.startsWith("blue:");
        backgroundMotion.show(next, animate && !sameBlueScene);
        backgroundKey=key;
        blueWallpaperMotion.setScene(blue && !stillBlue, animate);
    }
    private void updateHomeTextContrast() {
        updateHomeTextContrast(WallpaperCatalog.find(this, wallpaper()).photo != 0);
    }
    private void updateHomeTextContrast(boolean photo) {
        int dim = photo ? getPreferences(MODE_PRIVATE).getInt("dim", RECOMMENDED_DIM) : 100;
        int textAlpha = homeTextAlpha(dim, RECOMMENDED_DIM);
        setHomeTextBacking(content.findViewById(R.id.home_time), textAlpha);
        setHomeTextBacking(content.findViewById(R.id.home_greeting), textAlpha);
        // The softer date color needs a little more contrast than white.
        setHomeTextBacking(content.findViewById(R.id.home_date), homeTextAlpha(dim, 68));
        for (View tile : moveTiles()) setHomeTextBacking(tile.findViewById(R.id.app_name), textAlpha);
    }
    private static int homeTextAlpha(int dim, int minimumDim) {
        return dim >= minimumDim ? 0 : Math.round(255f * (minimumDim - dim) / (100 - dim));
    }
    private void setHomeTextBacking(View text, int alpha) {
        if (text == null) return;
        Drawable background = text.getBackground();
        if (alpha == 0) {
            if (background != null) text.setBackground(null);
        } else if (background != null) {
            if (background.getAlpha() != alpha) background.setAlpha(alpha);
        } else {
            Drawable backing = surface(Color.BLACK, dp(2));
            backing.setAlpha(alpha);
            text.setBackground(backing);
        }
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
    private static int nextTileDownId(List<View> tiles, int index) {
        int last = tiles.size() - 1;
        if (index / 5 == last / 5) return tiles.get(index).getId();
        // A short final row still has a reachable nearest tile.
        return tiles.get(Math.min(index + 5, last)).getId();
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
        if (movingPackage == null) {
            if (!moveKeyDownTimes.isEmpty() && trackMoveKey(event, false)) return true;
            int key = event.getKeyCode();
            if ("settings".equals(page) && (key == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || key == android.view.KeyEvent.KEYCODE_DPAD_DOWN)) {
                View current = getCurrentFocus();
                View detail = content.findViewById(R.id.settings_detail);
                if (isInside(current, detail)) {
                    if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                            && current == lastEnabledSettingsControl(detail)) return true;
                    if (key == android.view.KeyEvent.KEYCODE_DPAD_UP
                            && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                            && current.getNextFocusUpId() == R.id.settings_power) {
                        // A scrolled detail panel can consume Up before ViewRoot follows this link.
                        if (findViewById(R.id.settings_power).requestFocus()) return true;
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        }
        int key=event.getKeyCode();
        boolean direction=key==21 || key==22 || key==19 || key==20;
        boolean done=key==23 || key==66, cancel=key==4;
        if(!direction && !done && !cancel) return super.dispatchKeyEvent(event);
        trackMoveKey(event, true);
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
    private boolean trackMoveKey(android.view.KeyEvent event, boolean moving) {
        MoveKey key = new MoveKey(event.getDeviceId(), event.getKeyCode());
        int action = event.getAction();
        if (action == android.view.KeyEvent.ACTION_DOWN && moving) {
            moveKeyDownTimes.put(key, event.getDownTime());
            return false;
        }
        if (action == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            moveKeyDownTimes.remove(key);
            return false;
        }
        Long downTime = moveKeyDownTimes.get(key);
        if (downTime == null || downTime != event.getDownTime()) return false;
        // A catalog refresh can end Move before the user releases the key.
        if (action == android.view.KeyEvent.ACTION_UP) moveKeyDownTimes.remove(key);
        return !moving;
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
            tile.setNextFocusDownId(nextTileDownId(tiles, i));
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
        TextView title = sheet.findViewById(R.id.action_title); title.setText(app.label); title.setContentDescription(app.label); title.setTypeface(font);
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
            AppActionsLayout.addRow(rows,button);
        }
        dialog.setContentView(sheet);
        dialog.setOnDismissListener(d -> { if (anchor.isAttachedToWindow()) { anchor.requestFocus(); if (artwork != null) artwork.findViewById(R.id.focus_outline).setAlpha(1); } });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setWindowAnimations(motionEnabled() ? R.style.LumaDialogMotion : 0);
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = dialog.getWindow().getAttributes(); params.dimAmount = .45f; dialog.getWindow().setAttributes(params);
        }
        int width = dialogWidth(300);
        int height = AppActionsLayout.boundedHeight(sheet,width,getResources().getDisplayMetrics().heightPixels);
        dialog.show(); UiSounds.watch(dialog); dialog.getWindow().setLayout(width,height); rows.getChildAt(0).requestFocus();
    }
    private boolean motionEnabled() { return getPreferences(MODE_PRIVATE).getBoolean("animations", true) && android.animation.ValueAnimator.areAnimatorsEnabled(); }
    private void showSleepTimer() { new SleepTimerDialog(this, motionEnabled()).show(); }
    private void showSleepNow() {
        // Power is global. Dismiss the previous page before its confirmation appears.
        if (!"home".equals(page)) openPage("home");
        if (!SystemAccess.ready()) {
            Toast.makeText(this, R.string.sleep_access, Toast.LENGTH_LONG).show();
            return;
        }
        AlertDialog confirmation=new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_now)
                .setMessage(R.string.sleep_now_confirm)
                .setNegativeButton(R.string.cancel,UiSounds.dialogClick(this, null))
                .setPositiveButton(R.string.sleep_now,UiSounds.dialogClick(this, (dialog, which) -> worker.execute(() -> {
                    try {
                        if (!SystemAccess.sleepDevice(this)) throw new IllegalStateException("Sleep rejected");
                    } catch (Exception error) {
                        runOnUiThread(() -> Toast.makeText(this, R.string.sleep_failed, Toast.LENGTH_LONG).show());
                    }
                })))
                .show();
        UiSounds.watch(confirmation);
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
        int width = artwork.getIntrinsicWidth(), height = artwork.getIntrinsicHeight();
        boolean banner = width > 0 && height > 0 && width > height * 1.3f;
        icon.setPadding(banner ? 0 : dp(12), banner ? 0 : dp(8), banner ? 0 : dp(12), banner ? 0 : dp(8));
        icon.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER); icon.setImageDrawable(artwork);
    }
    @Override public void onBackPressed() {
        if("settings".equals(page)&&"about".equals(settingsCategory)&&authorsOpen) {closeAuthors();return;}
        if ("settings".equals(page) && settingsCategoryView != null && settingsCategoryView.isAttachedToWindow()) {
            View detail = content.findViewById(R.id.settings_detail);
            View current = getCurrentFocus();
            for (View node = current; node != null; ) {
                if (node == detail) {
                    if (settingsCategoryView.requestFocus()) return;
                }
                android.view.ViewParent parent = node.getParent();
                node = parent instanceof View ? (View) parent : null;
            }
        }
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
    private int dialogWidth(int desiredDp) {
        return Math.min(dp(desiredDp), Math.max(dp(200), getResources().getDisplayMetrics().widthPixels - dp(32)));
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
        try {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent == null) {
                Toast.makeText(this, R.string.app_missing, Toast.LENGTH_SHORT).show();
                return;
            }
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
        pendingFeedbackLaunch = null;
        pendingModalFocus = null;
        android.app.Dialog dialog = settingsDialog;
        settingsDialog = null;
        if (dialog != null) dialog.dismiss();
        cancelPendingAppsLayout();stopAuditRefresh();settingsDetailGeneration++;settingsDialogRequest++;
        wallpaperTimer.removeCallbacks(wallpaperRotationTick);
        if (pageMotion != null) pageMotion.cancel();
        if (backgroundMotion != null) backgroundMotion.cancel();
        catalogRefresh.close();
        worker.shutdownNow();
        wallpaperWorker.shutdownNow();
        rikka.shizuku.Shizuku.removeRequestPermissionResultListener(systemPermissionResult);
        rikka.shizuku.Shizuku.removeBinderReceivedListener(systemBinderReady);
        rikka.shizuku.Shizuku.removeBinderDeadListener(systemBinderDead);
        super.onDestroy();
    }
}

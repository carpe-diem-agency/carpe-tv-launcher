package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
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
    @Override public Activity newActivity(ClassLoader loader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity activity = super.newActivity(loader, className, intent);
        if (args != null && "yes".equals(args.getString("launcherfont"))
                && MainActivity.class.getName().equals(className)) {
            Configuration override = new Configuration();
            override.fontScale = intent.getFloatExtra("probe_font_scale", 1f);
            override.setLocale(java.util.Locale.forLanguageTag(intent.getStringExtra("probe_locale")));
            activity.applyOverrideConfiguration(override);
        }
        return activity;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        if (args != null && "yes".equals(args.getString("launcherfont"))) {
            try { LauncherFontProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("navigationaccessibility"))) {
            try { NavigationAccessibilityProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("audioexternal"))) {
            try {
                String witness = args.getString("audiowitness");
                if (witness == null) throw new IllegalArgumentException("External audio witness is required");
                AudioCoexistenceProbe.run(this, result, witness);
                finish(Activity.RESULT_OK, result);
            } catch (Throwable error) {
                result.putString("failure", error.toString());
                finish(Activity.RESULT_CANCELED, result);
            }
            return;
        }
        if (args != null && "yes".equals(args.getString("audiocoexistence"))) {
            try { AudioCoexistenceProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("uisounds"))) {
            try { UiSoundsProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("settingsstructure"))) {
            try { SettingsStructureProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("modaltouch"))) {
            try { ModalFocusProbe.run(this, result, true); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("libraryartwork"))) {
            try { LibraryArtworkProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("librarypool"))) {
            try { LibraryPoolProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("libraryinputperf"))) {
            try { LibraryInputLatencyProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("libraryperf"))) {
            try { LibraryPerformanceProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updatecallback"))) {
            try { UpdateInstallCallbackProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updateorphanpending"))) {
            try { UpdateOrphanProbe.run(this, result, true); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updaterehearsal"))) {
            try { UpdateInstallRehearsalProbe.run(this, args, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updatechain"))) {
            try { UpdateChainProbe.run(this, args, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updateenqueuecrash"))) {
            try { UpdateProcessDeathProbe.run(this, args, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updateorphan"))) {
            try { UpdateOrphanProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updatecancel"))) {
            try { UpdateCancelProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("updatebackup"))) {
            try { UpdateBackupProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("wallpapercache"))) {
            try { WallpaperCacheProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("pendinglibrary"))) {
            try { PendingLibraryStateProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("inactivelibrary"))) {
            try { InactiveLibraryStateProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("authorsstate"))) {
            try { AuthorsStateProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("modalfocus"))) {
            try { ModalFocusProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("catalogscanerror"))) {
            try { CatalogScanErrorProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("catalogstate"))) {
            try { CatalogStateProbe.run(this, result); CatalogStateProbe.verifyCorruptPreferences(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("largefont"))) {
            try { LargeFontProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("catalogbroadcast"))) {
            try { CatalogBroadcastProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("cataloglaunchdenied"))) {
            try { CatalogBroadcastProbe.verifyDeniedLaunch(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("startupdraw"))) {
            try { StartupDrawProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("longsession"))) {
            try { LongSessionProbe.run(this, args, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("librarycomposition"))) {
            try { LibraryCompositionProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("librarysoftkeys"))) {
            try { LibraryImeProbe.run(this, result, true); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("libraryime"))) {
            try { LibraryImeProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("libraryfocus"))) {
            try { LibraryFocusProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("focusring"))) {
            try { SettingsContrastProbe.runFocusRing(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("homecontrast"))) {
            try { SettingsContrastProbe.runHome(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("settingscontrast"))) {
            try { SettingsContrastProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("settingscompact"))) {
            try { SettingsFocusProbe.runCompact(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("settingsfocus"))) {
            try { SettingsFocusProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("navigationsoak"))) {
            try { NavigationSoakProbe.run(this, args, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
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
        if (args != null && "yes".equals(args.getString("corruptsaverphoto"))) {
            try { CorruptSaverPhotoProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("savervideolifecycle"))) {
            try { SaverVideoLifecycleProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("saversourcefailure"))) {
            try { SaverSourceFailureProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
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
        if (args != null && "yes".equals(args.getString("librarystate"))) {
            try { LibraryStateProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("diagnosticscompact"))) {
            try { FeedbackProbe.runCompactDiagnostics(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("feedbackbounded"))) {
            try { FeedbackProbe.runBounded(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("feedback"))) {
            try { FeedbackProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("homeroute"))) {
            try { HomeRouteProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch (Throwable error) { result.putString("failure",error.toString()); finish(Activity.RESULT_CANCELED,result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("reorder"))) {
            try { ReorderProbe.run(this,result); finish(Activity.RESULT_OK,result); }
            catch(Throwable error) {result.putString("failure",error.toString());finish(Activity.RESULT_CANCELED,result);}
            return;
        }
        if (args != null && "yes".equals(args.getString("headeredge"))) {
            try { HeaderConnectivityProbe.verifyEdges(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("headerconnectivity"))) {
            try { HeaderConnectivityProbe.run(this, result); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
            return;
        }
        if (args != null && "yes".equals(args.getString("appauditroute"))) {
            try { AppAuditProbe.run(this, result, true); finish(Activity.RESULT_OK, result); }
            catch (Throwable error) { result.putString("failure", error.toString()); finish(Activity.RESULT_CANCELED, result); }
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
        boolean hadWelcome = false;
        boolean hadAnimations = false;
        String originalWallpaper = null;
        boolean hadWallpaper = false;
        boolean originalRotation = false;
        boolean hadRotation = false;
        boolean compareWithoutMotion = args != null && "yes".equals(args.getString("noMotion"));
        boolean comparePlainWallpaper = args != null && "yes".equals(args.getString("plainWallpaper"));
        boolean comparePhotoWallpaper = args != null && "yes".equals(args.getString("photoWallpaper"));
        try {
            if (getTargetContext().getPackageName().endsWith(".publicpreview")) {
                previewPreferences = getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
                hadWelcome = previewPreferences.contains("welcome_seen");
                hadAnimations = previewPreferences.contains("animations");
                originalWelcomeSeen = previewPreferences.getBoolean("welcome_seen", false);
                originalAnimations = previewPreferences.getBoolean("animations", true);
                hadWallpaper = previewPreferences.contains("wallpaper");
                originalWallpaper = previewPreferences.getString("wallpaper", null);
                hadRotation = previewPreferences.contains(WallpaperRotation.ENABLED);
                originalRotation = previewPreferences.getBoolean(WallpaperRotation.ENABLED, false);
                if (!previewPreferences.edit().putBoolean("welcome_seen", true).commit())
                    throw new IllegalStateException("Cannot prepare isolated navigation probe");
                if (compareWithoutMotion && !previewPreferences.edit().putBoolean("animations", false).commit())
                    throw new IllegalStateException("Cannot prepare reduced-motion probe");
                if (comparePlainWallpaper && !previewPreferences.edit().putString("wallpaper", "plain")
                        .putBoolean(WallpaperRotation.ENABLED, false).commit())
                    throw new IllegalStateException("Cannot prepare plain-wallpaper probe");
                if (comparePhotoWallpaper && !previewPreferences.edit().putString("wallpaper", "alpine")
                        .putBoolean(WallpaperRotation.ENABLED, false).commit())
                    throw new IllegalStateException("Cannot prepare photo-wallpaper probe");
                result.putString("wallpaper", WallpaperCatalog.find(getTargetContext(),previewPreferences.getString("wallpaper",null)).key);
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
            if (previewPreferences != null) {
                SharedPreferences.Editor restore = previewPreferences.edit();
                if (hadWelcome) restore.putBoolean("welcome_seen", originalWelcomeSeen);
                else restore.remove("welcome_seen");
                if (hadAnimations) restore.putBoolean("animations", originalAnimations);
                else restore.remove("animations");
                if (hadWallpaper) restore.putString("wallpaper", originalWallpaper);
                else restore.remove("wallpaper");
                if (hadRotation) restore.putBoolean(WallpaperRotation.ENABLED, originalRotation);
                else restore.remove(WallpaperRotation.ENABLED);
                restore.commit();
            }
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

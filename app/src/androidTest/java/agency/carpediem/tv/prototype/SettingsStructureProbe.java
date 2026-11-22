package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only Settings IA and visible-only audit refresh checks. No system or cache actions. */
final class SettingsStructureProbe {
    private static final long WAIT_MS=8000,QUIET_MS=5250;
    private SettingsStructureProbe() { }

    static void run(Instrumentation test,Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Settings structure requires the disposable validation preview package");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        SharedPreferences soundPrefs=UiSounds.preferences(test.getTargetContext());
        Map<String,?> saved=new HashMap<>(prefs.getAll()),soundSaved=new HashMap<>(soundPrefs.getAll());
        MainActivity[] activity={null};
        AtomicBoolean paused=new AtomicBoolean(false),resumed=new AtomicBoolean(false);
        Application app=(Application)test.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPaused(Activity value) {if(value==activity[0]) {paused.set(true);resumed.set(false);}}
            @Override public void onActivityResumed(Activity value) {if(value==activity[0]) {resumed.set(true);paused.set(false);}}
            @Override public void onActivityCreated(Activity value,Bundle state) { }
            @Override public void onActivityStarted(Activity value) { }
            @Override public void onActivityStopped(Activity value) { }
            @Override public void onActivitySaveInstanceState(Activity value,Bundle state) { }
            @Override public void onActivityDestroyed(Activity value) { }
        };
        app.registerActivityLifecycleCallbacks(callbacks);
        Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare isolated welcome state");
            require(soundPrefs.edit().remove(UiSounds.ENABLED).remove(UiSounds.VOLUME).commit(),"Prepare sound defaults");
            test.setInTouchMode(false);
            MainActivity screen=activity[0]=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            select(test,screen,0);
            checkTimerMotion(test, screen, prefs);
            checkDimReset(test, screen, prefs, result);
            main(test,()->{
                ViewGroup detail=detail(screen);
                View appearance=detail.findViewById(R.id.appearance_heading),inter=detail.findViewById(R.id.interface_heading),sounds=detail.findViewById(R.id.sounds_heading);
                require(appearance!=null&&inter!=null&&sounds!=null,"Appearance must contain all three groups on the same page");
                require(screen.findViewById(R.id.ui_sounds_volume)!=null&&UiSounds.get(screen).volume()==70,
                        "Appearance sound default must be 70 percent");
            });
            for (int id : new int[]{R.id.dim_reset, R.id.animations, R.id.language_choice, R.id.ui_sounds_enabled}) {
                key(test, KeyEvent.KEYCODE_DPAD_DOWN);
                await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == screen.findViewById(id),
                        "Appearance Down did not reach " + screen.getResources().getResourceEntryName(id));
            }
            key(test,KeyEvent.KEYCODE_DPAD_UP);
            await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == screen.findViewById(R.id.language_choice),
                    "Sounds Up did not return to Language");
            select(test,screen,1);
            await(test,()->{
                TextView status=screen.findViewById(R.id.saver_cache_status);
                return status!=null&&!status.getText().toString().equals(screen.getString(R.string.saver_cache_checking));
            },"Screensaver cache usage did not load");
            main(test,()->{
                require(detail(screen).findViewById(R.id.saver_reserve)==null,"Screensaver exposes the removed reserve selector");
                require(findText(detail(screen),screen.getString(R.string.saver_system))==null,"Screensaver exposes Android system settings");
                require(findText(detail(screen),screen.getString(R.string.saver_credits))==null,"Media credits must be under Authors");
                Button clear=screen.findViewById(R.id.saver_clear);
                require(clear!=null&&clear.getText().toString().matches(".*[0-9].*"),"Clear-cache action must show its current MB amount");
                require(clear.getText().toString().startsWith(screen.getString(R.string.saver_clear,0).replaceFirst("0.*$","")),"Clear-cache label is inconsistent");
            });
            select(test,screen,5);
            main(test,()->{
                ViewGroup root=detail(screen);
                require(buttonCount(root)==3,"About must have exactly three primary buttons");
                for(int id:new int[]{R.id.about_updates,R.id.about_feedback,R.id.about_authors})
                    require(root.findViewById(id) instanceof Button,"About primary action is missing");
                View company=root.findViewById(R.id.about_company);
                require(company instanceof TextView&&company.isClickable(),"Agency name must be the website text link");
                root.findViewById(R.id.about_authors).performClick();
            });
            await(test,()->screen.findViewById(R.id.authors_back)!=null,"Authors subview did not open");
            main(test,()->{
                for(int id:new int[]{R.id.authors_back,R.id.authors_code_license,R.id.authors_third_party,R.id.authors_font_license,R.id.authors_media_credits,R.id.authors_github})
                    require(detail(screen).findViewById(id)!=null,"Authors subview action is missing");
                require(detail(screen).findViewById(R.id.about_feedback)==null,"Authors was added alongside instead of replacing primary About actions");
            });
            key(test,KeyEvent.KEYCODE_BACK);
            await(test,()->screen.getCurrentFocus()!=null&&screen.getCurrentFocus().getId()==R.id.about_authors,"Back did not return to Authors entry");
            verifyUsageBoundaries(screen);
            select(test,screen,4);
            await(test,()->{
                ProgressBar ram=screen.findViewById(R.id.device_ram_bar),storage=screen.findViewById(R.id.device_storage_bar);
                return ram!=null&&storage!=null&&ram.getTag() instanceof long[]&&storage.getTag() instanceof long[]
                        &&ram.getWidth()>0&&storage.getWidth()>0;
            },"Device usage model did not load");
            main(test,()->{
                checkBar(screen,R.id.device_ram_label,R.id.device_ram_bar);
                checkBar(screen,R.id.device_storage_label,R.id.device_storage_bar);
                require(buttonCount(detail(screen))<=4,"Device added an unnecessary memory submenu or extra primary controls");
                verifyUsageBarStates(screen, R.id.device_ram_label, R.id.device_ram_bar, R.string.device_ram);
                verifyUsageBarStates(screen, R.id.device_storage_label, R.id.device_storage_bar, R.string.device_storage);
                screen.findViewById(R.id.device_diagnostics).performClick();
            });
            result.putString("device_usage_states", "PASS: real usage and ordering, green/amber/red boundaries, unavailable and recovery on both bars");
            await(test,()->modal(screen,R.id.diagnostic_qr)!=null,"Diagnostic sheet did not open");
            String[] raw={null};
            main(test,()->{
                View modal=modal(screen,R.id.diagnostic_qr);
                TextView text=modal.findViewById(R.id.diagnostic_text);
                ImageView image=modal.findViewById(R.id.diagnostic_qr);
                DiagnosticReport.Report report=DiagnosticReport.capture(screen);
                require(text!=null&&report.displayText().contentEquals(text.getText()),"Diagnostic display differs from the localized compact report");
                require(image.getDrawable() instanceof BitmapDrawable,"Diagnostic QR has no generated bitmap");
                raw[0]=decode(((BitmapDrawable)image.getDrawable()).getBitmap());
                require(report.text().equals(raw[0]),"Diagnostic QR payload differs from the approved raw report");
            });
            verifyReport(raw[0]);
            main(test,()->{
                View panel=modal(screen,R.id.diagnostic_qr);
                TextView help=panel.findViewById(R.id.diagnostic_qr_help);
                require(help!=null&&help.getText().length()>0,"Raw diagnostic QR needs an explanation");
                View bug=panel.findViewById(R.id.diagnostic_report_bug);
                require(bug!=null&&bug.isFocusable()&&bug.requestFocus(),"Diagnostic Report bug action cannot receive focus");
            });
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            await(test,()->issueQr(screen)!=null,"Diagnostic Report bug did not open Feedback QR");
            main(test,()->{
                require(modal(screen,R.id.diagnostic_qr)==null,"Raw report stayed stacked under Feedback");
                ImageView image=issueQr(screen);
                require(image.getDrawable() instanceof BitmapDrawable,"Issue QR has no generated bitmap");
                android.net.Uri uri=android.net.Uri.parse(decode(((BitmapDrawable)image.getDrawable()).getBitmap()));
                require("https".equals(uri.getScheme())&&"github.com".equals(uri.getAuthority())
                        &&"/carpe-diem-agency/carpe-tv-launcher/issues/new".equals(uri.getPath()),"Report bug leaves approved GitHub Issue route");
                require(uri.getQueryParameterNames().equals(new java.util.HashSet<>(java.util.Arrays.asList("template","device")))
                        &&"bug.yml".equals(uri.getQueryParameter("template")),"Report bug did not select Bug issue template");
                require(raw[0].equals(uri.getQueryParameter("device")),"Report bug did not preserve the raw diagnostic payload");
            });
            key(test,KeyEvent.KEYCODE_BACK);
            await(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()!=null
                    &&screen.getCurrentFocus().getId()==R.id.device_diagnostics,"Diagnostics Back lost focus");
            // Open only the two organizational sheets; never activate any child action.
            for(int group:new int[]{R.id.device_home_actions,R.id.device_system_actions}) {
                main(test,()->screen.findViewById(group).performClick());
                await(test,()->anyModal(screen)!=null,"Device organizational sheet did not open");
                key(test,KeyEvent.KEYCODE_BACK);
                await(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()!=null&&screen.getCurrentFocus().getId()==group,
                        "Device organizational sheet did not restore its entry");
            }
            verifyAudit(test,screen,paused,resumed,result);
            result.putString("settings_structure","PASS: Appearance groups/language, simplified Screensaver, About/Authors, inline Device bars/raw diagnostics and visible-only audit refresh");
            result.putString("settings_structure_scope","No system, updater, external-link, language-choice or cache-clear actions activated; QR decoded from generated image pixels");
        } catch(Exception|Error failure) {primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(activity[0]!=null) try {finish(test,activity[0]);}catch(Exception|Error failure) {cleanup=failure;}
            try {
                if(activity[0]!=null) quiescent(test,activity[0]);
                restore(prefs,saved);restore(soundPrefs,soundSaved);
            } catch(Exception|Error failure) {cleanup=append(cleanup,failure);}
            app.unregisterActivityLifecycleCallbacks(callbacks);
            if(cleanup!=null) {if(primary!=null) primary.addSuppressed(cleanup);else rethrow(cleanup);}
        }
    }

    private static void checkDimReset(Instrumentation test, MainActivity screen,
            SharedPreferences prefs, Bundle result) throws Exception {
        int[] direction = {KeyEvent.KEYCODE_DPAD_RIGHT};
        main(test, () -> {
            SeekBar slider = screen.findViewById(R.id.wallpaper_dim);
            require(slider != null && slider.requestFocus(), "Cannot focus the dimming slider");
            if (slider.getProgress() == slider.getMax()) direction[0] = KeyEvent.KEYCODE_DPAD_LEFT;
        });
        key(test, direction[0]);
        int[] changed = {-1};
        main(test, () -> {
            SeekBar slider = screen.findViewById(R.id.wallpaper_dim);
            changed[0] = slider.getProgress();
            require(prefs.getInt("dim", -1) == changed[0], "Dimming input did not persist the value");
        });
        if (changed[0] == 60) key(test, direction[0]);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        main(test, () -> require(screen.getCurrentFocus() == screen.findViewById(R.id.dim_reset)
                && prefs.getInt("dim", -1) != 60, "Dimming Reset entry or changed value was lost"));
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        main(test, () -> {
            SeekBar slider = screen.findViewById(R.id.wallpaper_dim);
            TextView value = screen.findViewById(R.id.dim_value);
            require(slider.getProgress() == 60 && prefs.getInt("dim", -1) == 60 && "60%".contentEquals(value.getText()),
                    "Reset did not restore the stored and displayed 60 percent");
            require(screen.getCurrentFocus() == screen.findViewById(R.id.dim_reset), "Reset lost button focus");
        });
        key(test, KeyEvent.KEYCODE_DPAD_UP);
        main(test, () -> require(screen.getCurrentFocus() == screen.findViewById(R.id.wallpaper_dim),
                "Up from Reset did not return to the dimming slider"));
        result.putString("appearance_dim_reset", "PASS: D-pad adjustment persisted; Reset restored 60% and kept focus; Up returned to dimming");
    }

    private static void verifyAudit(Instrumentation test,MainActivity screen,AtomicBoolean paused,AtomicBoolean resumed,Bundle result) throws Exception {
        select(test,screen,3);
        await(test,()->screen.auditRefreshStats().applied>0&&!screen.auditRefreshStats().inFlight,"Initial audit snapshot did not apply");
        await(test,()->{View row=firstAuditRow(detail(screen));return row!=null&&row.getWidth()>0&&row.getHeight()>0;},"Audit rows did not lay out");
        View[] focus={null};
        main(test,()->{
            require(auditRows(detail(screen))==4,"Audit must contain exactly four package rows");
            require(focusableCount(detail(screen))==5,"Audit must contain only four rows and Manage all apps");
            require(screen.findViewById(R.id.audit_manage_apps)!=null,"Audit Manage all apps is missing");
            require(screen.findViewById(R.id.shizuku_status)==null,"Audit exposes removed system-access status");
            focus[0]=firstAuditRow(detail(screen));focus[0].requestFocus();
        });
        coalescing(test,screen);
        for (int tick = 1; tick <= 2; tick++) {
            MainActivity.AuditRefreshStats before=snapshot(test,screen);
            long timerWaitStart=SystemClock.uptimeMillis();
            try {
                await(test,()->screen.auditRefreshStats().applied>before.applied&&!screen.auditRefreshStats().inFlight,"Visible audit did not refresh after its five-second interval");
            } catch(AssertionError timeout) {
                String[] diagnostic={null};
                main(test,()->{
                    MainActivity.AuditRefreshStats after=screen.auditRefreshStats();
                    Field page=MainActivity.class.getDeclaredField("page"),category=MainActivity.class.getDeclaredField("settingsCategory");page.setAccessible(true);category.setAccessible(true);
                    diagnostic[0]="elapsedMs="+(SystemClock.uptimeMillis()-timerWaitStart)+" baseline={"+auditDiagnostic(before)+"} final={"+auditDiagnostic(after)+"} page="+page.get(screen)+" category="+category.get(screen)+" windowFocus="+screen.hasWindowFocus();
                });
                result.putString("audit_timer_failure",diagnostic[0]);
                throw new AssertionError("Visible audit did not refresh after its five-second interval: "+diagnostic[0],timeout);
            }
            long elapsed = SystemClock.uptimeMillis() - timerWaitStart;
            result.putLong("audit_visible_tick_" + tick + "_ms", elapsed);
            require(elapsed >= 4700, "Audit read sooner than the five-second interval: " + elapsed);
            main(test, () -> {
                MainActivity.AuditRefreshStats after = screen.auditRefreshStats();
                require(after.started == before.started + 1 && after.applied == before.applied + 1 && after.pendingTick,
                        "Audit interval did not perform exactly one read and schedule the next");
                require(screen.getCurrentFocus() == focus[0] && focus[0].isAttachedToWindow(),
                        "Automatic audit refresh rebuilt rows or lost focus");
            });
        }
        select(test,screen,0);quiet(test,screen,"category exit");
        select(test,screen,3);settled(test,screen);
        main(test,()->screen.findViewById(R.id.nav_home).requestFocus());quiet(test,screen,"page exit");
        select(test,screen,3);settled(test,screen);
        main(test,()->{focus[0]=firstAuditRow(detail(screen));focus[0].requestFocus();});
        paused.set(false);resumed.set(false);
        main(test,()->require(screen.moveTaskToBack(true),"ROUTE_UNVERIFIED: validation task did not move to background"));
        await(test,()->paused.get()&&!screen.auditRefreshStats().active,"ROUTE_UNVERIFIED: validation Activity did not pause");
        quiet(test,screen,"pause");
        MainActivity.AuditRefreshStats background=snapshot(test,screen);
        main(test,()->screen.startActivity(new Intent(screen,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
        await(test,()->resumed.get()&&screen.auditRefreshStats().active&&screen.hasWindowFocus(),"ROUTE_UNVERIFIED: validation Activity did not resume");
        main(test,()->require(screen.auditRefreshStats().started>background.started,"Resume did not immediately request a fresh audit snapshot"));
        settled(test,screen);
        main(test,()->require(screen.getCurrentFocus()==focus[0],"Pause/resume lost the selected audit row"));
        finish(test,screen);quiet(test,screen,"destroy");
        result.putString("audit_refresh","PASS: two visible five-second ticks, bounded coalescing, focus preservation, category/page/pause/destroy cancellation and immediate resume");
    }
    private static void settled(Instrumentation test,MainActivity screen) throws Exception {
        await(test,()->{MainActivity.AuditRefreshStats s=screen.auditRefreshStats();return s.active&&!s.inFlight&&s.pendingTick;},"Audit did not settle to one pending tick");
    }
    private static void coalescing(Instrumentation test,MainActivity screen) throws Exception {
        settled(test,screen);
        ExecutorService worker=executor(screen,"worker");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<Boolean> gate=worker.submit(()->{entered.countDown();return release.await(15,TimeUnit.SECONDS);});
        Throwable primary=null;
        try {
            require(entered.await(8,TimeUnit.SECONDS),"Audit coalescing barrier did not start");
            MainActivity.AuditRefreshStats before=snapshot(test,screen);
            main(test,()->{
                Method refresh=MainActivity.class.getDeclaredMethod("refreshApplicationAudit");refresh.setAccessible(true);
                for(int i=0;i<20;i++) refresh.invoke(screen);
                MainActivity.AuditRefreshStats s=screen.auditRefreshStats();
                require(s.started-before.started==1&&s.inFlight&&s.rerunRequested,"Busy audit requests queued more than one active job");
            });
            release.countDown();require(gate.get(8,TimeUnit.SECONDS),"Audit coalescing barrier timed out");
            settled(test,screen);
            MainActivity.AuditRefreshStats after=snapshot(test,screen);
            require(after.started-before.started==2&&after.completed-before.completed==2&&after.applied-before.applied==2,
                    "Twenty audit requests did not coalesce to one job plus one rerun");
        } catch(Exception|Error failure) {primary=failure;throw failure;}
        finally {
            release.countDown();
            try {gate.get(8,TimeUnit.SECONDS);worker.submit(()->{}).get(8,TimeUnit.SECONDS);test.waitForIdleSync();}
            catch(Exception|Error failure) {if(primary!=null) primary.addSuppressed(failure);else rethrow(failure);}
        }
    }
    private static void quiet(Instrumentation test,MainActivity screen,String phase) throws Exception {
        main(test,()->{MainActivity.AuditRefreshStats s=screen.auditRefreshStats();require(!s.active&&!s.pendingTick&&!s.rerunRequested,"Audit scheduler remained active after "+phase);});
        MainActivity.AuditRefreshStats before=snapshot(test,screen);
        SystemClock.sleep(QUIET_MS);test.waitForIdleSync();
        MainActivity.AuditRefreshStats after=snapshot(test,screen);
        require(after.started==before.started&&after.applied==before.applied&&!after.active&&!after.pendingTick,
                "Hidden audit performed work or bound stale results after "+phase);
    }
    private static String auditDiagnostic(MainActivity.AuditRefreshStats stats) {
        return "active="+stats.active+" inFlight="+stats.inFlight+" pendingTick="+stats.pendingTick+" rerunRequested="+stats.rerunRequested
                +" started="+stats.started+" completed="+stats.completed+" applied="+stats.applied;
    }
    private static MainActivity.AuditRefreshStats snapshot(Instrumentation test,MainActivity screen) throws Exception {
        MainActivity.AuditRefreshStats[] value={null};main(test,()->value[0]=screen.auditRefreshStats());return value[0];
    }
    static void select(Instrumentation test,MainActivity screen,int category) throws Exception {
        main(test,()->{
            screen.findViewById(R.id.nav_settings).requestFocus();
            ViewGroup categories=screen.findViewById(R.id.settings_categories);
            require(categories!=null&&categories.getChildCount()>category,"Settings category is missing");categories.getChildAt(category).requestFocus();
        });
        test.waitForIdleSync();
    }
    private static ViewGroup detail(MainActivity screen) {return screen.findViewById(R.id.settings_detail);}
    private static void checkBar(MainActivity screen,int labelId,int barId) {
        TextView label=screen.findViewById(labelId);ProgressBar bar=screen.findViewById(barId);
        require(label!=null&&bar!=null&&bar.getTag() instanceof long[],"Device usage lacks a bound model");
        long[] bytes=(long[])bar.getTag();require(bytes.length==2&&bytes[1]>0&&bytes[0]>=0&&bytes[0]<=bytes[1],"Device usage byte bounds are invalid");
        double expected=100d*bytes[0]/bytes[1];
        require(bar.getMax()>0&&bar.getProgress()>=0&&bar.getProgress()<=bar.getMax()
                &&Math.abs(100d*bar.getProgress()/bar.getMax()-expected)<=1,
                "Device usage bar disagrees with its byte values");
        int freeId=labelId==R.id.device_ram_label?R.id.device_ram_free:R.id.device_storage_free;
        TextView free=screen.findViewById(freeId);
        require(DeviceUsageText.pair(screen,bytes[0],bytes[1]).contentEquals(label.getText()),
                "Device compact pair disagrees with the bound bytes");
        require(free!=null&&DeviceUsageText.available(screen,bytes[1]-bytes[0]).contentEquals(free.getText()),
                "Device free-space line disagrees with bound bytes");
        require(free.getTop()>=bar.getBottom(),"Free-space line must appear below its usage bar");
        require(label.getLineCount()==1,"Compact usage pair wrapped");
        if(free.getLayout()!=null)for(int line=0;line<free.getLayout().getLineCount();line++)
            require(free.getLayout().getEllipsisCount(line)==0,"Free-space amount was ellipsized");
        require(bar.getWidth()>0&&bar.getHeight()>0&&bar.getLeft()>=0
                &&bar.getRight()<=((View)bar.getParent()).getWidth(),"Device usage bar exceeds its container");
        if(label.getLayout()!=null) for(int line=0;line<label.getLayout().getLineCount();line++)
            require(label.getLayout().getEllipsisCount(line)==0,"Device usage numbers were ellipsized");
    }
    private static void verifyUsageBarStates(MainActivity screen, int labelId, int barId, int titleText) throws Exception {
        boolean ram = barId == R.id.device_ram_bar;
        TextView title = screen.findViewById(ram ? R.id.device_ram_title : R.id.device_storage_title);
        TextView label = screen.findViewById(labelId);
        TextView free = screen.findViewById(ram ? R.id.device_ram_free : R.id.device_storage_free);
        ProgressBar bar = screen.findViewById(barId);
        require(screen.getString(titleText).contentEquals(title.getText()) && title.getParent() == label.getParent()
                && title.getRight() <= label.getLeft() && !bar.isFocusable(), "Device title, amount or focus order is incorrect");
        Method bind = MainActivity.class.getDeclaredMethod("bindUsageBar", int.class, int.class, int.class, long.class, long.class);
        bind.setAccessible(true);
        long[] original = (long[]) bar.getTag();
        int[] fractions = {0, 649, 650, 849, 850, 1000};
        int[] colors = {0xff75c7a0, 0xff75c7a0, 0xffe8be6b, 0xffe8be6b, 0xffed7b78, 0xffed7b78};
        try {
            bind.invoke(screen, labelId, barId, titleText, 0L, 0L);
            require(bar.getVisibility() == View.GONE
                    && screen.getString(R.string.diagnostic_unknown).contentEquals(label.getText())
                    && screen.getString(R.string.device_metrics_unavailable).contentEquals(free.getText()),
                    "Unavailable usage must hide the bar and display its unknown state");
            for (int i = 0; i < fractions.length; i++) {
                long used = fractions[i] * SaverCache.MIB;
                long total = 1000L * SaverCache.MIB;
                bind.invoke(screen, labelId, barId, titleText, used, total);
                require(bar.getVisibility() == View.VISIBLE && bar.getProgress() == fractions[i]
                        && bar.getProgressTintList().getDefaultColor() == colors[i],
                        "Usage color or progress differs at " + fractions[i] + "/1000");
                require(DeviceUsageText.pair(screen, used, total).contentEquals(label.getText())
                        && DeviceUsageText.available(screen, total - used).contentEquals(free.getText()),
                        "Usage value or free space differs at " + fractions[i] + "/1000");
            }
        } finally {
            bind.invoke(screen, labelId, barId, titleText, original[0], original[1]);
        }
    }
    private static void verifyUsageBoundaries(android.content.Context base) {
        long gib=1024L*1024*1024;
        for(String tag:new String[]{"en","uk"}) {
            android.content.res.Configuration cfg=new android.content.res.Configuration(base.getResources().getConfiguration());
            java.util.Locale locale=java.util.Locale.forLanguageTag(tag);cfg.setLocale(locale);
            android.content.Context context=base.createConfigurationContext(cfg);
            java.text.NumberFormat one=java.text.NumberFormat.getNumberInstance(locale);
            one.setGroupingUsed(false);one.setMinimumFractionDigits(1);one.setMaximumFractionDigits(1);
            String gb=context.getString(R.string.device_unit_gb),mb=context.getString(R.string.device_unit_mb);
            String pair=DeviceUsageText.pair(context,gib/2,2*gib);
            require(pair.contains(one.format(.5))&&pair.contains(one.format(2))&&pair.contains(gb),"Mixed used/total sizes must use one common GB unit");
            require(DeviceUsageText.available(context,0).equals(context.getString(R.string.device_free,"0 "+mb)),"Zero free-space must use MB");
            require(DeviceUsageText.available(context,gib-SaverCache.MIB).equals(context.getString(R.string.device_free,"1023 "+mb)),"Free-space below 1 GiB must remain MB");
            require(DeviceUsageText.available(context,gib).equals(context.getString(R.string.device_free,one.format(1)+" "+gb)),"Free-space 1 GiB boundary must use localized decimal GB");
        }
    }
    private static String decode(Bitmap bitmap) throws Exception {
        int width=bitmap.getWidth(),height=bitmap.getHeight();int[] pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);
        return new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.RGBLuminanceSource(width,height,pixels)))).getText();
    }
    private static void verifyReport(String report) {
        String[] lines=report.split("\\n",-1),keys={"Carpe TV: ","Device: ","Android: ","Build: ","Output: ","Interface: ","Developer options: "};
        require(lines.length==keys.length,"Diagnostic report contains fields outside the approved compact allowlist");
        for(int i=0;i<keys.length;i++) {
            require(lines[i].startsWith(keys[i])&&lines[i].length()>keys[i].length(),"Diagnostic report field allowlist mismatch");
            for(int c=0;c<lines[i].length();c++) require(!Character.isISOControl(lines[i].charAt(c)),"Diagnostic report contains control characters");
        }
        require(lines[6].matches("Developer options: (enabled|disabled|unknown)"),"Developer-mode diagnostic state is invalid");
    }
    private static ImageView issueQr(MainActivity screen) {
        for(View root:WindowInspector.getGlobalWindowViews())if(root!=screen.getWindow().getDecorView()
                &&root.isAttachedToWindow()&&root.isShown()){
            ImageView found=describedImage(root,screen.getString(R.string.issue_qr));if(found!=null)return found;
        }return null;
    }
    private static ImageView describedImage(View root,String description){
        if(root instanceof ImageView&&description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return(ImageView)root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){ImageView found=describedImage(group.getChildAt(i),description);if(found!=null)return found;}}return null;
    }
    private static View modal(MainActivity screen,int childId) {
        for(View root:WindowInspector.getGlobalWindowViews()) if(root!=screen.getWindow().getDecorView()
                &&root.isAttachedToWindow()&&root.isShown()&&root.findViewById(childId)!=null) return root;
        return null;
    }
    private static View anyModal(MainActivity screen) {
        for(View root:WindowInspector.getGlobalWindowViews()) if(root!=screen.getWindow().getDecorView()
                &&root.isAttachedToWindow()&&root.isShown()&&root.hasWindowFocus()) return root;
        return null;
    }
    private static void checkTimerMotion(Instrumentation test, MainActivity screen, SharedPreferences prefs) throws Exception {
        boolean hadPreference = prefs.contains("animations");
        boolean original = prefs.getBoolean("animations", true);
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                require(prefs.edit().putBoolean("animations", enabled).commit(), "Set isolated animation preference");
                main(test, () -> require(screen.findViewById(R.id.settings_timer).requestFocus(),
                        "Settings timer could not receive focus"));
                key(test, KeyEvent.KEYCODE_DPAD_CENTER);
                await(test, () -> anyModal(screen) instanceof ViewGroup, "Timer dialog did not open");
                boolean effective = enabled && android.animation.ValueAnimator.areAnimatorsEnabled();
                main(test, () -> {
                    ViewGroup root = (ViewGroup) anyModal(screen);
                    require(findText(root, screen.getString(R.string.sleep_timer)) != null,
                            "Expected the timer dialog");
                    WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) root.getLayoutParams();
                    require(attrs.windowAnimations == (effective ? R.style.LumaDialogMotion : 0),
                            "Timer window ignored the animation preference");
                    View close = findText(root, screen.getString(R.string.sleep_close));
                    require(close != null && close.requestFocus(), "Timer Close did not receive focus");
                });
                float expected = effective ? 1.03f : 1f;
                await(test, () -> {
                    ViewGroup root = (ViewGroup) anyModal(screen);
                    View close = root == null ? null : findText(root, screen.getString(R.string.sleep_close));
                    return close != null && close.hasFocus()
                            && Math.abs(close.getScaleX() - expected) < 0.001f
                            && Math.abs(close.getScaleY() - expected) < 0.001f;
                }, "Timer focus motion did not match the animation preference");
                key(test, KeyEvent.KEYCODE_BACK);
                await(test, () -> anyModal(screen) == null, "Back did not dismiss the timer dialog");
            }
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (hadPreference) editor.putBoolean("animations", original);
            else editor.remove("animations");
            require(editor.commit(), "Restore isolated animation preference");
        }
    }
    private static View findText(ViewGroup group,String text) {
        for(int i=0;i<group.getChildCount();i++) {View child=group.getChildAt(i);if(child instanceof TextView&&text.contentEquals(((TextView)child).getText())) return child;
            if(child instanceof ViewGroup) {View found=findText((ViewGroup)child,text);if(found!=null) return found;}}
        return null;
    }
    private static int buttonCount(ViewGroup group) {int count=0;for(int i=0;i<group.getChildCount();i++) {View child=group.getChildAt(i);if(child instanceof Button) count++;else if(child instanceof ViewGroup) count+=buttonCount((ViewGroup)child);}return count;}
    private static int focusableCount(ViewGroup group) {int count=0;for(int i=0;i<group.getChildCount();i++) {View child=group.getChildAt(i);if(child.isFocusable()) count++;else if(child instanceof ViewGroup) count+=focusableCount((ViewGroup)child);}return count;}
    private static int auditRows(ViewGroup group) {int count=0;for(int i=0;i<group.getChildCount();i++) {View child=group.getChildAt(i);if(child.getTag() instanceof String&&((String)child.getTag()).startsWith("audit:")) count++;else if(child instanceof ViewGroup) count+=auditRows((ViewGroup)child);}return count;}
    private static View firstAuditRow(ViewGroup group) {for(int i=0;i<group.getChildCount();i++) {View child=group.getChildAt(i);if(child.getTag() instanceof String&&((String)child.getTag()).startsWith("audit:")) return child;if(child instanceof ViewGroup) {View found=firstAuditRow((ViewGroup)child);if(found!=null) return found;}}return null;}
    interface Action {void run() throws Exception;}
    interface Check {boolean get() throws Exception;}
    static void main(Instrumentation test,Action action) throws Exception {Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null) rethrow(failure[0]);}
    static void await(Instrumentation test,Check check,String message) throws Exception {long end=SystemClock.uptimeMillis()+WAIT_MS;while(SystemClock.uptimeMillis()<end){boolean[] yes={false};main(test,()->yes[0]=check.get());if(yes[0])return;SystemClock.sleep(30);}throw new AssertionError(message);}
    private static void key(Instrumentation test,int key) {test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private static ExecutorService executor(MainActivity screen,String name) throws Exception {Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return(ExecutorService)field.get(screen);}
    static void finish(Instrumentation test,MainActivity screen) throws Exception {
        main(test,()->{if(!screen.isDestroyed())screen.finish();});
        await(test,screen::isDestroyed,"Settings fixture Activity did not finish");
        // Let onDestroy stop the workers after the Activity unregisters its receivers.
        for(String name:new String[]{"worker","wallpaperWorker"})
            require(executor(screen,name).awaitTermination(15,TimeUnit.SECONDS),"Settings fixture worker did not terminate: "+name);
        test.waitForIdleSync();
    }
    static void quiescent(Instrumentation test,MainActivity screen) throws Exception {main(test,()->require(screen.isDestroyed(),"Settings fixture Activity remains active"));for(String name:new String[]{"worker","wallpaperWorker"})require(executor(screen,name).isTerminated(),"Settings fixture worker remains active");}
    @SuppressWarnings("unchecked") static void restore(SharedPreferences prefs,Map<String,?> saved) {
        SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()) {String key=entry.getKey();Object value=entry.getValue();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));else throw new IllegalStateException("Unexpected preference type");}require(edit.commit(),"Cannot restore Settings fixture preferences");
    }
    private static Throwable append(Throwable first,Throwable next) {if(first==null)return next;first.addSuppressed(next);return first;}
    private static void rethrow(Throwable failure)throws Exception{if(failure instanceof Error)throw(Error)failure;if(failure instanceof Exception)throw(Exception)failure;throw new AssertionError(failure);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

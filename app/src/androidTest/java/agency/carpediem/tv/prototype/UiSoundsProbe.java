package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Device-only event/playback checks. Positive stream IDs do not establish audibility. */
final class UiSoundsProbe {
    private static final long WAIT_MS=8000;
    private UiSoundsProbe() { }

    static void run(Instrumentation test,Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "UI sound probe requires the disposable validation preview package");
        SharedPreferences appPrefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        SharedPreferences soundPrefs=UiSounds.preferences(test.getTargetContext());
        Map<String,?> appSaved=new HashMap<>(appPrefs.getAll()),soundSaved=new HashMap<>(soundPrefs.getAll());
        Application application=(Application)test.getTargetContext().getApplicationContext();
        List<MainActivity> sessions=new CopyOnWriteArrayList<>();
        AtomicReference<MainActivity> recreated=new AtomicReference<>();
        AtomicBoolean checkPause=new AtomicBoolean(false),paused=new AtomicBoolean(false),pendingOnPause=new AtomicBoolean(true);
        AtomicReference<Throwable> callbackFailure=new AtomicReference<>();
        UiSounds[] sounds={null};
        main(test,()->sounds[0]=UiSounds.get(test.getTargetContext()));
        // Register after the controller, so its own lifecycle callback runs first.
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity,Bundle state) {
                if(activity instanceof MainActivity) {sessions.add((MainActivity)activity);recreated.set((MainActivity)activity);}
            }
            @Override public void onActivityPaused(Activity activity) {
                if(activity instanceof MainActivity&&checkPause.get()) try {
                    pendingOnPause.set(sounds[0].stats().previewPending);paused.set(true);
                } catch(Throwable failure) {callbackFailure.set(failure);}
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity,Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        application.registerActivityLifecycleCallbacks(callbacks);
        Throwable primaryFailure=null;
        try {
            require(appPrefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare validation welcome state");
            require(soundPrefs.edit().remove(UiSounds.ENABLED).remove(UiSounds.VOLUME).commit(),"Prepare sound defaults");
            test.setInTouchMode(false);
            UiSounds.Stats beforeLaunch=stats(test,sounds[0]);
            MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(test,()->sounds[0].ready()&&screen.hasWindowFocus(),"Sound samples did not become ready");
            UiSounds.Stats startup=stats(test,sounds[0]);
            require(noEvents(beforeLaunch,startup)&&noPlays(beforeLaunch,startup),"Startup generated a UI sound");
            main(test,()->{
                require(sounds[0].enabled()&&sounds[0].volume()==70,"Default sounds must be enabled at 70 percent");
                sounds[0].setVolume(-5);require(sounds[0].volume()==0,"Volume lower bound");
                sounds[0].setVolume(105);require(sounds[0].volume()==100,"Volume upper bound");
                sounds[0].setVolume(70);
                screen.findViewById(R.id.nav_home).requestFocus();
            });
            UiSounds.Stats boundary=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_UP);
            main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.nav_home),"Rail boundary moved focus"));
            UiSounds.Stats unchanged=stats(test,sounds[0]);
            require(noEvents(boundary,unchanged)&&noPlays(boundary,unchanged),"Boundary key generated a sound without a focus change");
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.nav_apps),"Down did not select Apps rail"));
            UiSounds.Stats moved=stats(test,sounds[0]);
            require(moved.focusEvents-unchanged.focusEvents==1&&moved.focusPlays-unchanged.focusPlays==1
                    &&moved.lastFocusStream>0,"Actual focus change did not start exactly one loaded focus sample");
            // Rail handlers are custom. Exercise native ViewRootImpl focus
            // navigation too: tiles use nextFocus IDs without a custom key handler.
            await(test,()->tileCount(screen.findViewById(R.id.apps_grid))>=2,"Native focus case requires at least two loaded app tiles");
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
            View[] gridFirst={null};
            int[] nextGridId={View.NO_ID};
            main(test,()->{
                View tile=screen.getCurrentFocus();
                require(tile!=null&&tile.getTag() instanceof String,"Apps rail did not enter a real app tile");
                gridFirst[0]=tile;nextGridId[0]=tile.getNextFocusRightId();
                require(nextGridId[0]!=View.NO_ID&&nextGridId[0]!=tile.getId(),"Native focus case requires at least two app tiles");
            });
            // Use a deliberate remote cadence beyond the 55 ms audio throttle;
            // this is not a frame-time or input-latency assertion.
            SystemClock.sleep(80);
            UiSounds.Stats beforeGrid=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
            main(test,()->require(screen.getCurrentFocus()!=null&&screen.getCurrentFocus().getId()==nextGridId[0],
                    "Native grid Right did not focus the adjacent tile"));
            UiSounds.Stats afterGrid=stats(test,sounds[0]);
            require(afterGrid.focusEvents-beforeGrid.focusEvents==1&&afterGrid.focusPlays-beforeGrid.focusPlays==1
                    &&afterGrid.lastFocusStream>0,"Native horizontal grid focus did not start exactly one focus sample");
            main(test,()->{
                View boundaryTile=rightBoundary(screen.findViewById(R.id.apps_grid));
                require(boundaryTile!=null,"Grid right boundary is missing");boundaryTile.requestFocus();
            });
            UiSounds.Stats gridBoundary=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
            UiSounds.Stats afterBoundary=stats(test,sounds[0]);
            require(noEvents(gridBoundary,afterBoundary)&&noPlays(gridBoundary,afterBoundary),"Grid boundary emitted sound without moving focus");
            main(test,()->require(gridFirst[0].requestFocus(),"Cannot restore grid origin before rapid native input"));
            SystemClock.sleep(80);
            UiSounds.Stats beforeBurst=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);key(test,KeyEvent.KEYCODE_DPAD_LEFT);
            UiSounds.Stats afterBurst=stats(test,sounds[0]);
            long burstPlays=afterBurst.focusPlays-beforeBurst.focusPlays;
            require(afterBurst.focusEvents-beforeBurst.focusEvents==2&&burstPlays>=1&&burstPlays<=2,
                    "Rapid real focus changes lost events or exceeded one sound per change");
            main(test,()->screen.findViewById(R.id.nav_apps).requestFocus());
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            UiSounds.Stats beforeOk=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            UiSounds.Stats afterOk=stats(test,sounds[0]);
            require(afterOk.clickEvents-beforeOk.clickEvents==1&&afterOk.clickPlays-beforeOk.clickPlays==1
                    &&afterOk.lastClickStream>0,"One actual OK must start one click sample, without duplicate dispatch");
            require(afterOk.focusEvents==beforeOk.focusEvents,"OK-driven focus restoration was misclassified as D-pad navigation");
            await(test,()->screen.findViewById(R.id.ui_sounds_enabled)!=null,"Sound controls are missing from Appearance");
            main(test,()->{
                for(int id:new int[]{R.id.ui_sounds_enabled,R.id.ui_sounds_volume,R.id.ui_sounds_preview}) {
                    View view=screen.findViewById(id);
                    require(view!=null&&view.isFocusable()&&view.isEnabled(),"Sound control is not remotely focusable");
                }
                screen.findViewById(R.id.ui_sounds_enabled).requestFocus();
            });
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.ui_sounds_volume),"Down did not reach sound volume"));
            int initialVolume=volume(test,sounds[0]);
            UiSounds.Stats beforeSlider=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
            main(test,()->{
                SeekBar slider=screen.findViewById(R.id.ui_sounds_volume);
                require(slider.getProgress()>initialVolume&&slider.getProgress()<=100
                        &&slider.getProgress()==sounds[0].volume(),"Real D-pad volume adjustment did not persist");
            });
            UiSounds.Stats afterSlider=stats(test,sounds[0]);
            require(noEvents(beforeSlider,afterSlider)&&noPlays(beforeSlider,afterSlider),"Value adjustment without a focus change emitted navigation sound");
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.ui_sounds_preview),"Down did not reach sound preview"));
            UiSounds.Stats beforePreview=stats(test,sounds[0]);
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            await(test,()->sounds[0].stats().previewClickPlays>beforePreview.previewClickPlays&&!sounds[0].stats().previewPending,
                    "Preview did not complete its focus/click sequence");
            UiSounds.Stats preview=stats(test,sounds[0]);
            require(preview.previewRuns-beforePreview.previewRuns==1
                    &&preview.previewFocusPlays-beforePreview.previewFocusPlays==1
                    &&preview.previewClickPlays-beforePreview.previewClickPlays==1
                    &&preview.clickEvents==beforePreview.clickEvents,"Preview duplicated the automatic OK click");
            require(preview.lastFocusStream>0&&preview.lastClickStream>0,"Preview did not receive positive SoundPool stream IDs");

            // Toggle off with actual OK, then prove later directional and click
            // requests are observed while playback remains silent.
            main(test,()->screen.findViewById(R.id.ui_sounds_enabled).requestFocus());
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            main(test,()->require(!sounds[0].enabled(),"Actual sound toggle did not persist Off"));
            verifyMutedInput(test,screen,sounds[0],"Off");
            openAppearance(test,screen);
            verifyMutedPreview(test,screen,sounds[0],"Off");
            main(test,()->{
                Switch toggle=screen.findViewById(R.id.ui_sounds_enabled);
                require(!toggle.isChecked(),"Off state was not reflected in its control");toggle.requestFocus();
            });
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            main(test,()->{require(sounds[0].enabled(),"Actual sound toggle did not re-enable");sounds[0].setVolume(0);});
            verifyMutedInput(test,screen,sounds[0],"zero volume");
            openAppearance(test,screen);
            verifyMutedPreview(test,screen,sounds[0],"zero volume");
            main(test,()->{
                sounds[0].setVolume(53);
                SeekBar slider=screen.findViewById(R.id.ui_sounds_volume);slider.setProgress(53);
                screen.findViewById(R.id.ui_sounds_preview).requestFocus();
            });
            // Start and recreate in one main-thread turn. The delayed second
            // preview clip must be cancelled by lifecycle before it can run.
            UiSounds.Stats[] atRecreate={null};
            recreated.set(null);checkPause.set(true);
            main(test,()->{
                screen.findViewById(R.id.ui_sounds_preview).performClick();
                require(sounds[0].stats().previewPending,"Preview did not schedule its second clip");
                atRecreate[0]=sounds[0].stats();screen.recreate();
            });
            await(test,()->recreated.get()!=null&&recreated.get()!=screen&&sounds[0].ready()
                    &&recreated.get().hasWindowFocus(),"Recreated sound screen did not become ready");
            require(callbackFailure.get()==null,"Sound lifecycle observer failed");
            require(paused.get()&&!pendingOnPause.get(),"Pause did not cancel the pending preview");
            UiSounds.Stats restored=stats(test,sounds[0]);
            require(!restored.previewPending&&restored.previewClickPlays==atRecreate[0].previewClickPlays
                    &&noEvents(atRecreate[0],restored)&&noPlays(atRecreate[0],restored),
                    "Recreation played a delayed preview or startup/restoration sound");
            main(test,()->{
                MainActivity current=recreated.get();SeekBar slider=current.findViewById(R.id.ui_sounds_volume);
                Switch toggle=current.findViewById(R.id.ui_sounds_enabled);
                require(sounds[0].enabled()&&sounds[0].volume()==53&&slider!=null&&slider.getProgress()==53
                        &&toggle!=null&&toggle.isChecked(),"Recreation lost sound preferences or controls");
            });
            result.putString("ui_sounds","PASS: loaded samples, defaults/bounds, real D-pad/OK routing, controls, preview, Off/0 mute, persistence and lifecycle cancellation");
            result.putString("ui_sounds_scope","Positive SoundPool stream IDs and event counters only; actual audibility and physical remote not established");
        } catch(Exception|Error failure) {
            primaryFailure=failure;throw failure;
        } finally {
            Throwable cleanup=null;
            for(MainActivity session:sessions) try {finish(test,session);}
            catch(Exception|Error failure) {cleanup=append(cleanup,failure);}
            try {
                await(test,()->sounds[0].stats().released&&!sounds[0].stats().previewPending,"Last Activity did not release UI sounds");
                for(MainActivity session:sessions) quiescent(test,session);
                restore(appPrefs,appSaved);restore(soundPrefs,soundSaved);
            } catch(Exception|Error failure) {cleanup=append(cleanup,failure);}
            application.unregisterActivityLifecycleCallbacks(callbacks);
            if(cleanup!=null) {if(primaryFailure!=null) primaryFailure.addSuppressed(cleanup);else rethrow(cleanup);}
        }
    }
    private static int tileCount(ViewGroup group) {
        if(group==null) return 0;int count=0;
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child.getTag() instanceof String&&child.isFocusable()) count++;
            else if(child instanceof ViewGroup) count+=tileCount((ViewGroup)child);
        }
        return count;
    }
    private static View rightBoundary(ViewGroup group) {
        if(group==null) return null;
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child.getTag() instanceof String&&child.isFocusable()&&child.getNextFocusRightId()==child.getId()) return child;
            if(child instanceof ViewGroup) {View found=rightBoundary((ViewGroup)child);if(found!=null) return found;}
        }
        return null;
    }
    private static void verifyMutedInput(Instrumentation test,MainActivity screen,UiSounds sounds,String state) throws Exception {
        main(test,()->screen.findViewById(R.id.nav_home).requestFocus());
        UiSounds.Stats before=stats(test,sounds);
        key(test,KeyEvent.KEYCODE_DPAD_DOWN);key(test,KeyEvent.KEYCODE_DPAD_CENTER);
        UiSounds.Stats after=stats(test,sounds);
        require(after.focusEvents-before.focusEvents==1&&after.clickEvents-before.clickEvents==1,
                "Muted "+state+" input route was not exercised");
        require(noPlays(before,after),"Muted "+state+" input started playback");
    }
    private static void verifyMutedPreview(Instrumentation test,MainActivity screen,UiSounds sounds,String state) throws Exception {
        UiSounds.Stats before=stats(test,sounds);
        main(test,()->sounds.preview(screen.findViewById(R.id.ui_sounds_preview)));
        UiSounds.Stats after=stats(test,sounds);
        require(noPlays(before,after)&&!after.previewPending,"Muted "+state+" preview scheduled playback");
    }
    private static void openAppearance(Instrumentation test,MainActivity screen) throws Exception {
        main(test,()->{
            screen.findViewById(R.id.nav_settings).requestFocus();
            ViewGroup categories=screen.findViewById(R.id.settings_categories);categories.getChildAt(0).requestFocus();
        });
        await(test,()->screen.findViewById(R.id.ui_sounds_enabled)!=null,"Appearance sound controls did not return");
    }
    private static boolean noEvents(UiSounds.Stats a,UiSounds.Stats b) {return a.focusEvents==b.focusEvents&&a.clickEvents==b.clickEvents;}
    private static boolean noPlays(UiSounds.Stats a,UiSounds.Stats b) {return a.focusPlays==b.focusPlays&&a.clickPlays==b.clickPlays;}
    private static UiSounds.Stats stats(Instrumentation test,UiSounds sounds) throws Exception {
        UiSounds.Stats[] value={null};main(test,()->value[0]=sounds.stats());return value[0];
    }
    private static int volume(Instrumentation test,UiSounds sounds) throws Exception {
        int[] value={0};main(test,()->value[0]=sounds.volume());return value[0];
    }
    private static void key(Instrumentation test,int key) {test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private interface Action {void run() throws Exception;}
    private interface Check {boolean get() throws Exception;}
    private static void main(Instrumentation test,Action action) throws Exception {
        Throwable[] failure={null};test.runOnMainSync(()->{try {action.run();}catch(Throwable error) {failure[0]=error;}});
        if(failure[0]!=null) rethrow(failure[0]);
    }
    private static void await(Instrumentation test,Check check,String message) throws Exception {
        long end=SystemClock.uptimeMillis()+WAIT_MS;
        while(SystemClock.uptimeMillis()<end) {boolean[] ready={false};main(test,()->ready[0]=check.get());if(ready[0]) return;SystemClock.sleep(30);}
        throw new AssertionError(message);
    }
    private static ExecutorService executor(MainActivity screen,String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return (ExecutorService)field.get(screen);
    }
    private static void finish(Instrumentation test,MainActivity screen) throws Exception {
        Throwable failure=null;
        try {main(test,()->{if(!screen.isDestroyed()) screen.finish();});}
        catch(Exception|Error error) {failure=append(failure,error);}
        for(String name:new String[]{"worker","wallpaperWorker"}) try {
            ExecutorService worker=executor(screen,name);worker.shutdownNow();
            require(worker.awaitTermination(15,TimeUnit.SECONDS),"UI sound fixture worker did not terminate");
        } catch(Exception|Error error) {failure=append(failure,error);}
        try {await(test,screen::isDestroyed,"UI sound fixture Activity did not finish");test.waitForIdleSync();}
        catch(Exception|Error error) {failure=append(failure,error);}
        if(failure!=null) rethrow(failure);
    }
    private static void quiescent(Instrumentation test,MainActivity screen) throws Exception {
        main(test,()->require(screen.isDestroyed(),"Activity remains active; sound preferences not restored"));
        for(String name:new String[]{"worker","wallpaperWorker"})
            require(executor(screen,name).isTerminated(),"Worker remains active; sound preferences not restored");
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs,Map<String,?> saved) {
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(Map.Entry<String,?> entry:saved.entrySet()) {
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String) edit.putString(key,(String)value);else if(value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer) edit.putInt(key,(Integer)value);else if(value instanceof Long) edit.putLong(key,(Long)value);
            else if(value instanceof Float) edit.putFloat(key,(Float)value);else if(value instanceof Set) edit.putStringSet(key,new HashSet<>((Set<String>)value));
            else throw new IllegalStateException("Unexpected fixture preference type");
        }
        require(edit.commit(),"Restore isolated sound fixture preferences");
    }
    private static Throwable append(Throwable first,Throwable next) {if(first==null) return next;first.addSuppressed(next);return first;}
    private static void rethrow(Throwable failure) throws Exception {if(failure instanceof Error) throw (Error)failure;if(failure instanceof Exception) throw (Exception)failure;throw new AssertionError(failure);}
    private static void require(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
}

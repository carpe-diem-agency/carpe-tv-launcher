package agency.carpediem.tv.prototype;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

/** Preloaded, app-local sonification. Never changes device volume or requests audio focus. */
final class UiSounds {
    static final String ENABLED="enabled", VOLUME="volume";
    static final int DEFAULT_VOLUME=70;
    private static final long FOCUS_INTERVAL_MS=55, PREVIEW_GAP_MS=300;
    private static UiSounds instance;
    static synchronized UiSounds get(Context context) {
        if(instance==null) {
            Context application=context.getApplicationContext();
            if(!(application instanceof Application))
                throw new IllegalArgumentException("UI sounds require the app Application context");
            instance=new UiSounds((Application)application);
        }
        return instance;
    }
    static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences("ui_sounds",Context.MODE_PRIVATE);
    }
    private final Application app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SoundPool pool;
    private int focusSample,clickSample,stream;
    private boolean focusReady,clickReady;
    private long lastFocusAt=Long.MIN_VALUE;
    private Runnable previewClick;
    private View previewRoot;
    private long focusEvents,clickEvents,focusPlays,clickPlays,previewRuns,previewFocusPlays,previewClickPlays;
    private int lastFocusStream,lastClickStream;
    private UiSounds(Application application) {app=application;}
    synchronized void load() {
        if(pool!=null) return;
        pool=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((loaded,sample,status)->{
            synchronized(UiSounds.this) {
                if(pool!=loaded) return;
                if(sample==focusSample) focusReady=status==0;
                if(sample==clickSample) clickReady=status==0;
            }
        });
        focusSample=pool.load(app,R.raw.ui_focus,1);
        clickSample=pool.load(app,R.raw.ui_click,1);
    }
    boolean enabled() {return preferences(app).getBoolean(ENABLED,true);}
    int volume() {return Math.max(0,Math.min(100,preferences(app).getInt(VOLUME,DEFAULT_VOLUME)));}
    synchronized void setEnabled(boolean enabled) {
        preferences(app).edit().putBoolean(ENABLED,enabled).apply();
        if(!enabled) pause();
    }
    synchronized void setVolume(int value) {
        int bounded=Math.max(0,Math.min(100,value));
        preferences(app).edit().putInt(VOLUME,bounded).apply();
        if(bounded==0) pause();
        else if(pool!=null&&stream!=0) pool.setVolume(stream,bounded/100f,bounded/100f);
    }
    synchronized boolean ready() {return pool!=null&&focusReady&&clickReady;}
    synchronized void pause() {cancelPreview();stopStream();}
    synchronized void release() {
        pause();
        if(pool!=null) {pool.release();pool=null;}
        focusReady=false;clickReady=false;focusSample=0;clickSample=0;
    }
    private void stopStream() {if(pool!=null&&stream!=0) pool.stop(stream);stream=0;}
    private void cancelPreview() {
        if(previewClick!=null) handler.removeCallbacks(previewClick);
        previewClick=null;previewRoot=null;
    }
    synchronized void lostWindow(View root) {
        if(previewRoot==root) {cancelPreview();stopStream();}
    }
    private int play(boolean focus,boolean preview) {
        if(!ready()||!enabled()||volume()==0) return 0;
        stopStream();
        // Assets retain their original gain. Apply the user's linear level once.
        float gain=volume()/100f;
        int accepted=pool.play(focus?focusSample:clickSample,gain,gain,1,0,1f);
        stream=accepted;
        if(accepted!=0) {
            if(focus) {focusPlays++;lastFocusStream=accepted;if(preview) previewFocusPlays++;}
            else {clickPlays++;lastClickStream=accepted;if(preview) previewClickPlays++;}
        }
        return accepted;
    }
    private synchronized void navigation() {
        focusEvents++;
        cancelPreview();
        long now=SystemClock.uptimeMillis();
        if(lastFocusAt!=Long.MIN_VALUE&&now-lastFocusAt<FOCUS_INTERVAL_MS) return;
        if(play(true,false)!=0) lastFocusAt=now;
    }
    private synchronized void clicked() {clickEvents++;cancelPreview();play(false,false);}
    synchronized void preview(View anchor) {
        pause();
        if(!ready()||!enabled()||volume()==0||!anchor.isAttachedToWindow()||!anchor.hasWindowFocus()) return;
        previewRuns++;
        previewRoot=anchor.getRootView();
        play(true,true);
        previewClick=()->{
            synchronized(UiSounds.this) {
                if(previewClick==null) return;
                if(anchor.isAttachedToWindow()&&anchor.hasWindowFocus()&&anchor.isShown()) play(false,true);
                previewClick=null;previewRoot=null;
            }
        };
        handler.postDelayed(previewClick,PREVIEW_GAP_MS);
    }
    /** Wrap at registration time; preserve the actual click and native accessibility handling. */
    static void click(View view,View.OnClickListener listener) {
        view.setSoundEffectsEnabled(false);
        view.setOnClickListener(clicked->{get(clicked.getContext()).clicked();listener.onClick(clicked);});
    }
    static void longClick(View view,View.OnLongClickListener listener) {
        view.setSoundEffectsEnabled(false);
        view.setOnLongClickListener(clicked->{
            boolean handled=listener.onLongClick(clicked);
            if(handled) get(clicked.getContext()).clicked();
            return handled;
        });
    }
    /** Preview supplies its own pair, so it must not emit an additional automatic click. */
    static void previewButton(View view,View.OnClickListener listener) {
        view.setSoundEffectsEnabled(false);view.setOnClickListener(listener);
    }
    static DialogInterface.OnClickListener dialogClick(Context context,DialogInterface.OnClickListener listener) {
        return (dialog,which)->{get(context).clicked();if(listener!=null) listener.onClick(dialog,which);};
    }
    static void watch(Dialog dialog) {
        watch(dialog.getWindow());
        if(dialog instanceof android.app.AlertDialog) {
            android.app.AlertDialog alert=(android.app.AlertDialog)dialog;
            for(int which:new int[]{DialogInterface.BUTTON_POSITIVE,DialogInterface.BUTTON_NEGATIVE,DialogInterface.BUTTON_NEUTRAL}) {
                View button=alert.getButton(which);if(button!=null) button.setSoundEffectsEnabled(false);
            }
        }
    }
    static void watch(Window window) {
        if(window==null||window.getCallback() instanceof SoundWindow) return;
        View root=window.getDecorView();root.setSoundEffectsEnabled(false);
        window.setCallback(new SoundWindow(window,window.getCallback(),get(root.getContext())));
    }
    static final class Stats {
        final long focusEvents,clickEvents,focusPlays,clickPlays,previewRuns,previewFocusPlays,previewClickPlays;
        final int lastFocusStream,lastClickStream;
        final boolean ready,released,previewPending;
        Stats(UiSounds sound) {
            focusEvents=sound.focusEvents;clickEvents=sound.clickEvents;focusPlays=sound.focusPlays;clickPlays=sound.clickPlays;
            previewRuns=sound.previewRuns;previewFocusPlays=sound.previewFocusPlays;previewClickPlays=sound.previewClickPlays;
            lastFocusStream=sound.lastFocusStream;lastClickStream=sound.lastClickStream;
            ready=sound.ready();released=sound.pool==null;previewPending=sound.previewClick!=null;
        }
    }
    synchronized Stats stats() {return new Stats(this);}
    /** Observe only user direction dispatch, never layout/animation/restoration. */
    private static final class SoundWindow implements Window.Callback {
        private final Window window;
        private final Window.Callback original;
        private final UiSounds sound;
        SoundWindow(Window window,Window.Callback original,UiSounds sound) {this.window=window;this.original=original;this.sound=sound;}
        private Runnable pendingDirection;
        private View directionBefore;
        private void cancelDirection() {
            if(pendingDirection!=null) sound.handler.removeCallbacks(pendingDirection);
            pendingDirection=null;directionBefore=null;
        }
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            View root=window.getDecorView();
            View before=root.findFocus();
            boolean handled=original.dispatchKeyEvent(event);
            int key=event.getKeyCode();
            if(event.getAction()==KeyEvent.ACTION_DOWN&&before!=null
                    &&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN
                    ||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)
                    &&root.isAttachedToWindow()&&root.hasWindowFocus()&&pendingDirection==null) {
                directionBefore=before;
                pendingDirection=()->{
                    View prior=directionBefore;
                    pendingDirection=null;directionBefore=null;
                    View after=root.findFocus();
                    if(root.isAttachedToWindow()&&root.hasWindowFocus()&&prior!=null
                            &&after!=null&&after!=prior&&after.isShown()) sound.navigation();
                };
                // ViewRootImpl may move native focus only AFTER this callback
                // returns false. Inspect the final result at the end of this input
                // turn, ahead of queued catalog/layout/restoration callbacks.
                sound.handler.postAtFrontOfQueue(pendingDirection);
            }
            return handled;
        }
        @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {return original.dispatchKeyShortcutEvent(event);}
        @Override public boolean dispatchTouchEvent(MotionEvent event) {return original.dispatchTouchEvent(event);}
        @Override public boolean dispatchTrackballEvent(MotionEvent event) {return original.dispatchTrackballEvent(event);}
        @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {return original.dispatchGenericMotionEvent(event);}
        @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {return original.dispatchPopulateAccessibilityEvent(event);}
        @Override public View onCreatePanelView(int featureId) {return original.onCreatePanelView(featureId);}
        @Override public boolean onCreatePanelMenu(int featureId,Menu menu) {return original.onCreatePanelMenu(featureId,menu);}
        @Override public boolean onPreparePanel(int featureId,View view,Menu menu) {return original.onPreparePanel(featureId,view,menu);}
        @Override public boolean onMenuOpened(int featureId,Menu menu) {return original.onMenuOpened(featureId,menu);}
        @Override public boolean onMenuItemSelected(int featureId,MenuItem item) {return original.onMenuItemSelected(featureId,item);}
        @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {original.onWindowAttributesChanged(attrs);}
        @Override public void onContentChanged() {original.onContentChanged();}
        @Override public void onWindowFocusChanged(boolean focused) {
            original.onWindowFocusChanged(focused);
            if(!focused) {cancelDirection();sound.lostWindow(window.getDecorView());}
        }
        @Override public void onAttachedToWindow() {original.onAttachedToWindow();}
        @Override public void onDetachedFromWindow() {cancelDirection();sound.lostWindow(window.getDecorView());original.onDetachedFromWindow();}
        @Override public void onPanelClosed(int featureId,Menu menu) {original.onPanelClosed(featureId,menu);}
        @Override public boolean onSearchRequested() {return original.onSearchRequested();}
        @Override public boolean onSearchRequested(SearchEvent event) {return original.onSearchRequested(event);}
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {return original.onWindowStartingActionMode(callback);}
        @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,int type) {return original.onWindowStartingActionMode(callback,type);}
        @Override public void onActionModeStarted(ActionMode mode) {original.onActionModeStarted(mode);}
        @Override public void onActionModeFinished(ActionMode mode) {original.onActionModeFinished(mode);}
        @Override public void onProvideKeyboardShortcuts(List<KeyboardShortcutGroup> data,Menu menu,int deviceId) {original.onProvideKeyboardShortcuts(data,menu,deviceId);}
        @Override public void onPointerCaptureChanged(boolean hasCapture) {original.onPointerCaptureChanged(hasCapture);}
    }
}

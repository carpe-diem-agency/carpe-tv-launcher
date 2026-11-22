package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One preloaded UI pool shared by owned windows, released when the app is offscreen. */
public final class CarpeApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        UiSounds sounds=UiSounds.get(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private final Set<Activity> started=Collections.newSetFromMap(new IdentityHashMap<>());
            private boolean sonified(Activity activity) {return !(activity instanceof ScreensaverActivity);}
            @Override public void onActivityCreated(Activity activity,Bundle state) {if(sonified(activity)) UiSounds.watch(activity.getWindow());}
            @Override public void onActivityStarted(Activity activity) {if(sonified(activity)) {started.add(activity);sounds.load();}}
            @Override public void onActivityResumed(Activity activity) {if(sonified(activity)) UiSounds.watch(activity.getWindow());}
            @Override public void onActivityPaused(Activity activity) {if(sonified(activity)) sounds.pause();}
            @Override public void onActivityStopped(Activity activity) {started.remove(activity);if(started.isEmpty()) sounds.release();}
            @Override public void onActivitySaveInstanceState(Activity activity,Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) {started.remove(activity);if(started.isEmpty()) sounds.release();}
        });
    }
}

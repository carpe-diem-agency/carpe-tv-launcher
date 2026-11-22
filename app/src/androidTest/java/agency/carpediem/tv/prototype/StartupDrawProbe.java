package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Warm process/new Activity only. Records qualifying draw timestamps, never physical presentation. */
final class StartupDrawProbe {
    private static final String[] NAMES={"home_new","apps_first","settings_first","apps_cached","settings_cached"};
    static void run(Instrumentation test,Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),"Disposable validation required");
        require(Build.VERSION.SDK_INT>=29,"Pre-created lifecycle callback requires API29");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());
        Application application=(Application)test.getTargetContext().getApplicationContext();
        List<Measurement> samples=new ArrayList<>();AtomicReference<MainActivity> owned=new AtomicReference<>();
        AtomicReference<Measurement> launching=new AtomicReference<>();AtomicReference<Throwable> monitorFailure=new AtomicReference<>();Throwable primary=null;
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityPreCreated(Activity activity,Bundle state) {
                if(activity instanceof MainActivity&&launching.get()!=null) {
                    owned.set((MainActivity)activity);
                    try {require(state==null,"New Activity unexpectedly restored saved state");}
                    catch(Throwable error) {monitorFailure.compareAndSet(null,error);}
                }
            }
            @Override public void onActivityCreated(Activity activity,Bundle state) {
                if(activity==owned.get()&&launching.get()!=null) {
                    try {launching.get().attach((MainActivity)activity);}
                    catch(Throwable error) {monitorFailure.compareAndSet(null,error);}
                }
            }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        };
        application.registerActivityLifecycleCallbacks(callbacks);
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).remove("last_opened_package").commit(),"Prepare app-only launch preferences");
            test.setInTouchMode(false);
            for(int repeat=0;repeat<3;repeat++) {
                Measurement home=new Measurement(NAMES[0],"home");samples.add(home);launching.set(home);
                home.start=SystemClock.elapsedRealtimeNanos();
                MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                require(owned.get()==screen,"Lifecycle monitor missed launched Activity");launching.set(null);home.await(monitorFailure);
                int[] rails={R.id.nav_apps,R.id.nav_settings,R.id.nav_apps,R.id.nav_settings};
                for(int route=0;route<rails.length;route++) {
                    Measurement measurement=new Measurement(NAMES[route+1],route%2==0?"apps":"settings");samples.add(measurement);
                    final int rail=rails[route];
                    SettingsStructureProbe.main(test,()->{
                        measurement.attach(screen);measurement.start=SystemClock.elapsedRealtimeNanos();
                        require(screen.findViewById(rail).requestFocus(),"Cannot activate rail");screen.getWindow().getDecorView().invalidate();
                    });
                    measurement.await(monitorFailure);
                }
                SettingsStructureProbe.finish(test,screen);SettingsStructureProbe.quiescent(test,screen);owned.set(null);
            }
            for(String name:NAMES) {
                ArrayList<String> raw=new ArrayList<>();
                for(Measurement m:samples)if(name.equals(m.name))raw.add((m.usable-m.start)/1_000_000.0+","+(m.settled-m.start)/1_000_000.0);
                result.putString("startup_"+name+"_usable_settled_ms",raw.toString());
            }
            result.putInt("startup_samples_per_route",3);
            result.putString("startup_draw_scope","Warm instrumentation process/new Activity, retained disk caches; first qualifying OnDraw and transition-settled OnDraw. Not process-cold, physical display latency, artwork completeness or performance-budget acceptance.");
        }catch(Exception|Error error){primary=error;throw error;}
        finally {
            application.unregisterActivityLifecycleCallbacks(callbacks);
            Throwable cleanup=null;
            try {
                SettingsStructureProbe.main(test,()->{for(Measurement measurement:samples)measurement.detach();});
                MainActivity screen=owned.get();if(screen!=null){SettingsStructureProbe.finish(test,screen);SettingsStructureProbe.quiescent(test,screen);}
                SettingsStructureProbe.restore(prefs,saved);
            }catch(Exception|Error error){cleanup=error;}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static final class Measurement implements ViewTreeObserver.OnDrawListener {
        final String name,page;final CountDownLatch done=new CountDownLatch(1);
        MainActivity activity;View decor;ViewTreeObserver observer;long start,usable,settled;Throwable failure;
        Measurement(String name,String page){this.name=name;this.page=page;}
        void attach(MainActivity screen){activity=screen;decor=screen.getWindow().getDecorView();observer=decor.getViewTreeObserver();observer.addOnDrawListener(this);}
        @Override public void onDraw() {
            if(settled!=0||failure!=null)return;
            try {
                View content=(View)field(activity,"content");
                if(!page.equals(field(activity,"page"))||!activity.hasWindowFocus()||!content.isShown()||content.getAlpha()<=0f||content.getWidth()==0||content.getHeight()==0)return;
                int rail=page.equals("home")?R.id.nav_home:page.equals("apps")?R.id.nav_apps:R.id.nav_settings;
                if(!activity.findViewById(rail).isSelected()||activity.getCurrentFocus()==null||!activity.getCurrentFocus().isShown())return;
                boolean ready;
                if(page.equals("apps")) {
                    View search=content.findViewById(R.id.apps_search);
                    ready=search!=null&&search.isShown()&&(Boolean)field(activity,"appsReady")&&hasAction(content);
                } else if(page.equals("settings")) {
                    View detail=content.findViewById(R.id.settings_detail);
                    ready=detail!=null&&detail.isShown()&&hasAction(detail);
                } else {View clock=content.findViewById(R.id.home_time);ready=clock!=null&&clock.isShown()&&hasAction(content);}
                if(!ready)return;
                long now=SystemClock.elapsedRealtimeNanos();if(usable==0)usable=now;
                if(content.getAlpha()==1f&&content.getTranslationY()==0f){settled=now;decor.post(()->{detach();done.countDown();});}
            }catch(Throwable error){failure=error;decor.post(()->{detach();done.countDown();});}
        }
        void detach(){if(observer!=null&&observer.isAlive())observer.removeOnDrawListener(this);observer=null;activity=null;decor=null;}
        void await(AtomicReference<Throwable> monitorFailure) throws Exception {
            long deadline=SystemClock.elapsedRealtime()+15000;
            while(done.getCount()!=0&&monitorFailure.get()==null&&SystemClock.elapsedRealtime()<deadline) done.await(50,TimeUnit.MILLISECONDS);
            if(monitorFailure.get()!=null) throw new AssertionError("Lifecycle monitor failed",monitorFailure.get());
            require(done.getCount()==0,"Qualifying draw unavailable: "+name);
            if(failure!=null)throw new AssertionError("Draw readiness failed: "+name,failure);
            require(usable>=start&&settled>=usable,"Invalid endpoint ordering");
        }
    }
    private static boolean hasAction(View root){
        if(!root.isShown()||!root.isEnabled())return false;
        if(root.isFocusable()&&root.isClickable())return true;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(hasAction(group.getChildAt(i)))return true;}
        return false;
    }
    private static Object field(Object owner,String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

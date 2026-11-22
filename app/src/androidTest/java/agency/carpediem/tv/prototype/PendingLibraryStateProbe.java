package agency.carpediem.tv.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Two framework recreations while the middle instance has not populated its catalog. */
final class PendingLibraryStateProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {
        require(Build.VERSION.SDK_INT>=29,"INPUT_UNVERIFIED: pre-create lifecycle gate needs API29+");
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Validation package required");
        SharedPreferences prefs=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);Map<String,?> saved=new HashMap<>(prefs.getAll());
        List<MainActivity> sessions=new CopyOnWriteArrayList<>();Map<Activity,CountDownLatch> gates=new ConcurrentHashMap<>();AtomicReference<MainActivity> latest=new AtomicReference<>();AtomicReference<Throwable> callbackError=new AtomicReference<>();AtomicInteger created=new AtomicInteger();
        Application app=(Application)t.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks cb=new Application.ActivityLifecycleCallbacks(){
            public void onActivityPreCreated(Activity activity,Bundle state){if(activity instanceof MainActivity&&created.incrementAndGet()>1)try{
                CountDownLatch gate=new CountDownLatch(1);gates.put(activity,gate);
                ((ExecutorService)read((MainActivity)activity,"worker")).submit(()->{try{gate.await(45,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            }catch(Throwable error){callbackError.set(error);}}
            public void onActivityCreated(Activity a,Bundle b){if(a instanceof MainActivity){sessions.add((MainActivity)a);latest.set((MainActivity)a);}}
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };app.registerActivityLifecycleCallbacks(cb);Throwable primary=null;
        try{
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare welcome");t.setInTouchMode(false);
            MainActivity a=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(t,()->a.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(t,()->{ScrollView s=a.findViewById(R.id.apps_scroll);return s!=null&&s.getHeight()>0&&s.getChildCount()>0&&s.getChildAt(0).getHeight()-s.getHeight()>=80;},"INPUT_UNVERIFIED: real catalog needs 80px scroll");
            String[] selected={null};
            SettingsStructureProbe.main(t,()->{List<View> tiles=new ArrayList<>();collect(a.findViewById(R.id.apps_grid),tiles);require(tiles.size()>5,"INPUT_UNVERIFIED: catalog needs second row");View tile=tiles.get(5);require(tile.requestFocus(),"Cannot select real second-row tile");selected[0]=(String)tile.getTag();});
            t.waitForIdleSync();
            SettingsStructureProbe.main(t,()->{((ScrollView)a.findViewById(R.id.apps_scroll)).scrollTo(0,80);a.recreate();});
            SettingsStructureProbe.await(t,()->latest.get()!=a&&latest.get()!=null&&latest.get().hasWindowFocus(),"Instance B did not create");MainActivity b=latest.get();
            SettingsStructureProbe.await(t,()->loading(b),"INPUT_UNVERIFIED: B did not expose gated loading layout");
            SettingsStructureProbe.main(t,()->{require(callbackError.get()==null,"Lifecycle gate setup failed");pending(b,80,selected[0],"B");b.recreate();});
            SettingsStructureProbe.await(t,()->latest.get()!=b&&latest.get()!=null&&latest.get().hasWindowFocus(),"Instance C did not create");MainActivity c=latest.get();
            SettingsStructureProbe.await(t,()->loading(c),"INPUT_UNVERIFIED: C did not expose gated loading layout");
            SettingsStructureProbe.main(t,()->pending(c,80,selected[0],"C after second recreation"));
            gates.get(c).countDown();
            SettingsStructureProbe.await(t,()->{ScrollView s=c.findViewById(R.id.apps_scroll);return s!=null&&s.getScrollY()==80&&c.getCurrentFocus()!=null&&selected[0].equals(c.getCurrentFocus().getTag());},"C real catalog did not restore pending scroll/focus");
            result.putString("pending_library","PASS: real catalog pending 80px scroll and package survived two loading-state Activity recreations and restored after gate release");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally{for(CountDownLatch gate:gates.values())gate.countDown();Throwable cleanup=null;for(MainActivity a:sessions)try{SettingsStructureProbe.finish(t,a);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}try{for(MainActivity a:sessions)SettingsStructureProbe.quiescent(t,a);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}app.unregisterActivityLifecycleCallbacks(cb);if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}}
    }
    private static Object read(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private static boolean loading(MainActivity a)throws Exception{ScrollView s=a.findViewById(R.id.apps_scroll);return !((Boolean)read(a,"appsReady"))&&((List<?>)read(a,"allApps")).isEmpty()&&s!=null&&s.getHeight()>0&&s.getChildCount()>0&&s.getChildAt(0).getHeight()>0&&s.getScrollY()==0;}
    private static void pending(MainActivity a,int y,String pkg,String phase)throws Exception{require(((Integer)read(a,"restoredAppsScrollY"))==y,"Pending scroll lost in "+phase);require(pkg.equals(read(a,"restoredAppsFocusPackage")),"Pending selected package lost in "+phase);}
    private static void collect(ViewGroup root,List<View> out){if(root==null)return;for(int i=0;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v.isFocusable()&&v.getTag()instanceof String)out.add(v);else if(v instanceof ViewGroup)collect((ViewGroup)v,out);}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

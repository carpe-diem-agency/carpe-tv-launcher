package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Injected hardware keys to correct-result draw; no physical remote/IME/GPU latency claim. */
final class LibraryInputLatencyProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Validation package required");
        require(Build.VERSION.SDK_INT>=30,"IME insets require API30+");
        SharedPreferences prefs=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity activity=null;Throwable primary=null;
        try{
            require(prefs.edit().putBoolean("welcome_seen",true).putString("wallpaper","alpine").putBoolean(WallpaperRotation.ENABLED,false).putBoolean("animations",true).commit(),"Prepare fixture");t.setInTouchMode(false);
            MainActivity a=activity=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(t,()->a.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(t,()->cards(a).size()>=2,"Catalog needs actual apps");
            drain(t,a);
            Map<String,String> baseline=new LinkedHashMap<>();String[] prefix={null};
            SettingsStructureProbe.main(t,()->{baseline.putAll(cards(a));for(String label:baseline.values()){java.util.regex.Matcher m=java.util.regex.Pattern.compile("[a-z]{2}").matcher(label.toLowerCase(Locale.ROOT));if(m.find()){prefix[0]=m.group();break;}}require(prefix[0]!=null,"INPUT_UNVERIFIED: no ASCII fragment");require(a.findViewById(R.id.apps_search).requestFocus(),"Cannot focus search");});
            t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(t, () -> ime(a) && a.findViewById(R.id.apps_search).hasFocus(),
                    "INPUT_UNVERIFIED: real IME did not open before measurement");
            String[] expected={prefix[0].substring(0,1),prefix[0],prefix[0].substring(0,1),""};
            KeyEvent[][] input={letters(prefix[0].charAt(0)),letters(prefix[0].charAt(1)),delete(),delete()};
            for(int i=0;i<4;i++)sample(t,a,baseline,expected[i],input[i],"warm"+i);
            for(int pass=0;pass<2;pass++){for(String name:new String[]{"worker","wallpaperWorker"}){java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);((ExecutorService)f.get(a)).submit(()->{}).get(15,TimeUnit.SECONDS);}t.waitForIdleSync();}
            SettingsStructureProbe.await(t,()->{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("loadedWallpaper");f.setAccessible(true);return "alpine".equals(f.get(a));},"Alpine not ready");SystemClock.sleep(300);
            List<List<Long>> times=Arrays.asList(new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            for(int cycle=0;cycle<10;cycle++)for(int i=0;i<4;i++)times.get(i).add(sample(t,a,baseline,expected[i],input[i],"cycle"+cycle+"_op"+i));
            String[] names={"char1","char2","delete2","delete1"};for(int i=0;i<4;i++)result.putString(names[i]+"_ms",summary(times.get(i)));
            result.putInt("actual_catalog_count",baseline.size());result.putInt("measured_logical_edits",40);int events=0;for(KeyEvent[] keys:input)events+=keys.length;result.putInt("measured_input_event_count",events*10);
            android.util.DisplayMetrics d=a.getResources().getDisplayMetrics();result.putString("configuration","api="+Build.VERSION.SDK_INT+",viewport="+d.widthPixels+"x"+d.heightPixels+",densityDpi="+d.densityDpi+",IMEvisible,alpine,rotationOff,appAnimationsOn,bothWorkersDrained,noGC");
            result.putString("library_input_perf","PASS: 40 injected hardware-key edits, exact query/results/focus, IME visible");
            result.putString("scope","Before sendKeySync through start of the first verified result OnDraw; includes Android dispatch, excludes validation/post time, actual presentation, physical hardware and keyboard gestures");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally{Throwable cleanup=null;if(activity!=null)try{SettingsStructureProbe.finish(t,activity);SettingsStructureProbe.quiescent(t,activity);}catch(Exception|Error failure){cleanup=failure;}try{if(activity!=null)SettingsStructureProbe.quiescent(t,activity);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}}
    }
    private static long sample(Instrumentation t,MainActivity a,Map<String,String> baseline,String expected,KeyEvent[] keys,String stage)throws Exception{
        CountDownLatch drawn=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();long[] clock={0,0};boolean[] done={false};ViewTreeObserver[] observer={null};ViewTreeObserver.OnDrawListener[] listener={null};
        Set<String> wanted = new HashSet<>();
        for (Map.Entry<String, String> entry : baseline.entrySet()) {
            if (entry.getValue().toLowerCase(Locale.ROOT).contains(expected)) wanted.add(entry.getKey());
        }
        try{
            SettingsStructureProbe.main(t,()->{
                View grid=a.findViewById(R.id.apps_grid);
                EditText search=a.findViewById(R.id.apps_search);
                require(search.hasFocus()&&ime(a),"Input baseline mismatch; "+diagnostic(a,expected,stage));
                observer[0]=grid.getViewTreeObserver();
                listener[0]=()->{
                    long drawStart = SystemClock.elapsedRealtimeNanos();
                    if(done[0]||!expected.contentEquals(search.getText()))return;
                    try {
                        require(wanted.equals(cards(a).keySet()),"Hardware query results differ; "+diagnostic(a,expected,stage));
                        require(search.hasFocus()&&ime(a),"Hardware edit changed focus/IME; "+diagnostic(a,expected,stage));
                        clock[1]=drawStart;
                    } catch(Throwable error) {failure.set(error);}
                    done[0]=true;
                    grid.post(drawn::countDown);
                };
                observer[0].addOnDrawListener(listener[0]);
            });
            clock[0]=SystemClock.elapsedRealtimeNanos();long now=SystemClock.uptimeMillis();
            for(KeyEvent key:keys){KeyEvent fresh=new KeyEvent(now,SystemClock.uptimeMillis(),key.getAction(),key.getKeyCode(),key.getRepeatCount(),key.getMetaState(),KeyCharacterMap.VIRTUAL_KEYBOARD,0,key.getFlags(),InputDevice.SOURCE_KEYBOARD);t.sendKeySync(fresh);}
            if (!drawn.await(2,TimeUnit.SECONDS)) {
                String[] state = {null};
                SettingsStructureProbe.main(t, () -> state[0] = diagnostic(a, expected, stage)
                        + ",observerAlive=" + observer[0].isAlive() + ",drawSeen=" + done[0]
                        + ",resultsMatch=" + wanted.equals(cards(a).keySet()));
                throw new AssertionError("Correct hardware-input draw not witnessed; " + state[0]);
            }
            if(failure.get()!=null)throw new AssertionError("Hardware draw validation failed; stage="+stage+"; "+failure.get().getMessage(),failure.get());
            require(clock[1]>=clock[0],"Input/draw timestamps do not match the sampled edit");
            return clock[1]-clock[0];
        }finally{SettingsStructureProbe.main(t,()->{if(observer[0]!=null&&observer[0].isAlive()&&listener[0]!=null)observer[0].removeOnDrawListener(listener[0]);});}
    }
    private static String diagnostic(MainActivity a,String expected,String stage){
        View focus=a.getCurrentFocus();EditText search=a.findViewById(R.id.apps_search);
        String id="none";if(focus!=null)try{id=a.getResources().getResourceEntryName(focus.getId());}catch(android.content.res.Resources.NotFoundException unknown){id=Integer.toString(focus.getId());}
        return "stage="+stage+",focusId="+id+",focusType="+(focus==null?"none":focus.getClass().getSimpleName())
                +",IMEvisible="+ime(a)+",appsSelected="+a.findViewById(R.id.nav_apps).isSelected()
                +",expectedLength="+expected.length()+",currentLength="+(search==null?-1:search.getText().length());
    }
    private static void drain(Instrumentation t,MainActivity a)throws Exception{
        for(int pass=0;pass<2;pass++){
            for(String name:new String[]{"worker","wallpaperWorker"}){java.lang.reflect.Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);((ExecutorService)field.get(a)).submit(()->{}).get(15,TimeUnit.SECONDS);}
            t.waitForIdleSync();
        }
    }
    private static KeyEvent[] letters(char c){KeyEvent[] events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(new char[]{c});require(events!=null,"ASCII events unavailable");return events;}
    private static KeyEvent[] delete(){return new KeyEvent[]{new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL),new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DEL)};}
    private static boolean ime(MainActivity a){WindowInsets insets=a.getWindow().getDecorView().getRootWindowInsets();return insets!=null&&insets.isVisible(WindowInsets.Type.ime());}
    private static Map<String,String> cards(MainActivity a){Map<String,String> result=new LinkedHashMap<>();collect(a.findViewById(R.id.apps_grid),result);return result;}
    private static void collect(ViewGroup root,Map<String,String> result){if(root==null)return;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.isFocusable()&&child.getTag()instanceof String){TextView label=child.findViewById(R.id.app_name);require(result.put((String)child.getTag(),label.getText().toString())==null,"Duplicate catalog tile");}else if(child instanceof ViewGroup)collect((ViewGroup)child,result);}}
    private static String summary(List<Long> values){List<Long> copy=new ArrayList<>(values);Collections.sort(copy);return"n="+copy.size()+",p50="+copy.get((copy.size()-1)/2)/1e6+",p95="+copy.get((int)Math.ceil(copy.size()*.95)-1)/1e6+",max="+copy.get(copy.size()-1)/1e6;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Instrumented setText-to-next-draw measurements, not hardware/IME input or display latency. */
final class LibraryPerformanceProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Validation package required");
        SharedPreferences prefs=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity[] screen={null};
        HandlerThread thread=new HandlerThread("library-frame-metrics");thread.start();Handler handler=new Handler(thread.getLooper());
        List<Long> searchFrames=Collections.synchronizedList(new ArrayList<>()),navFrames=Collections.synchronizedList(new ArrayList<>());
        long[] dropped={0,0},excluded={0,0};java.util.concurrent.atomic.AtomicInteger phase=new java.util.concurrent.atomic.AtomicInteger(0);boolean[] added={false};
        Window.OnFrameMetricsAvailableListener listener=(window,metrics,lost)->{
            int p=phase.get();if(p<0||p>1)return;dropped[p]+=lost;
            if(metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)!=0){excluded[p]++;return;}
            (p==0?searchFrames:navFrames).add(metrics.getMetric(FrameMetrics.TOTAL_DURATION));
        };
        Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).putString("wallpaper","alpine").putBoolean(WallpaperRotation.ENABLED,false).putBoolean("animations",true).commit(),"Prepare welcome");t.setInTouchMode(false);
            screen[0]=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity a=screen[0];
            SettingsStructureProbe.main(t,()->a.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(t,()->tiles(a).size()>=2,"Actual catalog needs at least two apps");
            Map<String,String> baseline=new LinkedHashMap<>();String[] query={null};
            SettingsStructureProbe.main(t,()->{
                for(View tile:tiles(a))baseline.put((String)tile.getTag(),((TextView)tile.findViewById(R.id.app_name)).getText().toString());
                for(String label:baseline.values()){java.util.regex.Matcher m=java.util.regex.Pattern.compile("[a-z]{2}").matcher(label.toLowerCase(Locale.ROOT));if(m.find()){query[0]=m.group();break;}}
                require(query[0]!=null,"INPUT_UNVERIFIED: no ASCII label fragment");a.findViewById(R.id.apps_search).requestFocus();
            });
            // Warm the same result states before collecting memory and timings.
            edit(t,a,baseline,query[0]);edit(t,a,baseline,"");
            for(int pass=0;pass<2;pass++){
                for(String name:new String[]{"worker","wallpaperWorker"}){
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);
                    ((ExecutorService)field.get(a)).submit(()->{}).get(15,TimeUnit.SECONDS);
                }
                t.waitForIdleSync();
            }
            SettingsStructureProbe.await(t,()->{
                java.lang.reflect.Field loaded=MainActivity.class.getDeclaredField("loadedWallpaper"),sharp=MainActivity.class.getDeclaredField("sharpWallpaper");
                loaded.setAccessible(true);sharp.setAccessible(true);return "alpine".equals(loaded.get(a))&&sharp.get(a)!=null;
            },"Alpine wallpaper did not finish loading before baseline");
            SystemClock.sleep(300);edit(t,a,baseline,"");
            android.util.DisplayMetrics display=a.getResources().getDisplayMetrics();
            result.putString("runtime_context","api="+Build.VERSION.SDK_INT+",viewport="+display.widthPixels+"x"+display.heightPixels
                    +",densityDpi="+display.densityDpi+",systemAnimatorsEnabled="+android.animation.ValueAnimator.areAnimatorsEnabled());
            SettingsStructureProbe.main(t,()->{a.getWindow().addOnFrameMetricsAvailableListener(listener,handler);added[0]=true;});
            List<Long> narrow=new ArrayList<>(),equivalent=new ArrayList<>(),noMatch=new ArrayList<>(),clear=new ArrayList<>();StringBuilder memory=new StringBuilder();checkpoint(t,a,memory,0);
            for(int cycle=1;cycle<=60;cycle++){
                narrow.add(edit(t,a,baseline,query[0]));
                List<View> old=new ArrayList<>(),oldRows=new ArrayList<>();SettingsStructureProbe.main(t,()->{old.addAll(tiles(a));ViewGroup grid=a.findViewById(R.id.apps_grid);for(int r=0;r<grid.getChildCount();r++)oldRows.add(grid.getChildAt(r));});
                equivalent.add(edit(t,a,baseline,query[0].toUpperCase(Locale.ROOT)));
                SettingsStructureProbe.main(t,()->{require(old.equals(tiles(a)),"Equivalent query replaced tiles");ViewGroup grid=a.findViewById(R.id.apps_grid);require(grid.getChildCount()==oldRows.size(),"Equivalent query changed rows");for(int r=0;r<oldRows.size();r++)require(grid.getChildAt(r)==oldRows.get(r),"Equivalent query replaced row");});
                noMatch.add(edit(t,a,baseline,"carpe-unmatched-fixture-987654"));clear.add(edit(t,a,baseline,""));
                if(cycle%20==0)checkpoint(t,a,memory,cycle);
            }
            SettingsStructureProbe.main(t,()->a.getWindow().removeOnFrameMetricsAvailableListener(listener));added[0]=false;
            CountDownLatch drained=new CountDownLatch(1);handler.post(drained::countDown);require(drained.await(5,TimeUnit.SECONDS),"Metrics drain timeout");phase.set(1);
            SettingsStructureProbe.main(t,()->{tiles(a).get(0).requestFocus();a.getWindow().addOnFrameMetricsAvailableListener(listener,handler);added[0]=true;});
            for(int i=0;i<20;i++){key(t,KeyEvent.KEYCODE_DPAD_RIGHT);key(t,KeyEvent.KEYCODE_DPAD_LEFT);}
            SettingsStructureProbe.main(t,()->a.getWindow().removeOnFrameMetricsAvailableListener(listener));added[0]=false;
            CountDownLatch done=new CountDownLatch(1);handler.post(done::countDown);require(done.await(5,TimeUnit.SECONDS),"Metrics drain timeout");
            result.putInt("actual_catalog_count",baseline.size());result.putInt("measured_query_count",240);result.putString("configuration","alpine wallpaper, rotation off, app animations on; existing system motion/sound settings unchanged; warmed query states and both own executors/UI drained twice; Alpine loaded/sharp verified; 300 ms motion settle before baseline; no forced GC");
            result.putString("library_perf","PASS: 60 actual-catalog search cycles and 20 paced native grid round trips");
            result.putString("protocol","libraryperf_v2");
            String[] states={"narrow","equivalent","no_match","clear"};
            List<List<Long>> series=Arrays.asList(narrow,equivalent,noMatch,clear);
            int matches=0;for(String label:baseline.values())if(label.toLowerCase(Locale.ROOT).contains(query[0]))matches++;
            result.putString("result_counts","narrow="+matches+",equivalent="+matches+",no_match=0,clear="+baseline.size());
            for(int state=0;state<states.length;state++){
                result.putString(states[state]+"_next_draw_ms",summary(series.get(state)));
                for(int block=0;block<3;block++)result.putString(states[state]+"_cycles_"+(block*20+1)+"_"+(block*20+20),summary(series.get(state).subList(block*20,block*20+20)));
            }
            result.putString("search_frame_total_ms",summary(searchFrames));result.putString("paced_navigation_frame_total_ms",summary(navFrames));
            result.putString("frame_callback_drops",dropped[0]+","+dropped[1]);result.putString("excluded_first_draw_frames",excluded[0]+","+excluded[1]);result.putString("memory_cycle_pssKB_nativeBytes_views",memory.toString());
            result.putString("scope","setText start to correct-result OnDraw validation and posted completion; instrumented debug workload, not physical input/IME/GPU presentation/long soak or 60 FPS proof");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            try{if(screen[0]!=null&&added[0])SettingsStructureProbe.main(t,()->screen[0].getWindow().removeOnFrameMetricsAvailableListener(listener));}catch(Exception|Error failure){cleanup=failure;}
            thread.quitSafely();thread.join(5000);
            if(screen[0]!=null)try{SettingsStructureProbe.finish(t,screen[0]);SettingsStructureProbe.quiescent(t,screen[0]);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try{require(!thread.isAlive(),"Metrics thread did not stop");if(screen[0]!=null)SettingsStructureProbe.quiescent(t,screen[0]);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static long edit(Instrumentation t,MainActivity a,Map<String,String> baseline,String query)throws Exception {
        CountDownLatch drawn=new CountDownLatch(1);long[] start={0},elapsed={0};AtomicReference<Throwable> failure=new AtomicReference<>();
        ViewTreeObserver[] observer={null};ViewTreeObserver.OnDrawListener[] callback={null};boolean[] seen={false};
        try {
            SettingsStructureProbe.main(t,()->{
                EditText search=a.findViewById(R.id.apps_search);View grid=a.findViewById(R.id.apps_grid);observer[0]=grid.getViewTreeObserver();
                callback[0]=()->{if(seen[0])return;seen[0]=true;try{
                    Set<String> expected=new HashSet<>(),actual=new HashSet<>();for(Map.Entry<String,String> entry:baseline.entrySet())if(entry.getValue().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))expected.add(entry.getKey());
                    for(View tile:tiles(a))require(actual.add((String)tile.getTag()),"Duplicate result");require(expected.equals(actual),"Search results differ from actual catalog");
                    require(a.getCurrentFocus()==search,"Search lost focus");
                }catch(Throwable error){failure.set(error);}finally{grid.post(()->{elapsed[0]=SystemClock.elapsedRealtimeNanos()-start[0];drawn.countDown();});}};
                observer[0].addOnDrawListener(callback[0]);start[0]=SystemClock.elapsedRealtimeNanos();search.setText(query);grid.invalidate();
            });
            require(drawn.await(2,TimeUnit.SECONDS),"Correct-result draw timed out");if(failure.get()!=null)throw new AssertionError("Draw validation failed",failure.get());return elapsed[0];
        }finally{SettingsStructureProbe.main(t,()->{if(observer[0]!=null&&observer[0].isAlive()&&callback[0]!=null)observer[0].removeOnDrawListener(callback[0]);});}
    }
    private static List<View> tiles(MainActivity a){List<View> result=new ArrayList<>();collect(a.findViewById(R.id.apps_grid),result);return result;}
    private static void collect(ViewGroup root,List<View> result){if(root==null)return;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.isFocusable()&&child.getTag()instanceof String)result.add(child);else if(child instanceof ViewGroup)collect((ViewGroup)child,result);}}
    private static void checkpoint(Instrumentation t,MainActivity a,StringBuilder out,int cycle)throws Exception{Debug.MemoryInfo info=new Debug.MemoryInfo();Debug.getMemoryInfo(info);int[] count={0};SettingsStructureProbe.main(t,()->{
            require(a.getCurrentFocus()==a.findViewById(R.id.apps_search)&&((EditText)a.findViewById(R.id.apps_search)).getText().length()==0,"Memory checkpoint must use empty query/search focus");
            java.lang.reflect.Field loaded=MainActivity.class.getDeclaredField("loadedWallpaper"),sharp=MainActivity.class.getDeclaredField("sharpWallpaper");loaded.setAccessible(true);sharp.setAccessible(true);
            require("alpine".equals(loaded.get(a))&&sharp.get(a)!=null,"Memory checkpoint wallpaper is not loaded Alpine");count[0]=views(a.getWindow().getDecorView());});if(out.length()>0)out.append(';');out.append(cycle).append(':').append(info.getTotalPss()).append(':').append(Debug.getNativeHeapAllocatedSize()).append(':').append(count[0]);}
    private static int views(View root){int n=1;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)n+=views(group.getChildAt(i));}return n;}
    private static String summary(List<Long> values){List<Long> copy=new ArrayList<>(values);Collections.sort(copy);if(copy.isEmpty())return"n=0";return"n="+copy.size()+",p50="+copy.get((copy.size()-1)/2)/1e6+",p95="+copy.get((int)Math.ceil(copy.size()*.95)-1)/1e6+",max="+copy.get(copy.size()-1)/1e6;}
    private static void key(Instrumentation t,int code){t.sendKeyDownUpSync(code);t.waitForIdleSync();SystemClock.sleep(100);}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}

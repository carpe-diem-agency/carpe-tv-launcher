package agency.carpediem.tv.prototype;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** One process, paced functional workloads and canonical checkpoints; PSS is descriptive. */
final class LongSessionProbe {
    static void run(Instrumentation t,Bundle args,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Disposable validation required");
        require(Build.VERSION.SDK_INT>=29,"INPUT_UNVERIFIED: owned-window inspection requires API29+");
        int blocks=Integer.parseInt(args.getString("blocks","2"));require(blocks==2||blocks==24,"Only 2 or 24 blocks supported");
        SharedPreferences prefs=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());
        List<java.lang.ref.WeakReference<MainActivity>> sessions=new CopyOnWriteArrayList<>();AtomicReference<MainActivity> latest=new AtomicReference<>();
        Application app=(Application)t.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle state){if(a instanceof MainActivity){sessions.add(new java.lang.ref.WeakReference<>((MainActivity)a));latest.set((MainActivity)a);a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(callbacks);Throwable primary=null;long started=SystemClock.elapsedRealtime();int cycles=0;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).putString("wallpaper","alpine").putBoolean(WallpaperRotation.ENABLED,false).commit(),"Prepare steady wallpaper");
            t.setInTouchMode(false);
            MainActivity screen=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(int n=0;n<3;n++)workload(t,screen,n);
            canonical(t,screen);checkpoint(t,screen,0,cycles,started);
            for(int block=1;block<=blocks;block++) {
                long deadline=SystemClock.elapsedRealtime()+300000;
                do {workload(t,screen,cycles++);canonical(t,screen);dwell(15000);}while(deadline-SystemClock.elapsedRealtime()>35000);
                while(SystemClock.elapsedRealtime()<deadline)dwell(Math.min(15000,deadline-SystemClock.elapsedRealtime()));
                if(block%4==0) {
                    SettingsStructureProbe.select(t,screen,5);activate(t,screen,R.id.about_authors);
                    final MainActivity old=screen;
                    SettingsStructureProbe.main(t,()->{require(old.findViewById(R.id.authors_third_party).requestFocus(),"Authors recreation origin missing");old.recreate();});
                    SettingsStructureProbe.await(t,()->latest.get()!=old,"Recreation did not create replacement");screen=latest.get();final MainActivity fresh=screen;
                    SettingsStructureProbe.await(t,()->fresh.hasWindowFocus()&&fresh.findViewById(R.id.authors_back)!=null&&fresh.getCurrentFocus()!=null&&fresh.getCurrentFocus().getId()==R.id.authors_third_party,"Recreation lost Authors context");
                    key(t,KeyEvent.KEYCODE_BACK);
                    SettingsStructureProbe.finish(t,old);SettingsStructureProbe.quiescent(t,old);
                    workload(t,screen,cycles++);
                }
                canonical(t,screen);checkpoint(t,screen,block,cycles,started);
            }
            result.putString("long_session","PASS: "+blocks+" five-minute blocks in one process; functional and explicit caps only");
            result.putString("long_session_scope","Paced Dpad, direct InputConnection query, owned notices, Device/Audit dwell; PSS descriptive, no leak/physical-remote/latency certification");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            for(java.lang.ref.WeakReference<MainActivity> reference:sessions)try{MainActivity screen=reference.get();if(screen!=null)SettingsStructureProbe.finish(t,screen);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try{for(java.lang.ref.WeakReference<MainActivity> reference:sessions){MainActivity screen=reference.get();if(screen!=null)SettingsStructureProbe.quiescent(t,screen);}SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            app.unregisterActivityLifecycleCallbacks(callbacks);
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static void workload(Instrumentation t,MainActivity screen,int cycle)throws Exception {
        awaitRoute(t,screen,cycle,"beforeHomeRequest",()->field(screen,"settingsDialog")==null&&field(screen,"pendingModalFocus")==null&&screen.hasWindowFocus(),"");
        SettingsStructureProbe.main(t,()->require(screen.findViewById(R.id.nav_home).requestFocus(),"Home rail unavailable"));
        awaitRoute(t,screen,cycle,"beforeDown",()->"home".equals(field(screen,"page"))&&screen.hasWindowFocus()
                &&screen.findViewById(R.id.nav_home).isSelected()&&screen.getCurrentFocus()==screen.findViewById(R.id.nav_home)
                &&screen.findViewById(R.id.home_time)!=null&&screen.findViewById(R.id.home_time).isShown()
                &&((View)field(screen,"content")).getAlpha()==1f,"");
        String[] beforeDown={null};SettingsStructureProbe.main(t,()->beforeDown[0]=focusDiagnostic(screen,screen.findViewById(R.id.nav_home)));
        key(t,KeyEvent.KEYCODE_DPAD_DOWN);
        awaitRoute(t,screen,cycle,"afterOneDown",()->"apps".equals(field(screen,"page"))&&screen.hasWindowFocus()
                &&screen.findViewById(R.id.nav_apps).isSelected()&&screen.getCurrentFocus()==screen.findViewById(R.id.nav_apps)
                &&screen.findViewById(R.id.apps_search)!=null&&screen.findViewById(R.id.apps_search).isShown()
                &&((View)field(screen,"content")).getAlpha()==1f,beforeDown[0]);
        EditText[] search={null};String[] positive={null};Map<String,String> catalog=new LinkedHashMap<>();
        SettingsStructureProbe.await(t,()->{ViewGroup grid=screen.findViewById(R.id.apps_grid);if(grid==null)return false;Map<String,String> ready=new LinkedHashMap<>();collect(grid,ready);return !ready.isEmpty();},"INPUT_UNVERIFIED: actual catalog unavailable");
        SettingsStructureProbe.main(t,()->{
            search[0]=screen.findViewById(R.id.apps_search);collect(screen.findViewById(R.id.apps_grid),catalog);
            require(!catalog.isEmpty(),"INPUT_UNVERIFIED: empty actual catalog");
            for(String label:catalog.values())if(!label.isEmpty()){positive[0]=label.substring(0,1);break;}
            if(!search[0].requestFocus())throw new AssertionError("Search cannot focus cycle="+cycle+" stage=Apps-search-request "+focusDiagnostic(screen,search[0]));
        });
        for(String query:new String[]{positive[0],"\u2063validation-no-match-"+cycle,""}) {
            Set<String> expected=new HashSet<>();for(Map.Entry<String,String> item:catalog.entrySet())if(item.getValue().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))expected.add(item.getKey());
            SettingsStructureProbe.main(t,()->{InputConnection connection=search[0].onCreateInputConnection(new EditorInfo());require(connection!=null&&connection.setSelection(0,search[0].length())&&connection.commitText(query,1),"Query protocol rejected");});
            SettingsStructureProbe.await(t,()->{Map<String,String> actual=new LinkedHashMap<>();collect(screen.findViewById(R.id.apps_grid),actual);return query.contentEquals(search[0].getText())&&actual.keySet().equals(expected)&&screen.getCurrentFocus()==search[0];},"Search workload lost exact catalog/focus");
            dwell(250);
        }
        SettingsStructureProbe.select(t,screen,5);activate(t,screen,R.id.about_authors);
        int[] notice={R.id.authors_code_license,R.id.authors_third_party,R.id.authors_font_license};activate(t,screen,notice[cycle%3]);
        SettingsStructureProbe.await(t,()->modal(screen)!=null,"Owned notice did not focus");
        key(t,KeyEvent.KEYCODE_DPAD_DOWN);key(t,KeyEvent.KEYCODE_BACK);
        SettingsStructureProbe.await(t,()->screen.hasWindowFocus()&&screen.getCurrentFocus()!=null&&screen.getCurrentFocus().getId()==notice[cycle%3],"Notice Back lost exact action");
        key(t,KeyEvent.KEYCODE_BACK);
        SettingsStructureProbe.select(t,screen,4);
        SettingsStructureProbe.await(t,()->screen.findViewById(R.id.device_specs)!=null,"Device view missing");dwell(1500);
        SettingsStructureProbe.select(t,screen,3);
        SettingsStructureProbe.await(t,()->screen.auditRefreshStats().active&&!screen.auditRefreshStats().inFlight,"Audit initial job did not settle");
        long[] before={0};SettingsStructureProbe.main(t,()->before[0]=screen.auditRefreshStats().applied);
        dwell(5500);
        SettingsStructureProbe.await(t,()->screen.auditRefreshStats().applied>before[0],"Audit timed refresh did not apply");
    }
    private static void canonical(Instrumentation t,MainActivity screen)throws Exception {
        SettingsStructureProbe.main(t,()->require(screen.findViewById(R.id.nav_home).requestFocus(),"Canonical Home missing"));t.waitForIdleSync();
        for(int pass=0;pass<2;pass++){for(String name:new String[]{"worker","wallpaperWorker"})((ExecutorService)field(screen,name)).submit(()->{}).get(15,TimeUnit.SECONDS);t.waitForIdleSync();}
        dwell(300);
        SettingsStructureProbe.main(t,()->{require(screen.hasWindowFocus()&&screen.getCurrentFocus()==screen.findViewById(R.id.nav_home)&&screen.findViewById(R.id.home_time)!=null,"Canonical Home/focus lost");MainActivity.AuditRefreshStats audit=screen.auditRefreshStats();require(!audit.active&&!audit.pendingTick&&!audit.rerunRequested,"Inactive Audit retained scheduler");require(modal(screen)==null,"Owned modal retained at checkpoint");});
    }
    private static void checkpoint(Instrumentation t,MainActivity screen,int block,int cycles,long started)throws Exception {
        JSONObject json=new JSONObject();SettingsStructureProbe.main(t,()->{
            Set<View> active=Collections.newSetFromMap(new IdentityHashMap<>());count(screen.getWindow().getDecorView(),active);
            Set<View> all=Collections.newSetFromMap(new IdentityHashMap<>());all.addAll(active);
            Map<?,?> pages=(Map<?,?>)field(screen,"pageViews");require(pages.size()<=3,"Page cache exceeded three known pages");int pools=0,tiles=0;long bytes=0;
            for(Object value:pages.values()){View page=(View)value;count(page,all);View grid=page.findViewById(R.id.apps_grid);if(grid!=null&&grid.getTag()!=null){Object pool=field(grid.getTag(),"pool");if(pool instanceof LibraryTilePool){LibraryTilePool p=(LibraryTilePool)pool;require(p.size()<=LibraryTilePool.MAX_COUNT&&p.bytes()<=LibraryTilePool.MAX_BYTES,"Library pool exceeded production cap");pools++;tiles+=p.size();bytes+=p.bytes();}}}
            android.util.DisplayMetrics metrics=screen.getResources().getDisplayMetrics();
            Map<String,String> cachedCatalog=new LinkedHashMap<>();
            Object appsPage=pages.get("apps");
            if(appsPage instanceof View){ViewGroup appsGrid=((View)appsPage).findViewById(R.id.apps_grid);if(appsGrid!=null)collect(appsGrid,cachedCatalog);}
            require(!cachedCatalog.isEmpty(),"Canonical cached actual catalog missing");
            json.put("api",Build.VERSION.SDK_INT).put("width_px",metrics.widthPixels).put("height_px",metrics.heightPixels)
                    .put("density",metrics.density).put("density_dpi",metrics.densityDpi)
                    .put("font_scale",screen.getResources().getConfiguration().fontScale)
                    .put("wallpaper",screen.getPreferences(0).getString("wallpaper","alpine"))
                    .put("cached_actual_catalog_count",cachedCatalog.size());
            json.put("active_views",active.size()).put("cached_only_views",all.size()-active.size()).put("pool_count",pools).put("pooled_tiles",tiles).put("pooled_artwork_bytes",bytes).put("audit_inactive",!screen.auditRefreshStats().active);
        });
        Debug.MemoryInfo memory=new Debug.MemoryInfo();Debug.getMemoryInfo(memory);
        json.put("block",block).put("cycles",cycles).put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("pid",android.os.Process.myPid()).put("pss_kib",memory.getTotalPss()).put("native_pss_kib",memory.nativePss).put("dalvik_pss_kib",memory.dalvikPss).put("other_pss_kib",memory.otherPss);
        Bundle status=new Bundle();status.putString("longsession_checkpoint",json.toString());t.sendStatus(1,status);
    }
    private static void awaitRoute(Instrumentation t,MainActivity screen,int cycle,String stage,SettingsStructureProbe.Check check,String before)throws Exception {
        try{SettingsStructureProbe.await(t,check,"Long-session route not ready");}
        catch(AssertionError timeout){
            String[] witness={null};SettingsStructureProbe.main(t,()->{View search=screen.findViewById(R.id.apps_search);witness[0]=focusDiagnostic(screen,search!=null?search:screen.findViewById(R.id.nav_home));});
            throw new AssertionError("Route failure cycle="+cycle+" stage="+stage+" before="+before+" after="+witness[0],timeout);
        }
    }
    private static String focusDiagnostic(MainActivity screen,View search)throws Exception {
        View focus=screen.getCurrentFocus();android.graphics.Rect rect=new android.graphics.Rect();
        boolean visible=search.getLocalVisibleRect(rect);
        String focusId="none";
        if(focus!=null){try{focusId=screen.getResources().getResourceEntryName(focus.getId());}catch(android.content.res.Resources.NotFoundException missing){focusId="generated";}}
        return "page="+field(screen,"page")+" renderingPage="+field(screen,"renderingPage")+" modalShowing="+(field(screen,"settingsDialog")!=null)+" pendingModalFocus="+(field(screen,"pendingModalFocus")!=null)+" navHome="+screen.findViewById(R.id.nav_home).isSelected()
                +" navApps="+screen.findViewById(R.id.nav_apps).isSelected()+" navSettings="+screen.findViewById(R.id.nav_settings).isSelected()
                +" focusId="+focusId+" focusType="+(focus==null?"none":focus.getClass().getSimpleName())
                +" windowFocus="+screen.hasWindowFocus()+" touchMode="+search.isInTouchMode()
                +" searchFocusable="+search.isFocusable()+" searchFocusableInTouch="+search.isFocusableInTouchMode()
                +" searchEnabled="+search.isEnabled()+" searchShown="+search.isShown()+" searchAttached="+search.isAttachedToWindow()
                +" searchVisibility="+search.getVisibility()+" rectVisible="+visible+" rect="+rect.toShortString();
    }
    private static void activate(Instrumentation t,MainActivity s,int id)throws Exception{SettingsStructureProbe.main(t,()->{View action=s.findViewById(id);require(action!=null&&action.requestFocus(),"Safe action missing");action.requestRectangleOnScreen(new android.graphics.Rect(0,0,action.getWidth(),action.getHeight()),true);});key(t,KeyEvent.KEYCODE_DPAD_CENTER);}
    private static View modal(MainActivity screen){for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews())if(root!=screen.getWindow().getDecorView()&&root.isAttachedToWindow()&&root.isShown()&&root.hasWindowFocus())return root;return null;}
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void count(View view,Set<View> seen){if(!seen.add(view))return;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)count(g.getChildAt(i),seen);}}
    private static void collect(ViewGroup root,Map<String,String> values){for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.getTag() instanceof String&&child.isFocusable()){TextView label=child.findViewById(R.id.app_name);if(label!=null)values.put((String)child.getTag(),label.getText().toString());}else if(child instanceof ViewGroup)collect((ViewGroup)child,values);}}
    private static void key(Instrumentation t,int key){t.sendKeyDownUpSync(key);t.waitForIdleSync();dwell(250);}
    private static void dwell(long millis){SystemClock.sleep(millis);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

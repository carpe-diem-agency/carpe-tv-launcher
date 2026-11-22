package agency.carpediem.tv.prototype;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Real catalog only: inactive Apps context survives Settings Activity recreation. */
final class InactiveLibraryStateProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),"Disposable validation package required");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());List<MainActivity> sessions=new CopyOnWriteArrayList<>();
        AtomicReference<MainActivity> latest=new AtomicReference<>();Application app=(Application)test.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks callback=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){if(a instanceof MainActivity){sessions.add((MainActivity)a);latest.set((MainActivity)a);}}
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(callback);Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare welcome state");test.setInTouchMode(false);
            MainActivity original=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(test,()->original.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(test,()->{ScrollView s=original.findViewById(R.id.apps_scroll);return s!=null&&s.getHeight()>0&&s.getChildCount()>0&&s.getChildAt(0).getHeight()>s.getHeight();},"INPUT_UNVERIFIED: actual installed catalog lacks scrollable rows");
            int[] offset={0};String[] selected={null},query={null};
            SettingsStructureProbe.main(test,()->{
                ScrollView scroll=original.findViewById(R.id.apps_scroll);List<View> tiles=new ArrayList<>();tiles(original.findViewById(R.id.apps_grid),tiles);
                require(!tiles.isEmpty(),"Installed catalog is empty");View tile=tiles.get(tiles.size()-1);require(tile.requestFocus(),"Cannot select actual final app");selected[0]=(String)tile.getTag();
                query[0]=((EditText)original.findViewById(R.id.apps_search)).getText().toString();
            });
            test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{
                ScrollView scroll=original.findViewById(R.id.apps_scroll);int max=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());scroll.scrollTo(0,max);offset[0]=scroll.getScrollY();require(offset[0]>0,"INPUT_UNVERIFIED: installed catalog cannot retain nonzero scroll");
                original.findViewById(R.id.nav_settings).requestFocus();
            });
            test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{require(original.findViewById(R.id.nav_settings).isSelected(),"Settings did not open before recreation");original.recreate();});
            SettingsStructureProbe.await(test,()->latest.get()!=null&&latest.get()!=original&&latest.get().hasWindowFocus(),"Recreated Settings did not regain window focus");
            MainActivity restored=latest.get();
            SettingsStructureProbe.main(test,()->{require(restored.findViewById(R.id.nav_settings).isSelected(),"Recreation lost Settings context");restored.findViewById(R.id.nav_apps).requestFocus();});
            SettingsStructureProbe.await(test,()->{ScrollView s=restored.findViewById(R.id.apps_scroll);return s!=null&&s.getHeight()>0&&s.getChildCount()>0&&s.getChildAt(0).getHeight()>s.getHeight();},"Restored actual library did not lay out");
            SettingsStructureProbe.main(test,()->{
                ScrollView scroll=restored.findViewById(R.id.apps_scroll);int max=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());
                require(query[0].contentEquals(((EditText)restored.findViewById(R.id.apps_search)).getText()),"Inactive library query was lost");
                require(Math.abs(scroll.getScrollY()-Math.min(offset[0],max))<=2,"Inactive library scroll was lost after Settings recreation");
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->require(restored.getCurrentFocus()!=null&&selected[0].equals(restored.getCurrentFocus().getTag()),"Apps rail did not restore selected package after inactive recreation"));
            // Settings runs first to preserve the established before-fix evidence.
            for(int inactivePage:new int[]{R.id.nav_settings,R.id.nav_home}) {
            String phase=inactivePage==R.id.nav_settings?"Settings":"Home";
            int[] refreshOffset={0};View[] settingsFocus={null};
            SettingsStructureProbe.main(test,()->{
                ScrollView scroll=restored.findViewById(R.id.apps_scroll);
                int max=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());
                scroll.scrollTo(0,Math.min(80,max));refreshOffset[0]=scroll.getScrollY();
                require(refreshOffset[0]>0,"INPUT_UNVERIFIED: catalog refresh fixture needs nonzero scroll");
                restored.findViewById(inactivePage).requestFocus();settingsFocus[0]=restored.getCurrentFocus();
                java.lang.reflect.Field all=MainActivity.class.getDeclaredField("allApps");all.setAccessible(true);
                List<?> actual=(List<?>)all.get(restored);List<Object> changed=new ArrayList<>(actual);
                boolean removed=false;
                for(int i=0;i<changed.size();i++){
                    Object item=changed.get(i);java.lang.reflect.Field pkg=item.getClass().getDeclaredField("pkg");pkg.setAccessible(true);
                    if(!selected[0].equals(pkg.get(item))){changed.remove(i);removed=true;break;}
                }
                require(removed,"INPUT_UNVERIFIED: no unfocused catalog item to exclude");
                java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showApps",List.class);show.setAccessible(true);show.invoke(restored,changed);
                require(restored.findViewById(inactivePage).isSelected(),"Controlled catalog refresh changed active page: "+phase);
                if(inactivePage==R.id.nav_settings)require(restored.getCurrentFocus()==settingsFocus[0],"Controlled catalog refresh changed Settings focus");
                else require(restored.getCurrentFocus()!=null&&restored.getCurrentFocus().getId()==settingsFocus[0].getId(),"Controlled catalog refresh changed Home focus");
                restored.findViewById(R.id.nav_apps).requestFocus();
            });
            SettingsStructureProbe.await(test,()->{ScrollView scroll=restored.findViewById(R.id.apps_scroll);return scroll!=null&&scroll.getHeight()>0&&scroll.getChildCount()>0&&scroll.getChildAt(0).getHeight()>scroll.getHeight();},"Refreshed inactive library did not lay out");
            SettingsStructureProbe.main(test,()->{
                ScrollView scroll=restored.findViewById(R.id.apps_scroll);int max=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());
                int expected=Math.min(refreshOffset[0],max);
                require(Math.abs(scroll.getScrollY()-expected)<=2,"Controlled refresh while "+phase+" active lost Apps scroll: expected="+expected+", actual="+scroll.getScrollY());
                require(query[0].contentEquals(((EditText)restored.findViewById(R.id.apps_search)).getText()),"Controlled refresh lost inactive query");
            });
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->require(restored.getCurrentFocus()!=null&&selected[0].equals(restored.getCurrentFocus().getTag()),"Controlled refresh lost surviving selected package"));
            result.putString("inactive_catalog_refresh_"+phase.toLowerCase(java.util.Locale.ROOT),"PASS: one unfocused real catalog item excluded through showApps; active-page context, Apps scroll/query/surviving focus preserved; no package broadcast claimed");
            }
            result.putString("inactive_library","PASS: actual catalog nonzero scroll/query/selected package survive Settings recreation and Apps re-entry");
            result.putString("inactive_library_scope","Empty query baseline; real installed catalog only, no synthetic apps; Activity recreation, not process death");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;for(MainActivity a:sessions)try{SettingsStructureProbe.finish(test,a);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try{for(MainActivity a:sessions)SettingsStructureProbe.quiescent(test,a);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            app.unregisterActivityLifecycleCallbacks(callback);if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
    private static void tiles(ViewGroup group,List<View> result){if(group==null)return;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.isFocusable()&&child.getTag()instanceof String)result.add(child);else if(child instanceof ViewGroup)tiles((ViewGroup)child,result);}}
    private static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
}

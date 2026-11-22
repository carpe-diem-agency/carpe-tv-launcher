package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Run while CatalogStateProbe holds the actual catalog worker gate. */
final class CachedCatalogStatusProbe {
    static void run(Instrumentation test,MainActivity screen)throws Exception {
        final String query="cached-status-query";
        SettingsStructureProbe.main(test,()->{
            put(screen,"allApps",new ArrayList<>());put(screen,"displayedApps",new ArrayList<>());
            put(screen,"appsReady",false);put(screen,"appsLoadFailed",false);put(screen,"query",query);
            screen.findViewById(R.id.nav_apps).requestFocus();call(screen,"renderPage");
        });
        SettingsStructureProbe.await(test,()->shown(screen,R.id.apps_search),"Initial loading Apps not visible");
        settings(test,screen);
        View[] content={null},focus={null};
        SettingsStructureProbe.main(test,()->{
            content[0]=(View)get(screen,"content");focus[0]=screen.getCurrentFocus();
            call(screen,"catalogLoadFailed"); // Exact production UI callback after refreshApps catches RuntimeException.
            require(content[0]==get(screen,"content")&&screen.getCurrentFocus()==focus[0],"Catalog error rebuilt Settings or stole focus");
        });
        apps(test,screen);
        SettingsStructureProbe.main(test,()->{
            require(hasText(screen.findViewById(R.id.apps_grid),screen.getString(R.string.apps_retry)),"Cached loading did not become Retry on real rail return");
            require(query.equals(get(screen,"query")),"Catalog error changed query");
            screen.findViewById(R.id.apps_search).requestFocus();
        });
        key(test,KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus() instanceof TextView
                &&screen.getString(R.string.apps_retry).contentEquals(((TextView)screen.getCurrentFocus()).getText()),"Returned Retry is unreachable from search"));
        settings(test,screen);
        SettingsStructureProbe.main(test,()->{
            View active=(View)get(screen,"content"),focused=screen.getCurrentFocus();
            call(screen,"showApps",List.class,new ArrayList<>());
            require(active==get(screen,"content")&&focused==screen.getCurrentFocus(),"Empty recovery rebuilt Settings or stole focus");
        });
        apps(test,screen);
        SettingsStructureProbe.main(test,()->{
            View grid=screen.findViewById(R.id.apps_grid);
            require(!hasText(grid,screen.getString(R.string.apps_retry))&&hasText(grid,screen.getString(R.string.search_empty)),"Cached Retry survived same-empty recovery");
            require(query.equals(get(screen,"query")),"Empty recovery changed query");
        });
    }
    private static void settings(Instrumentation test,MainActivity screen)throws Exception {
        SettingsStructureProbe.main(test,()->screen.findViewById(R.id.nav_apps).requestFocus());key(test,KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test,()->"settings".equals(get(screen,"page"))&&shown(screen,R.id.settings_detail)&&screen.getCurrentFocus()==screen.findViewById(R.id.nav_settings),"Real rail did not enter Settings");
    }
    private static void apps(Instrumentation test,MainActivity screen)throws Exception {
        key(test,KeyEvent.KEYCODE_DPAD_UP);
        SettingsStructureProbe.await(test,()->"apps".equals(get(screen,"page"))&&shown(screen,R.id.apps_search)&&screen.getCurrentFocus()==screen.findViewById(R.id.nav_apps),"Real rail return did not enter Apps");
    }
    private static boolean shown(MainActivity screen,int id){View view=screen.findViewById(id);return view!=null&&view.isShown();}
    private static boolean hasText(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return true;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),text))return true;}return false;}
    private static Object get(MainActivity screen,String name){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(screen);}catch(Exception error){throw new AssertionError(error);}}
    private static void put(MainActivity screen,String name,Object value){try{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(screen,value);}catch(Exception error){throw new AssertionError(error);}}
    private static void call(MainActivity screen,String name){try{Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(screen);}catch(Exception error){throw new AssertionError(error);}}
    private static void call(MainActivity screen,String name,Class<?> type,Object value){try{Method method=MainActivity.class.getDeclaredMethod(name,type);method.setAccessible(true);method.invoke(screen,value);}catch(Exception error){throw new AssertionError(error);}}
    private static void key(Instrumentation test,int code){test.sendKeyDownUpSync(code);test.waitForIdleSync();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

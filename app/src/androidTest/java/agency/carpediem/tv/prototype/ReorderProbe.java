package agency.carpediem.tv.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
final class ReorderProbe {
    static void run(Instrumentation t, Bundle result) throws Exception {
        t.setInTouchMode(false);
        MainActivity a=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        SystemClock.sleep(1800);
        Method get=MainActivity.class.getDeclaredMethod("getFavorites");get.setAccessible(true);
        Method begin=MainActivity.class.getDeclaredMethod("beginMove",String.class);begin.setAccessible(true);
        Method tiles=MainActivity.class.getDeclaredMethod("moveTiles");tiles.setAccessible(true);
        List<String> original=(List<String>)get.invoke(a);
        if(original.size()<2)throw new AssertionError("Need two favorites");
        String pkg=original.get(0);
        String stored=a.getPreferences(0).getString("favorite_order",null);
        try {
            t.runOnMainSync(()->{try{begin.invoke(a,pkg);}catch(Exception e){throw new RuntimeException(e);}});
            t.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
            boolean[] sidebarFlash={false};
            View root=a.getWindow().getDecorView();
            ViewTreeObserver.OnGlobalFocusChangeListener monitor=(oldFocus,newFocus)->{if(newFocus!=null && (newFocus.getId()==R.id.home_wifi || newFocus.getId()==R.id.home_bluetooth || newFocus.getId()==R.id.nav_home || newFocus.getId()==R.id.nav_apps || newFocus.getId()==R.id.nav_settings || newFocus.getId()==R.id.nav_power)) sidebarFlash[0]=true;};
            t.runOnMainSync(()->root.getViewTreeObserver().addOnGlobalFocusChangeListener(monitor));
            key(t,22);
            t.runOnMainSync(()->root.getViewTreeObserver().removeOnGlobalFocusChangeListener(monitor));
            require(!sidebarFlash[0],"Toolbar and sidebar never take transient focus while moving");
            t.runOnMainSync(()->require(a.getCurrentFocus()!=null && ((android.widget.TextView)a.getCurrentFocus().findViewById(R.id.app_name)).getText().toString().startsWith("‹"),"Move marker stays visible after activation key release"));
            List<String> moved=(List<String>)get.invoke(a);
            require(moved.get(1).equals(pkg),"Right reorders");
            require(Objects.equals(stored,a.getPreferences(0).getString("favorite_order",null)),"Not saved before OK");
            t.runOnMainSync(()->require(a.getCurrentFocus()!=null&&pkg.equals(a.getCurrentFocus().getTag()),"Focus follows tile"));
            key(t,4);require(original.equals(get.invoke(a)),"Back cancels");
            t.runOnMainSync(()->{try{begin.invoke(a,pkg);}catch(Exception e){throw new RuntimeException(e);}});
            key(t,21);require(original.equals(get.invoke(a)),"Left boundary bounded");
            key(t,22); key(t,23);
            require(((List<String>)get.invoke(a)).get(1).equals(pkg),"OK commits");
            require(!Objects.equals(stored,a.getPreferences(0).getString("favorite_order",null)),"Persisted");
            key(t,19); t.runOnMainSync(()->require(a.getCurrentFocus().getId()==R.id.home_wifi,"Up exits to toolbar after save"));
            result.putString("reorder","PASS: arrows reorder; selected focus retained; boundary bounded; Back restores; OK persists; toolbar navigation resumes");
        } finally {
            SharedPreferences.Editor e=a.getPreferences(0).edit(); if(stored==null)e.remove("favorite_order");else e.putString("favorite_order",stored);e.commit();
        }
    }
    static void key(Instrumentation t,int k){t.sendKeyDownUpSync(k);SystemClock.sleep(300);}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

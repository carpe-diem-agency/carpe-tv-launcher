package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.view.ViewGroup;
import java.util.concurrent.atomic.AtomicReference;

/** Library search and scroll context must survive an Activity recreation. */
final class LibraryStateProbe {
    static void run(Instrumentation test, Bundle result) throws Exception {
        test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0)
                .edit().putBoolean("welcome_seen", true).commit();
        MainActivity original = (MainActivity) test.startActivitySync(
                new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        AtomicReference<MainActivity> recreated = new AtomicReference<>();
        Application application = original.getApplication();
        Application.ActivityLifecycleCallbacks callbacks = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity && activity != original) recreated.set((MainActivity) activity);
            }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        application.registerActivityLifecycleCallbacks(callbacks);
        try {
            test.setInTouchMode(false);
            test.runOnMainSync(() -> original.findViewById(R.id.nav_apps).requestFocus());
            SystemClock.sleep(500);
            test.runOnMainSync(() -> {
                EditText search=original.findViewById(R.id.apps_search);
                require(search!=null,"Library is missing before recreation");
                search.setText("a");
            });
            test.waitForIdleSync();
            AtomicReference<Integer> savedScroll=new AtomicReference<>(0);
            AtomicReference<String> selectedPackage=new AtomicReference<>();
            test.runOnMainSync(() -> {
                ViewGroup grid=original.findViewById(R.id.apps_grid);
                if(grid.getChildCount()>0 && grid.getChildAt(0) instanceof ViewGroup) {
                    ViewGroup row=(ViewGroup)grid.getChildAt(0);
                    View tile=row.getChildAt(0);
                    if(tile.getTag() instanceof String && tile.requestFocus())
                        selectedPackage.set((String)tile.getTag());
                }
                ScrollView scroll=original.findViewById(R.id.apps_scroll);
                int max=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());
                int target=Math.min(max,80);
                scroll.scrollTo(0,target);
                savedScroll.set(scroll.getScrollY());
                original.recreate();
            });
            for(int i=0;i<40 && recreated.get()==null;i++)SystemClock.sleep(100);
            MainActivity screen=recreated.get();
            require(screen!=null,"Activity did not recreate");
            SystemClock.sleep(600);
            test.waitForIdleSync();
            test.runOnMainSync(() -> {
                EditText search=screen.findViewById(R.id.apps_search);
                ScrollView scroll=screen.findViewById(R.id.apps_scroll);
                require(search!=null && scroll!=null,"Recreation left the library: "+screen.findViewById(R.id.nav_apps).isSelected());
                require("a".contentEquals(search.getText()),"Search query was lost");
                if(selectedPackage.get()!=null)
                    require(screen.getCurrentFocus()!=null && selectedPackage.get().equals(screen.getCurrentFocus().getTag()),
                            "Selected app was lost");
                require(Math.abs(scroll.getScrollY()-savedScroll.get())<=2,
                        "Scroll position was lost: "+savedScroll.get()+" -> "+scroll.getScrollY());
            });
            result.putString("library_state","PASS: library, search, selected app and scroll survive recreation");
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks);
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

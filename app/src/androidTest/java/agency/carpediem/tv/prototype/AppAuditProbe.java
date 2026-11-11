package agency.carpediem.tv.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
final class AppAuditProbe {
    static void run(Instrumentation t,Bundle result) throws Exception {
        t.setInTouchMode(false);
        t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0)
                .edit().putBoolean("welcome_seen",true).commit();
        MainActivity a=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        SystemClock.sleep(2500);
        Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);
        Field category=MainActivity.class.getDeclaredField("settingsCategory");category.setAccessible(true);
        Method render=MainActivity.class.getDeclaredMethod("renderPage");render.setAccessible(true);
        t.runOnMainSync(()->{try{page.set(a,"settings");category.set(a,"audit");render.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});
        SystemClock.sleep(700);
        ViewGroup categories=a.findViewById(R.id.settings_categories);
        View[] entry={null};
        t.runOnMainSync(()->{for(int i=0;i<categories.getChildCount();i++){View v=categories.getChildAt(i);if(v instanceof TextView && ((TextView)v).getText().toString().equals(a.getString(R.string.audit_title)))entry[0]=v;}if(entry[0]==null)throw new AssertionError("Audit category");entry[0].requestFocus();});
        SystemClock.sleep(500);
        t.runOnMainSync(()->{
            try {
                View root=a.getWindow().getDecorView();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));
                try(java.io.FileOutputStream out=a.openFileOutput("audit-preview.png",0)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }catch(Exception e){throw new RuntimeException(e);}
        });
        AppAudit.Snapshot snapshot=AppAudit.read(a);
        require(snapshot.availableRam>0&&snapshot.totalRam>=snapshot.availableRam&&snapshot.freeDisk>0,"Real system totals");
        t.runOnMainSync(()->{
            View root=a.getWindow().getDecorView();
            require(hasText(root,a.getString(R.string.choose_home)),"Generic Home chooser is available without Shizuku");
            boolean googlePresent=snapshot.states.containsKey("com.google.android.apps.tv.launcherx")
                    && snapshot.states.get("com.google.android.apps.tv.launcherx")!=0;
            require(hasText(root,a.getString(R.string.stock_restore))==googlePresent,
                    "Google TV restore action appears only when its package exists");
            for(String pkg:AppAudit.PACKAGES){TextView state=root.findViewWithTag("audit-state:"+pkg);require(state!=null,"Package row "+pkg+"; category="+readField(a,"settingsCategory")+"; page="+readField(a,"page"));View row=(View)state.getParent();TextView name=row.findViewById(R.id.audit_app_name);
                require(name!=null && name.getRight()<=state.getLeft(),"Separate name/status columns "+pkg);
                require(name.getBottom()<=row.getHeight() && state.getBottom()<=row.getHeight(),"No clipped row text "+pkg);
                require(!name.isFocusable() && !state.isFocusable() && row.isFocusable(),"One remote focus per app "+pkg);
                int s=snapshot.states.get(pkg);int expected=s==0?R.string.audit_unavailable:s==1?R.string.audit_enabled:R.string.audit_disabled;require(state.getText().toString().equals(a.getString(expected)),"Actual package state "+pkg);}
        });
        if(snapshot.states.get("com.google.android.apps.tv.launcherx")!=0) {
            int packageState=a.getPackageManager().getApplicationEnabledSetting("com.google.android.apps.tv.launcherx");
            boolean homeSelected=HomeChoice.isSelected(a);
            Dialog[] stockDialog={null};
            t.runOnMainSync(()->{
                try {
                    Method actions=MainActivity.class.getDeclaredMethod("showStockLauncherActions");actions.setAccessible(true);
                    Dialog dialog=(Dialog)actions.invoke(a);
                    stockDialog[0]=dialog;
                    require(dialog!=null&&dialog.isShowing(),"Stock launcher details open");
                    TextView message=dialog.findViewById(R.id.stock_warning_message);
                    int expected=snapshot.states.get("com.google.android.apps.tv.launcherx")==1
                            ?R.string.stock_manage_enabled:R.string.stock_manage_disabled;
                    require(message!=null&&message.getText().toString().equals(a.getString(expected)),"Stock warning matches actual state");
                    Button primary=dialog.findViewById(R.id.stock_action_primary);
                    require(primary!=null&&primary.hasFocus(),"Primary stock action receives remote focus");
                    require(!primary.getText().toString()
                            .equals(a.getString(R.string.system_disable)),"No unverified stock disable action");
                } catch(Exception error) {throw new RuntimeException(error);}
            });
            SystemClock.sleep(250);
            t.runOnMainSync(()->{
                try {
                    Dialog dialog=stockDialog[0];
                    View surface=dialog.getWindow().getDecorView();
                    require(surface.getWidth()>0&&surface.getHeight()>0,"Stock dialog laid out before capture");
                    android.graphics.Bitmap screenshot=android.graphics.Bitmap.createBitmap(surface.getWidth(),surface.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                    surface.draw(new android.graphics.Canvas(screenshot));
                    try(java.io.FileOutputStream out=a.openFileOutput("stock-home-warning.png",0)) {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                    }
                    screenshot.recycle();
                    dialog.findViewById(R.id.stock_action_close).performClick();
                } catch(Exception error) {throw new RuntimeException(error);}
            });
            require(packageState==a.getPackageManager().getApplicationEnabledSetting("com.google.android.apps.tv.launcherx")
                    && homeSelected==HomeChoice.isSelected(a),"Reading stock controls changes neither package nor Home");
        }
        t.runOnMainSync(()->{
            try {
                Field displayed=MainActivity.class.getDeclaredField("displayedApps");displayed.setAccessible(true);displayed.set(a,null);
                Field apps=MainActivity.class.getDeclaredField("allApps");apps.setAccessible(true);
                Method show=MainActivity.class.getDeclaredMethod("showApps",java.util.List.class);show.setAccessible(true);
                View original=a.findViewById(R.id.settings_detail);View focused=a.getCurrentFocus();
                show.invoke(a,apps.get(a));
                require(original==a.findViewById(R.id.settings_detail),"Package refresh preserves settings tree");
                require(focused==a.getCurrentFocus(),"Package refresh preserves focus");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        t.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);SystemClock.sleep(150);
        String[] focusedLabel={"<none>"};
        t.runOnMainSync(()->{
            View focused=a.getCurrentFocus();
            if(focused instanceof TextView)focusedLabel[0]=((TextView)focused).getText().toString();
            else if(focused!=null)focusedLabel[0]=focused.getClass().getSimpleName()+" #"+focused.getId();
        });
        require(focusedLabel[0].equals(a.getString(R.string.audit_access_action)),
                "Right reaches system access setup; actual="+focusedLabel[0]);
        result.putString("audit","PASS: package state, RAM/storage, Home chooser, stock warning and restore entry, remote focus; no package mutations");
    }
    private static boolean hasText(View root,String text){
        if(root instanceof TextView && text.equals(((TextView)root).getText().toString()))return true;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(hasText(group.getChildAt(i),text))return true;}
        return false;
    }
    static Object readField(Object a,String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){return e;}}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

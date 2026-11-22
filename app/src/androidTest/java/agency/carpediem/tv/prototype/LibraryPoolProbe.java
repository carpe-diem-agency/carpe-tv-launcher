package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import java.util.*;

/** Scoped synthetic pool-policy checks plus real installed-catalog reuse. */
final class LibraryPoolProbe {
    static void run(Instrumentation t,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(t.getTargetContext().getPackageName()),"Validation package required");
        SharedPreferences prefs=t.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity a=null;Throwable primary=null;
        try {
            SettingsStructureProbe.main(t,()->policy(t.getTargetContext()));
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare welcome");t.setInTouchMode(false);
            MainActivity screen=a=(MainActivity)t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(t,()->screen.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(t,()->cards(screen).size()>=2,"Catalog needs two actual apps");
            Map<String,View> before=new LinkedHashMap<>(),pooled=new LinkedHashMap<>();
            SettingsStructureProbe.main(t,()->{
                EditText search=screen.findViewById(R.id.apps_search);search.requestFocus();before.putAll(cards(screen));search.setText("carpe-pool-no-such-application-987654321");
                require(cards(screen).isEmpty(),"No-match query did not empty real grid");
                LibraryTilePool pool=pool(screen);require(pool.size()<=32&&pool.bytes()<=8L*1024*1024,"Real pool exceeded bounds");
                // A take/put round trip inspects ownership without manufacturing apps.
                for(Map.Entry<String,View> entry:before.entrySet()){
                    String label=((TextView)entry.getValue().findViewById(R.id.app_name)).getText().toString();
                    LinearLayout tile=pool.take(entry.getKey(),label);
                    if(tile!=null){require(tile.getParent()==null,"Pooled card retains an old row");pooled.put(entry.getKey(),tile);pool.put(entry.getKey(),tile);}
                }
                require(!pooled.isEmpty(),"Actual catalog retained no reusable cards within pool budget");search.setText("");
                Map<String,View> after=cards(screen);require(after.keySet().equals(before.keySet()),"Clearing query changed actual package set");
                for(Map.Entry<String,View> entry:pooled.entrySet())require(after.get(entry.getKey())==entry.getValue(),"Clear failed to reuse a retained real card");
                require(screen.getCurrentFocus()==search,"Pool transition stole search focus");
            });
            result.putString("library_pool","PASS: actual wide/no-match/clear card reuse; scoped count/bitmap bounds, detached ownership, label/package invalidation");
            result.putString("library_pool_scope","Synthetic private views/bitmaps test policy only; actual installed catalog tests reuse; lifecycle/query regressions remain separate probes");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally{Throwable cleanup=null;if(a!=null)try{SettingsStructureProbe.finish(t,a);SettingsStructureProbe.quiescent(t,a);}catch(Exception|Error failure){cleanup=failure;}try{if(a!=null)SettingsStructureProbe.quiescent(t,a);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}}
    }
    private static void policy(Context context){
        LibraryTilePool count=new LibraryTilePool(2,8L*1024*1024);List<LinearLayout> views=new ArrayList<>();List<Bitmap> bitmaps=new ArrayList<>();
        LibraryTilePool bytes=new LibraryTilePool(32,8L*1024*1024);
        try{
            LinearLayout parent=new LinearLayout(context);
            for(int i=0;i<3;i++){LinearLayout tile=tile(context,"label",null);views.add(tile);parent.addView(tile);count.put("count"+i,tile);require(tile.getParent()==null,"put must detach card from old row");}
            require(parent.getChildCount()==0&&count.size()==2,"Count cap or old-row ownership failed");require(count.take("count0","label")==null,"Count eviction did not remove oldest entry");
            LinearLayout changed=count.take("count1","different");require(changed==null,"Changed label reused stale card");
            count.retain(Collections.emptyMap());require(count.size()==0&&count.bytes()==0,"Removed catalog packages remain pooled");
            for(int i=0;i<3;i++){Bitmap bitmap=Bitmap.createBitmap(1024,1024,Bitmap.Config.ARGB_8888);bitmaps.add(bitmap);LinearLayout tile=tile(context,"bitmap",bitmap);views.add(tile);bytes.put("bytes"+i,tile);}
            require(bytes.size()==2&&bytes.bytes()==8L*1024*1024,"Bitmap byte budget not enforced independently of count");require(bytes.take("bytes0","bitmap")==null,"Byte eviction retained oldest bitmap");
            for(Bitmap bitmap:bitmaps)require(!bitmap.isRecycled(),"Pool eviction recycled a potentially shared bitmap");
            Bitmap oversized=Bitmap.createBitmap(1536,1536,Bitmap.Config.ARGB_8888);bitmaps.add(oversized);LinearLayout huge=tile(context,"huge",oversized);views.add(huge);bytes.put("huge",huge);
            require(bytes.size()<=32&&bytes.bytes()<=8L*1024*1024&&bytes.take("huge","huge")==null,"Oversized card entered bounded pool");
            bytes.clear();require(bytes.size()==0&&bytes.bytes()==0,"clear retained pool ownership");
            for(Bitmap bitmap:bitmaps)require(!bitmap.isRecycled(),"Pool rejection/clear recycled a potentially shared bitmap");
        }finally{count.clear();bytes.clear();for(LinearLayout view:views)((ImageView)view.findViewById(R.id.app_icon)).setImageDrawable(null);for(Bitmap bitmap:bitmaps)bitmap.recycle();}
    }
    private static LinearLayout tile(Context context,String name,Bitmap bitmap){LinearLayout tile=new LinearLayout(context);TextView text=new TextView(context);text.setId(R.id.app_name);text.setText(name);tile.addView(text);ImageView image=new ImageView(context);image.setId(R.id.app_icon);if(bitmap!=null)image.setImageDrawable(new BitmapDrawable(context.getResources(),bitmap));tile.addView(image);return tile;}
    private static LibraryTilePool pool(MainActivity a)throws Exception{Object state=a.findViewById(R.id.apps_grid).getTag();Field field=state.getClass().getDeclaredField("pool");field.setAccessible(true);return(LibraryTilePool)field.get(state);}
    private static Map<String,View> cards(MainActivity a){Map<String,View> result=new LinkedHashMap<>();collect(a.findViewById(R.id.apps_grid),result);return result;}
    private static void collect(ViewGroup root,Map<String,View> result){if(root==null)return;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.isFocusable()&&child.getTag()instanceof String)result.put((String)child.getTag(),child);else if(child instanceof ViewGroup)collect((ViewGroup)child,result);}}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

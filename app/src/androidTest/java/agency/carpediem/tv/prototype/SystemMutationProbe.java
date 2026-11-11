package agency.carpediem.tv.prototype;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.concurrent.*;
/** Explicitly invoked device mutation test. Leaves telemetry disabled and restores its journal entry. */
final class SystemMutationProbe {
 static final String PKG="com.miui.tv.analytics";
 static void run(Instrumentation t,Bundle result)throws Exception{
  Context context=t.getTargetContext();
  for(int i=0;i<20&&!SystemAccess.ready();i++)SystemClock.sleep(250);
  require(SystemAccess.ready(),"Explicit Shizuku permission required");
  int initial=context.getPackageManager().getApplicationEnabledSetting(PKG);
  require(initial==3,"This scenario requires analytics initially disabled-user");
  SharedPreferences journal=context.getSharedPreferences("system_changes",0);
  boolean had=journal.contains(PKG);int saved=journal.getInt(PKG,1);
  MainActivity a=(MainActivity)t.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  SystemClock.sleep(1800);
  Method confirm=MainActivity.class.getDeclaredMethod("confirmSystemAction",String.class,String.class);confirm.setAccessible(true);
  Field dialog=MainActivity.class.getDeclaredField("systemConfirmation");dialog.setAccessible(true);
  Field busy=MainActivity.class.getDeclaredField("systemOperation");busy.setAccessible(true);
  try{
   AlertDialog cancel=ui(t,()->{confirm.invoke(a,"restore",PKG);return (AlertDialog)dialog.get(a);});
   SystemClock.sleep(300);
   boolean warned=ui(t,()->{
    TextView message=cancel.findViewById(android.R.id.message);
    boolean valid=message!=null&&message.getText().toString().contains(PKG)&&message.getText().toString().contains(a.getString(R.string.system_restore_warning));
    saveWindow(a,cancel,"system-warning-restore.png");
    cancel.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();return valid;
   });
   require(warned,"Real warning identifies target and consequences");
   require(context.getPackageManager().getApplicationEnabledSetting(PKG)==initial,"Cancel changes no package");
   require(journal.contains(PKG)==had&&journal.getInt(PKG,1)==saved,"Cancel changes no journal");
   action(t,a,confirm,dialog,busy,"restore");
   require(context.getPackageManager().getApplicationEnabledSetting(PKG)==1,"Restore explicitly enables pre-existing disabled package");
   action(t,a,confirm,dialog,busy,"disable");
   require(context.getPackageManager().getApplicationEnabledSetting(PKG)==3,"Disable reaches Android");
   require(journal.getInt(PKG,-1)==1,"Original enabled state is journaled");
   action(t,a,confirm,dialog,busy,"restore");
   require(context.getPackageManager().getApplicationEnabledSetting(PKG)==1&&!journal.contains(PKG),"Restore replays saved state and clears journal");
   action(t,a,confirm,dialog,busy,"disable");
   require(context.getPackageManager().getApplicationEnabledSetting(PKG)==3,"Telemetry left disabled");
   result.putString("system_mutation","PASS: actual confirmation/cancel, explicit enable, disable, durable recovery journal, restore; final telemetry disabled-user");
  }finally{
   if(context.getPackageManager().getApplicationEnabledSetting(PKG)!=initial)SystemAccess.disableAnalytics(context,PKG);
   SharedPreferences.Editor edit=journal.edit();if(had)edit.putInt(PKG,saved);else edit.remove(PKG);require(edit.commit(),"Restore pre-test journal");
  }
 }
 static void action(Instrumentation t,MainActivity a,Method confirm,Field dialog,Field busy,String operation)throws Exception{
  ui(t,()->{confirm.invoke(a,operation,PKG);return null;});
  SystemClock.sleep(300);
  ui(t,()->{AlertDialog d=(AlertDialog)dialog.get(a);require(d!=null&&d.isShowing(),"Visible confirmation for "+operation);if(operation.equals("disable"))saveWindow(a,d,"system-warning-disable.png");d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();return null;});
  long end=SystemClock.uptimeMillis()+40000;
  while(SystemClock.uptimeMillis()<end){if(!ui(t,()->busy.getBoolean(a)))return;SystemClock.sleep(200);}
  throw new AssertionError("Operation completion timeout");
 }
 static void saveWindow(Context a,AlertDialog dialog,String name)throws Exception{
  android.view.View root=dialog.getWindow().getDecorView();
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
  root.draw(new android.graphics.Canvas(bitmap));try(java.io.FileOutputStream out=a.openFileOutput(name,0)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
 }
 static <T>T ui(Instrumentation test,Callable<T> action)throws Exception{FutureTask<T> work=new FutureTask<>(action);test.runOnMainSync(work);return work.get();}
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}

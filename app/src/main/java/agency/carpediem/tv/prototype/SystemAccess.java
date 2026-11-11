package agency.carpediem.tv.prototype;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;
/** No automatic permissions, background polling, or generic command interface. */
final class SystemAccess {
 static boolean ready(){try{return Shizuku.pingBinder()&&!Shizuku.isPreV11()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(RuntimeException e){return false;}}
 static boolean running(){try{return Shizuku.pingBinder();}catch(RuntimeException e){return false;}}
 static void request(Activity a){
  if(!running()){
   openManager(a);
  }else if(!ready())Shizuku.requestPermission(508);
 }
 static void openManager(Activity a){
  Intent intent=a.getPackageManager().getLeanbackLaunchIntentForPackage("moe.shizuku.privileged.api");
  if(intent==null)intent=a.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
  if(intent!=null)a.startActivity(intent);
  else a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/download/")));
 }
 interface Operation<T>{T run(ISystemPackages service)throws Exception;}
 private static <T>T withService(Context c,Operation<T> operation)throws Exception{
  if(android.os.Process.myUid()/100000!=0)throw new SecurityException("Only the primary Android user is supported");
  if(!ready())throw new SecurityException("System access not authorized");
  CompletableFuture<ISystemPackages> ready=new CompletableFuture<>();
  ServiceConnection connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName n,IBinder b){ready.complete(ISystemPackages.Stub.asInterface(b));}
   public void onServiceDisconnected(ComponentName n){ready.completeExceptionally(new IllegalStateException("Disconnected"));}
  };
  Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(c,SystemPackageService.class)).tag("analytics-control-v1").version(3).daemon(false).processNameSuffix("package-control");
  try{Shizuku.bindUserService(args,connection);return operation.run(ready.get(10,TimeUnit.SECONDS));}
  finally{try{Shizuku.unbindUserService(args,connection,true);}catch(RuntimeException ignored){}}
 }
 static void disableAnalytics(Context c,String pkg)throws Exception{
  PackagePolicy.checkAnalytics(pkg);
  withService(c,s->{int before=s.readAnalyticsState(pkg);if(before>=2)return null;
   SharedPreferences p=c.getSharedPreferences("system_changes",0);
   if(!p.contains(pkg)&&!p.edit().putInt(pkg,before).commit())throw new IllegalStateException("Recovery state could not be saved");
   s.disableAnalytics(pkg);return null;});
 }
 static void restoreAnalytics(Context c,String pkg)throws Exception{
  PackagePolicy.checkAnalytics(pkg);
  withService(c,s->{SharedPreferences p=c.getSharedPreferences("system_changes",0);int before=p.getInt(pkg,1);
   s.restoreAnalytics(pkg,before);if(!p.edit().remove(pkg).commit())throw new IllegalStateException("Recovery journal could not be cleared");return null;});
 }
 static boolean openBluetoothSettings(Context c)throws Exception{return withService(c,s->s.openBluetoothSettings());}
 static boolean sleepDevice(Context c)throws Exception{return withService(c,s->s.sleepDevice());}
 static void enableStockLauncher(Context c)throws Exception{withService(c,s->s.enableStockLauncher());}
}

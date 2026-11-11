package agency.carpediem.tv.prototype;
import android.app.Instrumentation;
import android.os.Bundle;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
/** Real Shizuku transport probe; strictly read-only. No package enable/disable methods invoked. */
final class SystemAccessProbe {
 static void run(Instrumentation test,Bundle result)throws Exception{
  for(int i=0;i<20&&!SystemAccess.ready();i++)android.os.SystemClock.sleep(250);
  if(!SystemAccess.ready())throw new AssertionError("User permission required; no changes made");
  android.content.Context context=test.getTargetContext();
  Map<String,?> before=new HashMap<>(context.getSharedPreferences("system_changes",0).getAll());
  Method bind=SystemAccess.class.getDeclaredMethod("withService",android.content.Context.class,SystemAccess.Operation.class);bind.setAccessible(true);
  Object state=bind.invoke(null,context,(SystemAccess.Operation<Integer>) service->{
   int actual=service.readAnalyticsState("com.miui.tv.analytics");
   if(actual<0||actual>4)throw new AssertionError("Unknown package state");
   boolean refused=false;
   try{service.readAnalyticsState("com.google.android.apps.tv.launcherx");}catch(SecurityException expected){refused=true;}
   if(!refused)throw new AssertionError("Analytics endpoint accepted stock launcher");
   return actual;
  });
  if(!before.equals(context.getSharedPreferences("system_changes",0).getAll()))throw new AssertionError("Read modified recovery journal");
  result.putString("system_access","PASS: authorized Shizuku UserService, actual analytics state="+state+", launcher rejected by analytics endpoint, recovery journal unchanged; no package mutations");
 }
}

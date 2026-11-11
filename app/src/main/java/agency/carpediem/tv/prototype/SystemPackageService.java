package agency.carpediem.tv.prototype;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.*;
/** Shizuku UserService: fixed operations only, no caller-supplied shell text. */
public final class SystemPackageService extends ISystemPackages.Stub {
 public SystemPackageService(){}
 private int state(String pkg){
  String text=run(new String[]{"/system/bin/dumpsys","package",pkg});
  Matcher m=Pattern.compile("(?m)^\\s*User 0:.*\\benabled=(\\d+)").matcher(text);
  if(!m.find())throw new IllegalStateException("State unavailable");int value=Integer.parseInt(m.group(1));if(value<0||value>4)throw new IllegalStateException("Unknown state");return value;
 }
 @Override public synchronized int readAnalyticsState(String pkg){PackagePolicy.checkAnalytics(pkg);return state(pkg);}
 @Override public synchronized int disableAnalytics(String pkg){
  PackagePolicy.checkAnalytics(pkg);run(new String[]{"/system/bin/pm","disable-user","--user","0",pkg});
  int actual=state(pkg);if(actual!=3)throw new IllegalStateException("Disable not applied");return actual;
 }
 @Override public synchronized int restoreAnalytics(String pkg,int original){
  run(PackagePolicy.restoreCommand(pkg,original));int actual=state(pkg);
  if(actual!=original)throw new IllegalStateException("Restore not applied");return actual;
 }
 @Override public synchronized boolean enableStockLauncher(){
  // This endpoint cannot disable, reset, uninstall, clear, or choose any other package.
  run(new String[]{"/system/bin/pm","enable","--user","0","com.google.android.apps.tv.launcherx"});
  if(state("com.google.android.apps.tv.launcherx")!=1)throw new IllegalStateException("Enable not applied");return true;
 }
 @Override public synchronized boolean sleepDevice(){run(new String[]{"/system/bin/input","keyevent","223"});return true;}
 @Override public synchronized boolean openBluetoothSettings(){
  String result=run(new String[]{"/system/bin/am","start","--user","0","-n","com.android.tv.settings/.slice.SliceActivity","-a","android.settings.SLICE_SETTINGS","--activity-clear-top","--es","slice_uri","content://com.android.tv.settings.accessories.sliceprovider/general"});
  return !result.contains("Error:") && !result.contains("Exception");
 }
 private String run(String[] command){
  int uid=android.os.Process.myUid();if(uid!=0&&uid!=2000)throw new SecurityException("System access required");
  Process process=null;ExecutorService reader=Executors.newSingleThreadExecutor();
  try{
   process=new ProcessBuilder(command).redirectErrorStream(true).start();final Process child=process;
   Future<String> output=reader.submit(()->{ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
    try(InputStream in=child.getInputStream()){while((n=in.read(b))!=-1){if(bytes.size()+n>524288)throw new IOException("Output limit");bytes.write(b,0,n);}}
    return bytes.toString(StandardCharsets.UTF_8.name());});
   if(!process.waitFor(8,TimeUnit.SECONDS))throw new IOException("Timeout");String text=output.get(1,TimeUnit.SECONDS);
   if(process.exitValue()!=0)throw new IOException("Operation rejected");return text;
  }catch(Exception e){throw new IllegalStateException("System operation failed",e);}
  finally{if(process!=null)process.destroy();reader.shutdownNow();}
 }
 @Override public void destroy(){System.exit(0);}
}

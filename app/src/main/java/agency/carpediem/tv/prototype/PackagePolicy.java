package agency.carpediem.tv.prototype;
/** Analytics-only mutations. The stock launcher has a separate enable-only endpoint. */
final class PackagePolicy {
 static boolean telemetry(String pkg){return "com.miui.tv.analytics".equals(pkg)||"com.xiaomi.statistic".equals(pkg);}
 static void checkAnalytics(String pkg){if(!telemetry(pkg))throw new SecurityException("Unsupported analytics package");}
 static String[] restoreCommand(String pkg,int state){
  checkAnalytics(pkg);
  if(state!=0&&state!=1)throw new SecurityException("Only an originally enabled analytics package may be restored");
  return new String[]{"/system/bin/pm",state==0?"default-state":"enable","--user","0",pkg};
 }
}

package agency.carpediem.tv.prototype;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
final class SleepTimerProbe {
    static void armLiveTest(Context c, Bundle out) throws Exception {
        android.app.AlarmManager manager=c.getSystemService(android.app.AlarmManager.class);
        if(android.os.Build.VERSION.SDK_INT>=31 && !manager.canScheduleExactAlarms()) throw new AssertionError("Exact alarm permission required");
        if(remainingUserTimer(c)) throw new AssertionError("User timer active");
        long until=SystemClock.elapsedRealtime()+5000;
        while(!SystemAccess.ready() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100);
        SleepTimer.schedule(c,1);
        out.putString("sleep_live","ARMED: production schedule and receiver in 60 seconds");
    }
    private static boolean remainingUserTimer(Context c) { return SleepTimer.remaining(c)>0; }
    static void run(Context c, Bundle out) {
        SharedPreferences p=c.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE);
        if(p.contains("deadline")) throw new AssertionError("Do not overwrite a user's timer");
        if(!"00:00".equals(SleepTimer.countdown(0)) || !"01:01".equals(SleepTimer.countdown(61000))
                || !"120:00".equals(SleepTimer.countdown(7200000))) throw new AssertionError("Minute/second display");
        long future=SystemClock.elapsedRealtime()+60000;
        int boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        try {
            p.edit().putLong("deadline",future).putInt("boot",boot).commit();
            if(SleepTimer.remaining(c)<=0) throw new AssertionError("Remaining time");
            SleepTimer receiver=new SleepTimer();
            receiver.onReceive(c,new Intent().setAction("wrong").putExtra("deadline",future));
            receiver.onReceive(c,new Intent().setAction("agency.carpediem.tv.SLEEP_TIMER").putExtra("deadline",future-1));
            receiver.onReceive(c,new Intent().setAction("agency.carpediem.tv.SLEEP_TIMER").putExtra("deadline",future));
            if(p.getLong("deadline",0)!=future) throw new AssertionError("Wrong, stale or early alarm consumed timer");
            p.edit().putInt("boot",boot-1).commit();
            if(SleepTimer.remaining(c)!=0) throw new AssertionError("Old boot must be inactive");
            SleepTimer.cancel(c);
            if(p.contains("deadline")) throw new AssertionError("Cancellation");
            out.putString("sleep_timer","PASS: remaining, wrong action, stale/early alarm, reboot, cancellation; no sleep executed");
        } finally { SleepTimer.cancel(c); }
    }
}

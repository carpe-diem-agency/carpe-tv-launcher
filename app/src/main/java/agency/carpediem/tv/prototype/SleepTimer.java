package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Toast;

/** One user-requested alarm; no polling, foreground service, or network access. */
public final class SleepTimer extends BroadcastReceiver {
    private static final String ACTION = "agency.carpediem.tv.SLEEP_TIMER";
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE); }
    private static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    private static PendingIntent alarm(Context c, long deadline) {
        return PendingIntent.getBroadcast(c, 0, new Intent(c, SleepTimer.class).setAction(ACTION)
                .putExtra("deadline", deadline), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static long remaining(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getInt("boot", -2) != boot(c)) return 0;
        return Math.max(0, p.getLong("deadline", 0) - SystemClock.elapsedRealtime());
    }
    static String countdown(long remaining) {
        long seconds=(Math.max(0,remaining)+999)/1000;
        return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    static void cancel(Context c) {
        c.getSystemService(AlarmManager.class).cancel(alarm(c, 0));
        prefs(c).edit().clear().commit();
    }
    static void schedule(Context c, int minutes) {
        if (minutes < 1 || minutes > 120) throw new IllegalArgumentException("Duration out of range");
        if (!SystemAccess.ready()) throw new IllegalStateException("Home service unavailable");
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) throw new SecurityException("Exact alarms unavailable");
        long deadline = SystemClock.elapsedRealtime() + minutes * 60000L;
        // Commit before scheduling: an obsolete broadcast cannot trigger a replacement timer.
        if (!prefs(c).edit().putLong("deadline", deadline).putInt("boot", boot(c)).commit())
            throw new IllegalStateException("Cannot save timer");
        try { manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, deadline, alarm(c, deadline)); }
        catch (RuntimeException error) { cancel(c); throw error; }
    }
    static void show(Activity a) { new SleepTimerDialog(a).show(); }
    private static void toast(Context c, int message) { Toast.makeText(c, message, Toast.LENGTH_LONG).show(); }
    @Override public void onReceive(Context c, Intent i) {
        if (!ACTION.equals(i.getAction())) return;
        SharedPreferences p = prefs(c);
        long expected = p.getLong("deadline", 0);
        if (expected == 0 || expected != i.getLongExtra("deadline", -1) || p.getInt("boot", -2) != boot(c)) return;
        if (SystemClock.elapsedRealtime() < expected) return;
        p.edit().clear().commit(); // one shot, never retry a sleep action after wake
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                long until=SystemClock.elapsedRealtime()+3000;
                while(!SystemAccess.ready() && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100);
                if (!SystemAccess.sleepDevice(c)) throw new IllegalStateException("Sleep rejected"); }
            catch (Exception error) { new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> toast(c,R.string.sleep_failed)); }
            finally { pending.finish(); }
        }, "carpe-sleep-once").start();
    }
}

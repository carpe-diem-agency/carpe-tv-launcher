package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

/** Repeated real D-pad routes with process-memory and view-tree checkpoints. */
final class NavigationSoakProbe {
    private NavigationSoakProbe() { }

    static void run(Instrumentation probe, Bundle result) {
        if (!probe.getTargetContext().getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Navigation soak requires the isolated preview package");
        SharedPreferences prefs = probe.getTargetContext()
                .getSharedPreferences(MainActivity.class.getName(), 0);
        boolean hadWelcome = prefs.contains("welcome_seen");
        boolean welcome = prefs.getBoolean("welcome_seen", false);
        prefs.edit().putBoolean("welcome_seen", true).commit();
        try {
            probe.setInTouchMode(false);
            MainActivity screen = (MainActivity) probe.startActivitySync(
                    new Intent(probe.getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SystemClock.sleep(1200);
            probe.runOnMainSync(() -> screen.findViewById(R.id.nav_home).requestFocus());
            StringBuilder pss = new StringBuilder();
            StringBuilder views = new StringBuilder();
            for (int cycle = 1; cycle <= 200; cycle++) {
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                key(probe, KeyEvent.KEYCODE_DPAD_DOWN);
                key(probe, KeyEvent.KEYCODE_DPAD_UP);
                key(probe, KeyEvent.KEYCODE_DPAD_UP);
                if (cycle % 40 == 0) {
                    SystemClock.sleep(250);
                    boolean[] valid = {false};
                    int[] viewCount = {0};
                    probe.runOnMainSync(() -> {
                        View focus = screen.getCurrentFocus();
                        valid[0] = focus != null && focus.getId() == R.id.nav_home
                                && screen.findViewById(R.id.home_time).isShown();
                        viewCount[0] = countViews(screen.getWindow().getDecorView());
                    });
                    if (!valid[0]) throw new AssertionError("Home or rail focus lost at cycle " + cycle);
                    Debug.MemoryInfo memory = new Debug.MemoryInfo();
                    Debug.getMemoryInfo(memory);
                    if (pss.length() > 0) { pss.append(','); views.append(','); }
                    pss.append(memory.getTotalPss());
                    views.append(viewCount[0]);
                }
            }
            result.putString("navigation_soak", "PASS: 200 Home / Apps / Settings / Apps / Home rail cycles");
            result.putString("pss_kb_at_40_80_120_160_200", pss.toString());
            result.putString("view_count_at_40_80_120_160_200", views.toString());
        } finally {
            SharedPreferences.Editor restore = prefs.edit();
            if (hadWelcome) restore.putBoolean("welcome_seen", welcome);
            else restore.remove("welcome_seen");
            restore.commit();
        }
    }

    private static void key(Instrumentation probe, int code) {
        probe.sendKeyDownUpSync(code);
        SystemClock.sleep(70);
    }

    private static int countViews(View view) {
        int count = 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += countViews(group.getChildAt(i));
        }
        return count;
    }
}

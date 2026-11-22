package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.util.Locale;

/** Explicit, small sharing allowlist. Never reads serials, Android IDs, networks or accounts. */
final class DiagnosticReport {
    private DiagnosticReport() { }

    static final class Report {
        private final String text, displayText;
        Report(String text, String displayText) { this.text = text; this.displayText = displayText; }
        String text() { return text; }
        String displayText() { return displayText; }
    }

    static Report capture(Activity activity) { return capture((Context) activity); }

    static Report capture(Context context) {
        String version = clean(BuildConfig.VERSION_NAME);
        String manufacturer = clean(Build.MANUFACTURER);
        String model = clean(Build.MODEL);
        if (!model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT)))
            model = manufacturer + " " + model;
        String android = clean(Build.VERSION.RELEASE);
        String build = clean(Build.DISPLAY);
        String output = "unknown", displayOutput = context.getString(R.string.diagnostic_unknown);
        try {
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) {
                Display.Mode mode = manager.getDefaultDisplay().getMode();
                float hz = mode.getRefreshRate();
                String rate = Math.abs(hz - Math.round(hz)) < .01f
                        ? Integer.toString(Math.round(hz)) : String.format(Locale.ROOT, "%.2f", hz);
                output = mode.getPhysicalWidth() + "x" + mode.getPhysicalHeight() + " @ " + rate + " Hz";
                displayOutput = context.getString(R.string.diagnostics_output,
                        mode.getPhysicalWidth(), mode.getPhysicalHeight(), rate);
            }
        } catch (RuntimeException unavailable) { /* Do not infer a physical mode from logical pixels. */ }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        String ui = metrics.widthPixels + "x" + metrics.heightPixels;
        int developer = -1;
        try {
            developer = Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED);
        } catch (Settings.SettingNotFoundException | RuntimeException unavailable) { }
        String developerState = developer < 0 ? "unknown" : developer == 0 ? "disabled" : "enabled";
        String localizedDeveloper = context.getString(developer < 0 ? R.string.diagnostic_unknown
                : developer == 0 ? R.string.diagnostic_disabled : R.string.diagnostic_enabled);
        String text = "Carpe TV: " + version + " (" + BuildConfig.VERSION_CODE + ", " + BuildConfig.BUILD_TYPE + ")\n"
                + "Device: " + model + "\n"
                + "Android: " + android + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Build: " + build + "\n"
                + "Output: " + output + "\n"
                + "Interface: " + ui + "\n"
                + "Developer options: " + developerState;
        String displayText = context.getString(R.string.diagnostics_report, version, BuildConfig.VERSION_CODE,
                model, android, Build.VERSION.SDK_INT, build, displayOutput,
                metrics.widthPixels + " × " + metrics.heightPixels, localizedDeveloper);
        return new Report(text, displayText);
    }

    static String issueUrl(Context context, String template) {
        if (!"bug.yml".equals(template) && !"idea.yml".equals(template) && !"other.yml".equals(template))
            throw new IllegalArgumentException("Unknown issue template");
        return Uri.parse("https://github.com/carpe-diem-agency/carpe-tv-launcher/issues/new")
                .buildUpon().appendQueryParameter("template", template)
                .appendQueryParameter("device", capture(context).text()).build().toString();
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < value.length() && text.length() < 96; i++) {
            char c = value.charAt(i);
            text.append(Character.isISOControl(c) ? ' ' : c);
        }
        return text.toString().trim();
    }
}

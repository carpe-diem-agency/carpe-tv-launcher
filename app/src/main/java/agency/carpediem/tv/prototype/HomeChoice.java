package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Only user-mediated platform actions. Does not disable another launcher or service. */
final class HomeChoice {
    private static final String CHECK_PREFS = "home_button_check";
    private static final String CHECK_PENDING = "pending";
    private static final String CHECK_STARTED = "started_at";
    private static final String CHECK_FINGERPRINT = "verified_fingerprint";
    static void request(Activity activity) {
        ComponentName entry = new ComponentName(activity, MainActivity.class.getPackage().getName() + ".HomeEntry");
        activity.getPackageManager().setComponentEnabledSetting(entry,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = activity.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(activity, AppLanguage.text(activity, R.string.home_already), Toast.LENGTH_LONG).show(); return;
                }
                clearVerification(activity);
                try { activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 401); return; }
                catch (RuntimeException ignored) { }
            }
        }
        chooseOther(activity);
    }
    static void chooseOther(Activity activity) {
        clearVerification(activity);
        for (String action : new String[]{Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS}) {
            try { activity.startActivity(new Intent(action)); return; }
            catch (RuntimeException ignored) { }
        }
        new android.app.AlertDialog.Builder(activity).setTitle(AppLanguage.text(activity, R.string.home_picker_title))
                .setMessage(AppLanguage.text(activity, R.string.home_picker_help))
                .setPositiveButton(AppLanguage.text(activity, R.string.understood), null).show();
    }
    static String status(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        boolean selected = isSelected(activity);
        String homeLabel;
        if (selected) homeLabel = AppLanguage.text(activity, R.string.app_name);
        else if (info != null && info.activityInfo != null) homeLabel = info.loadLabel(activity.getPackageManager()).toString();
        else homeLabel = AppLanguage.text(activity, R.string.home_unknown);
        return AppLanguage.text(activity, R.string.home_prefix) + homeLabel;
    }
    static boolean needsRemoteHomeCheck(Context context) {
        if (!isSelected(context) || isRemoteVerified(context)) return false;
        // The Home role and resolveActivity can both report this launcher while
        // an OEM remote still opens its own Home. Only the physical key proves
        // the routing, so keep the reminder visible after selection.
        return true;
    }
    static void startRemoteCheck(Activity activity) {
        if (!isSelected(activity)) {
            Toast.makeText(activity, AppLanguage.text(activity, R.string.setup_home_choose_first), Toast.LENGTH_LONG).show();
            return;
        }
        activity.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(CHECK_PENDING, true).putLong(CHECK_STARTED, System.currentTimeMillis())
                .remove(CHECK_FINGERPRINT).apply();
        Toast.makeText(activity, AppLanguage.text(activity, R.string.setup_home_press_remote), Toast.LENGTH_LONG).show();
        try { activity.startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        catch (RuntimeException error) {
            activity.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE).edit().putBoolean(CHECK_PENDING, false).apply();
            Toast.makeText(activity, AppLanguage.text(activity, R.string.settings_unavailable), Toast.LENGTH_LONG).show();
        }
    }
    static void recordHomeReturn(Context context, Intent intent) {
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME) || !isSelected(context)) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE);
        long elapsed = System.currentTimeMillis() - prefs.getLong(CHECK_STARTED, 0L);
        if (prefs.getBoolean(CHECK_PENDING, false) && elapsed >= 0 && elapsed < 120000L)
            prefs.edit().putBoolean(CHECK_PENDING, false)
                .putString(CHECK_FINGERPRINT, Build.FINGERPRINT).apply();
    }
    static boolean isRemoteVerified(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE);
        if (!isSelected(context)) {
            if (prefs.contains(CHECK_FINGERPRINT)) clearVerification(context);
            return false;
        }
        return Build.FINGERPRINT.equals(prefs.getString(CHECK_FINGERPRINT, null));
    }
    static void clearVerification(Context context) {
        context.getSharedPreferences(CHECK_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
    static boolean platformResolvesToCarpe(Context context) {
        ResolveInfo info = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null
                && context.getPackageName().equals(info.activityInfo.packageName);
    }
    static boolean isSelected(Context context) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = context.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) return roles.isRoleHeld(RoleManager.ROLE_HOME);
        }
        ResolveInfo info = context.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null && context.getPackageName().equals(info.activityInfo.packageName);
    }
}

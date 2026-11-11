package agency.carpediem.tv.prototype;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;
import java.util.Set;

/** A stable preference file for both the launcher icon and its Home activity alias. */
final class LauncherSettings {
    private static final String PRIMARY = MainActivity.class.getName();
    private static final String HOME_ALIAS = MainActivity.class.getPackage().getName() + ".HomeEntry";
    private static final String IMPORTED = "home_alias_imported";

    static synchronized SharedPreferences get(Context context) {
        SharedPreferences primary = context.getSharedPreferences(PRIMARY, Context.MODE_PRIVATE);
        if (primary.getBoolean(IMPORTED, false)) return primary;

        // Activity.getPreferences() used the alias name when Android launched HomeEntry.
        // Keep existing primary choices; import only keys that were saved exclusively there.
        SharedPreferences alias = context.getSharedPreferences(HOME_ALIAS, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = primary.edit();
        for (Map.Entry<String, ?> entry : alias.getAll().entrySet()) {
            String key = entry.getKey();
            if (primary.contains(key)) continue;
            Object value = entry.getValue();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Set) {
                @SuppressWarnings("unchecked") Set<String> values = (Set<String>) value;
                edit.putStringSet(key, values);
            }
        }
        edit.putBoolean(IMPORTED, true).commit();
        return primary;
    }

    private LauncherSettings() { }
}

package agency.carpediem.tv.prototype;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** App-only locale choice on every supported Android version; never changes device settings. */
final class AppLanguage {
    static String selected(Context c) { return c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getString("language",""); }
    static Context wrap(Context base) {
        String tag=selected(base);
        Locale locale=tag.isEmpty() ? base.getResources().getConfiguration().getLocales().get(0) : Locale.forLanguageTag(tag);
        Locale.setDefault(locale); // TextClock/date formatting in this process uses the same app locale.
        Configuration cfg=new Configuration(base.getResources().getConfiguration()); cfg.setLocales(new LocaleList(locale));
        return base.createConfigurationContext(cfg);
    }
    static String text(Context c,int id) { return wrap(c).getString(id); }
    static String label(Context c) {
        String tag=selected(c);
        return text(c,tag.equals("uk") ? R.string.language_uk : tag.equals("en") ? R.string.language_en : R.string.language_system);
    }
    static void choose(Context c,String tag) {
        if (!tag.isEmpty() && !tag.equals("uk") && !tag.equals("en")) throw new IllegalArgumentException("Unsupported language");
        c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).edit().putString("language",tag).apply();
    }
}

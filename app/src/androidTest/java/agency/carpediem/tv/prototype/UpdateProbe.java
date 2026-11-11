package agency.carpediem.tv.prototype;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;

/** Exercises real metadata/APK validation against actual signed APK fixtures. */
final class UpdateProbe {
    interface Check { void run() throws Exception; }
    private static void reject(Check action, String reason) throws Exception {
        try { action.run(); } catch (Exception expected) {
            if (!expected.getMessage().contains(reason)) throw new AssertionError("Wrong rejection: "+expected.getMessage());
            return;
        }
        throw new AssertionError("Invalid update accepted: "+reason);
    }
    static void run(Context c, Bundle args, Bundle result) throws Exception {
        java.util.Map<String,?> saved = new java.util.HashMap<>(UpdateManager.prefs(c).getAll());
        try {
            JSONObject good = new JSONObject(new String(Files.readAllBytes(new File(args.getString("metadata")).toPath()),"UTF-8"));
            String feed = args.getString("feed");
            UpdateManager.validateMetadata(c,good,feed);
            UpdateManager.prefs(c).edit().putString("metadata",good.toString()).commit();
            UpdateManager.validateApk(c,new File(args.getString("apk")));
            JSONObject bad = new JSONObject(good.toString()); bad.put("sha256",new String(new char[64]).replace('\0','0'));
            UpdateManager.prefs(c).edit().putString("metadata",bad.toString()).commit();
            reject(() -> UpdateManager.validateApk(c,new File(args.getString("apk"))),"Checksum");
            JSONObject wrongApp=new JSONObject(good.toString()); wrongApp.put("applicationId","other.application");
            reject(() -> UpdateManager.validateMetadata(c,wrongApp,feed),"іншого застосунку");
            JSONObject old=new JSONObject(good.toString()); old.put("versionCode",UpdateManager.version(UpdateManager.installed(c)));
            reject(() -> UpdateManager.validateMetadata(c,old,feed),"актуальну");
            JSONObject huge=new JSONObject(good.toString()); huge.put("byteSize",UpdateManager.MAX_BYTES+1);
            reject(() -> UpdateManager.validateMetadata(c,huge,feed),"розмір");
            reject(() -> UpdateManager.safeUrl(c,"http://example.com/update.json"),"HTTPS");
            JSONObject wrongKey = new JSONObject(new String(Files.readAllBytes(new File(args.getString("wrongMetadata")).toPath()),"UTF-8"));
            UpdateManager.prefs(c).edit().putString("metadata",wrongKey.toString()).commit();
            reject(() -> UpdateManager.validateApk(c,new File(args.getString("wrongApk"))),"Інший ключ");
            UpdateManager.prefs(c).edit().putString("state","checking").putInt("operationPid",-99).commit();
            UpdateManager.recoverNow(c);
            if(!"idle".equals(UpdateManager.state(c))) throw new AssertionError("Interrupted check did not recover");
            android.content.res.Configuration en = new android.content.res.Configuration(c.getResources().getConfiguration()); en.setLocale(java.util.Locale.ENGLISH);
            android.content.res.Configuration uk = new android.content.res.Configuration(en); uk.setLocale(java.util.Locale.forLanguageTag("uk"));
            if(!"Settings".equals(c.createConfigurationContext(en).getString(R.string.settings))) throw new AssertionError("English resource missing");
            if(!"Налаштування".equals(c.createConfigurationContext(uk).getString(R.string.settings))) throw new AssertionError("Ukrainian resource missing");
            result.putString("recovery_and_locales","PASS: interrupted check recovered; English and Ukrainian resources resolved");
            result.putString("updater_validation","PASS: real signed candidate accepted; corrupted checksum, wrong app, old version, oversize, insecure remote URL and wrong signing key rejected");
        } finally {
            android.content.SharedPreferences.Editor restore = UpdateManager.prefs(c).edit().clear();
            for(java.util.Map.Entry<String,?> entry:saved.entrySet()) {
                Object value=entry.getValue(); String key=entry.getKey();
                if(value instanceof String) restore.putString(key,(String)value);
                else if(value instanceof Long) restore.putLong(key,(Long)value);
                else if(value instanceof Integer) restore.putInt(key,(Integer)value);
                else if(value instanceof Boolean) restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Float) restore.putFloat(key,(Float)value);
            }
            restore.commit();
        }
    }
}

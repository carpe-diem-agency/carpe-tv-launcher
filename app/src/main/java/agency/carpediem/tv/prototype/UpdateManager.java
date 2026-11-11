package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native background download plus user-confirmed installer. No repository credentials. */
final class UpdateManager {
    static final long MAX_BYTES = 50L * 1024 * 1024;
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean REFRESHING = new AtomicBoolean();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("updates", Context.MODE_PRIVATE); }
    static boolean debug(Context c) { return (c.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0; }
    static long version(PackageInfo p) { return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; }
    static PackageInfo installed(Context c) throws Exception { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0); }
    static String state(Context c) { return prefs(c).getString("state", "idle"); }
    static String appVersion(Context c) { try { return installed(c).versionName; } catch (Exception e) { return "—"; } }
    static boolean busy(Context c) { return Arrays.asList("checking", "preparing", "downloading", "verifying", "installing", "confirming").contains(state(c)); }
    static void setState(Context c, String state, String message) {
        prefs(c).edit().remove("messageKey").remove("messageSuffix").putString("state", state).putString("message", message).putInt("operationPid",android.os.Process.myPid()).putLong("stateAt", System.currentTimeMillis()).apply();
    }
    static void error(Context c, Exception e) {
        if(e instanceof UpdateError) { UpdateError problem=(UpdateError)e; setState(c,"error",problem.resource,problem.suffix); }
        else setState(c,"error",R.string.update_action_failed);
    }
    static void setState(Context c,String state,int resource) { setState(c,state,resource,""); }
    static void setState(Context c,String state,int resource,String suffix) {
        prefs(c).edit().putString("state",state).putString("messageKey",c.getResources().getResourceEntryName(resource))
                .putString("messageSuffix",suffix).putString("message",AppLanguage.text(c,resource)+suffix).putInt("operationPid",android.os.Process.myPid()).putLong("stateAt",System.currentTimeMillis()).apply();
    }
    static String message(Context c,boolean noFeed) {
        String key=prefs(c).getString("messageKey","");
        if (key.equals("update_success_prefix")) return AppLanguage.text(c,R.string.update_success);
        if(!key.isEmpty()) {
            int id=c.getResources().getIdentifier(key,"string",c.getPackageName());
            if(id!=0) return AppLanguage.text(c,id)+prefs(c).getString("messageSuffix","");
        }
        String state=state(c);
        if(state.equals("ready")) return AppLanguage.text(c,R.string.update_ready);
        if(state.equals("available")) { try { return AppLanguage.text(c,R.string.update_available_prefix)+metadata(c).optString("versionName"); } catch(Exception ignored) { } }
        if(state.equals("error")) return AppLanguage.text(c,R.string.update_action_failed);
        String legacy=prefs(c).getString("message","");
        if(legacy.startsWith("Оновлено") || legacy.startsWith("Updated")) return AppLanguage.text(c,R.string.update_success);
        return AppLanguage.text(c,noFeed ? R.string.update_no_channel : R.string.check_prompt);
    }
    static final class UpdateError extends IOException {
        final int resource; final String suffix;
        UpdateError(Context c,int resource) { this(c,resource,""); }
        UpdateError(Context c,int resource,String suffix) { super(AppLanguage.text(c,resource)+suffix); this.resource=resource; this.suffix=suffix; }
    }
    static String source() { return BuildConfig.UPDATE_FEED_URL; }
    static void recover(Context context) {
        Context c=context.getApplicationContext(); WORKER.execute(() -> recoverNow(c));
    }
    static void recoverNow(Context c) {
        String state=state(c);
        boolean previousProcess=prefs(c).getInt("operationPid",-1)!=android.os.Process.myPid();
        if(previousProcess && (state.equals("checking") || state.equals("preparing"))) {
            setState(c,"idle",R.string.update_action_failed);
        } else if(previousProcess && state.equals("verifying")) {
            prefs(c).edit().putString("state","downloading").apply(); refreshNow(c);
        } else if(state.equals("installing") || state.equals("confirming")) {
            int id=prefs(c).getInt("sessionId",-1);
            if(id<0 || c.getPackageManager().getPackageInstaller().getSessionInfo(id)==null) {
                try { if(target(c).isFile()) setState(c,"ready",R.string.update_repeat_install); else setState(c,"idle",R.string.update_action_failed); }
                catch(Exception error) { setState(c,"idle",R.string.update_action_failed); }
            }
        }
    }
    static void maybeCheck(Context context) {
        Context c = context.getApplicationContext();
        if (busy(c) && prefs(c).getInt("operationPid",-1)!=android.os.Process.myPid()) { recover(c); return; }
        if (source().isEmpty() || busy(c) || "ready".equals(state(c))) return;
        if (System.currentTimeMillis() - prefs(c).getLong("checkedAt", 0) > 24L * 60 * 60 * 1000) check(c, source());
    }
    static URL safeUrl(Context c, String value) throws Exception {
        URL u = new URL(value);
        if (u.getUserInfo() != null || u.getRef() != null) throw new UpdateError(c,R.string.update_bad_url);
        if ("https".equals(u.getProtocol())) return u;
        String host = u.getHost();
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.startsWith("192.168.") || host.startsWith("10.") || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
        if (debug(c) && "http".equals(u.getProtocol()) && local) return u;
        throw new UpdateError(c,R.string.update_https);
    }
    static HttpURLConnection connect(Context c, String value, String method) throws Exception {
        URL u = safeUrl(c, value);
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection h = (HttpURLConnection) u.openConnection();
            h.setConnectTimeout(10000); h.setReadTimeout(15000); h.setInstanceFollowRedirects(false);
            h.setRequestMethod(method); h.setRequestProperty("Accept", "application/json, application/octet-stream");
            int code = h.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = h.getHeaderField("Location"); h.disconnect();
                if (location == null) throw new UpdateError(c,R.string.update_bad_redirect);
                URL next = safeUrl(c, new URL(u, location).toString());
                if (!sameOrigin(u, next) && !("https".equals(next.getProtocol()) && githubHost(next.getHost())))
                    throw new UpdateError(c,R.string.update_unsupported_redirect);
                u = next; continue;
            }
            if (code != 200) { h.disconnect(); throw new UpdateError(c,code == 404 ? R.string.update_unpublished : R.string.update_channel_unavailable_prefix, code == 404 ? "" : code + ")"); }
            return h;
        }
        throw new UpdateError(c,R.string.update_redirects);
    }
    private static boolean githubHost(String h) { return h.equals("github.com") || h.equals("api.github.com") || h.equals("release-assets.githubusercontent.com") || h.equals("objects.githubusercontent.com"); }
    private static boolean sameOrigin(URL a, URL b) { return a.getProtocol().equals(b.getProtocol()) && a.getHost().equals(b.getHost()) && a.getPort() == b.getPort(); }
    private static JSONObject readJson(Context c, String source) throws Exception {
        HttpURLConnection h = connect(c, source, "GET");
        try (InputStream input = h.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) { if (out.size() + count > 65536) throw new UpdateError(c,R.string.update_metadata_large); out.write(buffer, 0, count); }
            return new JSONObject(out.toString("UTF-8"));
        } finally { h.disconnect(); }
    }
    static JSONObject validateMetadata(Context c, JSONObject m, String feed) throws Exception {
        if (m.getInt("schemaVersion") != 1 || !c.getPackageName().equals(m.getString("applicationId"))) throw new UpdateError(c,R.string.update_wrong_app);
        String channel = m.getString("channel");
        if (!channel.equals("stable") && !(debug(c) && channel.equals("internal"))) throw new UpdateError(c,R.string.update_channel_unsupported);
        if (m.getLong("versionCode") <= version(installed(c))) throw new UpdateError(c,R.string.update_old);
        if (m.getInt("minSdk") > Build.VERSION.SDK_INT) throw new UpdateError(c,R.string.update_android_newer);
        long size = m.getLong("byteSize");
        if (size < 1024 || size > MAX_BYTES || !m.getString("sha256").matches("[a-fA-F0-9]{64}")) throw new UpdateError(c,R.string.update_bad_size_hash);
        URL a = safeUrl(c, m.getString("apkUrl")), f = safeUrl(c, feed);
        if (!sameOrigin(a, f) && !("https".equals(a.getProtocol()) && githubHost(a.getHost()))) throw new UpdateError(c,R.string.update_host);
        if (m.getString("versionName").length() > 64 || m.optString("releaseNotes").length() > 4000) throw new UpdateError(c,R.string.update_bad_description);
        return m;
    }
    static void check(Context context, String feed) {
        Context c = context.getApplicationContext();
        if (feed.isEmpty()) { setState(c,"idle",R.string.update_no_feed); return; }
        if (busy(c) || "ready".equals(state(c)) || !BUSY.compareAndSet(false, true)) return;
        prefs(c).edit().remove("metadata").apply();
        setState(c,"checking",R.string.update_checking);
        WORKER.execute(() -> {
            try {
                JSONObject m = readJson(c, feed);
                // GitHub release endpoint may reference a small update.json asset.
                if (m.has("assets")) {
                    if (m.optBoolean("draft") || m.optBoolean("prerelease")) throw new UpdateError(c,R.string.update_draft);
                    JSONArray assets = m.getJSONArray("assets"); String metadata = null;
                    for (int i = 0; i < assets.length(); i++) if ("update.json".equals(assets.getJSONObject(i).optString("name"))) metadata = assets.getJSONObject(i).getString("browser_download_url");
                    if (metadata == null) throw new UpdateError(c,R.string.update_missing_metadata);
                    URL asset = safeUrl(c, metadata);
                    if (!"https".equals(asset.getProtocol()) || !githubHost(asset.getHost())) throw new UpdateError(c,R.string.update_channel_invalid);
                    m = readJson(c, metadata);
                }
                prefs(c).edit().putLong("checkedAt", System.currentTimeMillis()).apply();
                if (m.has("versionCode") && m.getLong("versionCode") <= version(installed(c))) { setState(c,"idle",R.string.update_current); return; }
                validateMetadata(c, m, feed);
                prefs(c).edit().putString("metadata", m.toString()).apply();
                setState(c,"available",R.string.update_available_prefix,m.getString("versionName"));
                if (prefs(c).getBoolean("autoDownload", false)) download(c);
            } catch (Exception e) { error(c, e); }
            finally { BUSY.set(false); }
        });
    }
    static JSONObject metadata(Context c) throws Exception { return new JSONObject(prefs(c).getString("metadata", "{}")); }
    static File target(Context c) throws Exception {
        File directory = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) throw new UpdateError(c,R.string.storage_unavailable);
        return new File(directory, "carpe-update.apk");
    }
    static void download(Context context) {
        Context c = context.getApplicationContext();
        if (!"available".equals(state(c)) && !"error".equals(state(c))) return;
        setState(c,"preparing",R.string.update_preparing);
        WORKER.execute(() -> {
            try {
                JSONObject m = metadata(c); long size = m.getLong("byteSize");
                if (size > MAX_BYTES || size < 1024) throw new UpdateError(c,R.string.update_apk_size);
                File file = target(c); File dir = file.getParentFile(); if (!dir.exists() && !dir.mkdirs()) throw new UpdateError(c,R.string.storage_failed);
                if (dir.getUsableSpace() < size * 2 + 20 * 1024 * 1024) throw new UpdateError(c,R.string.storage_low);
                HttpURLConnection h = connect(c, m.getString("apkUrl"), "HEAD");
                String finalUrl;
                try { if (h.getContentLengthLong() != size) throw new UpdateError(c,R.string.update_server_size); finalUrl = h.getURL().toString(); } finally { h.disconnect(); }
                DownloadManager dm = c.getSystemService(DownloadManager.class);
                long old = prefs(c).getLong("downloadId", -1); if (old != -1) dm.remove(old);
                if (file.exists() && !file.delete()) throw new UpdateError(c,R.string.update_cleanup_failed);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(finalUrl))
                        .setTitle(AppLanguage.text(c, R.string.update_download_title_prefix) + m.getString("versionName"))
                        .setDescription(AppLanguage.text(c, R.string.update_notification_description))
                        .setMimeType("application/vnd.android.package-archive")
                        .setAllowedOverMetered(false).setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, "carpe-update.apk");
                long id = dm.enqueue(request);
                prefs(c).edit().putLong("downloadId", id).putLong("downloaded", 0).apply();
                setState(c,"downloading",R.string.update_downloading);
            } catch (Exception e) { error(c, e); }
        });
    }
    static void refresh(Context context) {
        Context c = context.getApplicationContext();
        if (!"downloading".equals(state(c)) || !REFRESHING.compareAndSet(false, true)) return;
        WORKER.execute(() -> { try { refreshNow(c); } finally { REFRESHING.set(false); } });
    }
    static void refreshNow(Context c) {
        if (!"downloading".equals(state(c))) return;
        long id = prefs(c).getLong("downloadId", -1);
        try (Cursor cursor = c.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst()) throw new UpdateError(c,R.string.update_download_missing);
            long bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            prefs(c).edit().putLong("downloaded", bytes).apply();
            if (bytes > metadata(c).getLong("byteSize") || bytes > MAX_BYTES) { c.getSystemService(DownloadManager.class).remove(id); throw new UpdateError(c,R.string.update_server_large); }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                setState(c,"verifying",R.string.update_verifying); validateApk(c, target(c)); setState(c,"ready",R.string.update_ready);
            } else if (status == DownloadManager.STATUS_FAILED) throw new UpdateError(c,R.string.update_download_failed);
        } catch (Exception e) { cleanupDownload(c); error(c, e); }
    }
    static void validateApk(Context c, File file) throws Exception {
        JSONObject m = metadata(c);
        if (!file.isFile() || file.length() != m.getLong("byteSize") || file.length() > MAX_BYTES) throw new UpdateError(c,R.string.update_incomplete);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) digest.update(b,0,n); }
        StringBuilder hex = new StringBuilder(); for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        if (!hex.toString().equalsIgnoreCase(m.getString("sha256"))) throw new UpdateError(c,R.string.update_checksum);
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo apk = c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo current = c.getPackageManager().getPackageInfo(c.getPackageName(), flags);
        if (apk == null || !c.getPackageName().equals(apk.packageName)) throw new UpdateError(c,R.string.update_apk_wrong_app);
        if (version(apk) != m.getLong("versionCode") || version(apk) <= version(current) || !m.getString("versionName").equals(apk.versionName)) throw new UpdateError(c,R.string.update_version_mismatch);
        if (apk.applicationInfo == null || apk.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new UpdateError(c,R.string.update_incompatible);
        if (!certificates(apk).equals(certificates(current)) || certificates(current).isEmpty()) throw new UpdateError(c,R.string.update_wrong_key);
    }
    private static Set<String> certificates(PackageInfo p) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? (p.signingInfo == null ? null : p.signingInfo.getApkContentsSigners()) : p.signatures;
        Set<String> result = new HashSet<>(); if (signatures != null) for (Signature s : signatures) result.add(s.toCharsString()); return result;
    }
    static void cleanupDownload(Context c) {
        long id = prefs(c).getLong("downloadId", -1);
        if (id != -1) c.getSystemService(DownloadManager.class).remove(id);
        try { File f = target(c); if (f.exists()) f.delete(); } catch (Exception ignored) { }
        prefs(c).edit().remove("downloadId").remove("downloaded").apply();
    }
    static void cancel(Context c) { cleanupDownload(c); setState(c,"available",R.string.update_cancelled); }
    private static void backup(Context c) throws Exception {
        File directory = new File(c.getFilesDir(), "update-rollback"); if (!directory.exists() && !directory.mkdirs()) throw new UpdateError(c,R.string.backup_failed);
        try (InputStream in = new FileInputStream(c.getApplicationInfo().sourceDir); OutputStream out = new FileOutputStream(new File(directory,"previous.apk"))) { byte[] b = new byte[32768]; int n; while ((n=in.read(b)) != -1) out.write(b,0,n); }
        JSONObject export = new JSONObject();
        export.put("schemaVersion", 2);
        export.put("versionCode", version(installed(c)));
        export.put("launcherPreferences", preferencesJson(LauncherSettings.get(c)));
        export.put("languageAndScreensaverPreferences", preferencesJson(c.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)));
        export.put("legacyHomePreferences", preferencesJson(c.getSharedPreferences(MainActivity.class.getPackage().getName() + ".HomeEntry", Context.MODE_PRIVATE)));
        try (Writer out = new OutputStreamWriter(new FileOutputStream(new File(directory,"preferences.json")), "UTF-8")) { out.write(export.toString()); }
    }
    private static JSONObject preferencesJson(SharedPreferences preferences) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            Object value = entry.getValue();
            result.put(entry.getKey(), value instanceof Set ? new JSONArray((Set<?>) value) : value);
        }
        return result;
    }
    static void install(Activity activity) {
        if (!"ready".equals(state(activity))) return;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); return;
        }
        Context c = activity.getApplicationContext(); setState(c,"installing",R.string.update_installing);
        WORKER.execute(() -> {
            int id = -1; PackageInstaller installer = c.getPackageManager().getPackageInstaller();
            try {
                File file = target(c); validateApk(c,file); backup(c);
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(c.getPackageName()); params.setSize(file.length());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                id = installer.createSession(params); prefs(c).edit().putInt("sessionId",id).commit();
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    try (InputStream input = new FileInputStream(file); OutputStream output = session.openWrite("base.apk",0,file.length())) {
                        byte[] b = new byte[32768]; int n; while ((n=input.read(b)) != -1) output.write(b,0,n); session.fsync(output);
                    }
                    Intent callback = new Intent(c, UpdateInstallReceiver.class).setAction(c.getPackageName()+".INSTALL_RESULT");
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    PendingIntent pending = PendingIntent.getBroadcast(c,id,callback,flags);
                    session.commit(pending.getIntentSender());
                }
            } catch (Exception e) {
                if (id != -1) try { installer.abandonSession(id); } catch (Exception ignored) { }
                setState(c,"ready",R.string.update_repeat_install);
            }
        });
    }
}

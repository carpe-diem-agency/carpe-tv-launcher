package agency.carpediem.tv.prototype;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()) ||
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-2) != UpdateManager.prefs(context).getLong("downloadId",-1)) return;
        Context app = context.getApplicationContext(); PendingResult pending = goAsync();
        UpdateManager.WORKER.execute(() -> { try { UpdateManager.refreshNow(app); } finally { pending.finish(); } });
    }
}

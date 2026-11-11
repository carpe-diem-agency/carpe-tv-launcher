package agency.carpediem.tv.prototype;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2) != UpdateManager.prefs(c).getInt("sessionId",-1)) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                UpdateManager.setState(c,"confirming",R.string.update_confirm);
                try { c.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                catch (RuntimeException error) { UpdateManager.setState(c,"ready",R.string.update_repeat_install); }
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdateManager.cleanupDownload(c);
            UpdateManager.prefs(c).edit().remove("sessionId").remove("metadata").apply();
            UpdateManager.setState(c,"idle",R.string.update_success);
        } else {
            UpdateManager.prefs(c).edit().remove("sessionId").apply();
            UpdateManager.setState(c,"ready", status == PackageInstaller.STATUS_FAILURE_ABORTED ? R.string.update_install_cancelled : R.string.update_installer_failed);
        }
    }
}

package agency.carpediem.tv.prototype;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int sessionId=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2);
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(UpdateManager.installCallback(c,sessionId,status)) {
            Intent confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null) {
                try {c.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
                catch(RuntimeException error) {UpdateManager.installConfirmationFailed(c,sessionId);}
            } else UpdateManager.installConfirmationFailed(c,sessionId);
        }
    }
}

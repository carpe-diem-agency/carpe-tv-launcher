package agency.carpediem.tv.prototype;

import android.app.ActivityManager;
import android.content.Context;
import android.os.StatFs;

/** Read-only memory/data-partition snapshot. No package scans or filesystem traversal. */
final class DeviceMetrics {
    private DeviceMetrics() { }

    static final class Snapshot {
        final long availableRam, totalRam, freeStorage, totalStorage;

        Snapshot(long availableRam, long totalRam, long freeStorage, long totalStorage) {
            this.totalRam = Math.max(0, totalRam);
            this.availableRam = Math.max(0, Math.min(availableRam, this.totalRam));
            this.totalStorage = Math.max(0, totalStorage);
            this.freeStorage = Math.max(0, Math.min(freeStorage, this.totalStorage));
        }

        long usedRam() { return totalRam - availableRam; }
        long usedStorage() { return totalStorage - freeStorage; }
        double ramUsage() { return totalRam > 0 ? (double) usedRam() / totalRam : 0; }
        double storageUsage() { return totalStorage > 0 ? (double) usedStorage() / totalStorage : 0; }
        boolean ramAvailable() { return totalRam > 0; }
        boolean storageAvailable() { return totalStorage > 0; }
    }

    static Snapshot capture(Context context) {
        long ram = 0, available = 0, storage = 0, free = 0;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(info);
                ram = info.totalMem;
                available = info.availMem;
            }
        } catch (RuntimeException unavailable) { /* Unknown is distinct from a full device. */ }
        try {
            StatFs stats = new StatFs(context.getFilesDir().getAbsolutePath());
            storage = stats.getTotalBytes();
            free = stats.getAvailableBytes();
        } catch (RuntimeException unavailable) { /* UI can hide the unknown bar. */ }
        return new Snapshot(available, ram, free, storage);
    }
}

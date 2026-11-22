package agency.carpediem.tv.prototype;

import java.util.concurrent.Executor;

/** Coalesces package broadcasts and resume requests on the shared catalog worker. */
final class CatalogRefresh {
    interface Scan {
        void run(boolean invalidateArtwork);
    }

    private final Executor worker;
    private final Scan scan;
    private boolean scheduled;
    private boolean requested;
    private boolean invalidateArtwork;
    private boolean closed;

    CatalogRefresh(Executor worker, Scan scan) {
        this.worker = worker;
        this.scan = scan;
    }

    synchronized void request(boolean artworkChanged) {
        if (closed) return;
        requested = true;
        invalidateArtwork |= artworkChanged;
        if (!scheduled) {
            scheduled = true;
            worker.execute(this::run);
        }
    }

    private void run() {
        boolean refreshArtwork;
        synchronized (this) {
            if (closed) return;
            refreshArtwork = invalidateArtwork;
            requested = false;
            invalidateArtwork = false;
        }
        try {
            scan.run(refreshArtwork);
        } finally {
            synchronized (this) {
                if (!closed && requested) worker.execute(this::run);
                else scheduled = false;
            }
        }
    }

    synchronized void close() {
        closed = true;
    }
}

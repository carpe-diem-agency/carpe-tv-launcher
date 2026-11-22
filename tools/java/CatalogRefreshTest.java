package agency.carpediem.tv.prototype;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Run with the JDK to verify catalog scheduling without a live PackageManager. */
public final class CatalogRefreshTest {
    public static void main(String[] args) {
        Queue<Runnable> worker = new ArrayDeque<>();
        List<String> events = new ArrayList<>();
        CatalogRefresh[] coordinator = new CatalogRefresh[1];
        coordinator[0] = new CatalogRefresh(worker::add, invalidateArtwork -> {
            events.add("scan " + invalidateArtwork);
            if (events.size() == 1) {
                for (int i = 0; i < 100; i++) coordinator[0].request(i == 99);
                require(worker.size() == 1, "Refresh requests queued work during the scan");
            }
        });
        CatalogRefresh refresh = coordinator[0];
        refresh.request(false);
        for (int i = 0; i < 100; i++) refresh.request(false);
        require(worker.size() == 1, "Queued refreshes did not merge");
        worker.add(() -> events.add("diagnostics"));
        worker.remove().run();
        require(worker.size() == 2, "Expected diagnostics and a single follow-up scan");
        worker.remove().run();
        worker.remove().run();
        require(events.equals(List.of("scan false", "diagnostics", "scan true")),
                "Follow-up lost artwork invalidation or ran ahead of diagnostics");
        require(worker.isEmpty(), "Extra scan remained queued");

        refresh.request(true);
        refresh.close();
        refresh.request(true);
        worker.remove().run();
        require(worker.isEmpty() && events.size() == 3, "Closed refresh still scanned");

        CatalogRefresh closeDuringScan = new CatalogRefresh(worker::add, ignored -> {
            coordinator[0].request(true);
            coordinator[0].close();
        });
        coordinator[0] = closeDuringScan;
        closeDuringScan.request(false);
        worker.remove().run();
        require(worker.isEmpty(), "Pending request survived close during scan");

        List<Boolean> artworkBatches = new ArrayList<>();
        CatalogRefresh artworkRefresh = new CatalogRefresh(worker::add, artworkBatches::add);
        artworkRefresh.request(true);
        artworkRefresh.request(false);
        worker.remove().run();
        artworkRefresh.request(false);
        worker.remove().run();
        require(artworkBatches.equals(List.of(true, false)),
                "Queued resume lost artwork invalidation or inherited it on the next scan");
        System.out.println("CatalogRefreshTest passed");
    }

    private static void require(boolean passed, String failure) {
        if (!passed) throw new AssertionError(failure);
    }
}

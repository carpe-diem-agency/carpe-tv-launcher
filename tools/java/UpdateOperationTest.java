package agency.carpediem.tv.prototype;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone JDK test of the production coordinator, without Android/network mocks. */
public final class UpdateOperationTest {
    public static void main(String[] args) throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch releaseStale=new CountDownLatch(1),releaseCommit=new CountDownLatch(1);
        try {
            UpdateOperation operation=new UpdateOperation();
            AtomicInteger effects=new AtomicInteger();
            UpdateOperation.Token a=operation.begin();
            CountDownLatch staleReady=new CountDownLatch(2);
            Future<Boolean> success=workers.submit(()->{
                staleReady.countDown();await(releaseStale);
                return operation.commit(a,()->effects.addAndGet(1));
            });
            Future<Boolean> error=workers.submit(()->{
                staleReady.countDown();await(releaseStale);
                return operation.commit(a,()->effects.addAndGet(100)); // error + destructive cleanup
            });
            await(staleReady);
            operation.cancel(()->effects.addAndGet(1000));
            UpdateOperation.Token b=operation.begin();
            releaseStale.countDown();
            require(!success.get(5,TimeUnit.SECONDS)&&!error.get(5,TimeUnit.SECONDS),"Stale result accepted");
            require(effects.get()==1000,"Stale success/error/cleanup ran");
            require(operation.commit(b,()->effects.addAndGet(10)),"Current result rejected");
            require(effects.get()==1010,"Current publication missing");

            CountDownLatch insideCommit=new CountDownLatch(1),cancelAttempt=new CountDownLatch(1);
            Future<Boolean> committed=workers.submit(()->operation.commit(b,()->{
                insideCommit.countDown();awaitUnchecked(releaseCommit);effects.set(20);
            }));
            await(insideCommit);
            Future<?> cancel=workers.submit(()->{
                cancelAttempt.countDown();operation.cancel(()->{
                    require(effects.get()==20,"Cancel interleaved inside accepted publication");effects.set(30);
                });
            });
            await(cancelAttempt);releaseCommit.countDown();
            require(committed.get(5,TimeUnit.SECONDS),"Already accepted publication failed");
            cancel.get(5,TimeUnit.SECONDS);
            require(effects.get()==30,"Cancellation did not follow publication");
            require(!operation.commit(b,()->effects.set(-1)),"Cancelled token accepted");
            require(!operation.commit(null,()->effects.set(-1)),"Null token accepted");

            UpdateOperation.Token c=operation.begin();
            try {operation.cancel(()->{throw new IllegalStateException("cleanup failure");});throw new AssertionError("Missing cleanup exception");}
            catch(IllegalStateException expected) {require("cleanup failure".equals(expected.getMessage()),"Wrong exception");}
            require(!operation.commit(c,()->effects.set(-1)),"Cleanup exception resurrected token");
            UpdateOperation.Token d=operation.begin();
            try {operation.commit(d,()->{throw new IllegalArgumentException("publication failure");});throw new AssertionError("Missing publication exception");}
            catch(IllegalArgumentException expected) {require("publication failure".equals(expected.getMessage()),"Wrong exception");}
            require(operation.commit(d,()->effects.set(40)),"Current error recovery rejected");
            System.out.println("PASS: stale success/error/cleanup, commit/cancel ordering, identity and exception recovery");
        } finally {
            releaseStale.countDown();releaseCommit.countDown();workers.shutdownNow();
            if(!workers.awaitTermination(5,TimeUnit.SECONDS)) throw new AssertionError("Workers did not terminate");
        }
    }
    private static void await(CountDownLatch latch) throws InterruptedException {
        if(!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out");
    }
    private static void awaitUnchecked(CountDownLatch latch) {
        try {await(latch);} catch(InterruptedException error) {Thread.currentThread().interrupt();throw new AssertionError(error);}
    }
    private static void require(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
}

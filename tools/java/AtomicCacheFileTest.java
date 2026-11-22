package agency.carpediem.tv.prototype;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Standalone Java 17 test; pass an existing SSD-backed directory as the sole argument. */
public final class AtomicCacheFileTest {
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("Supply the SSD-backed fixture parent directory");
        Path directory=Files.createTempDirectory(Path.of(args[0]),"atomic-cache-test-");
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch aPaused=new CountDownLatch(1),releaseA=new CountDownLatch(1);
        boolean terminated=false;
        try {
            Path target=directory.resolve("wallpaper.png");
            byte[] old=bytes("old-known-good"),a=bytes("writer-A-complete-payload"),b=bytes("writer-B-complete-payload");
            Files.write(target,old);
            Path orphan=directory.resolve("carpe-atomic-wallpaper.png-old-process.tmp");
            Path unrelated=directory.resolve("unrelated.tmp");
            Files.write(orphan,bytes("abandoned partial"));Files.write(unrelated,bytes("unrelated keep"));
            Future<Boolean> first=workers.submit(()->AtomicCacheFile.write(target.toFile(),out->{
                out.write(a,0,7);aPaused.countDown();
                try {if(!releaseA.await(5,TimeUnit.SECONDS)) throw new IOException("Writer A barrier timed out");}
                catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IOException(error);}
                out.write(a,7,a.length-7);return true;
            }));
            require(aPaused.await(5,TimeUnit.SECONDS),"Writer A never reached its partial-write gate");
            require(!Files.exists(orphan),"First writer did not clean its previous-process orphan");
            require(Files.exists(unrelated),"Startup cleanup removed an unrelated temporary file");
            same(target,old,"Partial A must not replace known-good target");
            java.io.OutputStream[] publishedStream={null};
            Future<Boolean> second=workers.submit(()->AtomicCacheFile.write(target.toFile(),out->{publishedStream[0]=out;out.write(b);return true;}));
            require(second.get(5,TimeUnit.SECONDS),"Concurrent writer B did not publish");
            same(target,b,"B publication must be complete while A is still partial");
            try {publishedStream[0].write(1);throw new AssertionError("Published stream was not closed");}
            catch(IOException expectedClosed) { }
            same(target,b,"A closed writer must not mutate published bytes");
            releaseA.countDown();require(first.get(5,TimeUnit.SECONDS),"Writer A did not publish");
            same(target,a,"Later A publication must contain only complete A");
            noTemps(directory,target);
            require(!AtomicCacheFile.write(target.toFile(),out->{out.write(bytes("partial-refused"));return false;}),"False callback must reject publication");
            same(target,a,"Refused write replaced known-good target");noTemps(directory,target);
            try {
                AtomicCacheFile.write(target.toFile(),out->{out.write(bytes("partial-error"));throw new IOException("expected callback failure");});
                throw new AssertionError("Expected callback IOException");
            } catch(IOException expected) {require("expected callback failure".equals(expected.getMessage()),"Wrong callback failure");}
            same(target,a,"Throwing callback replaced known-good target");noTemps(directory,target);
            try {
                AtomicCacheFile.write(target.toFile(),out->{out.write(1);throw new IllegalStateException("expected runtime failure");});
                throw new AssertionError("Expected runtime exception");
            } catch(IllegalStateException expected) { }
            same(target,a,"Runtime failure replaced known-good target");noTemps(directory,target);
            same(unrelated,bytes("unrelated keep"),"Writers changed the unrelated temporary file");
            Path occupied=directory.resolve("occupied-target"),marker=occupied.resolve("keep.marker");
            Files.createDirectory(occupied);Files.write(marker,bytes("known-good directory marker"));
            try {
                AtomicCacheFile.write(occupied.toFile(),out->{out.write(bytes("complete but cannot publish"));return true;});
                throw new AssertionError("Expected publication failure for a nonempty target directory");
            } catch(IOException expectedPublicationFailure) { }
            require(Files.isDirectory(occupied),"Publication failure replaced the occupied directory");
            same(marker,bytes("known-good directory marker"),"Publication failure changed the target marker");
            same(target,a,"Publication failure changed the unrelated good cache target");
            try(java.util.stream.Stream<Path> files=Files.list(directory)) {
                require(files.noneMatch(file->file.getFileName().toString().startsWith("carpe-atomic-")
                        &&file.getFileName().toString().endsWith(".tmp")),"Publication failure left its temporary file");
            }
            Files.delete(marker);Files.delete(occupied);noTemps(directory,target);
            System.out.println("PASS: gated concurrent publication, rejected/throwing callbacks, failed atomic move, complete targets and temporary cleanup");
        } finally {
            releaseA.countDown();workers.shutdownNow();terminated=workers.awaitTermination(5,TimeUnit.SECONDS);
            if(terminated) {
                try(java.util.stream.Stream<Path> files=Files.list(directory)) {for(Path file:files.toList()) Files.delete(file);}
                Files.delete(directory);
            }
            require(terminated,"Worker remains active; fixture retained for inspection");
        }
    }
    private static byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
    private static void same(Path file,byte[] expected,String message) throws IOException {require(Arrays.equals(Files.readAllBytes(file),expected),message);}
    private static void noTemps(Path directory,Path target) throws IOException {
        try(java.util.stream.Stream<Path> files=Files.list(directory)) {require(files.allMatch(file->file.equals(target)||file.getFileName().toString().equals("unrelated.tmp")),"Writer left temporary files behind");}
    }
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}
}

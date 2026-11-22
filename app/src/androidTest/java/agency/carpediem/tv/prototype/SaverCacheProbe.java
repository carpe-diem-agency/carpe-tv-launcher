package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Simulates a nearly full filesystem without filling the real device or touching other caches. */
final class SaverCacheProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        if(!test.getTargetContext().getPackageName().endsWith(".publicpreview"))
            throw new IllegalStateException("Cache policy probe requires the isolated preview package");
        if(SaverCache.diskLimit(80*SaverCache.MIB)!=40*SaverCache.MIB || SaverCache.diskLimit(8000*SaverCache.MIB)!=500*SaverCache.MIB) throw new AssertionError("Cache must respect half disk and 500 MiB cap");
        SaverMedia caption=new SaverMedia("https://example.com/x","File:Beautiful_landscape.jpg","Author","","","image");
        if(!caption.displayTitle().equals("Beautiful landscape")) throw new AssertionError("Clean caption");

        File root=new File(test.getTargetContext().getCacheDir(),"screensaver-policy-test-"+Long.toHexString(System.nanoTime()));
        require(root.mkdir(),"Create unique cache policy fixture");
        AtomicBoolean safeToDelete=new AtomicBoolean(true);
        Throwable primaryFailure=null;
        try {
            AtomicLong free=new AtomicLong(1024*1024);SaverCache cache=new SaverCache(root,128*1024,32*1024,free::get);
            SaverMedia a=item("a"),b=item("b"),c=item("c"),d=item("d");
            require(put(cache,a,32768),"First file");Thread.sleep(5);
            require(put(cache,b,32768),"Second file");Thread.sleep(5);
            require(put(cache,c,32768),"Third file");
            File pinned=cache.acquire(a);require(pinned!=null,"Pin active file");
            require(put(cache,d,32768),"Evict least recently used unpinned item");
            require(cache.file(a).isFile()&&!cache.file(b).exists()&&cache.file(d).isFile(),"Pinned survives, oldest unpinned removed");
            require(cache.bytes()<=128*1024,"Cache budget respected");
            free.set(32000);require(!put(cache,item("low"),1024),"Insufficient reserve refuses download");
            require(cache.file(a).isFile(),"Low storage preserves playing media");
            cache.release(a);free.set(1024*1024);cache.clear();
            SaverMedia broken=item("broken");
            require(!cache.store(broken,new ByteArrayInputStream(new byte[10]),20,100,()->true,f->true),"Truncated body rejected");
            require(!cache.store(broken,new ByteArrayInputStream(new byte[11]),-1,10,()->true,f->true),"Unknown-length response capped");
            require(!cache.store(broken,new ByteArrayInputStream(new byte[10]),10,100,()->false,f->true),"Cancelled transfer refused");
            require(!cache.store(broken,new ByteArrayInputStream(new byte[10]),10,100,()->true,f->false),"Invalid media not published");
            for(File file:root.listFiles()) require(!file.getName().endsWith(".part")&&!file.getName().endsWith(".tmp"),"No partial files remain");
            File orphan=new File(root,broken.id+".part");try(FileOutputStream out=new FileOutputStream(orphan)){out.write(1);}
            new SaverCache(root,128*1024,32*1024,free::get);require(!orphan.exists(),"Crash leftovers cleaned");
            for(int i=0;i<25;i++) require(put(cache,item("count"+i),64),"Small item download");
            require(cache.entries().size()<=24,"Bound the file count as well as bytes");
            require(SaverCatalog.allowed("https://images-assets.nasa.gov/image/example.jpg"),"Known HTTPS host accepted");
            require(!SaverCatalog.allowed("http://images-assets.nasa.gov/a")&&!SaverCatalog.allowed("https://127.0.0.1/a")
                    &&!SaverCatalog.allowed("https://images-assets.nasa.gov.evil.test/a")&&!SaverCatalog.allowed("https://user:pass@images-assets.nasa.gov/a"),"Untrusted hosts, credentials and HTTP rejected");
            verifyLiveRecovery(new File(root,"live-recovery"));
            verifyMaintenance(new File(root,"maintenance"));
            verifyPinnedRecovery(new File(root,"pinned-recovery"));
            verifyActiveTransfer(new File(root,"active-transfer"),safeToDelete);
            result.putString("cache_recovery","PASS: live missing/corrupt/zero-byte/mismatched metadata recovery, invalid-pair maintenance, pins and active transfer preservation");
            result.putString("cache_policy","PASS: quota/LRU/pins/low-space refusal/truncation/size cap/cancel/invalid media/partial cleanup/HTTPS allowlist");
        } catch(Exception|Error failure) {
            primaryFailure=failure;throw failure;
        } finally {
            try {
                require(safeToDelete.get(),"Scoped fixture retained because its transfer worker did not terminate");
                deleteFixture(root);
            } catch(Exception|Error cleanup) {
                if(primaryFailure!=null) primaryFailure.addSuppressed(cleanup);
                else if(cleanup instanceof Error) throw (Error)cleanup;
                else throw (Exception)cleanup;
            }
        }
    }
    private interface Damage { void apply(SaverCache cache,SaverMedia media) throws Exception; }
    private static SaverCache fixture(File root) {
        return new SaverCache(root,256*1024,0,()->1024*1024);
    }
    private static File metadata(SaverCache cache,SaverMedia media) {return new File(cache.root,media.id+".json");}
    private static void write(File file,String text) throws Exception {
        try(FileOutputStream out=new FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static void verifyLiveRecovery(File root) throws Exception {
        SaverCache cache=fixture(root);
        recover(cache,item("missing-json"),(c,m)->require(metadata(c,m).delete(),"Remove fixture metadata"));
        recover(cache,item("corrupt-json"),(c,m)->write(metadata(c,m),"{broken"));
        recover(cache,item("zero-media"),(c,m)->{try(FileOutputStream out=new FileOutputStream(c.file(m))) {out.getFD().sync();}});
        recover(cache,item("wrong-id"),(c,m)->write(metadata(c,m),item("different-url").json().toString()));
    }
    private static void recover(SaverCache cache,SaverMedia media,Damage damage) throws Exception {
        require(put(cache,media,1024),"Seed live recovery fixture");
        damage.apply(cache,media);
        // No new cache instance and no enumeration/maintenance sweep before retry.
        require(!cache.hasComplete(media),"Damaged pair cannot be reported complete");
        require(cache.acquire(media)==null,"Damaged pair cannot be acquired for playback");
        require(put(cache,media,2048),"Same instance must replace damaged pair");
        require(cache.hasComplete(media)&&cache.file(media).length()==2048,"Retry publishes a complete replacement");
        require(cache.entries().size()==1&&cache.entries().get(0).id.equals(media.id),"Replacement has one matching metadata entry");
        File playing=cache.acquire(media);require(playing!=null,"Recovered item can be acquired");cache.release(media);
        cache.clear();
        require(!cache.file(media).exists()&&!metadata(cache,media).exists(),"Recovered fixture clears cleanly");
    }
    private static void verifyMaintenance(File root) throws Exception {
        SaverCache cache=fixture(root);
        SaverMedia missing=item("clear-missing"),corrupt=item("clear-corrupt"),metadataOnly=item("clear-metadata-only");
        require(put(cache,missing,4096)&&put(cache,corrupt,4096),"Seed invalid maintenance pairs");
        require(metadata(cache,missing).delete(),"Create media-only pair");
        write(metadata(cache,corrupt),"not-json");
        write(metadata(cache,metadataOnly),metadataOnly.json().toString());
        File unrelated=new File(root,"unrelated-note.txt");write(unrelated,"keep");
        cache.clear();
        assertAbsent(cache,missing);assertAbsent(cache,corrupt);assertAbsent(cache,metadataOnly);
        require(unrelated.isFile(),"Cache maintenance only removes its recognized namespace");
        SaverMedia room=item("room-invalid");require(put(cache,room,4096),"Seed makeRoom invalid pair");
        write(metadata(cache,room),item("room-other-id").json().toString());
        long before=cache.bytes();
        require(cache.makeRoom(0),"Maintenance succeeds with sufficient free space");
        assertAbsent(cache,room);
        require(cache.bytes()<before,"makeRoom reclaims invalid-pair bytes even before quota pressure");
        require(unrelated.isFile(),"makeRoom preserves unrelated fixture file");
    }
    private static void verifyPinnedRecovery(File root) throws Exception {
        SaverCache cache=fixture(root);SaverMedia media=item("pinned-corrupt");
        require(put(cache,media,4096),"Seed pinned item");
        File first=cache.acquire(media),second=cache.acquire(media);
        require(first!=null&&second!=null,"Acquire two independent playback pins");
        write(metadata(cache,media),"{broken");
        require(!cache.hasComplete(media)&&cache.acquire(media)==null,"Corrupt attribution blocks new playback");
        cache.entries();cache.clear();cache.makeRoom(1024*1024);
        require(first.isFile()&&first.length()==4096&&metadata(cache,media).isFile(),"Maintenance preserves existing pinned playback pair");
        require(!put(cache,media,2048),"Repair cannot overwrite an actively pinned item");
        cache.release(media);cache.clear();
        require(first.isFile(),"One remaining pin still protects playback");
        cache.release(media);cache.clear();assertAbsent(cache,media);
        require(put(cache,media,2048)&&cache.hasComplete(media),"Released damaged item can be repaired");
    }
    private static void verifyActiveTransfer(File root,AtomicBoolean safeToDelete) throws Exception {
        SaverCache cache=fixture(root);SaverMedia media=item("in-flight");
        CountDownLatch paused=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();
        safeToDelete.set(false);
        Future<Boolean> stored=null;
        Throwable primaryFailure=null;
        try {
            InputStream input=new InputStream() {
                int position;
                @Override public int read() throws IOException {
                    byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;
                }
                @Override public int read(byte[] buffer,int offset,int length) throws IOException {
                    if(length==0) return 0;
                    if(position==2048) {
                        paused.countDown();
                        try {if(!release.await(15,TimeUnit.SECONDS)) throw new IOException("Active transfer fixture timed out");}
                        catch(InterruptedException error) {Thread.currentThread().interrupt();throw new IOException(error);}
                    }
                    if(position==4096) return -1;
                    int count=Math.min(length,(position<2048?2048:4096)-position);
                    java.util.Arrays.fill(buffer,offset,offset+count,(byte)1);position+=count;return count;
                }
            };
            stored=worker.submit(()->cache.store(media,input,4096,65536,()->true,f->f.length()==4096));
            require(paused.await(10,TimeUnit.SECONDS),"Pause transfer after writing its first chunk");
            File part=new File(root,media.id+".part");
            require(part.isFile()&&part.length()==2048,"In-flight partial fixture is present");
            cache.entries();cache.clear();cache.makeRoom(1024*1024);
            require(part.isFile()&&part.length()==2048,"Live maintenance preserves active transfer bytes");
            require(!cache.hasComplete(media)&&cache.acquire(media)==null,"Partial transfer is never playable");
            release.countDown();
            require(stored.get(10,TimeUnit.SECONDS),"Paused transfer finishes after maintenance");
            require(cache.hasComplete(media)&&cache.file(media).length()==4096,"Transfer publishes complete pair");
            require(!part.exists()&&!new File(root,media.id+".json.tmp").exists(),"Published transfer leaves no temporary files");
        } catch(Exception|Error failure) {
            primaryFailure=failure;throw failure;
        } finally {
            release.countDown();
            Throwable cleanupFailure=null;
            try {if(stored!=null) stored.get(10,TimeUnit.SECONDS);}
            catch(Exception|Error failure) {cleanupFailure=failure;}
            worker.shutdownNow();
            try {
                boolean terminated=worker.awaitTermination(10,TimeUnit.SECONDS);
                safeToDelete.set(terminated);
                require(terminated,"Transfer fixture worker terminates before deletion");
            }
            catch(Exception|Error failure) {if(cleanupFailure==null) cleanupFailure=failure;else cleanupFailure.addSuppressed(failure);}
            if(cleanupFailure!=null) {
                if(primaryFailure!=null) primaryFailure.addSuppressed(cleanupFailure);
                else if(cleanupFailure instanceof Error) throw (Error)cleanupFailure;
                else throw (Exception)cleanupFailure;
            }
        }
    }
    private static void assertAbsent(SaverCache cache,SaverMedia media) {
        require(!cache.file(media).exists()&&!metadata(cache,media).exists(),"Invalid pair should be removed by live maintenance");
    }
    private static void deleteFixture(File file) {
        File[] children=file.listFiles();if(children!=null) for(File child:children) deleteFixture(child);
        require(!file.exists()||file.delete(),"Remove only the scoped policy-test fixture");
    }
    private static SaverMedia item(String id) {return new SaverMedia("https://images-assets.nasa.gov/"+id,"Test","NASA","https://images.nasa.gov/","https://www.nasa.gov/","image");}
    private static boolean put(SaverCache cache,SaverMedia item,int size) throws Exception {
        return cache.store(item,new ByteArrayInputStream(new byte[size]),size,65536,()->true,f->true);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}

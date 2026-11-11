package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

/** Simulates a nearly full filesystem without filling the real device or touching other caches. */
final class SaverCacheProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {
        if(SaverCache.diskLimit(80*SaverCache.MIB)!=40*SaverCache.MIB || SaverCache.diskLimit(8000*SaverCache.MIB)!=500*SaverCache.MIB) throw new AssertionError("Cache must respect half disk and 500 MiB cap");
        SaverMedia caption=new SaverMedia("https://example.com/x","File:Beautiful_landscape.jpg","Author","","","image");
        if(!caption.displayTitle().equals("Beautiful landscape")) throw new AssertionError("Clean caption");

        File root=new File(test.getTargetContext().getCacheDir(),"screensaver-policy-test");root.mkdirs();
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
            result.putString("cache_policy","PASS: quota/LRU/pins/low-space refusal/truncation/size cap/cancel/invalid media/partial cleanup/HTTPS allowlist");
        } finally {File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();root.delete();}
    }
    private static SaverMedia item(String id) {return new SaverMedia("https://images-assets.nasa.gov/"+id,"Test","NASA","https://images.nasa.gov/","https://www.nasa.gov/","image");}
    private static boolean put(SaverCache cache,SaverMedia item,int size) throws Exception {
        return cache.store(item,new ByteArrayInputStream(new byte[size]),size,65536,()->true,f->true);
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}

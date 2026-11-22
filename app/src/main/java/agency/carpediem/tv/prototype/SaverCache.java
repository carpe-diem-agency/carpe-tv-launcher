package agency.carpediem.tv.prototype;

import android.content.Context;
import android.os.StatFs;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Private, bounded cache. Publishes only complete validated files; pins protect active playback. */
final class SaverCache {
    static final long MIB=1024L*1024, LIMIT=500*MIB, RESERVE=1024*MIB;
    private static final int MAX_METADATA=16384;
    private static final java.util.regex.Pattern ENTRY_NAME=
            java.util.regex.Pattern.compile("([a-f0-9]{64})\\.(media|json)");
    private static SaverCache instance;
    static synchronized SaverCache get(Context c) {
        if(instance==null) {
            File root=new File(c.getCacheDir(),"screensaver-media-v1"); root.mkdirs();
            instance=new SaverCache(root,diskLimit(new StatFs(root.getAbsolutePath()).getTotalBytes()),reserveMb(c)*MIB,()->new StatFs(root.getAbsolutePath()).getAvailableBytes());
        }
        instance.root.mkdirs();instance.limit=diskLimit(new StatFs(instance.root.getAbsolutePath()).getTotalBytes());instance.reserve=reserveMb(c)*MIB;return instance;
    }
    static int reserveMb(Context c) {return 1024;}
    final File root;
    static long diskLimit(long totalBytes) { return Math.min(LIMIT,Math.max(0,totalBytes/2)); }
    private volatile long limit;
    private volatile long reserve;
    private final LongSupplier free;
    private final Map<String,Integer> pinned=new HashMap<>();
    SaverCache(File root,long limit,long reserve,LongSupplier free) { this.root=root;this.limit=limit;this.reserve=reserve;this.free=free;root.mkdirs();
        File[] stale=root.listFiles();if(stale!=null) for(File f:stale) {
            String name=f.getName();
            if(name.matches("[a-f0-9]{64}\\.part")||name.matches("[a-f0-9]{64}\\.json\\.tmp")) f.delete();
            if(name.matches("[a-f0-9]{64}\\.media")&&!new File(root,name.replace(".media",".json")).isFile()) f.delete();
        }
    }
    File file(SaverMedia item) { return new File(root,item.id+".media"); }
    private File metadata(SaverMedia item) { return new File(root,item.id+".json"); }
    synchronized long bytes() { long sum=0; File[] files=root.listFiles(); if(files!=null) for(File f:files) if(f.isFile()) sum+=f.length(); return sum; }
    long freeBytes() { try { return Math.max(0,free.getAsLong()); } catch(RuntimeException error) { return 0; } }
    /** Per-entry check; no directory scan or media decode on playback/cache lookup. */
    synchronized boolean hasComplete(SaverMedia item) {
        SaverMedia stored=completeEntry(item.id);
        return stored!=null&&stored.kind.equals(item.kind);
    }
    private SaverMedia completeEntry(String id) {
        File media=new File(root,id+".media"), metadata=new File(root,id+".json");
        if(!media.isFile()||media.length()<=0||!metadata.isFile()
                ||metadata.length()<=0||metadata.length()>=MAX_METADATA) return null;
        try {
            SaverMedia item=SaverMedia.parse(new JSONObject(read(metadata,MAX_METADATA-1)));
            return id.equals(item.id)?item:null;
        } catch(Exception invalid) {return null;}
    }
    private static final class CacheScan {
        final List<SaverMedia> items=new ArrayList<>();
        int removed;
    }
    /** Recover final pairs only. Live transfers own .part/.json.tmp until publication. */
    private CacheScan scanEntries() {
        CacheScan scan=new CacheScan();Set<String> ids=new HashSet<>();
        File[] files=root.listFiles();
        if(files!=null) for(File file:files) {
            java.util.regex.Matcher name=ENTRY_NAME.matcher(file.getName());
            if(name.matches()&&file.isFile()) ids.add(name.group(1));
        }
        for(String id:ids) {
            SaverMedia item=completeEntry(id);
            if(item!=null) scan.items.add(item);
            else if(removePair(id)) scan.removed++;
        }
        scan.items.sort(Comparator.comparingLong(item->file(item).lastModified()));
        return scan;
    }
    synchronized List<SaverMedia> entries() {return scanEntries().items;}
    synchronized File acquire(SaverMedia item) {
        if(!hasComplete(item)) return null;
        File f=file(item);
        pinned.put(item.id,pinned.getOrDefault(item.id,0)+1);f.setLastModified(System.currentTimeMillis());return f;
    }
    synchronized void release(SaverMedia item) { if(item!=null) {int count=pinned.getOrDefault(item.id,0);if(count<=1) pinned.remove(item.id);else pinned.put(item.id,count-1); } }
    synchronized void discardAfterPlaybackError(SaverMedia item) {
        if (item == null) return;
        release(item);
        remove(item); // Other playback owners keep their pin until they finish.
    }
    synchronized int clear() {
        CacheScan scan=scanEntries();int deleted=scan.removed;
        for(SaverMedia item:scan.items) if(remove(item)) deleted++;
        return deleted;
    }
    private boolean removePair(String id) {
        if(pinned.containsKey(id)) return false;
        File media=new File(root,id+".media"), metadata=new File(root,id+".json");
        // Never recursively delete directories or files outside this pair namespace.
        // Preserve attribution if a media deletion fails, so the pair remains usable.
        boolean removed=media.isFile();
        if(media.exists()&&(!removed||!media.delete())) return false;
        if(metadata.isFile()) removed=metadata.delete()||removed;
        return removed;
    }
    private boolean remove(SaverMedia item) {return removePair(item.id);}
    synchronized boolean makeRoom(long incoming) {
        List<SaverMedia> items=entries(); String keep=items.isEmpty()?"":items.get(items.size()-1).id;
        for(SaverMedia item:items) {
            if(bytes()+incoming<=limit && freeBytes()>=reserve+incoming) return true;
            if(!item.id.equals(keep)&&!pinned.containsKey(item.id)) remove(item);
        }
        return bytes()+incoming<=limit && freeBytes()>=reserve+incoming;
    }
    private synchronized boolean entrySlot() {
        List<SaverMedia> items=entries();String keep=items.isEmpty()?"":items.get(items.size()-1).id;
        int count=items.size();
        for(SaverMedia item:items) {
            if(count<24) return true;
            if(!item.id.equals(keep)&&!pinned.containsKey(item.id)&&remove(item)) count--;
        }
        return count<24;
    }
    interface Validator { boolean valid(File file) throws Exception; }
    boolean store(SaverMedia item,InputStream input,long declared,long maxBytes,BooleanSupplier active,Validator validator) throws Exception {
        if(declared>maxBytes || declared==0 || !active.getAsBoolean()) return false;
        byte[] attribution=item.json().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(attribution.length>=MAX_METADATA) return false;
        synchronized(this) {
            if(hasComplete(item)||pinned.containsKey(item.id)) return false;
            removePair(item.id);
        }
        long reservation=declared>0?declared:Math.min(maxBytes,MIB);
        if(!makeRoom(reservation+MAX_METADATA)||!entrySlot()) return false;
        File part=new File(root,item.id+".part"), metaPart=new File(root,item.id+".json.tmp");
        long count=0,checked=0;
        try {
            try(FileOutputStream output=new FileOutputStream(part)) {
                byte[] buffer=new byte[65536];int n;
                while((n=input.read(buffer))!=-1) {
                    if(!active.getAsBoolean()||count+n>maxBytes) return false;
                    if(count==0||count-checked>=MIB) {
                        long remaining=declared>0?Math.max(n,declared-count):Math.min(maxBytes-count,MIB);
                        if(!makeRoom(remaining+MAX_METADATA)) return false;
                        checked=count;
                    }
                    output.write(buffer,0,n);count+=n;
                }
                output.getFD().sync();
            }
            if(count==0||(declared>0&&count!=declared)||!active.getAsBoolean()||!validator.valid(part)) return false;
            synchronized(this) {
                if(!active.getAsBoolean()||hasComplete(item)||pinned.containsKey(item.id)||!makeRoom(0)) return false;
                try(FileOutputStream out=new FileOutputStream(metaPart)) {out.write(attribution);out.getFD().sync();}
                if(!part.renameTo(file(item))) return false;
                if(!metaPart.renameTo(metadata(item))) {file(item).delete();return false;}
                file(item).setLastModified(System.currentTimeMillis());
            }
            return true;
        } finally {part.delete();metaPart.delete();}
    }
    static String read(File file,int max) throws IOException {
        try(InputStream in=new FileInputStream(file)) {return new String(readBytes(in,max),java.nio.charset.StandardCharsets.UTF_8);}
    }
    static byte[] readBytes(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1) {if(out.size()+n>max) throw new IOException("Response too large");out.write(b,0,n);}return out.toByteArray();
    }
}

package agency.carpediem.tv.prototype;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Same-directory atomic publication; each writer owns and cleans only its own temporary file. */
final class AtomicCacheFile {
    private static final String PREFIX="carpe-atomic-";
    private static final java.util.Set<String> preparedDirectories=new java.util.HashSet<>();
    private static final int MAX_PREPARED_DIRECTORIES=16;
    private AtomicCacheFile() { }
    /** This app owns its private cache directory in one process (manifest has no process overrides). */
    private static synchronized void prepare(File directory) throws IOException {
        String key=directory.getCanonicalPath();
        if(preparedDirectories.contains(key)) return;
        // Never evict a prepared directory: cleaning it again could delete a live
        // writer. Production uses one directory; extra callers skip stale cleanup.
        if(preparedDirectories.size()>=MAX_PREPARED_DIRECTORIES) return;
        File[] files=directory.listFiles();
        if(files!=null) for(File file:files)
            if(file.isFile()&&file.getName().startsWith(PREFIX)&&file.getName().endsWith(".tmp"))
                Files.deleteIfExists(file.toPath());
        preparedDirectories.add(key);
    }
    interface Writer {boolean write(OutputStream output) throws IOException;}
    static boolean write(File target,Writer writer) throws IOException {
        File destination=target.getAbsoluteFile();
        prepare(destination.getParentFile());
        File temporary=File.createTempFile(PREFIX+destination.getName()+"-",".tmp",destination.getParentFile());
        try {
            try(FileOutputStream output=new FileOutputStream(temporary)) {
                if(!writer.write(output)) return false;
                output.flush();output.getFD().sync();
            }
            // Closing first prevents a concurrent writer from modifying an already
            // published target. Do not fall back to a non-atomic copy/delete path.
            try {
                Files.move(temporary.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch(AtomicMoveNotSupportedException unsupported) {return false;}
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}

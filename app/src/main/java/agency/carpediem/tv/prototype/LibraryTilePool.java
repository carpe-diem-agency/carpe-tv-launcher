package agency.carpediem.tv.prototype;

import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** UI-thread-only, per-grid pool. Detached cards cannot retain old rows or sibling cards. */
final class LibraryTilePool {
    static final int MAX_COUNT=32;
    static final long MAX_BYTES=8L*1024*1024;
    private final int maxCount;
    private final long maxBytes;
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private long bytes;
    private static final class Entry {
        final LinearLayout tile;final long bytes;
        Entry(LinearLayout tile,long bytes) {this.tile=tile;this.bytes=bytes;}
    }
    LibraryTilePool(int maxCount,long maxBytes) {this.maxCount=Math.max(0,maxCount);this.maxBytes=Math.max(0,maxBytes);}
    int size() {return entries.size();}
    long bytes() {return bytes;}
    void put(String pkg,LinearLayout tile) {
        if(tile.getParent() instanceof ViewGroup) ((ViewGroup)tile.getParent()).removeView(tile);
        Entry previous=entries.remove(pkg);
        if(previous!=null) {bytes-=previous.bytes;if(previous.tile!=tile) release(previous.tile);}
        ImageView icon=tile.findViewById(R.id.app_icon);Drawable image=icon!=null?icon.getDrawable():null;
        android.graphics.Bitmap bitmap=image instanceof BitmapDrawable?((BitmapDrawable)image).getBitmap():null;
        long cost=bitmap!=null?bitmap.getAllocationByteCount():0;
        if(maxCount==0||cost>maxBytes) {release(tile);return;}
        entries.put(pkg,new Entry(tile,cost));bytes+=cost;
        Iterator<Entry> oldest=entries.values().iterator();
        while((entries.size()>maxCount||bytes>maxBytes)&&oldest.hasNext()) {
            Entry entry=oldest.next();oldest.remove();bytes-=entry.bytes;release(entry.tile);
        }
    }
    LinearLayout take(String pkg,String label) {
        Entry entry=entries.remove(pkg);if(entry==null) return null;
        bytes-=entry.bytes;
        if(!label.equals(label(entry.tile))) {release(entry.tile);return null;}
        return entry.tile;
    }
    void retain(Map<String,String> catalogLabels) {
        Iterator<Map.Entry<String,Entry>> iterator=entries.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<String,Entry> item=iterator.next();
            if(!label(item.getValue().tile).equals(catalogLabels.get(item.getKey()))) {
                Entry entry=item.getValue();iterator.remove();bytes-=entry.bytes;release(entry.tile);
            }
        }
    }
    void clear() {for(Entry entry:entries.values()) release(entry.tile);entries.clear();bytes=0;}
    private static String label(LinearLayout tile) {
        TextView name=tile.findViewById(R.id.app_name);return name!=null?name.getText().toString():"";
    }
    private static void release(LinearLayout tile) {
        tile.animate().cancel();
        android.view.View artwork=tile.findViewById(R.id.app_artwork);
        if(artwork!=null) artwork.animate().cancel();
        android.view.View ring=tile.findViewById(R.id.focus_outline);if(ring!=null) ring.animate().cancel();
        ImageView icon=tile.findViewById(R.id.app_icon);if(icon!=null) icon.setImageDrawable(null);
        // Bitmaps can also belong to the shared artwork cache; never recycle them.
    }
}

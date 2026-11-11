package agency.carpediem.tv.prototype;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/** Fixed, offline wallpaper set. Only thumbnails are decoded by the gallery. */
final class WallpaperCatalog {
    static final class Entry {
        final String key;
        final int viewId, title, photo, thumbnail;
        Entry(String key, int viewId, int title, int photo, int thumbnail) {
            this.key=key; this.viewId=viewId; this.title=title; this.photo=photo; this.thumbnail=thumbnail;
        }
    }
    static List<Entry> entries(Context context) {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("alpine",R.id.wallpaper_alpine,R.string.wallpaper_alpine,R.drawable.wallpaper_alpine,R.drawable.wallpaper_alpine_thumb));
        entries.add(new Entry("aurora",R.id.wallpaper_aurora,R.string.wallpaper_aurora,R.drawable.wallpaper_aurora,R.drawable.wallpaper_aurora_thumb));
        entries.add(new Entry("como",R.id.wallpaper_como,R.string.wallpaper_como,R.drawable.wallpaper_como,R.drawable.wallpaper_como_thumb));
        entries.add(new Entry("blue",R.id.wallpaper_blue,R.string.wallpaper_blue,0,R.drawable.blue_wallpaper));
        entries.add(new Entry("blue_motion",R.id.wallpaper_blue_motion,R.string.wallpaper_blue_motion,0,R.drawable.blue_wallpaper));
        entries.add(new Entry("plain",R.id.wallpaper_plain,R.string.wallpaper_plain,0,0));
        return entries;
    }
    static Entry find(Context context,String key) {
        for (Entry entry:entries(context)) if (entry.key.equals(key)) return entry;
        for (Entry entry:entries(context)) if (entry.key.equals("alpine")) return entry;
        throw new IllegalStateException("Missing default wallpaper");
    }
}

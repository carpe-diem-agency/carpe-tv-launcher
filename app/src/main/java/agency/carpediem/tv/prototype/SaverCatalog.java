package agency.carpediem.tv.prototype;

import android.content.Context;
import android.text.Html;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Small public catalogs, no account/key. Rights are filtered per Commons item, not assumed globally. */
final class SaverCatalog {
    private static final Set<String> HOSTS=new HashSet<>(Arrays.asList("commons.wikimedia.org","upload.wikimedia.org","thumb.wikimedia.org","images-api.nasa.gov","images-assets.nasa.gov","svs.gsfc.nasa.gov"));
    static boolean allowed(String address) {
        try {URL u=new URL(address);return u.getProtocol().equals("https")&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&HOSTS.contains(u.getHost().toLowerCase(Locale.ROOT));}
        catch(Exception error) {return false;}
    }
    static HttpURLConnection open(String address,BooleanSupplier active) throws IOException {
        for(int redirects=0;redirects<5;redirects++) {
            if(!active.getAsBoolean()) throw new InterruptedIOException();
            if(!allowed(address)) throw new IOException("Untrusted media host");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
            c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","CarpeTV/0.2.6 (https://github.com/carpe-diem-agency/carpe-tv-launcher)");
            c.setRequestProperty("Accept-Encoding","identity");
            try {
                int code=c.getResponseCode();if(!active.getAsBoolean()) throw new InterruptedIOException();
                if(code>=300&&code<400) {String location=c.getHeaderField("Location");if(location==null) throw new IOException("Missing redirect");address=new URL(c.getURL(),location).toString();c.disconnect();continue;}
                if(code!=200) throw new IOException("Media HTTP "+code);
                return c;
            } catch(IOException error) {c.disconnect();throw error;}
        }
        throw new IOException("Too many redirects");
    }
    private static String get(String url,BooleanSupplier active) throws Exception {
        if(!active.getAsBoolean()) throw new InterruptedIOException();
        HttpURLConnection c=open(url,active);
        try(InputStream in=c.getInputStream()) {
            if(c.getContentLengthLong()>2*SaverCache.MIB) throw new IOException("Catalog too large");
            ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while(active.getAsBoolean()&&(count=in.read(buffer))!=-1) {
                if(output.size()+count>2*SaverCache.MIB) throw new IOException("Catalog too large");output.write(buffer,0,count);
            }
            if(!active.getAsBoolean()) throw new InterruptedIOException();return output.toString("UTF-8");
        } finally {c.disconnect();}
    }
    static List<SaverMedia> load(Context c,BooleanSupplier active) throws Exception {
        File saved=new File(c.getFilesDir(),"saver-catalog-v2.json");
        long now=System.currentTimeMillis();
        if(saved.isFile()) try {
            JSONObject json=new JSONObject(SaverCache.read(saved,(int)(2*SaverCache.MIB)));
            long age=now-json.getLong("updated");
            if(age>=0&&age<86400000L) return parse(json.getJSONArray("items"));
        } catch(Exception ignored) { }
        List<SaverMedia> items=new ArrayList<>();
        // Explicitly reviewed NASA asset: clean 1080p Earthrise, no clock/photo overlays.
        items.add(new SaverMedia("https://svs.gsfc.nasa.gov/vis/a000000/a003900/a003936/earthrise.mp4","Earthrise",
                "NASA/Goddard SVS · Ernie Wright","https://svs.gsfc.nasa.gov/3936/","https://www.nasa.gov/nasa-brand-center/images-and-media/","video"));
        boolean refreshed=false;
        try {commonsVideo(items,active);refreshed=true;} catch(Exception error) {if(!active.getAsBoolean()) throw error;}
        try {commons(items,active,now);refreshed=true;} catch(Exception error) {if(!active.getAsBoolean()) throw error;}
        try {nasa(items,active);refreshed=true;} catch(Exception error) {if(!active.getAsBoolean()) throw error;}
        if(!refreshed&&saved.isFile()) try {return parse(new JSONObject(SaverCache.read(saved,(int)(2*SaverCache.MIB))).getJSONArray("items"));} catch(Exception ignored) { }
        if(refreshed&&active.getAsBoolean()) {
            JSONArray list=new JSONArray();for(SaverMedia item:items) list.put(item.json());
            File part=new File(saved.getParentFile(),saved.getName()+".tmp");
            try(FileOutputStream output=new FileOutputStream(part)) {output.write(new JSONObject().put("updated",now).put("items",list).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));output.getFD().sync();}
            if(!part.renameTo(saved)) part.delete();
        }
        return items;
    }
    private static List<SaverMedia> parse(JSONArray array) throws Exception {
        List<SaverMedia> list=new ArrayList<>();for(int i=0;i<Math.min(40,array.length());i++) {SaverMedia item=SaverMedia.parse(array.getJSONObject(i));if(allowed(item.url)) list.add(item);}return list;
    }
    private static String clean(String s) {return Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+"," ").trim();}
    private static String meta(JSONObject m,String name) {return m.optJSONObject(name)==null?"":m.optJSONObject(name).optString("value");}
    private static void commons(List<SaverMedia> list,BooleanSupplier active,long now) throws Exception {
        int offset=(int)((now/86400000L)%5)*8;
        String query=URLEncoder.encode("landscape filetype:bitmap haswbstatement:P275=Q20007257", "UTF-8");
        String url="https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrsearch="+query
                +"&gsrnamespace=6&gsrlimit=24&gsroffset="+offset+"&prop=imageinfo&iiprop=url%7Csize%7Cmime%7Cextmetadata&iiurlwidth=1920";
        JSONObject pages=new JSONObject(get(url,active)).optJSONObject("query");
        if(pages==null) return;pages=pages.optJSONObject("pages");if(pages==null) return;
        Iterator<String> keys=pages.keys();int accepted=0;
        while(keys.hasNext()&&accepted<8) {
            JSONObject page=pages.getJSONObject(keys.next());JSONArray info=page.optJSONArray("imageinfo");if(info==null||info.length()==0) continue;
            JSONObject image=info.getJSONObject(0), metadata=image.optJSONObject("extmetadata");if(metadata==null) continue;
            String license=meta(metadata,"LicenseShortName"), mime=image.optString("mime");
            if(!Arrays.asList("CC0","CC BY 4.0","CC BY 3.0").contains(license)||!mime.equals("image/jpeg")) continue;
            int width=image.optInt("width"),height=image.optInt("height");
            if(width<1280||height<720||width<height*1.3||width>height*2.5) continue;
            String media=image.optString("thumburl"), source=image.optString("descriptionurl"), artist=clean(meta(metadata,"Artist"));
            if(!allowed(media)||artist.isEmpty()) continue;
            list.add(new SaverMedia(media,clean(page.optString("title").replaceFirst("^File:","").replace('_',' ').replaceFirst("(?i)\\.(jpg|jpeg)$","")),artist+" · "+license,source,meta(metadata,"LicenseUrl"),"image"));accepted++;
        }
    }
    private static void commonsVideo(List<SaverMedia> list,BooleanSupplier active) throws Exception {
        // Reviewed CC0 aerial landscape; select HD instead of the 185 MB 4K original.
        String title=URLEncoder.encode("File:Aerial view of snowy landscape.webm","UTF-8");
        JSONObject query=new JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&format=json&titles="+title+"&prop=videoinfo&viprop=url%7Csize%7Cextmetadata%7Cderivatives",active)).getJSONObject("query").getJSONObject("pages");
        Iterator<String> pages=query.keys();
        while(pages.hasNext()) {
            JSONObject page=query.getJSONObject(pages.next());JSONArray infos=page.optJSONArray("videoinfo");if(infos==null||infos.length()==0) continue;
            JSONObject info=infos.getJSONObject(0),metadata=info.optJSONObject("extmetadata");if(metadata==null||!meta(metadata,"LicenseShortName").equals("CC0")) continue;
            JSONArray versions=info.optJSONArray("derivatives");if(versions==null) continue;String best="";int bestHeight=0;
            for(int i=0;i<versions.length();i++) {
                JSONObject version=versions.getJSONObject(i);int width=version.optInt("width"),height=version.optInt("height");
                if(width<=1920&&height>=360&&height<=1080&&height>bestHeight&&version.optString("type").contains("vp9")&&allowed(version.optString("src"))) {best=version.getString("src");bestHeight=height;}
            }
            if(!best.isEmpty()) list.add(new SaverMedia(best,"Snowy landscape",clean(meta(metadata,"Artist"))+" · CC0",info.getString("descriptionurl"),meta(metadata,"LicenseUrl"),"video"));
        }
    }
    private static void nasa(List<SaverMedia> list,BooleanSupplier active) throws Exception {
        JSONArray results=new JSONObject(get("https://images-api.nasa.gov/search?q=earth%20horizon&media_type=image&center=JSC&page_size=6",active)).getJSONObject("collection").getJSONArray("items");
        int accepted=0;
        for(int i=0;i<results.length()&&accepted<4;i++) {
            JSONObject item=results.getJSONObject(i), data=item.getJSONArray("data").getJSONObject(0);
            if(!data.optString("copyright").isEmpty()||!data.optString("center").equals("JSC")) continue;
            String id=data.getString("nasa_id");
            JSONArray assets=new JSONArray(get(item.getString("href"),active));String chosen="";
            for(int j=0;j<assets.length();j++) if(assets.getString(j).endsWith("~large.jpg")) {chosen=assets.getString(j);break;}
            if(!allowed(chosen)) continue;
            list.add(new SaverMedia(chosen,clean(data.getString("title")),"NASA/JSC","https://images.nasa.gov/details/"+URLEncoder.encode(id,"UTF-8"),"https://www.nasa.gov/nasa-brand-center/images-and-media/","image"));accepted++;
        }
    }
}

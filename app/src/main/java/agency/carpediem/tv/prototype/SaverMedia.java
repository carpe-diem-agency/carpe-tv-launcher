package agency.carpediem.tv.prototype;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Attribution travels with every cached item, independently of catalog refreshes. */
final class SaverMedia {
    final String url, title, credit, source, license, kind, id;
    SaverMedia(String url, String title, String credit, String source, String license, String kind) {
        this.url=url; this.title=title.substring(0,Math.min(180,title.length())); this.credit=credit.substring(0,Math.min(200,credit.length())); this.source=source; this.license=license; this.kind=kind;
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            char[] key=new char[64];for(int i=0;i<hash.length;i++) {int b=hash[i]&255;key[i*2]=Character.forDigit(b>>>4,16);key[i*2+1]=Character.forDigit(b&15,16);}id=new String(key);
        } catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
    String displayTitle() {
        String text=title.replaceFirst("(?i)^File:", "").replaceFirst("(?i)\\.(jpg|jpeg|png|webp|webm|mp4)$", "").replace('_',' ').trim();
        int comma=text.indexOf(','); if(comma>2) text=text.substring(0,comma);
        if(text.length()>48) { int end=text.lastIndexOf(' ',48); text=text.substring(0,end>20?end:48)+"…"; }
        return text;
    }
    JSONObject json() throws Exception { return new JSONObject().put("url",url).put("title",title).put("credit",credit).put("source",source).put("license",license).put("kind",kind); }
    static SaverMedia parse(JSONObject json) throws Exception {
        String kind=json.getString("kind");
        if(!kind.equals("image")&&!kind.equals("video")) throw new IllegalArgumentException("Unsupported media");
        return new SaverMedia(json.getString("url"),json.getString("title"),json.getString("credit"),json.getString("source"),json.getString("license"),kind);
    }
}

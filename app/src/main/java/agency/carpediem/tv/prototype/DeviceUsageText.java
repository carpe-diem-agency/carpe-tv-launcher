package agency.carpediem.tv.prototype;

import android.content.Context;
import java.text.NumberFormat;
import java.util.Locale;

/** Binary capacities, formatted in the interface locale. Display rounding never changes bar math. */
final class DeviceUsageText {
    private static final long MIB=1024L*1024,GIB=1024L*MIB;
    private DeviceUsageText() { }
    static String pair(Context context,long used,long total) {
        long safeTotal=Math.max(0,total),safeUsed=Math.max(0,Math.min(used,safeTotal));
        boolean giga=safeTotal>=GIB;
        return number(context,safeUsed,giga)+" / "+number(context,safeTotal,giga)+" "+context.getString(giga?R.string.device_unit_gb:R.string.device_unit_mb);
    }
    static String available(Context context,long bytes) {
        long safe=Math.max(0,bytes);boolean giga=safe>=GIB;
        String amount=number(context,safe,giga)+" "+context.getString(giga?R.string.device_unit_gb:R.string.device_unit_mb);
        return context.getString(R.string.device_free,amount);
    }
    private static String number(Context context,long bytes,boolean giga) {
        Locale locale=context.getResources().getConfiguration().getLocales().get(0);
        NumberFormat format=NumberFormat.getNumberInstance(locale);format.setGroupingUsed(false);
        format.setMinimumFractionDigits(giga?1:0);format.setMaximumFractionDigits(giga?1:0);
        return format.format(giga?(double)bytes/GIB:bytes/MIB);
    }
}

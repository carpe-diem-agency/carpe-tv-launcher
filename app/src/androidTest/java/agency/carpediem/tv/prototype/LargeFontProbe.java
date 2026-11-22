package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.LocaleList;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Detached actual XML measurement under app-local configuration; no system font settings. */
final class LargeFontProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        Context base=test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(base.getPackageName()),"Disposable validation package required");
        AppActionsFontProbe.run(test,result);
        ChoiceSheetFontProbe.run(test,result);
        SleepTimerFontProbe.run(test,result);
        SettingsStructureProbe.main(test,()->{
            for(String language:new String[]{"en","uk"})for(float scale:new float[]{1f,2f}) {
                Configuration config=new Configuration(base.getResources().getConfiguration());
                config.fontScale=scale;config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
                Context themed=new ContextThemeWrapper(base.createConfigurationContext(config),android.R.style.Theme_Material_NoActionBar);
                Typeface font=new Typeface.Builder(themed.getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build();
                float density=themed.getResources().getDisplayMetrics().density;
                TextView allow=UpdatesActivity.createUpdateButton(themed,font,themed.getString(R.string.allow_install),64);
                int width=Math.round(220*density);
                allow.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                allow.layout(0,0,width,allow.getMeasuredHeight());
                require(allow.getLayout()!=null&&allow.getLayout().getHeight()<=allow.getHeight()-allow.getCompoundPaddingTop()-allow.getCompoundPaddingBottom(),"Allow installation text clipped: "+language+"/"+scale);
                if(scale==1f)require(allow.getHeight()==Math.round(64*density),"Default update CTA height changed: "+language);
            }
        });
        result.putString("install_cta_font","PASS: actual Allow installation button fits EN/UK scale1/2");
        JSONArray measurements=new JSONArray();StringBuilder failures=new StringBuilder();
        SettingsStructureProbe.main(test,()->{
            for(String language:new String[]{"en","uk"})for(float scale:new float[]{1f,2f}) {
                Configuration config=new Configuration(base.getResources().getConfiguration());
                config.fontScale=scale;config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
                Context localized=base.createConfigurationContext(config);
                Context themed=new ContextThemeWrapper(localized,android.R.style.Theme_Material_NoActionBar);
                View root=LayoutInflater.from(themed).inflate(R.layout.appearance_controls,null,false);
                Typeface font;
                try{font=new Typeface.Builder(themed.getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build();}
                catch(RuntimeException missing){throw new AssertionError("INPUT_UNVERIFIED: actual app font unavailable",missing);}
                int[] controls={R.id.dynamic_wallpapers,R.id.animations,R.id.ui_sounds_enabled};
                for(int id:controls)((TextView)root.findViewById(id)).setTypeface(font);
                int width=Math.round(560*themed.getResources().getDisplayMetrics().density);
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
                root.layout(0,0,root.getMeasuredWidth(),root.getMeasuredHeight());
                for(int id:controls) {
                    TextView control=root.findViewById(id);Layout layout=control.getLayout();
                    require(layout!=null&&layout.getLineCount()>0,"Actual control text layout missing");
                    int available=control.getHeight()-control.getCompoundPaddingTop()-control.getCompoundPaddingBottom();
                    Rect ink=new Rect();String text=control.getText().toString();control.getPaint().getTextBounds(text,0,text.length(),ink);
                    int inkTop=control.getBaseline()+ink.top,inkBottom=control.getBaseline()+ink.bottom;
                    boolean fits=layout.getHeight()<=available&&inkTop>=control.getCompoundPaddingTop()&&inkBottom<=control.getHeight()-control.getCompoundPaddingBottom();
                    String name=base.getResources().getResourceEntryName(id);
                    measurements.put(new JSONObject().put("control",name).put("locale",language).put("font_scale",scale).put("width_px",control.getWidth()).put("height_px",control.getHeight()).put("text_layout_height_px",layout.getHeight()).put("available_text_height_px",available).put("lines",layout.getLineCount()).put("ink_top_px",inkTop).put("ink_bottom_px",inkBottom).put("fits",fits));
                    if(!fits){if(failures.length()>0)failures.append(';');failures.append(name).append('/').append(language).append('/').append(scale).append(" layout=").append(layout.getHeight()).append(" available=").append(available);}
                }
                checkSliderValues(root, themed, font, language, scale, failures);
                checkAppearanceButtons(root, themed, font, language, scale, failures);
            }
        });
        result.putString("largefont_measurements",measurements.toString());
        require(failures.length()==0,"Appearance controls did not fit in measured layout: "+failures);
        result.putString("largefont","PASS: Appearance switches, slider values, language, reset and preview fit at local fontScale1/2, English/Ukrainian");
        result.putString("largefont_scope","Detached themed XML at 320dp and 560dp; actual Montserrat and localized labels; excludes physical readability, full-page navigation and system font configuration");
    }

    private static void checkAppearanceButtons(View root, Context context, Typeface font,
            String locale, float scale, StringBuilder failures) {
        TextView language = root.findViewById(R.id.language_choice);
        TextView reset = root.findViewById(R.id.dim_reset);
        TextView preview = root.findViewById(R.id.ui_sounds_preview);
        for (TextView button : new TextView[]{language, reset, preview}) button.setTypeface(font);
        float density = context.getResources().getDisplayMetrics().density;
        for (int width : new int[]{320, 560}) {
            for (int option : new int[]{R.string.language_system, R.string.language_uk, R.string.language_en}) {
                language.setText(context.getString(R.string.language) + " · " + context.getString(option));
                measure(root, Math.round(width * density));
                if (!fitsLabel(language)) failures.append(" language ").append(locale).append('/').append(scale)
                        .append('/').append(width).append('/').append(context.getResources().getResourceEntryName(option));
            }
            for (TextView button : new TextView[]{reset, preview}) {
                if (!fitsLabel(button)) failures.append(' ').append(context.getResources().getResourceEntryName(button.getId()))
                        .append('/').append(locale).append('/').append(scale).append('/').append(width);
            }
            if (scale == 1f && (reset.getWidth() != Math.round(100 * density)
                    || reset.getHeight() != Math.round(40 * density)
                    || language.getHeight() != Math.round(52 * density))) {
                failures.append(" default button sizes ").append(locale).append('/').append(width);
            }
        }
    }

    private static void checkSliderValues(View root, Context context, Typeface font,
            String language, float scale, StringBuilder failures) {
        TextView dimLabel = root.findViewById(R.id.dim_label);
        dimLabel.setText(R.string.wallpaper_dim);
        TextView soundLabel = root.findViewById(R.id.ui_sounds_label);
        TextView dimValue = root.findViewById(R.id.dim_value);
        TextView soundValue = root.findViewById(R.id.ui_sounds_value);
        for (TextView control : new TextView[]{dimLabel, soundLabel, dimValue, soundValue}) control.setTypeface(font);
        SeekBar dim = root.findViewById(R.id.wallpaper_dim);
        SeekBar volume = root.findViewById(R.id.ui_sounds_volume);
        MainActivity.reserveSliderValueWidth(dimValue, dim.getMax(), level -> level + "%");
        MainActivity.reserveSoundSliderValueWidth(soundLabel, soundValue, volume.getMax());
        float density = context.getResources().getDisplayMetrics().density;
        for (int width : new int[]{320, 560}) {
            dimValue.setText("0%");
            soundValue.setText(context.getString(R.string.ui_sounds_level, 0));
            measure(root, Math.round(width * density));
            int dimTrack = dim.getWidth(), volumeTrack = volume.getWidth();
            if (!fitsValue(dimValue) || !fitsValue(soundValue)) failures.append(" zero value ").append(language).append('/').append(scale).append('/').append(width);
            dimValue.setText(dim.getMax() + "%");
            soundValue.setText(context.getString(R.string.ui_sounds_level, volume.getMax()));
            measure(root, Math.round(width * density));
            for (TextView value : new TextView[]{dimValue, soundValue}) {
                if (!fitsValue(value)) failures.append(' ').append(value.getText()).append('/').append(language).append('/').append(scale).append('/').append(width);
            }
            if (dimTrack != dim.getWidth() || volumeTrack != volume.getWidth()
                    || volumeTrack - volume.getPaddingStart() - volume.getPaddingEnd() < Math.round(64 * density)) {
                failures.append(" track ").append(language).append('/').append(scale).append('/').append(width);
            }
            if (!fitsLabel(dimLabel) || !fitsLabel(soundLabel)) {
                failures.append(" slider label ").append(language).append('/').append(scale).append('/').append(width);
            }
        }
    }

    private static void measure(View root, int width) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static boolean fitsValue(TextView value) {
        Layout layout = value.getLayout();
        int width = value.getWidth() - value.getCompoundPaddingLeft() - value.getCompoundPaddingRight();
        return layout != null && layout.getLineCount() == 1 && layout.getEllipsisCount(0) == 0
                && Layout.getDesiredWidth(value.getText(), value.getPaint()) <= width + 0.5f;
    }

    private static boolean fitsLabel(TextView label) {
        Layout layout = label.getLayout();
        int width = label.getWidth() - label.getCompoundPaddingLeft() - label.getCompoundPaddingRight();
        int height = label.getHeight() - label.getCompoundPaddingTop() - label.getCompoundPaddingBottom();
        if (layout == null || layout.getHeight() > height) return false;
        for (int line = 0; line < layout.getLineCount(); line++) {
            if (layout.getEllipsisCount(line) != 0 || layout.getLineMax(line) > width + 0.5f) return false;
        }
        return true;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

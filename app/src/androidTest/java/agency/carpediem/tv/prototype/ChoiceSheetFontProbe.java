package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shared production renderer, actual local text metrics; no system setting changes. */
final class ChoiceSheetFontProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        JSONArray measured=new JSONArray();int[] legacyClips={0};
        SettingsStructureProbe.main(test,()->{
            Context base=test.getTargetContext();
            for(String language:new String[]{"en","uk"})for(float scale:new float[]{1f,2f})
            for(int viewport:new int[]{540,300})for(int widthDp:new int[]{550,320})for(int selected:new int[]{-1,1}) {
                Configuration config=new Configuration(base.getResources().getConfiguration());
                config.fontScale=scale;config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
                Context c=new ContextThemeWrapper(base.createConfigurationContext(config),android.R.style.Theme_Material_NoActionBar);
                float density=c.getResources().getDisplayMetrics().density;
                Typeface font=new Typeface.Builder(c.getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build();
                String[] choices={c.getString(R.string.saver_builtin),c.getString(R.string.saver_aerial),
                        c.getString(R.string.saver_minutes,5)+c.getString(R.string.saver_recommended),c.getString(R.string.language_system)};
                int[] chosen={-1},dismissed={0};
                ChoiceSheetLayout sheet=new ChoiceSheetLayout(c,font,c.getString(R.string.saver_source_title),choices,selected,
                        (text,callback)->{Button b=MainActivity.createActionButton(c,font,text);b.setOnClickListener(v->callback.run());return b;},
                        option->{require(dismissed[0]>0,"Choice callback preceded dismiss");chosen[0]=option;},()->dismissed[0]++);
                int width=Math.round(widthDp*density),height=sheet.measure(width,Math.round(viewport*density));
                require(height<=Math.round((viewport-32)*density),"Choice panel exceeds viewport");
                require(sheet.cancel.getBottom()<=height-sheet.panel.getPaddingBottom(),"Cancel outside panel");
                require(sheet.scroll.getHeight()>0,"No option viewport");
                for(int i=0;i<choices.length;i++) {
                    Button row=(Button)sheet.list.getChildAt(i);fits(row);
                    require(row.isSelected()==(i==selected),"Selection changed");
                    require((row.getCompoundDrawablesRelative()[0]!=null)==(i==selected),"Selected badge missing/unexpected");
                    require(row.getNextFocusDownId()==(i+1<choices.length?sheet.list.getChildAt(i+1).getId():sheet.cancel.getId()),"Down route misses option/Cancel");
                    if(scale==1f)require(row.getHeight()>=Math.round(44*density),"Default minimum lost");
                    if(row.getLayout().getHeight()>Math.round(44*density)-row.getCompoundPaddingTop()-row.getCompoundPaddingBottom())legacyClips[0]++;
                }
                fits(sheet.cancel);
                require(sheet.initial==(selected>=0?sheet.list.getChildAt(selected):sheet.list.getChildAt(0)),"Initial focus selection changed");
                if(sheet.list.getHeight()>sheet.scroll.getHeight()) {
                    sheet.scroll.scrollTo(0,sheet.list.getHeight());
                    require(sheet.scroll.getScrollY()>0,"Overflow options cannot scroll");
                    View last=sheet.list.getChildAt(choices.length-1);
                    require(last.getBottom()-sheet.scroll.getScrollY()<=sheet.scroll.getHeight(),"Last option unreachable");
                }
                ((Button)sheet.list.getChildAt(1)).performClick();require(chosen[0]==1,"Wrong choice callback");
                chosen[0]=-1;sheet.cancel.performClick();require(chosen[0]==-1,"Cancel invoked choice");
                measured.put(new JSONObject().put("locale",language).put("scale",scale).put("viewport_dp",viewport)
                        .put("selected",selected).put("width_dp",widthDp).put("panel_px",height).put("list_px",sheet.list.getHeight()).put("viewport_px",sheet.scroll.getHeight()));
            }
        });
        result.putString("choice_sheet_measurements",measured.toString());
        result.putInt("choice_sheet_legacy_44dp_clips",legacyClips[0]);
        require(legacyClips[0]>0,"Fixture did not demonstrate fixed44dp clipping");
        result.putString("choice_sheet_font","PASS: actual shared renderer EN/UK scale1/2, chosen badge/Cancel, scroll and explicit D-pad links");
        result.putString("choice_sheet_scope","App-local detached geometry/callback order, not actual window focus/physical remote/recreation evidence");
    }
    private static void fits(TextView view) {
        require(view.getLayout()!=null&&view.getLayout().getHeight()<=view.getHeight()-view.getCompoundPaddingTop()-view.getCompoundPaddingBottom(),"Choice text clipped: "+view.getText());
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

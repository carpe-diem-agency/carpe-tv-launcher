package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.LocaleList;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual production XML and row sizing under local font/viewport configuration. */
final class AppActionsFontProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        JSONArray cases=new JSONArray();
        SettingsStructureProbe.main(test,()->{
            Context base=test.getTargetContext();
            for(String language:new String[]{"en","uk"})for(float scale:new float[]{1f,2f})
            for(int viewport:new int[]{540,240})for(boolean favorite:new boolean[]{false,true}) {
                Configuration c=new Configuration(base.getResources().getConfiguration());c.fontScale=scale;
                c.setLocales(new LocaleList(Locale.forLanguageTag(language)));
                Context themed=new ContextThemeWrapper(base.createConfigurationContext(c),android.R.style.Theme_Material_NoActionBar);
                float density=themed.getResources().getDisplayMetrics().density;
                Typeface font=new Typeface.Builder(themed.getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build();
                ScrollView sheet=(ScrollView)LayoutInflater.from(themed).inflate(R.layout.dialog_app_actions,null,false);
                TextView title=sheet.findViewById(R.id.action_title);title.setTypeface(font);
                title.setText("Long application title / Дуже довга назва програми з багатьма словами для перевірки обмеження заголовка");
                LinearLayout rows=sheet.findViewById(R.id.action_rows);
                int[] labels=favorite?new int[]{R.string.remove_favorite,R.string.move_app,R.string.app_info,R.string.cancel}
                        :new int[]{R.string.add_favorite,R.string.app_info,R.string.cancel};
                for(int label:labels) {
                    Button button=MainActivity.createActionButton(themed,font,themed.getString(label));button.setId(View.generateViewId());
                    button.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_heart,0,0,0);
                    button.setCompoundDrawablePadding(Math.round(12*density));
                    AppActionsLayout.addRow(rows,button);
                }
                int width=Math.round(300*density),screen=Math.round(viewport*density);
                int height=AppActionsLayout.boundedHeight(sheet,width,screen);
                sheet.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
                sheet.layout(0,0,width,height);
                require(height<=screen-Math.round(32*density),"Panel exceeds local viewport");
                require(title.getLineCount()<=3,"Long title is unbounded");
                for(int i=0;i<rows.getChildCount();i++) {
                    Button button=(Button)rows.getChildAt(i);
                    int available=button.getHeight()-button.getCompoundPaddingTop()-button.getCompoundPaddingBottom();
                    require(button.getLayout()!=null&&button.getLayout().getHeight()<=available,"Action text clipped: "+language+"/"+scale+"/"+button.getText());
                    require(button.getHeight()>=Math.round(48*density),"Default minimum action height lost");
                }
                int content=sheet.getChildAt(0).getHeight();
                if(viewport==240&&scale==2f)require(content>height,"Small large-font fixture must exercise scrolling");
                sheet.scrollTo(0,content);
                if(content>height)require(sheet.getScrollY()>0,"Overflowing content cannot scroll");
                cases.put(new JSONObject().put("locale",language).put("font_scale",scale).put("viewport_dp",viewport)
                        .put("favorite",favorite).put("height_px",height).put("content_px",content).put("title_lines",title.getLineCount()));
            }
        });
        result.putString("app_actions_font_measurements",cases.toString());
        result.putString("app_actions_font","PASS: actual XML/production row layout EN/UK scale1/2 and bounded small viewport");
        result.putString("app_actions_font_scope","Detached app-local measurement; not window focus, physical D-pad, callbacks or perceived readability evidence");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

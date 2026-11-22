package agency.carpediem.tv.prototype;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

/** Production choice geometry, also measured by local-font instrumentation. */
final class ChoiceSheetLayout {
    final LinearLayout panel,list;
    final ScrollView scroll;
    final TextView heading;
    final Button cancel;
    final Button initial;
    private final float density;
    private int dp(int value){return Math.round(value*density);}
    ChoiceSheetLayout(Context c,Typeface font,String title,String[] choices,int selected,
            BiFunction<String,Runnable,Button> factory,IntConsumer choose,Runnable dismiss) {
        density=c.getResources().getDisplayMetrics().density;
        panel=new LinearLayout(c);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(20),dp(20),dp(20),dp(20));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(c.getColor(R.color.cd_modal_surface));bg.setCornerRadius(20);panel.setBackground(bg);
        heading=new TextView(c);heading.setText(title);heading.setTextSize(22);heading.setTextColor(Color.WHITE);
        heading.setTypeface(Typeface.create(font,Typeface.BOLD));heading.setPadding(dp(8),0,0,dp(18));panel.addView(heading);
        scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setFocusable(false);
        list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0));
        Button first=null;
        for(int i=0;i<choices.length;i++) {
            final int option=i;
            Button button=factory.apply(choices[i],()->{dismiss.run();choose.accept(option);});
            sizeButton(button,15);button.setSelected(i==selected);button.setCompoundDrawablePadding(dp(10));
            if(i==selected)button.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check_badge,0,0,0);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=dp(6);list.addView(button,size);
            if(i==selected)first=button;
        }
        cancel=factory.apply(c.getString(R.string.cancel),dismiss);sizeButton(cancel,16);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(16);panel.addView(cancel,size);
        cancel.setId(View.generateViewId());
        for(int i=0;i<list.getChildCount();i++)list.getChildAt(i).setId(View.generateViewId());
        for(int i=0;i<list.getChildCount();i++) {
            View button=list.getChildAt(i);
            button.setNextFocusUpId(list.getChildAt(Math.max(0,i-1)).getId());
            button.setNextFocusDownId(i+1<list.getChildCount()?list.getChildAt(i+1).getId():cancel.getId());
        }
        cancel.setNextFocusUpId(list.getChildCount()>0?list.getChildAt(list.getChildCount()-1).getId():cancel.getId());
        cancel.setNextFocusDownId(cancel.getId());
        initial=first!=null?first:choices.length>0?(Button)list.getChildAt(0):cancel;
    }
    private void sizeButton(Button b,int sp) {
        b.setTextSize(sp);b.setSingleLine(false);b.setMaxLines(Integer.MAX_VALUE);
        b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));
    }
    int measure(int width,int screenHeight) {
        int inner=Math.max(1,width-panel.getPaddingLeft()-panel.getPaddingRight());
        int exact=View.MeasureSpec.makeMeasureSpec(inner,View.MeasureSpec.EXACTLY),free=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        heading.measure(exact,free);cancel.measure(exact,free);list.measure(exact,free);
        int fixed=panel.getPaddingTop()+panel.getPaddingBottom()+heading.getMeasuredHeight()+cancel.getMeasuredHeight()+dp(16);
        int available=Math.max(dp(1),screenHeight-dp(32)-fixed);
        // At normal scale retain the old 50dp-per-option / 350dp viewport cap.
        int listHeight=Math.min(Math.min(dp(350),list.getMeasuredHeight()),available);
        scroll.getLayoutParams().height=listHeight;
        int height=fixed+listHeight;
        panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        panel.layout(0,0,width,height);return height;
    }
}

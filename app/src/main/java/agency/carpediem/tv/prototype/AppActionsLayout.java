package agency.carpediem.tv.prototype;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/** Shared production sizing for the compact app-actions sheet. */
final class AppActionsLayout {
    private AppActionsLayout() { }
    private static int dp(View view,int value) {
        return Math.round(value*view.getResources().getDisplayMetrics().density);
    }
    static void addRow(LinearLayout rows,Button button) {
        button.setSingleLine(false);
        button.setMaxLines(Integer.MAX_VALUE);
        button.setMinHeight(dp(button,48));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
        size.bottomMargin=dp(button,6);
        rows.addView(button,size);
    }
    static int boundedHeight(View sheet,int width,int screenHeight) {
        sheet.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        return Math.min(sheet.getMeasuredHeight(),Math.max(1,screenHeight-dp(sheet,32)));
    }
}

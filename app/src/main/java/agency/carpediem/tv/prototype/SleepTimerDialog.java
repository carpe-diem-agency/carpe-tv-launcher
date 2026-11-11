package agency.carpediem.tv.prototype;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Remote-first timer sheet, using the same type and focus language as the launcher. */
final class SleepTimerDialog {
    private final Activity activity;
    private final Dialog dialog;
    private final Typeface font;
    private final LinearLayout panel;
    private TextView status;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,1000);}};
    SleepTimerDialog(Activity a) {
        activity=a;dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        Typeface loaded; try { loaded=new Typeface.Builder(a.getAssets(),"montserrat.ttf").setFontVariationSettings("'wght' 600").build(); } catch(RuntimeException e){loaded=Typeface.DEFAULT;}
        font=loaded;
        panel=new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(21),dp(22),dp(20));
        panel.setClipChildren(false);panel.setClipToPadding(false);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xF5273B5B,0xF5101B31});
        bg.setCornerRadius(dp(22));bg.setStroke(dp(1),0x728BAACB);panel.setBackground(bg);
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(font);return t;}
    private void updateStatus(){long left=SleepTimer.remaining(activity);status.setText(left>0?activity.getString(R.string.sleep_countdown,SleepTimer.countdown(left)):"");status.setTextColor(0xFFBFDFFF);status.setVisibility(left>0?View.VISIBLE:View.GONE);}
    private GradientDrawable buttonBackground(boolean focused,boolean primary){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused?new int[]{0xFFF7FAFF,0xFFDCEAFF}:primary?new int[]{0xFF3D6DAD,0xFF264F87}:new int[]{0xFF314761,0xFF22364F});
        bg.setCornerRadius(dp(13));bg.setStroke(dp(1),focused?Color.WHITE:0x667C99B9);return bg;
    }
    private TextView button(String title,boolean primary,Runnable click){
        TextView b=text(title,15,Color.WHITE);b.setGravity(Gravity.CENTER);b.setSingleLine(true);b.setFocusable(true);b.setClickable(true);b.setBackground(buttonBackground(false,primary));b.setOnClickListener(v->click.run());
        b.setOnFocusChangeListener((v,focused)->{b.setBackground(buttonBackground(focused,primary));b.setTextColor(focused?0xFF14243C:Color.WHITE);v.animate().cancel();v.animate().scaleX(focused?1.03f:1).scaleY(focused?1.03f:1).setDuration(190).start();});
        return b;
    }
    private void finish(){dialog.dismiss();if(activity instanceof MainActivity)((MainActivity)activity).updateSleepTimerIndicator();}
    void show(){
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(activity);icon.setImageResource(R.drawable.ic_sleep_timer);icon.setColorFilter(0xFFBFDFFF);heading.addView(icon,new LinearLayout.LayoutParams(dp(23),dp(23)));
        TextView title=text(activity.getString(R.string.sleep_timer),21,Color.WHITE);title.setPadding(dp(11),0,0,0);heading.addView(title);panel.addView(heading);
        status=text("",14,0xFFBFDFFF);status.setPadding(dp(1),dp(9),0,0);panel.addView(status);updateStatus();
        boolean access=SystemAccess.ready();
        boolean permission=Build.VERSION.SDK_INT<31||activity.getSystemService(AlarmManager.class).canScheduleExactAlarms();
        TextView initial;
        if(!access||!permission){
            TextView hint=text(activity.getString(!access?R.string.sleep_access:R.string.sleep_alarm),14,0xFFB9C9DC);hint.setLineSpacing(dp(2),1f);hint.setPadding(0,dp(15),0,0);panel.addView(hint);
            TextView setup=button(activity.getString(!access?R.string.sleep_setup:R.string.sleep_permission),true,()->{
                finish();try{if(!access)SystemAccess.request(activity);else activity.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName())));}catch(RuntimeException e){Toast.makeText(activity,R.string.settings_unavailable,Toast.LENGTH_LONG).show();}
            });LinearLayout.LayoutParams setupLayout=new LinearLayout.LayoutParams(-1,dp(50));setupLayout.topMargin=dp(18);panel.addView(setup,setupLayout);initial=setup;
        }else{
            TextView hint=text(activity.getString(R.string.sleep_choose),13,0xFFB9C9DC);hint.setPadding(dp(1),dp(17),0,dp(10));panel.addView(hint);
            LinearLayout row=new LinearLayout(activity);row.setClipChildren(false);panel.addView(row);
            int[] times={15,30,60,90,120};initial=null;
            for(int minutes:times){TextView b=button(Integer.toString(minutes),false,()->{try{SleepTimer.schedule(activity,minutes);finish();}catch(RuntimeException e){Toast.makeText(activity,R.string.sleep_failed,Toast.LENGTH_LONG).show();}});
                b.setContentDescription(activity.getString(R.string.sleep_minutes,minutes));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);if(initial==null)initial=b;}
        }
        LinearLayout footer=new LinearLayout(activity);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,dp(43));space.topMargin=dp(17);panel.addView(footer,space);
        if(SleepTimer.remaining(activity)>0){TextView cancel=button(activity.getString(R.string.sleep_cancel),false,()->{SleepTimer.cancel(activity);finish();});footer.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));}
        TextView close=button(activity.getString(R.string.sleep_close),false,this::finish);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(footer.getChildCount()>0)lp.leftMargin=dp(8);footer.addView(close,lp);
        dialog.setContentView(panel);Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.55f;w.setAttributes(attrs);w.setWindowAnimations(R.style.LumaDialogMotion);
        dialog.setOnDismissListener(d->handler.removeCallbacks(tick));dialog.show();
        int available=activity.getResources().getDisplayMetrics().widthPixels-dp(64);
        w.setLayout(Math.min(dp(420),available),-2);initial.requestFocus();handler.post(tick);
    }
}

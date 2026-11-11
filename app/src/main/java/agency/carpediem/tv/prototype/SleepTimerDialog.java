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
        panel=new LinearLayout(a); panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(28),dp(24),dp(28),dp(24));
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFA24344D,0xFC111D30});bg.setCornerRadius(dp(24));bg.setStroke(dp(1),0x446F8DAC);panel.setBackground(bg);
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(font);return t;}
    private void updateStatus(){long left=SleepTimer.remaining(activity);status.setText(left>0?activity.getString(R.string.sleep_countdown,SleepTimer.countdown(left)):activity.getString(R.string.sleep_idle));status.setTextColor(left>0?0xFFFFD68A:0xFFBAC8DB);status.setVisibility(left>0?View.VISIBLE:View.GONE);}
    private Button button(String title,Runnable click){
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(font);b.setTextColor(Color.WHITE);b.setBackgroundResource(R.drawable.search_background);b.setOnClickListener(v->click.run());
        b.setOnFocusChangeListener((v,focused)->{v.animate().cancel();v.animate().scaleX(focused?1.025f:1).scaleY(focused?1.025f:1).setDuration(180).start();});
        return b;
    }
    private void finish(){dialog.dismiss();if(activity instanceof MainActivity)((MainActivity)activity).updateSleepTimerIndicator();}
    void show(){
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(activity);icon.setImageResource(R.drawable.ic_sleep_timer);heading.addView(icon,new LinearLayout.LayoutParams(dp(28),dp(28)));
        TextView title=text(activity.getString(R.string.sleep_timer),26,Color.WHITE);title.setPadding(dp(12),0,0,0);heading.addView(title);panel.addView(heading);
        status=text("",20,0xFFFFD68A);status.setPadding(0,dp(18),0,dp(18));panel.addView(status);updateStatus();
        boolean access=SystemAccess.ready();
        boolean permission=Build.VERSION.SDK_INT<31||activity.getSystemService(AlarmManager.class).canScheduleExactAlarms();
        View initial;
        if(!access||!permission){
            TextView hint=text(activity.getString(!access?R.string.sleep_access:R.string.sleep_alarm),14,Color.WHITE);hint.setPadding(0,dp(16),0,dp(12));panel.addView(hint);
            Button setup=button(activity.getString(!access?R.string.sleep_setup:R.string.sleep_permission),()->{
                finish();try{if(!access)SystemAccess.request(activity);else activity.startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+activity.getPackageName())));}catch(RuntimeException e){Toast.makeText(activity,R.string.settings_unavailable,Toast.LENGTH_LONG).show();}
            });panel.addView(setup,new LinearLayout.LayoutParams(-1,dp(48)));initial=setup;
        }else{
            TextView hint=text(activity.getString(R.string.sleep_choose),14,0xFFBAC8DB);hint.setPadding(0,dp(22),0,dp(12));panel.addView(hint);
            LinearLayout row=new LinearLayout(activity);row.setClipChildren(false);panel.addView(row);
            int[] times={15,30,60,90,120};initial=null;
            for(int minutes:times){Button b=button(Integer.toString(minutes),()->{try{SleepTimer.schedule(activity,minutes);finish();}catch(RuntimeException e){Toast.makeText(activity,R.string.sleep_failed,Toast.LENGTH_LONG).show();}});
                b.setContentDescription(activity.getString(R.string.sleep_minutes,minutes));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1);lp.setMargins(dp(3),0,dp(3),0);row.addView(b,lp);if(initial==null)initial=b;}
        }
        LinearLayout footer=new LinearLayout(activity);LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,dp(46));space.topMargin=dp(22);panel.addView(footer,space);
        if(SleepTimer.remaining(activity)>0){Button cancel=button(activity.getString(R.string.sleep_cancel),()->{SleepTimer.cancel(activity);finish();});footer.addView(cancel,new LinearLayout.LayoutParams(0,-1,1));}
        Button close=button(activity.getString(R.string.sleep_close),this::finish);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.leftMargin=dp(8);footer.addView(close,lp);
        dialog.setContentView(panel);Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.6f;w.setAttributes(attrs);w.setWindowAnimations(R.style.LumaDialogMotion);
        dialog.setOnDismissListener(d->handler.removeCallbacks(tick));dialog.show();w.setLayout(dp(560),-2);initial.requestFocus();handler.post(tick);
    }
}

package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Real D-pad activation and modal containment for harmless Settings modals.
 * No language choice, cache clear, external link or system action is activated.
 */
final class ModalFocusProbe {
    private static final long WAIT_MS=7000;
    private ModalFocusProbe() { }

    static void run(Instrumentation test,Bundle result) throws Exception {
        run(test,result,false);
    }
    static void run(Instrumentation test,Bundle result,boolean touch) throws Exception {
        String packageName=test.getTargetContext().getPackageName();
        require("agency.carpediem.tv.validation.publicpreview".equals(packageName),"Modal focus probe requires the isolated preview package");
        SharedPreferences preferences=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(preferences.getAll());
        MainActivity activity=null;
        Throwable primaryFailure=null;
        int[] dialogWindow={-1};
        try {
            test.setInTouchMode(false);
            if(touch)test.getUiAutomation();
            require(preferences.edit().putBoolean("welcome_seen",true).commit(),"Cannot prepare isolated welcome state");
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final MainActivity screen=activity;
            test.waitForIdleSync();
            main(test,()->screen.findViewById(R.id.nav_settings).requestFocus());
            awaitMain(test,()->screen.findViewById(R.id.settings_categories)!=null,"Settings did not appear");
            main(test,()->{
                ViewGroup categories=screen.findViewById(R.id.settings_categories);
                require(categories.getChildCount()>1,"Screensaver category is missing");
                categories.getChildAt(categories.getChildCount()-1).requestFocus();
            });
            test.waitForIdleSync();
            View[] credits={null};
            main(test,()->{
                ViewGroup detail=screen.findViewById(R.id.settings_detail);
                View authors=detail.findViewById(R.id.about_authors);
                require(authors!=null,"Authors action is missing");authors.performClick();
                credits[0]=screen.findViewById(R.id.authors_media_credits);
                require(credits[0]!=null,"Credits action is missing");
            });
            verifyModal(test,screen,credits[0],screen.getString(R.string.saver_credits),
                    screen.getString(R.string.close),"credits",dialogWindow,result,touch);
            {
                int[] notices={R.id.authors_code_license,R.id.authors_third_party,R.id.authors_font_license};
                String[] titles={screen.getString(R.string.code_license),screen.getString(R.string.third_party_notices),"Montserrat · SIL OFL"};
                for(int i=0;i<notices.length;i++) {
                    if(touch) {
                        View[] anchor={null};final int noticeId=notices[i];
                        main(test,()->anchor[0]=screen.findViewById(noticeId));
                        verifyModal(test,screen,anchor[0],titles[i],screen.getString(R.string.close),"notice_"+i,dialogWindow,result,true);
                    } else verifyNotice(test,screen,notices[i],titles[i]);
                }
                result.putString("author_notices",touch?"PASS: all three notices actual touch/Up/Back exact origin":"PASS: all three notices shared typography, long-content D-pad/end scrolling, Back and Close exact origin");
            }
            // Opening and cancelling the existing language sheet never selects a language.
            String language=AppLanguage.selected(screen);
            main(test,()->{
                ViewGroup categories=screen.findViewById(R.id.settings_categories);
                categories.getChildAt(0).requestFocus();
            });
            awaitMain(test,()->screen.findViewById(R.id.language_choice)!=null,"Language action did not appear");
            View[] languageAction={null};
            main(test,()->languageAction[0]=screen.findViewById(R.id.language_choice));
            verifyModal(test,screen,languageAction[0],screen.getString(R.string.language),
                    screen.getString(R.string.cancel),"language_choice",dialogWindow,result,touch);
            require(language.equals(AppLanguage.selected(screen)),"Cancelling the language sheet changed the selected language");
            if(!touch)verifySaverSourceCancel(test,screen,result);
            result.putString(touch?"modal_touch":"modal_focus",touch
                    ?"PASS: actual injected touch, owned modal, D-pad Up containment and Back focus for Authors credits, three license notices and language choices"
                    :"PASS: real D-pad activation, owned modal, Up containment and Back focus for Authors credits, three license notices and language choices");
            result.putString("modal_focus_scope",touch?"Injected touchscreen route only; no physical remote/favorites/recreation evidence"
                    :"D-pad only; touch is a separate modaltouch check; no physical remote/favorites/recreation evidence");
        } catch(Exception|Error failure) {
            primaryFailure=failure;throw failure;
        } finally {
            Throwable cleanupFailure=null;
            if(activity!=null) {
                try {finishSession(test,activity);}
                catch(Exception|Error failure) {cleanupFailure=append(cleanupFailure,failure);}
            }
            try {
                if(activity!=null) requireQuiescent(test,activity);
                restore(preferences,saved);
            } catch(Exception|Error failure) {cleanupFailure=append(cleanupFailure,failure);}
            if(cleanupFailure!=null) {
                if(primaryFailure!=null) primaryFailure.addSuppressed(cleanupFailure);
                else rethrow(cleanupFailure);
            }
        }
    }

    private static void verifySaverSourceCancel(Instrumentation test,MainActivity screen,Bundle result)throws Exception {
        String provider=Screensaver.prefs(screen).getString(Screensaver.PROVIDER,"builtin");
        main(test,()->{
            ViewGroup categories=screen.findViewById(R.id.settings_categories);
            View saver=findText(categories,screen.getString(R.string.saver_title));
            require(saver!=null&&saver.requestFocus(),"Cannot focus Screensaver category");
        });
        awaitMain(test,()->screen.findViewById(R.id.saver_provider)!=null,"Screensaver source control missing");
        View[] origin={null};
        main(test,()->{origin[0]=screen.findViewById(R.id.saver_provider);require(origin[0].requestFocus(),"Cannot focus Screensaver source");});
        String title=screen.getString(R.string.saver_source_title),cancel=screen.getString(R.string.cancel);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->{View modal=ownedModal(screen,title,cancel);return modal!=null&&modal.hasWindowFocus();},"Screensaver choice sheet did not open");
        for(int step=0;step<3;step++)test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        awaitMain(test,()->{
            View modal=ownedModal(screen,title,cancel);
            if(!(modal instanceof ViewGroup))return false;
            View close=findText((ViewGroup)modal,cancel);
            return close!=null&&close.hasFocus()&&fullyVisible(close);
        },"D-pad Down did not reach visible Screensaver Cancel");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()==origin[0],"Cancel did not restore Screensaver source focus");
        require(provider.equals(Screensaver.prefs(screen).getString(Screensaver.PROVIDER,"builtin")),"Cancel changed Screensaver provider");
        result.putString("modal_saver_source_cancel","PASS: real D-pad OK/Down to visible Cancel and exact source focus restored");
    }

    private static void verifyModal(Instrumentation test,MainActivity screen,View anchor,
            String title,String close,String label,int[] ownedWindow,Bundle result,boolean touch) throws Exception {
        main(test,()->{
            require(anchor.isAttachedToWindow()&&anchor.requestFocus(),"Modal anchor cannot receive D-pad focus");
            anchor.requestRectangleOnScreen(new Rect(0,0,anchor.getWidth(),anchor.getHeight()),true);
        });
        test.waitForIdleSync();
        if(touch) {
            int[] xy=new int[2];
            awaitMain(test,()->{
                Rect visible=new Rect();int x=anchor.getWidth()/2,y=anchor.getHeight()/2;
                if(!screen.hasWindowFocus()||!anchor.getLocalVisibleRect(visible)||!visible.contains(x,y))return false;
                anchor.getLocationOnScreen(xy);xy[0]+=x;xy[1]+=y;return true;
            },"INPUT_UNVERIFIED: touch anchor has no visible center");
            long now=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {require(test.getUiAutomation().injectInputEvent(down,true),"INPUT_UNVERIFIED: touch Down rejected");}finally{down.recycle();}
            MotionEvent up=MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,xy[0],xy[1],0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {require(test.getUiAutomation().injectInputEvent(up,true),"INPUT_UNVERIFIED: touch Up rejected");}finally{up.recycle();}
        } else test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->{
            View modal=ownedModal(screen,title,close);
            return modal!=null&&modal.hasWindowFocus();
        },touch?"Touch-opened modal did not receive window focus":"D-pad-opened modal did not receive window focus");
        if(touch)main(test,()->{
            // Product deliberately exits touch mode before invoking the action.
            // The real accepted touchscreen events and owned modal prove the route.
            result.putBoolean("modal_"+label+"_touch_mode_after_open",anchor.isInTouchMode());
            View modal=ownedModal(screen,title,close);
            require(modal!=null&&modal.hasWindowFocus(),"Touch-opened modal did not receive window focus");
            require(anchor.isAttachedToWindow()&&settingsVisible(screen),"Touch activation lost its Settings origin");
        });
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);test.waitForIdleSync();
        main(test,()->{
            require(settingsVisible(screen),"D-pad Up changed Settings behind "+label);
            View modal=ownedModal(screen,title,close);
            require(modal!=null&&modal.hasWindowFocus(),"D-pad Up escaped the owned modal");
        });
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        awaitMain(test,()->screen.hasWindowFocus()&&anchor.isAttachedToWindow()
                &&screen.getCurrentFocus()==anchor&&settingsVisible(screen),"Back did not restore "+label+" anchor");
        result.putString("modal_"+label,touch?"PASS: actual touch/Up/Back and exact anchor restoration":"PASS: D-pad OK/Up/Back and exact anchor restoration");
    }
    private static void verifyNotice(Instrumentation test,MainActivity screen,int id,String title)throws Exception {
        View[] origin={null};String close=screen.getString(R.string.close);
        main(test,()->{origin[0]=screen.findViewById(id);require(origin[0]!=null&&origin[0].requestFocus(),"Notice origin missing");});
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->ownedModal(screen,title,close)!=null,"Notice did not open: "+title);
        android.widget.ScrollView[] scroll={null};
        awaitMain(test,()->{scroll[0]=noticeScroll(ownedModal(screen,title,close));return scroll[0]!=null;},"Visible notice scroll view missing: "+title);
        String marker=id==R.id.authors_code_license?"Apache License":id==R.id.authors_third_party?"ZXing Core":"SIL Open Font License";
        boolean[] overflow={false};
        main(test,()->{
            require(bodyContains(scroll[0],marker),"Notice asset marker missing: "+title);
            TextView body=noticeText(scroll[0],marker);
            java.lang.reflect.Field font=MainActivity.class.getDeclaredField("font");font.setAccessible(true);
            require(body!=null&&body.getTypeface().equals(font.get(screen)),"Notice body does not use app font face: "+title);
            require("asset-notice-body".equals(body.getTag()),"Notice body semantic tag missing: "+title);
            require(Math.abs(body.getLineSpacingExtra()-4f*screen.getResources().getDisplayMetrics().density)<1f,"Notice body line spacing mismatch: "+title);
            float density=screen.getResources().getDisplayMetrics().scaledDensity;
            require(Math.abs(body.getTextSize()/density-15f)<.2f&&body.getCurrentTextColor()==android.graphics.Color.WHITE,"Notice body typography/color mismatch: "+title);
            View modal=ownedModal(screen,title,close);
            View closeButton=findText((ViewGroup)modal,close);
            View heading=findText((ViewGroup)modal,title);
            require(closeButton!=null&&heading!=null&&closeButton.getParent()==scroll[0].getParent()&&heading.getParent()==scroll[0].getParent(),"Notice does not use shared heading/scroll/Close panel: "+title);
            require(fullyVisible(closeButton)&&fullyVisible(heading),"Notice heading/Close clipped: "+title);
            overflow[0]=scroll[0].getChildAt(0).getHeight()>scroll[0].getHeight();
            require(scroll[0].requestFocus(),"Notice scroll cannot focus: "+title);
        });
        for(int n=0;n<18;n++)test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
        test.waitForIdleSync();
        if(overflow[0])awaitMain(test,()->scroll[0].getScrollY()>0,"D-pad did not scroll long notice: "+title);
        // Android ScrollView's Alt+Down is a real hardware full-scroll action.
        main(test,()->require(scroll[0].requestFocus(),"Notice scroll lost focus before end action"));
        long event=SystemClock.uptimeMillis();
        test.sendKeySync(new KeyEvent(event,event,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0,KeyEvent.META_ALT_ON));
        test.sendKeySync(new KeyEvent(event,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN,0,KeyEvent.META_ALT_ON));
        if(overflow[0])awaitMain(test,()->!scroll[0].canScrollVertically(1)&&scroll[0].getScrollY()>0,"Notice end unreachable by D-pad: "+title);
        main(test,()->{
            require(settingsVisible(screen)&&ownedModal(screen,title,close)!=null,"Notice navigation escaped modal");
        });
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        awaitMain(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()==origin[0],"Notice Back lost exact origin");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->ownedModal(screen,title,close)!=null,"Notice did not reopen");
        main(test,()->{View button=findText((ViewGroup)ownedModal(screen,title,close),close);require(button!=null&&button.requestFocus(),"Notice Close cannot focus");});
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitMain(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()==origin[0],"Notice Close lost exact origin");
    }
    private static boolean fullyVisible(View view){Rect rect=new Rect();return view.getWidth()>0&&view.getHeight()>0&&view.getLocalVisibleRect(rect)&&rect.left==0&&rect.top==0&&rect.right==view.getWidth()&&rect.bottom==view.getHeight();}
    private static TextView noticeText(View root,String marker){
        if(root instanceof TextView&&((TextView)root).getText().toString().toLowerCase(java.util.Locale.ROOT).contains(marker.toLowerCase(java.util.Locale.ROOT)))return (TextView)root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){TextView found=noticeText(group.getChildAt(i),marker);if(found!=null)return found;}}return null;
    }
    private static android.widget.ScrollView noticeScroll(View root){
        if(root instanceof android.widget.ScrollView){
            android.widget.ScrollView candidate=(android.widget.ScrollView)root;
            if(candidate.isShown()&&candidate.getWidth()>0&&candidate.getHeight()>0&&candidate.getChildCount()>0
                    &&hasBodyText(candidate))return candidate;
        }
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.widget.ScrollView found=noticeScroll(group.getChildAt(i));if(found!=null)return found;}}return null;
    }
    private static boolean hasBodyText(View root){
        if(root instanceof TextView&&((TextView)root).getText().length()>40)return true;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(hasBodyText(group.getChildAt(i)))return true;}return false;
    }
    private static boolean bodyContains(View root,String marker){
        if(root instanceof TextView&&((TextView)root).getText().toString().toLowerCase(java.util.Locale.ROOT).contains(marker.toLowerCase(java.util.Locale.ROOT)))return true;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)if(bodyContains(group.getChildAt(i),marker))return true;}return false;
    }
    static View ownedModal(MainActivity screen,String title,String close) {
        for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
            if(root!=screen.getWindow().getDecorView()&&root.isAttachedToWindow()&&root.isShown()
                    &&root instanceof ViewGroup&&findText((ViewGroup)root,title)!=null&&findText((ViewGroup)root,close)!=null)return root;
        }
        return null;
    }
    private static View findText(ViewGroup group,String text) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child instanceof TextView&&text.contentEquals(((TextView)child).getText())) return child;
            if(child instanceof ViewGroup) {View found=findText((ViewGroup)child,text);if(found!=null) return found;}
        }
        return null;
    }
    private interface Check {boolean get() throws Exception;}
    private static void awaitMain(Instrumentation test,Check check,String message) throws Exception {
        long end=SystemClock.uptimeMillis()+WAIT_MS;
        while(SystemClock.uptimeMillis()<end) {
            boolean[] ready={false};main(test,()->ready[0]=check.get());
            if(ready[0]) return;SystemClock.sleep(30);
        }
        throw new AssertionError(message);
    }
    private interface MainAction {void run() throws Exception;}
    private static void main(Instrumentation test,MainAction action) throws Exception {
        Throwable[] failure={null};
        test.runOnMainSync(()->{try {action.run();}catch(Throwable error) {failure[0]=error;}});
        if(failure[0]!=null) rethrow(failure[0]);
    }
    private static boolean settingsVisible(MainActivity screen) throws Exception {
        Field page=MainActivity.class.getDeclaredField("page");page.setAccessible(true);
        Field content=MainActivity.class.getDeclaredField("content");content.setAccessible(true);
        ViewGroup current=(ViewGroup)content.get(screen);
        View categories=current.findViewById(R.id.settings_categories);
        View detail=current.findViewById(R.id.settings_detail);
        return "settings".equals(page.get(screen))&&current.isShown()
                &&categories!=null&&categories.isShown()&&detail!=null&&detail.isShown()
                &&screen.findViewById(R.id.nav_settings).isSelected()
                &&!screen.findViewById(R.id.nav_home).isSelected();
    }
    private static ExecutorService worker(MainActivity screen,String name) throws Exception {
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return (ExecutorService)field.get(screen);
    }
    private static void finishSession(Instrumentation test,MainActivity screen) throws Exception {
        Throwable failure=null;
        try {main(test,screen::finish);test.waitForIdleSync();}
        catch(Exception|Error error) {failure=append(failure,error);}
        for(String name:new String[]{"worker","wallpaperWorker"}) try {
            ExecutorService executor=worker(screen,name);executor.shutdownNow();
            require(executor.awaitTermination(15,TimeUnit.SECONDS),"Modal probe worker did not terminate");
        } catch(Exception|Error error) {failure=append(failure,error);}
        try {
            awaitMain(test,screen::isDestroyed,"Modal probe Activity did not finish before preference restoration");
            test.waitForIdleSync();
        } catch(Exception|Error error) {failure=append(failure,error);}
        if(failure!=null) rethrow(failure);
    }
    private static void requireQuiescent(Instrumentation test,MainActivity screen) throws Exception {
        main(test,()->require(screen.isDestroyed(),"Modal probe Activity remains active; preferences not restored"));
        for(String name:new String[]{"worker","wallpaperWorker"})
            require(worker(screen,name).isTerminated(),"Modal probe worker remains active; preferences not restored");
    }
    @SuppressWarnings("unchecked")
    private static void restore(SharedPreferences preferences,Map<String,?> saved) {
        SharedPreferences.Editor edit=preferences.edit().clear();
        for(Map.Entry<String,?> entry:saved.entrySet()) {
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String) edit.putString(key,(String)value);
            else if(value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer) edit.putInt(key,(Integer)value);
            else if(value instanceof Long) edit.putLong(key,(Long)value);
            else if(value instanceof Float) edit.putFloat(key,(Float)value);
            else if(value instanceof Set) edit.putStringSet(key,new HashSet<>((Set<String>)value));
            else throw new IllegalStateException("Unexpected preview preference type");
        }
        require(edit.commit(),"Cannot restore isolated preview preferences");
    }
    private static Throwable append(Throwable first,Throwable next) {
        if(first==null) return next;first.addSuppressed(next);return first;
    }
    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Error) throw (Error)failure;
        if(failure instanceof Exception) throw (Exception)failure;
        throw new AssertionError(failure);
    }
    private static void require(boolean condition,String message) {if(!condition) throw new AssertionError(message);}
}

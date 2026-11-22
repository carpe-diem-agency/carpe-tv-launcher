package agency.carpediem.tv.prototype;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Authors inline context and child-focus restoration across framework recreation. */
final class AuthorsStateProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        if(!"agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Disposable validation package required");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());List<MainActivity> sessions=new CopyOnWriteArrayList<>();
        AtomicReference<MainActivity> latest=new AtomicReference<>();Application app=(Application)test.getTargetContext().getApplicationContext();
        AtomicReference<MainActivity> noticeOwner = new AtomicReference<>();
        AtomicReference<View> noticeWindow = new AtomicReference<>();
        AtomicReference<Boolean> noticeAttachedAtDestroy = new AtomicReference<>();
        Application.ActivityLifecycleCallbacks callbacks=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){if(a instanceof MainActivity){sessions.add((MainActivity)a);latest.set((MainActivity)a);}}
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity activity) {
                if (activity == noticeOwner.get()) {
                    noticeAttachedAtDestroy.set(noticeWindow.get().isAttachedToWindow());
                }
            }
        };
        app.registerActivityLifecycleCallbacks(callbacks);Throwable primary=null;
        try {
            if(!prefs.edit().putBoolean("welcome_seen",true).commit())throw new AssertionError("Prepare welcome state");test.setInTouchMode(false);
            MainActivity screen=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.select(test,screen,5);
            SettingsStructureProbe.main(test,()->{screen.findViewById(R.id.about_authors).performClick();View child=screen.findViewById(R.id.authors_third_party);if(child==null||!child.requestFocus())throw new AssertionError("Authors child cannot focus");screen.recreate();});
            SettingsStructureProbe.await(test,()->latest.get()!=null&&latest.get()!=screen&&latest.get().hasWindowFocus(),"Recreated Authors did not regain window focus");
            MainActivity restored=latest.get();
            SettingsStructureProbe.await(test,()->restored.findViewById(R.id.authors_back)!=null&&restored.getCurrentFocus()!=null&&restored.getCurrentFocus().getId()==R.id.authors_third_party,"Recreation lost Authors child/context");
            String title = restored.getString(R.string.third_party_notices);
            String close = restored.getString(R.string.close);
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> {
                View modal = ModalFocusProbe.ownedModal(restored, title, close);
                if (modal == null || !modal.hasWindowFocus()) return false;
                noticeWindow.set(modal);
                return true;
            }, "D-pad did not open the Authors notice");
            noticeOwner.set(restored);
            SettingsStructureProbe.main(test, () -> {
                if (!noticeWindow.get().isAttachedToWindow()) throw new AssertionError("Notice detached before recreate");
                restored.recreate();
            });
            SettingsStructureProbe.await(test, () -> latest.get() != restored && latest.get().hasWindowFocus(),
                    "Recreated Activity did not regain focus after an open notice");
            result.putString("notice_at_owner_destroy", String.valueOf(noticeAttachedAtDestroy.get()));
            if (!Boolean.FALSE.equals(noticeAttachedAtDestroy.get())) {
                throw new AssertionError("Authors notice still had an attached window when its owner was destroyed");
            }

            MainActivity reopened = latest.get();
            SettingsStructureProbe.await(test, () -> reopened.findViewById(R.id.authors_back) != null
                    && reopened.getCurrentFocus() == reopened.findViewById(R.id.authors_third_party)
                    && ModalFocusProbe.ownedModal(reopened, title, close) == null,
                    "Recreation did not close the notice and restore its Authors action");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> {
                View modal = ModalFocusProbe.ownedModal(reopened, title, close);
                return modal != null && modal.hasWindowFocus();
            }, "Notice did not reopen from the restored action");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test, () -> reopened.hasWindowFocus()
                    && reopened.getCurrentFocus() == reopened.findViewById(R.id.authors_third_party),
                    "Notice Back did not restore the Authors action");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            test.waitForIdleSync();
            SettingsStructureProbe.await(test, () -> reopened.findViewById(R.id.about_feedback) != null
                    && reopened.getCurrentFocus() == reopened.findViewById(R.id.about_authors),
                    "Authors Back did not restore its About entry");
            result.putString("authors_state",
                    "PASS: Authors focus survives Activity recreation with and without an open notice; notice detaches during destruction and can reopen");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;for(MainActivity session:sessions)try{SettingsStructureProbe.finish(test,session);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try{for(MainActivity session:sessions)SettingsStructureProbe.quiescent(test,session);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            app.unregisterActivityLifecycleCallbacks(callbacks);if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }
}

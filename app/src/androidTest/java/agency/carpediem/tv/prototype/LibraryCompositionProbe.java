package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

/** Real IME visibility with direct editor InputConnection protocol, not keyboard UI gestures. */
final class LibraryCompositionProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),"IME probe requires disposable validation package");
        require(Build.VERSION.SDK_INT>=30,"IME visibility proof requires API 30 or later");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity activity=null;Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare validation welcome");
            test.setInTouchMode(false);
            MainActivity screen=activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(test,()->screen.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(test,()->firstTile(screen.findViewById(R.id.apps_grid))!=null,"Catalog did not become ready");
            Map<String,String> baseline=new LinkedHashMap<>();String[] chosen={null};
            SettingsStructureProbe.main(test,()->{
                collect(screen.findViewById(R.id.apps_grid),baseline);
                for(String label:baseline.values()) {
                    java.util.regex.Matcher match=java.util.regex.Pattern.compile("[a-z]{2}").matcher(label.toLowerCase(Locale.ROOT));
                    if(match.find()){chosen[0]=match.group();break;}
                }
                require(chosen[0]!=null,"INPUT_UNVERIFIED: catalog has no contiguous two-letter ASCII label fragment");
            });
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);key(test,KeyEvent.KEYCODE_DPAD_UP);
            SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.apps_search),"D-pad did not reach search"));
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test,()->imeVisible(screen),"INPUT_UNVERIFIED: search OK did not expose actual IME insets");
            EditText[] editor={null};
            android.view.inputmethod.InputConnection[] connection={null};
            SettingsStructureProbe.main(test,()->{
                editor[0]=screen.findViewById(R.id.apps_search);
                InputMethodManager manager=screen.getSystemService(InputMethodManager.class);
                require(manager.isActive(editor[0]),"INPUT_UNVERIFIED: search is not active IME editor");
                android.view.inputmethod.EditorInfo info=new android.view.inputmethod.EditorInfo();
                connection[0]=editor[0].onCreateInputConnection(info);
                require(connection[0]!=null,"INPUT_UNVERIFIED: editor InputConnection unavailable");
                require((info.imeOptions&android.view.inputmethod.EditorInfo.IME_MASK_ACTION)==android.view.inputmethod.EditorInfo.IME_ACTION_DONE,"Search does not advertise Done");
            });
            String positive=chosen[0];
            result.putString("library_composition_phase", "short ASCII");
            compose(test,screen,editor[0],connection[0],baseline,positive.substring(0,1));
            compose(test,screen,editor[0],connection[0],baseline,positive);
            SettingsStructureProbe.main(test,()->{
                require(connection[0].finishComposingText(),"finishComposingText rejected");
                require(android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor[0].getText())<0,"finishComposingText left composing span");
            });
            verify(test,screen,editor[0],baseline,positive,false);
            done(test,screen,editor[0],connection[0],positive,true);
            // Reopen the real keyboard without changing input methods or system settings.
            connection[0]=reopen(test,screen,editor[0]);
            result.putString("library_composition_phase", "short Cyrillic");
            compose(test,screen,editor[0],connection[0],baseline,"к");
            compose(test,screen,editor[0],connection[0],baseline,"ка");
            compose(test,screen,editor[0],connection[0],baseline,"КА");
            String absent="я";
            while(!expected(baseline,absent).isEmpty()&&absent.length()<64)absent+="я";
            require(expected(baseline,absent).isEmpty(),"INPUT_UNVERIFIED: cannot derive absent Unicode query");
            final String emptyQuery=absent;
            SettingsStructureProbe.main(test,()->{
                require(connection[0].setSelection(0,editor[0].length()),"Unicode commit selection rejected");
                require(connection[0].commitText(emptyQuery,1),"Unicode commit rejected");
            });
            verify(test,screen,editor[0],baseline,emptyQuery,false);
            done(test,screen,editor[0],connection[0],emptyQuery,false);
            result.putString("library_composition_phase", "rapid batch");
            verifyRapidEdits(test, screen, editor[0], baseline, positive, emptyQuery, result);
            result.putString("library_composition_phase", "verified");
            result.putString("library_composition","PASS: editor composing spans, incremental actual-catalog matching, Unicode composition/commit, Done matching/empty focus and IME hide");
            result.putString("library_composition_scope","Direct InputConnection protocol with actual IME visible; not soft-keyboard gesture/composition UI, suggestions, physical remote or input latency");
            result.putBoolean("library_composition_cyrillic_positive",!expected(baseline,"ка").isEmpty());
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(activity!=null){
                final MainActivity screen=activity;
                try{SettingsStructureProbe.main(test,()->((InputMethodManager)screen.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(screen.getWindow().getDecorView().getWindowToken(),0));}
                catch(Exception|Error failure){cleanup=failure;}
                try{SettingsStructureProbe.finish(test,screen);SettingsStructureProbe.quiescent(test,screen);}
                catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            }
            try{if(activity!=null)SettingsStructureProbe.quiescent(test,activity);SettingsStructureProbe.restore(prefs,saved);}
            catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }

    private static InputConnection reopen(Instrumentation test, MainActivity screen, EditText editor) throws Exception {
        SettingsStructureProbe.main(test, () -> require(editor.requestFocus(), "Cannot refocus search"));
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> imeVisible(screen), "INPUT_UNVERIFIED: IME reopen unavailable");
        InputConnection[] reopened = {null};
        SettingsStructureProbe.main(test, () -> {
            require(screen.getSystemService(InputMethodManager.class).isActive(editor), "Inactive editor after reopen");
            reopened[0] = editor.onCreateInputConnection(new android.view.inputmethod.EditorInfo());
            require(reopened[0] != null, "InputConnection unavailable after reopen");
        });
        return reopened[0];
    }

    private static void verifyRapidEdits(Instrumentation test, MainActivity screen, EditText editor,
            Map<String, String> baseline, String match, String absent, Bundle result) throws Exception {
        StringBuilder repeated = new StringBuilder();
        for (int i = 0; i < 128; i++) repeated.append("їєяЖ");
        String longQuery = repeated.toString();
        require(expected(baseline, longQuery).isEmpty(), "INPUT_UNVERIFIED: long query must have no matches");
        InputConnection connection = reopen(test, screen, editor);
        SettingsStructureProbe.main(test, () -> {
            require(connection.beginBatchEdit(), "IME batch edit rejected");
            try {
                for (int round = 0; round < 3; round++) {
                    require(connection.setSelection(0, editor.length())
                            && connection.setComposingText(longQuery, 1)
                            && longQuery.contentEquals(editor.getText()), "Long composition was truncated or rejected");
                    require(connection.finishComposingText() && connection.setSelection(editor.length(), editor.length())
                            && connection.deleteSurroundingText(longQuery.length(), 0)
                            && editor.length() == 0, "Long query deletion did not clear the editor");
                    require(connection.commitText(match, 1) && match.contentEquals(editor.getText()),
                            "Matching edit rejected");
                    require(connection.setSelection(0, editor.length()) && connection.commitText(absent, 1)
                            && absent.contentEquals(editor.getText()), "No-match edit rejected");
                }
                require(connection.setSelection(0, editor.length()) && connection.commitText(match, 1),
                        "Final matching edit rejected");
            } finally {
                connection.endBatchEdit();
            }
        });
        verify(test, screen, editor, baseline, match, false);
        done(test, screen, editor, connection, match, true);
        SettingsStructureProbe.main(test, () -> require(expected(baseline, match).contains(screen.getCurrentFocus().getTag()),
                "Done focused a stale tile after the burst"));

        InputConnection clear = reopen(test, screen, editor);
        SettingsStructureProbe.main(test, () -> {
            // An IME can mark the existing word as composing when it is reopened.
            // Finish that region before requesting deletion around the cursor.
            require(clear.finishComposingText() && clear.setSelection(editor.length(), editor.length())
                    && clear.deleteSurroundingText(editor.length(), 0) && editor.length() == 0,
                    "Final query deletion did not clear the editor");
        });
        verify(test, screen, editor, baseline, "", false);
        done(test, screen, editor, clear, "", true);
        result.putInt("library_rapid_composition_characters", longQuery.length());
        result.putInt("library_rapid_edit_rounds", 3);
        result.putString("library_rapid_edits", "PASS: Unicode burst, final exact results and Done focus, full catalog restored after clear");
    }

    private static Set<String> expected(Map<String,String> baseline,String query){
        Set<String> matches=new HashSet<>();String normalized=query.toLowerCase(Locale.ROOT);
        for(Map.Entry<String,String> entry:baseline.entrySet())if(entry.getValue().toLowerCase(Locale.ROOT).contains(normalized))matches.add(entry.getKey());
        return matches;
    }
    private static void compose(Instrumentation t,MainActivity screen,EditText editor,android.view.inputmethod.InputConnection connection,Map<String,String> baseline,String query)throws Exception {
        SettingsStructureProbe.main(t,()->{
            require(connection.setSelection(0,editor.length()),"Composition selection rejected");
            require(connection.setComposingText(query,1),"setComposingText rejected");
        });
        verify(t,screen,editor,baseline,query,true);
    }
    private static void verify(Instrumentation t,MainActivity screen,EditText editor,Map<String,String> baseline,String query,boolean composing)throws Exception {
        Set<String> wanted=expected(baseline,query);
        try {
            SettingsStructureProbe.await(t,()->{
                Map<String,String> actual=new LinkedHashMap<>();collect(screen.findViewById(R.id.apps_grid),actual);
                return query.contentEquals(editor.getText())&&actual.keySet().equals(wanted);
            },"InputConnection text/results did not settle to exact baseline match set");
        } catch (AssertionError error) {
            String[] evidence = {""};
            SettingsStructureProbe.main(t, () -> {
                Map<String, String> actual = new LinkedHashMap<>();
                collect(screen.findViewById(R.id.apps_grid), actual);
                evidence[0] = "expected query length " + query.length() + ", current length " + editor.length()
                        + ", same text " + query.contentEquals(editor.getText())
                        + ", expected result count " + wanted.size() + ", actual " + actual.size();
            });
            throw new AssertionError(error.getMessage() + " (" + evidence[0] + ")", error);
        }
        SettingsStructureProbe.main(t,()->{
            require(screen.findViewById(R.id.nav_apps).isSelected()&&screen.getCurrentFocus()==editor,"Editor update lost Apps/search focus");
            require(imeVisible(screen),"IME hid during composition/commit");
            int start=android.view.inputmethod.BaseInputConnection.getComposingSpanStart(editor.getText());
            int end=android.view.inputmethod.BaseInputConnection.getComposingSpanEnd(editor.getText());
            require(composing?(start==0&&end==query.length()):(start<0&&end<0),"Editor composing span mismatch");
        });
    }
    private static void done(Instrumentation t,MainActivity screen,EditText editor,android.view.inputmethod.InputConnection connection,String query,boolean matching)throws Exception {
        View[] target={null};
        SettingsStructureProbe.main(t,()->{
            target[0]=screen.findViewById(editor.getNextFocusDownId());
            if(matching)require(target[0]!=null&&target[0]!=editor&&target[0].isFocusable(),"Matching Done route missing");
            require(connection.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE),"IME Done rejected");
        });
        SettingsStructureProbe.await(t,()->!imeVisible(screen)&&screen.getCurrentFocus()==(matching?target[0]:editor),"Done did not hide IME and focus current result/search");
        SettingsStructureProbe.main(t,()->require(screen.findViewById(R.id.nav_apps).isSelected()&&query.contentEquals(editor.getText()),"Done lost Apps/query"));
    }
    private static boolean imeVisible(MainActivity screen){WindowInsets insets=screen.getWindow().getDecorView().getRootWindowInsets();return insets!=null&&insets.isVisible(WindowInsets.Type.ime());}
    private static View firstTile(ViewGroup root){if(root==null)return null;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.isFocusable()&&child.getTag()instanceof String)return child;if(child instanceof ViewGroup){View found=firstTile((ViewGroup)child);if(found!=null)return found;}}return null;}
    private static void collect(ViewGroup root,Map<String,String> result){
        if(root==null)return;
        for(int i=0;i<root.getChildCount();i++){
            View child=root.getChildAt(i);
            if(child.isFocusable()&&child.getTag()instanceof String){
                TextView name=child.findViewById(R.id.app_name);
                require(name!=null,"Catalog tile has no label");
                require(result.put((String)child.getTag(),name.getText().toString())==null,"Catalog rendered a duplicate package");
            }else if(child instanceof ViewGroup)collect((ViewGroup)child,result);
        }
    }
    private static void key(Instrumentation test,int key){test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

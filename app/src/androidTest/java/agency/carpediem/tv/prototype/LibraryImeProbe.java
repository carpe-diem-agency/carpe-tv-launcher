package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

/** Actual IME visibility and Back, using either hardware ASCII or visible keyboard controls. */
final class LibraryImeProbe {
    static void run(Instrumentation test,Bundle result)throws Exception {
        run(test, result, false);
    }

    static void run(Instrumentation test,Bundle result, boolean softKeys)throws Exception {
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
                if (!softKeys) {
                    for(String label:baseline.values()) {
                        java.util.regex.Matcher match=java.util.regex.Pattern.compile("[a-z]{2}").matcher(label.toLowerCase(Locale.ROOT));
                        if(match.find()){chosen[0]=match.group();break;}
                    }
                    require(chosen[0]!=null,"INPUT_UNVERIFIED: catalog has no contiguous two-letter ASCII label fragment");
                }
            });
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);key(test,KeyEvent.KEYCODE_DPAD_UP);
            SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.apps_search),"D-pad did not reach search"));
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test,()->imeVisible(screen),"INPUT_UNVERIFIED: search OK did not expose actual IME insets");
            UiAutomation automation = softKeys ? keyboardAutomation(test) : null;
            if (softKeys) {
                result.putString("library_soft_keys_phase", "keyboard visible");
                chosen[0] = softQuery(automation, baseline);
            }
            StringBuilder expected=new StringBuilder();
            for(char letter:chosen[0].toCharArray()) {
                if (softKeys) pressSoftKey(automation, letter, result);
                else {
                    KeyEvent[] events=KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(new char[]{letter});
                    require(events!=null,"Cannot create hardware ASCII events");
                    for(KeyEvent event:events){event.setSource(InputDevice.SOURCE_KEYBOARD);test.sendKeySync(event);}
                }
                expected.append(letter);String query=expected.toString();
                try {
                    SettingsStructureProbe.await(test,()->query.equalsIgnoreCase(((EditText)screen.findViewById(R.id.apps_search)).getText().toString()),
                            "Keyboard input did not reach search");
                } catch (AssertionError failure) {
                    SettingsStructureProbe.main(test, () -> {
                        EditText search = screen.findViewById(R.id.apps_search);
                        result.putInt("keyboard_expected_length", query.length());
                        result.putInt("keyboard_observed_length", search.length());
                    });
                    throw failure;
                }
                SettingsStructureProbe.main(test,()->{
                    require(screen.findViewById(R.id.nav_apps).isSelected(),"Typing left Apps");
                    require(screen.getCurrentFocus()==screen.findViewById(R.id.apps_search),"Typing stole search focus");
                    Map<String,String> rendered=new LinkedHashMap<>();collect(screen.findViewById(R.id.apps_grid),rendered);
                    Set<String> expectedPackages=new HashSet<>();
                    for(Map.Entry<String,String> item:baseline.entrySet())
                        if(item.getValue().toLowerCase(Locale.ROOT).contains(query))expectedPackages.add(item.getKey());
                    require(!expectedPackages.isEmpty(),"Baseline-derived query unexpectedly has no matches");
                    require(rendered.keySet().equals(expectedPackages),"Incremental search does not match the exact baseline package set");
                });
            }
            key(test,KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test,()->!imeVisible(screen),"Back did not hide actual IME");
            SettingsStructureProbe.main(test,()->{
                require(screen.findViewById(R.id.nav_apps).isSelected(),"First Back left Apps instead of only hiding IME");
                EditText search=screen.findViewById(R.id.apps_search);
                require(search!=null&&chosen[0].equalsIgnoreCase(search.getText().toString())&&screen.getCurrentFocus()==search,"First Back lost query/search focus");
            });
            Set<String> filteredPackages=new HashSet<>();
            for(Map.Entry<String,String> item:baseline.entrySet())if(item.getValue().toLowerCase(Locale.ROOT).contains(chosen[0]))filteredPackages.add(item.getKey());
            SettingsStructureProbe.await(test,()->screen.hasWindowFocus()&&!imeVisible(screen),"Search window did not regain input after IME hide");
            SettingsStructureProbe.main(test,()->{
                EditText search=screen.findViewById(R.id.apps_search);ViewGroup grid=screen.findViewById(R.id.apps_grid);
                View target=grid.findViewById(search.getNextFocusDownId());android.graphics.Rect visible=new android.graphics.Rect();
                boolean valid=target!=null&&target.getTag()instanceof String&&filteredPackages.contains(target.getTag())
                        &&target.isAttachedToWindow()&&target.isShown()&&target.isFocusable()&&target.isEnabled()
                        &&target.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;
                result.putBoolean("library_ime_before_down_visible_focusable_tile",valid);
                result.putInt("library_ime_before_down_target_id",search.getNextFocusDownId());
                result.putString("library_ime_before_down_target_type",target==null?"none":target.getClass().getSimpleName());
            });
            long downTime=SystemClock.uptimeMillis();
            KeyEvent down=new KeyEvent(downTime,downTime,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,0);down.setSource(InputDevice.SOURCE_DPAD);test.sendKeySync(down);
            KeyEvent up=new KeyEvent(downTime,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN,0);up.setSource(InputDevice.SOURCE_DPAD);test.sendKeySync(up);
            test.waitForIdleSync();
            SettingsStructureProbe.await(test,()->{
                EditText search=screen.findViewById(R.id.apps_search);ViewGroup grid=screen.findViewById(R.id.apps_grid);View focus=screen.getCurrentFocus();
                android.graphics.Rect visible=new android.graphics.Rect();
                return screen.findViewById(R.id.nav_apps).isSelected()&&!imeVisible(screen)&&chosen[0].equalsIgnoreCase(search.getText().toString())
                        &&focus!=null&&focus.getTag()instanceof String&&filteredPackages.contains(focus.getTag())&&grid.findViewWithTag(focus.getTag())==focus
                        &&focus.isAttachedToWindow()&&focus.isShown()&&focus.isFocusable()&&focus.isEnabled()&&focus.getGlobalVisibleRect(visible)&&visible.width()>0&&visible.height()>0;
            },"D-pad Down after hidden IME did not focus a visible filtered result while preserving query/IME state");
            SettingsStructureProbe.main(test,()->{
                Map<String,String> rendered=new LinkedHashMap<>();collect(screen.findViewById(R.id.apps_grid),rendered);
                require(rendered.keySet().equals(filteredPackages),"D-pad result focus changed exact filtered package set");
            });
            key(test,KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test,()->screen.findViewById(R.id.nav_home).isSelected(),"Back from focused result did not return Home");
            result.putBoolean("library_ime_focused_filtered_result",true);
            if (softKeys) {
                result.putString("library_soft_keys_phase", "verified");
                result.putString("library_soft_keys", "PASS: two visible IME keys activated by D-pad, exact match sets, Back and D-pad focus on a visible result");
            } else {
                result.putString("library_ime","PASS: real D-pad OK exposed IME, hardware ASCII updated filtered search, first Back hid IME preserving query/search focus, SOURCE_DPAD Down focused a real visible filtered result with query/IME unchanged, Back returned Home");
                result.putString("library_ime_scope","WindowInsets IME visibility and injected hardware ASCII only; no soft-key composition, IME Done, suggestions, Cyrillic or physical remote claim");
            }
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
    private static UiAutomation keyboardAutomation(Instrumentation test) {
        UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo service = automation.getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(service);
        return automation;
    }

    private static AccessibilityNodeInfo keyboardRoot(UiAutomation automation) {
        for (AccessibilityWindowInfo window : automation.getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null) return root;
            }
        }
        return null;
    }

    private static String softQuery(UiAutomation automation, Map<String,String> baseline) {
        Set<Character> letters = new HashSet<>();
        for (int attempt = 0; attempt < 5 && letters.isEmpty(); attempt++) {
            collectSoftKeys(keyboardRoot(automation), letters);
            if (letters.isEmpty()) SystemClock.sleep(200);
        }
        require(!letters.isEmpty(), "INPUT_UNVERIFIED: Android IME did not expose visible letter keys");
        for (String label : baseline.values()) {
            String lower = label.toLowerCase(Locale.ROOT);
            for (int i = 1; i < lower.length(); i++) {
                if (letters.contains(lower.charAt(i - 1)) && letters.contains(lower.charAt(i))) {
                    return lower.substring(i - 1, i + 1);
                }
            }
        }
        throw new AssertionError("INPUT_UNVERIFIED: no two visible keyboard letters matched the catalog");
    }

    private static void collectSoftKeys(AccessibilityNodeInfo node, Set<Character> keys) {
        if (node == null) return;
        Character letter = softLetter(node);
        if (letter != null) keys.add(letter);
        for (int i = 0; i < node.getChildCount(); i++) collectSoftKeys(node.getChild(i), keys);
    }

    private static Character softLetter(AccessibilityNodeInfo node) {
        if (!node.isVisibleToUser() || !node.isEnabled() || !node.isClickable()) return null;
        CharSequence text = node.getText();
        if (text == null || text.length() != 1) text = node.getContentDescription();
        return text != null && text.length() == 1 && Character.isLetter(text.charAt(0))
                ? Character.toLowerCase(text.charAt(0)) : null;
    }

    private static void pressSoftKey(UiAutomation automation, char letter, Bundle result) {
        for (int move = 0; move < 18; move++) {
            AccessibilityNodeInfo root = keyboardRoot(automation);
            require(root != null, "INPUT_UNVERIFIED: visible IME window unavailable");
            AccessibilityNodeInfo candidate = findSoftKey(root, letter);
            require(candidate != null, "INPUT_UNVERIFIED: visible IME letter key unavailable");
            AccessibilityNodeInfo selected = focusedKey(root);
            if (selected == null && move == 0) {
                keyboardDpad(automation, KeyEvent.KEYCODE_DPAD_RIGHT);
                continue;
            }
            result.putBoolean("library_soft_key_focus_exposed", selected != null);
            require(selected != null, "INPUT_UNVERIFIED: IME focused key unavailable");
            if (candidate.equals(selected)) {
                keyboardDpad(automation, KeyEvent.KEYCODE_DPAD_CENTER);
                return;
            }
            Rect target = new Rect(), current = new Rect();
            candidate.getBoundsInScreen(target);
            selected.getBoundsInScreen(current);
            require(!target.isEmpty() && !current.isEmpty(), "INPUT_UNVERIFIED: visible IME key bounds unavailable");
            if (Math.abs(target.centerY() - current.centerY()) > target.height() / 2) {
                keyboardDpad(automation, target.centerY() < current.centerY()
                        ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
            } else {
                keyboardDpad(automation, target.centerX() < current.centerX()
                        ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
            }
        }
        throw new AssertionError("INPUT_UNVERIFIED: D-pad did not reach the expected IME key");
    }

    private static AccessibilityNodeInfo focusedKey(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isVisibleToUser() && node.isEnabled() && node.isFocused() && node.isClickable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = focusedKey(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void keyboardDpad(UiAutomation automation, int code) {
        long start = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            KeyEvent event = new KeyEvent(start, SystemClock.uptimeMillis(), action, code, 0);
            event.setSource(InputDevice.SOURCE_DPAD);
            require(automation.injectInputEvent(event, true), "INPUT_UNVERIFIED: IME D-pad input rejected");
        }
        SystemClock.sleep(120);
    }

    private static AccessibilityNodeInfo findSoftKey(AccessibilityNodeInfo node, char target) {
        if (node == null) return null;
        Character letter = softLetter(node);
        if (letter != null && letter == target) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findSoftKey(node.getChild(i), target);
            if (found != null) return found;
        }
        return null;
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

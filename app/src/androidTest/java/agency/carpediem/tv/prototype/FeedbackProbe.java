package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.graphics.drawable.BitmapDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Decodes actual rendered Issue QRs, including the approved diagnostic prefill. */
final class FeedbackProbe {
    static void run(Instrumentation test,Bundle result) throws Exception {run(test,result,false);}
    static void runBounded(Instrumentation test,Bundle result) throws Exception {run(test,result,true);}
    @SuppressWarnings("deprecation")
    static void runCompactDiagnostics(Instrumentation test, Bundle result) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Use the disposable validation package");
        SharedPreferences prefs = test.getTargetContext().getSharedPreferences(MainActivity.class.getName(), 0);
        SharedPreferences language = test.getTargetContext().getSharedPreferences("MainActivity", 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll()), savedLanguage = new HashMap<>(language.getAll());
        UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        test.setInTouchMode(false);
        try {
            require(prefs.edit().putBoolean("welcome_seen", true).commit(), "Prepare welcome state");
            for (String locale : new String[]{"uk", "en"}) {
                require(language.edit().putString("language", locale).commit(), "Prepare locale " + locale);
                MainActivity screen = (MainActivity) test.startActivitySync(new Intent(test.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                Resources resources = screen.getResources();
                Configuration original = new Configuration(resources.getConfiguration());
                DisplayMetrics originalMetrics = new DisplayMetrics();
                originalMetrics.setTo(resources.getDisplayMetrics());
                try {
                    SettingsStructureProbe.select(test, screen, 4);
                    key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
                    View origin = screen.findViewById(R.id.device_diagnostics);
                    SettingsStructureProbe.await(test, () -> origin != null && origin.hasFocus(),
                            "Device entry did not focus Diagnostics");
                    int width = Math.round(360 * originalMetrics.density);
                    SettingsStructureProbe.main(test, () -> {
                        Configuration local = new Configuration(original);
                        local.fontScale = 2f;
                        DisplayMetrics metrics = new DisplayMetrics();
                        metrics.setTo(originalMetrics);
                        metrics.widthPixels = width + Math.round(32 * originalMetrics.density);
                        resources.updateConfiguration(local, metrics);
                        resources.getDisplayMetrics().widthPixels = metrics.widthPixels;
                    });
                    key(test, KeyEvent.KEYCODE_DPAD_CENTER);
                    SettingsStructureProbe.await(test, () -> {
                        ViewGroup window = qrWindow(screen, R.string.diagnostic_qr);
                        if (window == null || !window.hasWindowFocus()) return false;
                        result.putInt("diagnostics_observed_width_px", window.getWidth());
                        result.putInt("diagnostics_local_width_px", resources.getDisplayMetrics().widthPixels);
                        return window.getWidth() == width && resources.getConfiguration().fontScale == 2f;
                    }, "Diagnostics did not open in the compact fixture: " + locale);
                    DiagnosticReport.Report expected = DiagnosticReport.capture(screen);
                    SettingsStructureProbe.main(test, () -> {
                        ViewGroup window = qrWindow(screen, R.string.diagnostic_qr);
                        TextView report = window.findViewById(R.id.diagnostic_text);
                        View qr = window.findViewById(R.id.diagnostic_qr);
                        require(report != null && expected.displayText().contentEquals(report.getText()),
                                "Visible report differs from the current fields");
                        require(report.getWidth() >= width * 3 / 4, "Diagnostic report squeezed beside the QR: " + locale);
                        requireFullyVisible(qr, "diagnostic QR at open");
                    });
                    String raw = decodeRendered(test, automation, screen, "compact-diagnostics-" + locale,
                            R.string.diagnostic_qr);
                    verifyDiagnosticFields(raw);
                    require(expected.text().equals(raw), "Compact diagnostic QR changed the seven report fields");
                    focusCompactControl(test, screen, R.string.diagnostic_report_bug);
                    focusCompactControl(test, screen, R.string.close);
                    key(test, KeyEvent.KEYCODE_DPAD_CENTER);
                    awaitDiagnosticOrigin(test, screen, origin, "Compact Close");
                    result.putString("diagnostics_compact_" + locale,
                            width + "px wide, local 2x text, screen-decoded raw QR and D-pad actions");
                } finally {
                    try {
                        SettingsStructureProbe.finish(test, screen);
                    } finally {
                        SettingsStructureProbe.main(test, () -> resources.updateConfiguration(original, originalMetrics));
                    }
                }
            }
        } finally {
            SettingsStructureProbe.restore(language, savedLanguage);
            SettingsStructureProbe.restore(prefs, saved);
        }
    }

    private static void focusCompactControl(Instrumentation test, MainActivity screen, int id) throws Exception {
        View[] focused = {null};
        for (int step = 0; step < 10; step++) {
            SettingsStructureProbe.main(test, () -> {
                View target = findText(qrWindow(screen, R.string.diagnostic_qr), screen.getString(id));
                focused[0] = target != null && target.hasFocus() && fullyVisible(target) ? target : null;
            });
            if (focused[0] != null) break;
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            SystemClock.sleep(180);
        }
        require(focused[0] instanceof TextView, "D-pad could not reveal " + screen.getString(id));
        SettingsStructureProbe.main(test, () -> {
            TextView label = (TextView) focused[0];
            Layout layout = label.getLayout();
            require(layout != null && layout.getLineCount() > 0
                    && layout.getHeight() + label.getCompoundPaddingTop()
                    + label.getCompoundPaddingBottom() <= label.getHeight()
                    && layout.getLineEnd(layout.getLineCount() - 1) == label.length(),
                     "Compact action label is clipped: " + label.getText() + ", height=" + label.getHeight()
                    + ", text_height=" + (layout == null ? -1 : layout.getHeight()));
            for (int line = 0; line < layout.getLineCount(); line++) {
                require(layout.getEllipsisCount(line) == 0, "Compact action label is truncated");
            }
        });
    }

    private static void run(Instrumentation test,Bundle result,boolean bounded) throws Exception {
        require("agency.carpediem.tv.validation.publicpreview".equals(test.getTargetContext().getPackageName()),
                "Feedback probe requires the disposable validation package");
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences(MainActivity.class.getName(),0);
        Map<String,?> saved=new HashMap<>(prefs.getAll());MainActivity activity=null;Throwable primary=null;
        try {
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare isolated welcome state");
            // Connect capture support before the Activity creates its windows.
            UiAutomation automation = test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            test.setInTouchMode(false);
            MainActivity screen=activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.select(test,screen,5);
            key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
            SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.about_company),"About entry did not focus the agency website link"));
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.main(test,()->require(screen.getCurrentFocus()==screen.findViewById(R.id.about_updates),"About Down did not reach Check updates"));
            key(test,KeyEvent.KEYCODE_DPAD_DOWN);
            View[] feedback={null};
            SettingsStructureProbe.main(test,()->{
                feedback[0]=screen.findViewById(R.id.about_feedback);
                require(screen.getCurrentFocus()==feedback[0],"D-pad did not reach Feedback in the simplified About screen");
            });
            key(test,KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test,()->dialog(screen)!=null,"Rendered Issue QR dialog did not appear");
            if(bounded) SettingsStructureProbe.await(test,()->{
                ViewGroup root=dialog(screen);return root!=null&&root.hasWindowFocus()&&root.getWidth()>0&&root.getHeight()>0;
            },"Owned dialog did not lay out/focus before bounding");
            if(bounded) SettingsStructureProbe.main(test,()->{
                ViewGroup root=dialog(screen);
                require(root.getLayoutParams() instanceof android.view.WindowManager.LayoutParams,"Owned dialog window attrs unavailable");
                android.view.WindowManager.LayoutParams attrs=new android.view.WindowManager.LayoutParams();
                attrs.copyFrom((android.view.WindowManager.LayoutParams)root.getLayoutParams());
                attrs.width=Math.min(1280,root.getWidth());attrs.height=Math.min(720,root.getHeight());
                require(attrs.width>0&&attrs.height>0,"Owned dialog must be laid out before bounding");
                ((android.view.WindowManager)screen.getSystemService(android.content.Context.WINDOW_SERVICE)).updateViewLayout(root,attrs);
            });
            // The window's alpha/scale entrance runs for 160 ms after layout.
            SystemClock.sleep(200);
            if(bounded) SettingsStructureProbe.main(test,()->{
                ViewGroup root=dialog(screen);require(root.getWidth()>0&&root.getWidth()<=1280&&root.getHeight()>0&&root.getHeight()<=720,"Bounded viewport did not apply");
                result.putInt("feedbackbounded_viewport_width_px",root.getWidth());result.putInt("feedbackbounded_viewport_height_px",root.getHeight());
            });
            SettingsStructureProbe.main(test,()->require(findText(dialog(screen),screen.getString(R.string.open_issue))!=null,"Issue browser fallback is missing"));
            if(!bounded) SettingsStructureProbe.main(test,()->{
                requireFullyVisible(findText(dialog(screen),screen.getString(R.string.open_issue)),"initial Open on TV");
                requireFullyVisible(findText(dialog(screen),screen.getString(R.string.close)),"initial Close");
                requireFullyVisible(findText(dialog(screen),screen.getString(R.string.feedback_privacy)),"public issue and privacy notice");
            });
            String[] templates={"bug.yml","idea.yml","other.yml"};
            int[] labels={R.string.feedback_bug,R.string.feedback_idea,R.string.feedback_other};
            String ideaUrl = null;
            for(int i=0;i<templates.length;i++) {
                if(i>0)key(test,KeyEvent.KEYCODE_DPAD_RIGHT);
                final int choice=i;
                SettingsStructureProbe.main(test,()->{
                    View button=findText(dialog(screen),screen.getString(labels[choice]));
                    require(button!=null&&button.hasFocus(),"D-pad missed a feedback topic");
                });
                key(test,KeyEvent.KEYCODE_DPAD_CENTER);
                SystemClock.sleep(200);
                if(!bounded) SettingsStructureProbe.main(test,()->{
                    ViewGroup panel=dialog(screen);
                    requireFullyVisible(findText(panel,screen.getString(R.string.open_issue)),"Open on TV");
                    requireFullyVisible(findText(panel,screen.getString(R.string.close)),"Close");
                });
                String decoded=decodeRendered(test,automation,screen,templates[i]);
                Uri uri=Uri.parse(decoded);
                require("https".equals(uri.getScheme())&&"github.com".equals(uri.getAuthority())
                        &&"/carpe-diem-agency/carpe-tv-launcher/issues/new".equals(uri.getPath()),"Rendered feedback QR leaves the approved GitHub Issue route");
                require(uri.getFragment()==null && uri.getQueryParameterNames().size()==2
                        &&uri.getQueryParameters("template").size()==1 && uri.getQueryParameters("device").size()==1
                        &&templates[i].equals(uri.getQueryParameter("template")),"Rendered feedback QR has unexpected query fields or template");
                verifyDiagnosticFields(uri.getQueryParameter("device"));
                require(DiagnosticReport.capture(screen).text().equals(uri.getQueryParameter("device")),"Rendered Issue QR lacks the exact approved diagnostic prefill");
                if (i == 1) ideaUrl = decoded;
            }
            if(bounded) {
                key(test,KeyEvent.KEYCODE_DPAD_DOWN);
                SettingsStructureProbe.await(test,()->{
                    View button=findText(dialog(screen),screen.getString(R.string.open_issue));return button!=null&&button.hasFocus()&&fullyVisible(button);
                },"Bounded Down did not focus and reveal Open on TV");
                key(test,KeyEvent.KEYCODE_DPAD_DOWN);
                SettingsStructureProbe.await(test,()->{
                    View button=findText(dialog(screen),screen.getString(R.string.close));return button!=null&&button.hasFocus()&&fullyVisible(button);
                },"Bounded Down did not focus and reveal Close");
            }
            key(test,KeyEvent.KEYCODE_BACK);
            SettingsStructureProbe.await(test,()->screen.hasWindowFocus()&&screen.getCurrentFocus()==feedback[0],"Closing Feedback did not restore its About action");
            if(bounded) {
                result.putString("feedbackbounded","PASS: actual owned lower-pixel window, three rendered QR crops decoded, Down reveals/focuses Open and Close, Back restores About origin");
                result.putString("feedbackbounded_scope","Physical display/density unchanged; owned dialog window only capped at 1280x720px, measured viewport reported; no 720p-device, phone scan or browser-launch claim. Screenshots decoded in memory only.");
            } else {
                verifyOpenIntent(test, screen, feedback[0], ideaUrl);
                verifyDiagnostics(test, automation, screen, result);
                result.putString("feedback","PASS: About D-pad entry, three rendered Issue QRs with approved diagnostics, matching browser intent and Back focus");
                result.putString("feedback_open_intent","PASS: actual D-pad Open on TV emitted the decoded Idea QR URL and restored About focus; ACTION_VIEW intercepted before browser launch");
                result.putString("feedback_scope","No phone scan, sign-in, issue submission or external browser launch");
            }
        } catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(activity!=null)try{SettingsStructureProbe.finish(test,activity);}catch(Exception|Error failure){cleanup=failure;}
            try{if(activity!=null)SettingsStructureProbe.quiescent(test,activity);SettingsStructureProbe.restore(prefs,saved);}
            catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }

    private static void verifyDiagnostics(Instrumentation test, UiAutomation automation,
            MainActivity screen, Bundle result) throws Exception {
        SettingsStructureProbe.select(test, screen, 4);
        key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
        View[] diagnosticAction = {null};
        SettingsStructureProbe.main(test, () -> diagnosticAction[0] = screen.findViewById(R.id.device_diagnostics));
        View origin = diagnosticAction[0];
        SettingsStructureProbe.await(test, () -> origin != null && screen.getCurrentFocus() == origin,
                "Entering Device did not focus Diagnostics");
        openDiagnostics(test, screen);
        DiagnosticReport.Report expected = DiagnosticReport.capture(screen);
        SettingsStructureProbe.main(test, () -> {
            ViewGroup panel = qrWindow(screen, R.string.diagnostic_qr);
            TextView report = panel.findViewById(R.id.diagnostic_text);
            View hint = panel.findViewById(R.id.diagnostic_qr_help);
            require(report != null && expected.displayText().contentEquals(report.getText()),
                    "Visible diagnostic report does not match the current report");
            require(hint instanceof TextView
                    && screen.getString(R.string.diagnostic_qr_help).contentEquals(((TextView) hint).getText()),
                    "The raw-text QR hint is missing");
        });
        String raw = decodeRendered(test, automation, screen, "diagnostics", R.string.diagnostic_qr);
        verifyDiagnosticFields(raw);
        require(expected.text().equals(raw),
                "Diagnostic QR payload differs from the seven raw report lines");

        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test, () -> {
            View close = findText(qrWindow(screen, R.string.diagnostic_qr), screen.getString(R.string.close));
            return fullyVisible(close) && close.hasFocus();
        }, "Diagnostics Down did not reach Close");
        for (int direction : new int[]{KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP}) {
            key(test, direction);
            SettingsStructureProbe.main(test, () -> {
                ViewGroup panel = qrWindow(screen, R.string.diagnostic_qr);
                require(panel != null && panel.hasWindowFocus() && panel.findFocus() != null
                        && !screen.hasWindowFocus(), "A direction escaped the Diagnostics dialog");
            });
        }
        key(test, KeyEvent.KEYCODE_BACK);
        awaitDiagnosticOrigin(test, screen, origin, "Back from Diagnostics");

        openDiagnostics(test, screen);
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> {
            ViewGroup issue = dialog(screen);
            View bug = findText(issue, screen.getString(R.string.feedback_bug));
            return issue != null && issue.hasWindowFocus() && bug != null && bug.hasFocus()
                    && qrWindow(screen, R.string.diagnostic_qr) == null;
        }, "Diagnostics did not open Feedback focused on Bug");
        Uri bug = Uri.parse(decodeRendered(test, automation, screen, "diagnostic-bug", R.string.issue_qr));
        require("https".equals(bug.getScheme()) && "github.com".equals(bug.getAuthority())
                && "/carpe-diem-agency/carpe-tv-launcher/issues/new".equals(bug.getPath())
                && "bug.yml".equals(bug.getQueryParameter("template"))
                && raw.equals(bug.getQueryParameter("device")), "Diagnostics Bug QR did not carry the exact report");
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test, () -> {
            View open = findText(dialog(screen), screen.getString(R.string.open_issue));
            return fullyVisible(open) && open.hasFocus();
        }, "Feedback Down did not reach Open on TV");
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test, () -> {
            View close = findText(dialog(screen), screen.getString(R.string.close));
            return fullyVisible(close) && close.hasFocus();
        }, "Feedback Down did not reach Close");

        key(test, KeyEvent.KEYCODE_BACK);
        awaitDiagnosticOrigin(test, screen, origin, "Back from Bug feedback");
        openDiagnostics(test, screen);
        key(test, KeyEvent.KEYCODE_DPAD_DOWN);
        SettingsStructureProbe.await(test, () -> {
            View close = findText(qrWindow(screen, R.string.diagnostic_qr), screen.getString(R.string.close));
            return close != null && close.hasFocus();
        }, "Diagnostics Close could not be reached after reopening");
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        awaitDiagnosticOrigin(test, screen, origin, "Close from Diagnostics");
        result.putString("diagnostic_route",
                "PASS: rendered raw QR, D-pad containment, close/reopen, Bug prefill and exact Diagnostics focus");
    }

    private static void openDiagnostics(Instrumentation test, MainActivity screen) throws Exception {
        key(test, KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> {
            ViewGroup panel = qrWindow(screen, R.string.diagnostic_qr);
            View report = panel == null ? null : panel.findViewById(R.id.diagnostic_report_bug);
            return panel != null && panel.hasWindowFocus() && fullyVisible(report) && report.hasFocus();
        }, "Diagnostics did not open with Report a bug focused");
    }

    private static void awaitDiagnosticOrigin(Instrumentation test, MainActivity screen, View origin, String route)
            throws Exception {
        SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == origin
                && dialog(screen) == null && qrWindow(screen, R.string.diagnostic_qr) == null,
                route + " did not return focus to the same Diagnostics view");
    }

    private static void verifyDiagnosticFields(String report) {
        String[] prefixes = {"Carpe TV: ", "Device: ", "Android: ", "Build: ", "Output: ", "Interface: ", "Developer options: "};
        require(report != null, "QR diagnostic data is missing");
        String[] lines = report.split("\n", -1);
        require(lines.length == prefixes.length, "QR diagnostics contain an unexpected number of fields");
        for (int i = 0; i < prefixes.length; i++) {
            require(lines[i].startsWith(prefixes[i]) && lines[i].length() > prefixes[i].length(),
                    "QR diagnostics failed the approved field checks at line " + (i + 1));
        }
    }

    private static void verifyOpenIntent(Instrumentation test, MainActivity screen, View origin, String expectedUrl) throws Exception {
        require(expectedUrl != null, "Idea QR was not decoded");
        AtomicReference<Uri> captured = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override
            public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
                captured.set(intent.getData());
                return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
            }
        };
        test.addMonitor(monitor);
        try {
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            SettingsStructureProbe.await(test, () -> dialog(screen) != null && dialog(screen).hasWindowFocus(),
                    "Feedback did not reopen for the browser action");
            key(test, KeyEvent.KEYCODE_DPAD_RIGHT);
            SettingsStructureProbe.await(test, () -> {
                View idea = findText(dialog(screen), screen.getString(R.string.feedback_idea));
                return idea != null && idea.hasFocus();
            }, "D-pad did not reach Idea after reopening Feedback");
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            key(test, KeyEvent.KEYCODE_DPAD_DOWN);
            SettingsStructureProbe.await(test, () -> {
                View open = findText(dialog(screen), screen.getString(R.string.open_issue));
                return fullyVisible(open) && open.hasFocus();
            }, "D-pad did not reach Open on TV after selecting Idea");
            key(test, KeyEvent.KEYCODE_DPAD_CENTER);
            require(Uri.parse(expectedUrl).equals(captured.get()) && monitor.getHits() == 1,
                    "Open on TV did not request the same URL as the rendered Idea QR");
            SettingsStructureProbe.await(test, () -> screen.hasWindowFocus() && screen.getCurrentFocus() == origin,
                    "Open on TV did not restore the Feedback origin");
        } finally {
            test.removeMonitor(monitor);
        }
    }
    private static boolean fullyVisible(View view) {
        Rect visible=new Rect();return view!=null&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0
                &&view.getLocalVisibleRect(visible)&&visible.left==0&&visible.top==0&&visible.right==view.getWidth()&&visible.bottom==view.getHeight();
    }
    private static void requireFullyVisible(View view,String action) {
        Rect visible=new Rect();
        require(view!=null&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0
                &&view.getLocalVisibleRect(visible)&&visible.left==0&&visible.top==0
                &&visible.right==view.getWidth()&&visible.bottom==view.getHeight(),
                "Feedback action is not fully visible: "+action);
    }
    private static String decodeRendered(Instrumentation test,UiAutomation automation,MainActivity screen,String template) throws Exception {
        return decodeRendered(test, automation, screen, template, R.string.issue_qr);
    }

    private static String decodeRendered(Instrumentation test, UiAutomation automation, MainActivity screen,
            String template, int description) throws Exception {
        long deadline=SystemClock.uptimeMillis()+2000;
        int attempts=0,cropWidth=0,cropHeight=0,shotWidth=0,shotHeight=0;
        com.google.zxing.ReaderException last=null;
        for(int attempt=0;attempt<6&&SystemClock.uptimeMillis()<deadline;attempt++) {
            attempts++;
            Rect[] bounds={null};
            SettingsStructureProbe.main(test,()->{
                View qr=findDescription(qrWindow(screen, description), screen.getString(description));
                require(qr!=null&&qr.getWidth()>0&&qr.getHeight()>0,"Feedback QR disappeared before capture");
                int[] location=new int[2];qr.getLocationOnScreen(location);Rect visible=new Rect();
                require(qr.getLocalVisibleRect(visible)&&visible.width()==qr.getWidth()&&visible.height()==qr.getHeight(),
                        "Feedback QR is not fully visible for capture");
                bounds[0]=new Rect(location[0],location[1],location[0]+qr.getWidth(),location[1]+qr.getHeight());
            });
            Bitmap shot=automation.takeScreenshot(),region=null;
            try {
                require(shot!=null&&bounds[0].left>=0&&bounds[0].top>=0&&bounds[0].right<=shot.getWidth()
                        &&bounds[0].bottom<=shot.getHeight(),"Feedback QR capture bounds are invalid");
                shotWidth=shot.getWidth();shotHeight=shot.getHeight();cropWidth=bounds[0].width();cropHeight=bounds[0].height();
                region=Bitmap.createBitmap(shot,bounds[0].left,bounds[0].top,cropWidth,cropHeight);
                return bitmapText(region);
            } catch(com.google.zxing.ReaderException unavailable) {last=unavailable;}
            finally {if(region!=null)region.recycle();if(shot!=null)shot.recycle();}
            if(attempt<5&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(80);
        }
        // Native bitmap decoding is diagnostic only. It can never substitute for
        // decoding the actual screen crop, which remains the pass criterion.
        Bitmap[] raw={null};
        SettingsStructureProbe.main(test,()->{
            View qr=findDescription(qrWindow(screen, description), screen.getString(description));
            if(qr instanceof android.widget.ImageView&&((android.widget.ImageView)qr).getDrawable() instanceof BitmapDrawable)
                raw[0]=((BitmapDrawable)((android.widget.ImageView)qr).getDrawable()).getBitmap();
        });
        boolean rawDecodable=false;int rawWidth=0,rawHeight=0;
        if(raw[0]!=null) {
            rawWidth=raw[0].getWidth();rawHeight=raw[0].getHeight();
            try {bitmapText(raw[0]);rawDecodable=true;}catch(com.google.zxing.ReaderException unavailable) { }
        }
        throw new AssertionError("Rendered feedback QR failed: template="+template+", attempts="+attempts
                +", crop="+cropWidth+"x"+cropHeight+", screenshot="+shotWidth+"x"+shotHeight
                +", rawBitmap="+rawWidth+"x"+rawHeight+", rawDecodable="+rawDecodable,last);
    }
    private static String bitmapText(Bitmap bitmap) throws com.google.zxing.NotFoundException {
        int width=bitmap.getWidth(),height=bitmap.getHeight();int[] pixels=new int[width*height];
        bitmap.getPixels(pixels,0,width,0,0,width,height);
        return new com.google.zxing.MultiFormatReader().decode(new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(new com.google.zxing.RGBLuminanceSource(width,height,pixels)))).getText();
    }
    private static ViewGroup dialog(MainActivity screen) {
        return qrWindow(screen, R.string.issue_qr);
    }
    private static ViewGroup qrWindow(MainActivity screen, int description) {
        for (View root : WindowInspector.getGlobalWindowViews()) {
            if (root instanceof ViewGroup && root != screen.getWindow().getDecorView() && root.isAttachedToWindow()
                    && root.isShown() && findDescription(root, screen.getString(description)) != null) {
                return (ViewGroup) root;
            }
        }
        return null;
    }
    private static View findDescription(View root,String text) {
        if(root==null)return null;if(text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private static View findText(View root,String text) {
        if(root==null)return null;if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}return null;
    }
    private static void key(Instrumentation test,int key){test.sendKeyDownUpSync(key);test.waitForIdleSync();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

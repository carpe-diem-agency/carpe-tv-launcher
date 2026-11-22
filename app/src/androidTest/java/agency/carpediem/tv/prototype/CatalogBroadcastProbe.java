package agency.carpediem.tv.prototype;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.*;
import android.content.pm.PackageInfo;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual owned fixture package install/remove, never synthetic showApps or system packages. */
final class CatalogBroadcastProbe {
    private static final String FIXTURE="agency.carpediem.tv.testfixture.catalog";
    private static final String APK="/data/local/tmp/carpe-catalog-fixture.apk";

    static void verifyDeniedLaunch(Instrumentation test, Bundle result) throws Exception {
        Context context = test.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),
                "Disposable validation required");
        require(!present(test), "Fixture already installed; refuse ownership");
        String permission = android.Manifest.permission.DUMP;
        android.content.pm.PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(APK, android.content.pm.PackageManager.GET_ACTIVITIES);
        require(archive != null && FIXTURE.equals(archive.packageName) && archive.versionCode == 1
                && archive.activities != null && archive.activities.length == 1
                && permission.equals(archive.activities[0].permission), "Host must stage the protected fixture");
        require(context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_DENIED,
                "Validation client unexpectedly holds the fixture permission");
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.class.getName(), 0);
        Map<String, ?> saved = new HashMap<>(prefs.getAll());
        String history = "agency.carpediem.tv.testfixture.not_launched";
        MainActivity activity = null;
        try {
            require(shell(test, "pm install -t " + APK).contains("Success"), "Protected fixture install failed");
            require(prefs.edit().putBoolean("welcome_seen", true)
                    .putString("last_opened_package", history).commit(), "Could not prepare fixture state");
            test.setInTouchMode(false);
            MainActivity screen = activity = (MainActivity) test.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(test, () -> {
                screen.findViewById(R.id.nav_apps).requestFocus();
                ((EditText) screen.findViewById(R.id.apps_search)).setText("Калина");
            });
            awaitCatalog(test, screen, Collections.singleton(FIXTURE), "Protected fixture missing from filtered catalog");
            Intent launch = pm.getLeanbackLaunchIntentForPackage(FIXTURE);
            require(launch != null && new ComponentName(FIXTURE, archive.activities[0].name).equals(launch.getComponent()),
                    "Leanback resolver did not return the protected component");
            View[] tile = new View[1];
            int[] scroll = new int[1];
            SettingsStructureProbe.main(test, () -> {
                List<View> matches = new ArrayList<>();
                tiles(screen.findViewById(R.id.apps_grid), matches);
                require(matches.size() == 1 && matches.get(0).requestFocus(), "Could not focus fixture tile");
                tile[0] = matches.get(0);
                scroll[0] = ((ScrollView) screen.findViewById(R.id.apps_scroll)).getScrollY();
            });
            boolean toast = false;
            try {
                android.view.accessibility.AccessibilityEvent event = test.getUiAutomation(
                        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeAndWaitForEvent(
                        () -> test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER),
                        e -> e.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                                && e.getText().contains(screen.getString(R.string.app_launch_failed)), 2000);
                event.recycle();
                toast = true;
            } catch (TimeoutException unavailable) {
                // Some TV builds omit toast accessibility events. Check the actual system denial independently.
            }
            SettingsStructureProbe.main(test, () -> {
                require(screen.hasWindowFocus() && screen.findViewById(R.id.nav_apps).isSelected()
                        && screen.getCurrentFocus() == tile[0], "Denied launch changed page or focus");
                require("Калина".contentEquals(((EditText) screen.findViewById(R.id.apps_search)).getText())
                        && scroll[0] == ((ScrollView) screen.findViewById(R.id.apps_scroll)).getScrollY(),
                        "Denied launch changed query or scroll");
                require(history.equals(prefs.getString("last_opened_package", null)),
                        "Denied launch wrote successful launch history");
            });
            result.putString("catalog_launch_caller", android.os.Process.myPid() + ":" + android.os.Process.myUid());
            result.putString("cataloglaunchdenied", "PASS: tile OK preserved Apps, focus, query, scroll and history");
            result.putString("catalog_launch_failure_toast", toast ? "OBSERVED: app_launch_failed" : "UNVERIFIED: no toast event");
        } finally {
            try {
                if (activity != null) SettingsStructureProbe.finish(test, activity);
                SettingsStructureProbe.restore(prefs, saved);
            } finally {
                if (present(test)) require(shell(test, "pm uninstall " + FIXTURE).contains("Success"),
                        "Protected fixture cleanup failed");
            }
        }
    }

    static void run(Instrumentation t,Bundle result)throws Exception {
        Context context=t.getTargetContext();
        require("agency.carpediem.tv.validation.publicpreview".equals(context.getPackageName()),"Disposable validation required");
        verifyArtworkFallback(context, result);
        require(!present(t),"INPUT_UNVERIFIED: fixture already installed; refuse ownership");
        PackageInfo archive=context.getPackageManager().getPackageArchiveInfo(APK,0);
        require(archive!=null&&FIXTURE.equals(archive.packageName)&&archive.versionCode==1,"INPUT_UNVERIFIED: host must prevalidate and push exact fixture APK");
        SharedPreferences prefs=context.getSharedPreferences(MainActivity.class.getName(),0);Map<String,?> saved=new HashMap<>(prefs.getAll());
        AtomicInteger additions=new AtomicInteger(),removals=new AtomicInteger();
        AtomicBoolean additionReplacing=new AtomicBoolean(),removalReplacing=new AtomicBoolean();
        AtomicReference<Throwable> witnessFailure=new AtomicReference<>();
        Context application=context.getApplicationContext();
        BroadcastReceiver witness=new BroadcastReceiver(){
            @Override public void onReceive(Context ignored,Intent intent){
                try {
                    if(intent.getData()==null||!"package".equals(intent.getData().getScheme())||!FIXTURE.equals(intent.getData().getSchemeSpecificPart()))return;
                    boolean replacing=intent.getBooleanExtra(Intent.EXTRA_REPLACING,false);
                    if(Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())){additionReplacing.set(replacing);additions.incrementAndGet();}
                    else if(Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())){removalReplacing.set(replacing);removals.incrementAndGet();}
                }catch(Throwable failure){witnessFailure.compareAndSet(null,failure);}
            }
        };
        MainActivity activity=null;boolean installAttempted=false,witnessRegistered=false;Throwable primary=null;
        try {
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_PACKAGE_ADDED);filter.addAction(Intent.ACTION_PACKAGE_REMOVED);filter.addDataScheme("package");filter.addDataSchemeSpecificPart(FIXTURE,PatternMatcher.PATTERN_LITERAL);
            if(Build.VERSION.SDK_INT>=33)application.registerReceiver(witness,filter,Context.RECEIVER_EXPORTED);
            else application.registerReceiver(witness,filter);
            witnessRegistered=true;
            require(prefs.edit().putBoolean("welcome_seen",true).commit(),"Prepare welcome");t.setInTouchMode(false);
            MainActivity screen=activity=(MainActivity)t.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            SettingsStructureProbe.main(t,()->screen.findViewById(R.id.nav_apps).requestFocus());
            SettingsStructureProbe.await(t,()->{ViewGroup grid=screen.findViewById(R.id.apps_grid);return screen.findViewById(R.id.nav_apps).isSelected()&&grid!=null&&grid.isShown()&&!catalog(grid).isEmpty();},"INPUT_UNVERIFIED: real catalog unavailable");
            Map<String,String> baseline=new LinkedHashMap<>();String[] selected={null};int[] offset={0};
            SettingsStructureProbe.main(t,()->{
                EditText search=screen.findViewById(R.id.apps_search);require(search.getText().length()==0,"INPUT_UNVERIFIED: baseline query must be empty");
                baseline.putAll(catalog(screen.findViewById(R.id.apps_grid)));require(!baseline.containsKey(FIXTURE),"Fixture unexpectedly in baseline");
                List<View> tiles=new ArrayList<>();tiles(screen.findViewById(R.id.apps_grid),tiles);View last=tiles.get(tiles.size()-1);
                require(last.requestFocus(),"Cannot establish surviving tile focus");selected[0]=(String)last.getTag();
            });t.waitForIdleSync();
            SettingsStructureProbe.main(t,()->{
                ScrollView scroll=screen.findViewById(R.id.apps_scroll);int max=maximum(scroll);require(max>0,"INPUT_UNVERIFIED: real catalog must scroll");scroll.scrollTo(0,Math.min(80,max));offset[0]=scroll.getScrollY();require(offset[0]>0,"Nonzero scroll prerequisite unavailable");
            });
            installAttempted=true;
            require(shell(t,"pm install -t "+APK).contains("Success"),"INPUT_UNVERIFIED: owned fixture install failed");
            SettingsStructureProbe.await(t,()->additions.get()>0||witnessFailure.get()!=null,"Actual owned PACKAGE_ADDED witness missing");
            require(witnessFailure.get()==null&&!additionReplacing.get(),"Owned PACKAGE_ADDED witness failed or unexpectedly replacing");
            Set<String> added=new HashSet<>(baseline.keySet());added.add(FIXTURE);
            awaitCatalog(t,screen,added,"Actual PACKAGE_ADDED did not produce exact baseline plus fixture");
            SettingsStructureProbe.await(t,()->{
                ScrollView scroll=screen.findViewById(R.id.apps_scroll);View focus=screen.getCurrentFocus();
                return !scroll.isLayoutRequested()&&scroll.getChildCount()>0&&!scroll.getChildAt(0).isLayoutRequested()
                        &&focus!=null&&selected[0].equals(focus.getTag())&&Math.abs(scroll.getScrollY()-Math.min(offset[0],maximum(scroll)))<=2;
            },"Package addition did not settle surviving focus/nonzero scroll");
            SettingsStructureProbe.main(t,()->{
                EditText search=screen.findViewById(R.id.apps_search);ScrollView scroll=screen.findViewById(R.id.apps_scroll);
                require(search.getText().length()==0,"Package addition changed query");
                require(screen.getCurrentFocus()!=null&&selected[0].equals(screen.getCurrentFocus().getTag()),"Package addition lost surviving focus");
                require(Math.abs(scroll.getScrollY()-Math.min(offset[0],maximum(scroll)))<=2,"Package addition lost nonzero scroll");
                String label=catalog(screen.findViewById(R.id.apps_grid)).get(FIXTURE);require(label!=null&&label.toLowerCase(Locale.ROOT).contains("калина"),"INPUT_UNVERIFIED: fixture label contract mismatch");
                require(search.requestFocus(),"Search cannot focus before fixture query");search.setText("Калина");
            });
            awaitCatalog(t,screen,Collections.singleton(FIXTURE),"Fixture Cyrillic query did not isolate actual package");
            SettingsStructureProbe.main(t,()->{
                List<View> tiles=new ArrayList<>();tiles(screen.findViewById(R.id.apps_grid),tiles);require(tiles.size()==1&&FIXTURE.equals(tiles.get(0).getTag())&&tiles.get(0).requestFocus(),"Cannot select actual fixture tile");
            });
            verifyLaunchAndReturn(t,screen,prefs);
            result.putString("catalog_fixture_launch_return","PASS: focused tile OK → foreground fixture → Back → Apps, query and tile focus restored");
            verifySettingsRoundTrip(t, screen, prefs, result);
            require(prefs.edit().putString("favorite_order",new org.json.JSONArray(Collections.singletonList(FIXTURE)).toString()).commit(),
                    "Prepare isolated favorite for Apps return");
            verifyLaunchAndReturn(t,screen,prefs);
            result.putString("catalog_fixture_favorite_return","PASS: favorite opened from Apps returned to Apps, query and tile focus");
            require(prefs.edit().putString("favorite_order",new org.json.JSONArray(Arrays.asList(FIXTURE,selected[0])).toString()).commit(),
                    "Prepare isolated two-favorite menu");
            verifyActionsMenu(t,screen);
            result.putString("catalog_fixture_actions_dpad","PASS: four menu actions reachable with D-pad, Cancel returns focus to fixture tile");
            verifyMissingLaunch(t,screen,prefs,result);
            SettingsStructureProbe.await(t,()->removals.get()>0||witnessFailure.get()!=null,"Actual owned PACKAGE_REMOVED witness missing");
            require(witnessFailure.get()==null&&!removalReplacing.get(),"Owned PACKAGE_REMOVED witness failed or unexpectedly replacing");
            awaitCatalog(t,screen,Collections.emptySet(),"Actual PACKAGE_REMOVED did not empty fixture query");
            SettingsStructureProbe.await(t,()->{
                EditText search=screen.findViewById(R.id.apps_search);return screen.hasWindowFocus()&&screen.findViewById(R.id.nav_apps).isSelected()&&"Калина".contentEquals(search.getText())&&screen.getCurrentFocus()==search;
            },"Selected fixture removal did not preserve Apps/query and focus empty search");
            SettingsStructureProbe.main(t,()->((EditText)screen.findViewById(R.id.apps_search)).setText(""));
            awaitCatalog(t,screen,baseline.keySet(),"Clear after actual removal did not restore exact original catalog");
            verifyInactiveAddAndFilteredRemoval(t, screen, prefs, baseline, additions, removals, result);
            verifyInactiveRemoval(t, screen, prefs, additions, removals, result);
            List<String> favorites = new ArrayList<>(baseline.keySet());
            require(favorites.size() >= 6, "Six installed apps required for catalog interruption during Move");
            int addedBeforeMove = additions.get();
            ReorderProbe.verifyCatalogInterruption(t, screen, prefs, favorites.subList(0, 6), () -> {
                require(!present(t), "Fixture must be absent before the Move catalog update");
                require(shell(t, "pm install -t " + APK).contains("Success"), "Move fixture installation failed");
                SettingsStructureProbe.await(t, () -> additions.get() > addedBeforeMove,
                        "Move did not receive actual PACKAGE_ADDED");
            });
            result.putString("catalog_reorder_interruption",
                    "PASS: actual package addition cancels Move, restores order and focus, held Up is consumed and fresh Up navigates");
            require(witnessFailure.get()==null&&!additionReplacing.get()&&!removalReplacing.get(),
                    "Repeated add/remove witness failed or was a replacement");
            result.putInt("catalog_broadcast_baseline_count",baseline.size());
            result.putInt("catalog_broadcast_added_witnesses",additions.get());result.putInt("catalog_broadcast_removed_witnesses",removals.get());
            result.putBoolean("catalog_broadcast_added_replacing",additionReplacing.get());result.putBoolean("catalog_broadcast_removed_replacing",removalReplacing.get());
            result.putString("catalog_broadcast","PASS: real owned package add/remove, exact actual catalog, surviving scroll/focus, selected removal empty/search fallback");
            result.putString("catalog_broadcast_scope","Actual PACKAGE_ADDED/REMOVED and fixture app launch; programmatic query edits and injected remote keys; no system apps, installer session or soft keyboard claim");
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally {
            Throwable cleanup=null;
            if(witnessRegistered)try{application.unregisterReceiver(witness);}catch(Exception|Error failure){cleanup=failure;}
            if(installAttempted)try{if(present(t))require(shell(t,"pm uninstall "+FIXTURE).contains("Success"),"Owned fixture cleanup uninstall failed");require(!present(t),"Owned fixture remains installed");}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(activity!=null)try{SettingsStructureProbe.finish(t,activity);SettingsStructureProbe.quiescent(t,activity);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            try{if(activity!=null)SettingsStructureProbe.quiescent(t,activity);SettingsStructureProbe.restore(prefs,saved);}catch(Exception|Error failure){if(cleanup==null)cleanup=failure;else cleanup.addSuppressed(failure);}
            if(cleanup!=null){if(primary!=null)primary.addSuppressed(cleanup);else if(cleanup instanceof Error)throw(Error)cleanup;else throw(Exception)cleanup;}
        }
    }

    private static void verifyInactiveRemoval(Instrumentation test, MainActivity screen, SharedPreferences prefs,
            AtomicInteger additions, AtomicInteger removals, Bundle result) throws Exception {
        String query = "Калина";
        SettingsStructureProbe.main(test, () -> {
            EditText search = screen.findViewById(R.id.apps_search);
            require(search.requestFocus(), "Cannot focus search before background-removal query");
            search.setText(query);
        });
        awaitCatalog(test, screen, Collections.emptySet(), "Fixture query must be empty before reinstall");
        int addBefore = additions.get();
        require(shell(test, "pm install -t " + APK).contains("Success"), "Background-removal fixture install failed");
        SettingsStructureProbe.await(test, () -> additions.get() > addBefore, "Missing background-removal PACKAGE_ADDED");
        awaitCatalog(test, screen, Collections.singleton(FIXTURE), "Background-removal query did not pick up fixture");
        SettingsStructureProbe.main(test, () -> {
            List<View> visible = new ArrayList<>();
            tiles(screen.findViewById(R.id.apps_grid), visible);
            require(visible.size() == 1 && FIXTURE.equals(visible.get(0).getTag()) && visible.get(0).requestFocus(),
                    "Cannot focus the fixture before leaving Apps");
        });

        SettingsStructureProbe.select(test, screen, 0);
        View[] detail = new View[1], focused = new View[1];
        SettingsStructureProbe.main(test, () -> {
            require(screen.hasWindowFocus() && screen.findViewById(R.id.nav_settings).isSelected(),
                    "Settings must own the foreground before removal");
            detail[0] = screen.findViewById(R.id.settings_detail);
            focused[0] = screen.getCurrentFocus();
            require(detail[0] != null && focused[0] != null && focused[0].isShown(),
                    "Settings detail and focus must be visible before removal");
            require(prefs.getString("apps", "").contains("\"package\":\"" + FIXTURE + "\""),
                    "Fixture must be in the saved catalog before removal");
        });

        int removeBefore = removals.get();
        require(shell(test, "pm uninstall " + FIXTURE).contains("Success"), "Background-removal fixture uninstall failed");
        SettingsStructureProbe.await(test, () -> removals.get() > removeBefore
                && !prefs.getString("apps", "").contains("\"package\":\"" + FIXTURE + "\""),
                "Catalog did not apply the actual removal while Settings was open");
        catalogWorker(screen).submit(() -> {}).get(20, TimeUnit.SECONDS);
        test.waitForIdleSync();
        SettingsStructureProbe.main(test, () -> {
            require(screen.hasWindowFocus() && screen.findViewById(R.id.nav_settings).isSelected()
                    && screen.findViewById(R.id.settings_detail) == detail[0] && screen.getCurrentFocus() == focused[0],
                    "Background package removal changed the Settings view or focus");
            require(screen.findViewById(R.id.nav_apps).requestFocus(), "Cannot return to Apps after background removal");
        });
        awaitCatalog(test, screen, Collections.emptySet(), "Returning to Apps showed a removed fixture");
        SettingsStructureProbe.main(test, () -> {
            EditText search = screen.findViewById(R.id.apps_search);
            require(query.contentEquals(search.getText()), "Background removal changed the saved query");
            require(screen.getCurrentFocus() == screen.findViewById(R.id.nav_apps),
                    "Returning to Apps lost the rail focus");
        });
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);
        SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() == screen.findViewById(R.id.apps_search),
                "D-pad Right did not enter search after background removal");
        result.putString("catalog_inactive_removal",
                "PASS: actual removal kept Settings view/focus; Apps kept the query with zero matches and D-pad Right entered search");
    }

    private static void verifyInactiveAddAndFilteredRemoval(Instrumentation test, MainActivity screen,
            SharedPreferences prefs, Map<String, String> baseline, AtomicInteger additions,
            AtomicInteger removals, Bundle result) throws Exception {
        SettingsStructureProbe.select(test, screen, 0);
        View[] detail = new View[1], focused = new View[1];
        SettingsStructureProbe.main(test, () -> {
            require(screen.findViewById(R.id.nav_settings).isSelected(), "Settings must own the foreground");
            detail[0] = screen.findViewById(R.id.settings_detail);
            focused[0] = screen.getCurrentFocus();
            require(detail[0] != null && focused[0] != null, "Settings detail and focus must be visible");
        });
        int addBefore = additions.get();
        require(shell(test, "pm install -t " + APK).contains("Success"), "Second owned fixture install failed");
        SettingsStructureProbe.await(test, () -> additions.get() > addBefore
                && prefs.getString("apps", "").contains("\"package\":\"" + FIXTURE + "\""),
                "Background catalog did not observe the actual package addition");
        catalogWorker(screen).submit(() -> {}).get(20, TimeUnit.SECONDS);
        test.waitForIdleSync();
        SettingsStructureProbe.main(test, () -> {
            require(screen.hasWindowFocus() && screen.findViewById(R.id.nav_settings).isSelected()
                    && screen.findViewById(R.id.settings_detail) == detail[0] && screen.getCurrentFocus() == focused[0],
                    "Background package addition changed the Settings view or focus");
            require(screen.findViewById(R.id.nav_apps).requestFocus(), "Could not return from Settings to Apps");
        });
        Set<String> added = new HashSet<>(baseline.keySet());
        added.add(FIXTURE);
        awaitCatalog(test, screen, added, "Returning to Apps did not reveal the added package");

        String[] query = new String[1];
        Set<String> survivors = new HashSet<>();
        SettingsStructureProbe.main(test, () -> {
            Map<String, String> apps = catalog(screen.findViewById(R.id.apps_grid));
            String fixtureLabel = apps.get(FIXTURE).toLowerCase(Locale.ROOT);
            for (String letter : new String[]{"a", "r", "e", "c", "p", "q", "а", "и"}) {
                if (!fixtureLabel.contains(letter)) continue;
                survivors.clear();
                baseline.forEach((pkg, label) -> {
                    if (label.toLowerCase(Locale.ROOT).contains(letter)) survivors.add(pkg);
                });
                if (survivors.size() >= 2) { query[0] = letter; break; }
            }
            require(query[0] != null, "INPUT_UNVERIFIED: need two surviving matches for the fixture query");
            EditText search = screen.findViewById(R.id.apps_search);
            require(search.requestFocus(), "Cannot enter the shared fixture query");
            search.setText(query[0]);
        });
        Set<String> matching = new HashSet<>(survivors);
        matching.add(FIXTURE);
        awaitCatalog(test, screen, matching, "Fixture and surviving matches must share the same query");
        SettingsStructureProbe.main(test, () -> {
            List<View> visible = new ArrayList<>();
            tiles(screen.findViewById(R.id.apps_grid), visible);
            View fixture = visible.stream().filter(tile -> FIXTURE.equals(tile.getTag())).findFirst().orElse(null);
            require(fixture != null && fixture.requestFocus(), "Cannot focus the fixture among several results");
        });
        int removeBefore = removals.get();
        require(shell(test, "pm uninstall " + FIXTURE).contains("Success"), "Second owned fixture removal failed");
        SettingsStructureProbe.await(test, () -> removals.get() > removeBefore, "Missing second PACKAGE_REMOVED witness");
        awaitCatalog(test, screen, survivors, "Actual removal did not keep the surviving matches");
        SettingsStructureProbe.await(test, () -> {
            View focus = screen.getCurrentFocus();
            EditText search = screen.findViewById(R.id.apps_search);
            return focus != null && search != null && query[0].contentEquals(search.getText())
                    && survivors.contains(focus.getTag()) && focus.isShown();
        }, "Removal with other matches did not keep query and focus a surviving result");
        result.putInt("catalog_surviving_filtered_results", survivors.size());
        result.putString("catalog_inactive_add_filtered_removal",
                "PASS: actual package addition kept Settings tree/focus, removal among matches retained query and focused a surviving tile");
    }

    private static void verifyArtworkFallback(Context context, Bundle result) {
        final int iconColor = 0xff347d92;
        final int defaultColor = 0xff6e4d87;
        Rect original = new Rect(5, 7, 17, 19);
        Drawable brokenBanner = new UnrenderableArtwork(32);
        Drawable brokenIcon = new UnrenderableArtwork(16);
        brokenBanner.setBounds(original);
        brokenIcon.setBounds(original);

        Drawable.ConstantState recovered = AppArtwork.load(context.getResources(), () -> brokenBanner,
                () -> new ColorDrawable(iconColor), () -> {
                    throw new AssertionError("Valid app icon should avoid the default");
                });
        Drawable image = recovered.newDrawable(context.getResources());
        require(image instanceof BitmapDrawable && ((BitmapDrawable) image).getBitmap().getPixel(0, 0) == iconColor,
                "Malformed banner did not fall back to the actual app icon");
        require(original.equals(brokenBanner.getBounds()), "Failed banner draw changed source bounds");

        recovered = AppArtwork.load(context.getResources(), () -> {
            throw new android.content.pm.PackageManager.NameNotFoundException("Fixture has no banner");
        }, () -> brokenIcon, () -> new ColorDrawable(defaultColor));
        image = recovered.newDrawable(context.getResources());
        require(image instanceof ColorDrawable && ((ColorDrawable) image).getColor() == defaultColor,
                "Malformed app icon did not fall back to the default");
        require(original.equals(brokenIcon.getBounds()), "Failed icon draw changed source bounds");
        result.putString("catalog_artwork_fallback",
                "PASS: throwing banner and icon draw, valid icon/default fallback and source bounds restoration");
    }

    private static final class UnrenderableArtwork extends ColorDrawable {
        private final int width;

        UnrenderableArtwork(int width) { this.width = width; }

        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return 16; }
        @Override public void draw(Canvas canvas) {
            throw new IllegalArgumentException("Injected third-party drawable failure");
        }
    }
    private static ExecutorService catalogWorker(MainActivity screen) throws Exception {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("worker");field.setAccessible(true);
        return (ExecutorService)field.get(screen);
    }
    private static void verifyMissingLaunch(Instrumentation test,MainActivity screen,SharedPreferences prefs,Bundle result)throws Exception {
        ExecutorService worker=catalogWorker(screen);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> gate=worker.submit(()->{
            entered.countDown();
            try{require(release.await(45,TimeUnit.SECONDS),"Missing-launch worker gate timeout");}
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new AssertionError(error);}
        });
        Throwable primary=null;
        try {
            require(entered.await(15,TimeUnit.SECONDS),"Catalog worker gate unavailable");
            final View[] tile={null};final int[] scroll={0};final String[] query={null};final Object[] pending={null};
            final String sentinel="agency.carpediem.tv.testfixture.not_launched";
            require(prefs.edit().putString("last_opened_package",sentinel).commit(),"Prepare distinct launch-history sentinel");
            SettingsStructureProbe.main(test,()->{
                tile[0]=screen.getCurrentFocus();require(tile[0]!=null&&FIXTURE.equals(tile[0].getTag()),"Missing-launch fixture tile not focused");
                scroll[0]=((ScrollView)screen.findViewById(R.id.apps_scroll)).getScrollY();
                query[0]=((EditText)screen.findViewById(R.id.apps_search)).getText().toString();
                try{java.lang.reflect.Field awaiting=MainActivity.class.getDeclaredField("launchedAppAwaitingReturn");awaiting.setAccessible(true);pending[0]=awaiting.get(screen);}catch(Exception error){throw new AssertionError(error);}
            });
            require(shell(test,"pm uninstall "+FIXTURE).contains("Success"),"Owned fixture removal failed");
            require(!present(test)&&screen.getPackageManager().getLeanbackLaunchIntentForPackage(FIXTURE)==null
                    &&screen.getPackageManager().getLaunchIntentForPackage(FIXTURE)==null,"Actual removed package still resolves");
            SettingsStructureProbe.main(test,()->require(tile[0].isAttachedToWindow()&&tile[0].isShown()&&screen.getCurrentFocus()==tile[0],"Worker gate did not retain stale tile"));
            boolean toast=false;
            try {
                android.view.accessibility.AccessibilityEvent event=test.getUiAutomation().executeAndWaitForEvent(
                        ()->test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER),
                        e->e.getEventType()==android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                                &&e.getText().contains(screen.getString(R.string.app_missing)),1500);
                event.recycle();toast=true;
            }catch(TimeoutException unavailable){/* Toast accessibility delivery is optional; the key was still injected exactly once. */}
            test.waitForIdleSync();
            SettingsStructureProbe.main(test,()->{
                require(screen.hasWindowFocus()&&screen.findViewById(R.id.nav_apps).isSelected()&&screen.getCurrentFocus()==tile[0],"Missing launch changed foreground/page/tile focus");
                require(query[0].contentEquals(((EditText)screen.findViewById(R.id.apps_search)).getText())
                        &&scroll[0]==((ScrollView)screen.findViewById(R.id.apps_scroll)).getScrollY(),"Missing launch changed query or scroll");
                require(sentinel.equals(prefs.getString("last_opened_package",null)),"Missing launch wrote successful-launch history");
                try{java.lang.reflect.Field awaiting=MainActivity.class.getDeclaredField("launchedAppAwaitingReturn");awaiting.setAccessible(true);require(Objects.equals(pending[0],awaiting.get(screen)),"Missing launch changed return tracking");}catch(Exception error){throw new AssertionError(error);}
            });
            result.putString("catalog_fixture_missing_launch","PASS: actual removed package resolves null, stale selected tile real D-pad OK preserves foreground/focus/query/scroll/history before catalog refresh");
            result.putString("catalog_fixture_missing_toast",toast?"OBSERVED: actual app_missing toast accessibility event":"UNVERIFIED: platform did not expose toast event; state/focus assertions passed");
        }catch(Exception|Error error){primary=error;throw error;}
        finally {
            release.countDown();
            try{gate.get(10,TimeUnit.SECONDS);worker.submit(()->{}).get(20,TimeUnit.SECONDS);test.waitForIdleSync();}
            catch(Exception|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }

    private static void verifyActionsMenu(Instrumentation test,MainActivity screen)throws Exception {
        SettingsStructureProbe.main(test,()->{
            View tile=screen.getCurrentFocus();
            require(tile!=null&&FIXTURE.equals(tile.getTag())&&tile.performLongClick(),"Fixture actions menu did not open");
        });
        int[] actions={R.string.remove_favorite,R.string.move_app,R.string.app_info,R.string.cancel};
        UiAutomation automation=test.getUiAutomation();
        for(int index=0;index<actions.length;index++) {
            if(index>0)test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            String label=screen.getString(actions[index]);
            long deadline=SystemClock.uptimeMillis()+3000;
            boolean reached=false;
            do {
                AccessibilityNodeInfo root=automation.getRootInActiveWindow();
                if(root!=null){
                    try {
                        AccessibilityNodeInfo focus=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                        if(focus!=null){
                            try{reached=android.text.TextUtils.equals(screen.getPackageName(),focus.getPackageName())
                                    &&android.text.TextUtils.equals(label,focus.getText());}
                            finally{focus.recycle();}
                        }
                    }finally{root.recycle();}
                }
                if(!reached)SystemClock.sleep(40);
            }while(!reached&&SystemClock.uptimeMillis()<deadline);
            require(reached,"D-pad focus did not reach app action "+label);
        }
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test,()->screen.hasWindowFocus()&&screen.findViewById(R.id.nav_apps).isSelected()
                &&screen.getCurrentFocus()!=null&&FIXTURE.equals(screen.getCurrentFocus().getTag()),"App actions Cancel did not restore the originating tile");
    }
    private static void verifySettingsRoundTrip(Instrumentation test, MainActivity screen, SharedPreferences prefs,
                                                Bundle result) throws Exception {
        int[] scrollOffset = {-1};
        SettingsStructureProbe.main(test, () -> {
            ScrollView scroll = screen.findViewById(R.id.apps_scroll);
            scrollOffset[0] = scroll.getScrollY();
        });
        int left = KeyEvent.KEYCODE_DPAD_LEFT;
        int right = KeyEvent.KEYCODE_DPAD_RIGHT;
        int down = KeyEvent.KEYCODE_DPAD_DOWN;
        test.sendKeyDownUpSync(left);
        SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.nav_apps).isFocused(),
                "Left from the result did not reach the Apps rail");
        test.sendKeyDownUpSync(down);
        SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.nav_settings).isFocused(),
                "Down did not reach the Settings rail");
        test.sendKeyDownUpSync(right);
        SettingsStructureProbe.await(test, () -> {
            View categories = screen.findViewById(R.id.settings_categories);
            return categories instanceof ViewGroup && ((ViewGroup) categories).getChildAt(0).isFocused();
        }, "Right did not reach Appearance");
        test.sendKeyDownUpSync(right);
        SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() != null
                && screen.getCurrentFocus().getTag() instanceof WallpaperCatalog.Entry, "Right did not enter wallpaper choices");
        for (int control : new int[]{R.id.dynamic_wallpapers, R.id.wallpaper_dim, R.id.dim_reset, R.id.animations}) {
            test.sendKeyDownUpSync(down);
            SettingsStructureProbe.await(test, () -> screen.getCurrentFocus() != null
                    && screen.getCurrentFocus().getId() == control, "Down did not reach " + screen.getResources().getResourceEntryName(control));
        }
        boolean before = prefs.getBoolean("animations", true);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        SettingsStructureProbe.await(test, () -> {
            Switch animations = screen.findViewById(R.id.animations);
            return animations != null && animations.isFocused() && animations.isChecked() != before
                    && prefs.getBoolean("animations", true) != before;
        }, "OK did not persist the animation change");
        test.sendKeyDownUpSync(left);
        test.sendKeyDownUpSync(left);
        SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.nav_settings).isFocused(),
                "Left twice did not return from the animation control to the Settings rail");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
        SettingsStructureProbe.await(test, () -> screen.findViewById(R.id.nav_apps).isFocused(),
                "Up did not return to the Apps rail");
        awaitCatalog(test, screen, Collections.singleton(FIXTURE), "Returning from Settings changed the matching package");
        test.sendKeyDownUpSync(right);
        SettingsStructureProbe.await(test, () -> {
            EditText search = screen.findViewById(R.id.apps_search);
            ScrollView scroll = screen.findViewById(R.id.apps_scroll);
            View focused = screen.getCurrentFocus();
            return search != null && "Калина".contentEquals(search.getText()) && scroll != null
                    && scroll.getScrollY() == scrollOffset[0] && focused != null && FIXTURE.equals(focused.getTag());
        }, "Right after returning from Settings lost the query, scroll or focused result");
        result.putInt("catalog_fixture_settings_apps_scroll_px", scrollOffset[0]);
        result.putString("catalog_fixture_settings_round_trip",
                "PASS: Back from launched result → change animations by D-pad → Apps query, result, scroll and tile focus");
    }

    private static void verifyLaunchAndReturn(Instrumentation test,MainActivity screen,SharedPreferences prefs)throws Exception {
        SettingsStructureProbe.await(test,()->screen.hasWindowFocus()
                &&screen.getCurrentFocus()!=null&&FIXTURE.equals(screen.getCurrentFocus().getTag()),"Fixture not ready for DPAD OK");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        UiAutomation automation=test.getUiAutomation();
        long deadline=SystemClock.uptimeMillis()+8000;
        boolean foreground=false;
        while(SystemClock.uptimeMillis()<deadline&&!foreground){
            AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            if(root!=null){
                try{foreground=android.text.TextUtils.equals(FIXTURE,root.getPackageName())
                        &&!root.findAccessibilityNodeInfosByText("Carpe QA Калина").isEmpty();}
                finally{root.recycle();}
            }
            if(!foreground)SystemClock.sleep(50);
        }
        require(foreground,"DPAD OK did not show the owned fixture Activity");
        require(FIXTURE.equals(prefs.getString("last_opened_package",null)),"Launch did not persist the opened fixture");
        long now=SystemClock.uptimeMillis();
        require(automation.injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true),"Fixture Back down rejected");
        require(automation.injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true),"Fixture Back up rejected");
        SettingsStructureProbe.await(test,()->{
            EditText search=screen.findViewById(R.id.apps_search);
            View focus=screen.getCurrentFocus();
            return screen.hasWindowFocus()&&screen.findViewById(R.id.nav_apps).isSelected()
                    &&search!=null&&"Калина".contentEquals(search.getText())
                    &&focus!=null&&FIXTURE.equals(focus.getTag());
        },"Back from owned fixture did not restore Apps/query/tile focus");
    }
    private static void awaitCatalog(Instrumentation t,MainActivity screen,Set<String> expected,String message)throws Exception {
        SettingsStructureProbe.await(t,()->{ViewGroup grid=screen.findViewById(R.id.apps_grid);return screen.findViewById(R.id.nav_apps).isSelected()&&grid!=null&&grid.isShown()&&catalog(grid).keySet().equals(expected);},message);
    }
    private static boolean present(Instrumentation t)throws Exception{return shell(t,"pm path "+FIXTURE).contains("package:");}
    private static String shell(Instrumentation t,String command)throws Exception {
        ExecutorService reader=Executors.newSingleThreadExecutor();ParcelFileDescriptor pipe=t.getUiAutomation().executeShellCommand(command);
        try {
            Future<String> result=reader.submit(()->{try(ParcelFileDescriptor.AutoCloseInputStream input=new ParcelFileDescriptor.AutoCloseInputStream(pipe);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] bytes=new byte[1024];int count;while((count=input.read(bytes))!=-1){if(output.size()+count>16384)throw new IllegalStateException("Bounded fixture shell output exceeded");output.write(bytes,0,count);}return output.toString("UTF-8");}});
            return result.get(90,TimeUnit.SECONDS);
        }finally{pipe.close();reader.shutdownNow();require(reader.awaitTermination(5,TimeUnit.SECONDS),"Fixture shell reader did not stop");}
    }
    private static int maximum(ScrollView scroll){return scroll.getChildCount()==0?0:Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());}
    private static Map<String,String> catalog(ViewGroup grid){Map<String,String> items=new LinkedHashMap<>();List<View> tiles=new ArrayList<>();tiles(grid,tiles);for(View tile:tiles){TextView name=tile.findViewById(R.id.app_name);require(name!=null,"Actual tile label missing");require(items.put((String)tile.getTag(),name.getText().toString())==null,"Duplicate actual package");}return items;}
    private static void tiles(ViewGroup root,List<View> result){if(root==null)return;for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);if(child.isFocusable()&&child.getTag() instanceof String)result.add(child);else if(child instanceof ViewGroup)tiles((ViewGroup)child,result);}}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}

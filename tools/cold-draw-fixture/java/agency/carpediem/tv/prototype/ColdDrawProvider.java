package agency.carpediem.tv.prototype;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** Validation-only observer installed before the first Activity is created. */
public final class ColdDrawProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        if (!"agency.carpediem.tv.validation.publicpreview".equals(getContext().getPackageName())) {
            throw new IllegalStateException("Cold draw fixture requires the isolated preview");
        }
        Application application = (Application) getContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
                if (activity instanceof MainActivity) observePages(activity);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        return true;
    }

    private static void observePages(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            private final Rect visibleBounds = new Rect();
            private boolean homeReported;
            private boolean appsReported;

            @Override
            public void onDraw() {
                if (appsReported || !activity.hasWindowFocus()) return;
                View home = activity.findViewById(R.id.nav_home);
                View page = activity.findViewById(R.id.content_host);
                if (!homeReported && home != null && home.isSelected() && home.hasFocus()
                        && home.isEnabled() && visible(home) && visible(page) && hasVisibleTile(page)) {
                    homeReported = true;
                    Log.i("CarpeColdDraw", "home_draw " + SystemClock.elapsedRealtime());
                }
                View rail = activity.findViewById(R.id.nav_apps);
                if (rail == null || !rail.isSelected() || !rail.hasFocus()) return;
                View search = activity.findViewById(R.id.apps_search);
                View grid = activity.findViewById(R.id.apps_grid);
                if (!visible(search) || !search.isEnabled() || !visible(grid) || !hasVisibleTile(grid)) return;
                long drawUptimeMs = SystemClock.elapsedRealtime();
                appsReported = true;
                Log.i("CarpeColdDraw", "apps_draw " + drawUptimeMs);
                decor.post(() -> {
                    if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnDrawListener(this);
                });
            }

            private boolean hasVisibleTile(View view) {
                if (view.isEnabled() && view.isFocusable() && view.isClickable()
                        && view.findViewById(R.id.app_name) != null && visible(view)) return true;
                if (view instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) view;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        if (hasVisibleTile(group.getChildAt(i))) return true;
                    }
                }
                return false;
            }

            private boolean visible(View view) {
                if (view == null || !view.isShown() || !view.getGlobalVisibleRect(visibleBounds)) return false;
                for (View ancestor = view; ancestor != null;) {
                    if (ancestor.getAlpha() <= 0f) return false;
                    ancestor = ancestor.getParent() instanceof View ? (View) ancestor.getParent() : null;
                }
                return true;
            }
        });
    }

    @Override public Cursor query(Uri uri, String[] columns, String selection, String[] values, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues data, String selection, String[] values) { throw new UnsupportedOperationException(); }
}

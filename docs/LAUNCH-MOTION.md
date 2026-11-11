# App launch motion and Home focus — 0.2.4

The launcher uses Android's [ActivityOptions.makeScaleUpAnimation](https://developer.android.com/reference/android/app/ActivityOptions#makeScaleUpAnimation(android.view.View,int,int,int,int)) with the visible app artwork rectangle. Android animates the destination window from that origin. The app is launched immediately: there is no artificial wait, screen capture, full-screen bitmap, overlay, extra permission or runtime dependency.

Artwork has alpha-only pressed feedback (70 ms ease-in; 140 ms ease-out). It does not compete with the existing focused scale animation or move labels. The launcher animation setting and system animator state are respected. When disabled, no custom options are supplied and the launch Intent requests no transition animation. Missing/invisible origins fall back to an ordinary launch.

The final window transition is controlled by Android and can differ with firmware, an app's own splash screen or task behavior. It is not a promise of an identical animation in every third-party app.

## Home navigation

- Up from any favorite in the first row goes to the Wi-Fi icon in the top toolbar.
- Left/Right move between Wi-Fi and Settings. Up stays on the toolbar.
- Down from either toolbar icon returns to the last focused favorite.
- Sidebar navigation is entered explicitly with Left; Up from a favorite no longer chooses focus-activated sidebar Settings by geometry.
- App-list refreshes preserve toolbar focus and reconnect Down to a valid remaining tile.

## Device validation — 2026-11-03

Xiaomi MiTV_AFMU1 / Android TV 14, 1920×1080 UI, version code 11.

- Debug and instrumentation APK builds and `lintDebug` passed.
- `HomeFocusProbe` passed every first-row favorite: Up, bounded toolbar navigation, Down to the original tile, visible launch origin and disabled-motion fallback.
- Actual remote-key launch of Aerial Views was recorded; frames showed the destination window expanding over the library. Back returned focus to the originating tile with normal artwork opacity.
- TV Bro opened successfully from the second Home favorite after an Up/Down round trip through the toolbar.
- No global animation scales were changed. Original favorite order, language, dimming and dynamic wallpaper mode were retained.

The prior APK, preferences, source snapshot, recordings and result logs are kept in the private local checkpoint `mistick-customization/universal-workspace/checkpoints/app-launch-motion-20261103`. APK backups are not full system dumps. The launcher requests no new accessibility capabilities for app launch motion.

After instrumentation, the OEM accessibility manager kept the previously authorized Home bridge in its crashed/unbound set. Re-binding that same service (preserving any other enabled services) cleared the state. Physical Home from Aerial Views was then verified to return to Carpe TV. Future device test runs must verify the bound service and actual Home behavior after instrumentation; an enabled setting alone is insufficient.

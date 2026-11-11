# Architecture

Single application module, Java 17 source and Android framework XML Views.
minSdk 26, target/compileSdk 34. No CleanYourTV source or runtime library is used.
The application ID is intentionally unchanged during private development.

- `MainActivity`: app discovery, worker-backed metadata/artwork caches, three pages,
  favorites, search and settings. It currently contains several responsibilities;
  extracting repositories and page controllers is future work, not completed work.
- `AppArtwork`: prepares bounded TV artwork from installed app resources.
- `FocusMotion`: interruptible card focus and ring animation.
- `PageMotion` / `PageContent`: at most three cached page trees; inactive pages are
  invisible and blocked from focus; new input cancels the previous animation.
- `BackgroundMotion` / `WallpaperBlur`: background crossfade and worker-generated,
  disk-cached blur. No continuous blur computation.
- `BlueAtmosphere`: optional slow gradient-opacity animation over the blue wallpaper,
  suspended when the launcher is paused or loses window focus. No video download.
- `HomeChoice`: user-selected Android Home role. The stock launcher remains installed.
- `HomeBridgeService`: optional user-enabled Accessibility fallback on firmware that
  opens Google TV despite a different Home role. It listens only for Google TV
  window-state events, requests no screen-content access and does not filter keys.
- `IdleTimer` / `Screensaver`: one foreground-only inactivity callback, suspended on pause or focus loss. Optional Aerial Views preview launch is guarded by explicit package visibility and availability; no external media is bundled or fetched.
- `ScreensaverActivity`: offline RGB_565 photo decoding on a single worker, at most two displayed layers and one pending decode; advances every minute only while resumed. No wake lock or sleep override. Volume keys remain available; other remote input dismisses it without opening an app.
- `SaverCatalog` / `SaverSync` / `SaverCache`: daily public catalog refresh, one foreground-scoped transfer worker, byte/file-count limits, free-space reserve, validation, atomic publication and pinned LRU cache. No always-on download service. `VideoView` plays validated local files with no audio focus.
- `NavigationProbe`: separate test APK; never packaged into the launcher.

Data lives in app-private preferences/cache. Installed apps are obtained through
PackageManager; package changes refresh the list. Package visibility uses intent
queries rather than QUERY_ALL_PACKAGES. The updater requests INTERNET and REQUEST_INSTALL_PACKAGES. The manifest requests no
microphone, camera, location or broad storage permissions. The public build
contains an optional Accessibility service for the OEM Home fallback. It is never
enabled automatically; its install and Play Protect behavior must be rechecked
before a release.

Build dependencies: Gradle 9.8.2, Android Gradle Plugin 9.5.0, JDK 17, compile/target SDK 34 (Build Tools 36). Built-in Kotlin is disabled for this Java-only application to avoid adding Kotlin runtime dependencies.
Wrapper distribution SHA-256 is pinned. GitHub Actions are pinned to commit SHAs.
CI and release workflows use different, minimal token permissions.

AppLanguage provides app-only English/Ukrainian selection on API 26+, with resources
in values and values-uk. Third-party labels and external system UI remain device-owned.
Updater components are documented in UPDATES.md; they use native DownloadManager
and PackageInstaller with explicit user confirmation.

# Changelog

## 0.2.7 — private internal preview · 2026-11-03

- Includes all merged launcher improvements through PR #12, including wallpapers, screensaver downloads and remote app reordering.
- Compact Apps & background settings with actual package state and RAM/storage totals; package refresh preserves the settings screen and focus.
- Optional authorized Shizuku controls for Xiaomi analytics, with confirmations, saved restoration state and enable-only stock launcher recovery.
- Sleep timer presets, a minimal dialog and mm:ss countdown in Home, library and settings headers. Requires running Shizuku and exact-alarm access on Android 12+.
- Updated English/Ukrainian README, tested-device list, setup/rollback guides, screenshots and real-device walkthrough videos.
- Known issue: reported startup failure after reboot remains under investigation. Full Google TV package disable is unsupported on the tested firmware. Remote-button remapping is not implemented.
- Internal debug signature retained for update compatibility; no permanent update feed or public stable signing channel is configured.

## 0.2.6 — development

- Optional automatic Commons/NASA screensaver downloads, with daily catalog refresh only during an active built-in screensaver session.
- Up to three new items per session, at most 24 cached items / 500 MiB, and a 1 GiB free-space reserve (512 MiB selectable per device).
- Complete validated downloads are published atomically; truncated, oversized, cancelled and invalid files are removed. LRU cleanup preserves pinned playback and the most recent known-good item.
- Silent native playback of downloaded video; photo/video attribution, cache usage, reserve selection, credits and cache clearing in Settings.
- No added runtime libraries, account keys, background service, wake lock or permission changes.

## 0.2.5 — development

- Foreground-only screensaver timer: Off or 1, 3, 5, 10, 15, 30 minutes; defaults to 5 minutes.
- Built-in silent offline photo slideshow with a clock, minute-by-minute photo changes and remote dismissal.
- Optional Aerial Views provider via its documented preview Activity, with offline fallback when unavailable.
- Screensaver settings in English/Ukrainian, matching launcher controls; link to Android screensaver settings.
- No new permissions, network media downloads, background service, wake lock or automatic system timeout changes.

## 0.2.4 — development

- Launch apps with a native scale-up transition anchored to the visible app artwork, without added launch delays or screenshot allocation.
- Subtle pressed feedback on artwork; respects the launcher animation setting and system animator state.
- First-row Home favorites navigate Up to the toolbar; Down returns to the last focused tile.
- Explicit toolbar boundaries and toolbar focus preservation when the app list refreshes.

## 0.2.3 — development

- Optional dynamic wallpapers: a photo change every 30 minutes, with persistent timing.
- Foreground-only scheduling; overdue changes apply once on return, without background wakeups.
- Two selected HUD-free S.T.A.L.K.E.R. references: Duga and Pripyat (debug only).

## 0.2.2 — development

- Recommended wallpaper dimming of 60%, with a one-action restore button.
- Restoring immediately updates the slider, percentage, saved value and Home background.

## 0.2.1 — development

- Three offline CC0 photo wallpapers with source and author records.
- Horizontal wallpaper picker with names, selection checks and D-pad scrolling.
- Per-wallpaper cached blur, background decoding and lightweight thumbnails.
- Private debug-only S.T.A.L.K.E.R. 2 screenshot; excluded from release resources.
- Sidebar system power entry (sleep/restart options depend on firmware).

## 0.2.0 — private internal release

- App renamed to Carpe TV while retaining package/signature compatibility.
- English and Ukrainian resources with an app-only language selector and focus restoration.
- Background downloads through native DownloadManager and user-confirmed APK installation.
- Version/package/SDK/size/checksum/signer validation, cancellation/retry and private checkpoints.
- Recovery of interrupted checks, permission guidance and illustrated installation docs.
- No permanent public update channel enabled; private GitHub credentials are not bundled.

## 0.1.0 — private development baseline

- Standalone launcher with favorites, cached app library, search and settings.
- Montserrat, wide artwork cards and shared D-pad focus behavior.
- Interruptible card and sidebar page transitions; cached page views and wallpaper blur.
- Optional Home selection and limited Google TV Accessibility bridge.
- Private repository baseline, actual device screenshots and draft release process.

The previous personal launcher and its integrations are not part of this release.

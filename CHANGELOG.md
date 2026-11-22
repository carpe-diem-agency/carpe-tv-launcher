# Changelog

## 0.2.66

- Prepare the public GitHub update channel.

## 0.2.65 — device preview

- Let the library header, search and clock grow to fit larger text.

## 0.2.64 — device preview

- Focus the install action when the Updates screen opens with a verified APK already downloaded.

## 0.2.63 — device preview

- Make the ready-to-install action and its instructions readable from farther away.
- Give the Updates screen a direct D-pad route to Back.

## 0.2.62 — device preview

- Draw the blue wallpaper in one opaque layer when animations are disabled.

## 0.2.61 — device preview

- Keep a decoded screensaver photo fully visible when a pause interrupts its fade.

## 0.2.60 — device preview

- Expose update status changes to screen readers and avoid repeating unchanged messages on each refresh.

## 0.2.59 — device preview

- Keep the app focus outline visible against light and dark icons on Home and in the Apps library.

## 0.2.58 — device preview

- Keep focus on the timer at the right edge of the header, including during an active countdown.

## 0.2.57 — device preview

- Keep Home text legible on bright photo wallpapers at low dimming levels.

## 0.2.56 — device preview

- Preserve the saved favorite order and focus when an app installation interrupts reordering.

## 0.2.55 — device preview

- Preserve library context when Android refuses to launch an app.

## 0.2.54 — device preview

- Return focus to search when retrying a failed catalog load succeeds.

## 0.2.53 — device preview

- Give the diagnostic report full width and show the QR first in narrow dialogs.

## 0.2.52 — device preview

- Let settings buttons and the screensaver download switch grow with large text. Scroll the categories into view in short layouts.

## 0.2.51 — device preview

- Keep D-pad focus on the final control of a settings panel when pressing Down and make the route to the header reliable in long panels.

## 0.2.50 — device preview

- Close settings dialogs when Android recreates the launcher and restore focus to the underlying action.

## 0.2.49 — device preview

- Skip damaged cached screensaver photos and continue with the next available image.

## 0.2.48 — device preview

- Restore focus to the selected Home favorite when Android recreates Carpe TV after stopping its process.

## 0.2.47 — device preview

- Keep language and dimming-reset buttons readable with large text.

## 0.2.46 — device preview

- Keep dimming and sound percentages readable with large text without shifting the sliders as values change.

## 0.2.45 — device preview

- Keep a shorter second row of favorites reachable with the remote after saving a new order.

## 0.2.44 — device preview

- Reduce the blue wallpaper's travel and keep the glow continuous at the screen edges.
- Keep sleep timer buttons readable and reachable in compact windows with larger text.

## 0.2.43 — device preview

- Use the animated blue wallpaper when no wallpaper has been chosen. Keep existing wallpaper selections.

## 0.2.42 — device preview

- Add a slow-moving blue glow to the blue wallpaper. The Animations switch also controls this motion.

## 0.2.41 — device preview

- Clean up an interrupted update download even if Android has redirected it and has yet to create its file.

## 0.2.40 — device preview

- Evict broken cached videos and move on to the next screensaver slide.

## 0.2.39 — device preview

- Restore the app search without automatically reopening the keyboard.

## 0.2.38 — device preview

- Explain when the update server times out and offer another check.

## 0.2.37 — device preview

- Fall back to the app icon or the default icon when a banner cannot be loaded.
- Keep the app library available when artwork fails.

## 0.2.36 — device preview

- Honor the launcher animation setting in the sleep timer dialog.

## 0.2.35 — device preview

- Coalesce app catalog scans when Android reports several package changes.

## 0.2.34 — device preview · 2026-11-21

- Size Feedback QR codes to the available dialog viewport so the whole code
  can be scanned in shorter windows.

## 0.2.33 — device preview · 2026-11-21

- Move remote focus to the next update action when the focused button
  disappears during checking, downloading or verification.

## 0.2.32 — device preview · 2026-11-21

- Keep the launcher responsive when Android cannot resolve a selected app.
  Show the launch error and record launch history only after a successful start.

## 0.2.24 — device preview · 2026-11-15

- Allow Appearance switches to grow with larger text while retaining their existing 56dp minimum height, padding and remote-focus routes.
- Increment Android versionCode to 31 for the next in-place device update.

## 0.2.23 — device preview · 2026-11-15

- Present code, third-party and font licenses in the shared Montserrat settings panel with bounded scrolling, the existing modal palette and a consistent Close control.
- Preserve notice text, motion preferences and return focus to the originating Authors action.
- Increment Android versionCode to 30 for the next in-place device update.

## 0.2.22 — device preview · 2026-11-15

- Bind installer callbacks to their original download identity so delayed results cannot delete or overwrite a newer download.
- Clear terminal installer-session identity during callback handling and recovery; legacy unbound sessions cannot delete download artifacts.
- Increment Android versionCode to 29 for the next in-place device update.

## 0.2.21 — device preview · 2026-11-15

- Guard updater download and verification results with operation identities so cancellation rejects stale success, failure and cleanup callbacks.
- Resume persisted download identities and ignore completion events for older downloads; keep APK verification outside the publication lock.
- Increment Android versionCode to 28 for the next in-place device update.

## 0.2.20 — device preview · 2026-11-15

- Back up the actual Main Activity preferences alongside language/screensaver, interface sounds and the automatic-update preference in separate namespaces with explicit value types.
- Exclude transient installation, download, alarm and system-session state from the portable settings export.
- Increment Android versionCode to 27 for the next in-place device update.

## 0.2.19 — device preview · 2026-11-15

- Validate bounded PNG chunk structure and CRCs before accepting wallpaper cache files, regenerating truncated or corrupted entries.
- Preserve pending library scroll and tile focus across another Activity recreation before the catalog and layout finish restoring.
- Keep inactive library restoration from taking focus from Home or Settings.
- Increment Android versionCode to 26 for the next in-place device update.

## 0.2.18 — device preview · 2026-11-15

- Centralize exact existing secondary/link/body text colors, modal surface color and shared control radius/stroke dimensions in design tokens without changing rendered values or interaction timing.
- Increment Android versionCode to 25 for the next in-place device update.

## 0.2.17 — device preview · 2026-11-15

- Publish blurred wallpaper cache files through unique per-writer temporary files and atomic replacement after closing the output stream, preventing overlapping Activity workers from corrupting the shared cache.
- Continue displaying the generated bitmap when optional cache publication fails, preserving any existing complete cache file.
- Include the pending 0.2.16 touch-to-remote focus handoff and increment Android versionCode to 24 for the next in-place device update.

## 0.2.16 — device preview · 2026-11-15

- Share the guarded touch-to-remote focus handoff across the navigation rail, Settings category activation and Main action buttons, including Authors entry and Back.
- Explicitly refresh category-to-detail focus links after touch activation so the next D-pad key retains the selected context.
- Increment Android versionCode to 23 for the next in-place device update.

## 0.2.15 — device preview · 2026-11-15

- Hand off touch activation of a navigation-rail item to remote focus before opening its page, preventing the first D-pad press from switching back to Home.
- Suppress transient default-focus navigation during that handoff; keep existing D-pad and OK routes unchanged.
- Increment Android versionCode to 22 for the next in-place device update.

## 0.2.14 — device preview · 2026-11-15

- Preserve the inactive app-library scroll position when a catalog update invalidates its cached page while Home or Settings is open.
- Keep the search query, focus behavior and outstanding restore state unchanged.
- Increment Android versionCode to 21 for the next in-place device update.

## 0.2.13 — device preview · 2026-11-14

- Soften app-library press feedback with a smaller opacity change, a 110 ms press and a 190 ms recovery; keep app activation immediate and Home feedback unchanged.
- Respect disabled animations and use interruptible transitions from the current opacity.
- Increment Android versionCode to 20 for the next in-place device update.

## 0.2.12 — device preview · 2026-11-14

- Reuse detached library cards across query changes with a per-grid pool capped at 32 cards and 8 MiB of artwork; invalidate stale package/label entries without recycling shared bitmaps.
- Tighten Feedback dialog spacing so its actions fit alongside the full-size QR on the tested 1080p viewport.
- Increment Android versionCode to 19 for the next in-place device update.

## 0.2.11 — device preview · 2026-11-14

- Explain that the diagnostic QR contains copyable text rather than a web link.
- Add a direct Report a bug action from diagnostics to the GitHub feedback QR, retaining the original report and returning focus to Device diagnostics.
- Explain that GitHub issues are public and the included diagnostics should be reviewed before submission.
- Increment Android versionCode to 18 for an in-place device update.

## 0.2.10 — device preview · 2026-11-14

- Present RAM and storage with compact localized used/total GB figures and an adaptive MB/GB free-space line beneath the colored usage bar.
- Remove the redundant “used” label and long integer MB capacity figures from both resource blocks.
- Use a television icon for the Device category.
- Restore the cached library scroll position when returning after Settings Activity recreation.
- Increment Android versionCode to 17 for the next in-place device update.

## 0.2.9 — device preview · 2026-11-14

- Reorganize Settings into Appearance & sound, Screensaver, Favorites, Apps, Device and About, with a compact Authors & licenses subview.
- Show inline RAM/storage used/total/free figures with colored usage bars, plus a read-only diagnostic report and QR.
- Prefill GitHub-only feedback links with the approved diagnostic fields; improve rendered QR readability.
- Add configurable focus/click sounds at 70% default volume, with preview and lifecycle cancellation.
- Refresh package state every five seconds only while its settings section is active; simplify screensaver/cache controls.
- Reuse library tiles and equivalent search results; route Down to the nearest available tile in a short final row.
- Preserve Settings behind choice dialogs after touch-to-D-pad input and restore the exact originating control.
- Increment Android versionCode to 16 for an in-place update of the owner's existing device package. System Home integration remains deferred; this is not a public release.

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

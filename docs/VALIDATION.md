# Validation baseline — 2026-11-03

**Current public-source status (2026-11-11):** This file retains historical
measurements of the personal/internal launcher. The public APK no longer
contains the Accessibility Home bridge used in some checks below. For the
current isolated public build and signed-candidate evidence, read
[PERFORMANCE_CHECK.md](PERFORMANCE_CHECK.md) and
[RELEASE_APK_DEVICE_TEST.md](RELEASE_APK_DEVICE_TEST.md). Neither the historical
physical-Home check nor the personal app's post-reboot result proves that the
public APK handles the physical Home button on this firmware. That release
check remains open.

The current public Settings → Device screen now distinguishes a selected Home
role from Android's generic HOME resolver. If the role belongs to Carpe TV but
the resolver points to another package, it shows a short remote-button check
warning. `assembleDebug` and `lintDebug` pass for this change. The warning's
runtime appearance and the physical remote path remain unverified on the public
package; the personal launcher was not replaced to test the warning.

The isolated public 0.2.7 debug APK was also checked on MiTV-AFMU1 after the
Apache-2.0 About entry was added. Its full license text opened in the launcher
at 1920×1080. The top control now displays a power icon and the timer remains
in the side rail; the new layout built and passed lint. The sleep action was
not executed because it would interrupt the device. The isolated APK was
removed and the personal Home role holder remained unchanged. The install
page uses two clean captures and an actual seven-second transition recording
from this isolated build. The connected projector and final signed APK still
need their own media review.

The Apps & background Google TV row was retested in an isolated
`agency.carpediem.tv.publicpreview` APK. Its dialog showed the package-state
warning and no in-app disable action. `AppAuditProbe` verified that opening and
closing it changed neither the package enabled state nor the Home selection;
it also checked the existing recovery entry and remote focus. The probe passed
on MiTV-AFMU1. The two isolated test APKs were removed; the personal launcher
remained foreground and held the Home role. Google TV stayed enabled and
Xiaomi Analytics stayed disabled. No reboot or projector was used.
The first capture exposed a verbose stock Android dialog. The final compact
Montserrat panel was captured at the Stick's 1920×1080 output size in the
private `public-stock-home-20261111` checkpoint; its title, state, warning and
three actions were legible without clipping. The panel remained read-only in
the test. Projector appearance has not been reviewed.

Tested device: Xiaomi TV Stick, Android 14. Physical display reports 3840×2160;
UI override is 1920×1080 at density 320. Screenshots are native 1920×1080 captures.

- Debug build, test-APK build and Android lint passed locally.
- Native NavigationProbe passed content navigation and 36 ordinary/rapid sidebar
  transitions with final focus retained; background image opacity also checked.
- Card focus: 865 frames, median 10.98 ms, p95 14.00 ms, max 28.60 ms.
- Page transitions: 360 frames, median 18.01 ms, p95 40.55 ms, max 49.59 ms.
- These are one-device FrameMetrics samples, not a guaranteed 60 FPS claim.
- Physical Home return from TV Bro to the launcher was verified after testing;
  the opt-in service was bound, with no crashed service recorded.
- The three-page cache has not undergone a long-duration memory soak test.

The original rollback APK and private device logs remain outside this repository.
No serial number, network address, account dump or private preferences are required
for these results. The test APK is uninstalled after device checks.

## Recheck for a change

Run `./gradlew assembleDebug lintDebug assembleDebugAndroidTest` and repository
checks. On a dedicated test device, optionally run:

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument -w agency.carpediem.tv.test/agency.carpediem.tv.prototype.NavigationProbe
adb -s DEVICE_SERIAL uninstall agency.carpediem.tv.test
```

Instrumentation can interrupt Home Accessibility on some firmware. Record its
state before the test and re-enable only the service the user had authorized.
Tests are for a dedicated device and will move focus / open launcher pages.

## 0.2.0 internal release — 2026-11-03

Final internal APK: versionName 0.2.0, versionCode 7, same application ID and local
debug signing identity as the previous install. App label is Carpe TV.

- End-to-end 0.1.2 → 0.2.0 update through DownloadManager and native Android confirmation.
  Final build 7 was also downloaded and installed by that same path.
- Controlled download completed and APK validated while TV Bro was foreground;
  no updater Activity polling was needed for completion.
- User granted the first-source install permission; instructions now include that
  step and a real Carpe TV system-permission screenshot. No permission was enabled
  automatically by launcher code.
- Canceling the Android install left the old app running and allowed a retry.
- Favorite order, wallpaper, dimming, animation preference and selected language
  were compared before/after update and preserved.
- Native UpdateProbe accepted an actual matching-signed future APK, rejected wrong
  app/old version/oversize/insecure remote HTTP/checksum mismatch/another signing key.
  Interrupted-check recovery and English/Ukrainian resource resolution passed.
- English and System-default Ukrainian were selected through the actual UI; date,
  greeting, library, settings, about and updater screens were inspected. Focus returns
  to the language row after switching.
- Matching-signed debug ADB rollback to build 6 was tested with preferences retained;
  final build 7 was restored. This does not claim a production one-click downgrade.
- Native navigation passed 36 ordinary/rapid sidebar transitions. Card focus: 863
  frames, median 11.01 ms, p95 13.85 ms, max 25.39 ms. Page transitions: 359 frames,
  median 25.06 ms, p95 42.85 ms, max 87.90 ms. Frame times vary; no locked-60-FPS claim.

The private test server was temporary and stopped after testing. A permanent update
feed/public APK channel and production signing key remain unconfigured. No GitHub
credential was stored in the APK. Other device models and a memory soak are untested.
Private logs, test fixtures, preferences and rollback files stay outside Git.

## System controls — Xiaomi TV Stick, 2026-11-03

One physical Xiaomi TV Stick was tested, Android model `MiTV-AFMU1`, device codename
`twilight`, Android TV 14, build `UKG3.250826.001.V816.0.7.0.UZFAATK`, security patch
2026-03-01. No second model/generation is claimed. Carpe TV development 0.2.6/code13,
PR #12; Shizuku manager 13.6.0 and API/provider 13.1.5. These features may not be in
older release APKs.

| Scenario | Observed result |
| --- | --- |
| Package-state audit | Enabled/disabled/unavailable states matched Android; RAM/storage and remote navigation passed. Running-process state is not inferred from enabled state. |
| Authorized Shizuku transport | Real UserService read analytics state; analytics endpoint rejected Google TV; journal unchanged; helper exited after unbind. |
| Analytics confirmation and Cancel | Real target/consequences shown; Cancel changed neither package nor journal. |
| Analytics disable and restore | Explicit temporary enable, disable, durable original-state record and restore passed. Package left disabled-user; original test journal entry restored. |
| Stock recovery button | Enable-only Google TV operation succeeded; Carpe Home bridge disabled itself; Android Home selection opened. Google TV selected and physical Home opened it. |
| Return to Carpe TV | Selected Carpe TV in Android Home settings and restored its previously authorized Home bridge. Home returned to Carpe. |
| Reboot | Carpe TV auto-opened; Home bridge bound with no crashed service; Xiaomi Analytics remained disabled with no process or active services. |
| Access unavailable after reboot | UI reported Shizuku absent; confirming a system action changed neither Google TV state nor Home bridge. Restarting official Shizuku retained authorization. |
| Full Google TV package disable, earlier controlled test | Failed: OEM `setupwraith/.RecoveryActivity` won Home resolution. Google TV immediately restored. No Google TV disable operation is shipped. |

Stock recovery was tested with Google TV already available; we did not deliberately
repeat the unsupported disabled-package state for the videos. The recovery API is
hard-coded to enable Google TV only. No data wipe, uninstall or factory reset was used.

A delayed confirmation-dismissal race was fixed during testing. Final debug and
instrumentation builds, lint, audit and read-only transport probes passed. A local
policy probe rejected arbitrary packages, shell-like text and invalid restoration states.
Other TV firmware and long-duration Shizuku behavior remain unverified.

### Recorded walkthroughs

  no audio; begins with enabled analytics for demonstration, ends disabled.
  Android Home chooser; ends just after selection, before recording a personal Google TV feed.

Recordings use 1280×720 H.264 video captured from the 1920×1080 UI. Trimming removes
only leading/trailing idle time; it does not synthesize steps or speed up the procedure.
Private originals, analysis proxies, backups, serials and network addresses remain outside Git.

During recording the firmware had a 640 dpi override with a 1920×1080 UI, making
Android's native Home picker oversized and Google TV report an unsupported display
configuration. With explicit user approval, density was returned to the previously
tested 320 dpi; the resolution override stayed 1920×1080. The stock-Home walkthrough
was recorded again under that configuration. Carpe TV's own normalized canvas does
not prove native Android/Google TV compatibility with a mismatched density.


### Sleep timer — 2026-11-03 (development)

- One exact elapsed-realtime alarm, non-exported receiver, private deadline + boot count. No foreground service or recurring job. Explicit user consent through Android Alarms & reminders settings.
- Repository policy checks, debug/test APK assembly and Android lint passed.
- Device probe passed: remaining time, wrong action, stale/early delivery rejection, boot mismatch and cancellation. The probe never executes sleep unless explicitly invoked with `sleeplive=yes`.
- Accessibility `GLOBAL_ACTION_LOCK_SCREEN` was delivered but did not sleep this Stick; this route was removed. Sleep now uses an authorized, fixed Shizuku `input keyevent 223` operation. No generic caller-supplied commands were added.
- Live test armed the production scheduler for one minute, with Android Settings in front. Wi-Fi ADB disconnected at expiry and the user confirmed the screen went dark. The Stick was woken with its remote; dumpsys power confirmed the last sleep reason as sleep_button. Final UI captured; ADB cannot remotely wake this Stick once its Wi-Fi debugging connection sleeps.
- Timer presets: 15/30/60/90/120 minutes, cancel and remaining time; Montserrat custom dialog with remote focus and the existing dialog transition. Shizuku must remain running/authorized. Reboot cancels rather than replays the timer.


### Apps & background layout simplification — 2026-11-03

- One focusable row per package, without repeated decorative app icons; separate weighted name and fixed status columns, wrapping height and 8dp gaps.
- Short package states, two-line RAM/storage summary, one shared explanation that enabled is not the same as running. Stock Home recovery moved below the package list.
- Settings category icon bounds explicitly fixed at 22dp. Category names allow two lines.
- Device probe extended to assert non-overlapping text bounds, no vertical clipping and one focus stop per row. Device probe passed on MiTV-AFMU1: column bounds, text bounds and one focus stop per row. Screenshot captured; no package state was changed for this visual edit.

- Timer UI simplified to duration buttons and, while active, a live mm:ss countdown with cancellation. The same countdown appears in Home, library and settings headers; visible-only one-second updates stop when the activity pauses. Active user timer preserved during final screenshot capture.
- Home role currently points to Carpe TV and HomeEntry is enabled. Removed duplicate long/short forms of the same own accessibility component while preserving any other services; own service bound, no crashed services after test recovery. The reported reboot regression still needs an actual reboot reproduction; a currently selected Home role alone does not prove it fixed.


### Refreshed walkthroughs — 2026-11-03

- Re-recorded both MP4 walkthroughs against the compact Apps & background UI; updated the warning screenshots. Source captures retained privately with backups and exact edit mappings. Only long static waits shortened, no speed-up or simulated UI.
- Actual analytics restore/disable verified; final raw enabled state3 (disabled-user). Actual Google TV Home selection verified, then Carpe TV Home restored and Google TV package returned to original state0 (default). Own Home service bound, no crashed services. Existing sleep timer preserved.
- Removed duplicate Cancel from the analytics action menu. Package-list refresh now preserves the current settings tree and focus instead of rebuilding it after a package change; extended AppAuditProbe passed on the Stick. Build, lint and repository policy checks passed.

### Home focus and page transitions — 2026-11-11 (development)

- Device: the same `MiTV-AFMU1`, Android TV 14, 1920×1080 override and 320 dpi physical density. No reboot was performed for this check.
- The installed 0.2.7/code14 APK was copied before the test to the private `mistick-customization` checkpoint directory. Its SHA-256 is `06f49a6de6c1863cfae4a3afeb3320a4bde95ff409ced5dcf8f99926b68c9501`; installed and test APK signing certificate digests matched. Android's direct HOME resolution reported Google TV before installation; Carpe's separately authorized Home bridge is not captured by that resolution query.
- Debug, unsigned release and instrumentation APKs assembled; `lintDebug`, repository policy checks and `git diff --check` passed. The unsigned release is not distributable.
- On-device `HomeFocusProbe` passed first-row navigation, restoration of the last opened application tile on Home, and hiding Settings when Power gains focus. A separate end-to-end check launched TV Bro from its tile, sent the Home key and observed Carpe TV in front with TV Bro focused. The system power dialog still needs visual verification.
- On-device `NavigationProbe` passed 36 ordinary and rapid sidebar page changes with focus retained. Card navigation: 862 frames, median 11.00 ms, p95 16.22 ms, max 28.64 ms. Page transitions: 305 frames, median 32.12 ms, p95 52.56 ms, max 72.01 ms. Page animation therefore still misses a 60 fps target on this Stick; further profiling and optimization are required before describing it as Apple TV smooth.
- The instrumentation runner kills the target app process when it finishes, leaving Android's already-enabled Home accessibility service in `Crashed services` until re-bound. This was reproduced after `HomeFocusProbe`. Reinstalling the same debug APK did not produce the condition. The previously authorized service was re-bound after testing; do not mistake this test-harness side effect for an APK-update failure.
- Removing an unnecessary full-tree state fade traversal on every cached-page switch did not establish a performance gain: in a repeat run, card p95 was 15.74 ms and page p95 was 60.99 ms. The distribution varied enough that this small change cannot be credited as a fix. The retained change avoids redundant work; background compositing and page inflation still need targeted profiling.
- Two further A/B checks did not help enough to keep: disabling wallpaper crossfade yielded page p95 53.79 ms, while temporary hardware layers on both page trees yielded 71.16 ms. Both experiments were reverted. The measurements are separate runs, not a controlled thermal benchmark, but neither supports shipping those variants.
- A 2026-11-11 follow-up extended the device-only navigation probe to record `FrameMetrics` stages. It passed 36 page changes. Page total median was 33.13 ms, p95 63.01 ms, max 76.37 ms (301 frames). Stage p95 values: input 0.003 ms, animation 2.06 ms, layout/measure 1.97 ms, draw 16.42 ms, command issue 16.92 ms, sync 1.41 ms and swap 8.24 ms. Individual p95 values are from different frames and must not be added together. This points to rendering/composition work, rather than input dispatch or layout, as the next profiling target; it does not yet prove a particular view is responsible.
- The diagnostic test APK was removed afterward. As before, instrumentation left the already-authorized Home service in Android's crashed-service set. The sole enabled accessibility entry was confirmed to be Carpe TV, then re-bound; the final device readback showed it bound and no crashed service. The launcher APK was not replaced and the Stick was not rebooted.
- A temporary one-tree transition variant hid the outgoing page immediately and animated only the incoming page. On the same Stick its page p95 was 56.60 ms (311 frames), versus 63.01 ms (301 frames) in the preceding baseline; stage p95 draw was 19.12 ms and command issue 16.09 ms. This small single-run difference does not establish a durable improvement, and the alternate visual transition could not be judged from an HDMI-off screen recording (too few frames). The variant was reverted in source. The backed-up original 0.2.7 APK was restored on the Stick, the diagnostic APK removed, and the previously authorized Home service was verified bound with no crash. No reboot was performed.

### Home setup order — 2026-11-11 (development)

- Moved the three Home actions to the top of Device settings, immediately below the current Home status. Android settings follows; Shizuku controls now sit under a separate optional-actions heading. No package state or Home choice was changed.
- `assembleDebug` and `lintDebug` passed. The updated debug APK installed over Carpe TV on the same MiTV-AFMU1; the pretest APK checkpoint remains available for rollback. A 1920×1080 ADB screenshot confirmed that the Device page fits and that the three Home actions are visible without scrolling.
- After installation, Android reported the authorized Carpe TV Home accessibility service bound and no crashed services. The projector was off, so this verifies Android's rendered frame and service state, not the image as seen on the projector. No reboot was performed.
- A follow-up corrected the Home status label on this OEM firmware: Android's resolver reports Google TV even when RoleManager says Carpe TV holds the Home role. The page now prioritizes the selected role and displays “Вибраний Home: Carpe TV” with the separately enabled Home-button service. `assembleDebug` and `lintDebug` passed; the updated debug APK was installed and the exact text verified in an ADB screenshot. This is status reporting only, not a change to routing.

### Sidebar navigation memory check — 2026-11-11 (development)

- On the same Stick and same Carpe TV process (PID 22913), measured `dumpsys meminfo` before and after repeated Home → Apps → Settings → Apps → Home D-pad navigation. The baseline was 103,109 KB PSS, 169,464 KB RSS and 242 views. A rapid 100-cycle pass ended at 101,041 KB PSS, 169,296 KB RSS and 242 views. A second 35-cycle pass with 200 ms pauses between keys ended at 103,725 KB PSS, 170,832 KB RSS and 242 views. These short samples do not prove long-term leak freedom, but showed no accumulating view trees or clear upward memory trend.
- A single D-pad step from Home was visually confirmed to open Apps. After the settled cycle, the Home sidebar item was focused. A separate Settings → Power sequence showed the Home content behind the focused Power item; Settings did not remain visible. The authorized Home service stayed bound with no crashed services. The projector was off, so visual checks used ADB-rendered screenshots.

### First-run Home choice — 2026-11-11 (development)

- Added a one-time, remote-focused welcome panel on a clean installation, with “Make Home” and “Explore first”. It does not change Home automatically. Existing installations with an app cache or an already selected Home role skip the panel. The Device settings retain the same choice and rollback paths.
- `assembleDebug`, unsigned `assembleRelease` and `lintDebug` passed. The release APK media check found the three bundled nature photos and none of the known private artwork. Repository checks and `git diff --check` passed.
- Installed the debug build on MiTV-AFMU1 without clearing data. A debug-only preview intent showed the panel at 1920×1080 with the intended first button focused; D-pad Down + Select dismissed it and returned to a usable Home screen. This is a preview of the clean-install UI, not a full fresh-install test. The projector was off, so image verification used ADB screenshots. No reboot or stock package change was performed.

### Installation page on TV Bro — 2026-11-11

- Served only a temporary copy of `docs/index.html` and its bundled background image to the Stick's TV Bro on the local network. At 1920×1080 output the browser exposed a smaller CSS viewport; the original page pushed the download button below the visible area.
- Added a compact TV-browser layout and shortened both language versions of the three installation steps. A new on-device screenshot confirms the download button, release link, steps and rollback note are visible together without scrolling.
- This validates layout only. The future GitHub release asset URL was not downloaded or installed from the page. The temporary server was stopped and the personal launcher returned to the foreground; it remained the selected Home role.
- A second TV Bro check switched the visible page to English at 1920×1080. Its download button, three steps and rollback text also fit without scrolling. The page was returned to Ukrainian, the personal launcher restored, and the temporary server stopped. This was a pointer-input check; D-pad focus and the eventual live download remain open.
- A keyboard Tab sequence in TV Bro showed the visible focus ring on the language buttons and then the APK button. An injected D-pad Down from the initial page and D-pad Right from the focused APK button did not produce a visible focus change. This does **not** establish remote-only operation in TV Bro; retest with the physical remote and its cursor mode before release. No download was started in this check.
- The page now requests initial focus on the visible APK link. On reopening TV Bro, its first injected Tab made the APK button's focus ring visible, whereas before this change the first Tab selected the language control. The browser did not draw the ring until keyboard input; a physical remote OK/download test is still pending.

### Release description and build gate — 2026-11-11

- An independent source review confirmed that Settings → Power and last-opened-app focus have device-test evidence, while physical remote Home after reboot and rollback with the public APK remain unverified. The Ukrainian and English README feature lists now describe the Home chooser rather than promising Home-button compatibility on Xiaomi.
- `:app:assembleDebug` and `:app:lintDebug` passed with Gradle 9.8.2, Android Gradle Plugin 9.5.0, JDK 17 and Android SDK 34. The first offline attempt lacked the plugin cache; the next attempt lacked `ANDROID_HOME`. Both were environment setup failures, resolved by an online run with the local SDK path. No APK was installed and no device settings were changed.
- The owner chose Apache-2.0 for the original source code. The exact license text and Carpe TV notice are now in the repository and the APK. An unsigned release build passed `check_release_apk.py` with three nature photos, six required license texts and two notices. This does not resolve the separate LGPL distribution gate.
- A GitHub Pages workflow was prepared for a future stable release. It checks the release asset names and uploads only the bilingual install page plus its photo, with a public-repository guard. Its release-asset `jq` expression was checked locally, but no GitHub Actions run or public Pages deployment has happened.
- The About screen now exposes the original-code Apache-2.0 text separately from third-party notices. Debug and unsigned release builds plus `lintDebug` passed after the change; the release APK check found both bundled notices. This UI change has not yet been visually verified on the Stick.

### Isolated preview navigation recheck — 2026-11-11

- Restored the local JDK 17 build tool. `assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` passed offline against Android SDK 34. The preview used the separate `agency.carpediem.tv.publicpreview` package; the personal launcher APK was not replaced.
- On MiTV-AFMU1 at the 1920×1080 override and 320 dpi, `HomeFocusProbe` passed: toolbar focus, simulated Home returning to the last-opened tile, app-list refresh retaining that focus, and Settings hiding when Power gains focus.
- `NavigationProbe` passed 36 ordinary and rapid page changes. Card focus: 868 frames, median 10.65 ms, p95 18.46 ms. Page changes: 304 frames, median 27.17 ms, p95 55.33 ms, max 141.65 ms. The same stress probe with animations disabled reported page p95 76.83 ms over only 36 frames, so removing animation is not supported as a performance fix. These are diagnostic runs, not an exact visual smoothness rating.
- The isolated preview and its test package were uninstalled. The personal launcher returned to the foreground; Android readback showed its Home accessibility service bound with no crashed service. No reboot or Home-package change was performed.

### Install-page D-pad check — 2026-11-11

- Served the local `docs/index.html` temporarily over the home LAN and opened it in TV Bro on MiTV-AFMU1 at 1920×1080. The Ukrainian heading, direct APK button, three steps and rollback note were visible in the first viewport. This checked layout, not the future public GitHub URL.
- From the initial page, one injected D-pad Down visibly focused the APK button with a white focus ring. D-pad Right then switched TV Bro into pointer navigation instead of visibly focusing the adjacent release link. The primary APK action is reachable, but complete remote-only navigation and an actual download/install still need a physical-remote check against the release asset.
- Corrected the second install step to say that Android grants install permission to whichever app opens the APK. This covers a browser or a phone-transfer receiver without promising that the browser owns every installation. The local server was stopped; temporary device screenshots were removed and the personal launcher was brought back to the foreground.

### Stock Home comparison capture — 2026-11-11

- Captured the actual Google TV Home from the same MiTV-AFMU1 at 1920×1080 for the install-page comparison. The first explicit launch was redirected by the personal Home bridge and showed Carpe TV; it was rejected. For the accepted capture, the single enabled Home accessibility service was temporarily removed from Android's enabled-service setting, Google TV was opened, and the exact prior setting was restored in a `finally` path. Readback showed Carpe's service bound, enabled, and absent from crashed services.
- Replaced the illustrative stock-screen placeholder with the real capture in both page languages. The image includes third-party recommendation art and the owner's visible app row; review that content again before a public release. The isolated test package was removed and the personal launcher returned to the foreground.
- A separate incoming-page transition experiment removed page opacity but kept its 4 dp movement. The isolated device probe passed navigation; page p95 was 53.73 ms over 311 frames versus 55.33 ms over 304 frames in the earlier run. This difference is too small and uncontrolled to claim improvement. The experiment was reverted; page transition performance remains open.

### Public Pages preview gate — 2026-11-11

- The Pages source defaults to a preview: APK download buttons are disabled and structured data has no `downloadUrl`. Only after a stable release's APK, source archive, checksums, updater metadata and version tag are verified does the workflow enable the download UI. Subsequent source pushes repeat the same gate against the latest stable release.
- The preview transform passed a local output check; repository policy and whitespace checks passed. The Git history contains six agency-authored commits; a scan of 311 reachable Git objects with private-key, GitHub-token and credential-assignment patterns found zero candidate paths. This pattern check is narrower than a dedicated secret scanner and does not clear all publication gates.
- The local `gh` CLI authentication was invalid, but the user's Chrome GitHub session and the connected GitHub account were authenticated as `carpe-diem-agency`. The new public `carpe-tv-launcher` repository was created without an initial GitHub commit; the six clean-history commits and this preview-gate commit were pushed as `main`. The first Pages run failed because Pages was not yet enabled. Its source was set to GitHub Actions, the failed job was rerun, and attempt 2 passed. The live URL served the Ukrainian preview with real comparison images and the footer link “Детальна інструкція”. The APK release path remains untested because no public release exists.

### Focus and language-state regression — 2026-11-11

- The isolated `agency.carpediem.tv.publicpreview` APK and its test runner were installed on MiTV-AFMU1. `LanguageStateProbe` passed after setting a last-opened app: recreating Settings retained the Appearance screen and focus on the language control. This covers the in-app language change lifecycle, not a physical remote or process death.
- `HomeFocusProbe` passed with the current five-column Home-to-toolbar map, simulated Home-return focus, app refresh, and toolbar navigation. The isolated packages were removed, the personal launcher returned to the foreground, and Android still reported `agency.carpediem.tv.prototype` as Home holder.
- The GitHub issue form now has Ukrainian and English field labels. Its YAML parses locally, and the QR target returns GitHub's login flow for a signed-out visitor. Scanning the QR with a phone and submitting an issue have not yet been checked on a device.

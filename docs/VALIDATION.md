# Validation baseline — 2026-11-03

## Device checklist and remote navigation — 2026-11-11

The redesigned Settings → Device checklist was installed in the isolated
`agency.carpediem.tv.publicpreview` package on MiTV-AFMU1 at 1920×1080.
Its Home card remained unverified because Android's Home role belongs to
Carpe TV while the Xiaomi remote route still opens Google TV. The optional
system-access card showed ready. No stock package was disabled.

`settingsfocus` passed right/down/up/left navigation between the Device
category and both setup cards. `homefocus` passed toolbar order, Back/Home
handling inside Carpe TV, external-app return to the prior tile, app refresh,
Power confirmation and launch motion. The Home-focus probe now closes the
Power confirmation before checking navigation behind it. These tests do not
prove physical Home from another app; that release check remains open.

## Rendered feedback QR — 2026-11-11

On the same isolated preview, the feedback dialog was captured at the Stick's
1920×1080 output size. An independent ZXing QR reader decoded the rendered
image to `https://github.com/carpe-diem-agency/carpe-tv-launcher/issues/new?template=bug.yml`.
The dialog retained the browser action, and Back restored focus to Settings.
This verifies the TV image and encoded destination; a phone-browser Issue
submission remains a separate end-to-end check.

## Favorites and language focus — 2026-11-11

On the isolated preview, `reorder` passed remote arrows, boundary handling,
Back cancellation, OK persistence, selected-tile focus and return to the top
toolbar. The test restored the original favorite order afterward.
`languagestate` passed Activity recreation from Settings after a previously
opened app: the Settings page and language control retained focus. Its test
setup now restores the first-run preference as well as the last-opened app.
These checks cover the stated paths, not every possible application lifecycle.

## Shared-background navigation check — 2026-11-11

On the isolated public preview on MiTV-AFMU1, the selected blue wallpaper was
identical across Home, Library and Settings. The launcher nevertheless replaced
and cross-faded its full-screen drawable on every page change. The background
key now stays shared for blue, plain and not-yet-decoded photo placeholders;
loaded photos still get the page-specific dimmed/blurred treatment.

The same device navigation probe passed before and after the change: 36 normal
and rapid page changes retained focus. Before the change, page frames had
median 31.21 ms and p95 54.42 ms (315 frames). After the change, two runs had
medians 20.52/28.05 ms and p95 43.54/43.69 ms (289/294 frames). Focus-card
p95 stayed near 15 ms. This is a one-device comparison, not a locked-60-FPS
claim. The wallpaper probe also passed all five choices, D-pad focus, rapid
switches and restart persistence. No system packages were changed.

## OEM Home route and optional bridge experiment — 2026-11-11

On Xiaomi MiTV-AFMU1 (Android TV 14), `agency.carpediem.tv.publicpreview`
held the Android Home role, but an injected Home key from YouTube opened
`com.google.android.apps.tv.launcherx/.home.HomeActivity`. Android's
`cmd package set-home-activity` reported success yet did not change the
resolver or this result. This reproduces the OEM routing gap in the current
public-source behavior; the physical remote remains a separate check.

An isolated experimental APK with an opt-in Accessibility service was built
and installed over the preview. The service listened only for Google TV window
changes, had no screen-content capability, and ran only while Carpe TV held the
Home role. With the service temporarily enabled, YouTube → injected Home
returned to the preview launcher with the YouTube tile focused. Switching the
Home role to Google TV while leaving the service enabled made the same sequence
land on Google TV, confirming the rollback guard. The role was then restored
to the previously selected preview; the temporary Accessibility setting was
removed (`enabled_accessibility_services=null`, `accessibility_enabled=0`).

The experiment is saved on local branch `experiment/oem-home-bridge` and is
**not in the public APK source**. An earlier signed APK with this kind of
service was blocked by Play Protect when installed through TV Bro, while the
same candidate without it installed. See
[PLAY_PROTECT_TEST.md](PLAY_PROTECT_TEST.md). Before promoting any bridge,
repeat ordinary sideload installation and rollback tests with the exact
release candidate. The current public build must not claim that the Home role
alone works on this Xiaomi firmware.

## System setup wording and remote focus — 2026-11-11

An isolated `agency.carpediem.tv.publicpreview` debug APK with the revised
Shizuku explanation was built and installed on MiTV-AFMU1. Before replacement,
the previous preview APK was copied outside the repository for rollback, and
the Home role holder was recorded. `SystemSetupFocusProbe` passed: all visible
wizard buttons were reached by D-pad Down in order, then D-pad Up returned to
the first action. The probe did not start pairing or change system packages.
The instrumentation APK was removed afterward. The Home role still belonged
to the preview package, Accessibility remained unset, and the preview launcher
was returned to the foreground. `FLAG_SECURE` prevents a screenshot of this
wizard; a projector visual review of the revised text remains open.

## Public preview UX recheck — 2026-11-11

On Xiaomi MiTV-AFMU1 (Android 14), the isolated `publicpreview` APK and
instrumentation APK built and installed. These focused probes passed on the
device: `homefocus` (including Home within Carpe TV preserving the selected
favorite), `settingsfocus`, `feedback` (Issue QR and Back focus), and
`pairinglimit` (English and Ukrainian phone pages load; five rejected codes
close the local session). The test package
was separate from the selected Home launcher.

The navigation probe also passed 36 normal/rapid page changes. On this run,
focused-card frames had median 10.57 ms and p95 15.02 ms (862 frames); page
transition frames had median 31.39 ms and p95 56.38 ms (315 frames). A
reduced-motion comparison still measured page p95 55.97 ms (36 frames), so
this single pair of runs does not identify animation as the sole cause of
slow page frames. Further profiling and visual review remain necessary.

The physical remote's Home routing from YouTube into the future public release,
phone scanning of the Issue QR, and a complete visual review of every settings
screen remain release checks. The Shizuku guide intentionally prevents ADB
screenshots, so its wording and visible controls were checked through Android's
UI hierarchy instead of a captured image.

### Home alias preference regression — 2026-11-12

On MiTV-AFMU1, the isolated public preview had a separate focus regression:
launching `HomeEntry` after a process stop focused the left Home navigation item
even though YouTube was the last opened favorite. Android's activity-specific
`getPreferences()` had written two files: `MainActivity.xml` held
`last_opened_package`, while `HomeEntry.xml` held the Home-alias settings. The
launcher now uses one stable preference file for both entry points and imports
alias-only settings without replacing existing main settings. The screensaver
reads the same selected wallpaper. The updater's local JSON checkpoint now
exports the launcher, language/screensaver, and legacy alias preference files.

Before the device install, the installed preview APK and preference files were
copied to private local rollback files. `assembleDebug`, `assembleDebugAndroidTest`,
and `lintDebug` passed. A controlled process stop followed by an explicit
`HomeEntry` launch focused YouTube again; the isolated `homealias` instrumentation
probe passed and its temporary test package was removed. This validates focus
when Carpe TV is launched as Home. It does **not** validate the physical Home
button on Xiaomi firmware; that route still opens Google TV.

An additional read-only resolver check still selected Google TV's
`launcherx/.home.HomeActivity`, while the preview's `HomeEntry` remained enabled
and registered as a preferred activity. A reversible component-only experiment
could not proceed: Android rejected both shell `disable-user` and `enable` for
Google TV's protected Home component with `SecurityException`. No component
state changed; the resolver still selected Google TV afterward. Disabling the
entire Google TV package is not a replacement for this test because an earlier
controlled trial routed Home to Xiaomi's recovery activity. The stock package
and the current Home selection were left intact.

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
- Back from the app library now restores the last focused Home tile rather than selecting the sidebar. The extended `HomeFocusProbe` passed this route on MiTV-AFMU1 in an isolated test package; this does not prove physical Home after leaving YouTube.
- `FeedbackProbe` passed on the same stick: the GitHub Issue QR is present and described for accessibility, the TV-browser fallback is visible, and Back restores the previously focused Settings entry. A phone scan and actual issue submission remain to be checked.

### Current visual and remote recheck — 2026-11-11

- Built the isolated `agency.carpediem.tv.publicpreview` APK, verified its package name with `aapt`, and installed it without replacing the personal `agency.carpediem.tv.prototype` launcher. Captured Home, Apps and Settings directly from MiTV-AFMU1 at its 1920×1080 override and 320 dpi. The screenshots stayed local because the Apps view shows the owner's installed software.
- D-pad Down moved from the Home rail to Apps and then Settings; each screen rendered without a loading placeholder. `HomeFocusProbe` passed again for the toolbar, Back, simulated Home return and focus after an app-list refresh.
- Visual follow-up: the Settings detail column is dense at 1080p, and the next wallpaper card is cut off at the viewport edge. Check whether the carousel affordance and lower controls remain clear with a physical remote before using these screens as release imagery.
- The isolated APK and runner were removed. Android still reports `agency.carpediem.tv.prototype` as the Home holder. Physical Home after returning from YouTube remains unverified for the public build.
- Enlarged the wallpaper cards from 170×96 dp to 205×116 dp so the next image peeks into view without a separate arrow. A native fading-edge attempt was not visible on this TV build; a circular chevron was then rejected as visually inconsistent and removed. The final 1080p device capture shows the larger cards, and `WallpaperProbe` passed five-card D-pad navigation, selection, rapid switching and restart persistence. This change remains only in the isolated preview build until a signed release is prepared.
- Extended `HomeFocusProbe` to leave the isolated launcher for Android Settings and return with Back, exercising Android's actual pause/resume path. The probe supplies the expected last-opened package in isolated test state, and the stick restored focus to that tile. This is stronger than calling `onNewIntent` alone, but still does not test launching YouTube or the physical Home key with the public package as the selected Home role.

### Isolated updater and device screen — 2026-11-11

- Kept the personal `agency.carpediem.tv.prototype` APK and Home role intact. Saved its installed APK separately before testing; this is an APK checkpoint, not a backup of its private data.
- Installed `agency.carpediem.tv.publicpreview` 0.2.8, then used its in-app updater with a temporary local HTTP feed to download a 0.2.9 debug APK. Android requested the per-app install permission and showed its own update confirmation. The preview package advanced to version code 16; the updater's state returned to idle. This verifies the local-feed debug path, not GitHub Releases, final signing, or unattended installation.
- Enlarged the update actions and stabilized the status panel after the permission action clipped on the projector. A real 1920×1080 capture of the updated preview showed a green success icon and the single status “У вас актуальна версія”. The Device page displayed `MiTV-AFMU1`, Android 14, 3840×2160 at 60 Hz, and preview version 0.2.9 without clipping.
- `assembleDebug` and `lintDebug` passed locally. GitHub's Build and checks and Pages jobs passed for commit `8416159`. The personal Home accessibility entry was restored and read back after the test.

### OEM Home routing after YouTube — 2026-11-11

- Repeated the check on the installed `agency.carpediem.tv.publicpreview`
  0.2.8 after the personal package had been removed. Saved the exact installed
  APK at `/private/tmp/carpe-publicpreview-before-home-test.apk` (SHA-256
  `bb1255cbd742486ddca6973d5991a9064e3293b35c55cf4ff91ddd5281210724`).
  Android's Home role holder was the preview, but `MAIN` + `HOME` resolved to
  `com.google.android.apps.tv.launcherx/.home.HomeActivity`. Launching YouTube
  and sending an injected Home key resumed that Google TV activity. Carpe TV
  was returned to the foreground afterward; Google TV remained enabled and no
  package state or Home role was changed. This confirms the current installed
  build still has the same routing failure. It is not a physical-remote test.
- On MiTV-AFMU1, enabled the isolated preview's Home entry using its own Device action and Android's Home picker. Android reported `agency.carpediem.tv.publicpreview` as the Home role holder. The personal Home bridge was temporarily disabled for the test.
- Launched YouTube from the preview Home tile. After an injected Home key event, Android resumed `com.google.android.apps.tv.launcherx/.home.HomeActivity`, not the selected preview. This confirms an OEM routing gap on this firmware. It does not establish the behavior of the physical remote, but it is sufficient to prevent claiming that choosing the Android Home role alone works on this stick.
- Restored `agency.carpediem.tv.prototype` as the Home holder and its previously enabled Home bridge, verified the service bound with no crashed service, and brought the personal launcher to the foreground. No system package was disabled or removed. The isolated preview was then uninstalled so it no longer appears as a second Carpe TV in the Android Home picker; Home role and service were read back again.
- Changed the Device warning to remain visible whenever Carpe TV holds the Home role, because `resolveActivity` and the role holder can both look correct while the OEM Home screen still appears. The warning asks users to test the remote before changing system packages.

### Optional system access guide — 2026-11-11

- Reworded the TV wizard and website to explain Shizuku as an optional helper for sleep on a timer and selected system actions. The wizard now shows QR on phone → code on TV → access ready, and names the exact Android path for the six-digit code. The phone form repeats that path and removes the code form after successful pairing.
- Built the isolated preview APK and inspected the wizard's 1920×1080 view hierarchy on MiTV-AFMU1: all three steps, status and enabled buttons fit on screen. `SystemSetupFocusProbe` passed D-pad navigation. `PairingLimitProbe` passed the Ukrainian and English phone instructions and five-attempt limit without using a real code. The website inline script passed `node --check`.
- The wizard uses `FLAG_SECURE`, so ADB screenshot capture produced an empty file. No pairing code was recorded. The preview APK was backed up before replacement; the temporary test runner was removed after testing. This check did not perform a new real pairing or change system packages.

### Guided phone-to-TV pairing layout — 2026-11-11

- Replaced the detached QR, instructions and settings button with one guide: scan QR on phone → open Wireless debugging and select pairing by code on TV → enter the six digits on the phone. The settings button sits beside the TV step. The long introduction hides during pairing so the QR and all three actions fit at 1920×1080.
- Built and installed only `agency.carpediem.tv.publicpreview`, after saving its previous APK to `/private/tmp/carpe-preview-before-guided-qr.apk`. `SystemSetupFocusProbe` passed on MiTV-AFMU1 Android 14: every instruction was fully visible and D-pad Down from the QR action reached the settings button. The temporary androidTest package was uninstalled. No real code was entered and no system launcher setting changed.

### Device setup checklist and Home route — 2026-11-11

- Saved the installed isolated preview APK to `/private/tmp/carpe-preview-before-setup-checklist.apk` before updating it. The Device settings now present Home and optional system actions as two focusable cards with distinct vector icons. The system-access card is green only when the access is ready.
- On MiTV-AFMU1 Android 14, the preview held the Android Home role, but an injected Home key from Android Settings resumed `com.google.android.apps.tv.launcherx/.home.HomeActivity`. Manually returning to Carpe TV did not mark Home as verified. The Device card therefore reports that Google TV still opens from Home. This is an injected-key test; the owner also reported the same result with the physical remote.
- The final 1920×1080 device capture shows both cards without clipping. D-pad Right from the Device category focused the Home card. `assembleDebug` and `lintDebug` passed for the isolated preview. No stock package was disabled or removed. The Home routing gap remains a release blocker for claiming that Carpe TV fully replaces Home on this Xiaomi firmware.

### Toolbar focus and sidebar highlight — 2026-11-11

- On MiTV-AFMU1, focusing a top action previously left the current page highlighted in the left rail. The isolated preview now clears that rail selection while the toolbar or Apps search has focus and restores it when focus returns to page content.
- Saved the previous installed preview APK as `/private/tmp/carpe-publicpreview-before-focus-fix.apk`, then installed only the isolated preview. `HomeFocusProbe`, `SettingsFocusProbe` and `ReorderProbe` all passed on the stick. This change does not alter the unresolved OEM Home routing.

### YouTube return route — 2026-11-11

- On the installed `agency.carpediem.tv.publicpreview` 0.2.8, closed Carpe TV's idle screen, moved D-pad Right from the Home rail to the YouTube tile, and opened it with Select. Android reported YouTube's `MainActivity` as resumed.
- One Back key exposed Aerial Views' dream activity; a second Back key resumed Google TV, not Carpe TV. This observation does not establish whether YouTube itself or the system dream caused the first Back transition, but it confirms that Back did not return directly to Carpe in this run.
- Opening the existing Carpe TV task explicitly then focused the YouTube tile again. This verifies last-tile focus restoration when Carpe returns to the foreground, while the OEM Home routing remains unresolved. No system package, Home role or accessibility setting was changed during this test.
- `HomeChoice.request()` no longer reports only “already selected” when Carpe holds the Home role but Android resolves Home to the stock launcher. The Device status uses the same generic route-conflict wording on different brands. CI `android` passed on draft PR #8 at commit `2412a6f`; this text fix has not been installed on the stick.

# Navigation performance on the Xiaomi stick

On 2026-11-11 an isolated `agency.carpediem.tv.publicpreview` debug APK was
measured on MiTV-AFMU1 (Android TV 14, 1920×1080, 320 dpi) with HDMI output
off. The test app and its instrumentation were removed afterward; the personal
launcher remained installed and resumed its screensaver.

The navigation probe passed focus-only sidebar switching, content entry and
return, rapid D-pad input, and Home. In 867 sampled content-navigation frames,
median total frame time was 11.57 ms and p95 was 19.00 ms. In 305 sampled
frames during 36 normal and rapid page changes, median was 34.66 ms, p95 was
61.52 ms, and maximum was 71.62 ms. The page-transition p95 draw and command
stages were 19.41 ms and 18.38 ms respectively. These are instrumented debug
build measurements; they do not prove release-build frame rate or visual
smoothness on a projected screen.

The first attempt failed because the newly installed isolated APK displayed
first-run onboarding over the navigation probe. The probe now temporarily
marks onboarding complete only for `.publicpreview` and restores that value
afterward. A clean repeat passed. A short experiment using temporary hardware
layers for the two page trees raised page-change p95 to 79.46 ms, so that
change was reverted. The remaining page-transition cost should
be reviewed visually with HDMI on and measured again in a signed release
candidate before publication.

An incoming-page-only transition was then tested on the same isolated device.
The previous page is hidden before the new page fades from 72% to full opacity
over 180 ms, so only one page tree is drawn during the transition. Two runs
passed the D-pad probe. Page-change p95 was 54.14 ms and 54.49 ms; the first
run had one 134.35 ms outlier, while the second maximum was 66.68 ms. The
Home-focus regression also passed, including Settings-to-Power and restoring
the last opened app tile. This variant is retained for a projected-screen
visual review; the debug measurements alone do not establish final smoothness.
An ADB screen recording with HDMI off showed the incoming page over the
wallpaper without a black frame in the captured transition frames. The
recording had a sparse frame stream during static content, so it cannot prove
the full transition timing or projected image quality. The diagnostic recording
is stored privately because it shows the owner's installed apps.

## Bounded memory run

The isolated preview remained in the foreground for 100 Home → Apps →
Settings → Apps → Home D-pad cycles over 279 seconds. `dumpsys meminfo`
reported 95,632 KB PSS at the start, then 112,064 / 112,466 / 112,314 /
112,546 / 112,322 KB after 20 / 40 / 60 / 80 / 100 cycles. The process stayed
alive and the final screen was Home. This suggests a stable warmed cache for
this workload on one stick, but does not cover hours of use, wallpaper rotation
or all optional integrations. Screenshots and raw samples remain in the private
test checkpoint because they show installed apps.

On 2026-11-11 the isolated wallpaper-gallery probe passed with HDMI off:
five choices, D-pad gallery navigation, selection markers, asynchronous
loading, rapid changes and restart persistence. Its first failures came from
the probe reading `MainActivity.xml` while the Activity used its fully
qualified preferences file. The corrected probe measured about 68.9 MB of
native heap at the end of one run. This is a single endpoint, not a
wallpaper-rotation memory trend. Recheck visually and measure memory over
extended use on the final signed candidate.

After the sidebar D-pad handler change, the isolated navigation probe passed
focus-only sidebar switching, content entry/return, rapid input and Home.
It collected 866 general frames (median 10.69 ms, p95 20.38 ms) and 303 page
transition frames (median 19.55 ms, p95 54.70 ms, max 129.07 ms). These are
debug-build timings with HDMI off, so projected-screen smoothness is still
unverified.

## Temporary signed release navigation run

On 2026-11-11, a release APK signed with a disposable test key was installed
under the public package ID beside the personal launcher. With HDMI output off,
20 Home → Apps → Settings → Apps → Home D-pad cycles produced 1,045 frames in
`dumpsys gfxinfo`: 110 janky frames (10.53%), median 21 ms, p90 32 ms,
p95 34 ms, and p99 46 ms. Android attributed 109 frames to slow issue-draw
commands and 7 to a slow UI thread. This is a full navigation workload, so its
percentiles are not directly comparable with the debug page-only samples above.
The result suggests that drawing cost still deserves projected-screen review;
it does not establish perceived smoothness with the projector connected.

The final Home screenshot was checked. The test package, script and temporary
key were removed; the personal launcher was returned to the foreground and
remained the Home holder. Raw `gfxinfo` stays in the private checkpoint because
device details and installed-app information can appear in test artifacts.

## Wallpaper drawing change awaiting projected review

After this measurement, Apps and Settings wallpaper tints were moved to the
background decode worker. Each page now draws one precomposed blurred bitmap
instead of a blurred bitmap plus a full-screen tint layer during transitions.
The Home wallpaper retains its adjustable tint. Both debug and release APKs
built successfully, and screenshots of Home, Apps and Settings on the same
stick showed the expected backgrounds. The isolated public package was then
uninstalled; `agency.carpediem.tv.prototype` remained the Home role holder.
This change has not yet been compared with a matched release-build navigation
run or visually reviewed with HDMI output on, so no frame-rate improvement is
claimed here.

A matched release-build run was attempted with another disposable test key.
The first-run action opened Android's Home-role chooser over the app; only 21
launcher frames were recorded, so that sample was discarded. With the HDMI
output off, subsequent screenshots of the chooser were black and the run was
stopped rather than risk changing the default Home. The public test APK and
script were removed, the personal launcher was resumed, and its Home role was
confirmed. That sample was excluded from the comparison below.

A valid follow-up used `-PisolatedPreview` to give the same release build a
separate package ID. The welcome dialog was visibly on **Explore first**;
pressing OK entered the launcher, while the personal package retained Home.
After resetting `gfxinfo`, the same 20 Home → Apps → Settings → Apps → Home
cycles yielded 1,043 frames: 67 janky (6.42%), median 13 ms, p90 22 ms,
p95 24 ms and p99 36 ms. The preceding release run had 1,045 frames:
110 janky (10.53%), median 21 ms, p90 32 ms, p95 34 ms and p99 46 ms.
Android reported 65 slow issue-draw frames and 3 slow UI-thread frames in the
optimized run, versus 109 and 7 before. The final screen was Home. This is
one matched workload on one device with HDMI output off; package IDs differ,
and run-to-run variation has not been quantified. It supports the wallpaper
change but does not establish Apple TV-level or projected-screen smoothness.

The isolated package and test script were removed. The normal public package
was rebuilt and verified as `agency.carpediem.tv`; only the personal package
remained installed on the stick, with its Home role confirmed. Raw results and
screenshots remain in the private `public-release-performance-20261111` and
`public-release-performance-20261111-optimized` checkpoints.

## App-list refresh focus regression — 2026-11-11

A package-list change previously rebuilt the visible Home or Apps page without
explicitly restoring a focused app tile. `showApps` now records that tile's
package before rebuilding, then focuses its replacement. If the package has
been removed, it focuses the first remaining tile. The existing Home-return
focus path remains ahead of this fallback.

The isolated `agency.carpediem.tv.publicpreview` debug APK and its instrumentation
APK were installed on MiTV-AFMU1. `HomeFocusProbe` exercised first-row D-pad
navigation, Home-return focus, a pending Home restore during refresh, a normal
focused second-tile refresh, and Settings → Power. It reported `home_focus=PASS`.
Both isolated test packages were then uninstalled, the personal launcher was
resumed, and Android's Home role still read `agency.carpediem.tv.prototype`.
This verifies a controlled refresh on one device; it does not measure visual
smoothness on the projector or replace final release testing.

## Current isolated navigation comparison — 2026-11-11

On MiTV-AFMU1 with HDMI output off, the current isolated debug build passed
36 normal/rapid page changes and focus checks. With motion enabled, the
instrumentation recorded 866 focus-navigation frames (median 10.75 ms,
p95 17.26 ms) and 304 page-transition frames (median 20.88 ms, p95 50.32 ms).
A second run used the same workload with motion disabled only inside the
isolated test app: 79 focus frames (median 16.60 ms, p95 35.61 ms) and 36
page frames (median 47.75 ms, p95 61.24 ms). Both runs passed navigation.

The frame populations differ greatly because removing animation removes its
intermediate frames; these percentiles are not a direct speed comparison.
They do show that replacing transitions with hard cuts does not make page
changes uniformly cheap. Page draw, command issue, background composition and
first-render work need release-build profiling on the projected display.
No animation setting was changed in the personal launcher. Both isolated APKs
were removed and its Home role was confirmed after each run. These numbers
are debug instrumentation evidence, not a claim of Apple TV-level smoothness.

## Release-variant navigation on the stick — 2026-11-11

A locally signed release variant with the isolated `.publicpreview` app ID was
installed on MiTV-AFMU1. After first run, 20 scripted rail cycles through
Home → Apps → Settings → Apps → Home were measured with `dumpsys gfxinfo`.
The result was 923 frames, median 10 ms, p90 13 ms, p95 22 ms, p99 57 ms;
Android reported 6 janky frames (0.65%) under its current metric and 62
(6.72%) under the legacy metric. The final ADB screenshot showed the Home
page and five favorite apps. The test APK was uninstalled, the personal
launcher resumed, and its Home role remained selected.

This release workload is closer to shipping behavior than debug instrumentation,
but it still uses injected input with the projector off. It does not establish
perceived smoothness on the projected image or behavior on other hardware.
The debug page-transition p95 above and this `gfxinfo` p95 have different
measurement windows and should not be compared as identical metrics.

## Source-built release candidate follow-up — 2026-11-11

A release APK built with a test-modified, source-built SPAKE2 AAR was signed
with a disposable certificate and temporarily installed under the public app
ID on MiTV-AFMU1. This checks the current release UI code after the timer
dialog change; it is not a production-signed APK. Android reported a 4K/60 Hz
physical display mode with a 1920×1080 logical UI override.

In 20 Home → Apps → Settings → Apps → Home cycles with 120 ms between injected
D-pad keys, `dumpsys gfxinfo` recorded 1,045 frames: median 10 ms, p90 13 ms,
p95 14 ms, p99 24 ms, 5 janky frames (0.48%) under the current Android metric
and 18 (1.72%) under its legacy metric. A deliberately faster run without
pauses had 542 frames, median 21 ms, p95 26 ms, 2 current-metric janky frames
and 470 legacy janky frames (86.72%). The rapid run queues transitions more
densely and is not representative of ordinary remote use; it identifies a
stress behavior that still deserves visual review on the projector.

The final screenshot showed Home and the expected five favorites. The test
package was removed, the personal launcher resumed and the Android Home role
still named `agency.carpediem.tv.prototype`. These ADB-injected measurements
do not establish perceived smoothness, physical-remote behavior or performance
on other devices. Raw captures remain outside the public repository because
they contain the owner's app list.

## Wallpaper cost isolation — 2026-11-12

The isolated debug build on MiTV-AFMU1 passed the same 36 page changes and
focus checks in each run. With the default Alpine photo, page-frame p95 was
59.56 ms (309 frames); focus-frame p95 was 16.70 ms. A test-only
`plainWallpaper=yes` mode kept the navigation workload and replaced the
wallpaper temporarily: page-frame p95 fell to 38.24 ms (313 frames), and
focus-frame p95 to 14.22 ms. The probe restored the wallpaper preference.

Two unshipped experiments tested likely costs. Suppressing the background
crossfade only during page changes yielded 49.23 ms page-frame p95 (292
frames). Precomposing the home dim into a full-resolution bitmap while
retaining the crossfade yielded 59.63 ms (308 frames). Each is one debug run,
so the numbers are diagnostic rather than a reliable percentage improvement.
The no-crossfade version also changes the visual transition, and the
precomposed version adds bitmap memory without a measured benefit. Both
product-code experiments were reverted. Keep the photo and its current
transition until a visually acceptable optimization passes repeated device
measurements and a projector review.

## Follow-up with explicit wallpaper selection — 2026-11-12

The isolated preview instrumentation now records the selected wallpaper and
can temporarily force the Alpine photo. On MiTV-AFMU1 with HDMI output off,
the current branch passed focus checks and 36 normal/rapid page changes in
three runs. With the blue wallpaper, page-frame p95 was 45.86 ms across 286
frames. Two Alpine runs measured 41.45 ms across 317 frames and 41.80 ms
across 315 frames. Focus-navigation p95 was 16.40 ms on blue, then 16.88 ms
and 14.85 ms on Alpine. The probe restored the prior wallpaper setting after
each run.

These debug-build samples show that the current photo path did not regress
against blue in this workload. They do not establish a repeatable improvement
over earlier builds because code, cache warmth and measurement conditions
changed. No product transition was altered. The previous isolated preview APK
was restored, the instrumentation package removed, and projector-visible
smoothness remains to be checked before release.

## Repeated navigation memory check — 2026-11-12

The current isolated `.publicpreview` debug build ran 200 real D-pad cycles
through Home → Apps → Settings → Apps → Home on MiTV-AFMU1. Every 40 cycles,
the probe checked that Home and its rail focus were restored, then sampled
process PSS and the active window's view count. All focus checks passed.
PSS at cycles 40 / 80 / 120 / 160 / 200 was 104,933 / 106,331 / 103,547 /
104,187 / 105,103 KB. The view count was 244 at every checkpoint.

This bounded run found no accumulating view trees or sustained PSS increase in
that navigation path. It does not prove leak freedom over hours, wallpaper
rotation, app install/remove events or external-app sessions. The previous
isolated preview APK was restored, the test APK removed, and enabled
accessibility services still read `null`. System Home integration was not
tested because that work is paused.

The same 200-cycle route was repeated with the Alpine photo wallpaper forced
on the isolated preview. All focus checks passed. PSS at cycles 40 / 80 / 120 /
160 / 200 was 113,129 / 111,322 / 112,886 / 113,974 / 111,710 KB; the view
count stayed at 244. Photo wallpaper used roughly 7–9 MB more PSS than the
blue-wallpaper run, with no sustained rise across this bounded test. These are
separate runs, not a controlled long-duration comparison. The test restored
the prior wallpaper and rotation preferences; the previous isolated APK was
restored, the test APK removed, and enabled accessibility services still read
`null`. Projector-visible transition quality remains to be checked.

## Isolated debug follow-up after layout measurement change — 2026-11-11

The `.publicpreview` debug APK at source commit `21472ce` and its separate
instrumentation APK were installed on MiTV-AFMU1 without changing the personal
launcher. The D-pad probe passed focus navigation and 36 ordinary/rapid page
changes. It recorded 872 focus frames (median 11.37 ms, p95 20.16 ms) and
312 page-transition frames (median 23.71 ms, p95 46.67 ms, max 71.78 ms).
Page-stage p95 values were draw 10.22 ms, command issue 15.64 ms and layout
1.25 ms; these are marginal percentiles from different frames and cannot be
added. The `homefocus` probe also passed app-list refresh, last-app focus,
Settings hidden on Power focus, and launch-motion fallback.

These numbers are one debug run with HDMI output off. They do not prove the
small `baselineAligned` edit improved performance or that the projected image
is consistently smooth. Both temporary APKs were uninstalled. A readback
showed only `agency.carpediem.tv.prototype` remained among Carpe TV packages,
and Android's Home resolver still named Google TV as it did before the test.

# Isolated validation probes

`run_validation_probes.py` packages the previously audited SSD `run_probes.py`
protocol. It keeps existing instrumentation flags, the 180-second per-probe cap,
and `navigationsoak -e photoWallpaper yes` unchanged. It never installs, replaces
or uninstalls the normal `agency.carpediem.tv.publicpreview` package.

Read-only artifact preflight, **no ADB/device operations**:

```sh
python3 tools/run_validation_probes.py --adb /path/to/sdk/platform-tools/adb --aapt /path/to/sdk/build-tools/35.0.0/aapt --transport-id 1 --runtime-root /path/to/isolated-carpe-tv-runtime --expected-version-code 24 --label offline-preflight-24-a --verify-only pendinglibrary wallpapercache
```

Root must verify the selected SDK/runtime first. APK paths are fixed to the existing
APFS runtime's `build/app/outputs/apk/{debug,androidTest/debug}` output locations.
Actual APK package/code and matching Gradle metadata are checked for both APKs.
The test APK permits empty/0 version code; its manifest instrumentation block
must name NavigationProbe and target the exact validation package. APK and script
hashes associate evidence with artifacts. Verify-only reports runtime **UNRUN**.

For an authorized live run, remove `--verify-only`, use a fresh safe label and
explicit probe flags. Device transport is always selected with `adb -t`. The
runner refuses either existing validation package, including interrupted-run
leftovers; it does not silently remove them. Home must be the fixed normal
launcher before installing fresh disposable validation APKs. Installs use `-t`
and 90-second timeouts. Cleanup tracks attempts, including timed-out installs,
and only removes packages this fresh run attempted to install. It resumes the
normal launcher and verifies Home/services/normal package version readbacks.

Logs and summaries stay under the selected approved runtime `logs/` directory. Raw runtime
logs may include private device information; do not publish them. A process-death
check uses the separate host tool: instrumentation completion is not process-death
restoration evidence. Offline APK preflight is not a build, runtime or UX PASS.
No live run is authorized while the device is unavailable; root owns execution.

Fresh labels reject both an existing summary and any matching probe log, including
interrupted runs without a summary. Every log, including timeout output, uses the
same exclusive-create/0600 writer. Live preflight reads normal APK SHA-256 values
and hashes sorted shared-preference file contents through read-only run-as tar
(headers excluded); unavailable evidence stops before installation. Post-resume
readbacks compare these with version, Home and services. A mismatch is reported
as an observed cleanup/baseline change, not automatically a product defect.

The owner authorized an internal trial using exactly
`/path/to/carpe-tv-runtime` (`--runtime-root`),
which must resolve to that existing directory. Historical `/path/to/isolated-carpe-tv-runtime`
still requires a mounted volume. Arbitrary runtime directories remain rejected;
APK paths, CRC/metadata preflight, isolated-package and baseline guards are unchanged.

Use `--artifact-root` for an alternate Gradle output directory beneath
the approved local runtime or mounted `/Volumes/CarpeTVOverflow`. The
directory must contain the `app/outputs/apk/` tree. Logs still go to the
runtime selected by `--runtime-root`.

`feedbackbounded` caps only the owned Feedback dialog window at 1280×720 pixels,
reports its measured bounds, decodes all three QR crops from the screen, and
checks focus and scroll to Open and Close. The physical display and density stay
at their existing settings. Use `feedback` alongside it for the ordinary window.

`appauditroute` is an opt-in Xiaomi check for system app details and the app
list. It opens the Xiaomi Analytics choices, checks Back, then opens Manage
in Android and verifies the app-details Activity and the app label. The
Manage all apps button opens the TV Settings All Apps Activity. Back from
each system screen must restore the original Carpe TV control, and the four
audited package states must match the baseline.

`headerconnectivity` is opt-in for the Xiaomi MiTV-AFMU1 firmware where the
accessories slice requires `WRITE_SECURE_SETTINGS`. It sets the starting page
and Wi-Fi control in the Main, Apps and Settings headers, then uses D-pad OK,
Right and Back. It expects the TV Network screen for Wi-Fi, the main TV Settings
screen for the Bluetooth fallback, and the exact originating control after
each Back. It requires ordinary app access and compares the Wi-Fi and Bluetooth
settings before and after.

`headeredge` checks Wi-Fi → Bluetooth → timer, three Right presses at
the timer boundary and Left back to Bluetooth on Home, Apps and Settings.
It exercises both the idle icon and an app-private simulated countdown,
and checks that the blocked Right presses produce no focus or click event.
The probe never schedules an alarm or activates a header button.

`navigationaccessibility` reads the visible Android accessibility tree in
English and Ukrainian. It checks the selected/focused sidebar, names and
actions for the four header controls, the Apps search hint, and the accessible
name and long-click action of a visible tile on Home and Apps. The probe
uses Down to change pages and leaves the existing accessibility services
available. It does not invoke any header action or change device language.

`cataloglaunchdenied` requires the signed `--catalog-fixture` built with
`tools/catalog-fixture/build.py --deny-launch`. It requires the installed
fixture to resolve from the library, then checks for a real Android launch
denial after D-pad OK. Apps must retain query, tile focus, scroll and launch
history. Toast observation is reported separately because some Android TV
versions omit toast accessibility events. Use the ordinary fixture in a
separate `catalogbroadcast` run.

`catalogbroadcast` also installs the owned fixture during an uncommitted
Home favorite move with a direction held down. It checks rollback to the
stored order, the original tile focus, cleanup of move decoration and
translation, and isolation of the held repeat from the next fresh press.

`audioexternal` takes an ordinary `--catalog-fixture` rebuilt with the silent
audio witness. On Xiaomi it requires idle audio focus and playback before
launch. The host identifies the two installed UIDs and validates a token-scoped
playback counter and focus callbacks. A controlled loss and gain must be
observed before testing the interface sounds, cached video and Back. During
those phases the other app's playback head must advance without further focus
callbacks. Run `audiocoexistence` first if checking both modes in one run.

`catalogscanerror` is opt-in for the disposable catalog fault APK. In an
isolated copy, add a test-only guard at the start of the production scan's
`try` block in `MainActivity.refreshApps`. Have the guard throw
`IllegalStateException` only for the validation package while the
`validation_catalog_fail` Activity preference is true. The probe sets and
clears that preference, exercises failed and successful Retry with D-pad,
and checks preservation of a populated catalog during a warm failure. The
guard is confined to the isolated fixture source.

`libraryinputperf` opens the real Android keyboard and waits for visible IME
insets before starting. The probe uses four hardware-key edits over ten warmed
cycles and clocks from before injected input to the first app OnDraw with the
verified text and exact result set. Report per-operation medians and maxima.
Each operation has ten samples. This measures the app draw opportunity and
includes Android key dispatch.

`librarysoftkeys` expects the stock TV keyboard to expose visible keys and its
current D-pad focus through the input-method window. It navigates to two letters
using their geometry and sends D-pad OK only when each key owns focus. It checks
the exact filtered catalog after each character, Back to hide the keyboard
and Down to a visible result. The scope is injected D-pad with the current
keyboard layout. `libraryime` covers the hardware ASCII route separately.

`libraryartwork` measures the actual installed catalog after warming it,
emptying the query results and evicting the artwork LRU, then repeating a
short Apps–Settings round trip. It lists allocation bytes in the active
page tree, retained page trees, the LRU and detached tiles, plus a
bitmap-identity-deduplicated total. It checks the configured LRU and pool
limits and the detached tiles' lack of a parent. These snapshots do not
cover in-flight decode buffers, process memory or a multi-hour soak.

`settingscontrast` measures the active reset-dimming and sound-preview labels
with and without focus in English and Ukrainian on all built-in wallpapers.
Blue is sampled at both motion endpoints and the midpoint. Window PixelCopy
pairs visible glyph interiors with the same pixels while the text is transparent.
The minimum must reach 4.5:1. The probe reports each sample and restores the
isolated app's preferences. The sampled viewport and phase positions define
the coverage of this check.

`homecontrast` measures the Home clock, date, greeting and the first
visible favorite label over the built-in wallpapers. Photos use 0%, 30%
and 60% dimming. The probe checks the label with rail and tile focus,
confirms that the focus outline changes state, and applies the same 4.5:1
threshold to the sampled glyph interiors. Its measurements concern the
application frame, with wallpaper and focus animations disabled.
The same run selects Alpine from Blue, resets dimming to 60% and selects
Blue again through the Appearance controls. It verifies that returning
with Back retains the Home views and favorite focus, with the proper
backing for each combination.

`focusring` measures the first tile on Home and Apps using controlled
white, gray and black artwork. With the tile focused, Window PixelCopy
pairs the same pixels with the outline visible and hidden. Each sampled
edge center must include at least one outline pixel with 3:1 contrast.
The tile center is checked to be unchanged. The capture uses the settled
focus state.

`audiocoexistence` expects idle device media with no existing audio-focus
owner. It uses owned focus listeners, a silent looping AudioTrack, low-volume
app sounds and an 8-second synthetic H.264/AAC fixture in the private saver
cache. The fixture has a flat color frame and a quiet generated sine track.
The test verifies the focus-loss/focus-gain control, actual cached video and
track progress, and the absence of new focus callbacks during both routes.
The test uses same-UID clients and excludes hardware audibility or the behavior
of a separate media player's audio policy.

`corruptsaverphoto` uses an isolated offline saver session and two private cache
fixtures. The truncated PNG retains its header and demonstrably produces partial
pixels with BitmapFactory. The saver must skip it, remove its cache pair and fall
back to a built-in photo. A complete JPEG must display with attribution and stay
cached after Back. An executor gate queues a second decode of the JPEG while
Updates pauses the saver. The test uses the private file access timestamp and
drawable identity to verify the queued request was skipped, then waits for a
fresh frame on resume. Back with a new queued request must stop the executor
and leave no cache pins. The gate controls work before decoding.

`feedback` also opens Device Diagnostics by D-pad, reads the rendered raw QR
from the screen, and follows Report a bug into the Bug Issue QR. The seven
report lines must match exactly. Both dialogs must return focus to the same
Diagnostics control after the tested Back and Close routes.

# Short matched release rail / gfxinfo diagnostic

`run_release_gfxinfo.py` is root/device-operator tooling. Source preparation does
not run ADB or build anything. `--verify-only` performs only local artifact checks.

```
python3 tools/run_release_gfxinfo.py --adb APPROVED_ADB --aapt APPROVED_AAPT \
  --runtime-root /path/to/carpe-tv-runtime \
  --transport-id 1 --expected-version-code CURRENT_CODE \
  --label FRESH-LABEL --verify-only
```

Exact release path: `build/app/outputs/apk/release/app-release.apk` under the
approved runtime. Use `--artifact-root` for a separate Gradle output directory
below the approved local runtime. Historical SSD runs may select an output
directory below mounted `/Volumes/CarpeTVOverflow` when using the SSD runtime.
Keep the same `app/outputs/apk/release/` layout. Logs go to the selected
runtime. Every path requires CRC, actual/Gradle package+code, variant release,
non-debuggable badging, and pinned test signer. Only exact validation app is
installed; existing validation app/test packages are refused. Normal APK hashes,
canonical preferences/version/Home/accessibility are captured before mutation,
with private atomic manifest before attempted installs and owned cleanup after.
No settings writes, performance mode, clock locking, HDMI changes or Home key /
category. System animator scales, API and physical size/density are read-only
metadata; display dump stays private. Test-signed release is not public release.

Actual UIAutomator trees must identify the validation package. If welcome is
present, only the exact localized Explore Button within the owned Welcome title
can be activated, using its actual focused state or visible bounds. Make Home is
never activated. Bounded Left-only navigation is allowed only while actual Home
is selected/shown, to reach nav_home. Failure to prove the exact selected/focused
foreground rail and page token is INPUT_UNVERIFIED, never guessed navigation.

Two unmeasured Home→Apps→Settings→Apps→Home cycles verify every page/rail.
After a two-second UI settle, collect two samples in the same process and APK:

- Prove foreground Home/nav_home, then reset only this app's gfxinfo counters.
- Inject 20 cycles of Down/Down/Up/Up. The minimum requested key-start gap is
  300ms with the default `--cadence paced` and 100ms with `--cadence rapid`.
  Both modes use the same 300ms warmup.
- Record actual monotonic command start/end for all 80 keys; ADB overhead may
  increase the gap. Raw timestamps and min/median/max actual gaps are authoritative.
- After final settle, capture gfxinfo summary/framestats, then prove foreground
  Home/nav_home and unchanged PID. No UIA dumps run inside the measured loop.

This is sampled route proof: warmup proves all pages, each sample proves its
boundary rail; it does not claim every intermediate event had an observed page.
With `--device-paced-input`, a single bounded ADB shell runs the 80 measured
events with the same minimum gaps. The shell records each event before and
after its local `input` call using `/proc/uptime` at 10 ms resolution.
The shell has a 60-second device deadline before the longer ADB deadline.
This removes the repeated host ADB command setup while retaining possible
device-side input and shell stalls. These times come from a different clock
than host mode and do not measure physical button-to-display latency.
Summary records total frames, janky numerator/fraction, separate legacy numerator
and available Android percentile fields. When exactly one usable profile is
available for that process, the summary also records `rolling_window` with its own
row counts, GPU deadline misses, dequeue wait, and timestamp-based presentation
latency and cadence. Missing, ambiguous, or unusable profiles yield a null
rolling window while leaving the process counters intact. The window omits
flagged frames and counts presentation timestamps only when they occur after
GPU completion. The CSV interval field is unused because Android 14 can label
its frame interval and frame start slots in reversed order. Keep raw framestats
and use the process total for aggregate fractions. Do not
combine these samples with old mixed FrameMetrics populations or a rapid run.
Some Android reports also repeat the counters for each window. Use the process
aggregate under the exact package and PID header once, before the window sections.
Rapid uses sequential injected keys and records achieved cadence. It does not
simulate holding a button or hardware repeat. No forced GC, latency budget or stable60fps / physical
remote / perceived projector smoothness claim.

The presentation duration starts at the frame's intended vsync. It excludes
the time from the physical button or ADB command to that vsync. Gaps measure
adjacent distinct presentation timestamps in the retained window. They can
include intentional idle periods between animations.

Fresh isolated defaults for rotation, animation and sound are recorded from
source. The default wallpaper is read from the selected gallery tile during
warmup. With `--motion on` or `--motion off`,
the runner first visits Appearance in the isolated app, finds the actual
animation switch, and confirms the requested checked state on-screen.
The two arms use separate fresh installs of the same APK. Use the same cadence,
record actual key gaps, and compare process counters and rolling frame timings
with their respective denominators. Fewer frames with motion off can include
intentional idle gaps. The default `--motion default` skips the switch route.
Use `--wallpaper blue` to select the blue wallpaper through the isolated
gallery with D-pad, verify its selected state, and record it in the summary.
Use this option in both arms when comparing the moving and static blue glow.
Use `--wallpaper plain` to choose and verify the static dark wallpaper
with the same app animation setting. Use `--wallpaper alpine` to choose and
verify the Alpine photograph. For a
scene comparison, run Blue, Alpine, Alpine, Blue on the same APK and device
using fresh installations and the same motion and device-paced cadence flags.
The animation can draw extra frames between keys. Report both frame totals
and jank counts for each sample alongside the measured cadence. Frame share
alone cannot establish the rendering cost of a transition.
Warmup is UI-based, not an asserted executor/artwork drain. Both samples have the
same protocol/cache setup; small timing differences remain diagnostic observations.

Owned UI dump staging has a fresh label/path guard and independent removal.
Raw UI/display/gfxinfo/key logs and atomic summary are private 600 in runtime logs.
Individual commands/waits are bounded; workload is exactly two 20-cycle samples,
not a long soak. Failed/offline cleanup remains explicit; only attempted owned
validation is stopped/uninstalled, normal Main resumed and full baseline compared.

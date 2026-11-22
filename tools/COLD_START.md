# Isolated platform cold-start diagnostics

Source-ready; root alone runs device checks. No AndroidX/new dependencies,
Gradle, production edits or device actions are performed by preparing this tool.

```
python3 tools/run_cold_start.py --adb APPROVED_ADB --aapt APPROVED_AAPT \
  --runtime-root /path/to/carpe-tv-runtime \
  --transport-id 1 --expected-version-code CURRENT_CODE \
  --label FRESH-LABEL --samples 3 --verify-only
```

Remove `--verify-only` only when the root/device operator is ready. Allowed sample
counts are 3 or 5. Verify-only performs full ZIP CRC and actual APK/Gradle package /
version / hash checks without ADB. Only the fixed validation APK is installed;
pre-existing validation app/test packages are refused. Normal installed APK hashes,
canonical preference contents, version, Home role and accessibility services are
captured before mutations and compared after cleanup. Summary is private 600,
atomic and persisted before each attempted install; raw launch logs are private.
Only the approved exact local runtime or mounted historical SSD is accepted.

Each sample force-stops **only** `agency.carpediem.tv.validation.publicpreview`,
requires its PID to disappear, then starts its explicit MainActivity with
`am start -W`. No HOME action/category/key, role changes, system settings writes,
other package force-stops or compilation-state changes. A valid sample requires
Status ok, exact Activity, LaunchState COLD and a distinct new validation PID.
All timing fields and PID witnesses remain in private artifacts. Missing COLD /
PID / timing proof makes the measurement unverified, not a performance failure.

Report `am TotalTime`, optional `ThisTime`, and `WaitTime` separately, with raw
samples and descriptive min/median/max of TotalTime. This is the platform's
process-cold first-display diagnostic on a debug isolated APK, **not** a release
Macrobenchmark, TTFD, first-usable UI or physical remote-to-display measurement.
It does not prove artwork readiness or impose a latency budget. Data/disk caches
remain between launches; the first sample follows a fresh isolated install.
Onboarding is not clicked or suppressed, so the initial screen may include the
welcome panel. No claim of a previously onboarded user's startup is made.

Every command and wait is bounded (install 90s, launch 45s, PID queries/polls 5s,
other baseline commands 45s); 3/5 fixed iterations cap work. Finally force-stop /
uninstall only an attempted owned validation installation, explicitly resume the
normal MainActivity and verify normal state. Host interruption/offline cleanup
requires independent root readback before a later run; never auto-remove unknown
leftovers. Existing 180-second instrumentation runner is unchanged.

`--build-type release` selects only
`build/app/outputs/apk/release/app-release.apk` beneath the approved runtime.
It requires actual aapt badging to omit `application-debuggable`, matching
Gradle `variantName=release`, and the same exact validation package/code.
The summary records requested/verified variant and actual debuggable status.
Default `--build-type debug` retains the original path/behavior. Root prepares
the isolated release APK with its test signing certificate and feed disabled;
this runner does not install the normal package or claim to verify feed content.
Even non-debuggable release measurements remain `am start -W` process-cold
first-display diagnostics, not AndroidX Macrobenchmark, TTFD or first-usable UI.

`--check-first-input` adds a separate cold interaction check. The host verifies
the pinned test signer, dismisses the isolated welcome through its focused
Explore action once, and keeps the resulting app data for the samples. After
each cold `am start -W`, a single device shell checks the exact foreground
Activity and injects one D-pad Down. A followed-up accessibility snapshot must
show the selected and focused Apps rail, the search field and a catalog item,
all in the same validation process. No extra keys are sent to obtain this proof.

Device uptime markers record the time between first-display command return and
injection start, the key-command duration, and an observed upper bound from
launch to the verified Apps state. The last number includes UI-dump overhead.
The option uses a fresh owned temporary UI file, which is removed during normal
cleanup. Verify-only still performs no ADB calls.

## First usable Home and Apps draws after cold launch

`--check-library-draw` pairs with `--build-type release --check-first-input`.
For this mode, build the non-debuggable, test-signed validation APK with
`-I tools/cold-draw-fixture/validation.init.gradle`,
`-Dcarpe.coldDrawRuntime=APPROVED_RUNTIME`, `-PisolatedPreview` and
`assembleRelease`. Use the same Gradle, SDK and reviewed SPAKE2 AAR as the
release candidate. The init script keeps the update feed disabled. Its
manifest and observer sources are absent from a normal release or owner build.
The host rejects an APK whose observer does not match the requested mode.

The validation-only observer records the first Home draw with a selected,
focused rail and a visible actionable favorite. It also records the first
Apps draw with its rail focused, an enabled search field and an actionable
tile. The runner requires both markers from the new validation PID, with
Home before the device's only D-pad Down and Apps after it. The following
accessibility snapshot independently checks the enabled search and a
visible focusable, clickable tile with a label.

Report the launch-command-start and key-command-start deltas separately from
platform first display. These deltas use device elapsed time with 10 ms shell
marker resolution and include shell dispatch overhead. The observation covers
UI draw readiness in the measured configuration. It does not time physical
input or display presentation, confirm complete artwork, or represent a
fully cold filesystem or uninstrumented production APK.

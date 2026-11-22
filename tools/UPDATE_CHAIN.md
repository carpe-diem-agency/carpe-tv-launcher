# Real updater feed → download → Ready

Root/device operator only. `run_update_chain.py` stages a newer APK but never
installs it. Android `updatechain=yes` exercises actual production check/feed,
HEAD, DownloadManager GET, and default package/version/signer/hash validation.
A second actual download must fail the wrong-SHA gate. No installer/session,
unknown-source permission, system settings, Home action or external server.

```
python3 tools/run_update_chain.py --adb APPROVED_ADB --aapt APPROVED_AAPT \
  --runtime-root /path/to/carpe-tv-runtime \
  --transport-id 1 --expected-version-code INSTALLED_FIXTURE_CODE \
  --candidate-apk /APPROVED_RUNTIME/SEALED_NEWER/app-debug.apk \
  --candidate-version-code STRICTLY_NEWER_CODE --candidate-sha256 EXACT_SHA256 \
  --label FRESH-LABEL --verify-only
```

Candidate must reside beneath the approved runtime and have its matching sibling
`output-metadata.json`. Verify-only invokes no ADB. App/test/candidate CRC and
actual APK/Gradle packages/codes are checked. All three signers must equal the
known public test certificate `4434…af8a`, verified by SDK apksigner under Java17
(`--java-home` defaults to the approved Homebrew Java17). Candidate SHA is required
explicitly and size must match production bounds 1KiB–50MiB. Same package is
always `agency.carpediem.tv.validation.publicpreview`; strictly newer version is
mandatory. The normal launcher cannot be a candidate or installation target.

With `--artifact-root`, Gradle outputs can live in a separate child directory
of the local runtime's `tmp/` or `fixtures/`. The mounted
`/Volumes/CarpeTVOverflow` remains supported for older runs.
Logs still go to `--runtime-root`. Use `--require-download-retry` to require
the live APK GET failure and recovery witness from current instrumentation.
Use `--require-dpad-retry` to require the corresponding live update-screen
route with injected D-pad keys, which keeps installer and settings actions
inactive.

Host refuses existing validation app/test packages, captures normal APK hashes,
canonical preferences/version/Home/accessibility baseline and atomically persists
private summary before attempted installs. Only the older validation app/test are
installed. Fresh app-private `files/update-feed-fixture` is required; host creates
it then streams candidate bytes using bounded `adb shell -T run-as` to exactly
`files/update-feed-fixture/newer.apk`. Remote and local sealed SHA must agree
before instrumentation. Test args: `candidateSha256`, `candidateVersionCode`.

Actual Android probe owns its loopback feed server, new DM rows and updater
preference snapshot; it returns `updatechain`, `updatechain_wrong_sha` PASS and
`updatechain_cleanup_verified=true` only after app-UID row cleanup, server stop
and full preference restore. Host requires all fields and successful instrumentation.
Use `--require-feed-timeout` to require evidence that the exact test accepted a
feed request and withheld its response through the production read timeout.
The probe verifies the timeout message and Check focus, presses D-pad OK, and
requires recovery to Available without starting an APK download.
It force-stops only owned validation, removes only its staged file/directory,
uninstalls attempted validation packages, resumes normal MainActivity and compares
all normal baseline fields. Missing row-cleanup proof is reported independently;
uninstall is not treated as a DM row-query witness.

Install/stage deadlines are 90s, individual preflight/baseline commands 15–45s,
probe deadline 330s, or 540s when requiring the additional APK GET failure and
recovery. Each wait within the probe also has its own deadline.
Private 600 logs/atomic summaries contain artifact hashes and outcomes. Interrupted
or offline cleanup requires root investigation; pre-existing installations are
never auto-removed. This is local isolated debug updater integration, not public
release distribution or installation evidence. No code/device actions occur just
by preparing this source protocol.

## Matching APK with another signer

Add both `--wrong-signer-apk` and `--wrong-signer-sha256` to require the
production signing-certificate rejection. Use a copy of the newer validation
APK re-signed with a disposable local key. Preflight requires the same
validation package, version code and version name, the exact staged hash, and
a different valid signer. The optional APK is streamed solely into the test
app's private staging directory and served by a second loopback feed.

The probe checks actual HEAD and DownloadManager GET, the specific signing
error, focus on Check, hidden Install, and cleanup of the failed download.
The regular pinned-signer download then has to reach Ready in the same run.
The optional APK and loopback feed are cleaned up by the same owned protocol.
This exercises the updater's validation screen before any installation request.

# Real updater enqueue / process death / recovery

Root owns runtime execution. New guarded runner `run_update_enqueue_crash.py`
uses only the fixed disposable validation app/test packages and existing APK
outputs. It reuses full ZIP CRC, actual APK/Gradle version/package/instrumentation
checks, exact approved runtime paths, fresh-package refusal, normal launcher APK /
canonical preferences / version / Home / accessibility baselines, private atomic
summary before attempted installs and owned uninstall/resume cleanup.

```
python3 tools/run_update_enqueue_crash.py --adb APPROVED_ADB --aapt APPROVED_AAPT \
  --runtime-root /path/to/carpe-tv-runtime \
  --transport-id 1 --expected-version-code CURRENT_CODE --label FRESH-LABEL \
  --verify-only
```

Remove verify-only only when root is ready. No ADB is called in verify-only.
The app/test hashes, script hash and random canonical nonce are recorded. The
runner never opens Main before recovery, which could otherwise consume journal
state before independent observation.

Phase `updateenqueuecrash=yes, phase=prepare, nonce=UUID` starts the production
`UpdateManager.download` through a delegating test-only SharedPreferences wrapper.
Its app-owned loopback HEAD server may die with the target process. After actual
DownloadManager enqueue, the wrapper syncs private
`files/update-process-death-fixture/witness.json` and kills only its exact isolated
PID immediately before delegating the editor commit that would publish downloadId.
The wrapper does not synthesize the production journal or a failed commit.
HTTP GET completion and APK validation/installation are explicitly outside scope.

Expected instrumentation failure alone is never success. Host independently reads
the witness and real `shared_prefs/updates.xml`: exact schema/package/nonce, positive
old PID/owned row ID, canonical operation UUID, exact UUID filename and loopback URI,
enqueuePending true and no published downloadId. Old PID must disappear (a different
receiver-started PID is allowed). Private raw witnesses are saved before phase B.

Phase `recover` must prove a different process, inspect the actual app-UID DM row
before calling production recovery, verify exact row/file rollback and durable
journal cleanup, then restore its private preferences snapshot and fixtures. Host
requires exact nonce/old/new PID result fields, explicit verified-row-cleanup result,
successful instrumentation and a final durable preferences readback. Host never
reads/deserializes `preferences.snapshot`. That private Java snapshot is consumed
only by the trusted Android test fixture for its own restore.

Every phase command is bounded to 120 seconds; installs 90 seconds; other commands
45 seconds; PID observations use 5-second commands / eight-second polling. No adb
reverse, external server, system settings, Home key/action, role mutation or task
restore claim. All logs are private 600; summary uses atomic fsync + replace.

Prepare intentionally skips Java finally by killing the process. If recovery
cannot independently verify owned row cleanup, the host reports cleanup FAIL /
UNVERIFIED even if owned validation uninstall succeeds. Do not mistake uninstall
for a DM row-query witness. Retain private artifacts for root investigation; never
auto-remove a pre-existing validation installation or mutate the normal launcher.

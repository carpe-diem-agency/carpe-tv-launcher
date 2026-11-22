# Long session validation

Root/device operator only. Use the same app/test APK hashes for smoke and full runs.
First run `--verify-only`, then `--blocks 2` (ten minutes), then `--blocks 24`
(two hours plus bounded warmup/recreation/cleanup). Each invocation installs fresh
isolated validation packages and runs one instrumentation/process throughout;
never compare memory across the two invocations as a continuous session.

```
python3 tools/run_long_session.py --adb SSD_ADB --aapt SSD_AAPT \
  --transport-id 1 --runtime-root /path/to/isolated-carpe-tv-runtime \
  --expected-version-code 30 --label UNIQUE-LABEL --blocks 2
```

The independent runner retains the normal launcher APK/preferences/version/Home/
accessibility baseline guards, refuses existing validation packages, streams status
into a private runtime log, and uninstalls only packages it attempted to install.
Existing `run_validation_probes.py` and its 180-second limit are unchanged.
Interrupted device/offline cleanup remains unverified until operator readback.

Each five-minute block repeats paced D-pad navigation, editor InputConnection
matching/no-match/clear against actual installed labels, one rotating Authors
notice with Back focus checks, Device viewing and Audit dwell beyond five seconds.
Remaining block time is spent at canonical Home. App-only KEEP_SCREEN_ON prevents
screen timeout; no Home role, system settings, remote URLs, cache clearing or update
controls are activated. Every fourth block verifies Authors recreation context,
quiesces and drops the old Activity fixture, and warms the replacement again.

Checkpoints after both executors/UI drain report active and cached-only view
identity counts, actual pool bytes/count against production caps, inactive Audit
scheduler, and PSS categories. No forced GC, arbitrary memory threshold, frame/
latency or leak certification. Raw per-block PSS is descriptive: compare warmed
baseline, final three samples and a repeated identical run before investigating
retention. Process PID continuity and all checkpoint populations are mandatory.
Whole Main preference snapshot is restored only after Activity/workers quiesce.

For the owner-authorized internal trial, `--runtime-root` also accepts exactly
`/path/to/carpe-tv-runtime` as an existing resolved directory.
The historical `/path/to/isolated-carpe-tv-runtime` path still must be mounted. No arbitrary
root is allowed; all integrity, package, baseline and cleanup guards remain.

The host deadline additionally allows90 seconds per fourth-block recreation/rewarm,
beyond the five minutes of block work. It does not shorten or change the device
workload; full-session host limit is bounded at134 minutes including overhead.

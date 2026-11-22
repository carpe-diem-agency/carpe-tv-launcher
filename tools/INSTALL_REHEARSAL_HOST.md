# Opt-in rehearsal host — source ready, not executed

`run_install_rehearsal.py` is separate from the ordinary auto-cleanup updater
runner. It never clicks an app/system Install CTA, opens Settings, grants trust,
creates/abandons installer sessions or invokes an APK upgrade. Those actions are
outside this tool. Preparing source does not authorize runtime execution.

Without `--execute`, every command is device-free: prepare verifies local artifacts
and prints a plan; inspect/cleanup print the retained manifest. Local aapt /
apksigner checks are allowed, but no ADB is called. Runtime prepare additionally
requires `--owner-approved --approval-reference OWNER_MESSAGE_REFERENCE`. This
records operator confirmation of prior explicit owner consent; the flag does not
obtain or authenticate consent. Inspect/cleanup reuse that persisted approval and
require `--execute`, so no repeated owner permission is invented.

## Sealed inputs

Use `--pair 62-to-63` for the isolated 0.2.55 → 0.2.56 chain. The historical
pairs `59-to-60`, `47-to-48`, `45-to-46` and `36-to-37` remain available.
Existing commands still default to `36-to-37`.

```
fixtures/installer-rehearsal-<PAIR>/old/app-debug.apk
fixtures/installer-rehearsal-<PAIR>/new/app-debug.apk
fixtures/installer-rehearsal-<PAIR>/proof.json
```

Each APK needs matching sibling output-metadata.json. proof.json records the
ordinary chain with runtime/cleanup PASS and exact old/new hashes.
Supply opt-in instrumentation separately,
with explicit SHA, exact validation test package/code0, target/name and pinned
4434…af8a signer checks. Seal that test APK and metadata before starting: later
inspect/cleanup require its installed hash to match the retained manifest.
All APKs are CRC checked and actual package/code/signer verified. The candidate
additionally requires the exact fixture versionName for the selected pair and application `testOnly`
absent/false in the binary manifest (badging testOnly is rejected too); candidate cannot
be the normal package. The pair and test are never replaced by working outputs.

```
python3 tools/run_install_rehearsal.py prepare --adb APPROVED_ADB \
  --aapt APPROVED_AAPT --runtime-root APPROVED_RUNTIME --transport-id 1 \
  --pair 62-to-63 --label FRESH-LABEL --test-apk APPROVED_RUNTIME/SEALED_TEST/app-debug-androidTest.apk \
  --test-sha256 EXACT_NEW_TEST_SHA256
```

The example is a local preflight only. Only after separate owner approval, root
may repeat it with `--execute --owner-approved --approval-reference ...`.
No rehearsal runtime execution is authorized by this document.

## Runtime behavior after approval

Prepare refuses any installed validation app/test. It captures normal APK /
preferences/version/Home/services baseline, persists atomic 600 ownership manifest
before attempted installs, installs only its old validation app and sealed test,
and streams the exact new candidate to fresh app-private
`files/update-feed-fixture/newer.apk` with bounded `adb shell -T run-as`.
Remote SHA must match before opt-in instrumentation.

Android `updaterehearsal=yes phase=prepare nonce=UUID` runs actual feed/download /
production verification, retains its own Ready state and snapshots, and stops its
server. Host validates exact package, nonce, sealed versions/hashes, UUID filename and owned row
witness. Read-only inspect records installation-source eligibility. False means
`BLOCKED_SETTINGS_CONSENT`: no automatic Settings route or app CTA follows.

Inspect uses `phase=inspect` only. Installed app must exactly match the sealed
baseline or candidate; test APK must match the retained signed hash. It records
actual session, state, version and eligibility. The upgraded version requires
the exact old backup. Ready or canceled in the baseline version is valid
without a pre-install backup. Normal APK/version/Home/services remain
hard guards. Interim normal preference hash is observational while fixture apps
are installed; final cleanup must match the complete original baseline.

Cleanup first inspects. A present/unknown session or installing/confirming state
blocks all mutation and requires owner resolution. Android independently repeats
that check and refuses unrelated new operations. Only verified own row/target /
snapshot cleanup permits host staging removal and uninstall of attempted own
validation/test packages. It then explicitly resumes normal Main and checks the
full original baseline plus validation absence. No automatic reinstall/downgrade
or baseline preference overwrite is performed.

```
python3 tools/run_install_rehearsal.py inspect COMMON_ARGS --execute
python3 tools/run_install_rehearsal.py cleanup COMMON_ARGS --execute
```

COMMON_ARGS are the same adb/aapt/runtime/transport/label. A changed host script
or imported guard helper hash stops resume for root review. Private state lives in
`RUNTIME/install-rehearsals/LABEL/manifest.json`, with unique private phase logs.
Failures retain state (`needs_inspection`) instead of racing a dialog/commit with
automatic uninstall. Partial install/preparation failures may lack a complete
Android witness; cleanup fails closed and root must inspect exact ownership.
Do not delete the manifest or treat missing fixture data as successful cleanup.

All device commands are bounded: install/stage90s, phase180s, baseline45s,
aapt30s/apksigner45s. Existing runners and their defaults are unchanged. See
INSTALL_REHEARSAL.md for the separate future owner-led CTA/confirmation protocol.

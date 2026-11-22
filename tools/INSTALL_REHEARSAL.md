# Isolated PackageInstaller rehearsal — approval protocol

**Reviewable plan only. No device actions or persistent Ready fixture are prepared
by this document.** Current updater-chain runner remains unchanged and always
cleans up. Ordinary feed/download/Ready proof is not install evidence.

## Exact candidate and evidence packet

Use the sealed pair selected in [the host instructions](INSTALL_REHEARSAL_HOST.md).
For the 0.2.40 chain, the packet uses these identities:

- Package: `agency.carpediem.tv.validation.publicpreview` only.
- Candidate: `0.2.41-updater-fixture`, versionCode **48**.
- Baseline: `0.2.40`, versionCode **47**.
- Test signer SHA-256: `4434b55f94de7a3470a4c623b5f0bff9f015fc4e0f975a9cfd15f62e5b86af8a`.
- The sealed `proof.json` records the exact APK/test APK hashes and successful
  **47 → 48** chain and cleanup before the approval step.

Seal both APKs plus output-metadata and proof summary under one fresh private
runtime rehearsal directory. Record their full hashes and source/test revision.
Recheck CRC, actual package/code/versionName, same signer, NEW not testOnly, and
artifact hashes immediately before preparation. A rebuilt/different hash requires
a new packet. These are isolated test-signed artifacts, not a public release or
normal-launcher upgrade. The normal package is never an install target.

## Prepare gate — no installer or Settings action

1. Obtain explicit owner consent for the selected isolated install rehearsal,
   including retaining its own validation state while waiting for confirmation.
   This does **not** authorize changing Android Settings or unknown-source trust.
2. Fresh-package guards and normal APK/preferences/version/Home/accessibility
   baseline are mandatory. Persist private atomic recovery manifest before any
   attempted installation. Record old/test/new hashes, nonce, own rows/files and
   all phase transitions; never use a previous unknown validation installation.
3. A separate opt-in `prepareReady` test phase is needed. It must execute the same
   real feed → HEAD/GET → DM → production verifier path, then retain Ready, exact
   metadata/UUID/row/file and a full private preference snapshot. It must stop its
   HTTP server/workers, without canceling/restoring the retained fixture. Existing
   default chain behavior stays unchanged. Source for an opt-in phase/host is prepared separately; runtime approval and verification remain pending.
4. Open Updates through the genuine isolated Main/About action. Capture the
   installed baseline identity, real Ready state, validated candidate SHA/size, exact
   visible install CTA/resource/focus, window package and read-only
   `canRequestPackageInstalls` result. Do not invoke install directly/reflection.
5. If eligibility is false, stop **before clicking**: production would open
   `ACTION_MANAGE_UNKNOWN_APP_SOURCES`. Report `BLOCKED_SETTINGS_CONSENT` and ask
   separately if the owner wants that app-specific Settings route. No Settings
   grant, app-op, permission adoption or shell bypass is part of this plan.

## Inspect / owner handoff / resume

Before any CTA click, present the exact packet and eligibility screenshot to the
owner. With eligibility already true and owner consent valid, activate only the
genuine isolated app Install CTA, once. Production must validate again, create
`update-rollback/previous.apk` and preferences.json, persist its bound session and
commit through PackageInstaller. Capture the owned session ID, binding UUID,
expected app/version and backup OLD hash before confirmation when observable.

At the Android installer confirmation, **hands off**: the coordinator does not
click Install/Update or automate approval. The owner confirms or cancels the
visible request. Capture the window/package and displayed target before handoff;
Carpe TV labels alone are insufficient to distinguish normal and validation apps.
The already-bound session's package must be the validation package. An unexpected
window, permission dialog or target is a stop condition. No automatic timeout
uninstall may race an owner-visible confirmation.

Resume observation only after the owner reports completion/cancel, or explicitly
asks to stop the rehearsal. Self-upgrade may kill instrumentation, so its success
bundle/finally is not the oracle. Use an independent guarded host and a fresh
app-UID inspection phase after replacement. Do not reopen Main while a late
callback may still be settling; observe terminal package/session/result first.

Successful installation requires the actual installed candidate version, exact NEW installed
APK hash/cert, preference sentinel/type preservation across the replacement,
OLD backup hash and namespaced preference export, and real bound callback/session
cleanup. A visible version label or vanished session alone is insufficient.
Cancellation requires the installed baseline unchanged, a retained verified retry artifact,
correct ready/cancelled feedback, retired owned session and no data loss. Record
actual callback delivery separately if platform replacement prevents observing it;
do not synthesize success or silently substitute `adb install -r`.

## Interrupted cleanup / recovery manifest

Persist before CTA: baseline, sealed hashes, nonce, exact package, staged paths,
owned DM IDs, binding UUID, session ID if created, preference snapshot location,
last observed installer window and installed version. Keep all raw evidence 600.
The preference snapshot is private; never publish or print its contents.

- Before CTA: opt-in app-UID cleanup may cancel only its matching operation/rows,
  restore its snapshot, finish workers, then host removes only attempted own
  validation/test packages and staged files.
- During confirmation/commit: classify `INTERRUPTED_PENDING_INSTALL`. Retain the
  manifest and inspect the exact session plus installed package before mutation.
  Do not uninstall while a dialog/commit may still complete. Ask the owner to
  finish/cancel the confirmation. Only app-UID cleanup of a verified own session
  may abandon it; never enumerate-and-abandon others or use shell install as a
  replacement. Unknown ownership remains blocked for investigation.
- After terminal success/cancel: preserve witnesses, perform app-UID own-row/file
  cleanup, then uninstall only this rehearsal's validation/test installation.
  Explicitly resume normal Main and verify original APK/preferences/version/Home/
  services. A missing row-query or baseline readback means cleanup unverified,
  even if uninstall succeeds.

No Home action/key/role changes, system service changes, Settings writes or public
publication. Separate opt-in prepare/inspect/cleanup source now exists; see
INSTALL_REHEARSAL_HOST.md. Its default performs no ADB. Runtime still requires
prior explicit owner approval, and genuine app CTA / hands-off Android installer
confirmation remain outside the tool. Existing auto-cleanup runner is unchanged.

# In-app updater — implemented baseline

Version 0.2.0. Implemented and device-tested; permanent delivery is not configured.
The app now requests INTERNET and REQUEST_INSTALL_PACKAGES. It uses Android framework
APIs only, with no new runtime dependency.

## Implementation

- `UpdatesActivity`: one D-pad-accessible native panel, check/download/install/cancel,
  optional automatic downloads, permission guidance, progress and English/Ukrainian text.
- `UpdateManager`: bounded metadata fetch, daily-on-visible check when a feed exists,
  size/free-space checks, same-app/higher-version/minSdk/SHA-256/signer validation,
  a single pending download and native PackageInstaller session.
- Android `DownloadManager`: continues downloads when a different app is foreground.
- `UpdateDownloadReceiver`: queries the owned download ID and validates completed APKs;
  no automatic installation or interruption of another app on download completion.
- `UpdateInstallReceiver`: handles user confirmation, cancellation, failure and success.
- App-private checkpoint: one previous APK plus supported MainActivity preferences.
  It is not uploaded and is not a full private-data dump. Temporary APK is cleaned
  after success/cancel/download failure. System download size is checked before
  enqueue and on progress/completion; this is not a custom hard-capped network stream.
- Interrupted checking/preparing states from a previous process are recoverable;
  lost installer sessions permit retry instead of a permanently disabled panel.

Only trusted configured endpoints should be used. Metadata is limited to 64 KiB and
APK size to 50 MiB. Native installer performs final cryptographic APK verification.
Checksums alone do not establish publisher identity. Signing key rotation is not
supported by this first validator; current signer sets must match exactly.

## Metadata

Schema 1: channel, applicationId, versionCode, versionName, minSdk, apkUrl, byteSize,
sha256, releaseNotes. `scripts/make_update_manifest.py` generates these fields from
an actual APK. The signed draft workflow attaches `update.json` with APK/checksum.

A compiled HTTPS feed may return metadata directly, or GitHub's latest published
release response with an `update.json` asset. Draft/prerelease GitHub responses are
rejected by that public/stable path. Debug direct metadata may use the `internal`
channel and a private LAN HTTP URL. Debug test routing is not enabled in release builds.
The release variant embeds `https://api.github.com/repos/carpe-diem-agency/carpe-tv-launcher/releases/latest`
by default; debug has no feed unless a test URL is supplied at build time.
Redirects use same-origin or recognized HTTPS GitHub asset hosts, without auth headers.

Checks happen at most once per 24 hours on launcher resume, not via a permanent service.
Downloads use unmetered, non-roaming networks in this build. Auto-download is off until selected; downloading and installation are separate actions.
There is no shared GitHub token in source or APK.

## Current delivery and signing constraints

The private test used a temporary LAN endpoint and the same debug signing identity
as the existing installation. The final APK is saved as a private internal release.
No public binary repository, permanent update URL, gateway or production signing
key has been created. Private GitHub assets still need authorization; anonymous
clients cannot fetch them just because the updater exists.

Recommended deployment remains private source + a separately approved public binary
channel. If binaries must stay private, an authorized delivery design is still needed.
Choose and back up a stable release key before general rollout. Switching from this
debug identity to an unrelated key needs an explicit migration, not an in-place update.

## Recovery

Android normally rejects a lower versionCode. Retaining an old APK does not guarantee
a user-installable downgrade. Internal debuggable builds can be recovered with a
matching-signed APK via authorized ADB downgrade; general distribution should use
a newer-versionCode recovery release and compatible preference migrations.

See VALIDATION.md for actual test evidence and limits. Public rollout still requires
PUBLICATION.md. No workflow changes repository visibility automatically.

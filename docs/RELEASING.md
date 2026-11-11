# Carpe TV releases

This public source uses Android application ID `agency.carpediem.tv`. It is a
separate app from the personal development build, which uses
`agency.carpediem.tv.prototype`. Installing one does not replace the other or
copy its settings. The Java namespace remains `agency.carpediem.tv.prototype`
until a later code migration; it is not the installable application ID.

## Build

Use JDK 17, Android SDK Platform 34 and Build Tools 36.0.0. Pull requests run
the repository checks, Android lint and debug/release builds. An unsigned
release APK is for inspection only; Android cannot install it.

```sh
./gradlew --no-daemon assembleDebug assembleRelease lintDebug
python3 scripts/check_repository.py
python3 scripts/check_release_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
python3 scripts/make_update_manifest.py app/build/outputs/apk/release/app-release-unsigned.apk \
  --expected-application-id agency.carpediem.tv \
  --apk-url https://github.com/carpe-diem-agency/carpe-tv-launcher/releases/download/TEST/carpe-tv.apk \
  --output /tmp/carpe-update-check.json
```

The last command is a local metadata check, not a publish step. It reads the
actual APK package, version and minimum Android version using the installed
Android SDK Build Tools, and fails if the package is not the public app ID.
The Release workflow applies the same check to the signed APK.
The repository check also keeps the bilingual install-page download links,
release workflow asset name, checksum/metadata attachments, and in-app GitHub
feed in sync. On 2026-11-11, the manifest generator read the locally signed
0.2.7 candidate and its application ID, version, byte count, and SHA-256
matched the APK. This is a local release-contract check, not a live download
or a GitHub Actions draft-release test.

Release builds now contain the public GitHub `releases/latest` API endpoint as
their update feed. Debug builds have no feed unless `-PupdateFeedUrl=...` is
provided for a controlled test. A draft or prerelease is not returned by
`releases/latest`; automatic checks begin working only after a stable release
is published in the public repository. The installer still requires Android's
confirmation and the one-time "install unknown apps" permission for Carpe TV.

## Signing and draft release

Create and back up a dedicated release key outside Git. Losing the key prevents
updates with the same application ID. Set these GitHub Actions environment
secrets in the `release` environment: `RELEASE_KEYSTORE_BASE64`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` and
`RELEASE_CERT_SHA256`. Record the signing certificate's SHA-256 digest from
the backed-up key in the last secret. The workflow verifies that the signed
APK matches this pinned identity before creating a draft.
You can inspect the digest locally with `keytool -list -v -keystore release.jks`
and copy the `SHA256` fingerprint without its colons. Keep the keystore and
passwords outside Git and back them up separately.

A local dedicated-key candidate was created on 2026-11-11 and used to sign an
on-device test APK. Its second copy is on the same disk, so it is **not yet a
release-ready backup**. The owner must arrange an independent encrypted backup
and retain its recovery information before this key becomes the production
identity. The candidate certificate fingerprint and APK test evidence are in
[the device test record](RELEASE_APK_DEVICE_TEST.md). No key material belongs
in commits, issue comments, release assets or screenshots. The revised signed
candidate installed through TV Bro without the Play Protect block; its exact
hash and remaining Home test are in [the installation test](PLAY_PROTECT_TEST.md).
Earlier signed candidates containing the Accessibility Home bridge are obsolete.

The manual Release workflow builds a signed APK, verifies its signature and
artwork, then attaches the APK, the SPAKE2 source bundle, SHA256SUMS and
update.json to a **draft**
pre-release. Reviewers should check the resulting APK on real Android TV
devices and approve publication only after the [publication checklist](PUBLICATION.md)
is complete. A public repository, stable release and update feed are separate
manual decisions.

For the first public candidate, use the **same production key** for the draft
and later updates. Test the downloaded draft APK as a fresh install without
ADB: open it through a TV browser, grant the source app's install permission,
complete first run, choose Home, launch another app, press physical Home and
return to the stock launcher. Separately test a phone-to-TV file transfer on a
device without a browser. Record the exact receiving app and prompts. Do not
mark the one-APK install path verified from an ADB install alone. The optional
Shizuku setup is a separate test and is not part of the basic install path.

## Install page for the public release

`docs/index.html` is a Ukrainian/English install page designed for a TV remote
and a phone. It includes search/social metadata, two reviewed preview screens
and a short real transition recording. The final release-media review remains
open; see [screenshots](screenshots/README.md). It links its main button directly to the stable
release asset `releases/latest/download/carpe-tv.apk`, with a fallback link to
the release list. The page uses the same verified CC0 alpine photo as the app.
The local page has been checked at TV and phone viewport sizes, including
keyboard focus and both languages; its download link cannot work until a
**stable** release with exactly that asset name is public.

The prepared `.github/workflows/pages.yml` deploys only the install page,
its approved preview media, `robots.txt` and `sitemap.xml` after a **stable
release is published in a public repository**.
It checks that the release has `carpe-tv.apk`, `SHA256SUMS` and `update.json`.
Before that release, set **Settings → Pages → Build and deployment → GitHub
Actions** and restrict the `github-pages` environment to the intended branch.
Do not select branch `/docs` publishing in parallel. The expected address is
`https://carpe-diem-agency.github.io/carpe-tv-launcher/`; verify the actual URL reported
by the deployment. Test it in a TV browser and on a phone, including the APK
download, Android installer prompt, both languages, and rollback link. The
page and APK do not become public merely because this workflow file exists.
GitHub Pages may be public even for a private repository, so leave Pages
disabled until publication. If the release asset name changes, update the
page, workflow check and update instructions together.

For each update, increase versionCode and keep the same application ID and
signing key. Do not use the debug key for a public release.

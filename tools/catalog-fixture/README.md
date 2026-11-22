# Controlled catalog fixture

Fixed package `agency.carpediem.tv.testfixture.catalog`, versionCode 1 / versionName 1.0, label `Carpe QA Калина`. One exported Activity with MAIN, LAUNCHER and LEANBACK_LAUNCHER. testOnly=true; no HOME category, requested permissions, services, receivers or providers.

`build.py` accepts explicit existing local SDK (`--sdk`), build-tools version (`--build-tools`), JDK (`--java-home`), private keystore (`--keystore` / `--key-alias`), expected signing certificate (`--certificate-sha256`) and a fresh output child of `/path/to/carpe-tv-runtime/fixtures/` (`--output`). Optional `--platform` defaults to android-34. Passwords come from `CATALOG_STORE_PASSWORD` and `CATALOG_KEY_PASSWORD`; do not print or commit them. No dependencies are downloaded, no Gradle build runs, no package is installed.

The script validates the source manifest contract, compiles Java with local android.jar, links aapt2 resources, creates dex, aligns and signs, verifies certificate/APK CRC/package/version/label and writes `catalog-fixture.apk`, `verification.txt`, `sha256.txt`. The signed APK is published only after verification. Use normal Python (not `-O`, which disables assertions). The owner-approved active runtime is `/path/to/carpe-tv-runtime`; it must already exist. SDK, JDK and caches are local. The script rejects outputs outside its `fixtures/` subtree and requires an existing parent plus a new output directory. Existing app/test artifacts stay untouched; detached historical SSD/APFS storage is not required or used.

Runtime lifecycle tests must require initial package absence, install only this fixed package with test-only permission, and remove it in finally. Never use the fixture package as Home or infer actual broadcasts from direct Main method calls. Host staging path agreed with QA: `/data/local/tmp/carpe-catalog-fixture.apk`; root owns staging/install/removal, baseline and certificate checks.

Use `--deny-launch` for the `cataloglaunchdenied` probe. This adds a launch
permission to the fixture activity, with the same package, signing identity
and launcher filters. It grants no permissions to the validation client.

For `audioexternal`, use an ordinary build from current source. A token
provided by the host enables a silent looping track and bounded focus and
playback counters. A launch from the catalog uses the ordinary fixture screen.

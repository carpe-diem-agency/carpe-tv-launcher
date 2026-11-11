# Rebuilding Carpe TV with a modified SPAKE2 library

Carpe TV includes `spake2-android` 2.2.1 through the optional local system
setup feature. The library is LGPL-3.0. This page describes the tested
**technical** path from modified library source to an installable APK. It is
not a conclusion that every distribution requirement has been met; the
[release checklist](PUBLICATION.md) keeps that review open.

## Materials

- The source for the **same Carpe TV release tag** as the APK. A GitHub release
  must also contain `spake2-java-2.2.1-with-submodule.tar.gz` and `SHA256SUMS`.
  Verify the archive against the checksum before editing it. The staged archive
  in this repository contains the exact upstream tag and its native submodule.
- JDK 17, Android SDK Platforms 34 and 35, Android Build Tools 35.0.0 and
  36.0.0, NDK 27.2.12479018, and CMake 3.22.1. The exact tool installation
  and build commands are in
  [the source-build workflow](../.github/workflows/spake2-source-check.yml).
- A signing key of your own for installing the modified APK. Never use the
  Carpe TV production key or distribute a private key with source.

## Build and inspect

From a clean checkout of the matching Carpe TV tag, unpack the source archive
outside the repository and edit the library source there. For a native change,
the relevant files are under `android/src/main/cpp/spake2-c/` in the unpacked
tree. Then build its AAR:

```sh
cd /path/to/spake2-java-2.2.1
./gradlew --no-daemon :android:assembleRelease
```

Build Carpe TV with the replacement AAR. The `spake2Aar` property excludes
the published transitive library and inserts this one:

```sh
cd /path/to/carpe-tv-launcher
./gradlew --no-daemon :app:assembleRelease \
  -Pspake2Aar=/path/to/spake2-java-2.2.1/android/build/outputs/aar/android-release.aar
```

The resulting `app/build/outputs/apk/release/app-release-unsigned.apk` is
**not installable** until signed. Before signing, compare the four
`jni/<ABI>/libspake2.so` entries in the AAR with the corresponding
`lib/<ABI>/libspake2.so` entries in the APK. The source-build workflow performs
this byte-for-byte check and fails if an ABI is missing or differs. The
workflow's optional `modified_probe` also verifies that a test source edit
survives into all four APK libraries. Run
`python3 scripts/check_release_apk.py app/build/outputs/apk/release/app-release-unsigned.apk`
to check packaged artwork and license files.

Sign with your own key using Android's `apksigner`, then run
`apksigner verify --verbose modified-carpe-tv.apk`. Android requires signed
APKs; an APK signed with a different key cannot update the official package
in place. For a side-by-side functional test, build with
`-PisolatedPreview` too, which adds `.publicpreview` to the application ID.
For the ordinary application ID, removing an existing differently signed app
may also remove its settings. Keep the stock Home launcher available while
testing a modified build.

The [source record](SPAKE2_SOURCE.md) documents successful clean-runner builds,
all four library matches, and an isolated signed device test using a modified
native marker. A production-signed release and final distribution review are
still pending.

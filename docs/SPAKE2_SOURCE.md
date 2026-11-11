# SPAKE2 native-library source record

The release runtime resolves `com.github.MuntashirAkon.spake2-java:spake2-android:2.2.1` through `libadb-android`. The resolved AAR contributes `libspake2.so` for four Android ABIs. Its local SHA-256 is `8798fb6c04b5d53a6307ed9481e9afe563227abd4e8b9e917c8514fa850071bb`. Carpe TV does not modify SPAKE2, but an APK distributes copies of its binary.

The upstream [2.2.1 tag](https://github.com/MuntashirAkon/spake2-java/tree/2.2.1) is LGPL-3.0. Its `android/src/main/cpp/spake2-c` submodule points to commit `0d15933e5ba3e662cb01245a7ac0dc9fca3eac31` of [spake2-c](https://github.com/MuntashirAkon/spake2-c). The tag's `android/build.gradle` uses CMake for the JNI library and sets the release linker option for 16 KB pages. The source can be retrieved with:

```sh
git clone --branch 2.2.1 --recurse-submodules https://github.com/MuntashirAkon/spake2-java.git
```

The LGPL-3.0 and GPL-3.0 license texts are bundled in `assets/licenses/`. The upstream source links above identify the exact version and native submodule, but linking to upstream and bundling license text alone may not satisfy all distribution requirements. Before a public release, document how a recipient can obtain the complete corresponding source and replace or relink the LGPL library in the distributed APK; verify the actual signed artifact against that procedure. Keep the source and relinking offer available for the applicable period if this distribution route is chosen.

This is a provenance record, not a legal clearance. Checked against the official upstream tag and the local resolved AAR on 2026-11-11.

A source bundle with the nested native submodule is staged at
`compliance/source/spake2-java-2.2.1-with-submodule.tar.gz`. Its SHA-256 is
`af64a8a6f5273ded0ce2635b807c68ed0741384db32cf871f6af71d495f63e07`.
The draft-release workflow now attaches it alongside the APK and includes
both files in `SHA256SUMS`; the repository check verifies its checksum and
essential source files. The checked-out upstream tag resolves to commit
`7615ddd680b990e14513ebb66eac4cb0dbf82464`, and its native submodule to
`0d15933e5ba3e662cb01245a7ac0dc9fca3eac31`. The tag's
`android/build.gradle` still declares `version = '2.2.0'` while the published
JitPack artifact is `2.2.1`; provenance was checked by tag and submodule,
but binary reproduction from this source has **not** been demonstrated.
The bundle does not settle the application-code/relinking or installation
information requirements for a signed APK. Keep that publication gate open.

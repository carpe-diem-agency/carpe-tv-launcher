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

## Source rebuild check

The manual [GitHub Actions run #1](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42990630242) succeeded on 2026-11-11 at commit `86aa698`. It verified the bundle checksum, installed the Android NDK and CMake on the runner, built the upstream Android release AAR, and uploaded the resulting AAR as an artifact. This shows that the archived source can be built with the workflow's toolchain. It does not show that its native bytes match the JitPack AAR in Carpe TV, that a user can replace the library in the signed APK, or that the LGPL-3.0 distribution obligations are satisfied. The public release gate remains open.

## Building with a replacement AAR

For the full recipient-facing sequence, including the toolchain, signing and
side-by-side installation limits, see [rebuilding with a modified library](RELINKING.md).

The launcher accepts an explicitly supplied Android AAR in place of libadb's
transitive `spake2-android` artifact:

```sh
./gradlew :app:assembleRelease -Pspake2Aar=/absolute/path/to/android-release.aar
```

The AAR can be built from the staged source with the commands in
`.github/workflows/spake2-source-check.yml`. The path must point to an existing
`.aar` file. A local release build using this property and the original
unmodified dependency AAR succeeded on 2026-11-11; the release dependency
report no longer contained the transitive `spake2-android` module. This proves
the dependency substitution path works for that AAR. A rebuilt APK needs its
own signing key; it cannot be installed as an update over an APK signed by a
different key.

The follow-up [GitHub Actions run #2](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42991685259)
at commit `33552fc` also passed: after rebuilding the source AAR, the runner
built an unsigned Carpe TV release APK with that AAR supplied through
`-Pspake2Aar`. This verifies the source-to-APK build path on a clean runner.
The workflow artifact was not signed or installed in that run, and this was the
unmodified upstream source.

The subsequent [run #3](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42992685963)
at commit `563b24f` passed a stronger packaging check: SHA-256 of each
source-built `libspake2.so` in the AAR matched the corresponding file in the
unsigned APK for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. This verifies
which native files entered that build. It does not resolve the
signed-distribution requirements.

## Isolated signed device check

On 2026-11-11, the AAR from run #3 was downloaded and supplied to an offline
local `assembleRelease -PisolatedPreview -Pspake2Aar=…` build. The unsigned
APK passed `scripts/check_release_apk.py`; its four packaged SPAKE2 native
libraries matched the source-built AAR byte for byte. The APK was signed with
a disposable test certificate, installed under the isolated
`agency.carpediem.tv.publicpreview` ID on MiTV-AFMU1, and launched. Home and
the app library rendered, and D-pad navigation worked. The test APK was then
removed, leaving the personal Home role unchanged. This establishes that the
source-built library can run inside a signed launcher APK on the tested stick.
The manual [modified-library run #4](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42999102098)
at commit `d15875f` added a test-only exported marker to SPAKE2 source, built
the AAR and unsigned release APK, and verified the marker plus matching native
library hashes in all four APK ABIs. `scripts/check_release_apk.py` passed on
the downloaded APK. We signed it with a disposable test certificate, installed
it under the public `agency.carpediem.tv` ID on MiTV-AFMU1, and confirmed the
welcome screen and app library rendered with D-pad navigation. We then
uninstalled that test package. The personal `agency.carpediem.tv.prototype`
package and Android Home role remained in place. This proves the technical
source-edit-to-signed-APK-to-device path for this test. It does not establish
an update path over the eventual production-signed APK or complete the LGPL
distribution review. The marker is only a build probe and is not in the
normal release dependency.

The draft-release workflow is now configured to verify the staged source
archive, build its Android AAR, substitute that AAR for the transitive
JitPack artifact, and compare all four native libraries inside the signed
APK with the source-built AAR. This removes the unresolved JitPack binary
provenance from the planned public artifact. The signed draft workflow has
not yet run with production signing credentials, so its actual output must
still be checked. The LGPL combined-work and installation-information review
remains a separate publication gate.

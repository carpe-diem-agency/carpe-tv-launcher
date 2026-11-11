# Runtime dependency audit for the public APK

Checked against `:app:dependencies --configuration releaseRuntimeClasspath` and
`app-release-unsigned.apk` on 2026-11-11. This is an inventory, not a legal
clearance or a complete software bill of materials.

| Dependency | Resolved version | Published license / source | APK notice state |
| --- | --- | --- | --- |
| ZXing Core | 3.5.4 | Apache-2.0 | Apache text bundled; component attribution in source |
| libadb-android | 3.1.1 | [Upstream dual license: Apache-2.0 or GPL-3.0-or-later](https://github.com/MuntashirAkon/libadb-android#license) | Apache option text bundled; component attribution in source |
| spake2-android | 2.2.1 | [Upstream LGPL-3.0](https://github.com/MuntashirAkon/spake2-java/tree/2.2.1) | LGPL and GPL texts bundled; [exact source record](SPAKE2_SOURCE.md); source offer / relinking review pending |
| Conscrypt Android | 2.8.0 | Apache-2.0 | Apache text bundled; component attribution in source |
| Bouncy Castle `bcpkix`, `bcutil`, `bcprov` | 1.86 | [MIT-style Bouncy Castle License](https://www.bouncycastle.org/about/license/) | Official notice bundled; release APK byte check added |
| Shizuku API, provider, shared, AIDL | 13.1.5 | MIT | MIT notice bundled |
| AndroidX Annotation / Annotation JVM | 1.9.1 | Apache-2.0 | Transitive; component listed in bundled notice |
| Kotlin stdlib / JetBrains annotations | 1.9.24 / 13.0 | Apache-2.0 / Apache-2.0 | Transitive; components listed in bundled notice |

The APK contains `libspake2.so` and `libconscrypt_jni.so` for four ABIs. Its
source now includes Apache-2.0, LGPL-3.0 and GPL-3.0 license texts alongside
Montserrat OFL, Shizuku MIT and the Bouncy Castle notice under `assets/licenses/`.
It also includes `THIRD-PARTY-NOTICES.md` with the resolved versions and source
links. The notice is readable from **Settings → About → Third-party notices**
in both interface languages. On 2026-11-11 an isolated build opened this view
on MiTV-AFMU1; D-pad scrolling reached the final component and source note.
The component text is in English in either UI language. The older JetBrains annotations 13.0 POM in the resolved Gradle cache
declares Apache-2.0; the license of newer JetBrains annotations releases must
not be substituted for this resolved version.
The unsigned release APK was rebuilt on 2026-11-11 and `check_release_apk.py`
verified all six packaged license texts. Inspect the final signed public APK
again before publication. The LGPL source, modification and
relinking obligations still need review; text inclusion alone is insufficient.
The
`libadb-android` upstream explicitly notes its LGPL dependency even when its
Apache-2.0 option is used.

The basic launcher works without Shizuku; local ADB pairing and the optional
system-access wizard are what pull in `libadb-android`, SPAKE2, Conscrypt and
Bouncy Castle. Removing that wizard from a future baseline would reduce the
dependency surface, but that is a product decision and is not done here.

[LGPL-3.0 section 4](https://www.gnu.org/licenses/lgpl-3.0.html#section4)
requires more than notices for a combined work: depending on the chosen route,
provide corresponding source and application material suitable for relinking,
or verify that a suitable shared-library mechanism permits an interface-compatible
modified library. The current signed APK has not been demonstrated to satisfy
either route. This is a release gate, not a claim that the library cannot be used.

The [libadb maintainer discussion](https://github.com/MuntashirAkon/libadb-android/issues/22)
also rules out treating a switch to pure Java SPAKE2 as an already verified
solution: the reporter observed pairing failure when forcing that variant, and
the maintainer described incomplete support. We have not reproduced a working
replacement on the Xiaomi stick. Keep the current optional system-access
feature and the distribution gate separate until either relinking is validated
or a tested alternative is available.

Next gates: review the notice inventory against the final resolved artifacts,
review LGPL distribution requirements for the bundled native library, and
inspect the newly signed APK. An explicit
license for Carpe TV's own code is a separate owner decision.

On 2026-11-11, `check_release_apk.py` also passed on the locally signed 0.2.7
candidate APK with application ID `agency.carpediem.tv`. This verifies the
packaged media and six license files in those exact bytes. It does not settle
the remaining LGPL distribution obligations or transitive notice review.
After the notice was added, both unsigned and locally signed release APKs
rebuilt successfully. The artifact check passed with six license texts and one
third-party notice in that signed candidate; its signer was verified and the exact
APK installed on the Xiaomi stick. The earlier signed-candidate hash identifies
bytes without the notice and must not be used for a draft release.

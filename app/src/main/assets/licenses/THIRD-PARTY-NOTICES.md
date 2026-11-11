# Third-party software in Carpe TV

This file identifies the third-party runtime components resolved for the
Android release build. It is not a license for Carpe TV's own source code.
The accompanying files in this directory contain the applicable license texts.

| Component | Version | License | Source |
| --- | --- | --- | --- |
| ZXing Core | 3.5.4 | Apache-2.0 | https://github.com/zxing/zxing/tree/zxing-3.5.4 |
| libadb-android | 3.1.1 | Apache-2.0 option of its dual license | https://github.com/MuntashirAkon/libadb-android/tree/3.1.1 |
| spake2-android, including `libspake2.so` | 2.2.1 | LGPL-3.0 | https://github.com/MuntashirAkon/spake2-java/tree/2.2.1 |
| Conscrypt Android | 2.8.0 | Apache-2.0 | https://github.com/google/conscrypt |
| Bouncy Castle bcpkix, bcutil, bcprov | 1.86 | Bouncy Castle license | https://www.bouncycastle.org/about/license/ |
| Shizuku API, provider, shared and AIDL | 13.1.5 | MIT | https://github.com/RikkaApps/Shizuku-API |
| AndroidX Annotation / Annotation JVM | 1.9.1 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin standard library | 1.9.24 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| JetBrains annotations | 13.0 | Apache-2.0 | https://github.com/JetBrains/intellij-community |
| Montserrat typeface | bundled version | OFL-1.1 | https://github.com/JulietaUla/Montserrat |

`libadb-android` is used under its Apache-2.0 option. Its SPAKE2 dependency
has a separate LGPL-3.0 license; see `SPAKE2-LGPL-3.0.txt` and
`GPL-3.0.txt`. The exact native source revision and retrieval instructions are
in the public source document `docs/SPAKE2_SOURCE.md`; the rebuild procedure is
in `docs/RELINKING.md`. A source archive named
`spake2-java-2.2.1-with-submodule.tar.gz` accompanies release APKs. The release team must
complete the corresponding-source and relinking review before distribution.

The Shizuku application itself is a separate optional installation; the APK
contains its API libraries, not the Shizuku app.

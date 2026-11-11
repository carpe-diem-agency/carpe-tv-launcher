# Third-party materials

| Material | Origin / terms | Distribution status |
| --- | --- | --- |
| Montserrat font | Project authors, SIL Open Font License 1.1; full notice in `app/src/main/assets/licenses/Montserrat-OFL.txt` | Bundled with license |
| Gradle wrapper scripts / JAR | Gradle project, Apache License 2.0: https://github.com/gradle/gradle/blob/v9.8.2/LICENSE | Build tooling only; distribution checksum pinned; full notice in `licenses/Gradle-LICENSE.txt` |
| Android SDK and Android Gradle Plugin | Google SDK terms / respective component licenses | Downloaded by build environment, not vendored |
| Mountain lake, Northern lights, Lake Como wallpapers | CC0 1.0; individual authors, URLs and hashes in [wallpaper sources](docs/wallpapers/README.md) | Bundled with source records; redistribution allowed by CC0 |
| Carpe Diem logo | Agency brand asset, https://carpediem.agency/themes/custom/carpediem/logo.png | Agency branding; confirm publication scope |
| Native XML vectors and Carpe TV mark | Created for this project; code-native assets | Original project assets; confirm agency branding scope |
| App logos visible in screenshots | Loaded at runtime from apps installed on the test device | Respective owners' trademarks; not bundled logo packs |
| ZXing Core 3.5.4 | ZXing authors, Apache License 2.0 | QR generation; Apache text in `app/src/main/assets/licenses/Apache-2.0.txt` |
| libadb-android 3.1.1 | Muntashir Al-Islam, GPL-3.0-or-later or Apache License 2.0; [upstream](https://github.com/MuntashirAkon/libadb-android) | Optional local ADB pairing; Apache option text bundled |
| spake2-android 2.2.1 | Muntashir Al-Islam, LGPL-3.0; [upstream](https://github.com/MuntashirAkon/spake2-java) | LGPL and incorporated GPL texts bundled; complete upstream-tag source with native submodule staged at `compliance/source/spake2-java-2.2.1-with-submodule.tar.gz`; public APK relinking obligations require review |
| Conscrypt Android 2.8.0 | Conscrypt contributors, Apache License 2.0 | TLS for local ADB; Apache text bundled |
| Bouncy Castle 1.86 | Legion of the Bouncy Castle, MIT-style [Bouncy Castle License](https://www.bouncycastle.org/about/license/) | Local ADB identity generation; official notice bundled in `app/src/main/assets/licenses/Bouncy-Castle-LICENSE.md` |
| Shizuku API 13.1.5 | Rikka, MIT License | Optional system-access bridge; Shizuku app is installed separately |

The earlier CleanYourTV experiment is not included as a
source dependency or runtime dependency in this repository. Historical build
wrapper files are standard Gradle tooling, not launcher implementation code.

The new local ADB dependency chain and its LGPL component still need a full
redistribution and relinking review before a public APK is shipped.
See the [resolved runtime dependency audit](docs/DEPENDENCY_AUDIT.md) for the
transitive AndroidX, Kotlin and Bouncy Castle modules and current APK notice gaps.

No blanket license in this repository grants rights to third-party brands or
artwork. Public distribution remains a separate reviewed step.

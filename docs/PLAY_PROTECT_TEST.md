# TV Bro installation test · 2026-11-11

Device: Xiaomi MiTV-AFMU1, Android TV 14. TV Bro downloaded both APKs from a
temporary HTTP page on the same LAN. The same Android package installer opened
each file. TV Bro already had permission to install unknown apps before the test.

| APK | Accessibility Home service | Result after tapping Install |
| --- | --- | --- |
| Locally signed 0.2.7 candidate | Present | Google Play Protect blocked the install, saying the app may request access to sensitive data. |
| Diagnostic debug 0.2.7 built from a source copy | Removed from manifest | Android reported “App installed.” |
| Revised locally signed 0.2.7 candidate, same release certificate | Removed from manifest | Android reported “App installed”; Carpe TV first run opened. |

The third APK used the same signing certificate as the blocked first APK.
The source also changed the power control and Home status text, so this is
strong evidence, not a single-variable proof, that the Accessibility service
caused the Play Protect block. The revised public source removes the service,
its Settings action and the power-menu dependency. The sidebar control now
opens the sleep timer. A separate isolated debug installation verified that
control visually; it was uninstalled afterward.

The original signed candidate must **not** be distributed. The revised signed
candidate SHA-256 is
`ba59627c7a825eb77cea2f6d166ebe35e818a1d88c4f64541bd47e2817ada157`;
its certificate SHA-256 is
`2892d5fc34a65bc17125a9e84c01e3f4df946633385045fba38249148e3df60e`.
It still needs physical remote Home and rollback tests. On this Xiaomi firmware,
standard Android Home selection alone may not prevent Google TV from appearing.

After the tests, both downloaded files and test packages were removed, the
temporary LAN servers were stopped, and the personal
`agency.carpediem.tv.prototype` package remained the Android Home holder.

## Signed candidate after return-focus fix

The public 0.2.7 source was rebuilt with the return-focus fix and signed with
the same certificate. The resulting APK SHA-256 is
`d46edc75c3e0e97be9016a320761c98f33f8e62aca212b177d4e508751646214`.
The release artifact check passed. TV Bro downloaded this exact file from a
temporary LAN server; the downloaded file's SHA-256 matched. Android's package
installer reported **App installed** without a Play Protect block. The signed
app's first-run screen opened. The public test package and downloaded APK were
then removed, the server stopped, and the personal Home holder confirmed.
This does not validate a future GitHub release URL or physical Home behavior.

## Separate Home helper experiment · 2026-11-11

To check whether separating the Home accessibility service from the main APK
would avoid the installation block, we built a minimal, independently packaged
debug APK (`agency.carpediem.tv.homehelper.experiment`). Its service listened only
for Google TV window-state events, requested no window content, and intentionally
performed no action. Its SHA-256 was
`a324e0fda2d4cc93ccce06f0a95ac6836cecd4cbf1eb69f1ae8101239856f0b4`.

TV Bro downloaded the APK from a temporary local HTTP server. Android's package
installer offered **Install**, then Google Play Protect blocked it with the
warning that the app could request access to sensitive data. The app was not
installed or enabled. The temporary server was stopped. This shows that simply
putting the same type of service in a separate APK does not make ordinary TV Bro
installation work on this stick. The fixture is not a distributable solution.

We also tried a narrower system-component route: disabling only Google TV's
`home.HomeActivity` through shell, while leaving the Google TV package enabled.
Android rejected the command with `SecurityException: Shell cannot change
component state`. A readback still resolved Home to Google TV and showed the
Google TV package enabled. No system component was changed.

The platform's `cmd package set-home-activity --user 0
agency.carpediem.tv.publicpreview` returned **Success**, but a subsequent
`resolve-activity` still selected `com.google.android.apps.tv.launcherx` for
`MAIN` + `HOME`. This command therefore did not fix routing on the test stick.

## Full Google TV package routing check · 2026-11-11

With the isolated Carpe TV preview holding `ROLE_HOME`, the stick still resolved
`MAIN` + `HOME` to Google TV (priority 2). We temporarily disabled the Google TV
package inside a shell command with an automatic `EXIT` trap that restored it.
During that brief interval, Home resolved to
`com.google.android.tungsten.setupwraith/.RecoveryActivity` (priority 1), not
Carpe TV's `HomeEntry` (priority 0). A final readback confirmed Google TV was
enabled again (`enabled=1`) and resumed as the Home resolver. No physical Home
key was pressed during this check. Disabling Google TV alone is therefore not
an acceptable Home fix on this firmware, and the recovery package was left
untouched.

Android's manifest documentation also rules out raising the non-privileged
`HomeEntry` intent-filter priority above these OEM candidates: requested
positive priorities are capped at zero for non-privileged apps. See
<https://developer.android.com/guide/topics/manifest/intent-filter-element#priority>.

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

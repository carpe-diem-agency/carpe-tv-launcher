# Unsigned release0.2.24(31): actual APK surface

2026-11-15. Full ZIP CRC check **PASS before inspection**, SHA-256
`6d0c242be69f8bfe3960db782ee259eef96a3d978214d4e58b67b1f3ed48a80c`,
matching candidate.json. Read with local SDK36 aapt xmltree; no ADB,
installation, build, signing or publication performed for this audit.
Artifact: `carpe-tv-runtime-local/release-candidate/0.2.24-31/Carpe-TV-0.2.24-31-unsigned.apk`.
Package `agency.carpediem.tv`, version0.2.24/code31, minSDK26/targetSDK34.

## Permissions (4)

- `android.permission.INTERNET`
- `android.permission.REQUEST_INSTALL_PACKAGES`
- `android.permission.SCHEDULE_EXACT_ALARM`
- `moe.shizuku.manager.permission.API_V23`

No broad storage, phone-state, camera, microphone or location request in
this manifest inventory. Application `allowBackup=false`; `debuggable`
attribute absent (platform default false), `extractNativeLibs=false`.
Application class is `agency.carpediem.tv.prototype.CarpeApplication`.

## Named components (9), explicitly exported (4)

| Component | Kind | Exported / relevant attributes |
| --- | --- | --- |
| rikka.shizuku.ShizukuProvider | provider | true, enabled=true, multiprocess=false; permission INTERACT_ACROSS_USERS_FULL; authority agency.carpediem.tv.shizuku |
| agency.carpediem.tv.prototype.SleepTimer | receiver | false |
| agency.carpediem.tv.prototype.SystemSetupActivity | activity | false, launchMode singleTop |
| agency.carpediem.tv.prototype.ScreensaverActivity | activity | false |
| agency.carpediem.tv.prototype.UpdatesActivity | activity | false |
| agency.carpediem.tv.prototype.UpdateDownloadReceiver | receiver | true, DOWNLOAD_COMPLETE intent |
| agency.carpediem.tv.prototype.UpdateInstallReceiver | receiver | false |
| agency.carpediem.tv.prototype.MainActivity | activity | true, singleTask; MAIN/LEANBACK_LAUNCHER |
| agency.carpediem.tv.prototype.HomeEntry | activity-alias | true, enabled=false; target MainActivity, MAIN/HOME/DEFAULT |

No service component declared. Queries include known TV Settings/Shizuku/
Xiaomi/GoogleTV/Aerial packages and MAIN launcher/Home intents; queries are
package visibility declarations, not permissions or evidence of system mutation.

## Comparison with historical code15 inventory

[APK_SURFACE_AUDIT](APK_SURFACE_AUDIT.md) describes0.2.8/code15. Current
artifact retains its four named permissions and the same described exported
surface (launcher, disabled Home alias, Shizuku provider, download receiver),
with backup disabled and internal saver/updater/setup/sleep/install callbacks.
The old prose does not enumerate every component attribute/application class;
**do not infer byte-for-byte manifest equivalence** from this agreement.
Version is now31; this report supplies exact current names and attributes.
The historical warning about an earlier signed Accessibility-bridge APK
remains history, not a claim that this current artifact contains that service.

Evidence: `/path/to/carpe-tv-runtime/security/apk-surface-0.2.24-31/`
contains `manifest-xmltree.txt` and `inventory.json` with actual APK path/hash/
CRC outcome. This is an unsigned-artifact inventory, not threat-model clearance,
permission-runtime acceptance, a certificate audit or a security certification.
Repeat against any final signed bytes. P0/Home role/device settings were untouched.

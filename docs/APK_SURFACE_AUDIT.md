# APK permissions and components

Inspected the built `app-release-unsigned.apk` with Android build-tools `aapt`
on 2026-11-11. The artifact is Carpe TV `agency.carpediem.tv`, version
`0.2.8` (code 15), min SDK 26, target SDK 34, approximately 16 MB. This is
an artifact inventory, not a security certification or a multi-device test.

| Permission in APK | Used for |
| --- | --- |
| `INTERNET` | Wallpapers, updates and optional local system setup |
| `REQUEST_INSTALL_PACKAGES` | User-confirmed update installation |
| `SCHEDULE_EXACT_ALARM` | User-set sleep timer |
| `moe.shizuku.manager.permission.API_V23` | Optional Shizuku system controls |

The APK does not request storage or phone-state permissions. Backup is disabled
in the merged manifest. Shizuku is not needed for the launcher, app library or
wallpaper; its access needs separate user action.

Exported components in the revised unsigned build are the launcher activity,
a disabled-by-default Home alias, the Shizuku provider, and
the download-complete receiver. The receiver verifies that the broadcast
download ID matches the ID stored by Carpe TV and then reads DownloadManager
state. Its exported status is necessary for Android's download completion
broadcast but should remain in the release threat review. The sleep receiver,
screensaver, updater screen, system-setup screen and installer callback are not
exported.

The inspected 0.2.8 artifact above did not contain an Accessibility service.
An earlier signed candidate with a Home Accessibility service was blocked by
Play Protect during TV Bro installation. A new **optional** Home bridge is under
development; this table does not describe that new candidate. Rebuild the APK,
inspect its merged manifest and repeat ordinary TV Bro installation and Play
Protect testing before shipping it. Home behavior, permission prompts and
rollback still need tests on another Android TV model. Never ask users to
bypass Play Protect.

### Feature-branch recheck — 2026-11-11

Built `app-release-unsigned.apk` from `feature/home-bridge-animated-blue` and
inspected it with Android Build Tools `aapt`. The package remains
`agency.carpediem.tv` 0.2.8/code 15, min SDK 26, target SDK 34, and about 16 MB.
The `uses-permission` list is unchanged. The merged manifest now declares one
exported `HomeBridgeService` protected by `BIND_ACCESSIBILITY_SERVICE`; its
metadata limits events to Google TV window-state changes and disables window
content retrieval. No audio or video resource was added for the animated blue
wallpaper. This is an unsigned, local inventory only; it does not clear the
Play Protect or ordinary-installation gate.

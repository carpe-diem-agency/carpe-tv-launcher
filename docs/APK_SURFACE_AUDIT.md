# APK permissions and components

Inspected the built `app-release-unsigned.apk` with Android build-tools `aapt`
on 2026-11-11. The artifact is Carpe TV `agency.carpediem.tv`, version
`0.2.7` (code 14), min SDK 26, target SDK 34, approximately 16 MB. This is
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

The earlier signed candidate still contains the Accessibility service and was
blocked by Play Protect during TV Bro installation. Do not distribute it. The
revised unsigned build removes that service; its final signed APK must be
checked again after signing. Home behavior,
permission prompts and rollback still need tests on another Android TV model.

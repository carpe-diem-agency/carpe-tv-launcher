# APK permissions and components

## Local candidate 0.2.31 (38) · 2026-11-21

Checked the unsigned `agency.carpediem.tv` candidate from `73d7aa5` with
`aapt dump badging`, `permissions` and `xmltree`. APK SHA-256
`8af89d6161e56e9304da19b8c853efad136cc0562730ad26c628b578fdef5919`.
The compiled manifest equals 0.2.30 (37) after removing version code/name and
source-line annotations. Min SDK 26, target and compile SDK 34. Backup is
disabled. The four requested permissions are listed below.

- The TV launcher activity and download-complete receiver are exported. The
  exported Home alias is disabled by default. The exported Shizuku provider
  requires `android.permission.INTERACT_ACROSS_USERS_FULL`.
- The sleep receiver, setup activity, screensaver, updates activity and
  installer callback receiver are private. The manifest contains no
  Accessibility service.

This is an inventory of the exact build. Production signing and runtime
permission behavior have separate acceptance checks.

## Historical inventory · 0.2.8 (15)

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

The earlier signed candidate still contains the Accessibility service and was
blocked by Play Protect during TV Bro installation. Do not distribute it. The
revised unsigned build removes that service; its final signed APK must be
checked again after signing. Home behavior,
permission prompts and rollback still need tests on another Android TV model.

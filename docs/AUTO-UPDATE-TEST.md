# Automatic update validation — 2026-11-03

Validated on Xiaomi MiTV_AFMU1, Android TV 14, with a separate application ID (`agency.carpediem.tv.prototype.updatetest`). Production Home, service and preferences were not replaced by the updater fixture.

- Opening the test launcher triggered the due check and automatic APK download without tapping Check or Download.
- DownloadManager completed the download and the app validated the APK after leaving the test application.
- Android's per-source installation permission and confirmation were required. The test upgraded `0.2.0-auto.1` to `0.2.0-auto.2`.
- The automatic download preference survived the update.
- Reopening within the check interval made no new download. Making another check due returned “current version”; the server recorded only one APK GET across the test.
- Test applications were removed afterwards. Production remained version `0.2.0` (code 7).

Delivery used a temporary private LAN HTTP endpoint permitted only by the debug build. This validates the automatic check/download/install path, **not** a permanent GitHub delivery channel. The production feed remains unconfigured; private GitHub assets require an explicit delivery/access design. No GitHub credentials are embedded in the APK.

Private QA APKs, HTTP request logs and the pre-power-change production backup remain in the local checkpoint `mistick-customization/universal-workspace/checkpoints/auto-update-power-20261103`; they are not release artifacts.

## Power entry

The sidebar power icon follows Settings. It opens Android's own power dialog through the already enabled Home accessibility service, only on activation, never on focus. Available actions belong to the device firmware. No root, shell execution, forced shutdown, or new permissions are added. If the service is unavailable, the launcher explains the dependency instead of pretending to power off.

# Updating Carpe TV

[Українською](UPDATING.uk.md)

## 1. Open the updater

Open **Settings → About → Updates**. Select Check for updates, then Download.
You can leave this screen: Android DownloadManager continues in the background.
After validation, select Install. No browser or manual APK-file selection is needed.


## 2. Allow this source on the first installation

If Android has not allowed this app to install APKs, the button says **Allow installation**
and opens the system permission screen. Find **Carpe TV**, enable **Allow from this
source / Install unknown apps**, then press Back. Grant this only to Carpe TV.
The permission belongs to this source app; do not enable unrelated sources.


This path was checked on Xiaomi Android TV 14 with Ukrainian system settings. Other firmware
may show a dedicated per-app page or use different wording. Carpe TV does not turn
this permission on automatically. If the screen cannot be opened, use the device's
Apps → Special app access / Security settings to locate Install unknown apps.

## 3. Return and confirm

Return to Updates, select **Install** again and confirm **Update** in Android.
Canceling leaves the current version usable and the validated download available
for retry. Installation briefly closes the app; Home opens the launcher afterward.


The APK must have a compatible signing certificate and newer versionCode. Settings
were checked across the tested update, but retaining an APK alone is not a full
backup of app-private data. The updater keeps one private previous-APK/preferences
checkpoint; there is no automatic downgrade button.

## Delivery and test status

The public release build points to GitHub's latest stable release feed. That
feed will become usable only after the first public stable release. The earlier
end-to-end test used a temporary trusted LAN server in debug mode; it does not
prove the future public download path.

The configured feed can check at most daily when the launcher is visible.
Automatic downloading is off by default and can be enabled explicitly. Android
installation confirmation is still required. No permanent app-owned polling service
or shared repository token is used.

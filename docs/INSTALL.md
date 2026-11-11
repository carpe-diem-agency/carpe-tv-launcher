# Installation, Home and rollback

**English** · [Українська](INSTALL.uk.md) · [README](../README.en.md)

## Before starting

- Android TV / Google TV, Android 8.0 (API 26)+. System controls were tested only on
  Xiaomi TV Stick `MiTV-AFMU1`, Android TV 14 — [device matrix](../README.en.md#tested-devices).
- Keep the stock launcher installed and do not clear its data.
- Features can differ between APK versions. Check the release notes before
  following optional system-control steps below.
- System mutations support primary Android user 0. Root is not required.

## 1. Install Carpe TV

**Without a computer:** open the
[Carpe TV page](https://carpe-diem-agency.github.io/carpe-tv-launcher/)
on your TV and select **Download APK**. If your TV has no browser, download the
APK from the same page on your phone and transfer the file to your TV. Some
devices need a file receiver app from Google Play.

The page currently shows a preview only. The download button becomes active after
the public release. [GitHub Releases](https://github.com/carpe-diem-agency/carpe-tv-launcher/releases)
will provide the same APK as a fallback.

1. Open the downloaded APK on the TV. If Android asks which app to use, choose
   **Package Installer**. On [Android 8+](https://developer.android.com/studio/publish#install-unknown-apps), allow installation for
   **the browser or file manager that opened the file**, then confirm the Android
   installer prompt. Carpe TV's permission to install future updates is separate.
2. Open Carpe TV from the app list. Choose **Explore first** and check the app
   library and remote. **Make Home** opens Android's Home picker directly.

The basic launcher needs **one APK**. Shizuku, ADB, root and a computer are not
required for the first launch. Check the filename, version and SHA-256 on the
release page; an APK signed with a different key cannot update Carpe TV.

If this device already has a personal test build, it uses a different package ID.
The public APK installs **as a separate app**: test it first, then select it as
Home. The personal build remains available for rollback. Later public releases
update the public app in place when the signing key matches and the version
number increases.

**For developers — ADB install:**

```sh
adb devices
adb -s DEVICE_SERIAL install -r carpe-tv.apk
adb -s DEVICE_SERIAL shell am start -n agency.carpediem.tv/agency.carpediem.tv.prototype.MainActivity
```

Replace DEVICE_SERIAL with your own `adb devices` entry. Do not uninstall the previous
app to bypass a signing mismatch. Debug APKs from different computers/CI may use different keys.

## 2. Set up Home

1. Select **Make Home** on first launch, or later open Carpe TV →
   **Settings → Device → Make Home**.
2. Choose **Carpe TV** in Android Home selection.
3. Open another app and press physical Home. Repeat after reboot.

Some firmware ignores the standard Home choice. The public APK does not include
an Accessibility service that intercepts Google TV. If Home does not work, keep
the stock launcher enabled until a device-specific rollback path is verified.

## 3. Connect Shizuku optional

Shizuku is a separate app providing authorized ADB-level actions without root.
The ordinary launcher and package-state display work without it.

1. Install Shizuku from its [official source](https://shizuku.rikka.app/download/).
2. Enable Wireless debugging in Android Developer options. The phone and TV must be
   on the same **trusted** Wi-Fi network.
3. Open **Carpe TV → Settings → Apps & background → System access setup** and select
   **1. Pair this TV**. Scan the QR code with your phone. The local page expires
   after 15 minutes; do not share its address or the pairing code. Five failed
   attempts close the session; restart the wizard to try again.
4. On the TV, open **Wireless debugging → Pair device with pairing code**. Enter the
   six digits only on the phone page. When it says **Paired**, return to Carpe TV.
   This is a one-time pairing unless Android resets it.
5. Select **2. Start Shizuku** with the TV remote. Authorize **Carpe TV** in Shizuku
   if you agree, and verify the system-access status before making changes.

Phone pairing and remote-button restart were tested on Xiaomi `MiTV-AFMU1`;
other firmware needs separate testing. The temporary local page uses HTTP,
so pair only on a trusted network.

Revoke access in Shizuku at any time. Without root, re-enable Wireless debugging if
needed and press **2. Start Shizuku** after reboot;
our Stick retained the Carpe TV authorization. Without a running Shizuku server,
state remains visible but system mutations are blocked. Disabled analytics stays disabled.

## 4. Disable Xiaomi analytics

Before changing a system package, retain a working APK and a verified route
back to the stock Home app. An APK is not a full backup of private app data.


1. **Settings → Apps & background → Xiaomi Analytics**.
2. If it already says **Disabled**, leave it alone. The video starts with it enabled
   only to demonstrate the action.
3. Select **Disable analytics**. Check that the warning names `com.miui.tv.analytics`.
4. Read the consequences. **Cancel** makes no change; **Continue** executes the action.
5. Wait for confirmation and **Disabled**. If verification fails, use **Refresh status**;
   pressing a button alone does not prove the package changed.
6. Recheck after reboot. Our Stick retained disabled state, with no analytics process
   or active services.

The launcher saves an enabled package's prior state before disabling it. Data is retained.
Enabled describes package state, not proof of current background activity.

To restore analytics: start Shizuku → same card → **Enable / restore analytics** → read
and confirm. A recorded state is restored. If it was disabled outside Carpe TV and no
record exists, the action explicitly enables it; it does not invent a previous state.
Analytics and its services may then run again.

## Return to the stock launcher


### If the stock launcher is enabled

Open **Settings → Device → Choose another launcher** or **Apps & background →
Choose another launcher**. Select the stock Home app in Android's picker and
test the physical Home button. Shizuku is not needed for this route.

### If Google TV is disabled on Xiaomi

1. Ensure Shizuku is running and Carpe TV is authorized.
2. **Settings → Apps & background → Enable Google TV and choose Home**.
3. Read and confirm. Carpe TV enables the Google TV package, verifies the result,
   and opens Android Home selection. Cancelling the chooser leaves the current
   Home selection unchanged.
4. Choose **Google TV**. The Restore button alone does not change the system Home selection.
5. Press physical Home. Check after reboot before removing Carpe TV.

The video ends just after selecting Google TV, before displaying a personal recommendation
feed. Physical Home return to Google TV was separately verified on the same Stick.
If Shizuku is unavailable, no change is made: restart it or use the manual route.

### Without Shizuku

If Google TV is already enabled, select it in Android **Apps → Default apps → Home app**,
Labels vary by firmware.
Keep Carpe TV available until Home is verified.

### If Carpe TV cannot open — ADB recovery

These commands target the verified Google TV package on our Xiaomi Stick. On another
box, identify the actual stock package first. Previously configured ADB access is required;
Shizuku is not needed for this route.

```sh
adb devices
adb -s DEVICE_SERIAL shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb -s DEVICE_SERIAL shell am start -a android.settings.HOME_SETTINGS
```

Choose Google TV in the system screen.
If HOME_SETTINGS is unavailable, use the firmware's own Home settings.
No app-data wipe, APK reinstall or factory reset is involved.

## Return to Carpe TV again

1. Open Carpe TV from the installed apps list. If needed:

```sh
adb -s DEVICE_SERIAL shell am start -n agency.carpediem.tv/agency.carpediem.tv.prototype.MainActivity
```

2. Repeat Carpe TV Home selection from step 2 above.
3. Verify Home from another app. The stock launcher remained installed throughout testing.

## Full Google TV disable — firmware limitation

Disabling `com.google.android.apps.tv.launcherx` on our `MiTV-AFMU1` firmware opened
`com.google.android.tungsten.setupwraith/.RecoveryActivity` instead of Carpe TV.
Reapplying Home selection did not resolve it; Google TV was immediately restored.

Therefore **this guide does not provide Google TV disable instructions**, and the
module offers only its enable action. Do not disable setup/recovery packages or unknown
system services to bypass this behavior. Old Xiaomi Home `com.mitv.tvhome.atv` was already
disabled before our test; that is not a recommendation for other Sticks. Other models
require their own compatibility and rollback tests.

## Updates and older APKs

[Updates, source permission and screenshots](UPDATING.md). Check the channel and signing
notes for each release. Android may reject a lower versionCode even with the same key.
Do not uninstall the current app solely to force a downgrade and lose its private settings.

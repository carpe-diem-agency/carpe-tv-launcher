# Isolated first-run check

Tested on Xiaomi MiTV-AFMU1, Android TV 14, with a 1920×1080 UI at 320 dpi.
The test APK used `-PisolatedPreview`, which adds `.publicpreview` to the
application ID. It installed beside the personal Carpe TV build. After
changing the public application ID to `agency.carpediem.tv`, the new isolated
ID `agency.carpediem.tv.publicpreview` was installed and cold-started again.
Activity Manager reported 1156 ms. The welcome dialog was dismissed with
D-pad Down and OK. Settings → Device displayed the Home chooser, alternative
launcher, Home service and Android settings actions without clipping. None
of these actions was used to change the device's Home role.

- Cold launch from `am start -W`: 1174 ms reported by Activity Manager.
- Fresh install opened the Ukrainian welcome dialog without clipping.
- Pressing Back without choosing an option shows the welcome dialog again on
  the next launch. The installed-app cache no longer suppresses this dialog.
- The `welcome_seen` preference is written synchronously only after selecting
  a welcome action. The isolated `firstrun` instrumentation probe passed on
  MiTV-AFMU1 with the projector off: Back leaves the choice unset, the dialog
  returns on the next Activity launch, and **Спершу оглянути** persists across
  another cold Activity launch. A physical Home and visible-screen check remains.
- D-pad Down and OK selected **Спершу оглянути**.
- Home showed five installed apps in the preferred order: YouTube, TV Bro,
  Київстар ТБ, Netflix and Steam Link. Other devices fall back to their first
  available launchable apps if these are absent.
- D-pad Down moved from the Home rail item to **Усі програми** and opened the
  app library without a click.
- The release APK manifest contains Internet, APK installation, exact-alarm
  and optional Shizuku permissions. The transitive storage and phone-state
  permissions were removed and verified absent with Android `aapt`.

This is a first-run and layout check on one device, not a universal Home role,
rollback, performance-soak or release-signing result. The test build was not
selected as the Android Home app.
After the check, the isolated test package was uninstalled and the personal
Carpe TV activity was confirmed in the foreground.

## Visual check — 2026-11-11

The isolated public preview was installed fresh on MiTV-AFMU1 at 1920×1080.
The Ukrainian welcome dialog showed the complete Home fallback explanation
and both remote-selectable choices without clipping. The screenshot is kept
in the private checkpoint because the owner's app tiles are visible behind
the dialog. The isolated APK was then removed and the personal screensaver
returned to the foreground.

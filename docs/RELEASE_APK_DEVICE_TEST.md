# Temporary signed release APK check — MiTV-AFMU1

## Current navigation candidate, 2026-11-11

### One-APK TV Bro installation check

The exact signed navigation candidate below was copied to a temporary LAN HTTP
server and opened on the stick in TV Bro, without using ADB to install it. TV
Bro downloaded `carpe-tv.apk` to Android's Download folder; SHA-256 on the
device matched `122a2452e3f2616c06b3aa906ad342b77807c96198559569da22b4b0f10e50bb`.
Opening the download showed an Android chooser. Selecting **Package Installer**
and then **Install** reached Android's installation-success screen for Carpe TV.
TV Bro already had permission to install unknown apps, so this did not verify
the first-time permission prompt. It also used a LAN URL, not a GitHub Release.

The personal launcher was restored to the foreground and the public test APK
was uninstalled. Package listing showed only `agency.carpediem.tv.prototype`
after cleanup. The foreground activity was the personal launcher. Android's
generic HOME intent resolved to the stock Google TV package, while
`cmd role get-role-holders android.app.role.HOME` returned the personal
`agency.carpediem.tv.prototype` package. This firmware-specific difference
means generic intent resolution alone is insufficient to verify the physical
Home button. No reboot or projector was used.

TV Bro initially displayed a stale failed page from a prior local-server port.
Restarting only TV Bro and loading the current URL made the download succeed;
this was browser task state, not a Carpe TV installation failure.

The current source was built as the public `agency.carpediem.tv` release APK
and signed locally with the dedicated candidate key. The private test file is
`carpe-tv-0.2.7-nav-candidate.apk`; SHA-256:
`122a2452e3f2616c06b3aa906ad342b77807c96198559569da22b4b0f10e50bb`.
`apksigner verify` passed and its certificate matched the pinned candidate
fingerprint. The APK content check found the three CC0 nature photos, all six
required license texts, the third-party notice and no known private artwork.

On MiTV-AFMU1 with HDMI output off, the signed APK installed beside the
personal launcher. Cold launch took 572 ms according to `am start -W`. The
Ukrainian first-run dialog fit at 1920×1080 and focused **Спершу оглянути**;
selecting it did not change the Android Home holder. On the real release APK,
two D-pad Down presses opened Settings and one more focused Power. The screen
behind Power showed Home, with no Settings panel left visible. Power was not
activated.

Opening YouTube from the first favorite tile succeeded. Returning via the
candidate's `from_home` intent focused the YouTube tile; after force-stopping
only the candidate process, a cold launch with the same intent still focused
that tile (`am start -W` reported 428 ms). These checks exercise the launcher
code path, not the physical Home button. Private ADB screenshots were captured
for first run, Home, Settings, Power focus, hot return and cold return. They
contain the owner's app list and are not approved release media.

The personal launcher was brought to the foreground and the candidate was
uninstalled. Package listing then showed only
`agency.carpediem.tv.prototype`. A subsequent generic HOME resolution pointed
to stock Google TV, but a direct role-holder query returned the personal
package. The physical Home button still needs testing. The candidate key
still needs an independent backup; a real
release-link installation, physical remote Home, projector review and
user-driven rollback remain open. No system reboot was performed.

This page also records earlier candidates. A prior revised signed candidate
and its TV Bro installation result are in [PLAY_PROTECT_TEST.md](PLAY_PROTECT_TEST.md).
The Accessibility Home bridge described below was removed from the current
public source; the older APKs listed here must not be distributed.

Checked on 2026-11-11 with Android TV 14, Android size override 1920×1080,
physical mode 3840×2160 and density 320 dpi. The projector was off. The APK
was the `release` variant for `agency.carpediem.tv` version 0.2.7 (code 14),
signed with a **temporary, test-only key**. Its SHA-256 was
`bf34bc8bb006744ae9e3a28502e95702cdf7345515baa122b4468491cc75433f`.
The test keystore was deleted; this APK is not suitable for publication or
future updates.

- `apksigner verify` passed, and the APK installed alongside the personal
  `agency.carpediem.tv.prototype` without replacing it.
- Cold `am start -W` reported 759 ms for the launcher activity.
- The Ukrainian first-run dialog fit at 1920×1080. **Спершу оглянути** was
  selected by D-pad without assigning the Home role.
- The home screen showed five launchable favorites. D-pad navigation opened
  Settings; moving focus from Settings to the Power rail closed Settings and
  showed Home behind the focused Power icon.
- Opening YouTube from a tile succeeded. Returning to Carpe TV with the
  `from_home` intent restored focus to the YouTube tile. This exercises the
  Home-return code path, **not** the physical Home button or Accessibility
  fallback.
- The Home role holder before cleanup was the personal package. After the
  test, the temporary public package was uninstalled; package listing and
  resumed activity showed only the personal Carpe TV package, and its Home
  role remained selected.

Private ADB screenshots are stored in the local checkpoint
`mistick-customization/universal-workspace/checkpoints/public-release-test-20261111`.
They show installed app names and are not public-release media.

Still required: a backed-up production signing key, installation from the
actual release link without a computer, physical Home and rollback on the
production-signed APK, projected motion review, and tests on another model.

## First-run choice retest

On 2026-11-11 an isolated `.publicpreview` debug build was installed on the
same stick with fresh app data. The welcome dialog put **Explore first** above
**Make Home** and focused Explore. Pressing OK entered the launcher without
opening Android's Home chooser. The personal `.prototype` package remained the
Home role holder. The isolated preview was uninstalled after the check. The
welcome copy was then shortened in Ukrainian and English and a release build
completed; the exact final copy has not yet been reviewed on a projected screen.

## Home bridge and Power focus follow-up

The critical review found two code paths that needed correction before a
public Home test. The accessibility bridge no longer opens Carpe TV merely
because the service reconnects; it responds only to the stock Google TV Home
window event while Carpe is still the selected Home role holder. During stock
rollback, it remains enabled if the Android chooser is cancelled and disables
itself only after stock Home is observed with Carpe no longer selected.

An isolated debug build was installed on the Xiaomi stick. D-pad focus from
Settings to the Power rail showed Home behind a focused Power icon, with the
Home icon unhighlighted; D-pad Up returned to Settings. The test package was
removed and the personal package remained the Home holder. Both APK variants
built and Android lint passed. The revised bridge still needs a physical Home
and rollback check with the production-signed candidate.

## Dedicated signing-key candidate

On 2026-11-11, a dedicated release-key candidate was generated outside the
source tree. Its signing certificate SHA-256 is
`2892d5fc34a65bc17125a9e84c01e3f4df946633385045fba38249148e3df60e`.
The key and credentials have a second **local, same-disk** copy. This does not
meet the independent-backup gate for publication. Neither the key nor its
credentials are in the source tree.

The signed candidate APK for `agency.carpediem.tv` version 0.2.7 (code 14) has
SHA-256 `32c50667e4aae7e2953a544b9fecf86926b1be97d50a526cf168c85865db643f`.
`apksigner verify` passed and its signer certificate matched the key candidate.
The packaged-artwork and license check passed. This is a locally built
candidate, not an APK downloaded from GitHub Releases.

The APK installed alongside the personal launcher on MiTV-AFMU1. Its Ukrainian
first-run dialog appeared with **Спершу оглянути** focused. After that choice,
the Home screen showed the five favorites. Settings opened; moving D-pad focus
to the Power icon showed Home behind Power instead of leaving Settings visible.
The projector was off, so these observations come from ADB screenshots, not a
physical-screen review. Power was not activated. The personal package remained
the Android Home role holder throughout. After the test, the personal app was
brought to the foreground and the signed candidate was uninstalled; package
listing showed only `agency.carpediem.tv.prototype`.

ADB captures from this candidate are in the private local checkpoint
`mistick-customization/universal-workspace/checkpoints/public-signed-candidate-20261111`.
They include first run, Home, Settings and Power focus; they are not yet
approved as public screenshots.

Physical Home, Home-bridge behavior, stock-launcher rollback, projector motion,
installation from the actual release link and another-device testing remain
open. The local candidate must not be treated as a published release.

## Candidate rebuilt with third-party notice

The same key signed a rebuilt 0.2.7 APK after adding the bundled third-party
component notice. This then-current local candidate had SHA-256
`2becc9346461e1cbf4783eecd383c18bbbd1e00e124db668b3f6f11de450d25e`;
the earlier hash above identifies the prior bytes only. `apksigner verify`
passed with one v2 signer and the same certificate fingerprint. The artifact
check found three expected CC0 photos, six license texts and the new notice.
The APK installed on MiTV-AFMU1 with the public application ID. Its fresh
Ukrainian welcome screen still focused **Спершу оглянути**. Pressing OK kept
the personal package as Home role holder. The personal app was then brought to
the foreground and the public test package uninstalled. The projector stayed
off; this check does not replace physical Home, motion or rollback testing.

## Controlled Home-role and ADB keyevent check

The then-current signed candidate was installed again on MiTV-AFMU1. Android 14
refused a shell attempt to enable another application's Home component, so the
candidate's own **Make Home** first-run action enabled it. The Android Home
chooser was cancelled for this controlled test. A script temporarily assigned
the public package to the Home role and enabled its Home bridge alongside the
personal bridge. From Android Settings, an ADB-injected Home keyevent opened
the public Carpe TV main activity. The resulting screenshot is in the private
checkpoint as `home-role-keyevent.png`.

The script restored the original Accessibility setting and personal Home role,
brought the personal launcher to the foreground, and uninstalled the public
candidate. Readback confirmed the personal package held Home again and Google
TV remained enabled. No reboot or projector was used. This verifies the ADB
keyevent and scripted rollback; the physical remote, user-driven Home chooser,
post-reboot behavior, and installation from a live public release remain open.

## Candidate after focus and catalog-cache fixes

The then-current local signed 0.2.7 candidate had SHA-256
`753ff655eef82ead14a2e2bc126e0a216be099787f14f34ebaad4736d9445e78`.
Its signer matches the pinned candidate certificate, v2 signature verification
passed, and the APK check found three CC0 nature photos, six license texts and
the component notice. The prior signed candidate was preserved privately.

This exact APK was installed alongside the personal launcher on MiTV-AFMU1.
ADB confirmed its main activity opened; the first-run screenshot showed
**Спершу оглянути** focused. The test package was then uninstalled and the
personal Home role was confirmed. The HDMI projector was off. The changed
focus behavior was separately exercised by the isolated instrumentation test
recorded in [PERFORMANCE_CHECK.md](PERFORMANCE_CHECK.md); the current signed
APK has not yet been exercised through physical Home or a live release link.

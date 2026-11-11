# Public source preview

This source began as a clean export of the private Carpe TV development tree at
`d00dcdc9929de8f001b71b602818d04dcbd7b539`. It now has its own history
in the public [carpe-tv-launcher repository](https://github.com/carpe-diem-agency/carpe-tv-launcher).
The repository and [preview page](https://carpe-diem-agency.github.io/carpe-tv-launcher/)
are public; a production APK has not been released.

The exported app now uses `agency.carpediem.tv`, separate from the installed
personal build (`agency.carpediem.tv.prototype`). A debug variant with the
additional `.publicpreview` suffix was installed beside the personal build to
check first run, app tiles, settings and D-pad navigation. It was removed after
each check; the personal build was returned to the foreground. The README no
longer offers the private `0.2.7-internal.1` APK as a download for this source.

The export excludes personal game wallpapers, old device screenshots and
device-recorded videos. Both debug and unsigned release APKs build locally.
With HDMI output off, ADB screenshots still showed the isolated public Home,
app library, Settings and the Power-rail transition at 1920×1080. The Settings
page closed when Power received focus. These captures remain in the private
workspace because the library shows the owner's installed apps; they are not
publication screenshots. A read-only device check on 2026-11-11 reported a
3840×2160 physical display mode with a 1920×1080 Android size override and
320 dpi. This does not prove the final HDMI image is correctly scaled. A later
279-second navigation run completed 100
Home→Apps→Settings→Apps→Home cycles; PSS was about 112 MB after the first
20 cycles and stayed near that level through cycle 100. This is a bounded
debug test on one stick, not a release or multi-device soak result. Details
are in [performance checks](PERFORMANCE_CHECK.md).
The release media check finds the three documented CC0 landscape photos and
none of the known private artwork. This is not a complete rights or secret
audit. The built unsigned APK is `agency.carpediem.tv`, version 0.2.7,
approximately 16 MB; its manifest permissions and exported components are
recorded in the [APK surface audit](APK_SURFACE_AUDIT.md). See the
[publication checklist](PUBLICATION.md) for the remaining gates.

A temporary test signature also allowed the release variant with the public
application ID to install beside the personal build. Its onboarding, app
launch, Settings→Power transition and simulated Home-return focus passed on
the Xiaomi stick. The test key was discarded and the test package removed.
This does not substitute for a production-signed APK installed from GitHub;
see [release APK device test](RELEASE_APK_DEVICE_TEST.md).

A dedicated signing-key candidate was then generated outside the source tree.
Its locally signed 0.2.7 APK passed signature, package-media and on-device
first-run checks. Settings→Power also showed Home behind the focused Power
icon. The candidate package was removed after testing; the personal build is
still the Home role holder. The key has only same-disk copies, so independent
backup, GitHub draft-release testing and physical Home/rollback checks remain
open. Details and hashes are in the [device test record](RELEASE_APK_DEVICE_TEST.md).

The installed personal build and its backup remain separate from this export.

On 2026-11-11, the public source aligned the top controls on Home, Apps and
Settings: Power, Wi-Fi, Bluetooth and Timer. The timer was removed from the
left rail. The isolated debug package was installed beside the personal build;
all three pages were visually checked at 1920×1080 and the D-pad Home focus
probe passed. The test package was then removed and the personal launcher
remained the Android Home role holder. This source change does not update the
personal 0.2.7 APK on the stick.

The sleep-timer sheet was then reduced to a bounded 420 dp width with smaller
controls and spacing. An isolated debug build opened it on the same stick at
1920×1080; the screenshot showed the system-access explanation and both
buttons without clipping. This only verifies the unavailable-access state.
Scheduling and the active countdown still require a signed build with the
optional system-access setup and separate device checks.

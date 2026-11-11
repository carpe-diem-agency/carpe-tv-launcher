# Stock Home control

Carpe TV shows the stock Google TV package in **Settings → Apps & background**.
Selecting its row now explains the current package state, offers Android's app
details, and provides the existing enable-only recovery action when the package
is disabled. It never disables Google TV from this screen.

## Why the disable switch is absent on the tested Stick

On Xiaomi `MiTV-AFMU1` / Android TV 14, a controlled attempt to disable
`com.google.android.apps.tv.launcherx` opened the OEM
`com.google.android.tungsten.setupwraith/.RecoveryActivity`. Choosing Carpe TV
for Home again did not prevent that route. The package was re-enabled. A
warning dialog alone would not make the operation safe or reversible for a
person without ADB access.

## Recovery that works today

If Google TV is still enabled, choose it in Android's Home selection from
**Settings → Device → Choose another launcher**. If the package is disabled,
Carpe TV's **Enable Google TV and choose Home** action uses authorized Shizuku
access to enable only the verified Google TV package, checks the resulting
state, and opens the Android Home chooser. It does not delete app data.

If Carpe TV cannot open, the previously configured ADB recovery is documented
in [INSTALL.md](INSTALL.md) and [INSTALL.uk.md](INSTALL.uk.md).
Keep the stock package installed and its data intact.

## Requirements before adding disable support for another model

1. Identify the exact stock Home package and firmware, and verify another Home
   app remains selected and reachable using the physical remote.
2. Record the stock package's per-user enabled state and retain a recovery path
   that works even if Carpe TV fails to start. A reinstallable APK alone is not
   sufficient.
3. Implement a device-specific, allowlisted Shizuku transaction: disable only
   the identified stock Home package, immediately verify the resulting Home
   route, and restore the original package state on failure or timeout.
4. Show the exact package, effect, recovery steps, and an explicit confirmation
   before the mutation. Do not make it automatic on installation or startup.
5. Test Home, cold launch and rollback on that model before exposing the switch
   to its users. Xiaomi `MiTV-AFMU1` remains excluded unless new firmware
   evidence overturns the recovery-screen result.

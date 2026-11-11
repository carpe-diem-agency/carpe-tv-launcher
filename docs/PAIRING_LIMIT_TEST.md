# Isolated LAN pairing-form test

On 2026-11-11, `PairingLimitProbe` ran on Xiaomi MiTV-AFMU1 / Android TV 14 through the isolated `agency.carpediem.tv.publicpreview` debug package. It started a local form with a receiver that always rejects codes, without invoking Android pairing, Shizuku or a real TV code.

The probe received HTTP 200 for the first form, HTTP 409 for each of five six-digit code submissions, then confirmed that the old form was unreachable. It created a fresh session and received HTTP 200 there. Instrumentation returned `pairingLimit=5 rejected; old session closed; fresh session reachable` and success code `-1`.

The test and preview packages were uninstalled afterward. Android still reported `agency.carpediem.tv.prototype` as the Home role holder. The projector remained off; no reboot occurred.

This checks the five-attempt path and new-session recovery. It does not test a phone browser, real Android pairing, the five-second stalled-read timeout or whether plain HTTP is acceptable for public distribution.

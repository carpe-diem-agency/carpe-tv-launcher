# Remote shortcuts — implementation plan

Status: the connected Xiaomi remote profile and firmware layout have been read. Physical key capture and delivery checks are pending. Remapping remains planned.

## Scope agreed with the owner

- Configure only branded shortcuts: Netflix, YouTube, Prime Video, Xiaomi TV.
- Keep directional navigation, OK, Back, Home, volume, mute and power unchanged.
- Targets: an installed application, Carpe TV app library, sleep timer or screensaver.
- Show a matching remote illustration only for a verified hardware profile. Otherwise show a clearly generic remote diagram, with actual detected device name and supported shortcut buttons.

## Xiaomi TV Stick firmware evidence · 2026-11-22

On the MiTV-AFMU1 running Carpe TV 0.2.61, Android identified the connected
Bluetooth input as `Xiaomi RC Consumer Control` (vendor `2717`, product
`32b9`). The active layout was
`/vendor/usr/keylayout/Vendor_2717_Product_32b9.kl`. The shell read the
layout and key capabilities of that input node without capturing key presses.
The driver reports each Linux code in the table. The names come from the
firmware comments and still need confirmation against the physical labels.

| Firmware section | Linux scan code | Android key name |
| --- | --- | --- |
| Netflix | 240 or 416 | `BUTTON_6` |
| Prime Video | 207 | `BUTTON_7` |
| YouTube | 425 | `BUTTON_3` |
| MI | 399, 150 or 400 | `BUTTON_4` for 399, `BUTTON_5` for 150 or 400 |

The channel-list section also maps scan code 393 to `BUTTON_3`. Matching
YouTube on `BUTTON_3` alone would also match that code. The MI section lists
multiple codes, so its comment alone cannot identify the owner's physical
Xiaomi TV button. Plan a verified match on descriptor, Android key code and
observed scan code for this profile. Treat an unavailable or inconsistent
scan code as unsupported when the Android key code collides. Input delivery to
the launcher or accessibility filter remains unverified. [Android key layout files](https://source.android.com/docs/core/interaction/input/key-layout-files) · [Android KeyEvent](https://developer.android.com/reference/android/view/KeyEvent#getScanCode()).

For physical confirmation, use a short, explicit learning session for one
named button at a time on this remote. Stop after its first down/up pair or
the timeout. Compare its observed code with the firmware section before
enabling an assignment. Check actual dispatch to the intended receiver
separately, since driver delivery alone cannot establish that Android or the
firmware will let Carpe TV consume the key.

## Implementation order

1. First diagnose the reported regression: Carpe TV does not always start after reboot. Read Home role, enabled HomeEntry component, enabled and bound Home service, boot completion and foreground activity before changing any setting. Preserve Google TV recovery and existing accessibility services. Do not add a boot receiver that merely assumes Android permits a background activity launch.
2. Read InputDevice name, vendor/product IDs and descriptor; use descriptor for local per-device mappings, not a changing numeric device ID. Do not upload hardware identifiers.
3. Add an explicit opt-in shortcut filter via AccessibilityService onKeyEvent / canRequestFilterKeyEvents. Explain the expanded capability before enabling. No window-content retrieval. No general key log. A second key-filter service can conflict, so show a compatibility failure rather than promise support.
4. Guided learning session with timeout: ask the owner to press a chosen branded button once. Reject reserved navigation/system/media keys. Store only the verified Android key code, scan code where needed, device descriptor and chosen target. Unrecognized or firmware-intercepted buttons remain unsupported.
5. Allow app selection from the existing cached library and three fixed Carpe actions. One press launches once; consume both down/up for a successful mapping, avoid repeats; preserve normal handling if no valid target exists. No arbitrary shell commands or injected replacement key presses.
6. Provide per-button reset to factory action, global disable and reset-all. Default is unchanged behavior. Never uninstall or disable the original branded apps as a shortcut workaround.
7. Verify the four physical buttons from Home and another app, long press/repeat, target removal, service off, reboot and rollback. ADB injected events do not replace a physical remote compatibility test.

## UI

Settings → Remote. Remote illustration on the left; selectable shortcut buttons and current assignments on the right. A visible “Not configured / Supported / Not detected” state. Selecting a supported button opens an app/action picker in the existing Montserrat, rounded-surface and remote-focus style.

## Android references

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS
- https://developer.android.com/reference/android/view/InputDevice#getDescriptor()

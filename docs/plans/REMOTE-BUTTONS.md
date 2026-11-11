# Remote shortcuts — implementation plan

Status: planned; physical key capture and compatibility verification pending. No remapping or additional accessibility capability is enabled yet.

## Scope agreed with the owner

- Configure only branded shortcuts: Netflix, YouTube, Prime Video, Xiaomi TV.
- Keep directional navigation, OK, Back, Home, volume, mute and power unchanged.
- Targets: an installed application, Carpe TV app library, sleep timer or screensaver.
- Show a matching remote illustration only for a verified hardware profile. Otherwise show a clearly generic remote diagram, with actual detected device name and supported shortcut buttons.

## Implementation order

1. First diagnose the reported regression: Carpe TV does not always start after reboot. Read Home role, enabled HomeEntry component, enabled and bound Home service, boot completion and foreground activity before changing any setting. Preserve Google TV recovery and existing accessibility services. Do not add a boot receiver that merely assumes Android permits a background activity launch.
2. Read InputDevice name, vendor/product IDs and descriptor; use descriptor for local per-device mappings, not a changing numeric device ID. Do not upload hardware identifiers.
3. Add an explicit opt-in shortcut filter via AccessibilityService onKeyEvent / canRequestFilterKeyEvents. Explain the expanded capability before enabling. No window-content retrieval. No general key log. A second key-filter service can conflict, so show a compatibility failure rather than promise support.
4. Guided learning session with timeout: ask the owner to press a chosen branded button once. Reject reserved navigation/system/media keys. Store only the verified shortcut code + device descriptor + chosen target. Unrecognized or firmware-intercepted buttons remain unsupported.
5. Allow app selection from the existing cached library and three fixed Carpe actions. One press launches once; consume both down/up for a successful mapping, avoid repeats; preserve normal handling if no valid target exists. No arbitrary shell commands or injected replacement key presses.
6. Provide per-button reset to factory action, global disable and reset-all. Default is unchanged behavior. Never uninstall or disable the original branded apps as a shortcut workaround.
7. Verify the four physical buttons from Home and another app, long press/repeat, target removal, service off, reboot and rollback. ADB injected events do not replace a physical remote compatibility test.

## UI

Settings → Remote. Remote illustration on the left; selectable shortcut buttons and current assignments on the right. A visible “Not configured / Supported / Not detected” state. Selecting a supported button opens an app/action picker in the existing Montserrat, rounded-surface and remote-focus style.

## Android references

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onKeyEvent(android.view.KeyEvent)
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#FLAG_REQUEST_FILTER_KEY_EVENTS
- https://developer.android.com/reference/android/view/InputDevice#getDescriptor()

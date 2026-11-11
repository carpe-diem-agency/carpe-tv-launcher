# User-flow acceptance for a public Carpe TV APK

This is a release gate for the **exact APK offered to users**, not a claim that
the current source or an isolated preview has passed. Run each flow with a
physical remote on the tested device. Record model, Android version, display
mode, APK SHA-256, app version, signing certificate and result. Keep screenshots
that show accounts, installed apps or network details out of public media.

| Flow | Expected result | Evidence today | Final APK gate |
| --- | --- | --- | --- |
| First install from the published link | Android opens the APK, grants install-source permission when needed, then starts Carpe TV in Ukrainian or English without changing Home automatically. | [Isolated first run](FIRST_RUN_TEST.md) and [LAN TV Bro install](RELEASE_APK_DEVICE_TEST.md) cover parts of this flow. | Open the GitHub release asset from TV Bro and repeat on a second TV device. |
| Choose Home | The user selects Carpe TV through Android's chooser. Home opens Carpe TV after another app and after a reboot. | [ADB-assisted candidate test](RELEASE_APK_DEVICE_TEST.md) covers the role and injected Home only. | Press the physical Home key from YouTube and after reboot. Test firmware that honors the Home role and record any OEM fallback separately. |
| Return to the same tile | Launch YouTube from a favorite, press Home, land on Home with the YouTube tile focused. Repeat after the launcher process is killed. | [Candidate hot and cold return](RELEASE_APK_DEVICE_TEST.md) passed through a `from_home` intent; [HomeFocusProbe](../app/src/androidTest/java/agency/carpediem/tv/prototype/HomeFocusProbe.java) covers lifecycle and refresh. | Repeat using the physical remote and final APK. The next D-pad move must start from YouTube, with no focus jump to Settings. |
| Browse and launch apps | Sidebar focus opens the selected page; the library appears without a loading jump; search, app launch, Back and reorder preserve focus. | [Navigation and reorder probes](VALIDATION.md) passed on one stick. | Rapidly traverse every rail item and a long app list on both test devices; visually check focus, labels, shadows and clipping. |
| Settings and personalization | Each category opens with one D-pad move; wallpaper, dimming, saver timeout, language and favorite order survive process restart. | Individual checks are recorded in [validation](VALIDATION.md); one final end-to-end run is missing. | Complete the sequence on the final APK in both languages and at 1080p and 4K output. |
| Update and rollback | A newer signed APK downloads in the background, Android asks for install confirmation, preferences survive, and a user can restore the stock Home choice. | [Update tests](VALIDATION.md) used controlled feeds and candidates. | Test the published GitHub feed and exact signed release asset, then restore stock Home through the user-facing path. |
| Report a problem | Settings opens a readable GitHub Issue QR; phone scan reaches the issue form; Back returns to the same Settings entry. | [FeedbackProbe](../app/src/androidTest/java/agency/carpediem/tv/prototype/FeedbackProbe.java) checks QR, browser fallback and focus; phone scan remains open. | Scan the final APK's QR with a phone and verify the form opens without exposing local device details. |
| Optional system access | Basic launcher works without Shizuku; the wizard explains each extra permission and safe recovery. | [System-access tests](VALIDATION.md) cover one stick. | Repeat only after the basic launcher passes; verify turning the stock launcher back on before any disable action is offered. |

For each flow, record **pass**, **fail** or **not tested**. A build, static test,
emulator or injected key event cannot stand in for the physical-remote and
published-APK checks above. A failed flow blocks release until fixed and
retested; do not remove the existing launcher or disable stock Home to make a
test pass.

# Wallpaper validation — 2026-11-03

Xiaomi MiTV_AFMU1, Android TV 14, Carpe TV 0.2.1 (version code 8), 1920×1080 UI canvas.

- Debug, Android instrumentation APK and unsigned release builds compiled; `lintDebug` passed.
- APK ZIP inspection: S.T.A.L.K.E.R. image and thumbnail present only in debug, absent in release.
- On-device instrumentation passed all seven choices, horizontal D-pad scrolling, selected-state markers, asynchronous loading, rapid changes and persistence after Activity restart.
- Full backgrounds and blur load on the existing background worker; gallery uses 320×180 thumbnails.
- Actual Home previews were captured for all four additions. No synthetic UI was used.
- Existing wallpaper choice and dimming restored after QA; the optional Home service restored after instrumentation.

The pre-change APK, preferences and code snapshot are retained locally in `mistick-customization/universal-workspace/checkpoints/wallpapers-20261103`. APK backups are not full system or private-data dumps. No new GitHub release was published by this change.

## Dynamic mode and selected HUD-free posters — 0.2.3

- Debug, test and unsigned release builds plus lint passed.
- Synthetic-clock tests covered just before/exactly at 30 minutes, no duplicate change, a long overdue interval, clock rollback and a complete cycle of the six photo choices.
- Real Activity lifecycle test confirmed one scheduled foreground callback, cancellation while Android Settings was foreground, one overdue rotation upon return, and cancellation when disabled.
- APK resource inspection confirmed all four S.T.A.L.K.E.R. image/thumbnail resources absent from release and present in debug.
- Full 30-minute wall-clock waiting was replaced by controlled timestamp tests; this is not a claim that a 30-minute recording was made.
- Recommended dimming remains 60%. New debug posters replace combat with the selected Duga and Pripyat images.

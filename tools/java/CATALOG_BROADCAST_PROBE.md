# CatalogBroadcastProbe — device protocol

Dispatch with `catalogbroadcast=yes` through the guarded host runner.
Root alone owns device execution.
No synthetic catalog, production hook or system package mutation.

Host prerequisites: build Android's tiny `agency.carpediem.tv.testfixture.catalog`
APK (versionCode 1, label `Carpe QA Калина`); validate whole ZIP CRC, actual manifest package/version/signature and exactly
one safe launcher label containing `Калина`; push that verified hash to exactly
`/data/local/tmp/carpe-catalog-fixture.apk`. Refuse an already installed fixture,
and record normal APK/preferences/Home/services baseline before isolated launch.
Never overwrite another package or an existing fixture installation.

Probe requires actual scrollable catalog with empty query, records exact package
set, selects an existing tile and nonzero Y. Actual `pm install -t` must cause the
registered PACKAGE_ADDED path to show precisely original set plus fixture while
preserving query, surviving focus and clamped scroll. Programmatic Cyrillic query
isolates the fixture. Injected DPAD OK opens the actual fixture Activity and Back
returns to the same query and tile focus. Actual uninstall
must yield empty results, unchanged query and search focus. Clear restores exact
original package set. Real broadcasts are not simulated or manually delivered.

The second install occurs with Settings in the foreground. The probe waits for
the fixture to enter the catalog cache, lets the catalog worker hand off its
result, and checks that the same Settings detail and control still have focus.
Returning to Apps must show the exact expanded set. A shared search query then
selects the fixture alongside multiple existing apps. Actual uninstall must
retain the query and focus a surviving result in the exact remaining set.

Only the exact fixture package can be uninstalled, and only after this invocation
attempted installation from an absent baseline. Finally checks its absence, stops
owned Activity/workers and restores full Main preferences. Host must independently
check fixture absence and remove only its own pushed APK after success/failure;
a killed instrumentation cannot guarantee Java finally. Shell calls have bounded
90-second waits/read limits; the host preserves its existing 180-second per-probe
cap. Package command failures are prerequisite failures, not launcher defects.
Scope excludes system settings, Home intents, installer sessions, remote downloads,
soft-keyboard interaction and removal while Settings is open.

Host invocation adds `--catalog-fixture PATH_TO_VERIFIED_APK` and optionally
`--java-home` (defaults to the approved Homebrew Java 17), with flag
`catalogbroadcast`. `--verify-only` checks fixture CRC, exact manifest and pinned
public debug certificate without ADB. Runtime refuses pre-existing fixture package
or staging file, pushes only the fixed path and verifies its SHA. Finally host
independently removes its owned fixture package/staged file and verifies absence.

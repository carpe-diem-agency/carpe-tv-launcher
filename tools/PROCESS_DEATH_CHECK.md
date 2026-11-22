# Isolated stopped-task process death check

Run from an external host so the observer survives target-process death.
It only accepts a fresh `agency.carpediem.tv.validation.publicpreview` APK with
the positive version code explicitly passed with `--expected-version-code`, and refuses an already installed validation package.

```sh
runtime=/path/to/carpe-tv-runtime
python3 tools/process_death_check.py --adb "$runtime/sdk/platform-tools/adb" \
  --aapt "$runtime/sdk/build-tools/36.0.0/aapt" \
  --apk "$runtime/build/app/outputs/apk/release/app-release.apk" \
  --expected-version-code CURRENT_CODE --artifacts "$runtime/logs/process-death-checks" \
  --label process-death-home-01 --case home-favorite \
  --main-component agency.carpediem.tv.publicpreview/agency.carpediem.tv.prototype.MainActivity
```

Artifacts can live in the approved local runtime at
`/path/to/carpe-tv-runtime/logs/process-death-checks`
or beneath mounted `/path/to/isolated-carpe-tv-runtime` or `/Volumes/CarpeTVOverflow`.
Each run directory is
mode 0700. `--transport-id` defaults to 1 and selects `adb -t` explicitly. Raw UI XML/activity dumps
contain private app labels; do not publish them. The printed summary is sanitized.
No force-stop, clear-task, Home intent/key, permission/IME/system-setting action or
production APK installation is used. Main is started explicitly to background
validation for the ordinary cases and to restore the original foreground on cleanup.
Main preferences are hashed through
read-only `run-as tar`; inability to obtain that hash stops the test before install.

The Apps case uses an empty query and actual installed apps. It captures a focused
tile identity hash, tile bounds/order and UIA query-attribute hash (may include hint text), backgrounds validation,
requires STOPPED state, kills only that background process, and requires a new PID
with the same retained task on explicit component relaunch. It compares observable
UI fingerprints; it does **not** establish exact private scroll Y, physical Home,
nonempty-query restoration, Authors, modal restoration or fresh-task persistence.
The XML parsing and viewport prerequisites can return INPUT_UNVERIFIED; never use
force-stop as a substitute when `am kill` does not kill a background target.

Cleanup uninstalls only packages installed by this run, explicitly resumes Main,
and checks Home-role/accessibility-services/main version/APK hashes and canonical sorted preference-file content hashes (tar header metadata excluded).
A cleanup failure requires host review of the private evidence before proceeding.

The case includes actual Apps-rail tap followed by D-pad navigation. Failures in
that setup are UX6 evidence, not process-death evidence. Summaries report the
phase and whether the process-death command was reached; only new-PID/same-task
verification establishes actual process restoration.

## Additional cases

`--case apps-external-back --catalog-fixture PATH` uses the ordinary
signed catalog fixture and a non-debuggable validation APK. It focuses the
fixture through the Apps search and opens it with D-pad OK. It waits for the
validation Activity to stop, kills only that process and confirms the
fixture still has the same foreground task and PID. One D-pad Back must
restore the original validation task in a new process with an equal Apps
fingerprint. The return path uses no explicit launcher activity start.

`--case device-diagnostics` opens the Device category and its diagnostics from
the focused action. After stopped-task process death it requires the Device
category and action focus to return with the dialog closed. D-pad OK must open
the report again with a visible QR and matching report text. Back must restore
the same action. The check observes the QR and its report text in the UI.

`--case apps-scroll` requires a non-debuggable validation APK with a
validation-only observer. Add an AccessibilityDelegate to the Apps ScrollView
in an isolated copy of the source. After calling the parent node initializer,
set only the node description to `carpe-test-scroll-y:` followed by
`host.getScrollY()`. Keep that source patch outside the delivered app.
The runner requires a baseline at zero and a nonzero offset with a changed
focused tile. Two matching UI reads before and after prove exact reported Y,
focus, visible tile order and bounds for the retained task. An ordinary
validation APK without the observer fails closed for this case.

`--case apps-scroll-rail` uses the same observer. It leaves the scrolled grid
by moving Left across the current row to the selected Apps sidebar item, with
the viewport held at the same offset. After process death it compares the
sidebar focus and viewport, then presses Right and compares the focused tile
and viewport against the last grid state seen before moving onto the sidebar.

`--case apps-query --query a` enters an ASCII query through Android input and
uses D-pad to select a matching tile. It compares the visible matching tiles,
focused tile and bounds after the same stopped-task process-death sequence.
Use a short query with at least two visible matches. The UI fingerprints
establish the visible viewport, not a private scroll offset or offscreen results.
The runner records Android's keyboard visibility after typing. When the keyboard
is reported open, Back must leave the query and editor focus intact before Down
can target a tile.
If Down leaves focus in the editor, the test re-enters through the verified Apps
rail and records the route as `apps-rail-reentry`. That run covers restoration
and leaves the editor-to-results Down route unverified.

`--case apps-query-scroll --query a` uses that same input route and the
validation-only scroll observer. It requires a filtered list that can scroll
through D-pad input. It establishes a zero offset after entering the query
and a positive offset with a different focused tile before backgrounding. A
compact Apps layout may be used in the isolated fixture when the matching
catalog fits the normal viewport. The summary includes the measured viewport
height. The check compares the query, exact offset, focus, and visible tile
order and bounds for the retained task.

`--case apps-query-editor` types a nonempty query and leaves focus in the
editor with the keyboard closed. It uses the same process/task witnesses and
checks editor focus, the query, result bounds and keyboard visibility after
restore. It then opens the keyboard explicitly with D-pad OK and verifies that
Back restores the same editor context. Both query cases accept `--query` with
short ASCII text.

`--case apps` retains the original touch/D-pad Apps setup. `--case authors`
opens Settings → localized About → Authors and establishes real D-pad focus on
Authors Back. `--case authors-modal` establishes focus on the code-license action,
captures the underlying Authors fingerprint, then opens and confirms Apache text.
No URL or system action is activated. Both use the same STOPPED / kill / retained
task / new-PID protocol. The modal case expects the license safely closed after
restore, with Authors and its originating control restored; it does not expect a
dialog to reopen. Missing UI/focus prerequisites are INPUT_UNVERIFIED. Differences
after the PID/task gates are FAIL. Raw fingerprints remain in the private run directory.

`--case home-favorite` focuses the second real favorite with D-pad and compares
the active tile, visible order and bounds after process death. `--case home-rail`
and `--case home-header` first focus the same tile, then move away from it.
They verify that the active rail or header button restores and that its next
Right or Down still enters the remembered tile. The runner uses Back as a
local app action and never activates the system Home action or a header button.

## Artifact association

New runs include validation APK SHA-256, the executing host script SHA-256 and
expected version code in the summary. `process-witness.json` in the private run
directory records old PID/task, verified absence of the killed PID, retained task,
and restored new PID/task as each protocol gate is reached. Sanitized summaries
retain the booleans; private IDs remain in the run witness. Earlier runs without
these fields cannot be retroactively described as having recorded these hashes.

Retained-task relaunch uses `am start -W`. The summary includes first-display
timing only when Android reports the exact validation Activity and a cold
launch. Functional restoration still requires the separate new-PID/same-task
and stable UI checks. The host's time to the verified UI is an upper bound
that includes ADB, the settling delay and two matching UI snapshots.

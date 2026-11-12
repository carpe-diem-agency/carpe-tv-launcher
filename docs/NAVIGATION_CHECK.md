# D-pad navigation check on MiTV-AFMU1

Android TV 14, 1920×1080 UI, 320 dpi. Tests used an isolated `.publicpreview`
debug package installed beside the personal build. The personal Home selection,
system packages and display settings were not changed.

Thirty rapid cycles of Home → Apps → Settings → Apps → Home were sent through
ADB. `dumpsys gfxinfo` was reset before each run:

| Page transition | Frames | Median | p95 | Janky frames |
| --- | ---: | ---: | ---: | ---: |
| Original 190 ms crossfade | 142 | 44 ms | 93 ms | 81 (57%) |
| Temporary whole-page GPU layers | 117 | 57 ms | 129 ms | 86 (74%) |
| 70 ms outgoing fade | 118 | 61 ms | 121 ms | 79 (67%) |
| Suppress overlapping page transitions | 138 | 44 ms | 89 ms | 80 (58%) |

The GPU-layer and short-fade experiments were reverted. The last variant
keeps the normal 190 ms crossfade and immediately settles an interrupted
transition during rapid key repeats. Its measured improvement is small and
does not establish Apple TV-level frame pacing; the library still needs
rendering work and longer visual checks on the projector.

Settings → Power was checked on the isolated build: Home content appeared
behind the focused Power icon. YouTube was launched from a favorite tile;
starting the test activity with `from_home=true` restored focus to that tile.
That models the Home intent but does not prove physical Home behavior with an
isolated package. The physical Home bridge was tested separately in the
personal build. The isolated package was uninstalled after the checks.

## Personal build, passive measurement

With the personal build foreground and no test input, `dumpsys meminfo`
reported 104,039 KB total PSS (147,488 KB RSS), including 40,168 KB native
heap and 18,144 KB graphics. `dumpsys gfxinfo` reported 711 accumulated
frames, 68 janky (9.56%), median 10 ms and p95 65 ms. These counters span
earlier activity in that process; they are not a controlled benchmark of
the public preview. GPU p95 was 14 ms, while Android counted 49 slow UI
thread frames and 62 slow draw-command frames. The rapid D-pad test above
is the stronger evidence for transition responsiveness.

After limiting state-fade configuration to the newly built page and configuring
the fixed sidebar once, an isolated debug install completed ten D-pad cycles.
Android reported 190 frames, 29 janky (15.26%), median 21 ms and p95 109 ms.
The screenshot showed the Home page and five app tiles intact. This shorter
run used a different input sequence and includes cold first-run work, so it
cannot be compared directly with the thirty-cycle table or claimed as a
performance improvement. The test app was removed and the personal build
restored afterward.

The isolated Apps & background probe was repeated after adding a generic
**Choose another launcher** action. It verified that the chooser is available
without Shizuku and that the Google TV enable action appears only when its
package exists. The first run exposed a stale test setup: the welcome dialog
still held focus. With first run dismissed, D-pad Right reached the
system-access action and the probe passed without changing package states.
Both preview packages were removed; the personal launcher remained Home.
This verifies controls and focus, not a physical rollback.

## Instrumented frame stages

The existing `NavigationProbe` ran on the isolated build after first run.
It passed sidebar and app-grid focus checks. For 36 page changes it measured
312 frames, median 31.99 ms and p95 56.73 ms. Stage p95 values were input
0.009 ms, animation 2.09 ms, layout 1.54 ms, draw 9.34 ms, commands 15.84 ms,
sync 1.99 ms and swap 10.09 ms. The main cost is rendering and submission,
not key handling.

An experiment that hid the outgoing page immediately and faded only the
incoming page measured p95 48.36 ms over 309 frames, but draw-stage p95 rose
to 19.55 ms. With HDMI output off, Android screen recording captured too few
frames to judge whether the transition looked natural. The experiment was
reverted; the measured result is evidence for future work, not a shipped
animation change.

# Home focus regression, isolated build

On MiTV-AFMU1 (Android TV 14), the `homefocus` instrumentation probe passed
after the app-list refresh fix. It checks that Home returns focus to the last
opened app, a subsequent background list update keeps that focus, and moving
from Settings to Power shows the Home page. The test APKs were uninstalled and
the personal launcher was confirmed in the foreground afterward. This does
not validate a physical Home press with the projector off.

On 2026-11-11 the isolated probe was repeated after adding an explicit
Settings → Power visibility assertion. The first assertion incorrectly treated
an invisible cached Settings page as visible content; Android's root
`findViewById` can locate cached views. The corrected probe checks the active
page tree and `isShown()`. It passed Home toolbar navigation, last-app focus,
app-list refresh focus, and Settings hidden on Power focus. Both isolated test
packages were removed; `agency.carpediem.tv.prototype` remained the Home role
holder and foreground app. This still does not test the physical Home key in
the public signed build.

# Return focus follow-up · 2026-11-11

The public source now restores the last opened app when its activity resumes
after launching an app, when a Home intent arrives, and after a cold relaunch.
It waits for the launcher window to gain focus and for the cached tile to be
attached. On MiTV-AFMU1, an isolated `agency.carpediem.tv.publicpreview` build
launched Netflix with D-pad/OK; bringing Carpe TV back highlighted Netflix.
A cold relaunch highlighted the previously opened TV Bro tile. The test package
was uninstalled, and `agency.carpediem.tv.prototype` remained the Home holder.
ADB's touch-coordinate input puts Android in touch mode; a non-touch-focusable
TV tile will not receive programmatic focus in that mode, so the return test
used D-pad/OK. Physical Home with the public release remains unverified.

On 2026-11-11 a later isolated build was opened on the same stick. From
Settings → About, the third-party notice opened and scrolled with D-pad.
After closing it, selecting the left-rail timer switched the page underneath
to Home before displaying the timer dialog; the Settings panel was absent in
the captured frame. The preview package was removed afterward and the
personal launcher remained the Home role holder. This visual check covers the
Settings → timer transition, not sleep execution or physical Home behavior.

A later direct-tap check found a separate path through the same control:
touch activation did not necessarily move focus first, so the timer opened
over Settings. The Power click handler now switches to Home itself before
opening the timer. On MiTV-AFMU1 the isolated build was installed again,
Settings → Power was activated by tap, and the captured frame showed Home
behind the timer. The preview package was then removed; the personal launcher
was again foreground and the Android Home role holder. This verifies the
direct-click path, while physical remote navigation still needs release testing.

## Settings Back route — 2026-11-12

When focus is inside the settings detail pane, Back now returns to the
selected category. A second Back from the category list still returns Home.
This matches the existing Left route and avoids losing the settings context.
On MiTV-AFMU1, the isolated `settingsfocus` instrumentation probe passed
rail → category → detail → Back → category → next category → detail → Left →
category → Left → rail, then direct category activation → detail → Back to
that same category. The probe did not change any system setting. The
previous isolated preview APK was restored after the test; a visual check on
the projected display is still pending.

## Library search focus — 2026-11-12

The isolated `libraryfocus` instrumentation probe passed on MiTV-AFMU1:
Apps rail → first tile → search; entering the first app's full name retained
that app in results and Down/Up moved between result and search; an unmatched
query showed the empty-state message and kept focus in search; clearing it
restored the grid; Down returned to the first tile and Left to the Apps rail.
The test used the stick's installed app list and did not open
an external app or change Android settings. It checks focus behavior, not
keyboard appearance, search latency or projected-screen layout. The prior
isolated preview APK was restored, the instrumentation package removed, and
the accessibility-service setting still read `null` afterward.

## Library search rendering — 2026-11-12

An ADB screenshot of the isolated build exposed a gap in the focus probe:
typing `Aerial` left an empty-looking library even though Android's view tree
contained the matching Aerial Views tile. Moving focus down made the tile
appear. The search change now resets the grid scroll position and invalidates
the grid and viewport after rebuilding results. On MiTV-AFMU1, the matching
tile remained visible while the TV keyboard was open after typing and deleting
characters. This is a visual check on the 1920×1080 output, not a projector
review or a guarantee for other IMEs.

## Screensaver settings density — 2026-11-12

On the 1920×1080 Xiaomi stick, the three-line cache explanation pushed the
**Preview now** action partly below the visible area. D-pad Down did scroll it
fully into view, so the route worked but the layout was unnecessarily dense.
The cache usage and low-space state now occupy one line, and toggling online
downloads refreshes that state immediately. An isolated preview screenshot
showed **Preview now** fully visible; the switch-off state kept the shorter
status. The `screensaver` instrumentation probe then passed idle boundaries,
offline photo launch, remote dismissal and settings focus/reserve navigation.
The test package was removed and the previous preview APK restored. The visual
check used ADB capture; projector appearance remains to be reviewed.

Selecting a new free-space reserve previously rebuilt the Screensaver pane
and moved focus to the online-download switch. The selection now returns focus
to **Keep free**, matching the timeout and source selectors. The isolated
`screensaver` probe passed the reserve-choice route and confirmed the pane
stays open with that control focused. The test package was removed and the
previous preview APK restored after this check.

## Library state after recreation — 2026-11-12

The isolated `librarystate` probe exposed a lost selected tile after Activity
recreation. The library now saves its search query, selected app and scroll
offset, then restores focus after the cached app list arrives. The probe passed
on MiTV-AFMU1 at 1920×1080: library, query, selected tile and scroll offset
survived recreation. The previous preview APK was restored, the test package
removed and accessibility services remained disabled (`null`).

## Toolbar focus during app refresh — 2026-11-12

App-list broadcasts rebuild Home or Library while a remote-control toolbar
button can have focus. Focus restoration now accepts the visible replacement
of that control, including Power, Wi-Fi, Bluetooth and Timer, as well as the
existing app-tile path. The isolated `homefocus` probe passed on MiTV-AFMU1
after refreshing both Home and Library with toolbar focus. It also passed the
tile, Back and external-app return paths. The test closes the optional sleep
confirmation before checking D-pad movement. The previous preview APK was
restored, the test package removed and accessibility services remained `null`.

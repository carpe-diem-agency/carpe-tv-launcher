# Design and navigation baseline

The screenshots are the current implementation baseline, not a claim of pixel-perfect
match to older concepts. Montserrat, wide TV cards, soft shadows and native XML
vectors form the current visual system.

- Shared flush-left icon-only rail: Home, app library, settings; subtle agency logo
  and version at the bottom. Section changes on focus, without a second OK.
- Up/down stays in the rail. Right enters page content; left returns to the rail.
- Library uses five columns at the reference width. Search keyboard opens on OK;
  Back hides it before leaving the page.
- Focus scales artwork to 1.025 over 240 ms; exit is 200 ms. Labels stay on baseline.
- The previous page is hidden when a transition starts. The incoming page
  animates from 72% opacity with a 4 dp vertical offset to its final position
  over 180 ms. The wallpaper crossfades separately over 200 ms. Animations
  are interruptible and honor the app/system animation setting. This avoids
  drawing two full page trees at once on the tested Xiaomi stick.
- The default wallpaper is a redistributable mountain lake photo. The library
  uses a blurred version of the selected wallpaper and settings add dimming.
- Activity density adapts a 960 dp canvas to the display width. The app does not
  force a global display resolution. Test captures are 1920×1080; 4K output and
  other aspect ratios require separate device validation.

Source of truth for sizes/colors: `app/src/main/res/values/design_tokens.xml`.
Do not add a new visual/focus convention per page. New features must preserve
D-pad routes, legibility at viewing distance and bounded bitmap memory.

## Language and updater

English is the default resource set with a complete values-uk translation. Appearance
contains System default / Українська / English; selection affects this app only and
returns focus to the language row after recreation. Third-party names/system dialogs
remain device-owned. The updater uses the same Montserrat/surface/focus vocabulary,
with a scrolling panel and bounded descriptions. The app label is Carpe TV.

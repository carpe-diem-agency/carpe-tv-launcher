# Wallpaper sources and distribution

## Redistributable photo set

These three images are documented as **CC0 1.0** on their individual Wikimedia Commons file pages (rechecked 2026-11-11). CC0 permits copying, adaptation and redistribution, including commercial use; no attribution is required, but source and author credits are retained here. The SHA-256 hashes of the three bundled images were also checked against `sources.json` on 2026-11-11.

| Launcher name | Author | Source |
| --- | --- | --- |
| Mountain lake / Гірське озеро | Andrew Spencer | [Tranquil mountain lake](https://commons.wikimedia.org/wiki/File:Tranquil_mountain_lake_(Unsplash).jpg) — published under CC0 before Unsplash changed its license |
| Northern lights / Північне сяйво | CangemiY | [Aurora dancing in the Swedish Lapland sky](https://commons.wikimedia.org/wiki/File:Aurora_dancing_in_the_Swedish_Lapland_sky.jpg) |
| Lake Como / Озеро Комо | Stinglehammer | [Sunset on Lake Como](https://commons.wikimedia.org/wiki/File:Sunset_on_Lake_Como.jpg) |

[CC0 deed](https://creativecommons.org/publicdomain/zero/1.0/) · [Legal text](https://creativecommons.org/publicdomain/zero/1.0/legalcode.en).

The packaged copies are resized and center-cropped to 1920×1080. Gallery previews are 320×180. Original and packaged SHA-256 hashes and source URLs are recorded in [sources.json](sources.json). No photo download or stock-photo account is needed at runtime.

## Resource limits

Only the selected full-size image is decoded on a background worker, using RGB_565. The gallery decodes thumbnails, not every full photo. Blur runs on a downsampled image and is cached per wallpaper in app cache. Stale loads are discarded after rapid selection changes; there are no background wakeups or repeated network work. Existing background fades are retained.

## Device previews

Updated public screenshots will be captured after the release UI is finalized.



## Dynamic wallpaper mode

Appearance offers an optional switch for changing photos every 30 minutes. It cycles through available photo wallpapers, excluding the blue and dark solid choices. Manual selection starts a fresh interval. Turning the switch off retains the current photo.

The chosen photo, mode and last-change timestamp persist. A single Handler callback exists only while the launcher is resumed; on leaving it is removed. Returning after an overdue interval advances once, without catch-up bursts. Clock rollback resets the interval. There is no alarm, foreground service, network fetch or device wakeup. Existing background fades and downsampled blur remain in use.

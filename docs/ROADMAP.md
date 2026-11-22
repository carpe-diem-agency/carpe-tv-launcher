# Roadmap

## Current work order (agreed 2026-11-12)

The P0 block is **paused** at the owner's request because its system
integration requires more resources. Home, stock-launcher replacement,
return from other apps, restored focus and post-reboot behavior remain
unverified release criteria. Do not resume that block until the owner asks.

1. Finish P1: consistent UI and remote navigation, settings focus, motion and
   performance, loading/error states, long-use checks and projector review.
2. Finish the agreed P2 polish and release materials without presenting
   unfinished system controls as ready.
3. When the owner resumes P0: physical Home button, stock launcher rollback,
   app return, reboot and sleep scenarios on the Xiaomi stick, then another TV device.

The settings Back/Left navigation fix has passed an isolated test on the Xiaomi
stick in draft PR #12. The normal installed launcher was restored after that
test. Wallpaper-related page transitions still need visual optimization; a
plain wallpaper improved measured page timing, but it is not a product fix.

## Current baseline

- [x] Standalone Java/XML launcher, favorite apps and searchable app library.
- [x] Shared focus/animation system and opt-in Home integration.
- [x] One-device validation and private rollback checkpoint.
- [x] Isolated source repository, screenshots, build CI and release draft workflow.

## Before public distribution

- [ ] Final name, license, release signing and redistributable artwork.
- [ ] App/page controller extraction, automated device matrix and memory soak.
- [ ] First-run setup and final universal package identity.
- [ ] Public installation/recovery validation on multiple TV manufacturers.

## Update delivery

- [x] Updater design and private delivery/signing constraints documented in `UPDATES.md`.
- [x] Background download, APK validation/install UI and private settings checkpoint.
- [x] English/Ukrainian interface and Carpe TV app label.
- [ ] Production signing transition and permanent approved delivery channel.

## Separate future scope

Spaces, cameras, investments, social analytics, Quest and smart-home integrations
are excluded from this baseline. They require their own design/access/review work.

# Before switching visibility to public

- [x] Adopt Carpe TV as the working app/repository name; Luma TV has been replaced.
- [ ] Complete public branding review before general distribution.
- [x] Select an explicit code license with the project owner: Apache-2.0 for
      original source code, with third-party and artwork terms kept separate.
- [x] Keep private game artwork out of both debug and release resources in this public source copy.
- [x] Reject known private artwork in CI and draft-release APKs with `scripts/check_release_apk.py`;
      the guard also verifies all three bundled CC0 landscape photos. This checks the
      packaged media bytes, not Git history or independently modified copies.
- [x] Export this source as a fresh tree without the private repository history or old screenshots.
- [x] Publish that clean-history source to the separate public
      `carpe-diem-agency/carpe-tv-launcher` repository. The private personal
      `carpe-tv` repository remains separate.
- [ ] Capture and review new screenshots from the final release candidate.
- [x] Prepare clean Home and Settings previews plus a short on-device
      transition recording for the install page. These were captured from an
      isolated debug APK; replace and recheck against the final signed APK.
- [x] Prepare a bilingual TV/phone install page in `docs/index.html`, with a
      direct link to the intended stable APK asset and a fallback release link.
- [x] Prepare a gated GitHub Pages workflow that publishes only the install
      page, its reviewed media and sitemap in a public repository. Before a
      stable APK exists, the page shows a source preview without a download
      action or structured download metadata. A stable release switches the
      page to installation mode only after all four release assets are present
      and the downloaded APK and source archive match `SHA256SUMS`, while
      `update.json` matches the APK hash, size, package ID and release URL.
- [x] Enable GitHub Pages from Actions and verify the live Ukrainian source
      preview at `https://carpe-diem-agency.github.io/carpe-tv-launcher/`.
- [ ] Publish and test that install page only after the public stable release
      exists; verify the TV-browser download and phone transfer on real devices.
- [x] Bundle Apache-2.0, LGPL-3.0 and incorporated GPL-3.0 texts in the APK;
      also bundle Montserrat OFL, Shizuku MIT and the Bouncy Castle notice.
      Include a third-party component/versions notice. `scripts/check_release_apk.py`
      checks all six license files plus the agency and third-party notices (unsigned release APK checked
      2026-11-11). The current locally signed candidate also contains it.
- [x] Stage the exact upstream-tag SPAKE2 source with its native submodule as
      a separate draft-release asset, verify its SHA-256 and include it in the
      release checksums. This does not prove binary reproducibility or LGPL
      relinking compliance; see [source record](SPAKE2_SOURCE.md).
- [x] Rebuild that source bundle in a manual GitHub Actions job on 2026-11-11;
      [run #1](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42990630242)
      passed and uploaded an AAR. This establishes source buildability, not
      binary equivalence or a working signed-APK relinking path.
- [x] Build an unsigned Carpe TV APK with the source-built AAR on a clean runner;
      [run #2](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42991685259)
      passed. Signing and device installation were subsequently checked with
      disposable test certificates.
- [x] Verify native packaging in
      [run #3](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42992685963):
      all four SPAKE2 ABI libraries in the unsigned APK match the source-built
      AAR byte for byte. An isolated signed build with that AAR also installed
      and ran on MiTV-AFMU1; see [source record](SPAKE2_SOURCE.md).
- [x] Verify a test-only source modification in
      [run #4](https://github.com/carpe-diem-agency/carpe-tv-launcher/actions/runs/42999102098):
      its native marker survived into all four APK ABIs. The downloaded APK
      passed the release artifact check, was signed with a disposable test
      certificate, installed and navigated on MiTV-AFMU1, then removed.
      Production-signed replacement and the LGPL distribution review remain open.
- [ ] Complete distribution review of libadb-android and its LGPL-3.0
      `spake2-android` 2.2.1 transitive component, including native-library
      source and relinking obligations. Bundled license text alone is not clearance.
      The bundled component notice is available in Settings → About; this does
      not resolve the LGPL-3.0 section 4 release gate. The owner chose to keep
      the optional Shizuku wizard in the first public APK, so this gate cannot
      be deferred by omitting that dependency.
- [ ] Review agency branding, third-party font/tooling notices and app trademarks.
- [ ] Replace device-specific screenshots where appropriate; confirm visible app list.
- [x] Separate the public application ID (`agency.carpediem.tv`) from the personal build.
- [ ] Finalize release signing with an independently backed-up key. A dedicated
      candidate key and a second same-disk copy exist; a locally signed 0.2.7
      candidate with the return-focus fix passed signer and ordinary TV Bro
      installation checks. The independent
      backup and owner handoff are still required; see
      [device evidence](RELEASE_APK_DEVICE_TEST.md).
- [ ] Configure release environment secrets and reviewers; test the signed draft flow.
- [x] Embed and build-check the public GitHub update feed in the release variant;
      it becomes usable only after the repository and a stable release are public.
- [ ] Finish history scan and dependency/permission review. A dedicated
      [source-tree secret scan](SECRET_SCAN.md) found zero Gitleaks findings on
      2026-11-11; all 25 public-history commits at `63a460a` also
      scanned with zero findings. Repeat after final changes and scan the
      signed APK. The current
      APK [permission and component inventory](APK_SURFACE_AUDIT.md)
      and [runtime dependency inventory](DEPENDENCY_AUDIT.md) are initial evidence,
      not final clearance.
- [ ] Review the one-time LAN pairing page: it uses HTTP on a trusted local Wi-Fi,
      a random URL and a 15-minute session. Five failed pairing attempts now
      close the session and stalled reads time out after five seconds. An isolated
      on-device probe passed the five-failure and fresh-session behavior on
      MiTV-AFMU1; the full phone-to-TV flow and HTTP suitability still need review.
      Decide whether the HTTP flow is suitable
      for public use.
- [x] Check a fresh first run on the Xiaomi stick using an isolated app ID; see
      [first-run evidence](FIRST_RUN_TEST.md). This does not validate the Home role.
- [x] Check a temporarily signed release-variant APK with the public app ID on
      the Xiaomi stick: fresh first run, app launch, Settings→Power and simulated
      Home-return focus passed; see [device evidence](RELEASE_APK_DEVICE_TEST.md).
- [x] In a controlled ADB test, the signed candidate accepted the Android Home
      role and an injected Home keyevent returned from Settings to Carpe TV.
      The script restored the personal Home role and removed the candidate;
      this does not cover a physical remote or a reboot.
- [ ] Repeat installation, physical Home and rollback using the final
      production-signed APK from the actual GitHub release link, without ADB.
      Test direct TV-browser download and a phone-to-TV transfer path separately;
      document the exact extra app, if that device needs one.
- [x] Repeat ordinary TV Bro installation with a newly signed local APK after
      removing the Accessibility Home bridge. The earlier signed 0.2.7 APK
      was blocked by Play Protect; the revised APK, signed with the same
      certificate, installed through TV Bro and launched on MiTV-AFMU1. The
      latest signed candidate also includes the return-focus fix and passed
      the same installation path.
      See [test evidence](PLAY_PROTECT_TEST.md). This does not test the future
      GitHub release download path or physical Home. Never instruct users to
      bypass Play Protect.
- [ ] Check physical Home and rollback with the public APK on firmware that
      honors the standard Android Home role. On the tested Xiaomi firmware,
      removing the bridge may allow ordinary installation but can expose its
      OEM Home interception again; do not claim full Home support there yet.
- [x] Verify return focus in an isolated D-pad test on MiTV-AFMU1: launch an app
      with OK, bring the launcher back, and confirm that the same tile receives
      focus. The 2026-11-11 check used Netflix; a cold relaunch restored the
      previously opened TV Bro tile. See [navigation evidence](NAVIGATION_CHECK.md).
- [ ] Before broad distribution, check [Android developer verification](https://developer.android.com/developer-verification)
      for the target countries and register the production signing identity if
      required. Google's rollout began in select regions in 2026 and expands
      globally in 2027; the installation guide must reflect the actual policy
      at release time.
- [x] Remove library-added storage and phone-state permissions from the merged APK.
- [ ] Test Home choice and rollback on more than the current Xiaomi test device.
- [ ] Finish long-duration memory, accessibility and D-pad checks. The isolated
      [navigation probe and 279-second memory run](PERFORMANCE_CHECK.md) passed
      on one stick. A newer source-built release candidate measured 14 ms p95
      across 20 paced page cycles, while an unpaced stress run was markedly
      slower. Projected-screen visuals and other devices remain to be checked.
- [ ] Review this checklist, README, support scope and Releases before publication.

This checklist is preparation for a later public release, not a reason to stop
private development. No workflow changes repository visibility automatically.

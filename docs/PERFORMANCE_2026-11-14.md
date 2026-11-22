# Library performance checkpoint — 2026-11-14

Historical checkpoint: owner-visible main at this measurement was **0.2.12 (19)**.
For the current installed version and fresh checks, see [PUBLICATION](PUBLICATION.md).
Pool/layout measurements below describe the isolated candidate before deployment. P0/Home/services unchanged.
All build/cache/log/artifact data resides on SSD-backed APFS CarpeTVBuild.

## Method and limits

V2 uses the same actual25-app catalog and query protocol, both workers
drained, loaded Alpine wallpaper settled, per-state samples and20blocks.
Two before runs (18) and two pool-only after runs (19) passed. Compare
equivalent clear-query populations only; this is a bounded one-device
instrumented workload, not a physical-remote/soft-IME/production60fps claim.
Before run navigation p95 varied15.5/28.3ms; no general FPS inference.

V1 mixed changed-query cases with an undrained wallpaper worker; its timings
remain historical descriptive evidence in the project records, not the matched
baseline used here.

## Matched V2 clear-query results

| Metric (ms) | Before18 A | Before18 B | Pool-only19 A | Pool-only19 B | Pool+layout19 A | Pool+layout19 B |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Clear next-draw p50 | 197.979 | 191.023 | 43.576 | 74.387 | 38.888 | 56.826 |
| Clear next-draw p95 | 347.205 | 352.151 | 124.495 | 114.731 | 110.526 | 84.552 |
| Latest20 clear p50 | 164.010 | 163.317 | 41.835 | 63.019 | 36.678 | 51.311 |

Both after runs improve this defined clear-query latency distribution;
run-to-run variation remains visible. Do not extrapolate to every query,
all devices, end-to-end soft composition, or stable60fps. After-run final
PSS112003/107316KB, active viewcount197 stable; these endpoints do **not**
prove memory benefit, absence of leaks, or multi-hour stability.

## Functional and artifact evidence

Pool-only build **SUCCESS1m10s**. `librarypool`, `libraryfocus`,
`inactivelibrary`, `librarystate`, `catalogstate`, `feedback` (full Open/Close
fit), V2 performance and navigation soak **all PASS**; cleanup PASS.
LibraryTilePool is bounded32entries/8MiB; QA includes reuse/policy and
bitmap-not-recycled checks. Functional PASS does not close every UX gate.

SSD logs under `/Volumes/CarpeTVBuild/logs/`:

- `perf-v2-before-a-0211-20261114`, `perf-v2-before-b-0211-20261114` namespaces.
- `pool-after-a-0212-20261114`, `pool-after-b-0212-20261114` namespaces.

Pool-only APKs, V2 test source and variant metadata archived at
`/Volumes/CarpeTVBuild/performance-artifacts/pool-only-0212/`, including
`variant.json`. Preserve these bytes for comparison with further changes.

## Final pool+layout candidate

One-line Apps-row `setBaselineAligned(false)` candidate removes unused
weighted-row baseline premeasurement; Home rows untouched. Incremental
pool-layout runs A/B **both PASS**, same actual25-app V2 metadata, zero
callback drops, viewcount197. Run A also libraryfocus/inactivelibrary PASS;
run B performance PASS. Final PSS112500/108274KB; no leak/memory-benefit
claim. Logs namespaces `pool-layout-after-a-0212-20261114` and
`pool-layout-after-b-0212-20261114` under the SSD logs directory.

The combined candidate supports improved clear-query workload latency
against before18. Variance prevents precise causal percentage attribution
to the baseline-alignment flag alone; pool-only A/B remains separately
archived. Static source reasoning about unused weighted premeasurement and
functional correctness are separate from the measured combined effect.

Final main build `device-build-0.2.12-layout-19` **SUCCESS45s**, superseding
the earlier pool-only normal build41s. Owner19 **not installed yet**;
coordinator deploys next after final checks. A separate40-native-input
latency probe is being prepared; no results yet, V2 protocol unchanged.

Clicked-icon color motion remains pending owner clarification; no motion
edits included in these candidates. Owner19 deployment awaits candidate
selection/validation. Physical P0, phone live form and broader soak gates
remain open; no public release is authorized by this checkpoint.

### Main0.2.12(19) visible deployment / наступні докази

**Main0.2.12(19) встановлено й візуально перевірено**, status
`installed_visible_and_verified` у
`/Volumes/CarpeTVBuild/device-deploy/0.2.12-19/deployment.json`.
Preferences unchanged, Home/services preserved. Root оглянув
`owner-bug-qr-0.2.12.png` у цьому каталозі: Open/Close повністю вміщуються,
QR лишився192dp; hint і GitHub Bug action видимі. Це замінює попередній
pending19 deployment стан. Bug QR залишено foreground для phone check;
**phone PASS очікує відповіді власника**, не заявляється за screenshot.

Перший native-input run FAIL на precondition focus/IME mismatch без
достатніх stage details — product defect не доведено. QA fixture додав
початковий drain обох workers і stage diagnostics; нова test build
`library-input-diagnostic-0212` триває. **PF1 hardware-input latency values
ще немає**; V2 clear-query результати не підмінюють цей окремий вимір.

Clicked-icon motion question ще без відповіді; read-only actual owner
preference `animations=true`. Motion code не змінювався; ввімкнене
налаштування не є доказом бажаної clicked-color поведінки. Goal і решта
відкритих gates лишаються активними; P0/public release не виконувалися.

Уточнення після owner reply: clicked-icon motion стосується Apps library
icons; scoped implementation готується для0.2.13(20), без нових PASS.
Ці motion edits не включені в наведені V2 pool/layout виміри. Телефон
відкрив GitHub і запросив sign-in: destination підтверджено, diagnostic
form prefill ще unverified.

### Native-input diagnostic: метод вимірювання не підтверджено

Diagnostic run terminal **FAIL** на stage `warm1`: focus `apps_search`
(EditText), IME visible=true, Apps selected=true; expected query length2,
actual length1. Hidden-IME fixture precondition після native key невалідний.
Cleanup **PASS**. Це **не доведений product defect** і не причина міняти
продукт для задоволення fixture; фізичний input/input-to-result latency
не виміряно. Метод measurement потребує окремого коректного protocol,
PF1 latency gate відкритий; існуючі V2 timings не є цим виміром.

Apps-only motion candidate готується: alpha0.94, timings110/190ms;
вузька runtime-перевірка QA pending для0.2.13(20). Немає motion PASS
або deployment20 на підставі самого source опису.

# StartupDrawProbe scope

Source-only instrumentation probe, awaiting dispatch wiring, compilation and runtime validation by the root agent.

Runs only the disposable validation package on API 29+. Three new Activities in the already-running instrumentation process measure Home, first Apps/Settings activation and cached Apps/Settings returns. Welcome and last-opened-app preferences are temporarily controlled and restored after owned Activity workers stop. Disk caches remain intact.

Each result records milliseconds from the launch invocation or main-thread rail activation to the first actionable current-page draw and first actionable draw with incoming content alpha 1 / translation Y 0. Timestamps are captured inside OnDraw; posted callbacks only release the waiter and remove listeners. Qualifying visibility excludes hidden cached pages. Listener attachment at Activity-created occurs before initial traversal; pre-created observes the owned Activity and rejects unexpected saved-state restoration.

The 15-second waits are fixture availability timeouts, not performance budgets. Raw three-sample pairs are descriptive observations; do not infer percentiles, physical presentation, process-cold startup, complete artwork readiness or broad performance acceptance. Empty/error catalog startup is not independently exercised here. No Home intents, system settings changes, downloads, force-GC or production hooks.

Route activation is scheduled through main-thread programmatic requestFocus and
invalidate, not injected hardware input. OnDraw timestamps are taken after the
readiness reflection/tree scan, so that validation overhead is included. Settled
traversal is not GPU completion or physical display presentation. These are raw
warm-process/new-Activity observations, not a cold-process or input-latency claim.

## Separate cold-process follow-up (not implemented)

Current app has no reportFullyDrawn marker. Warm-process results here do not
close cold-process startup acceptance. A separate Macrobenchmark module would
need an isolated release-like non-debuggable/profileable target, explicit Main
Intent, declared compilation mode and bounded trace storage. Audit library shell
commands before adoption: no Home role or global device-setting modifications.
First displayed frame (TTID) differs from fully drawn/usable state (TTFD); define
an honest production ready condition before adding reportFullyDrawn. Never use
an early onCreate signal to improve a metric. SDK/dependency compatibility and
local free-space budget still require checks; no new dependency downloaded.

Official references: [metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics),
[overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
[versions](https://developer.android.com/jetpack/androidx/releases/benchmark).

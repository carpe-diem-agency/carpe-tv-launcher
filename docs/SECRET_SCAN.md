# Public source secret scan

On 2026-11-11 the current public-preview source tree was copied to a temporary scan directory, excluding Gradle and APK build outputs. [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) `dir` scanned that 3.6 MB copy with its default rules and full redaction. The official macOS arm64 archive SHA-256 was verified as `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`. Result: **0 findings**.

`scripts/check_repository.py` also passed. A targeted search found no previously used device IP, email address, serial number or MAC address in the public-preview source and docs. These checks do not inspect the private repository's history, prove the absence of every possible secret, or cover future commits. Run the scanner again against the final fresh Git history and the signed APK before publication; never publish the temporary scan report if it contains findings.

The clean-history source was subsequently published to the separate public
`carpe-diem-agency/carpe-tv-launcher` repository. On 2026-11-11, Gitleaks
8.30.1 also scanned **all 18 commits** reachable from local refs at commit
`8124f35` using `gitleaks git . --redact`: **0 findings**. The redacted JSON
report is kept outside the repository. This scan does not cover future
commits or the final signed APK, so both must be checked again before release.

On 2026-11-11, after the modified-library device check, Gitleaks 8.30.1
rescanned the public repository at `63a460a` with `gitleaks git . --redact`.
It covered all 25 reachable commits and found **0 leaks**. The redacted report
remains outside the repository. The production-signed release APK does not yet
exist, so its required artifact scan remains open.

## Local candidate source/history scan — 2026-11-15

Pinned Gitleaks8.30.1 was obtained from its official macOS-arm64 release;
archive SHA-256 matches the official checksum and the recorded pin above.
The current tracked+untracked/non-ignored worktree snapshot at HEAD1b791d9
contained274 files/10,985,223bytes, no skipped symlinks. `dir` completed exit0
with0findings. Gitleaks Git mode with `--log-opts=--all` scanned182reachable commits, exit0/0findings.
Default built-in rules were explicitly extended, without ignore/baseline or
inline-allow suppression; reports use100%redaction and stay private locally.

Evidence under the active internal runtime
`security/gitleaks-20261115-current/`: scope.json, dir/git-redacted JSON/logs.
Root read back both JSON finding arrays as empty. This covers that snapshot
and reachable Git history, not later edits, ignored runtime/key files, compiled
APK contents, encrypted/otherwise undetected secrets or final signed artifacts.
No publication performed; repeat relevant checks on final bytes.

## 2026-11-21 · 0.2.29 source checkpoint

Gitleaks 8.30.1 reported zero findings in the exact `1327c7f` source archive and in the commits reachable from it. Scan used full redaction, no baseline and ignored inline allow directives. Private scope and redacted reports are under `carpe-tv-runtime-local/security/gitleaks-20261121-1327c7f`. Scope covers that source and reachable history only.

## 2026-11-21 · 0.2.31 candidate

Gitleaks 8.30.1 returned exit 0 and zero findings for each of three inputs: the
extracted candidate source ZIP, Git commits reachable from `73d7aa5`, and the
extracted unsigned APK entries. Checksums matched for the APK, source ZIP and
local update fixture before scanning. Full redaction and
`--ignore-gitleaks-allow` were enabled. The private reports are under
`carpe-tv-runtime-local/security/gitleaks-20261121-073d7aa5`. The APK check
used the scanner's file rules without decompilation. A production-signed artifact
still requires verification against its final bytes.

## 2026-11-21 · 0.2.32 candidate with source-built SPAKE2

Gitleaks 8.30.1 returned exit 0 and zero findings for the extracted unsigned
APK, extracted source ZIP and Git history reachable from `1a1ef05`. Full
redaction and `--ignore-gitleaks-allow` were enabled. Private results are in
`carpe-tv-runtime-local/security/gitleaks-20261121-relinked-0232`.

## 2026-11-21 · 0.2.33 candidate with source-built SPAKE2

Gitleaks 8.30.1 returned exit 0 and zero findings for the extracted unsigned
APK, extracted source ZIP and Git history reachable from `fc73f1e`. Full
redaction and `--ignore-gitleaks-allow` were enabled. Private results are in
`carpe-tv-runtime-local/security/gitleaks-20261121-relinked-0233`.

## 2026-11-21 · 0.2.34 candidate with source-built SPAKE2

Gitleaks 8.30.1 returned exit 0 and zero findings for the extracted unsigned
APK, extracted source ZIP and Git history reachable from `a3f74fb`. Full
redaction and `--ignore-gitleaks-allow` were enabled. Private results are in
`carpe-tv-runtime-local/security/gitleaks-20261121-relinked-0234`.

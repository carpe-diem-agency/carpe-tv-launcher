# Public source secret scan

On 2026-11-11 the current public-preview source tree was copied to a temporary scan directory, excluding Gradle and APK build outputs. [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) `dir` scanned that 3.6 MB copy with its default rules and full redaction. The official macOS arm64 archive SHA-256 was verified as `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`. Result: **0 findings**.

`scripts/check_repository.py` also passed. A targeted search found no previously used device IP, email address, serial number or MAC address in the public-preview source and docs. These checks do not inspect the private repository's history, prove the absence of every possible secret, or cover future commits. Run the scanner again against the final fresh Git history and the signed APK before publication; never publish the temporary scan report if it contains findings.

The clean-history source was subsequently published to the separate public
`carpe-diem-agency/carpe-tv-launcher` repository. On 2026-11-11, Gitleaks
8.30.1 also scanned **all 18 commits** reachable from local refs at commit
`8124f35` using `gitleaks git . --redact`: **0 findings**. The redacted JSON
report is kept outside the repository. This scan does not cover future
commits or the final signed APK, so both must be checked again before release.

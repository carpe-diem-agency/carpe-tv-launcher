# Public source secret scan

On 2026-11-11 the current public-preview source tree was copied to a temporary scan directory, excluding Gradle and APK build outputs. [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1) `dir` scanned that 3.6 MB copy with its default rules and full redaction. The official macOS arm64 archive SHA-256 was verified as `b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5`. Result: **0 findings**.

`scripts/check_repository.py` also passed. A targeted search found no previously used device IP, email address, serial number or MAC address in the public-preview source and docs. These checks do not inspect the private repository's history, prove the absence of every possible secret, or cover future commits. Run the scanner again against the final fresh Git history and the signed APK before publication; never publish the temporary scan report if it contains findings.

The fresh public-preview tree was initialized as a **local-only** Git repository
on 2026-11-11. Its 182 staged files contain no APK, keystore, signing file or
local Android properties. Gitleaks 8.30.1 scanned the working tree (about
6.94 MB after ignore rules) with redacted output and reported zero findings.
No commit or remote push was made. Rescan the committed history and final
release artifact before publication.

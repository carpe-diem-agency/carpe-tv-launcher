# Security

This public source repository is in pre-release. It has no security support SLA
or guarantee that any APK is free from all threats. Download APKs only from the
official GitHub Releases page and compare their SHA-256 with the `SHA256SUMS`
file attached to the same release. A checksum verifies file integrity against
the published value; it does not independently certify the source code or
replace review of the app and its dependencies.

Report sensitive findings privately to the repository owner. Do not include
exploit details or secrets in a public issue or pull request.

Never commit keystores, API tokens, camera/account credentials or device dumps.
Network access and installation requests are restricted to the optional updater.
There are no personal integrations or shared repository credentials. Its
optional Accessibility service must remain explicit and narrowly scoped.

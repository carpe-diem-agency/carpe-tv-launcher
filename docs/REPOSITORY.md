# Repository operations

Public source repository: `carpe-diem-agency/carpe-tv-launcher`, default branch
`main`. This clean source tree is pushed to that GitHub remote. The older
`carpe-diem-agency/carpe-tv` repository remains private and must not be made
public because its history contains personal development work. Personal
workspaces and rollback APKs remain local. Public releases will store signed
APK and checksum assets outside Git objects, plus schema-1 update metadata.

- Actions have explicit permissions, pinned action commits, build timeouts and
  14-day artifact retention. Dependabot proposes monthly build/action updates.
- The release environment still needs signing secrets, reviewers and a `main`
  branch restriction before a signed draft is built.
- Signing secrets and required reviewers are not configured yet.
- Configure branch protection and required checks in the public repository.
- Prefer squash merge and automatic branch deletion.
- The Pages workflow publishes a source preview; no workflow makes a stable APK
  release or changes repository visibility automatically.

Use pull requests and review CI even while server-enforced protection is unavailable.
Before publishing an APK, complete `PUBLICATION.md`. Original project code is
licensed under Apache-2.0; third-party materials keep their own licenses.

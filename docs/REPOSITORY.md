# Repository operations

Planned public repository: `carpe-diem-agency/carpe-tv-launcher`, default branch
`main`. This clean source tree is local and has no GitHub remote yet. The older
`carpe-diem-agency/carpe-tv` repository remains private and must not be made
public because its history contains personal development work. Personal
workspaces and rollback APKs remain local. Public releases will store signed
APK and checksum assets outside Git objects, plus schema-1 update metadata.

- Actions have explicit permissions, pinned action commits, build timeouts and
  14-day artifact retention. Dependabot proposes monthly build/action updates.
- The release environment must be configured and restricted to branch `main`
  after creating the new repository.
- Signing secrets and required reviewers are not configured yet.
- Configure branch protection and required checks in the new public repository.
- Prefer squash merge and automatic branch deletion in the new repository.
- No workflow publishes a release or changes repository visibility automatically.

Use pull requests and review CI even while server-enforced protection is unavailable.
Before publication, complete `PUBLICATION.md`. Original project code is
licensed under Apache-2.0; third-party materials keep their own licenses.

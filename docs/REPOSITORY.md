# Repository operations

GitHub: `carpe-diem-agency/carpe-tv`, private, default branch `main`.
Only the standalone launcher baseline is tracked. The original personal workspace
and rollback APKs remain local. The initial draft and the private internal release store APK/checksum assets
outside Git objects. The updater release also includes schema-1 update metadata.

- Actions have explicit permissions, pinned action commits, build timeouts and
  14-day artifact retention. Dependabot proposes monthly build/action updates.
- Release environment exists and is restricted to branch `main`.
- Signing secrets and required reviewers are not configured yet.
- Main branch protection was requested, but GitHub rejected it because private
  repository protection requires a different account plan. No billing/visibility
  change was made. CI therefore runs but is not an enforced merge gate today.
- Squash merge is enabled; merge commits/rebase merge are disabled; merged branches
  are deleted automatically. Repository wiki/projects are disabled.
- No workflow publishes a release or changes repository visibility automatically.

Use pull requests and review CI even while server-enforced protection is unavailable.
Before publication, complete `PUBLICATION.md` and decide the public code license.

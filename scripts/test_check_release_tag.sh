#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf -- "$tmp"' EXIT
git init -q -b main "$tmp/remote"
git -C "$tmp/remote" -c user.name=Test -c user.email=test@example.invalid \
  -c commit.gpgsign=false commit -q --allow-empty -m first
old=$(git -C "$tmp/remote" rev-parse HEAD)
git -C "$tmp/remote" -c user.name=Test -c user.email=test@example.invalid \
  -c commit.gpgsign=false commit -q --allow-empty -m second
git clone -q "$tmp/remote" "$tmp/build"
cd "$tmp/build"
head=$(git rev-parse HEAD)

check() { bash "$script_dir/check_release_tag.sh" "$@"; }
reject() {
  local message=$1 output
  shift
  if output=$(check "$@" 2>&1) || [[ "$output" != *"$message"* ]]; then
    echo "Expected release tag check to reject: $message" >&2
    exit 1
  fi
}

# A related tag name must not count as an exact match.
git -C "$tmp/remote" -c tag.gpgSign=false tag v0.2.41-other "$old"
check origin v0.2.41 "$head"
git -C "$tmp/remote" -c tag.gpgSign=false tag v0.2.41 "$head"
check origin v0.2.41 "$head"
git -C "$tmp/remote" -c user.name=Test -c user.email=test@example.invalid \
  -c tag.gpgSign=false tag -a v0.2.42 "$head" -m current
check origin v0.2.42 "$head"
git -C "$tmp/remote" -c tag.gpgSign=false tag v0.2.43 "$old"
reject "Existing release tag points to a different commit" origin v0.2.43 "$head"
git -C "$tmp/remote" -c user.name=Test -c user.email=test@example.invalid \
  -c tag.gpgSign=false tag -a v0.2.44 "$old" -m old
reject "Existing release tag points to a different commit" origin v0.2.44 "$head"
reject "differs from the checked-out commit" origin v0.2.43 "$old"
reject "Unable to verify release tag on remote" "$tmp/missing" v0.2.41 "$head"
echo "Release tag checks passed."

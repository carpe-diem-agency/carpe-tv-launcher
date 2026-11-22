#!/usr/bin/env bash
set -euo pipefail

remote=$1
tag_ref="refs/tags/$2"
build_commit=$3

git check-ref-format "$tag_ref"
if [[ "$(git rev-parse --verify HEAD)" != "$build_commit" ]]; then
  echo "Release build commit differs from the checked-out commit" >&2
  exit 1
fi

if ! refs=$(git -c credential.helper= -c 'credential.helper=!gh auth git-credential' \
  ls-remote --tags "$remote" "$tag_ref" "${tag_ref}^{}"); then
  echo "Unable to verify release tag on remote: $tag_ref" >&2
  exit 1
fi
tag_commit=$(awk -v tag="$tag_ref" '
  $2 == tag { direct = $1 }
  $2 == tag "^{}" { peeled = $1 }
  END { print (peeled == "" ? direct : peeled) }
' <<< "$refs")
if [[ -n "$tag_commit" && "$tag_commit" != "$build_commit" ]]; then
  echo "Existing release tag points to a different commit: $tag_ref" >&2
  exit 1
fi

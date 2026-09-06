#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'HELP'
Usage: mise run release -- VERSION [--draft] [--dry-run] [--notes-file FILE]

Build and verify locally, then publish a GitHub release with its plugin JAR.
VERSION must be a stable version such as 2.0.0 (without v or -SNAPSHOT).
--draft       Upload a draft release for review instead of publishing it.
--dry-run     Build and verify only. Does not contact GitHub or create a tag.
--notes-file  Use a Markdown file instead of docs/releases/VERSION.md.
              If neither is present, generate notes through GitHub's API.
HELP
}
fail() { printf '%s\n' "$*" >&2; exit 1; }

if [[ $# -eq 0 || ${1:-} == --help || ${1:-} == -h ]]; then
  usage
  exit 0
fi
version=$1
shift
[[ $version =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || fail "Use a stable version such as 2.0.0."
draft=false
dry_run=false
notes_file="docs/releases/$version.md"
explicit_notes=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --draft) draft=true; shift ;;
    --dry-run) dry_run=true; shift ;;
    --notes-file)
      [[ $# -ge 2 && -n $2 ]] || fail "--notes-file needs a path."
      notes_file=$2; explicit_notes=true; shift 2 ;;
    *) fail "Unknown option: $1" ;;
  esac
done
[[ $explicit_notes == false || -s $notes_file ]] || fail "Release notes file is missing or empty: $notes_file"
repository=Staticpast/SlackMinecraft
tag="v$version"
jar="target/SlackMinecraft-$version.jar"

require_clean() {
  [[ -z $(git status --porcelain --untracked-files=normal) ]] || fail "Commit your changes before creating a release. Use --dry-run to test an uncommitted build."
}

if [[ $dry_run == false ]]; then
  command -v gh >/dev/null || fail "Install GitHub CLI (gh), then run gh auth login."
  [[ $(git branch --show-current) == dev ]] || fail "Create releases from the dev branch."
  require_clean
  case "$(git remote get-url origin)" in
    https://github.com/Staticpast/SlackMinecraft|https://github.com/Staticpast/SlackMinecraft.git|git@github.com:Staticpast/SlackMinecraft.git|ssh://git@github.com/Staticpast/SlackMinecraft.git) ;;
    *) fail "origin must point to $repository." ;;
  esac
  commit=$(git rev-parse HEAD)
  remote_commit=$(git ls-remote --exit-code origin refs/heads/dev | cut -f1)
  [[ $commit == "$remote_commit" ]] || fail "Local dev must match origin/dev. Commit and push, or pull remote changes first."
  gh auth status --hostname github.com >/dev/null
  remote_tag=$(git ls-remote origin "refs/tags/$tag" "refs/tags/$tag^{}")
  [[ -z $remote_tag ]] || fail "Tag $tag already exists. Existing releases are never overwritten."
fi

mvn --batch-mode --no-transfer-progress "-Drevision=$version" clean verify
[[ -s $jar ]] || fail "Build did not produce $jar."

if [[ $dry_run == true ]]; then
  printf 'Verified %s. No release or tag was created.\n' "$jar"
  exit 0
fi

# Refuse to publish if the source changed while the tests were running.
require_clean
[[ $(git rev-parse HEAD) == "$commit" ]] || fail "HEAD changed during the build. Run the release again."

# Freeze the tested asset before the upload so another build cannot replace it.
release_dir=$(mktemp -d "${TMPDIR:-/tmp}/slackminecraft-release.XXXXXX")
trap 'rm -rf "$release_dir"' EXIT
cp "$jar" "$release_dir/SlackMinecraft-$version.jar"
args=(release create "$tag" "$release_dir/SlackMinecraft-$version.jar"
  --repo "$repository" --target "$commit" --title "Build $version")
if [[ -s $notes_file ]]; then
  args+=(--notes-file "$notes_file")
else
  args+=(--generate-notes)
fi
if [[ $draft == true ]]; then args+=(--draft); fi
printf 'Creating %s on %s from %s.\n' "$tag" "$repository" "$commit"
gh "${args[@]}"

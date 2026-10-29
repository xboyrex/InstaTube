#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

log() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# The script lives in <pack>/scripts; support direct invocation from either the
# pack root or the scripts directory, plus an extra nested extraction folder.
if [[ -f "$SCRIPT_DIR/README.md" && -d "$SCRIPT_DIR/docs/assets/instatube/screenshots" ]]; then
  ROOT="$SCRIPT_DIR"
elif [[ -f "$SCRIPT_DIR/../README.md" && -d "$SCRIPT_DIR/../docs/assets/instatube/screenshots" ]]; then
  ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"
elif [[ -f "$SCRIPT_DIR/InstaTube-README-Pack/README.md" ]]; then
  ROOT="$SCRIPT_DIR/InstaTube-README-Pack"
elif [[ -f "$SCRIPT_DIR/../InstaTube-README-Pack/README.md" ]]; then
  ROOT="$SCRIPT_DIR/../InstaTube-README-Pack"
else
  fail "Could not find README.md and docs/assets/instatube/screenshots relative to $SCRIPT_DIR. Extract the ZIP completely."
fi
[[ -f "$ROOT/README.md" ]] || fail "README.md not found beside the launcher. Extract the ZIP fully and run START-TERMUX.sh again."
[[ -f "$ROOT/scripts/push-instatube-readme.sh" ]] || fail "Publishing script not found in $ROOT/scripts."
command -v git >/dev/null 2>&1 || fail "Git is missing. Install it with: pkg install git"

DEFAULT_URL="https://github.com/xboyrex/InstaTube.git"
URL="${INSTATUBE_REPO_URL:-}"
if [[ -z "$URL" ]]; then
  read -r -p "GitHub repository URL [$DEFAULT_URL]: " URL
  URL="${URL:-$DEFAULT_URL}"
fi

DEFAULT_BRANCH="main"
REF_LINE="$(git ls-remote --symref "$URL" HEAD 2>/dev/null | head -n 1 || true)"
if [[ "$REF_LINE" =~ refs/heads/(.+)$ ]]; then DEFAULT_BRANCH="${BASH_REMATCH[1]}"; fi
BRANCH="${INSTATUBE_BRANCH:-}"
if [[ -z "$BRANCH" ]]; then
  read -r -p "Target branch [$DEFAULT_BRANCH]: " BRANCH
  BRANCH="${BRANCH:-$DEFAULT_BRANCH}"
fi

STAMP="$(date '+%Y%m%d-%H%M%S')"
WORKDIR="${INSTATUBE_WORKDIR:-$HOME/InstaTube-readme-deploy-$STAMP}"
BACKUP_ROOT="$HOME/InstaTube-README-backups/$STAMP"
[[ ! -e "$WORKDIR" ]] || fail "Working directory already exists: $WORKDIR. Set INSTATUBE_WORKDIR to a different path or rerun later."
mkdir -p "$BACKUP_ROOT"

log "Cloning $URL (branch: $BRANCH)"
git clone --single-branch --branch "$BRANCH" "$URL" "$WORKDIR" || fail "Clone failed. Check the URL, branch, network, and repository access."

# Backup the README and package-owned asset directory before changing anything.
if [[ -f "$WORKDIR/README.md" ]]; then cp -p "$WORKDIR/README.md" "$BACKUP_ROOT/README.md"; fi
if [[ -d "$WORKDIR/docs/assets/instatube" ]]; then
  mkdir -p "$BACKUP_ROOT/docs/assets"
  cp -a "$WORKDIR/docs/assets/instatube" "$BACKUP_ROOT/docs/assets/instatube"
fi
if [[ -f "$WORKDIR/README-PUBLISH-TERMUX.md" ]]; then cp -p "$WORKDIR/README-PUBLISH-TERMUX.md" "$BACKUP_ROOT/README-PUBLISH-TERMUX.md"; fi
if [[ -f "$WORKDIR/push_instatube_readme_termux.sh" ]]; then cp -p "$WORKDIR/push_instatube_readme_termux.sh" "$BACKUP_ROOT/push_instatube_readme_termux.sh"; fi
log "Backup stored at $BACKUP_ROOT"

log "Copying README, 11 screenshots, animated preview, SVG icons, and platform icon exports"
cp "$ROOT/README.md" "$WORKDIR/README.md"
cp "$ROOT/README-PUBLISH-TERMUX.md" "$WORKDIR/README-PUBLISH-TERMUX.md"
cp "$ROOT/push_instatube_readme_termux.sh" "$WORKDIR/push_instatube_readme_termux.sh"
mkdir -p "$WORKDIR/docs/assets"
rm -rf "$WORKDIR/docs/assets/instatube"
cp -a "$ROOT/docs/assets/instatube" "$WORKDIR/docs/assets/instatube"

cd "$WORKDIR"
git add -f -- README.md README-PUBLISH-TERMUX.md push_instatube_readme_termux.sh docs/assets/instatube || fail "Git staging failed."
git diff --cached --check || fail "Whitespace/conflict-marker validation failed. Review the staged diff."

# Enforce a strict allow-list so unrelated repository files are never included.
while IFS= read -r path; do
  case "$path" in
    README.md|README-PUBLISH-TERMUX.md|push_instatube_readme_termux.sh|docs/assets/instatube/*) ;;
    *) fail "Unexpected staged path detected: $path. Nothing was pushed." ;;
  esac
done < <(git diff --cached --name-only)

log "Staged change summary"
git diff --cached --stat
printf '\nBackup: %s\nWorking copy: %s\n' "$BACKUP_ROOT" "$WORKDIR"
if git diff --cached --quiet; then
  log "No changes to publish; remote already matches this pack."
  exit 0
fi

printf '\nReview the staged summary above. Commit and push to %s (%s)? Type PUBLISH to continue: ' "$URL" "$BRANCH"
read -r ANSWER
[[ "$ANSWER" == "PUBLISH" ]] || { log "Cancelled. Files remain staged locally; no push was attempted."; exit 0; }

if ! git config user.name >/dev/null; then
  read -r -p "Git author name: " GIT_NAME
  [[ -n "$GIT_NAME" ]] || fail "Git author name cannot be blank."
  git config user.name "$GIT_NAME"
fi
if ! git config user.email >/dev/null; then
  read -r -p "Git author email: " GIT_EMAIL
  [[ -n "$GIT_EMAIL" ]] || fail "Git author email cannot be blank."
  git config user.email "$GIT_EMAIL"
fi

git commit -m "docs: redesign InstaTube README and media gallery" || fail "Commit failed. Working copy and backup are preserved at $WORKDIR and $BACKUP_ROOT."
if git push origin "$BRANCH"; then
  log "PUBLISH SUCCESSFUL"
  printf 'Repository: %s\nREADME: %s\n' "$URL" "${URL%.git}/blob/$BRANCH/README.md"
else
  printf '\nPush failed, but the commit is saved locally. Fix GitHub authentication or write access, then run:\n\n  git -C "%s" push origin "%s"\n\nBackup: %s\n' "$WORKDIR" "$BRANCH" "$BACKUP_ROOT" >&2
  exit 1
fi

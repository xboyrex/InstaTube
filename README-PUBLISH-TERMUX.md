# Publish InstaTube README + Assets from Termux

This pack targets the existing public repository `xboyrex/InstaTube` and preserves all unrelated app source files. It includes the README redesign, 11 screenshot captures, looping showcase GIF, SVG brand/feature icons, and generated platform icon sizes grouped under `docs/assets/instatube/brand/icon-kitchen/`.

## One-run setup

Download the ZIP, then run in Termux:

```bash
pkg update
pkg install git unzip -y
termux-setup-storage

ZIP="$(find ~/storage/downloads -maxdepth 1 -type f -name 'InstaTube-README-Pack*.zip' | head -n 1)"
if [ -z "$ZIP" ]; then echo 'ZIP not found in Downloads'; exit 1; fi
mkdir -p ~/InstaTube-README-Pack
unzip -o "$ZIP" -d ~/InstaTube-README-Pack
cd ~/InstaTube-README-Pack
bash START-TERMUX.sh
```

The ZIP's files are at its root. The launcher still detects the older nested-folder layout if one exists. Accept the default repository URL and detected default branch unless you intentionally want a different remote. Read the staged summary and type `PUBLISH` in uppercase to authorize the commit and push.

## What the script changes

- Backs up the current root `README.md`, previous `docs/assets/instatube` directory, and the prior publishing guide/script when present.
- Replaces only `README.md`, `README-PUBLISH-TERMUX.md`, `push_instatube_readme_termux.sh`, and `docs/assets/instatube/**`.
- Force-stages only those exact allow-listed paths, handling `.gitignore` rules that would otherwise suppress `docs/`.
- Verifies `git diff --cached --check`, displays staged paths and stats, and requires explicit confirmation before committing and pushing.
- Uses a fresh timestamped clone by default. It never runs `git reset --hard`, `git clean`, or force-push.

## Authentication

The target repository is `https://github.com/xboyrex/InstaTube.git`. Your GitHub identity must have write permission. Use your configured Git credential helper, a token supported by Git's authentication flow, or a configured SSH key. Never paste a token into the script or README. If push is rejected, the script prints the exact local recovery command; the commit and backups remain preserved.

## Contents

- `README.md` — redesigned GitHub landing page.
- `docs/assets/instatube/screenshots/` — all 11 supplied screenshots at original resolution.
- `docs/assets/instatube/brand/instatube-showcase.gif` — animated UI preview made from the supplied screenshots.
- `docs/assets/instatube/brand/banner.svg`, `logo.svg`, `icons/` — custom SVG graphics with no emoji-based UI elements.
- `docs/assets/instatube/brand/icon-kitchen/` — vector source and generated Android, web, and iOS-sized icon exports. These are a generated icon set shaped for platform use, not a claim of being exported by the IconKitchen website itself.

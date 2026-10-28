#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# ==============================================================================
# InstaTube 3.0 (v3.0.0) — Automated Termux GitHub Deploy Script
# Developer: ㅤᏞᴀᴢʏㅤᎷꫝʏᴀɴк まやんく (Mayank · @xboyrex) — https://github.com/xboyrex/InstaTube
# ==============================================================================

REPO_NAME="${1:-InstaTube}"
ZIP_PATH="/sdcard/Download/InstaTube.zip"
ALT_ZIP_PATH="$HOME/storage/downloads/InstaTube.zip"
WORK_DIR="$HOME/instatube_build_workspace"

echo "========================================================"
echo "  InstaTube v3.0.0 — Termux GitHub Auto-Deploy Script"
echo "  Developed by: ㅤᏞᴀᴢʏㅤᎷꫝʏᴀɴк まやんく (@xboyrex)"
echo "========================================================"

# 1. Ensure storage access & required packages
if [ ! -d "$HOME/storage" ] && [ ! -d "/sdcard/Download" ]; then
    echo "[*] Requesting Termux storage access..."
    termux-setup-storage
    sleep 2
fi

echo "[*] Checking required Termux packages (git, gh, unzip, openssh)..."
pkg install -y git gh unzip openssh >/dev/null 2>&1 || true

# 2. Locate InstaTube.zip in Downloads
FOUND_ZIP=""
if [ -f "$ZIP_PATH" ]; then
    FOUND_ZIP="$ZIP_PATH"
elif [ -f "$ALT_ZIP_PATH" ]; then
    FOUND_ZIP="$ALT_ZIP_PATH"
else
    LATEST_MATCH=$(ls -t /sdcard/Download/InstaTube*.zip "$HOME"/storage/downloads/InstaTube*.zip 2>/dev/null | head -n 1 || true)
    if [ -n "$LATEST_MATCH" ] && [ -f "$LATEST_MATCH" ]; then
        FOUND_ZIP="$LATEST_MATCH"
    fi
fi

if [ -z "$FOUND_ZIP" ]; then
    echo "[!] Error: InstaTube.zip not found in /sdcard/Download/"
    echo "    Please save InstaTube.zip in your phone's Download folder."
    exit 1
fi

echo "[+] Found archive: $FOUND_ZIP"

# 3. Verify GitHub CLI authentication
if ! gh auth status >/dev/null 2>&1; then
    echo "[*] Please authenticate with GitHub CLI (gh):"
    gh auth login
fi

GH_USER=$(gh api user -q ".login")
echo "[+] Authenticated as GitHub user: $GH_USER"

# 4. Extract InstaTube.zip into a clean workspace
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
echo "[*] Extracting $FOUND_ZIP..."
unzip -q "$FOUND_ZIP" -d "$WORK_DIR"

cd "$WORK_DIR"

# Patch repository owner if different from xboyrex
if [ -n "$GH_USER" ] && [ "$GH_USER" != "xboyrex" ]; then
    grep -rl "github.com/xboyrex/InstaTube" . 2>/dev/null | while read -r f; do
        sed -i "s|github.com/xboyrex/InstaTube|github.com/${GH_USER}/${REPO_NAME}|g" "$f" || true
    done
    grep -rl "repos/xboyrex/InstaTube" . 2>/dev/null | while read -r f; do
        sed -i "s|repos/xboyrex/InstaTube|repos/${GH_USER}/${REPO_NAME}|g" "$f" || true
    done
fi

chmod +x gradlew 2>/dev/null || true

# 5. Initialize Git repository & commit as Mayank (@xboyrex)
git init -b main
git config user.name "Mayank"
git config user.email "${GH_USER}@users.noreply.github.com"

git add -A
git commit -m "InstaTube v3.0.0: Floating Overlay Assistant, Live Transfer Notifications, IG+YT Fusion Logo, dev.xboyrex.instatube"

# 6. Create or update GitHub repository and push
if gh repo view "${GH_USER}/${REPO_NAME}" >/dev/null 2>&1; then
    echo "[*] Updating existing repository ${GH_USER}/${REPO_NAME} with v3.0.0..."
    git remote add origin "https://github.com/${GH_USER}/${REPO_NAME}.git" 2>/dev/null || \
        git remote set-url origin "https://github.com/${GH_USER}/${REPO_NAME}.git"
    git push -u origin main --force
else
    echo "[*] Creating new public repository ${GH_USER}/${REPO_NAME} and pushing..."
    gh repo create "$REPO_NAME" --public --source=. --remote=origin --push
fi

echo ""
echo "========================================================"
echo "  SUCCESS! InstaTube v3.0.0 pushed to:"
echo "  https://github.com/${GH_USER}/${REPO_NAME}"
echo ""
echo "  GitHub Actions is now building your signed APK:"
echo "  https://github.com/${GH_USER}/${REPO_NAME}/actions"
echo "========================================================"
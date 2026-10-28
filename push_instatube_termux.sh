#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=========================================="
echo "   InstaTube - Termux GitHub Auto-Push    "
echo "=========================================="

REPO_NAME="${1:-InstaTube}"
WORK_DIR="$HOME/instatube_repo"

# 1. Ensure required Termux packages are installed
for cmd in git unzip gh; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Installing $cmd..."
    pkg update -y && pkg install -y git unzip gh
    break
  fi
done

# 2. Locate InstaTube.zip in Downloads folder
if [ ! -d "$HOME/storage/downloads" ] && [ ! -d "/sdcard/Download" ]; then
  echo "Setting up Termux storage access..."
  termux-setup-storage
  sleep 2
fi

ZIP_FILE=$(ls -t "$HOME/storage/downloads"/InstaTube*.zip /sdcard/Download/InstaTube*.zip /storage/emulated/0/Download/InstaTube*.zip 2>/dev/null | head -n 1 || true)

if [ -z "$ZIP_FILE" ] || [ ! -f "$ZIP_FILE" ]; then
  echo "Error: Could not find InstaTube.zip in your Downloads folder."
  echo "Please make sure InstaTube.zip is saved in /sdcard/Download and run again."
  exit 1
fi

echo "Found archive: $ZIP_FILE"

# 3. Extract archive to clean workspace
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
unzip -q "$ZIP_FILE" -d "$WORK_DIR"
cd "$WORK_DIR"

# If zip extracted into a subfolder, move contents up
if [ ! -f "settings.gradle.kts" ]; then
  SUBDIR=$(find . -maxdepth 2 -name "settings.gradle.kts" | head -n 1 | xargs dirname)
  if [ -n "$SUBDIR" ] && [ "$SUBDIR" != "." ]; then
    shopt -s dotglob
    mv "$SUBDIR"/* .
    rmdir "$SUBDIR" 2>/dev/null || true
  fi
fi

chmod +x gradlew

# 4. Authenticate with GitHub CLI if needed
if ! gh auth status >/dev/null 2>&1; then
  echo "Please log in to your GitHub account:"
  gh auth login
fi

GH_USER=$(gh api user -q ".login")
echo "Authenticated as GitHub user: $GH_USER"

# 5. Update repository links to match the authenticated GitHub user
for file in \
  "app/src/main/java/com/tensei/ytxtube/Updates.kt" \
  "app/src/main/java/com/tensei/ytxtube/SettingsScreen.kt" \
  "app/src/main/java/com/tensei/ytxtube/InstaTubeScreen.kt" \
  "app/src/main/java/com/tensei/ytxtube/Account.kt" \
  "README.md" \
  "PRIVACY.md"; do
  if [ -f "$file" ]; then
    sed -i "s|github.com/Roshan/InstaTube|github.com/${GH_USER}/${REPO_NAME}|g" "$file"
    sed -i "s|api.github.com/repos/Roshan/InstaTube|api.github.com/repos/${GH_USER}/${REPO_NAME}|g" "$file"
    sed -i "s|https://github.com/Roshan\"|https://github.com/${GH_USER}\"|g" "$file"
  fi
done

# 6. Initialize Git repository and commit as Roshan
git config --global user.name "Roshan"
git config --global user.email "${GH_USER}@users.noreply.github.com"

rm -rf .git
git init -b main
git add -A
git commit -m "Initial release: InstaTube v1.4.0 (Instagram & YouTube Liquid Glass Client)"

# 7. Create new GitHub repository (or push if it already exists)
if gh repo view "${GH_USER}/${REPO_NAME}" >/dev/null 2>&1; then
  echo "Repository ${GH_USER}/${REPO_NAME} already exists. Pushing to main..."
  git remote add origin "https://github.com/${GH_USER}/${REPO_NAME}.git"
  git push -u origin main --force
else
  echo "Creating new public repository ${GH_USER}/${REPO_NAME} and pushing..."
  gh repo create "${REPO_NAME}" --public --source=. --remote=origin --push
fi

echo ""
echo "=========================================="
echo "Successfully pushed InstaTube to GitHub!"
echo "Repository : https://github.com/${GH_USER}/${REPO_NAME}"
echo "Actions APK: https://github.com/${GH_USER}/${REPO_NAME}/actions"
echo "Releases   : https://github.com/${GH_USER}/${REPO_NAME}/releases"
echo "=========================================="

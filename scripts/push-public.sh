#!/usr/bin/env bash
# 将当前工作树以「无私有历史」方式强制同步到 public remote（iiileo/nairo-android）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE="${PUBLIC_REMOTE:-public}"
BRANCH="${PUBLIC_BRANCH:-main}"
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

rsync -a \
  --exclude '.git/' \
  --exclude '.gradle/' \
  --exclude 'build/' \
  --exclude 'app/build/' \
  --exclude '.idea/' \
  --exclude 'local.properties' \
  --exclude '.kotlin/' \
  --exclude '.DS_Store' \
  --exclude '.ljapp/' \
  --exclude '*.jks' \
  --exclude '*.keystore' \
  --exclude 'keystore.properties' \
  --exclude 'release/*.apk' \
  --exclude '*.apk' \
  "$ROOT/" "$TMP/"

cd "$TMP"
git init -q -b "$BRANCH"
git add -A
MSG="${1:-Sync public tree from private development repo.}"
git -c user.name="${GIT_AUTHOR_NAME:-iiileo}" \
    -c user.email="${GIT_AUTHOR_EMAIL:-iiileo@users.noreply.github.com}" \
    commit -q -m "$MSG"

URL="$(git -C "$ROOT" remote get-url "$REMOTE")"
git remote add origin "$URL"
git push -f origin "HEAD:$BRANCH"
echo "Pushed clean tree to $URL ($BRANCH), commits=$(git rev-list --count HEAD)"

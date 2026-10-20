#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?set ANDROID_HOME}"
ADB="$ANDROID_HOME/platform-tools/adb"
OUT="${1:-captures/screen.png}"
mkdir -p "$(dirname "$OUT")"
"$ADB" exec-out screencap -p > "$OUT"
echo "saved $OUT"

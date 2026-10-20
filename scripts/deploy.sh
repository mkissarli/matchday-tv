#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?set ANDROID_HOME}"
ADB="$ANDROID_HOME/platform-tools/adb"

if [[ $# -gt 0 && -n "${1:-}" ]]; then
  "$ADB" connect "$1"
fi

./gradlew :app:installDebug
"$ADB" shell am force-stop dev.matchday.tv.debug
"$ADB" shell am start -n dev.matchday.tv.debug/dev.matchday.tv.MainActivity

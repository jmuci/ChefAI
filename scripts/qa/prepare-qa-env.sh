#!/usr/bin/env bash
# Prepares a safe environment for a QA session (see docs/qa/README.md).
#
# - checks the local backend is up and not stale (compares image build time to backend HEAD)
# - builds the debug APK PINNED to the local backend (-PchefaiApiBaseUrl= blank)
# - installs it on every *emulator* (physical devices are never touched)
# - launches the app and verifies from logcat that requests go to 10.0.2.2:8080, never production
#
# Usage: scripts/qa/prepare-qa-env.sh [--rebuild-backend] [--skip-build]
# Env:   CHEFAI_BACKEND_DIR  backend checkout (default: ~/Projects/ktor-chefai)
set -euo pipefail

BACKEND_DIR="${CHEFAI_BACKEND_DIR:-$HOME/Projects/ktor-chefai}"
APP_ID="com.tenmilelabs.chefai"
APK="app/build/outputs/apk/debug/app-debug.apk"
REBUILD_BACKEND=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --rebuild-backend) REBUILD_BACKEND=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    *) echo "unknown option: $arg" >&2; exit 2 ;;
  esac
done

fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { echo "ok:   $*"; }
warn() { echo "WARN: $*" >&2; }

cd "$(git rev-parse --show-toplevel)"

# --- Backend ---------------------------------------------------------------
[ -d "$BACKEND_DIR" ] || fail "backend checkout not found at $BACKEND_DIR (set CHEFAI_BACKEND_DIR)"
if [ "$REBUILD_BACKEND" = 1 ]; then
  echo "Rebuilding backend image from $BACKEND_DIR ..."
  (cd "$BACKEND_DIR" && git fetch -q && docker compose up -d --build)
fi

curl -fsS --max-time 5 http://localhost:8080/health >/dev/null \
  || fail "backend not answering on http://localhost:8080/health (docker compose up -d in $BACKEND_DIR)"
ok "backend answers /health"

image_epoch=$(date -j -u -f '%Y-%m-%d %H:%M:%S' "$(docker inspect -f '{{slice .Created 0 19}}' chefai_service | tr 'T' ' ')" +%s 2>/dev/null || echo 0)
head_epoch=$(git -C "$BACKEND_DIR" log -1 --format=%ct origin/main 2>/dev/null || git -C "$BACKEND_DIR" log -1 --format=%ct)
if [ "$image_epoch" -lt "$head_epoch" ]; then
  warn "backend image is OLDER than backend origin/main. Features added since (cook events, saved plans,"
  warn "recipe language, ...) won't work server-side and will show up as false bugs. Re-run with --rebuild-backend."
else
  ok "backend image is newer than backend origin/main"
fi

# --- Build, pinned to local -------------------------------------------------
# The marker records the hash of an APK this script built pinned, so --skip-build can't install and
# launch an arbitrary (possibly production-pointing) APK.
MARKER="app/build/.qa-pinned-apk.sha"
if [ "$SKIP_BUILD" = 0 ]; then
  echo "Building debug APK pinned to the local backend ..."
  ./gradlew :app:assembleDebug -PchefaiApiBaseUrl= -q
  shasum "$APK" > "$MARKER"
else
  [ -f "$APK" ] && [ -f "$MARKER" ] && [ "$(shasum "$APK")" = "$(cat "$MARKER")" ] \
    || fail "--skip-build only works for an APK this script built pinned to local; run without it"
fi
[ -f "$APK" ] || fail "no APK at $APK"

# --- Devices: emulators only -------------------------------------------------
emulators=()
while read -r serial; do emulators+=("$serial"); done < <(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}')
adb devices | awk '$2=="device" && $1 !~ /^emulator-/ {print $1}' | while read -r phys; do
  warn "ignoring physical device $phys (never used for QA)"
done
[ "${#emulators[@]}" -gt 0 ] || fail "no emulator running (boot one, e.g. emulator -avd <name>)"

for serial in "${emulators[@]}"; do
  echo "--- $serial"
  adb -s "$serial" install -r "$APK" >/dev/null
  adb -s "$serial" logcat -c
  adb -s "$serial" shell am force-stop "$APP_ID"
  adb -s "$serial" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

  hosts=""
  for _ in $(seq 1 20); do
    hosts=$(adb -s "$serial" logcat -d | grep -o 'REQUEST: https\?://[^/ ]*' | awk '{print $2}' | sort -u || true)
    [ -n "$hosts" ] && break
    sleep 1
  done
  if echo "$hosts" | grep -q 'pocket-chef.app'; then
    fail "$serial talks to PRODUCTION ($hosts). Do not test with this build."
  elif echo "$hosts" | grep -q '10.0.2.2:8080'; then
    ok "$serial requests go to http://10.0.2.2:8080 only"
  else
    warn "$serial: no REQUEST: line seen yet (hosts='${hosts:-none}'). Open the app, trigger a sync, then check:"
    warn "  adb -s $serial logcat -d | grep 'REQUEST:'   # must show 10.0.2.2:8080 and never pocket-chef.app"
  fi
done

echo
echo "Ready. Emulators: ${emulators[*]}"
echo "Next: follow docs/qa/README.md, assign one emulator per UI agent, one findings file per area."

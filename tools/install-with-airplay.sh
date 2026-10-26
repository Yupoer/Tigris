#!/usr/bin/env bash
# Explicit install on a chosen Android TV; does not run during build or source sync.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
: "${TV:?Set TV to the Android TV IP address and ADB port}"
FLAVOR=${FLAVOR:-standard}
[[ "$FLAVOR" == standard || "$FLAVOR" == guard ]] || { echo 'FLAVOR must be standard or guard' >&2; exit 2; }
SERIAL=$(TV="$TV" bash "$ROOT/tools/resolve-adb-tv.sh")
AIRPLAY_APK="$ROOT/dist/AirPlay-Direct.apk"
TIGRIS_APK="$ROOT/dist/Tigris-$FLAVOR.apk"
[[ -s "$AIRPLAY_APK" && -s "$TIGRIS_APK" ]] || { echo 'Run tools/build-with-airplay.sh first' >&2; exit 2; }
python3 - "$ROOT/dist" <<'PY'
import hashlib,sys
from pathlib import Path
folder=Path(sys.argv[1])
for line in (folder/'SHA256SUMS.txt').read_text().splitlines():
    expected,name=line.split(None,1)
    if hashlib.sha256((folder/name).read_bytes()).hexdigest()!=expected:
        raise SystemExit(f"APK checksum mismatch: {name}")
print('Bundle checksums verified')
PY
[[ "$(adb -s "$SERIAL" get-state)" == device ]] || { echo 'ADB connection is not authorized' >&2; exit 1; }
SDK=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$SDK" =~ ^[0-9]+$ ]] && (( SDK >= 28 )) || { echo 'Tigris requires Android 9 or newer' >&2; exit 1; }
# Keep an already installed receiver and its settings. An explicit replacement still
# refuses a signature mismatch; this script never uninstalls an existing package.
if adb -s "$SERIAL" shell pm path io.github.jqssun.airplay.cleanhome | grep -q '^package:'; then
  if [[ "${REPLACE_AIRPLAY:-0}" == 1 ]]; then
    adb -s "$SERIAL" install -r "$AIRPLAY_APK"
  else
    echo 'AirPlay Direct is already installed; keeping that copy (set REPLACE_AIRPLAY=1 to update).'
  fi
else
  adb -s "$SERIAL" install "$AIRPLAY_APK"
fi
TV="$SERIAL" APK="$TIGRIS_APK" bash "$ROOT/install.sh" install --flavor "$FLAVOR"
echo 'AirPlay Direct and Tigris installed. Verify Home, receiver name, and a real iPhone/Mac projection on the TV.'

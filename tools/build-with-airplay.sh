#!/usr/bin/env bash
# Build the Tigris launcher and its one AirPlay Direct companion app from this checkout.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
PREPARE=(python3 "$ROOT/airplay-direct/tools/prepare_native_libs.py")
if [[ -n "${AIRPLAY_OFFICIAL_APK:-}" ]]; then PREPARE+=(--apk "$AIRPLAY_OFFICIAL_APK"); fi
"${PREPARE[@]}"

bash "$ROOT/gradlew" -p "$ROOT" --no-daemon :app:testStandardDebugUnitTest :app:assembleStandardRelease :app:assembleGuardRelease
bash "$ROOT/gradlew" -p "$ROOT/airplay-direct" --no-daemon :app:testDebugUnitTest :app:assembleRelease

mkdir -p "$ROOT/dist"
cp "$ROOT/app/build/outputs/apk/standard/release/app-standard-release.apk" "$ROOT/dist/Tigris-standard.apk"
cp "$ROOT/app/build/outputs/apk/guard/release/app-guard-release.apk" "$ROOT/dist/Tigris-guard.apk"
cp "$ROOT/airplay-direct/app/build/outputs/apk/release/app-release.apk" "$ROOT/dist/AirPlay-Direct.apk"
python3 - "$ROOT/dist" <<'PY'
import hashlib,sys
from pathlib import Path
folder=Path(sys.argv[1])
with (folder/'SHA256SUMS.txt').open('w') as manifest:
    for name in ('Tigris-standard.apk','Tigris-guard.apk','AirPlay-Direct.apk'):
        path=folder/name
        manifest.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {name}\n")
print(f"Bundle ready in {folder}")
PY

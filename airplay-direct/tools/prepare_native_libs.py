#!/usr/bin/env python3
"""Restore the receiver's pinned native libraries from the official v0.0.36 APK.

The fork stores the Android/Kotlin and JNI bridge source. It does not commit
prebuilt binaries; this script verifies the official APK and each extracted lib.
"""
import argparse
import hashlib
import io
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/jniLibs/armeabi-v7a"
URL = "https://github.com/jqssun/android-airplay-server/releases/download/v0.0.36/app-release.apk"
APK_SHA256 = "b8c7c595ed93f91562feedce92c27800f08f9c84df424f6ac2045e82324cf2a4"
LIBRARIES = {
    "libairplay_native.so": "116138cf2cb275a7912b0e0bb43ba0ed4818e4491b0fe5ee9331d01dfa68ccdb",
    "libc++_shared.so": "37d4ffc8c166bd8e670971985bc888e89279c771fcbf90ec70a592ad9645c9d7",
    "libcrypto.so": "2da3f6912b0f9ee7c14c5d36e5e896b570a2fb3a07859e155282d92e1f975fac",
    "liboboe.so": "599f988dfbc3793d7c6bc2621c02f179eac3b091a9181f65ead1cf30bb570e11",
}

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def prepare(apk: bytes) -> None:
    if digest(apk) != APK_SHA256:
        raise SystemExit("Official AirPlay APK SHA-256 mismatch; native libraries were not changed")
    with zipfile.ZipFile(io.BytesIO(apk)) as archive:
        verified = {}
        for name, expected in LIBRARIES.items():
            data = archive.read(f"lib/armeabi-v7a/{name}")
            if digest(data) != expected:
                raise SystemExit(f"Native library checksum mismatch: {name}")
            verified[name] = data
    DEST.mkdir(parents=True, exist_ok=True)
    for name, data in verified.items():
        temporary = DEST / f"{name}.part"
        temporary.write_bytes(data)
        temporary.replace(DEST / name)
    print(f"Verified {len(verified)} AirPlay native libraries from upstream v0.0.36")

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, help="Already available official v0.0.36 APK; avoids a download")
    args = parser.parse_args()
    if all((DEST / name).is_file() and digest((DEST / name).read_bytes()) == expected
           for name, expected in LIBRARIES.items()):
        print("AirPlay native libraries already verified")
        return
    if args.apk:
        apk = args.apk.read_bytes()
    else:
        with urllib.request.urlopen(URL, timeout=60) as response:
            apk = response.read(32 * 1024 * 1024)
        if len(apk) > 30 * 1024 * 1024:
            raise SystemExit("Official APK exceeds expected size")
    prepare(apk)

if __name__ == "__main__":
    main()

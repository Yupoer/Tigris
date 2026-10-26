#!/usr/bin/env bash
# Reopen the legacy ADB port used by Tigris's on-TV power and memory actions.
# Android may close this port after reboot; Wireless debugging must be enabled first.
set -euo pipefail
ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
: "${TV:?Set TV to the Chromecast IPv4 address}"
IP=${TV%%:*}
SERIAL=$(TV="$TV" bash "$ROOT/tools/resolve-adb-tv.sh")
adb -s "$SERIAL" tcpip 5555
for attempt in 1 2 3 4 5; do
  adb connect "$IP:5555" >&2 || true
  if [[ "$(adb -s "$IP:5555" get-state 2>/dev/null || true)" == device ]]; then
    [[ "$(adb -s "$IP:5555" shell getprop service.adb.tcp.port | tr -d '\r')" == 5555 ]] || {
      echo 'ADB connected, but TCP 5555 was not confirmed on the TV' >&2
      exit 1
    }
    echo "$IP:5555"
    exit 0
  fi
  sleep 1
done
echo 'TCP 5555 did not open. Keep Wireless debugging enabled and retry.' >&2
exit 1

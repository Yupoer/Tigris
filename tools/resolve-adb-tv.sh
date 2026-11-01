#!/usr/bin/env bash
# Find the current wireless-debugging port after the computer has been paired once.
set -euo pipefail
: "${TV:?Set TV to the Android TV IPv4 address, optionally with :port}"
[[ "$TV" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(:[0-9]+)?$ ]] || {
  echo 'TV must be an IPv4 address, optionally with :port' >&2
  exit 2
}

candidates=()
if [[ "$TV" == *:* ]]; then
  candidates+=("$TV")
else
  while IFS= read -r endpoint; do
    [[ -n "$endpoint" ]] && candidates+=("$endpoint")
  done < <(adb mdns services | awk -v ip="$TV" '$0 ~ /_adb-tls-connect\._tcp/ { split($NF, address, ":"); if (address[1] == ip) print $NF }')
fi

for endpoint in "${candidates[@]}"; do
  adb connect "$endpoint" >&2 || continue
  if [[ "$(adb -s "$endpoint" get-state 2>/dev/null || true)" == device ]]; then
    printf '%s\n' "$endpoint"
    exit 0
  fi
done

echo "Could not connect to $TV. Keep Wireless debugging enabled; pair this computer again only if Android revoked pairing. Older TVs with a legacy port must pass TV=IP:PORT explicitly." >&2
exit 1

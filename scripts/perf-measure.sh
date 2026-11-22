#!/usr/bin/env bash
# Albumiv runtime sampling: cold start, jank, memory and thumbnail/scan logs.
#
# Usage:
#   ./scripts/perf-measure.sh [runs]        # default 5 cold-start runs
#   ADB="adb -H 127.0.0.1" ./scripts/perf-measure.sh   # WSL -> Windows adb server
#
# The jank step is interactive: scroll the album for 30 seconds, then press
# Enter so the script can read dumpsys gfxinfo. Numbers are written to stdout
# only; paste them into the guide's 10.1 table when a device is available.
set -euo pipefail

PKG="${PKG:-com.example.album}"
ACTIVITY="${ACTIVITY:-.MainActivity}"
RUNS="${1:-5}"
ADB="${ADB:-adb}"

if ! $ADB get-state >/dev/null 2>&1; then
    echo "no adb device. Start one, or set ADB=\"adb -H 127.0.0.1\" in WSL." >&2
    exit 1
fi

echo "== device =="
$ADB shell getprop ro.product.model | tr -d '\r'
$ADB shell getprop ro.build.version.release | tr -d '\r'

echo "== cold start (${RUNS}x) =="
times=()
for i in $(seq 1 "$RUNS"); do
    $ADB shell am force-stop "$PKG"
    sleep 1
    out=$($ADB shell am start -W -n "$PKG/$ACTIVITY" | tr -d '\r')
    total=$(echo "$out" | sed -n 's/^TotalTime: //p')
    echo "run $i: TotalTime=${total}ms"
    times+=("$total")
done
printf '%s\n' "${times[@]}" | sort -n | awk '
    { a[NR] = $1 }
    END {
        printf "median: %dms (min %d, max %d)\n", a[int((NR + 1) / 2)], a[1], a[NR]
    }'

echo "== jank =="
$ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null
echo "scroll the album for 30s, then press Enter"
read -r
$ADB shell dumpsys gfxinfo "$PKG" | grep -E 'Janky frames|50th|90th|95th|99th'

echo "== memory (browse) =="
$ADB shell dumpsys meminfo "$PKG" | grep -E 'TOTAL|Native Heap|Dalvik Heap|Graphics'

echo "== thumbnail/scan log =="
$ADB logcat -c
$ADB logcat -d -s AlbumThumbs | tail -20

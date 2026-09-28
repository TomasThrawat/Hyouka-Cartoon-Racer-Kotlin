#!/usr/bin/env bash
set -euo pipefail

PKG="com.tomasthrawat.hyoukacartoonracer.debug"
ACTIVITY="com.tomasthrawat.hyoukacartoonracer.MainActivity"
COMPONENT="$PKG/$ACTIVITY"

dump_diagnostics() {
  adb logcat -d -t 2000 > smoke-logcat.txt 2>&1 || true
  adb shell dumpsys activity activities > smoke-activity.txt 2>&1 || true
  adb shell dumpsys window windows > smoke-windows.txt 2>&1 || true
}
trap dump_diagnostics EXIT

adb wait-for-device
adb shell getprop sys.boot_completed | grep -Fx "1"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c

adb shell cmd package resolve-activity --brief "$PKG" | tail -1 | grep -Fx "$COMPONENT"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$COMPONENT"
sleep 4

adb shell pidof "$PKG"
adb shell dumpsys activity activities | grep -F "$COMPONENT"
adb exec-out screencap -p > smoke-menu.png

SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=$(printf '%s\n' "$SIZE" | cut -d x -f1)
H=$(printf '%s\n' "$SIZE" | cut -d x -f2)
if [ "$W" -lt "$H" ]; then
  T="$W"
  W="$H"
  H="$T"
fi

adb shell input tap $((W / 2)) $((H * 56 / 100))
sleep 1
adb exec-out screencap -p > smoke-howto.png

adb shell input tap $((W / 2)) $((H * 85 / 100))
sleep 1

adb shell input tap $((W / 2)) $((H * 43 / 100))
sleep 4
adb exec-out screencap -p > smoke-race-start.png

S=$((H * 20 / 100))
Y=$((H - 28 - S / 2))

X=$((28 + S / 2))
adb shell input tap "$X" "$Y"
sleep 0.5

X=$((44 + S + S / 2))
adb shell input tap "$X" "$Y"
sleep 0.5

X=$((W - 42 - (S * 118 / 100) / 2))
adb shell input tap "$X" "$Y"
sleep 1

adb exec-out screencap -p > smoke-race-controls.png
adb shell logcat -d -t 1500 > smoke-logcat.txt

grep -q 'MENU=HOW_TO' smoke-logcat.txt
grep -q 'HOW_TO=BACK' smoke-logcat.txt
grep -q 'MENU=PLAY' smoke-logcat.txt
grep -q 'STATE=COUNTDOWN' smoke-logcat.txt
grep -q 'STATE=RACING' smoke-logcat.txt
grep -q 'CONTROL=LEFT' smoke-logcat.txt
grep -q 'CONTROL=RIGHT' smoke-logcat.txt
grep -q 'CONTROL=BOOST' smoke-logcat.txt
! grep -E 'FATAL EXCEPTION|ANR in |AndroidRuntime' smoke-logcat.txt

adb shell am force-stop "$PKG"

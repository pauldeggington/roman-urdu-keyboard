#!/usr/bin/env bash
# Runs inside the emulator job: installs the preview APK, drives the keyboard and takes screenshots.
set -u
OUT=shots
mkdir -p $OUT
PKG=com.paul.urdukb
IME=$PKG/.ime.UrduImeService

shot() { sleep 1.2; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
hook() { adb shell am broadcast -a com.paul.urdukb.PREVIEW -p $PKG --es do "$1" > /dev/null; }
key() { # x y of a key from the last dump
  grep -E "KEY $1 " dump.txt | tail -1 | awk '{print $3, $4}'
}
dump() { adb logcat -c; hook dump; sleep 1.5; adb logcat -d -s PREVIEW:I | sed 's/.*PREVIEW: //' > dump.txt; }

LOG=$OUT/steps.txt
{
adb wait-for-device
adb shell getprop ro.build.version.sdk
ls -la app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/*.apk
sleep 8
adb shell pm list packages | grep urdukb
adb shell settings put secure show_ime_with_hard_keyboard 1
for i in 1 2 3 4 5 6 7 8 9 10; do
  adb shell ime enable $IME && adb shell ime set $IME && break
  sleep 3
done
adb shell settings get secure default_input_method
adb shell ime list -a | grep -i -A3 urdukb
adb shell cmd uimode night yes
adb shell am start -W -n $PKG/.PreviewActivity
} > $LOG 2>&1
sleep 6
adb logcat -d -s AndroidRuntime:E ActivityManager:W > $OUT/crash.txt 2>&1
shot 01_letters_dark
adb shell wm size > $OUT/wm_size.txt; adb shell wm density >> $OUT/wm_density.txt

dump; cp dump.txt $OUT/keys.txt

# preview bubble: hold a key without accents
read X Y < <(key x)
adb shell input swipe $X $Y $X $Y 1400 &
sleep 0.7; adb exec-out screencap -p > $OUT/02_preview_bubble.png; wait

# long-press popup on e
read X Y < <(key e)
adb shell input swipe $X $Y $X $Y 1800 &
sleep 1.1; adb exec-out screencap -p > $OUT/03_popup_e.png; wait

# long-press popup on period (two rows)
read X Y < <(key .)
adb shell input swipe $X $Y $X $Y 1800 &
sleep 1.1; adb exec-out screencap -p > $OUT/04_popup_period.png; wait

# type something
for k in h e l l o; do read X Y < <(key $k); adb shell input tap $X $Y; sleep 0.25; done
shot 05_typed_hello

hook symbols1; shot 06_symbols1
hook symbols2; shot 07_symbols2
hook emoji; sleep 1; shot 08_emoji
hook emoticons; sleep 1; shot 09_emoticons
hook letters; hook tools; shot 10_tools_row

adb shell cmd uimode night no
sleep 2
hook notools; hook letters; shot 11_letters_light
hook emoji; sleep 1; shot 12_emoji_light
hook letters
adb shell am start -n $PKG/.PreviewActivity --es type number
sleep 3; shot 13_number_field
adb logcat -d -s PREVIEW:I > $OUT/preview_log.txt
adb logcat -d -t 400 *:E > $OUT/errors.txt
echo done

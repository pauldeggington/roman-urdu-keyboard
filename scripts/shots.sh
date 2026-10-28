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
dump() { adb logcat -c; hook dump; sleep 1.5; adb logcat -d -s PREVIEW:I | sed 's/.*PREVIEW *: //' > dump.txt; }

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
read X Y < <(key h); echo "tap h at $X $Y" > $OUT/tapinfo.txt; adb shell input tap $X $Y >> $OUT/tapinfo.txt 2>&1; sleep 1
for k in h e l l o; do read X Y < <(key $k); adb shell input tap $X $Y; sleep 0.25; done
shot 05_typed_hello

hook symbols1; shot 06_symbols1
hook symbols2; shot 07_symbols2
hook emoji; sleep 1; shot 08_emoji
hook emoticons; sleep 1; shot 09_emoticons
hook letters; hook tools; shot 10_tools_row
hook notools
hook gif; sleep 2.5; shot 20_gif
hook stickers; sleep 2.5; shot 21_stickers
hook emoticons; sleep 1; shot 22_emoticons
hook search_emoji; sleep 1.5; shot 23_search_emoji
hook search_gif; sleep 2.5; shot 24_search_gif
# typing while the search is open must go into the search box, not into the app
dump; [ -s dump.txt ] || cp $OUT/keys.txt dump.txt
read X Y < <(key a); adb shell input tap $X $Y; sleep 0.4
read X Y < <(key b); adb shell input tap $X $Y; sleep 0.8
shot 25_search_typed
hook letters; sleep 1
hook clipboard_off; sleep 1.5; shot 26_clipboard_off
hook clipboard; sleep 1.5; shot 27_clipboard
hook letters; sleep 1
adb shell am start -n $PKG/.TranslateTextActivity --es android.intent.extra.PROCESS_TEXT "Kal milte hain, aap ka bohat shukriya" > /dev/null 2>&1; sleep 4; shot 28_translate_sheet_nomodel
adb shell am start -n $PKG/.TranslateTextActivity --es android.intent.extra.PROCESS_TEXT "Kal milte hain, aap ka bohat shukriya" --es preview_result "See you tomorrow, thank you very much" > /dev/null 2>&1; sleep 4; shot 29_translate_sheet_layout
adb shell am start -f 0x10008000 -n $PKG/.PreviewActivity > /dev/null 2>&1; sleep 3
hook letters; sleep 1

adb shell cmd uimode night no
sleep 2
hook notools; hook letters; shot 11_letters_light
hook emoji; sleep 1; shot 12_emoji_light
hook letters
adb shell am start -n $PKG/.PreviewActivity --es type number
sleep 3; shot 13_number_field

# ---------------------------------------------------------------- README material
# app screens: the main app first (setup marked done), then every setup step
adb shell cmd uimode night yes; sleep 2
adb shell am start -f 0x10008000 -n $PKG/.PreviewActivity --ez markdone true > /dev/null 2>&1; sleep 3
adb shell am start -f 0x10008000 -n $PKG/.MainActivity > /dev/null 2>&1; sleep 5
shot app_main_1
adb shell input swipe 540 1900 540 700 500; sleep 1; shot app_main_2
adb shell input swipe 540 1900 540 700 500; sleep 1; shot app_main_3
for S in WELCOME MODEL ENABLE PERSONAL PRACTICE DONE; do
  adb shell am start -f 0x10008000 -n $PKG/.SetupActivity --es step $S > /dev/null 2>&1
  sleep 4; shot app_setup_$S
  adb logcat -d -t 300 AndroidRuntime:E '*:S' >> $OUT/crash_app.txt 2>&1
  adb logcat -c
done
adb shell cmd uimode night no; sleep 2
adb shell am start -f 0x10008000 -n $PKG/.SetupActivity --es step WELCOME > /dev/null 2>&1; sleep 4; shot app_setup_WELCOME_light
adb shell cmd uimode night yes; sleep 2

# glide typing: slide across the letters with a pointer that stays down
adb shell am start -f 0x10008000 -n $PKG/.PreviewActivity > /dev/null 2>&1; sleep 4
hook letters; sleep 1
dump; [ -s dump.txt ] || cp $OUT/keys.txt dump.txt
cp dump.txt $OUT/keys_glide.txt
px=0; py=0
glide() { # glide <letters...> ; optional env STOP_BEFORE_UP=1 keeps the finger down
  local first=1
  for k in "$@"; do
    read X Y < <(key $k)
    if [ $first = 1 ]; then adb shell input motionevent DOWN $X $Y; first=0
    else
      mx=$(( (px + X) / 2 )); my=$(( (py + Y) / 2 ))
      adb shell input motionevent MOVE $mx $my
      adb shell input motionevent MOVE $X $Y
    fi
    px=$X; py=$Y
  done
  if [ -n "${STOP_BEFORE_UP:-}" ]; then return; fi
  adb shell input motionevent UP $px $py
}
STOP_BEFORE_UP=1 glide t h a n k s; sleep 0.4; shot 30_glide_trail
adb shell input motionevent UP $px $py; sleep 1.5; shot 31_glide_thanks
glide h e l l o; sleep 1.5; shot 32_glide_hello
glide w o r l d; sleep 1.5; shot 33_glide_world
adb shell input keyevent 67; sleep 1; shot 34_glide_backspace
adb logcat -d -s PREVIEW:I | grep GLIDE > $OUT/glide_log.txt 2>&1 || true

adb shell ime enable $IME; adb shell ime set $IME
# animated demo: one screenshot after every step, stitched into a GIF afterwards
mkdir -p $OUT/frames; N=0
frame() { N=$((N+1)); adb exec-out screencap -p > "$OUT/frames/$(printf %03d $N)${1:+_$1}.png"; }
tapkey() { read X Y < <(key "$1"); adb shell input tap $X $Y; sleep 0.35; frame; }
adb shell am start -f 0x10008000 -n $PKG/.PreviewActivity > /dev/null 2>&1; sleep 4
hook letters; sleep 1; frame; frame
dump; [ -s dump.txt ] || cp $OUT/keys.txt dump.txt; cp dump.txt $OUT/keys_demo.txt
for k in k y a space h a a l space h a i; do
  # the space bar sits in the gesture-navigation strip on the emulator, so it gets a key event instead of a tap
  if [ "$k" = space ]; then adb shell input keyevent 62; sleep 0.35; frame; else tapkey $k; fi
done
frame typed; frame
STOP_BEFORE_UP=1 glide t o m o r o w; sleep 0.3; frame; frame
adb shell input motionevent UP $px $py; sleep 0.7; frame; frame; frame
hook tools; sleep 1; frame; frame; hook notools; sleep 0.5
read X Y < <(key e); adb shell input swipe $X $Y $X $Y 1800 & sleep 1.1; frame; wait
sleep 0.5; adb shell input keyevent 67; sleep 0.4
hook symbols1; sleep 0.8; frame; hook symbols2; sleep 0.8; frame; hook letters; sleep 0.8; frame
hook emoji; sleep 1.2; frame emoji; frame
adb shell input swipe 540 1900 540 1250 400; sleep 0.8; frame
adb shell input swipe 540 1900 540 1250 400; sleep 0.8; frame
hook emoticons; sleep 1; frame; frame
hook letters; sleep 0.8
adb shell cmd uimode night no; sleep 3
hook letters; sleep 1; frame; frame
hook emoji; sleep 1.2; frame; frame
hook letters; sleep 0.8; frame
adb logcat -d -s PREVIEW:I > $OUT/preview_log.txt
adb shell dumpsys window windows > $OUT/windows.txt 2>&1
adb logcat -d -t 400 *:E > $OUT/errors.txt
adb logcat -d | grep -i -E 'StrictMode|FATAL EXCEPTION|ANR in' > $OUT/strictmode.txt 2>&1 || true
echo done

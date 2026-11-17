#!/system/bin/sh
# 676b: Hide navigation bar check on a handheld - the isolated QA app only (com.shelf.launcher.qa). Read-only for the device:
# no setting is written (no settings put, no wm, no root); the QA app's own "Hide navigation bar" is never changed (the OFF
# case uses the session-only test hook voltcap_test_navshown, Compose state only).
# usage (on the device): sh navbar676.sh TAG   -> /sdcard/pc/TAG/{nav.txt,*.png}
# Each step samples: the QA window's requested navigation-bar visibility, Android's NavigationBar0, the firmware's
# SecondaryHomeHandle0 (Retroid / AYN pill), the focused window, and a screenshot.
TAG=${1:-nav}
PKG=com.shelf.launcher.qa
ACT=$PKG/com.shelf.launcher.MainActivity
OUT=/sdcard/pc/$TAG
mkdir -p $OUT
R=$OUT/nav.txt
: > $R
echo "device=$(getprop ro.product.model) android=$(getprop ro.build.version.release) nav_mode=$(settings get secure navigation_mode) hide_nav_bar=$(settings get global hide_nav_bar)" >> $R
dumpsys package $PKG | grep -m1 versionName >> $R

vis() { dumpsys window windows | sed -n "/$1}/,/Window #/p" | grep -m1 isVisible | tr -d ' '; }
sample() {
  W=$(dumpsys window windows)
  req=$(echo "$W" | sed -n "/$PKG\/com.shelf.launcher.MainActivity}/,/Window #/p" | grep -m1 "Requested visib" | grep -o "ITYPE_NAVIGATION_BAR: [a-z]*")
  foc=$(dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{[^ ]* [^ ]* //; s/}.*//')
  h=$(vis SecondaryHomeHandle0); n=$(vis NavigationBar0)
  echo "$1 | req[$req] | NavigationBar0 $n | SecondaryHomeHandle0 $h | focus $foc" >> $R
  screencap -p $OUT/$1.png
}
# (676d, D334: the target confirmed first; the launch-block file makes the QA app's Back at its root stay in front.)
pm path $PKG > /dev/null 2>&1 || { echo "STOP: $PKG not installed" >> $R; exit 1; }
echo "target: serial=$(getprop ro.serialno) package=$PKG" >> $R
touch /sdcard/Android/data/$PKG/files/qa_nolaunch
# keys only while the QA app has the focus; anything else (the owner's Voltcap included): stop, nothing pressed
# (676n, Thor t676n_nav: the first mCurrentFocus line can be another display's window - DroidDeck's session on the Thor - while the
# QA app is the resumed activity that gets the keys; the guard reads Android's focused resumed activity, the package exactly.)
fgq() { r=$(dumpsys activity activities | grep -m1 "ResumedActivity: "); case "$r" in *" $PKG/"*) ;; *) echo "ABORT: resumed is $(echo "$r" | sed 's/.*{[^ ]* [^ ]* //; s/}.*//') - no key pressed" >> $R; exit 2;; esac; }
k() { fgq; input keyevent $1; sleep ${2:-1.2}; }
am force-stop $PKG; sleep 1
am start -n $ACT >/dev/null 2>&1; sleep 5; sample 01_startup
k BUTTON_A 2; sample 02_popup_open
k DPAD_DOWN 0.8; k DPAD_DOWN 0.8; sample 03_popup_moved
k BUTTON_B 1.5; sample 04_popup_closed
k BUTTON_R1 1.8; sample 05_r1_panel
k BUTTON_B 1.5
k BUTTON_START 1.8; sample 06_start_menu
k BUTTON_B 1.5
k BUTTON_R2 1.8; sample 07_category_r2
k BUTTON_L2 1.8; sample 08_category_l2
# background / resume: another app in front (Android's Settings, only opened - nothing changed), then the QA app again
am start -a android.settings.SETTINGS >/dev/null 2>&1; sleep 2.5; sample 09_other_app
am start -n $ACT >/dev/null 2>&1; sleep 3; sample 10_resumed
# a swipe up from the bottom edge: only with SWIPE=1. (16 Nov, RP Nova: where the window doesn't hide the bar - Retroid with its
# own hide_nav_bar on - that swipe is a real Home gesture and brought the owner's Voltcap, the home app, to the front. The QA
# script must never do that: opt in only on a device whose home app is not the owner's Voltcap.)
if [ "$SWIPE" = 1 ] && ! cmd package resolve-activity -c android.intent.category.HOME -a android.intent.action.MAIN 2>/dev/null | grep -q "packageName=com.shelf.launcher$"; then
  input swipe 640 955 640 700 250; sleep 0.4; sample 11_swipe_peek
  sleep 4; sample 12_after_peek
else echo "11_swipe | NOT TESTED (no SWIPE=1, or the owner's Voltcap is the home app)" >> $R; fi
# OFF (session only, the test hook): the window asks for the bar shown
am start -n $ACT --es voltcap_test_navshown on >/dev/null 2>&1; sleep 2.5; sample 13_setting_off
am start -n $ACT --es voltcap_test_navshown off >/dev/null 2>&1; sleep 2.5; sample 14_setting_on_again
echo finished >> $R

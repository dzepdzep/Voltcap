#!/system/bin/sh
# Update 513: "Who's playing?" left waiting — CPU % and frames per 6 s (QA app only, demo profiles, nothing saved).
#   sh picker513.sh TAG [LOOK]   windows: 2-8 s, 12-18 s, 22-28 s after it opens, then 1-7 s after one D-pad press.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; T=$1; LK=${2:-AURORA}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
k() { f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) ;; *) echo "$T ABORT focus $f"; exit 2;; esac; input keyevent $1; sleep ${2:-0.6}; }
win() { dumpsys gfxinfo $P reset >/dev/null; C=$(top -b -n 2 -d 6 -p $(pidof $P) | grep " $P" | tail -1 | awk '{print $9}')
  echo "$T $LK $1 cpu=${C}% frames6s=$(dumpsys gfxinfo $P | grep -m1 'Total frames rendered' | awk '{print $4}') screen=$(cut -d' ' -f1 $F/voltcap_qa_screen)"; }
A --es voltcap_test_pickerlook $LK --es voltcap_test_profiles 3; sleep 2
win open+2s; sleep 4; win open+12s; sleep 4; win open+22s
k 22 1; win key+1s
k 97 1.5; A --es voltcap_test_profiles off --es voltcap_test_pickerlook off

# Update 489c (D287) — proves the QA launch block is fail-safe on a QA build. Nothing can start: the one Play press happens
# only after qa_guard confirmed "blocked", and the run checks RetroArch's history (read-only md5) and the foreground app.
# sh d287v.sh [GAME]  -> /sdcard/pc/d287/result.txt. Run detached with its log in /data/local/tmp (`input` fails when its
# output is a file on /sdcard). After a fresh install "Who's playing?" opens first: close it with B (keeps the profile).
input keyevent KEYCODE_WAKEUP; sleep 1
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; OUT=/sdcard/pc/d287; mkdir -p $OUT; R=$OUT/result.txt; : > $R
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
. $(dirname $0)/qaguard.sh
st() { rm -f $F/qa_launch_state; echo 1 > $F/qa_launch_check; i=0; while [ $i -lt 15 ]; do [ -s $F/qa_launch_state ] && break; sleep 0.2; i=$((i+1)); done; cat $F/qa_launch_state 2>/dev/null; }
H=$(ls /sdcard/RetroArch/playlists/builtin/content_history.lpl /storage/emulated/0/RetroArch/playlists/builtin/content_history.lpl 2>/dev/null | head -1)
h0=$(md5sum "$H" 2>/dev/null | cut -c1-12)
qa_guard && echo "1 guard: blocked PASS" >> $R || { echo "1 guard: FAIL - stop" >> $R; exit 1; }
# 2: what broke 489 — a script switching the block off and deleting its file. Must stay blocked.
A --ez voltcap_test_nolaunch false; rm -f $F/qa_nolaunch; sleep 1
s=$(st); [ "$s" = blocked ] && echo "2 nolaunch false + file removed: $s PASS" >> $R || echo "2 nolaunch false + file removed: $s FAIL" >> $R
# 3: only qa_allow_launch unblocks (written and removed at once, nothing is pressed meanwhile)
touch $F/qa_allow_launch; s3=$(st); rm -f $F/qa_allow_launch; s4=$(st)
[ "$s3" = ALLOWED ] && [ "$s4" = blocked ] && echo "3 allow file: $s3 -> removed: $s4 PASS" >> $R || echo "3 allow file: $s3 -> removed: $s4 FAIL" >> $R
# 4: one real Play press, only behind the guard; the log must say blocked and Voltcap must stay in front
if qa_guard 2>> $R; then
  logcat -c; A --ez voltcap_test_preview true; [ -n "$1" ] && { A --es voltcap_test_game "$1"; sleep 2.5; }
  input keyevent KEYCODE_BUTTON_A; sleep 2.5; input keyevent KEYCODE_BUTTON_A; sleep 3
  b=$(logcat -d -s VoltcapQA | grep -c "LAUNCH blocked"); a=$(logcat -d -s VoltcapQA | grep -c "LAUNCH asked")
  fg=$(dumpsys window | grep -m1 mCurrentFocus | grep -c $P)
  input keyevent KEYCODE_BUTTON_B; sleep 1; input keyevent KEYCODE_BUTTON_B; sleep 1
  [ $b -gt 0 ] && [ $a -eq 0 ] && [ $fg -eq 1 ] && echo "4 Play pressed: blocked=$b asked=$a voltcap_front=$fg PASS" >> $R || echo "4 Play pressed: blocked=$b asked=$a voltcap_front=$fg CHECK" >> $R
else echo "4 skipped (guard)" >> $R; fi
h1=$(md5sum "$H" 2>/dev/null | cut -c1-12)
[ "$h0" = "$h1" ] && echo "5 RetroArch history unchanged ($h0) PASS" >> $R || echo "5 RetroArch history CHANGED $h0 -> $h1 FAIL" >> $R
touch $F/qa_nolaunch
cat $R

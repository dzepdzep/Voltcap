#!/system/bin/sh
# 682j (owner 18 Nov: "test intentional A presses immediately after page transitions"; L1 / R1 then A must never start the
# game). LEVEL 1 - the isolated QA app only (com.shelf.launcher.qa), its stand-in library, launches blocked (the QA app never
# starts a game: every Play press logs "LAUNCH blocked"). Per layout: open the game window (A on the home), then quick L1 / R1
# + A sequences at 0 / 60 / 150 ms, R1 R1 + A, a storm + A, R1 B A; the count of "LAUNCH" lines must be 0; then the window is
# closed, opened again (it opens on Play) and one deliberate A must give exactly 1.
# 682j-c (owner 18 Nov 14:54, D359): every key goes through winguard.sh - R1 / L1 / A only while the QA app's expected game
# window has the focus (checked before EACH key), the home's A only while the QA app's home window has it and shows HOME;
# the picker, Android quick settings / panels, the home or any other window where a game window is expected = ABORT, nothing
# more pressed, the QA app's test state put back. Nothing is retried blindly.
#   sh nav682j.sh TAG "CLASSIC TILES CRATE TV"      -> /sdcard/pc/TAG/{log.txt,LAYOUT_*.png,LAYOUT_*_qa.txt}
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=${2:-"CLASSIC TILES CRATE TV"}
O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt; : > $L; WG_LOG=$L
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
putback() { A --es voltcap_test_snapshot restore; sleep 1; A --ez voltcap_test_preview false; sleep 1
  sh vqa.sh hooks off > /dev/null; am force-stop $P; log "QA app test state put back, QA app stopped"; }
WG_ONABORT=putback
. ./winguard.sh
launches() { logcat -d -s VoltcapQA | grep -c "LAUNCH "; }
# close the confirmed game window: one B at a time, each only while that window has the focus, until the home is back
closewin() { for i in 1 2 3; do pg=$(wg_popup); [ "$pg" = null ] && return 0
    wg_key_win B; sleep 1.4; h=$(wg_hash "$(wg_focus)"); [ "$h" = "$WG_WIN" ] || break; done
  wg_home; }
dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep"; echo DONE >> $L; exit 3; }
touch $F/qa_nolaunch
sh vqa.sh hooks on > /dev/null; sleep 2            # (QaClip dumps and the screen mark need the test hooks)
A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 6
A --es voltcap_test_snapshot save; sleep 1
A --ez voltcap_test_preview true; sleep 1
wg_pass_picker
fails=0
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  A --es voltcap_test_category ALL; sleep 2
  wg_home; logcat -c
  wg_key_home A; sleep 2.5; wg_window; g=$WG_GAME; log "$lay window open: $g"
  screencap -p $O/${lay}_0open.png
  for s in "R1 A" "R1 W60 A" "R1 W150 A"; do wg_window "$g"; wg_seq_win "$s"; sleep 1.2
    [ "$s" = "R1 A" ] && screencap -p $O/${lay}_1r1a.png; wg_window "$g"; wg_key_win L1; sleep 0.8; done
  wg_window "$g"; wg_seq_win "R1 R1 A"; sleep 1.2; screencap -p $O/${lay}_2r1r1a.png
  wg_window "$g"; wg_key_win B; sleep 0.8
  wg_window "$g"; wg_seq_win "R1 L1 R1 L1 R1 L1 R1 A"; sleep 1.2
  wg_window "$g"; wg_key_win B; sleep 0.8
  wg_window "$g"; wg_seq_win "R1 W300 B A"; sleep 1.2; screencap -p $O/${lay}_3bA.png
  n=$(launches); log "$lay stray presses: LAUNCH lines $n (want 0)"; [ "$n" = 0 ] || fails=$((fails + 1))
  wg_window "$g"; closewin
  # deliberate: the window opens on Play - one A
  wg_key_home A; sleep 2.5; wg_window "$g"; screencap -p $O/${lay}_4open.png
  wg_key_win A; sleep 1.5
  m=$(launches); log "$lay deliberate A on Play: LAUNCH lines $m (want 1)"; [ "$m" = 1 ] || fails=$((fails + 1))
  logcat -d -s VoltcapQA | grep -E "LAUNCH|winkey" > $O/${lay}_qa.txt
  if [ "$(wg_popup)" = null ]; then wg_home; else wg_window "$g"; closewin; fi
done
putback
log "RESULT fails=$fails"; echo DONE >> $L

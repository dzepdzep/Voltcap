#!/system/bin/sh
# 682j (owner 18 Nov: "test intentional A presses immediately after page transitions"; L1 / R1 then A must never start the
# game). LEVEL 1 - the isolated QA app only (com.shelf.launcher.qa), its stand-in library, launches blocked (the QA app never
# starts a game: every Play press logs "LAUNCH blocked"). Per layout: open the game window (A on the home), then quick L1 / R1
# + A sequences at 0 / 60 / 150 ms, B, a storm; every key only while the QA app has the focus (else STOP, nothing pressed).
# The count of "LAUNCH" lines must be 0 after the stray presses, then exactly 1 after one deliberate A on Play (the window
# opens on Play). Screens + QaClip dumps after each step for the record.
#   sh nav682j.sh TAG "CLASSIC TILES CRATE TV"      -> /sdcard/pc/TAG/{log.txt,LAYOUT_*.png,LAYOUT_*_qa.txt}
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=${2:-"CLASSIC TILES CRATE TV"}
O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt; : > $L
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
fgk() { dumpsys window | grep mCurrentFocus | tail -1 | grep -q "$P/" || { log "STOP the QA app lost the focus - nothing pressed"; echo DONE >> $L; exit 3; }; }
kc() { case $1 in R1) echo BUTTON_R1;; L1) echo BUTTON_L1;; B) echo BUTTON_B;; A) echo BUTTON_A;; L) echo DPAD_LEFT;; R) echo DPAD_RIGHT;;
  U) echo DPAD_UP;; D) echo DPAD_DOWN;; esac; }
seq() { for k in $1; do case $k in W*) sleep $(awk "BEGIN{print ${k#W}/1000}");; *) fgk; input keyevent KEYCODE_$(kc $k);; esac; done; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
# is a game window open? (a QaClip dump's popupGame; no dump = unknown = treated as open, so B is pressed at most 3 times)
winopen() { rm -f $F/qa_clip/${TAG}_p.json; echo "${TAG}_p" > $F/qa_clip_req; chmod 666 $F/qa_clip_req 2>/dev/null; i=0
  while ! dr $F/qa_clip/${TAG}_p.json && [ $i -lt 30 ]; do sleep 0.2; i=$((i + 1)); done
  dr $F/qa_clip/${TAG}_p.json || return 0; ! grep -q '"popupGame": *null' $F/qa_clip/${TAG}_p.json; }
closewin() { for i in 1 2 3; do winopen || return 0; seq "B"; sleep 1.4; done; }
# 682j-b (D359): R1 on the HOME opens the quick-settings panel, whose A switches system things (it opened Android's Wi-Fi
# panel on both handhelds when the first A had not opened a window). Every page key is pressed only while a game window is
# confirmed open (QaClip popupGame); the "Who's playing?" picker is passed first. No window -> that layout is skipped.
picker() { grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null; }
openwin() { for t in 1 2 3; do picker && { seq "B"; sleep 2; }; done
  winopen && popknown && return 0
  seq "A"; sleep 2.5; winopen && popknown; }
# (winopen treats "no dump" as open, to never press B too often; here a window must be seen in the dump)
popknown() { dr $F/qa_clip/${TAG}_p.json && ! grep -q '"popupGame": *null' $F/qa_clip/${TAG}_p.json; }
# a page-key sequence only while the window is still open
pseq() { winopen && popknown || { log "$lay: no game window open - page keys NOT pressed"; return 1; }; seq "$1"; }
launches() { logcat -d -s VoltcapQA | grep -c "LAUNCH "; }
dumpsys power | grep -q "mWakefulness=Awake" || { log "STOP asleep - not woken"; echo DONE >> $L; exit 3; }
touch $F/qa_nolaunch
sh vqa.sh hooks on > /dev/null; sleep 2     # (QaClip dumps and the picker mark need the test hooks)
A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 6
A --es voltcap_test_snapshot save; sleep 1
A --ez voltcap_test_preview true; sleep 1
fails=0
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  A --es voltcap_test_category ALL; sleep 2
  fgk; logcat -c
  openwin || { log "$lay SKIP: the game window did not open - nothing else pressed"; fails=$((fails + 1)); closewin; continue; }
  screencap -p $O/${lay}_0open.png
  # stray: R1 then A at once, 60 ms, 150 ms; R1 R1 + A; an L1 / R1 storm then A; R1, B back then A at once.
  pseq "R1 A" || continue;        sleep 1.2; screencap -p $O/${lay}_1r1a.png; pseq "L1" || continue; sleep 0.8
  pseq "R1 W60 A" || continue;    sleep 1.2; pseq "L1" || continue; sleep 0.8
  pseq "R1 W150 A" || continue;   sleep 1.2; pseq "L1" || continue; sleep 0.8
  pseq "R1 R1 A" || continue;     sleep 1.2; screencap -p $O/${lay}_2r1r1a.png; pseq "B" || continue; sleep 0.8
  pseq "R1 L1 R1 L1 R1 L1 R1 A" || continue; sleep 1.2; pseq "B" || continue; sleep 0.8
  pseq "R1 W300 B A" || continue; sleep 1.2; screencap -p $O/${lay}_3bA.png
  n=$(launches); log "$lay stray presses: LAUNCH lines $n (want 0)"
  [ "$n" = 0 ] || fails=$((fails + 1))
  # deliberate: back to the window's first page on Play - close it, open again (it opens on Play), A once.
  closewin; winopen && { log "$lay window would not close - deliberate step skipped"; fails=$((fails + 1)); continue; }
  openwin || { log "$lay: the window did not open again - deliberate step NOT TESTED"; fails=$((fails + 1)); continue; }
  screencap -p $O/${lay}_4open.png; pseq "A" || continue; sleep 1.5
  m=$(launches); log "$lay deliberate A on Play: LAUNCH lines $m (want 1)"
  [ "$m" = 1 ] || fails=$((fails + 1))
  logcat -d -s VoltcapQA | grep -E "LAUNCH|winkey" > $O/${lay}_qa.txt
  closewin
done
A --es voltcap_test_snapshot restore; sleep 1; A --ez voltcap_test_preview false; sleep 1
sh vqa.sh hooks off > /dev/null; am force-stop $P
log "RESULT fails=$fails"; echo DONE >> $L

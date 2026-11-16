#!/system/bin/sh
# 529k - the shelf family's own pages (Game shelf, Case stacks, Console stack, Coverflow details) on the handheld. Isolated QA
# app ONLY (com.shelf.launcher.qa), fixture library + achievements, launches blocked, preview on (nothing saved); the owner's QA
# snapshot is kept first and put back by `restore`. Never wakes / unlocks: keys only while the QA app is in front and awake.
#   sh shelfpages529.sh run TAG [SCALE] ["LAYOUTS"]   per layout: A opens; R1 R1 R1 walks Details / Achievements / Options
#                                                     (recorded, a shot + layout dump per page); L1 x3 back; B closes; rapid
#                                                     B A x3 (no launch); then Motion Off: the same R1 walk recorded.
#   sh shelfpages529.sh restore TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
SC=${3:-off}; LAYS=${4:-"SHELF STACKS CONSOLES COVERDETAILS"}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
k() { gate; input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
# (675b: the fixed sleep is the minimum; then up to 8 s more for the app's own dump - see popclip.sh dw.)
# (675h: as popclip.sh 675d - wait until the dump READS whole (last byte the closing brace), not only for a size; and every
# grep of a dump goes through jl, one node per line, so one long line can't hide a control from the device's regex.)
# (675o: and as plain ASCII - popclip.sh's jl: the device's grep missed controls in dumps holding "›".)
jl() { tr '{' '\n' < "$1" 2>/dev/null | tr '\213\257\242' '123' | tr '\200-\377' '~'; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
shot() { rm -f $F/qa_clip/${TAG}_$1.json; screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1
  i=0; while [ ! -s $F/qa_clip/${TAG}_$1.json ] && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  i=0; while ! dr $F/qa_clip/${TAG}_$1.json && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  dr $F/qa_clip/${TAG}_$1.json || { [ -s $F/qa_clip/${TAG}_$1.json ] && log "dump unreadable: $1" || log "dump missing: $1"; }; }
launches() { logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked'; }
qawins() { dumpsys window windows | grep -E "Window #|isVisible=" | grep -A1 "Window #.*$P" | grep -c "isVisible=true"; }
. /data/local/tmp/rec.sh 2>/dev/null
walk() { # name: R1 x3 recorded with a shot per page, then L1 x3
  REC_SEG=9 rec_start $O/$1_r1 9; sleep 0.6
  k BUTTON_R1 1.0; shot $1_details; k BUTTON_R1 1.0; shot $1_trophies; k BUTTON_R1 1.0; shot $1_options
  rec_stop; sleep 0.3
  k BUTTON_L1 0.9; k BUTTON_L1 0.9; k BUTTON_L1 1.0; shot $1_back_main
}
# 529l touch (TOUCH=1): on the Achievements page a tap on the first achievement row, on the Options page a tap on the
# "Game settings" row (opens a sheet - nothing is chosen in it; B goes back). Taps go to the middle of the shown part of the
# control in that page's layout dump; nothing is tapped when the dump has no such control.
# (678b: as popclip.sh 678a - the device's grep -iE read no label that fixed-string reads found, and this trial logged "tap:
# no shown control" for both taps. No regex reads a label any more: ctl takes a shell glob (case-blind; the label matches it,
# or ends in a non-letter + it - the old rule), cuts label / full / shown out with ${x#..} / ${x%%..} and prints the same line
# the old grep -o did; tapj skips zero-size keys in the shell instead of a grep -vE.)
ctl() { w=$(printf '%s' "$1" | tr A-Z a-z); jl $2 | while IFS= read -r ln; do case "$ln" in '"label":"'*'","kind":"control","full":['*'],"shown":['*) ;; *) continue;; esac; lb=${ln#'"label":"'}; lb=${lb%%'"'*}; r=${ln#*'"full":['}; fu=${r%%']'*}; r=${ln#*'"shown":['}; sw=${r%%']'*}; case "$fu,$sw" in *[!0-9,-]*) continue;; esac; lo=$(printf '%s' "$lb" | tr A-Z a-z); case "$lo" in $w|*[!a-z]$w) printf '"label":"%s","kind":"control","full":[%s],"shown":[%s]\n' "$lb" "$fu" "$sw";; esac; done; }
tapj() { r=$(ctl "$1" $F/qa_clip/${TAG}_$2.json | while IFS= read -r c; do s=${c#*'"shown":['}; s=${s%%']'*}; set -- $(echo $s | tr ',' ' '); [ "$3" != 0 ] && [ "$4" != 0 ] && { echo $s; break; }; done)
  [ -n "$r" ] || { log "tap: no shown control '$1' in $2"; return 1; }
  gate; q=$1; set -- $(echo $r | tr ',' ' '); input tap $(( $1 + $3 / 2 )) $(( $2 + $4 / 2 )); log "tap '$q' at $(( $1 + $3 / 2 )),$(( $2 + $4 / 2 ))"; }
# (678d, emulator run 43961010406 on 678c: both taps still "no shown control" - the dumps were read right, the PAGES were
# wrong: the game had no achievement set (the art fixture gives one game in four none), so its window had Main / Details /
# Options; R1 R1 landed on Options and the next R1 wrapped to Main. touchp now names a page by what its dump holds, counts
# its R1 presses and goes back with as many L1; a game without a set logs the achievement tap NOT TESTED, never a miss.
# And run takes a game WITH a set (the art fixture's "achfix n=10" log) when the chosen game logs n=0.)
has() { [ -n "$(ctl "$1" $F/qa_clip/${TAG}_$2.json)" ]; }
touchp() { n=2
  k BUTTON_R1 1.0; k BUTTON_R1 1.2; shot $1_t_trophies
  if has "*achievement 1*" $1_t_trophies; then
    tapj "*achievement 1*" $1_t_trophies && { sleep 1.3; shot $1_t_ach_tap; }
    k BUTTON_R1 1.2; n=3; shot $1_t_options
  elif has "game settings*" $1_t_trophies; then
    log "touch: $1 has no Achievements page (R1 R1 reached Options: no achievement set) - achievement tap NOT TESTED"
    shot $1_t_options
  else
    log "touch: $1 page after R1 R1 is neither Achievements nor Options - achievement tap NOT TESTED"
    k BUTTON_R1 1.2; n=3; shot $1_t_options
  fi
  tapj "game settings*" $1_t_options && { sleep 1.6; shot $1_t_opt_tap; k BUTTON_B 1.3; shot $1_t_opt_back; }
  m=$n; while [ $n -gt 0 ]; do k BUTTON_L1 $([ $n = 1 ] && echo 1.0 || echo 0.9); n=$((n - 1)); done; shot $1_t_main
  { has "full details" $1_t_main || has "game settings*" $1_t_main || has "*achievement 1*" $1_t_main; } && log "touch: $1 not back on the main page after $m L1"; :
}
# (678d) GM: the game; a game whose art-fixture log says n=0 (no set) is swapped for the first one with a set of 10.
GM="Shadow of the Colossus"
achgame() { lc=$(printf '%s' "$GM" | tr A-Z a-z)
  logcat -d -s VoltcapQA | grep -F "achfix n=0 game=" | sed 's/.*game=//' | tr A-Z a-z | grep -qF "$lc" || return 0
  g=$(logcat -d -s VoltcapQA | grep -F "achfix n=10 game=" | head -1 | sed 's/.*game=//' | tr -d '\r')
  [ -n "$g" ] && { log "'$GM' has no achievement set (art fixture n=0) - using '$g' (a set of 10)"; GM=$g; }; }
case $MODE in
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null && { k BUTTON_B 1.5; log "picker closed with B"; }
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  achgame; A --es voltcap_test_game "$GM"; sleep 2
  log "setup scale=$SC game=$GM layouts=$LAYS"
  for lay in $LAYS; do
    A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout $lay; sleep 3.5; gate
    n=${lay}; logcat -c
    # (529p: a capture is labelled only when its state is there - one QA window on the home, two once the game window is open.)
    [ "$(qawins)" = 1 ] || { log "$lay: PRECONDITION FAILED - $(qawins) QA windows on the home (a dialog over it?) - layout NOT TESTED"; screencap -p $O/${n}_precondition.png; continue; }
    shot ${n}_home; k BUTTON_A 1.9
    [ "$(qawins)" = 2 ] || { log "$lay: the game window did not open ($(qawins) QA windows) - layout NOT TESTED"; screencap -p $O/${n}_precondition.png; k BUTTON_B 1.2; continue; }
    shot ${n}_main
    walk ${n}
    [ "$TOUCH" = 1 ] && touchp ${n}
    k BUTTON_B 1.4; shot ${n}_closed
    logcat -c; for i in 1 2 3; do k BUTTON_A 0.35; k BUTTON_B 0.35; done; sleep 1.5
    echo "rapid_reopen_launches=$(launches)" > $O/${n}_rapid.txt; shot ${n}_after_rapid
    # Motion Off (Settings › Motion level, held in preview): page changes are immediate.
    A --es voltcap_test_pref "motion_level=OFF"; sleep 1.2; gate
    k BUTTON_A 1.6; walk ${n}_motionoff; k BUTTON_B 1.4
    A --es voltcap_test_pref "motion_level=NORMAL"; sleep 1
    log "$lay done (launches during the walk: $(launches))"
  done
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  if [ -d $O/snapbak ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"; fi
  am force-stop $P; log "QA app stopped" ;;
esac

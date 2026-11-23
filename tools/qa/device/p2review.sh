#!/system/bin/sh
# P2 (681b, owner 17 Nov: "complete screenshots and short page-transition recordings covering Info, Liner Notes, Tracklist and
# Pressing Notes at x1.0 and x1.3 ... both unlocked and locked achievements, long descriptions and enough items to test
# scrolling ... focused buttons and destructive-option confirmations"): one layout's own pages, reviewed. Isolated QA app ONLY
# (com.shelf.launcher.qa), fixture library + synthetic achievements, launches blocked, preview on (nothing saved); the owner's
# QA snapshot kept and put back by `restore`. Never wakes / unlocks: keys only while the QA app is in front and awake.
#   sh p2review.sh run TAG SCALE "LAYOUTS"   per layout, on a game with earned AND locked achievements (art fixture "achmix"):
#        home, Info (+ the 2nd key focused), the page walk R1 R1 R1 R1 recorded, a shot per page and its focused first row,
#        a track opened (A) and the list scrolled; then the 120-achievement game's Tracklist scrolled far; then an Android app's
#        Options page with a destructive row ARMED (one press - never confirmed) and moved off; Motion Off walk recorded.
#   sh p2review.sh restore TAG
TWO_STEP="CUBE"   # layouts whose home opens a console first, then the game
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; MODE=$1; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
SC=${3:-off}; LAYS=${4:-CRATE}
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T) $*" >> $L; }
stop() { log "STOP $1"; echo DONE >> $L; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" || stop "QA app not in front"; }
# (682j-c, owner 18 Nov 14:54: keys only through winguard.sh - inside the game window only while that window has the focus;
# the home's A only while the QA app's home window has it; anything else = ABORT, nothing more pressed, test state put back.)
WG_LOG=$L
putback() { A --es voltcap_test_libapp remove:com.android.settings; A --ez voltcap_test_preview false; A --es voltcap_test_snapshot restore; sleep 2; am force-stop $P; }
WG_ONABORT=putback
. "$(dirname "$0")/winguard.sh"
wgk() { case $1 in BUTTON_A) echo A;; BUTTON_B) echo B;; BUTTON_R1) echo R1;; BUTTON_L1) echo L1;; DPAD_UP) echo U;; DPAD_DOWN) echo D;;
  DPAD_LEFT) echo L;; DPAD_RIGHT) echo R;; *) echo "$1";; esac; }
k() { wg_key_win $(wgk $1); sleep ${2:-0.8}; }
# close the game window: B only while that window still has the focus, until QaClip says no window is open (each B checked;
# a window that took the focus elsewhere = ABORT through winguard), then the home is confirmed again
closewin() { for i in 1 2 3; do [ "$(wg_popup)" = null ] && break; wg_key_win B; sleep 1.4; [ "$(wg_hash "$(wg_focus)")" = "$WG_WIN" ] || break; done
  wg_home; }
# (682r) the armed confirmation read twice (a dump taken while the row was still growing called a shown confirmation missing
# at x1.0, review 44280831400) - a second READ, never a second key
armed() { grep -qiE 'again to confirm|cover lifted|guarded switch|are you sure' $O/${TAG}_$1.json 2>/dev/null && return 0
  sleep 0.8; shot $1; grep -qiE 'again to confirm|cover lifted|guarded switch|are you sure' $O/${TAG}_$1.json 2>/dev/null; }
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
shot() { rm -f $F/qa_clip/${TAG}_$1.json; screencap -p $O/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1
  i=0; while ! dr $F/qa_clip/${TAG}_$1.json && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  dr $F/qa_clip/${TAG}_$1.json && cp $F/qa_clip/${TAG}_$1.json $O/ || log "dump missing: $1"; }
qawins() { dumpsys window windows | grep -E "Window #|isVisible=" | grep -A1 "Window #.*$P" | grep -c "isVisible=true"; }
# 0 when the dump [1] has the window's Play control focused (a focused node labelled Play), i.e. A would start the game.
# (681x: the old ERE's [^]] bracket did not match under the device's toybox grep - the TV dump had Play focused and A was
# pressed. One node per line (split on '{'; a node holds no nested object), then fixed-string matches only.)
# (682f) the D-pad is on the "Hide from library" option row (QaClip: a focused node whose label carries it)
onhide() { [ -s $O/$1.json ] && tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"focused":true' | grep -qiF 'Hide from library'; } # (682h: -i, Hub / TV print it in capitals)
playfocused() { [ -s $O/$1.json ] && tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"label":"Play",' | grep -F '"kind":"control",' | grep -qF '"focused":true'; }
# (682r) the dump's focused node is one of the window's own keys (Play / Full list / Full details / Back ...), not a page row
# (every focused control's label, one a line - the window's own node is focused too, before or after the row, by layout:
# RP Classic Arcade listed the row first, the KONKR Magazine the window first)
focuslabel() { tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"focused":true' | grep -F '"kind":"control"' | sed -n 's/.*"label":"\([^"]*\)".*/\1/p'; }
onkey() { [ -s $O/$1.json ] && focuslabel $1 | grep -qixE '(▸ |▶ )?(play|play it!|full list|full details|back|details|options|achievements)'; }
launches() { logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked'; }
# (682r, RP Classic: "." on a missing rec.sh ended the script silently before its first log line - the recorder now comes from
# the script's folder or /data/local/tmp; none = the recordings are NOT TESTED, said so, the walk still runs)
for r in "$(dirname "$0")/rec.sh" /data/local/tmp/rec.sh; do [ -f "$r" ] && { . "$r"; break; }; done
type rec_start > /dev/null 2>&1 || { rec_start() { log "no recorder (rec.sh) - recording $1 NOT TESTED"; }; rec_stop() { :; }; }
pick() { logcat -d -s VoltcapQA | grep -F "$1" | head -1 | sed 's/.*game=//' | tr -d '\r'; }
openw() { # layout name -> 0 when the game window is open
  A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=${MOTION:-NORMAL}"; A --es voltcap_test_layout $1; sleep 3.5; gate
  [ "$(qawins)" = 1 ] || { log "$1: PRECONDITION FAILED - $(qawins) QA windows on the home"; screencap -p $O/${2}_precondition.png; return 1; }
  shot ${2}_home; wg_home
  # (682r, KONKR p2k13b: the Cube menu's home is a cube per console - its first A opens the console's face, the game is one A
  # further. Declared per layout, never a retry: the second A only for a layout listed here, on the confirmed home.)
  # (682r, KONKR / RP Classic p2k13c: after the first walk the cube stays on the console's face - its first A already opened
  # the game and the home check then aborted. After the first A: a game window open = that is the window.)
  case " $TWO_STEP " in *" $1 "*) wg_key_home A; sleep 1.6
    if [ -n "$(wg_popup | grep -v '^null$')" ]; then log "$1: the console's face was open - the game window opened at once"; wg_window; return; fi
    log "$1: console opened (two-step home)"; wg_home;; esac
  wg_key_home A; sleep 1.9
  wg_window; }   # (no window = ABORT: winguard stops the run, nothing more pressed)
# (682aj, owner 23 Nov 18:23 - "Investigate the 48% slow frames ... with screen recording enabled and disabled, stable screen versus
# page transitions, idle versus active navigation"): frame-time phases per layout, each between two read-only gfxinfo dumps
# (tools/gfxdelta.py --detail) plus the last 120 frames' framestats. Recording only in the *_rec phases.
#   sh p2review.sh perf TAG SCALE "LAYOUTS"   then  sh p2review.sh restore TAG
phase() { dumpsys gfxinfo $P > $O/${n}_P_$1_gfx0.txt 2>/dev/null; }
phase_end() { dumpsys gfxinfo $P > $O/${n}_P_$1_gfx.txt 2>/dev/null; dumpsys gfxinfo $P framestats > $O/${n}_P_$1_fs.txt 2>/dev/null; }
perf_layout() {
  # (PERF_GAME: the game the home starts on - comparing builds needs the same one; builds before 681b had no "achmix" game
  # and started on the fallback, so a build comparison sets PERF_GAME="Shadow of the Colossus" for both.)
  n=$1; A --es voltcap_test_game "${PERF_GAME:-${MIX:-Shadow of the Colossus}}"; sleep 2
  A --ez voltcap_test_preview true --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout $1; sleep 4; gate
  [ "$(qawins)" = 1 ] || { log "$1: PRECONDITION FAILED - $(qawins) QA windows on the home"; return 1; }
  wg_home
  phase home_idle; sleep 6; phase_end home_idle
  phase home_idle_rec; REC_SEG=8 rec_start $O/${n}_P_home_idle 8; sleep 6.5; rec_stop; phase_end home_idle_rec
  phase home_nav; for i in 1 2 3 4 5 6; do wg_key_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do wg_key_home L; sleep 0.5; done; sleep 0.8; phase_end home_nav
  openw $1 ${n}_perf || return 1
  sleep 1.5; phase win_idle; sleep 6; phase_end win_idle
  phase win_walk; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3; phase_end win_walk
  phase win_walk_rec; REC_SEG=12 rec_start $O/${n}_P_walk 12; sleep 0.6; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3; rec_stop; sleep 0.3; phase_end win_walk_rec
  phase win_nav; for i in 1 2 3; do k DPAD_RIGHT 0.5; done; for i in 1 2 3; do k DPAD_LEFT 0.5; done; sleep 0.6; phase_end win_nav
  closewin; log "$1: perf phases done (launches: $(launches))"; }
# (682al, D386 - the hitch at a page change: what the main thread does. atrace of the QA app's own sections (Recomposer,
# AndroidOwner measure / layout, input, view, gfx) over one page walk inside the confirmed window. Level 1, nothing else traced.)
#   sh p2review.sh trace TAG SCALE "LAYOUTS"   then  sh p2review.sh restore TAG
trace_layout() {
  n=$1; A --es voltcap_test_game "${PERF_GAME:-${MIX:-Shadow of the Colossus}}"; sleep 2
  openw $1 ${n}_trace || return 1
  sleep 1.5
  atrace --async_start -a $P -b 16384 gfx view input am dalvik > /dev/null 2>&1
  sleep 0.5; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3
  atrace --async_dump -o $O/${n}_trace.txt > /dev/null 2>&1; atrace --async_stop > /dev/null 2>&1
  closewin; log "$1: trace done ($(wc -c < $O/${n}_trace.txt 2>/dev/null) bytes, launches: $(launches))"; }
# (682an, owner Task 1 row-position checks): the home only, per layout, at each Row place (R1 Row tile's own hook) - a screenshot +
# QaClip dump each, no key pressed. Level 1.   sh p2review.sh rows TAG SCALE "LAYOUTS"   then  sh p2review.sh restore TAG
rows_layout() {
  n=$1; A --es voltcap_test_game "${PERF_GAME:-${MIX:-Shadow of the Colossus}}"; sleep 2
  A --ez voltcap_test_preview true; A --es voltcap_test_layout $1; sleep 3.5; gate
  for D in TOP MIDDLE BOTTOM; do A --es voltcap_test_dock $D; sleep 2.5; gate; shot ${n}_row_$D; done
  A --es voltcap_test_dock MIDDLE; sleep 1; log "$1: row places shot"; }
case $MODE in
rows)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  for lay in $LAYS; do rows_layout $lay; done
  log "rows done"; echo DONE >> $L;;
trace)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  for lay in $LAYS; do logcat -c; trace_layout $lay; done
  log "trace done"; echo DONE >> $L;;
perf)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  for lay in $LAYS; do logcat -c; perf_layout $lay; done
  log "perf done"; echo DONE >> $L;;
run)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  for lay in $LAYS; do
    n=$lay; logcat -c
    A --es voltcap_test_game "${MIX:-Shadow of the Colossus}"; sleep 2
    openw $lay $n || continue
    shot ${n}_info; k DPAD_RIGHT 0.9; shot ${n}_info_key2; k DPAD_LEFT 0.9
    # (682ag, owner 23 Nov 13:32 - page-transition audit: the walk's frame times from the app's own renderer - its gfxinfo counters
    # before and after the walk (tools/gfxdelta.py takes the difference), next to the recording; screenrecord fps is no frame-time
    # evidence on the RP Classic. Never "gfxinfo reset": on the KONKR it aborted the QA app in hwui's JankTracker ("Impossible
    # totalDuration 0", run kg13) - read-only dumps only.)
    dumpsys gfxinfo $P > $O/${n}_walk_gfx0.txt 2>/dev/null
    REC_SEG=12 rec_start $O/${n}_walk 12; sleep 0.6
    k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.1; k BUTTON_R1 1.3
    rec_stop; sleep 0.3
    dumpsys gfxinfo $P > $O/${n}_walk_gfx.txt 2>/dev/null
    k BUTTON_R1 1.1; shot ${n}_details; k DPAD_UP 0.9; shot ${n}_details_focus
    k BUTTON_R1 1.1; shot ${n}_trophies; k DPAD_DOWN 0.8; k DPAD_DOWN 0.8; shot ${n}_trophies_focus
    # (682r, KONKR Magazine x1.3: the list had no row on screen, two Downs reached the window's "Full list" key and the A
    # opened the achievement dialog - winguard stopped the run. A only on an achievement row: never on a window key.)
    # (682ab, Icon bar x1.0 fk10 / fr10: its achievements are a grid of icons - two rows hold ten, so Down Down from the order
    # icon passes row 2 to the keys. A grid page gets one Up back onto the row above the keys; A only if that is not a key.)
    if onkey ${TAG}_${n}_trophies_focus; then k DPAD_UP 0.8; shot ${n}_trophies_focus
      if onkey ${TAG}_${n}_trophies_focus; then log "$lay: FAIL - two Downs from the page's first row reached a window key ($(focuslabel ${TAG}_${n}_trophies_focus | tr '\n' ';')), no achievement row - A NOT pressed"
      else log "$lay: grid page - Down Down passed the last row, one Up back onto an achievement"; k BUTTON_A 1.0; fi
    else k BUTTON_A 1.0; fi; shot ${n}_trophies_open
    for i in 1 2 3 4 5 6 7; do k DPAD_DOWN 0.45; done; sleep 0.6; shot ${n}_trophies_scroll
    k BUTTON_R1 1.1; shot ${n}_options; k DPAD_DOWN 0.8; shot ${n}_options_focus
    k BUTTON_B 1.2; shot ${n}_back_main; closewin; shot ${n}_closed
    if [ -n "$BIG" ]; then
      A --es voltcap_test_game "$BIG"; sleep 2
      if openw $lay ${n}_big; then
        k BUTTON_R1 1.1; k BUTTON_R1 1.1; shot ${n}_big_trophies
        for i in $(seq 1 24); do k DPAD_DOWN 0.3; done; sleep 0.8; shot ${n}_big_trophies_far
        closewin
      fi
    fi
    # The destructive confirmation: an app in the QA library's App category ("Hide from library" asks first) - one press arms
    # it, moving off cancels, B leaves. Never confirmed.
    # (681z, review 44247782675: the category still did not take - the QA library had no app, the launcher logged "category
    # App is empty here; left as it was" and the window opened on a ROM game. Then the confirmation is NOT TESTED, said so.)
    # (No logcat -c here: the layout's blocked-launch count reads the same buffer.)
    # (682c: the QA library gets an app of its own for this - Settings, never launched, never hidden; taken out after)
    A --es voltcap_test_libapp add:com.android.settings; sleep 2
    e0=$(logcat -d -s VoltcapQA | grep -cF 'category App is empty here'); A --es voltcap_test_category App; sleep 2
    if [ "$(logcat -d -s VoltcapQA | grep -cF 'category App is empty here')" -gt "$e0" ]; then log "$lay: no Android app in the QA library - destructive confirmation NOT TESTED"
    elif openw $lay ${n}_app; then
      k BUTTON_L1 1.2; shot ${n}_app_options
      # (682f, review 44264372593: ten Downs left the list for the "B Back" hint and the A pressed it - back to Info, logged as
      # armed. Now one Down at a time, the dump read after each: A only once "Hide from library" itself has the focus.)
      j=0; found=0
      while [ $j -lt 14 ]; do k DPAD_DOWN 0.5; sleep 0.3; shot ${n}_app_step$j
        if onhide ${TAG}_${n}_app_step$j; then found=1; break; fi
        if playfocused ${TAG}_${n}_app_step$j; then break; fi
        j=$((j + 1)); done
      cp $O/${n}_app_step$j.png $O/${n}_app_options_last.png 2>/dev/null
      if [ $found = 0 ]; then log "$lay: the focus never reached 'Hide from library' ($j Downs) - A NOT pressed (armed confirmation NOT TESTED)"
      else
        k BUTTON_A 1.0; shot ${n}_app_armed
        # (claimed only when the dump shows the confirmation line - text, not the log's hope)
        # (682h: the dumps are kept as $O/${TAG}_<name>.json - review 44272567211 read the wrong name and called a shown
        # confirmation missing)
        if armed ${n}_app_armed; then
          k DPAD_UP 0.9; shot ${n}_app_disarmed
          if grep -qiE 'again to confirm|cover lifted|guarded switch|are you sure' $O/${TAG}_${n}_app_disarmed.json 2>/dev/null; then log "$lay: FAIL - the confirmation stayed after moving off"
          else log "$lay: destructive row armed (confirmation shown) and left (cancelled; never confirmed)"; fi
        else log "$lay: FAIL - A on 'Hide from library' showed no confirmation"; fi
      fi
      closewin
    fi
    A --es voltcap_test_category ALL; sleep 1.5
    A --es voltcap_test_libapp remove:com.android.settings; sleep 1.5
    # Motion Off: the walk again, recorded - page changes immediate.
    A --es voltcap_test_game "${MIX:-Shadow of the Colossus}"; sleep 2
    MOTION=OFF; if openw $lay ${n}_off; then REC_SEG=10 rec_start $O/${n}_walk_motionoff 10; sleep 0.6
      k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.0; k BUTTON_R1 1.2; rec_stop; closewin; fi; MOTION=NORMAL
    log "$lay done (launches: $(launches))"
  done
  log "run done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L ;;
restore)
  A --es voltcap_test_libapp remove:com.android.settings; sleep 1.5
  A --es voltcap_test_pref "motion_level=NORMAL" --es voltcap_test_scale off --es voltcap_test_fixture off; sleep 1.5
  A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
  log "restore verify: $(cut -c1-60 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
  # (681z, review 44247782675: on a fresh emulator there were no snapshot files to keep - the empty folder's literal glob made
  # cp / cmp fail and the restore logged diff=1. An empty keep folder means there was nothing to put back.)
  if [ -d $O/snapbak ] && [ -n "$(ls $O/snapbak 2>/dev/null)" ]; then cp $O/snapbak/* $F/; n=0; for f in $O/snapbak/*; do cmp -s $f $F/$(basename $f) || n=1; done; log "owner snapshot files back: diff=$n"
  elif [ -d $O/snapbak ]; then log "owner snapshot files back: none existed before the run"; fi
  am force-stop $P; log "QA app stopped" ;;
esac

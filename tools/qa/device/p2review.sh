#!/system/bin/sh
. "$(dirname "$0")/qaguard.sh" 2>/dev/null || . /data/local/tmp/vqa/qaguard.sh 2>/dev/null || { echo "qaguard.sh missing - push it beside this script" >&2; exit 3; } # (682ar, D390: a real end)
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
putback() { A --es voltcap_test_libapp remove:com.android.settings; [ -n "$APP_PKG" ] && A --es voltcap_test_libapp remove:$APP_PKG; A --ez voltcap_test_preview false; A --es voltcap_test_snapshot restore; sleep 2; am force-stop $P; }
WG_ONABORT=putback
. "$(dirname "$0")/winguard.sh"
# (682ar, D390: a real end - every key / tap checks the deadline, 15 min by default (P2_MAX_S overrides, never more); a
# watchdog ends the run a minute after it, putting the QA app's state back. Restore presses nothing and has no deadline.)
[ "$MODE" = restore ] || { [ -n "$P2_MAX_S" ] && [ "$P2_MAX_S" -lt 900 ] && WG_DEADLINE=$(( $(date +%s) + P2_MAX_S )); wg_watchdog; }
wgk() { case $1 in BUTTON_A) echo A;; BUTTON_B) echo B;; BUTTON_R1) echo R1;; BUTTON_L1) echo L1;; DPAD_UP) echo U;; DPAD_DOWN) echo D;;
  DPAD_LEFT) echo L;; DPAD_RIGHT) echo R;; *) echo "$1";; esac; }
k() { wg_key_win $(wgk $1); sleep ${2:-0.8}; }
# close the game window: B only while that window still has the focus, until QaClip says no window is open (each B checked;
# a window that took the focus elsewhere = ABORT through winguard), then the home is confirmed again
closewin() { for i in 1 2 3; do [ "$(wg_popup)" = null ] && break; wg_key_win B; sleep 1.4; [ "$(wg_hash "$(wg_focus)")" = "$WG_WIN" ] || break; done
  wg_home; }
# (682r) the armed confirmation read twice (a dump taken while the row was still growing called a shown confirmation missing
# at x1.0, review 44280831400) - a second READ, never a second key
# (748a, review 45566681915 p2r10: "A showed no confirmation" while the kept dump says "PRESS A AGAIN TO CONFIRM" - the device's
# grep -qiE missed it in the dump's one long line with a '·' in it; read like onhide: short lines, fixed strings)
confirmtext() { [ -s "$1" ] && tr -d '\n' < "$1" | tr '{' '\n' | grep -qiF -e 'again to confirm' -e 'cover lifted' -e 'guarded switch' -e 'are you sure'; }
armed() { confirmtext $O/${TAG}_$1.json && return 0
  sleep 0.8; shot $1; confirmtext $O/${TAG}_$1.json; }
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
# (682ao, owner Task 1 - 714a on hardware): Tile home's round buttons placed by hand at Row Middle, then the REAL R1 Row tile
# tapped Middle -> Bottom -> Top -> Middle: the hand spot is kept aside for Middle and comes back there; at Bottom / Top the
# buttons sit at their normal spot. Taps only on the QA app's own R1 tile, found by its QaClip bounds, while the QA app has the
# focus (gate). Settings come back with the snapshot (restore).   sh p2review.sh r714 TAG SCALE   then  restore TAG
wpos() { run-as $P sh -c 'cat shared_prefs/voltcap_features.xml' 2>/dev/null | tr -d '\n' | grep -o '"widget_positions">[^<]*' | cut -d'>' -f2; }
qtap() { # tap the centre of the first QaClip node whose label starts with $1 in dump $2
  tr -d '\n' < $O/$2.json | tr '{' '\n' | grep -F "\"label\":\"$1" | head -1 | grep -o '"full":\[[0-9.,-]*\]' | tr -d '"full:[]' > $O/tap.txt
  d=$(grep -o '"density":[0-9.]*' $O/$2.json | head -1 | cut -d: -f2)
  [ -s $O/tap.txt ] || { log "no '$1' in $2 - nothing tapped"; return 1; }
  xy=$(awk -F, -v d=$d '{printf "%d %d", ($1+$3/2)*d, ($2+$4/2)*d}' $O/tap.txt)
  gate; wg_time_ok; case "$(wg_focus)" in *"$P/"*) ;; *) stop "tap: QA app lost the focus";; esac
  input tap $xy; log "tapped '$1' at $xy"; }
rowtile() { k_home R1; sleep 1.6; shot r1_$1; qtap "Row" ${TAG}_r1_$1 || return 1; sleep 1.2; shot r1_after_$1; k_home_any B; sleep 1.2; }
# (725b: through winguard - R1 only while the confirmed home holds the focus and says HOME; the B that closes the R1 panel
# only while the QA app's confirmed main window still holds it. The old raw `input keyevent` skipped the guard - selftest red.)
k_home() { gate; wg_key_home $1; }
# (682as, Priority 3 - RP Classic Arcade GPU: which part of the Arcade home costs the GPU. The Arcade home with ONE of its own
# look options changed at a time (Features prefs, the same switches as Layout options; the snapshot restores them all), each
# with a still phase and a home-navigation phase between read-only gfxinfo dumps. Level 1, QA app, synthetic games only.
#   sh p2review.sh arbisect TAG SCALE   then  sh p2review.sh restore TAG
# (682as, owner 24 Nov "Arcade A: measure the RP Classic with the same 30-game category, recording disabled, before and after"):
# the same build, the tubes' text legacy (before) and new (after) by the QA-only switch, interleaved twice; each: one press and
# back, then 8 s still (frames drawn with nothing pressed), then 12 home moves. Level 1, QA app, synthetic games.
#   sh p2review.sh arab TAG SCALE   then  sh p2review.sh restore TAG
# (682at, owner 24 Nov option 2 - the charging light: "compare the visual appearance and rendering cost before/after on KONKR and
# RP Classic, with the same charging conditions"): the same build, legacy / new by the QA-only switch, interleaved twice; the
# battery shown as charging at 77 % by the QA app's own status hook (the same on both handhelds, the real charging untouched);
# per variant: 6 s to let the start feedback pass, a press and back, then 8 s still (frames drawn); then the level to 78 % and
# six screenshots of the light 0.25 s apart plus one 6 s later. Level 1, QA app only.
#   sh p2review.sh chab TAG SCALE   then  sh p2review.sh restore TAG
# (682au, owner 24 Nov - Arcade B: "use a controlled comparison"): FitText's size search legacy / new by the QA-only switch, the same
# build, interleaved twice; each: 2 x 12 home moves on the 30-game category (gfxinfo + framestats). Level 1, QA app only.
#   sh p2review.sh fsab TAG SCALE   then  sh p2review.sh restore TAG
# (682av, owner 25 Nov - hardware QA of 723a R1 Focus position, never through the R1 panel: the QA-only hook makes the tile's own
# change, FocusPosTile.next / pick): 1) saved for real in the QA app's own settings, the QA app restarted - kept; 2) previewed:
# Voltcap row and Tile home, every choice, the home browsed right and back with a QaClip dump (where the game you're on sits),
# Endless on at the first game; a press cycling through all; 3) other layouts (GRID HERO ARCADE COVERFLOW): the tile hidden.
#   APP-free.  sh p2review.sh focuspos TAG SCALE   then  sh p2review.sh restore TAG
# (682av: after the QA app is force-stopped, the app behind it comes to the front for a moment (RP Classic: its Settings) - wait
# until the QA app is in front again before anything else; nothing pressed meanwhile)
# (682av: the QA lines kept as they come - a long run's logcat ring dropped the first section's lines on the KONKR)
qa_keep() { logcat -d -s VoltcapQA | grep -E "$2" >> $O/$1; logcat -c; }
qa_back() { A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; i=0
  while [ $i -lt 30 ]; do dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity" | grep -q "$P/" && break; sleep 0.5; i=$((i + 1)); done
  sleep 4; gate; }
fp_layout() {
  lay=$1; A --es voltcap_test_layout $lay; sleep 3; gate
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  # (All - the fixture's ~30 games; a console holds 2-5)
  # (RP Classic x1.3: the QA app showed its profile picker again after the restart + scale - passed as at the start)
  A --es voltcap_test_category ALL; sleep 2; gate; [ "$(wg_mark)" = PICKER ] && { wg_pass_picker; sleep 1; }; wg_home
  for c in 0 1 2 3; do
    A --es voltcap_test_focuspos $c; sleep 1.4
    shot FP_${lay}_c${c}_a
    for i in 1 2 3 4; do k_home R; sleep 0.45; done; sleep 0.8; shot FP_${lay}_c${c}_b
    for i in 1 2 3 4; do k_home L; sleep 0.45; done; sleep 0.8; shot FP_${lay}_c${c}_c
  done
  for i in 1 2 3 4 5; do A --es voltcap_test_focuspos NEXT; sleep 0.9; done
  # Endless on: from the category's first game one more Left goes round to its last
  A --es voltcap_test_focuspos 1; sleep 1; A --es voltcap_test_loop true; sleep 1.2
  for i in 1 2 3 4 5 6 7 8 9 10; do k_home L; sleep 0.35; done; sleep 0.8; shot FP_${lay}_loop_a
  k_home L; sleep 1.0; shot FP_${lay}_loop_b
  A --es voltcap_test_loop false; sleep 1; A --es voltcap_test_focuspos 0; sleep 1
  log "$lay focuspos done"; }
# (682av, owner 25 Nov - hardware QA of 724a Recomps / 725a START multi-category, on an installed app put in the QA app's OWN
# library - APP_PKG, a harmless app already on the handheld; its choices are the QA app's own data (library_apps snapshot)):
# the same view-model calls START > Categories makes (QA hook), its START window and Categories window shown and walked with the
# D-pad and one touch, the QA app restarted (kept), the choices taken off one by one (the app stays, same record), and its
# window's Play pressed once (the QA launch block records the launch of that app's own record).
#   APP_PKG=<package> sh p2review.sh appcat TAG SCALE   then  APP_PKG=<package> sh p2review.sh restore TAG
ac() { A --es voltcap_test_appcat "$1"; sleep 1.6; qa_keep appcat.txt " appcat |LAUNCH|libapp"; }
fs_ab() {
  n=FS_$1_$2; A --es voltcap_test_fitsearch $1; sleep 2; gate; wg_home
  phase nav; for r in 1 2; do for i in 1 2 3 4 5 6; do k_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do k_home L; sleep 0.5; done; done; sleep 0.8; phase_end nav
  log "fs $n done"; }
# (682ay, owner 25 Nov 10:42 - Arcade B on its own branch: "a new reproducible baseline on the ~30-game All category"):
# per layout, the All category (its game count logged), the same build legacy / new by the QA-only switch voltcap_test_focussave
# (a build without it: both halves the same - the baseline), interleaved twice; each: 2 x 12 home moves 0.5 s apart (gfxinfo +
# framestats) and the home's own rebuild counts (QaTrace "recomp ... Screen=N" lines). Level 1, QA app, synthetic games only.
#   sh p2review.sh isoab TAG SCALE "ARCADE CLASSIC TILES GRID CUBE"   then  sh p2review.sh restore TAG
is_ab() {
  n=IS_$3_$1_$2; A --es voltcap_test_focussave $1; sleep 2; gate; wg_home; logcat -c
  phase nav; for r in 1 2; do for i in 1 2 3 4 5 6; do k_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do k_home L; sleep 0.5; done; done; sleep 0.8; phase_end nav
  sleep 1.2; k_home R; sleep 1.5; k_home L; sleep 1.5
  qa_keep ${n}_recomp.txt " recomp | focussave| category "
  log "is $n done ($(grep -c ' recomp ' $O/${n}_recomp.txt) recomp lines)"; }
ch_ab() {
  n=CH_$1_$2; A --es voltcap_test_chargelight $1 --es voltcap_test_battery "77|true"; sleep 6; gate; wg_home
  k_home R; sleep 0.7; k_home L; sleep 1.2
  phase still; sleep 8; phase_end still
  A --es voltcap_test_battery "78|true"; sleep 0.3
  for i in 1 2 3 4 5 6; do screencap -p $O/${n}_light_$i.png; sleep 0.25; done
  sleep 6; screencap -p $O/${n}_rest.png
  log "ch $n done"; }
ar_ab() {
  n=AB_$1_$2; A --es voltcap_test_arattract $1; sleep 2; gate; wg_home
  k_home R; sleep 0.7; k_home L; sleep 1.2
  phase still; sleep 8; phase_end still
  # (the status bar's charging light runs ~10 s after a press while the handheld charges - USB QA always does - HomeIdle.settled
  # stops it; 11-19 s after the press the home is still awake (calm comes at 30 s) and only its own activity draws)
  sleep 2; phase settled; sleep 8; phase_end settled
  phase nav; for i in 1 2 3 4 5 6; do k_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do k_home L; sleep 0.5; done; sleep 0.8; phase_end nav
  log "ab $n done"; }
ar_variant() {
  n=AR_$1; shift
  for kv in "$@"; do A --es voltcap_test_pref "$kv"; sleep 0.4; done
  sleep 3; gate; wg_home
  phase still; sleep 6; phase_end still
  phase nav; for i in 1 2 3 4 5 6; do k_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do k_home L; sleep 0.5; done; sleep 0.8; phase_end nav
  log "variant $n: $* done"; }
k_home_any() { gate; wg_key_main $1; }
# (682ap, D379): atrace of the home moving through games (6 right, 6 left) - QA app sections + RenderThread. Level 1.
tracehome_layout() {
  n=$1; A --es voltcap_test_game "${PERF_GAME:-${MIX:-Shadow of the Colossus}}"; sleep 2
  A --ez voltcap_test_preview true; A --es voltcap_test_layout $1; sleep 4; gate; wg_home
  atrace --async_start -a $P -b 16384 gfx view input am dalvik > /dev/null 2>&1; sleep 0.5
  for i in 1 2 3 4 5 6; do wg_key_home R; sleep 0.5; done; for i in 1 2 3 4 5 6; do wg_key_home L; sleep 0.5; done; sleep 0.5
  atrace --async_dump -o $O/${n}_tracehome.txt > /dev/null 2>&1; atrace --async_stop > /dev/null 2>&1
  log "$1: tracehome done ($(wc -c < $O/${n}_tracehome.txt 2>/dev/null) bytes)"
  # (682as: the still home a moment after a press - what is still drawn while it is awake)
  k_home R; sleep 0.8; k_home L; sleep 2
  atrace --async_start -a $P -b 16384 gfx view input am dalvik > /dev/null 2>&1; sleep 5
  atrace --async_dump -o $O/${n}_traceawake.txt > /dev/null 2>&1; atrace --async_stop > /dev/null 2>&1
  log "$1: traceawake done ($(wc -c < $O/${n}_traceawake.txt 2>/dev/null) bytes)"; }
# (682aq, owner 22:43 - D389 lazy rows: "Test 0, 1, 10 and 120 achievements, including rapid input and early closure ... Back /
# reopen"): per layout and fixture game (LZ_GAMES: "name|name"), the Achievements page (R1 R1) opened with its frames read
# (gfxinfo), D-pad bursts of 10 inside the confirmed window (focus checked before every burst) down to the end and back up,
# the focused row read from QaClip each time, B and the page reopened (recorded), and an early close (R1 R1 B straight away).
# Level 1.   sh p2review.sh lazywalk TAG SCALE "LAYOUTS"   then  sh p2review.sh restore TAG
rowlabel() { tr -d '\n' < $O/$1.json | tr '{' '\n' | grep -F '"focused":true' | sed -n 's/.*"label":"\([^"]*\)".*/\1/p' | head -2 | tr '\n' '|'; }
burst() { # $1 key, $2 bursts of 10 - each burst only while the game window has the focus (winguard's own check)
  kc=KEYCODE_$1; b=0; while [ $b -lt $2 ]; do
    wg_time_ok; h=$(wg_hash "$(wg_focus)"); [ "$h" = "$WG_WIN" ] || wg_abort "burst $1: the game window lost the focus"
    input keyevent $kc $kc $kc $kc $kc $kc $kc $kc $kc $kc; b=$((b + 1)); done; }
lazy_layout() {
  lay=$1; gi=0
  # (no `while read` over a file: am / cmd read the loop's stdin - the second game's name went to the first am start)
  OIFS=$IFS; IFS='|'; set -- $LZ_GAMES; IFS=$OIFS
  for gname in "$@"; do
    [ -n "$gname" ] || continue; gi=$((gi + 1)); n=${lay}_g$gi
    A --es voltcap_test_game "$gname"; sleep 2
    openw $lay ${n}_lz || continue
    log "$lay g$gi '$gname': window open"
    sleep 1; phase lz_open; k BUTTON_R1 1.1; k BUTTON_R1 1.6; phase_end lz_open; shot ${n}_lz_page
    log "$lay g$gi: page focus [$(rowlabel ${TAG}_${n}_lz_page)]"
    phase lz_down; burst DPAD_DOWN 14; sleep 1.6; phase_end lz_down; shot ${n}_lz_end
    log "$lay g$gi: after 140 Down [$(rowlabel ${TAG}_${n}_lz_end)]"
    sleep 3; shot ${n}_lz_end2; log "$lay g$gi: 3 s later [$(rowlabel ${TAG}_${n}_lz_end2)] rows-move log: $(logcat -d -s VoltcapQA | grep -c 'rows move') fails: $(logcat -d -s VoltcapQA | grep -c 'rows move.*ok=false') in: $(logcat -d -s VoltcapQA | grep -c 'rows key Down in') ends: $(logcat -d -s VoltcapQA | grep -c 'passed on') unhandled: $(logcat -d -s VoltcapQA | grep -c 'win unhandled')"
    phase lz_up; burst DPAD_UP 14; sleep 1.6; phase_end lz_up; shot ${n}_lz_top
    log "$lay g$gi: after 140 Up [$(rowlabel ${TAG}_${n}_lz_top)]"
    k BUTTON_B 1.3; shot ${n}_lz_back; log "$lay g$gi: B -> [$(rowlabel ${TAG}_${n}_lz_back)] popup=$(wg_popup | head -c 40)"
    if [ "$(wg_popup)" != null ]; then
      phase lz_reopen; REC_SEG=6 rec_start $O/${n}_lz_reopen 6; sleep 0.5; k BUTTON_R1 1.1; k BUTTON_R1 1.8; rec_stop; phase_end lz_reopen
      shot ${n}_lz_reopen; log "$lay g$gi: reopened [$(rowlabel ${TAG}_${n}_lz_reopen)]"
    fi
    closewin
    # early close: the page asked for and the window closed before it settles
    A --es voltcap_test_game "$gname"; sleep 1.5
    if openw $lay ${n}_lze; then k BUTTON_R1 0.25; k BUTTON_R1 0.12; wg_key_win B; sleep 0.25; closewin; shot ${n}_lz_early
      log "$lay g$gi: early close - popup=$(wg_popup | head -c 20) crash=$(logcat -d -b crash | grep -c "$P")"; fi
  done; }
# (682aq, owner 22:43 item 2 - Coverflow option A at x1.3: "all facts reachable by scrolling, the synopsis not under the fixed button
# bar"): Cover details window, its Details page: shots while Up walks to the top (the facts) and Down to the story's end, each with
# its QaClip dump (the text's and the keys' bounds). Level 1.   sh p2review.sh cover TAG SCALE   then  sh p2review.sh restore TAG
cover_walk() {
  # (682aq8: its own counter - shot() runs its own `i` loop; sharing it kept this walk pressing Up for 30 minutes, KONKR / RP
  # Classic 24 Nov 02:32-03:03, QA app only, stopped by hand)
  n=COVERDETAILS; A --es voltcap_test_game "${PERF_GAME:-${MIX:-Tetris (World)}}"; sleep 2
  openw COVERDETAILS ${n}_cv || return 1
  k BUTTON_R1 1.4; shot ${n}_cv_details; log "details focus [$(rowlabel ${TAG}_${n}_cv_details)]"
  for cv in 1 2 3 4 5 6 7 8; do k DPAD_UP 0.7; shot ${n}_cv_up$cv; log "up $cv [$(rowlabel ${TAG}_${n}_cv_up$cv)]"; done
  for cv in 1 2 3 4 5 6 7 8 9 10 11 12; do k DPAD_DOWN 0.7; shot ${n}_cv_dn$cv; log "down $cv [$(rowlabel ${TAG}_${n}_cv_dn$cv)]"; done
  closewin; log "cover walk done (launches: $(launches))"; }
# (682ar, owner 24 Nov - "rapid L1 / R1 / A / B sequences cannot accidentally launch games"): per layout, the 120-achievement game
# (or LZ_GAMES), its Achievements page scrolled deep, then quick bursts in one input call each - L1 R1 A, R1 L1 A, L1 R1 R1 A,
# L1 R1 A B, A L1 R1 A, B R1 R1 A ... - with the focused control and the launch count read after each. A launch fails the run.
# Level 1, synthetic games only (the QA app's launch block records, never starts).   sh p2review.sh storm TAG SCALE "LAYOUTS"
# (682au, owner 24 Nov - "complete the Record Crate / Dock-BAR safety verification": scrolled AND re-sorted 120-achievement lists,
# rapid L1 / R1 + A, rapid Back / reopen, and deliberate Play still working): after the storm (storm_layout), per layout: the
# Achievements page, A on the ORDER stamp (its first focus) twice - two re-sorts - then deep down and quick bursts; B B and the
# window reopened at once, R1 R1 A; then a fresh window, its focus read, and ONE deliberate A on Play - exactly one launch must be
# recorded (the QA app's launch block records, never starts). Any other launch fails. Level 1, synthetic games only.
#   sh p2review.sh safety TAG SCALE "LAYOUTS"   then  sh p2review.sh restore TAG
safety_layout() {
  lay=$1; n=${lay}_sf; l0=$(launches)
  # (the plain storm first only with STORM=1 - it passed on both handhelds in 682ar2 / ar3; the 15-minute limit per task)
  if [ "${STORM:-0}" = 1 ]; then storm_layout $lay; [ "$(launches)" = "$l0" ] || { log "FAIL $lay: the storm launched"; return 1; }; fi
  A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 2
  openw $lay $n || return 1
  k BUTTON_R1 1.1; k BUTTON_R1 1.6; shot ${n}_page; log "$lay safety: page focus [$(rowlabel ${TAG}_${n}_page)]"
  for r in 1 2; do
    k BUTTON_A 1.4; shot ${n}_sort$r; log "$lay safety: re-sort $r focus [$(rowlabel ${TAG}_${n}_sort$r)] launches=$(launches)"
    burst DPAD_DOWN 2; sleep 1.2
    for seq in "L1 R1 A" "R1 L1 A" "L1 R1 A A"; do
      [ "$(wg_popup)" != null ] || { log "$lay safety: window closed before '$seq'"; break; }
      wg_keys_win "$seq"; sleep 1.5; lc=$(launches)
      log "$lay safety sorted$r '$seq': launches=$((lc - l0)) popup=$(wg_popup | head -c 12)"
      [ "$lc" = "$l0" ] || { log "FAIL $lay: sorted$r '$seq' launched a game"; return 1; }
      k BUTTON_B 1.2; [ "$(wg_popup)" != null ] || { A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.5; openw $lay ${n}_r || return 1; }
      k BUTTON_R1 1.1; k BUTTON_R1 1.6
    done
    burst DPAD_UP 3; sleep 1.2
  done
  # rapid Back / reopen: B B (to the home), the window again at once, R1 R1 A in one call
  wg_key_win B; sleep 0.3; [ "$(wg_popup)" = null ] || { wg_key_win B; sleep 0.6; }
  A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.2
  if openw $lay ${n}_bo; then wg_keys_win "R1 R1 A"; sleep 1.5; lc=$(launches)
    log "$lay safety back/reopen 'R1 R1 A': launches=$((lc - l0))"
    [ "$lc" = "$l0" ] || { log "FAIL $lay: back/reopen launched a game"; return 1; }
    closewin; fi
  # deliberate Play: a fresh window, its first focus read (Play), one A - exactly one launch recorded
  A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.5
  openw $lay ${n}_pl || return 1
  shot ${n}_play_focus; f=$(rowlabel ${TAG}_${n}_play_focus); log "$lay safety: fresh window focus [$f]"
  k BUTTON_A 2; lc=$(launches)
  log "$lay safety deliberate Play: launches=$((lc - l0)) (want 1)"
  [ "$((lc - l0))" = 1 ] || log "FAIL $lay: deliberate Play recorded $((lc - l0)) launches"
  closewin; log "$lay safety done"; }
storm_layout() {
  lay=$1; n=${lay}_st
  A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 2
  openw $lay $n || return 1
  k BUTTON_R1 1.1; k BUTTON_R1 1.6; shot ${n}_page; log "$lay storm: page focus [$(rowlabel ${TAG}_${n}_page)]"
  for seq in "L1 R1 A" "R1 L1 A" "L1 R1 R1 L1 A" "L1 R1 A B" "A L1 R1 A" "L1 R1 A A" "R1 R1 R1 R1 A"; do
    burst DPAD_DOWN 2; sleep 1.2
    [ "$(wg_popup)" != null ] || { log "$lay storm: window closed before '$seq'"; break; }
    wg_keys_win "$seq"; sleep 1.5; shot ${n}_s; lc=$(launches)
    log "$lay storm '$seq': launches=$lc popup=$(wg_popup | head -c 12) focus [$(rowlabel ${TAG}_${n}_s)]"
    [ "$lc" = 0 ] || { log "FAIL $lay: '$seq' launched a game"; break; }
    # (back to the achievements page for the next burst: B to Info if the burst left it elsewhere, then R1 R1)
    [ "$(wg_popup)" != null ] || { log "$lay storm: '$seq' closed the window (B)"; A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.5; openw $lay ${n}_re || break; }
    k BUTTON_B 1.2; [ "$(wg_popup)" != null ] || { A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.5; openw $lay ${n}_re2 || break; }
    k BUTTON_R1 1.1; k BUTTON_R1 1.6
  done
  # (paced as a thumb presses them - each key checked by winguard, ~0.3 s apart: the A must act on what the turn lit)
  for seq in "L1 R1 A" "R1 L1 A" "L1 R1 A B"; do
    [ "$(wg_popup)" != null ] || break
    o0=$(rowlabel ${TAG}_${n}_s); wg_seq_win "$seq"; sleep 1.5; shot ${n}_p; lc=$(launches)
    log "$lay paced '$seq': launches=$lc focus [$(rowlabel ${TAG}_${n}_p)] (before [$o0])"
    [ "$lc" = 0 ] || { log "FAIL $lay: paced '$seq' launched a game"; break; }
    k BUTTON_B 1.2; [ "$(wg_popup)" != null ] || { A --es voltcap_test_game "${LZ_GAMES:-$BIG}"; sleep 1.5; openw $lay ${n}_rp || break; }
    k BUTTON_R1 1.1; k BUTTON_R1 1.6; shot ${n}_s
  done
  closewin; log "$lay storm done (launches: $(launches))"; }
# (682ar, D391 - owner 24 Nov: "activity inside a game window must prevent the launcher behind it from dimming or going black"):
# the screensaver's idle time set to 40 s for the session (voltcap_test_saveridle, QA app only, nothing saved), a Cover details
# window open, a D-pad press every 5 s inside it for 120 s - the screensaver must not start (VoltcapQA "screensaver on" lines: 0);
# then no presses for 60 s with the window still open - now it must start (the timer itself still works). Shots at 60 / 115 / 175 s.
# Level 1.   sh p2review.sh awake TAG SCALE
awake_walk() {
  n=COVERDETAILS; A --ei voltcap_test_saveridle 40; sleep 1
  A --es voltcap_test_game "${PERF_GAME:-${MIX:-Tetris (World)}}"; sleep 2
  openw COVERDETAILS ${n}_aw || return 1
  logcat -c; t0=$(date +%s); dir=D
  while [ $(( $(date +%s) - t0 )) -lt 120 ]; do
    k $dir 5; [ $dir = D ] && dir=U || dir=D
    el=$(( $(date +%s) - t0 )); for at in 60 115; do [ $el -ge $at ] && [ ! -e $O/aw_$at ] && { touch $O/aw_$at; shot ${n}_aw_$at; }; done
  done
  on1=$(logcat -d -s VoltcapQA | grep -c 'screensaver on'); log "awake: 120 s of presses in the window - screensaver starts: $on1 (want 0)"
  sleep 60; shot ${n}_aw_175
  on2=$(logcat -d -s VoltcapQA | grep -c 'screensaver on'); log "awake: then 60 s without input - screensaver starts: $on2 (want 1)"
  [ "$on1" = 0 ] && [ "$on2" -ge 1 ] && log "PASS awake" || log "FAIL awake"
  A --ei voltcap_test_saveridle 0; sleep 1
  [ "$(wg_popup)" != null ] && { closewin; } ; log "awake walk done (launches: $(launches))"; }
case $MODE in
tracehome)
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
  for lay in $LAYS; do logcat -c; tracehome_layout $lay; done
  log "tracehome done"; echo DONE >> $L;;
r714)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  logcat -c; A --es voltcap_test_artfix "delay=0"; sleep 3
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  MIX=$(pick "achmix n=10 "); BIG=$(pick "achmix n=120 "); [ -n "$BIG" ] || BIG=$(pick "achfix n=120 ")
  log "setup scale=$SC layouts=$LAYS mixed='$MIX' long='$BIG'"
  [ -n "$MIX" ] || log "no fixture game with both earned and locked achievements - Tracklist sides NOT TESTED"
  A --ez voltcap_test_preview false; A --es voltcap_test_layout TILES; sleep 3.5; gate; wg_home
  A --es voltcap_test_pref "widget_positions=shortcuts=0.3000,0.2000"; sleep 2; shot m0_hand
  log "start: widget_positions='$(wpos)'"
  for step in 1 2 3; do rowtile $step || break; shot after_$step; log "after tap $step: widget_positions='$(wpos)'"; done
  log "r714 done"; echo DONE >> $L;;
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
lazywalk)
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
  [ -n "$LZ_GAMES" ] || LZ_GAMES="$(pick "achfix n=0 ")|$(pick "achfix n=1 ")|$MIX|$BIG"
  log "lazywalk games: $LZ_GAMES"
  for lay in $LAYS; do logcat -c; lazy_layout $lay; done
  log "lazywalk done"; echo DONE >> $L;;
cover)
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
  cover_walk
  log "cover done"; echo DONE >> $L;;
storm)
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
  for lay in $LAYS; do logcat -c; storm_layout $lay; done
  log "storm done"; echo DONE >> $L;;
safety)
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
  for lay in $LAYS; do logcat -c; safety_layout $lay; done
  log "safety done (launches: $(launches))"; echo DONE >> $L;;
awake)
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
  awake_walk
  log "awake done"; echo DONE >> $L;;
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
arbisect)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout ARCADE; sleep 4; gate
  B="ar_mode=SHOWCASE arcade_glow=true ar_side_art=true ar_video=true arcade_lines=true ar_motion=true ar_room=CARPET ar_crt=LIGHT"
  ar_variant base $B
  ar_variant noglow arcade_glow=false;        A --es voltcap_test_pref arcade_glow=true
  ar_variant noside ar_side_art=false;        A --es voltcap_test_pref ar_side_art=true
  ar_variant novideo ar_video=false;          A --es voltcap_test_pref ar_video=true
  ar_variant nolines arcade_lines=false;      A --es voltcap_test_pref arcade_lines=true
  ar_variant nomotion ar_motion=false;        A --es voltcap_test_pref ar_motion=true
  ar_variant alloff arcade_glow=false ar_side_art=false ar_video=false arcade_lines=false ar_motion=false
  ar_variant hall $B ar_mode=HALL
  ar_variant base2 $B
  log "arbisect done (launches: $(launches))"; echo DONE >> $L;;
focuspos)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  # 1) saved for real (the QA app's own settings) and kept across a restart
  A --es voltcap_test_layout CLASSIC; sleep 3; gate; logcat -c
  A --es voltcap_test_focuspos LOG; sleep 1; A --es voltcap_test_focuspos 1; sleep 1.5; qa_keep focuspos.txt " focuspos |LAUNCH"
  am force-stop $P; sleep 1.5; qa_back; wg_pass_picker
  A --es voltcap_test_focuspos LOG; sleep 1.2; log "restart check logged"
  A --es voltcap_test_focuspos 0; sleep 1; qa_keep focuspos.txt " focuspos |LAUNCH"
  # 2) previewed browsing on the two layouts that have it
  A --ez voltcap_test_preview true; sleep 1
  for lay in CLASSIC TILES; do fp_layout $lay; qa_keep focuspos.txt " focuspos |LAUNCH"; done
  # 3) the tile hidden elsewhere
  for lay in GRID HERO ARCADE CUBE; do A --es voltcap_test_layout $lay; sleep 2.5; A --es voltcap_test_focuspos LOG; sleep 1; done
  qa_keep focuspos.txt " focuspos |LAUNCH"
  log "focuspos done (launches: $(launches))"; echo DONE >> $L;;
appcat)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -n "$APP_PKG" ] || stop "APP_PKG not set"
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_layout CLASSIC; sleep 3; gate
  A --es voltcap_test_libapp add:$APP_PKG; sleep 2.5
  logcat -c; ac log:$APP_PKG
  ac recomp:$APP_PKG:on; ac kind:$APP_PKG:GAME; ac console:$APP_PKG:N64:on
  # (682aw) an existing collection of your own - made first with a ROM game in it - and the app put in it too (the picker's own
  # toggle); the collections shown (preview only)
  ac "ucoll:-:on"; ac "collnew:Super Mario World:QA Collection"; ac "coll:$APP_PKG:QA Collection"; qa_keep appcat.txt " appcat |LAUNCH|libapp"
  # the START window and its Categories window, as a player reaches them
  wg_home
  ac options:$APP_PKG; sleep 1; wg_dialog; shot AC_options
  wg_key_dlg B; sleep 1.2; wg_home
  ac cats:$APP_PKG; sleep 1; wg_dialog; shot AC_cats_0
  # the D-pad down the list; where it is on Recomps (the dump's focused row), one touch on that row - its tick goes off - and
  # the same tap again - back on (no QA hook call while the window is open: an `am start` takes the focus from it)
  tapped=0
  # (its own counter: shot() runs its own `i` loop - D390's lesson)
  for ci in 1 2 3 4 5 6 7 8; do wg_key_dlg D; sleep 0.6; shot AC_cats_d$ci
    if [ $tapped = 0 ]; then
      xy=$(tr -d '\n' < $O/${TAG}_AC_cats_d$ci.json 2>/dev/null | tr '{' '\n' | grep -F '"focused":true' | grep -F '"kind":"control"' | grep -i '"label":"[^"]*Recomps' | head -1 | sed -n 's/.*"shown":\[\([0-9]*\),\([0-9]*\),\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/p')
      # (the list scrolls to the row it moved onto - the dump taken just after showed it still moving and the tap fell between
      # two rows (KONKR x1.3): once on Recomps, a still second dump, and its bounds)
      [ -n "$xy" ] && { sleep 1.2; shot AC_cats_pre; xy=$(tr -d '\n' < $O/${TAG}_AC_cats_pre.json 2>/dev/null | tr '{' '\n' | grep -F '"focused":true' | grep -F '"kind":"control"' | grep -i '"label":"[^"]*Recomps' | head -1 | sed -n 's/.*"shown":\[\([0-9]*\),\([0-9]*\),\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/p'); }
      # (QaClip's rows are placed inside their own window: the dialog window's top - its "at" - is added for the screen tap)
      wy=$(tr -d '\n' < $O/${TAG}_AC_cats_pre.json 2>/dev/null | grep -o '"at":\[[0-9]*,[0-9]*,[0-9]*,[0-9]*\],"focus":true' | sed -n 's/"at":\[[0-9]*,\([0-9]*\),.*/\1/p'); wy=${wy:-0}
      if [ -n "$xy" ]; then set -- $xy; tx=$(( $1 + $3 / 2 )); ty=$(( $2 + $4 / 2 + wy ))
        wg_tap_dlg $tx $ty; sleep 1.4; shot AC_cats_tap1; wg_tap_dlg $tx $ty; sleep 1.4; shot AC_cats_tap2
        log "touch on the Recomps row at $tx,$ty (tap1 / tap2 shots)"; tapped=1; fi
    fi
  done
  [ $tapped = 1 ] || log "touch NOT TESTED: the Recomps row never had the focus"
  # (682aw) on down to "Collections...", A: its collection picker - a shot - and back
  coll=0
  for cj in $(seq 1 30); do
    if tr -d '\n' < $O/${TAG}_AC_cats_cl.json 2>/dev/null | tr '{' '\n' | grep -F '"focused":true' | grep -F '"kind":"control"' | grep -qi '"label":"Collections'; then coll=1; break; fi
    wg_key_dlg D; sleep 0.5; shot AC_cats_cl
  done
  if [ $coll = 1 ]; then wg_key_dlg A; sleep 1.5; f=$(wg_hash "$(wg_focus)"); shot AC_collpicker; log "Collections... opened (focus $f, dialog $WG_DLG)"
    # (the picker replaces the Categories window - its B goes back to the home)
    [ "$f" = "$WG_DLG" ] || wg_dialog; wg_key_dlg B; sleep 1.2
  else log "Collections... NOT TESTED: never focused"; fi
  # Collections... (its own picker) - shot, then back
  [ "$(wg_hash "$(wg_focus)")" = "$WG_MAIN" ] || { wg_dialog; wg_key_dlg B; sleep 1.2; }
  [ "$(wg_hash "$(wg_focus)")" = "$WG_MAIN" ] || { wg_dialog; wg_key_dlg B; sleep 1.2; }
  # where it is listed now: Recomps, Android games, Nintendo 64 - once in All
  ac log:$APP_PKG
  A --es voltcap_test_category Recomps; sleep 2; shot AC_home_recomps
  A --es voltcap_test_category N64; sleep 2; shot AC_home_n64
  qa_keep appcat.txt " appcat |LAUNCH|libapp"
  # the QA app restarted: kept
  am force-stop $P; sleep 1.5; qa_back; wg_pass_picker
  A --ez voltcap_test_preview true; sleep 1; ac log:$APP_PKG; log "restart check logged"
  # its window's Play, once: the launch of the app's own record (recorded, never started)
  A --es voltcap_test_game "${APP_NAME:-$APP_PKG}"; sleep 2
  # (its START window's Play - the first row - as a player starts an app from START)
  wg_home; ac options:$APP_PKG; sleep 1; wg_dialog; shot AC_play_focus; wg_key_dlg A; sleep 2
  qa_keep appcat.txt " appcat |LAUNCH|libapp"; log "app Play: $(grep 'LAUNCH blocked' $O/appcat.txt | tail -1 | sed 's/.*VoltcapQA: //')"
  [ "$(wg_hash "$(wg_focus)")" = "$WG_DLG" ] && { wg_key_dlg B; sleep 1; }
  # taken off one by one - the app stays (found, same key), each category gone when empty
  ac "coll:$APP_PKG:QA Collection"
  ac console:$APP_PKG:N64:off; ac recomp:$APP_PKG:off; ac kind:$APP_PKG:AUTO
  A --es voltcap_test_libapp remove:$APP_PKG; sleep 2
  qa_keep appcat.txt " appcat |LAUNCH|libapp"
  log "appcat done (launches: $(launches))"; echo DONE >> $L;;
arab)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout ARCADE; sleep 4; gate
  for kv in ar_mode=SHOWCASE arcade_glow=true ar_side_art=true ar_video=true arcade_lines=true ar_motion=true; do A --es voltcap_test_pref "$kv"; sleep 0.3; done
  ar_ab legacy 1; ar_ab new 1; ar_ab legacy 2; ar_ab new 2
  A --es voltcap_test_arattract new
  log "arab done (launches: $(launches))"; echo DONE >> $L;;
fsab)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout ARCADE; sleep 4; gate
  for kv in ar_mode=SHOWCASE arcade_glow=true ar_side_art=true ar_video=true arcade_lines=true ar_motion=true; do A --es voltcap_test_pref "$kv"; sleep 0.3; done
  fs_ab legacy 1; fs_ab new 1; fs_ab legacy 2; fs_ab new 2
  A --es voltcap_test_fitsearch new
  log "fsab done (launches: $(launches))"; echo DONE >> $L;;
isoab)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; sleep 0.5
  for lay in ${4:-ARCADE}; do
    A --es voltcap_test_layout $lay; sleep 4; gate
    [ $lay = ARCADE ] && for kv in ar_mode=SHOWCASE arcade_glow=true ar_side_art=true ar_video=true arcade_lines=true ar_motion=true; do A --es voltcap_test_pref "$kv"; sleep 0.3; done
    logcat -c; A --es voltcap_test_category ALL; sleep 2; A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2; gate
    qa_keep IS_${lay}_cat.txt " category |focus"
    log "$lay: All $(grep -o 'games=[0-9]*' $O/IS_${lay}_cat.txt | tail -1)"
    is_ab legacy 1 $lay; is_ab new 1 $lay; is_ab legacy 2 $lay; is_ab new 2 $lay
  done
  A --es voltcap_test_focussave new
  log "isoab done (launches: $(launches))"; echo DONE >> $L;;
chab)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  # (a home that is otherwise still: Arcade after 682as - the Voltcap row's animated theme scene draws every frame by itself)
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout ARCADE; sleep 4; gate
  ch_ab legacy 1; ch_ab new 1; ch_ab legacy 2; ch_ab new 2
  A --es voltcap_test_chargelight new --es voltcap_test_battery off
  log "chab done (launches: $(launches))"; echo DONE >> $L;;
stilldiff)
  dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep - not woken"
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  [ -d $O/snapbak ] || { mkdir -p $O/snapbak; cp $F/voltcap_test_snapshot* $O/snapbak/ 2>/dev/null; log "owner snapshot files kept"; }
  touch $F/qa_nolaunch
  A --ez voltcap_test_fixture_roms true --ez voltcap_test_nolaunch true; sleep 5; gate
  wg_pass_picker; log "home: $(wg_mark)"
  A --es voltcap_test_snapshot save; sleep 2
  A --ez voltcap_test_preview true; sleep 1
  A --es voltcap_test_artfix "delay=0"; sleep 2
  A --es voltcap_test_fixture "names=real,art=covers,meta=real"; sleep 2
  A --es voltcap_test_scale "$SC"; sleep 2
  A --es voltcap_test_game "${PERF_GAME:-Shadow of the Colossus}"; sleep 2
  A --es voltcap_test_pref "motion_level=NORMAL"; A --es voltcap_test_layout ARCADE; sleep 4; gate
  # (682as: what changes on a still Arcade home - five screenshots, nothing pressed; tools diff them)
  sleep 4; gate; wg_home; for i in 1 2 3 4 5; do screencap -p $O/still_$i.png; sleep 0.2; done
  # (682as: the same a moment after a press - the home is awake for 30 s after input; HomeIdle.calm stills it after)
  k_home R; sleep 0.8; k_home L; sleep 2; for i in 1 2 3 4 5; do screencap -p $O/awake_$i.png; sleep 0.2; done
  log "stilldiff done (launches: $(launches))"; echo DONE >> $L;;
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
          if confirmtext $O/${TAG}_${n}_app_disarmed.json; then log "$lay: FAIL - the confirmation stayed after moving off"
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

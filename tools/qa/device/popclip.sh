# Update 467 (QA rule R26) — popup geometry sweep. Run detached: setsid sh popclip.sh TAG "LAYOUTS" "STYLES" "GAME" &
# Per layout x R1 popup style, on the game GAME (voltcap_test_game): A opens the popup (main, controller); TAPS (touch) on
# Details (page) and More (more), each followed by B; a tap on Play (launch blocked, counted); B closes the popup (closed,
# controller). After each settled step: a screenshot and QaClip's
# geometry dump (a tag written to <files>/qa_clip_req — an intent would close the popups that live in the home screen). The caller saves / restores the owner's settings (voltcap_test_snapshot) around it.
# Preview only, launches blocked. Evaluate with tools/qa/tools/popclip.py. Progress in $OUT/log.txt, DONE at the end.
# (Batch 521b: the isolated QA app only; its activity keeps the original class name.)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; STYLES=$3; GAME=$4
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
# (Update 474: the run manifest — build, device, configuration, start / end — that tools/qa/tools/evidence.py requires
# before any combination of this run can PASS.)
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
ev_start $OUT $TAG "layouts=$LAYS" "styles=$STYLES" "game=$GAME" "content=${QA_CONTENT:-default}" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
. $(dirname $0)/qaguard.sh
# (676d, D334: the target confirmed before anything is pressed - this device, the QA package, installed; logged in the run. And
# the launch-block file first: while it exists the QA app's Back at its root stays in front (QaVariant.harnessActive) - a B
# there had closed it and Android showed the OWNER's Voltcap, the home app.)
pm path $P > /dev/null 2>&1 || { echo "STOP: $P not installed - nothing pressed" >> $OUT/log.txt; echo DONE >> $OUT/log.txt; exit 1; }
touch $F/qa_nolaunch
echo "target: serial=$(getprop ro.serialno) model=$(getprop ro.product.model) package=$P $(dumpsys package $P | grep -m1 versionName | tr -d ' ')" >> $OUT/log.txt
# (523p: a key or a tap only while a QA app window has the focus - a crash dialog's buttons lead into Settings' App info,
# GT78 523. A dialog / other window: the QA app is stopped and the run ends; nothing else is pressed.)
fg() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *Application*) break;; *com.shelf.launcher.qa*) return 0;; *=null*) echo "ABORT no window has the focus (asleep?) - not woken (owner rule)" >> $OUT/log.txt 2>/dev/null; exit 2;; esac; sleep 0.5; done
  echo "ABORT focus=$f" >> $OUT/log.txt 2>/dev/null; am force-stop $P; exit 2; }
# (523w, KONKR GT78 / Android 12 FUSE: a request file made by the shell stays unreadable to the QA app (owner shell, mode
# 660) - every dump and launch check went unanswered, kp523u = 318 x INFRA. Each request file is made world-readable.)
k() { fg; input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
# Is a popup (a Play / Continue action) up? — a probe dump, so B is never pressed once too often (B on the home opens the
# category chooser, and the next layout would start from there).
# The layouts' words for Play and Details (GameWindow.kt WinLook; the same lists as geomcheck.PLAY_WORDS / DETAILS_WORDS).
PLAYW="(Play|Continue|Resume|Start|Start game|Continue game|Play Game|Press Start|Power on|Run|Play it!|Continue!|Rent & Play|Rent & Continue|Rent & Resume)"
DETW="(Details|Full details|Information|Info|Game info|Contents|Status|Sleeve notes|Card text|Turn over|Summary|The story|Type NFO|Catalogue card)"
# (Counted against the home step: a layout whose home screen has its own Play button — Custom — is not "a popup up".)
# jl FILE: the dump one node per line. (675d, emulator run 43786748164: the device's grep missed controls that were in the dump
# - "no Details action" / More step missing / "A opened nothing" with Play, Details and More on screen - on exactly the dumps
# whose one 10-13 KB line also held "Full story on Details ›"; the same files matched on the PC. Short per-node lines keep one
# odd node or the line's length from hiding every other node from the device's regex.)
# (675o, emulator run 43833359452 on 1990deb4: the misses go on with jl - e529b's probe held Play, Details and More (copied
# right after the failed read: probeA / probeB) and e529a's SHELF main held More, yet the device's grep read none of them -
# and again exactly the dumps that hold "Full story on Details ›"; "·" alone never did it. The PC's grep and toybox on
# glibc read the same files. jl now hands grep plain ASCII: the bytes of the round More glyphs are kept apart first
# (⋯ = ~12, • = ~~3 - their own last bytes) and every other non-ASCII byte becomes ~. MOREW is the More key in that form.)
jl() { tr '{' '\n' < "$1" 2>/dev/null | tr '\213\257\242' '123' | tr '\200-\377' '~'; }
MOREW="(More|~12|~~3~~3~~3)"
pc() { jl $1 | grep -oiE '"label":"([^"]*[^A-Za-z])?'"$PLAYW"'","kind":"control"' | wc -l; }
# dw FILE: wait for the app's own dump. (675b, emulator run 43762702001: the software-rendered emulator wrote the dumps after
# the fixed sleep - the probe read no file, so a popup that was up counted as "first A opened nothing" / "no Details action"
# and A was pressed again on it. The fixed sleep stays the minimum; then up to 8 s more for the file, which QaClip writes
# whole (renamed into place). A dump that never comes is logged - the step then fails / stays NOT TESTED as before.)
# (675d: and until the file READS whole - its last byte the JSON's closing brace - not only until it shows a size.)
dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
dw() { i=0; while ! dr "$1" && [ $i -lt 40 ]; do sleep 0.2; i=$((i + 1)); done
  dr "$1" || { [ -s "$1" ] && echo "dump unreadable: ${1##*/}" >> $OUT/log.txt || echo "dump missing: ${1##*/}" >> $OUT/log.txt; }; }
up() { rm -f $F/qa_clip/${TAG}_probe.json; echo "${TAG}_probe" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 0.9; dw $F/qa_clip/${TAG}_probe.json; [ $(pc $F/qa_clip/${TAG}_probe.json) -gt $(pc $F/qa_clip/${TAG}_${n}_home.json) ]; }
# tapat LABEL_RE STEP: tap the middle of the shown part of the first control whose label ends in LABEL_RE (false if none shows).
# (The popup's own control: one that the home step didn't have — Custom's home has a Play button of its own.)
ctl() { jl $2 | grep -oiE '"label":"([^"]*[^A-Za-z])?'"$1"'","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9-]*,[0-9-]*,[0-9]*,[0-9]*\]'; }
tapat() {
  ctl "$1" $F/qa_clip/${TAG}_${n}_home.json > $F/qa_tap_home.txt
  [ "$2" = probe ] && src=$F/qa_clip/${TAG}_probe.json || src=$F/qa_clip/${TAG}_${n}_$2.json
  r=$(ctl "$1" $src | grep -vxF -f $F/qa_tap_home.txt | grep -vE '"shown":\[[0-9-]*,[0-9-]*,0,|"shown":\[[0-9-]*,[0-9-]*,[0-9]*,0\]' | head -1 | sed 's/.*"shown":\[//;s/\]//')
  [ -n "$r" ] || return 1
  fg; set -- $(echo $r | tr ',' ' '); input tap $(( $1 + $3 / 2 )) $(( $2 + $4 / 2 ))
}
# seek LABEL_RE DIR: walk the D-pad DIR (DPAD_RIGHT / DPAD_LEFT) until a key matching LABEL_RE shows in the probe, then tap it;
# sk = the presses it took. (674a: a uniform row that scrolls (673g) keeps Details / More / Play out of view until the D-pad
# brings them in - k673k Crate / Pocket "Details shown but its tapped page is missing", f673l / k673k "Play tap not recorded"
# after the walk to More had scrolled the row to its end.)
seek() { sk=0; while :; do tapat "$1" probe && return 0; [ $sk -ge 7 ] && return 1; k $2 0.45; sk=$((sk + 1)); up; done; }
shot() { screencap -p $OUT/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; dw $F/qa_clip/${TAG}_$1.json; }
# (Owner, 12 Nov: never wake a device - an asleep display stops the run instead.)
dumpsys power | grep -q "mWakefulness=Awake" || { echo "STOP: display asleep - not woken" >> $OUT/log.txt; echo DONE >> $OUT/log.txt; exit 3; }
# (Update 469, D210: a fresh Voltcap opens on "Who's playing?" — its first A picks the profile, so the first combo of every
# run logged "first A opened nothing". The picker is answered with B (keep the profile) first; the app's own mark says so.)
# (Update 470: the mark is only trusted once it carries the running Voltcap's pid — just after an install it still held the
# old process's "HOME" and the first A went to the picker again.)
pid=$(pidof $P); for i in 1 2 3 4 5 6 7 8; do grep -q " $pid\$" $F/voltcap_qa_screen 2>/dev/null && break; sleep 0.8; done
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "profile picker closed with B" >> $OUT/log.txt; done
[ -n "$GAME" ] && { logcat -c; A --es voltcap_test_game "$GAME"; sleep 3; echo "$GAME" > $OUT/game.txt
  # (Update 469: the hook logs which game it found — a run on the wrong game is stopped, not graded.)
  logcat -d -s VoltcapTest | grep -q "game '.*' -> NO MATCH" && { echo "STOP: no game matches '$GAME'" >> $OUT/log.txt; echo DONE >> $OUT/log.txt; exit 1; }
  logcat -d -s VoltcapTest | grep "game '" | tail -1 | sed 's/.*VoltcapTest: //' >> $OUT/log.txt
  ev_note $OUT "game_match=$(logcat -d -s VoltcapTest | grep "game '" | tail -1 | sed "s/.*-> //;s/https[^ ]*//g")"; }
touch $F/qa_nolaunch
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1.5
for lay in $LAYS; do
  # (Asleep = no focused window: every key would end in an ANR. Preview keeps the screen on; this is the backstop.)
  dumpsys power | grep -q "mWakefulness=Awake" || { echo "STOP: display asleep before $lay - not woken" >> $OUT/log.txt; break; }
  # (Update 470: preview asked again before every layout — a Voltcap restart (a crash, a font change) dropped it and the
  # next layouts were SAVED: the KONKR ended on File select, the Odin on Art library after the 469 runs.)
  A --ez voltcap_test_preview true
  A --es voltcap_test_layout $lay; sleep 3.5
  # (676d, D334b, RP Nova n676c13: just after an install the QA app was still on its first-start scan, the layout asked for
  # never came, and the keys meant for it went to whatever was on screen. The app's own dump says which layout it shows
  # ("homeLayout"); asked once more if it is another; still another: this layout is left out (NOT TESTED), nothing pressed.)
  rm -f $F/qa_clip/${TAG}_lay.json; echo "${TAG}_lay" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; dw $F/qa_clip/${TAG}_lay.json
  if jl $F/qa_clip/${TAG}_lay.json | grep -q '"homeLayout"' && ! jl $F/qa_clip/${TAG}_lay.json | grep -q "\"homeLayout\":\"$lay\""; then
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $lay; sleep 5
    rm -f $F/qa_clip/${TAG}_lay.json; echo "${TAG}_lay" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; dw $F/qa_clip/${TAG}_lay.json
    jl $F/qa_clip/${TAG}_lay.json | grep -q "\"homeLayout\":\"$lay\"" || {
      echo "$lay: SKIPPED - the app shows $(jl $F/qa_clip/${TAG}_lay.json | grep -o '"homeLayout":"[A-Z_0-9]*"') after two asks; no key pressed (NOT TESTED)" >> $OUT/log.txt; continue; }
  fi
  # (Update 469: Card binder and Pocket Quest play on A by default (cards_a_plays / pq_a_plays) — their window opens on A only
  # with that option off, so it is switched off for the sweep; the caller's voltcap_test_snapshot restore puts the owner's
  # value back. Before, these combos were "A plays" = NOT TESTED.)
  case $lay in CARDS) A --es voltcap_test_pref "cards_a_plays=false"; sleep 1; echo "$lay: cards_a_plays=false for the sweep" >> $OUT/log.txt;;
    POCKET) A --es voltcap_test_pref "pq_a_plays=false"; sleep 1; echo "$lay: pq_a_plays=false for the sweep" >> $OUT/log.txt;;
    # (Green Core's home is its own menu — GAMES / SYSTEMS / ... — A enters the games first; the first combo logged "first
    # A opened nothing" for it.)
    # (Only when it is on that menu — its "SAVE VAULT" entry in the dump; Green Core keeps the games view it was left on.)
    GREENCORE) rm -f $F/qa_clip/${TAG}_gc.json; echo "${TAG}_gc" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; dw $F/qa_clip/${TAG}_gc.json
      jl $F/qa_clip/${TAG}_gc.json | grep -q '"label":"SAVE VAULT"' && { k BUTTON_A 1.5; echo "$lay: A entered GAMES (its home menu)" >> $OUT/log.txt; };; esac
  for st in $STYLES; do
    n=${lay}_$st
    # (Update 475: the run's game again before every combination — after a combination whose window misbehaved (Blades'
    # "first A opened nothing", k475P1) the home was left on another game / category and every later combination measured
    # other content: popclip.py now grades that INFRA, and this keeps it from happening.)
    [ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 2; }
    logcat -c
    A --es voltcap_test_pref "popup_style_by_layout=$lay=$st"; sleep 1.2
    # (Update 474, D242: the app's own answer that the style is set — a SKIP (style not offered) only counts with it.)
    ev_note $OUT "style_set $n $(logcat -d -s VoltcapTest | grep 'pref popup_style_by_layout' | grep -c "$lay=$st")"
    dumpsys power | grep -q "mWakefulness=Awake" || { echo "STOP: display asleep at $n - not woken" >> $OUT/log.txt; break 2; }
    # (Update 478: "Who's playing?" comes up again after an install / restart, between layouts too — the first A of the
    # first layout picked a profile there and the gate failed "open" (TV / Arcade / Classic, o478C / o478E). Closed with B
    # whenever the app's own mark says PICKER, right before the home shot, and logged.)
    for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "$lay: profile picker closed with B" >> $OUT/log.txt; done
    # (675r, RP Nova x1.3 n675d: Cube's home there is the category cube - "1 game · you're here", an Open key, the hint "Ⓐ Open" -
    # and no game drawn ("hasRow":false). Its A opens the category's games, as its hint says; the next A opens the window. The
    # gate read that as "first A opened nothing". As for Green Core's home menu: only on that screen, A enters the games
    # first and it is logged; on Cube's menu home (x1.0) the game is drawn and nothing changes.)
    if [ $lay = CUBE ]; then rm -f $F/qa_clip/${TAG}_cube.json; echo "${TAG}_cube" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.1; dw $F/qa_clip/${TAG}_cube.json
      if jl $F/qa_clip/${TAG}_cube.json | grep -q '"hasRow":false' && jl $F/qa_clip/${TAG}_cube.json | grep -q '"label":"Open"'; then
        k BUTTON_A 1.8; echo "$n: A entered the category's games (Cube's category cube home, hint: A Open)" >> $OUT/log.txt; fi; fi
    # The home screen before the popup (what "closed" must come back to).
    shot ${n}_home
    # Controller: A opens the popup.
    logcat -c; k BUTTON_A 1.9
    # (A first A that opened nothing is recorded — the gate fails "open" on it — and A is pressed once more to go on.)
    # (675c, emulator run 43780985909: e529a's probe saw no popup while the firstA screenshot taken just after showed it, and
    # the second A then pressed the open window's Play. The failed probe is kept (${n}_probeA) and the app is asked once more
    # 1.5 s later (${n}_probeB, both carry the app's own popupAgeMs and its known windows): up then = "first A opened late"
    # - still a FAIL of the open gate, but no second A on the open window. Not up = "first A opened nothing", as before.)
    up || { cp $F/qa_clip/${TAG}_probe.json $F/qa_clip/${TAG}_${n}_probeA.json 2>/dev/null
      if [ $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') -gt 0 ]; then echo "$n A plays" >> $OUT/log.txt
      elif sleep 1.5 && up; then cp $F/qa_clip/${TAG}_probe.json $F/qa_clip/${TAG}_${n}_probeB.json 2>/dev/null
        echo "$n first A opened late" >> $OUT/log.txt; screencap -p $OUT/${n}_firstA.png
      else cp $F/qa_clip/${TAG}_probe.json $F/qa_clip/${TAG}_${n}_probeB.json 2>/dev/null
        echo "$n first A opened nothing" >> $OUT/log.txt; screencap -p $OUT/${n}_firstA.png
        # (675o: what the device's grep read - Play keys in the probe / the home - to set beside the copied probes.)
        echo "$n read: Play keys probe $(pc $F/qa_clip/${TAG}_probe.json), home $(pc $F/qa_clip/${TAG}_${n}_home.json)" >> $OUT/log.txt
        # (Update 469: a crash left the system's "has stopped" dialog / launcher in front and the next keys went to App info
        # and Android Settings — never press on outside Voltcap.)
        case "$(dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity')" in *com.shelf.launcher*) ;;
          *) echo "STOP: not Voltcap in front after the first A of $n (crash?)" >> $OUT/log.txt; logcat -d -b crash > $OUT/${n}_crash.txt; break 2;; esac
        k BUTTON_A 1.9; fi; }
    shot ${n}_main
    # Touch: taps go to the middle of the part of a control that SHOWS (the dump's "shown" box of the main step).
    # Details opens its page; B goes back.
    # (Update 469: "Details" itself first — Storage's tabs are INFO / DETAILS / …, and the list's "Info" matched INFO, the tab
    # already open: "a tap on Details changed nothing". The other words only where no Details key is there.)
    if tapat "(Details|Full details)" main || tapat "$DETW" main; then sleep 1.4; shot ${n}_page; k BUTTON_B 1.0
    # (674c: the layout's own word for Details too - Record crate's "Sleeve notes", Pocket's "Status" (k674a).)
    elif jl $F/qa_clip/${TAG}_${n}_main.json | grep -qiE '"label":"([^"]*[^A-Za-z])?'"$DETW"'","kind":"control"' && up && seek "$DETW" DPAD_RIGHT; then
      echo "$n Details reached with $sk D-pad presses (the row scrolled)" >> $OUT/log.txt; sleep 1.4; shot ${n}_page; k BUTTON_B 1.0
    else echo "$n no Details action" >> $OUT/log.txt; fi
    # More (where the window has one, or the About text's More): a tap, then B.
    # (Update 529b: the round key's label is "•••" - mlast529 found it missed on 9 of 10 layouts.)
    if jl $F/qa_clip/${TAG}_${n}_main.json | grep -qiE '"label":"[^"]*'"$MOREW"'","kind":"control"'; then
      up || { k BUTTON_A 1.9; up; }
      # (Where the window is NOW — the probe's dump: a window can sit elsewhere after a page or More changed its height.)
      # (673g, owner brief 15 Nov: a key row that doesn't fit scrolls a whole key at a time and MORE is its last key - off
      # screen at first on a narrow window. The D-pad walks right until the row brings it into view, as a player would.)
      mr=0; found=0
      while :; do
        if tapat "$MOREW" probe; then found=1; break; fi
        [ $mr -ge 7 ] && break
        k DPAD_RIGHT 0.45; mr=$((mr + 1)); up
      done
      # (674g: a vertical menu - Record crate at x1.3 - keeps MORE under its visible keys: the D-pad walks down to it.)
      # (674k: the walk right can leave the stack (KONKR k674i / k674k: "More not on screen") - back up to its first key, then down.)
      if [ $found = 0 ]; then for i in 1 2 3 4 5 6; do k DPAD_UP 0.35; done; up
        if seek "$MOREW" DPAD_DOWN; then found=1; mr=$sk; echo "$n More reached walking down the menu" >> $OUT/log.txt; fi; fi
      if [ $found = 1 ]; then [ $mr -gt 0 ] && echo "$n More reached with $mr D-pad presses (the row scrolled)" >> $OUT/log.txt
        sleep 1.3; shot ${n}_more; k BUTTON_B 0.9
      else echo "$n More not on screen" >> $OUT/log.txt; fi
    # (675o: a main dump the device read without a More key is said so - before, the step was silently missing.)
    else echo "$n More: none read in the main dump ($(jl $F/qa_clip/${TAG}_${n}_main.json | grep -c '"kind":"control"') controls read)" >> $OUT/log.txt
    fi
    # A tap on Play must ask for the launch — blocked (qa_nolaunch), so it shows in the log and nothing starts.
    # (Update 489b, D287: only when the running build confirms the block — qaguard.sh; else the tap is skipped.)
    if qa_guard 2>> $OUT/log.txt; then
      up || { k BUTTON_A 1.9; up; }
      logcat -c
      if tapat "$PLAYW" probe || { seek "$PLAYW" DPAD_LEFT && echo "$n Play reached with $sk D-pad presses (the row scrolled)" >> $OUT/log.txt; }; then sleep 1.3
        echo "blocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" > $OUT/${n}_play.txt
        # (Update 469: which game it asked for — the key after "LAUNCH blocked <where>"; popclip.py checks it is the run's game.)
        logcat -d -s VoltcapQA | grep 'LAUNCH blocked' | sed 's/.*LAUNCH blocked [^ ]* \(.*\) popup=.*/key=\1/;s/https[^ ]*//g' >> $OUT/${n}_play.txt
      fi
    fi
    # Controller: B closes the popup.
    up && k BUTTON_B 1.4; up && { k BUTTON_B 1.4; echo "$n needed a second B to close" >> $OUT/log.txt; }
    shot ${n}_closed
    # (Update 529, master sprint Phase 4, POP_EXTRA=1: the shoulder buttons and rapid presses on every combination.
    # L1 / R1 on the open window walk its pages where it has them (r1 / l1 steps); then A B A B at 250 ms must start nothing and
    # leave the home as it was (rapid.txt, after step). Launches stay blocked; B is pressed only while a window is up.)
    if [ -n "$POP_EXTRA" ]; then
      k BUTTON_A 1.6
      if up; then k BUTTON_R1 1.1; shot ${n}_r1; k BUTTON_L1 1.1; shot ${n}_l1; k BUTTON_B 1.0; up && k BUTTON_B 1.2; up && k BUTTON_B 1.2
      # (675o, e529a: "extra: A opened nothing", then the rapid A B A B started a launch - its first A met the window that had
      # opened after all. Asked once more 1.5 s later, as the first A is: up then = "extra: A opened late", closed with B, so
      # the rapid presses start from the home as their check says. Still nothing = "A opened nothing", as before.)
      elif sleep 1.5 && up; then echo "$n extra: A opened late" >> $OUT/log.txt; k BUTTON_B 1.2; up && k BUTTON_B 1.2
      else echo "$n extra: A opened nothing" >> $OUT/log.txt; fi
      logcat -c
      for i in 1 2; do fg; input keyevent KEYCODE_BUTTON_A; sleep 0.25; fg; input keyevent KEYCODE_BUTTON_B; sleep 0.25; done; sleep 1.6
      echo "rapid_launch=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" > $OUT/${n}_rapid.txt
      up && { echo "$n rapid A B A B left a window open" >> $OUT/log.txt; k BUTTON_B 1.2; }
      shot ${n}_after
    fi
    echo "$n done" >> $OUT/log.txt
  done
  # (Once per layout — dumpsys is slow on the Odin: a game must never have started.)
  fg=$(dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity")
  case "$fg" in *com.shelf.launcher*) ;; *) echo "STOP: not Voltcap in front after $lay" >> $OUT/log.txt; break;; esac
done
# (Update 489b, D287: the launch block stays — qa_nolaunch is removed only by the final clean-up with the release build back.
# Switching it off here let a later hand-made tap start a game.)
A --ez voltcap_test_preview false
echo "launch blocked: $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/log.txt
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null; rm -f $OUT/qa_clip/${TAG}_probe.json $OUT/qa_clip/${TAG}_gc.json $OUT/qa_clip/*.part $F/qa_tap_home.txt
ev_end $OUT
echo DONE >> $OUT/log.txt

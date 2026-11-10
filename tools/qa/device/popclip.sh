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
k() { input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
# Is a popup (a Play / Continue action) up? — a probe dump, so B is never pressed once too often (B on the home opens the
# category chooser, and the next layout would start from there).
# The layouts' words for Play and Details (GameWindow.kt WinLook; the same lists as geomcheck.PLAY_WORDS / DETAILS_WORDS).
PLAYW="(Play|Continue|Resume|Start|Start game|Continue game|Play Game|Press Start|Power on|Run|Play it!|Continue!|Rent & Play|Rent & Continue|Rent & Resume)"
DETW="(Details|Full details|Information|Info|Game info|Contents|Status|Sleeve notes|Card text|Turn over|Summary|The story|Type NFO|Catalogue card)"
# (Counted against the home step: a layout whose home screen has its own Play button — Custom — is not "a popup up".)
pc() { grep -oiE '"label":"([^"]*[^A-Za-z])?'"$PLAYW"'","kind":"control"' $1 2>/dev/null | wc -l; }
up() { rm -f $F/qa_clip/${TAG}_probe.json; echo "${TAG}_probe" > $F/qa_clip_req; sleep 0.9; [ $(pc $F/qa_clip/${TAG}_probe.json) -gt $(pc $F/qa_clip/${TAG}_${n}_home.json) ]; }
# tapat LABEL_RE STEP: tap the middle of the shown part of the first control whose label ends in LABEL_RE (false if none shows).
# (The popup's own control: one that the home step didn't have — Custom's home has a Play button of its own.)
ctl() { grep -oiE '"label":"([^"]*[^A-Za-z])?'"$1"'","kind":"control","full":\[[0-9,-]*\],"shown":\[[0-9-]*,[0-9-]*,[0-9]*,[0-9]*\]' $2 2>/dev/null; }
tapat() {
  ctl "$1" $F/qa_clip/${TAG}_${n}_home.json > $F/qa_tap_home.txt
  [ "$2" = probe ] && src=$F/qa_clip/${TAG}_probe.json || src=$F/qa_clip/${TAG}_${n}_$2.json
  r=$(ctl "$1" $src | grep -vxF -f $F/qa_tap_home.txt | grep -vE '"shown":\[[0-9-]*,[0-9-]*,0,|"shown":\[[0-9-]*,[0-9-]*,[0-9]*,0\]' | head -1 | sed 's/.*"shown":\[//;s/\]//')
  [ -n "$r" ] || return 1
  set -- $(echo $r | tr ',' ' '); input tap $(( $1 + $3 / 2 )) $(( $2 + $4 / 2 ))
}
shot() { screencap -p $OUT/$1.png; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.1; }
k WAKEUP 0.5
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
  dumpsys power | grep -q "mWakefulness=Awake" || { k WAKEUP 1.5; echo "woke the screen before $lay" >> $OUT/log.txt; }
  # (Update 470: preview asked again before every layout — a Voltcap restart (a crash, a font change) dropped it and the
  # next layouts were SAVED: the KONKR ended on File select, the Odin on Art library after the 469 runs.)
  A --ez voltcap_test_preview true
  A --es voltcap_test_layout $lay; sleep 3.5
  # (Update 469: Card binder and Pocket Quest play on A by default (cards_a_plays / pq_a_plays) — their window opens on A only
  # with that option off, so it is switched off for the sweep; the caller's voltcap_test_snapshot restore puts the owner's
  # value back. Before, these combos were "A plays" = NOT TESTED.)
  case $lay in CARDS) A --es voltcap_test_pref "cards_a_plays=false"; sleep 1; echo "$lay: cards_a_plays=false for the sweep" >> $OUT/log.txt;;
    POCKET) A --es voltcap_test_pref "pq_a_plays=false"; sleep 1; echo "$lay: pq_a_plays=false for the sweep" >> $OUT/log.txt;;
    # (Green Core's home is its own menu — GAMES / SYSTEMS / ... — A enters the games first; the first combo logged "first
    # A opened nothing" for it.)
    # (Only when it is on that menu — its "SAVE VAULT" entry in the dump; Green Core keeps the games view it was left on.)
    GREENCORE) rm -f $F/qa_clip/${TAG}_gc.json; echo "${TAG}_gc" > $F/qa_clip_req; sleep 1.1
      grep -q '"label":"SAVE VAULT"' $F/qa_clip/${TAG}_gc.json 2>/dev/null && { k BUTTON_A 1.5; echo "$lay: A entered GAMES (its home menu)" >> $OUT/log.txt; };; esac
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
    k WAKEUP 0.3
    # (Update 478: "Who's playing?" comes up again after an install / restart, between layouts too — the first A of the
    # first layout picked a profile there and the gate failed "open" (TV / Arcade / Classic, o478C / o478E). Closed with B
    # whenever the app's own mark says PICKER, right before the home shot, and logged.)
    for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "$lay: profile picker closed with B" >> $OUT/log.txt; done
    # The home screen before the popup (what "closed" must come back to).
    shot ${n}_home
    # Controller: A opens the popup.
    logcat -c; k BUTTON_A 1.9
    # (A first A that opened nothing is recorded — the gate fails "open" on it — and A is pressed once more to go on.)
    up || { if [ $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') -gt 0 ]; then echo "$n A plays" >> $OUT/log.txt
      else echo "$n first A opened nothing" >> $OUT/log.txt; screencap -p $OUT/${n}_firstA.png
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
    else echo "$n no Details action" >> $OUT/log.txt; fi
    # More (where the window has one, or the About text's More): a tap, then B.
    if grep -qiE '"label":"[^"]*(More|⋯)","kind":"control"' $F/qa_clip/${TAG}_${n}_main.json 2>/dev/null; then
      up || { k BUTTON_A 1.9; up; }
      # (Where the window is NOW — the probe's dump: a window can sit elsewhere after a page or More changed its height.)
      if tapat "(More|⋯)" probe; then sleep 1.3; shot ${n}_more; k BUTTON_B 0.9
      else echo "$n More not on screen" >> $OUT/log.txt; fi
    fi
    # A tap on Play must ask for the launch — blocked (qa_nolaunch), so it shows in the log and nothing starts.
    # (Update 489b, D287: only when the running build confirms the block — qaguard.sh; else the tap is skipped.)
    if qa_guard 2>> $OUT/log.txt; then
      up || { k BUTTON_A 1.9; up; }
      logcat -c
      if tapat "$PLAYW" probe; then sleep 1.3
        echo "blocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" > $OUT/${n}_play.txt
        # (Update 469: which game it asked for — the key after "LAUNCH blocked <where>"; popclip.py checks it is the run's game.)
        logcat -d -s VoltcapQA | grep 'LAUNCH blocked' | sed 's/.*LAUNCH blocked [^ ]* \(.*\) popup=.*/key=\1/;s/https[^ ]*//g' >> $OUT/${n}_play.txt
      fi
    fi
    # Controller: B closes the popup.
    up && k BUTTON_B 1.4; up && { k BUTTON_B 1.4; echo "$n needed a second B to close" >> $OUT/log.txt; }
    shot ${n}_closed
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
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null; rm -f $OUT/qa_clip/${TAG}_probe.json $OUT/qa_clip/${TAG}_gc.json $F/qa_tap_home.txt
ev_end $OUT
echo DONE >> $OUT/log.txt

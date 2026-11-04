# Update 467 (QA rule R26) — popup geometry sweep. Run detached: setsid sh popclip.sh TAG "LAYOUTS" "STYLES" "GAME" &
# Per layout x R1 popup style, on the game GAME (voltcap_test_game): A opens the popup (main, controller); TAPS (touch) on
# Details (page) and More (more), each followed by B; a tap on Play (launch blocked, counted); B closes the popup (closed,
# controller). After each settled step: a screenshot and QaClip's
# geometry dump (a tag written to <files>/qa_clip_req — an intent would close the popups that live in the home screen). The caller saves / restores the owner's settings (voltcap_test_snapshot) around it.
# Preview only, launches blocked. Evaluate with tools/qa/tools/popclip.py. Progress in $OUT/log.txt, DONE at the end.
P=com.shelf.launcher; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; STYLES=$3; GAME=$4
OUT=/sdcard/pc/$TAG; rm -rf $OUT; mkdir -p $OUT; rm -f $F/qa_clip/${TAG}_*
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.7}; }
# Is a popup (a Play / Continue action) up? — a probe dump, so B is never pressed once too often (B on the home opens the
# category chooser, and the next layout would start from there).
# The layouts' words for Play and Details (GameWindow.kt WinLook; the same lists as geomcheck.PLAY_WORDS / DETAILS_WORDS).
PLAYW="(Play|Continue|Resume|Start|Start game|Continue game|Play Game|Press Start|Power on|Run|Play it!|Continue!|Rent & Play)"
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
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; echo "profile picker closed with B" >> $OUT/log.txt; done
[ -n "$GAME" ] && { A --es voltcap_test_game "$GAME"; sleep 3; }
touch $F/qa_nolaunch
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 1.5
for lay in $LAYS; do
  # (Asleep = no focused window: every key would end in an ANR. Preview keeps the screen on; this is the backstop.)
  dumpsys power | grep -q "mWakefulness=Awake" || { k WAKEUP 1.5; echo "woke the screen before $lay" >> $OUT/log.txt; }
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
    A --es voltcap_test_pref "popup_style_by_layout=$lay=$st"; sleep 1.2
    k WAKEUP 0.3
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
    if [ -f $F/qa_nolaunch ]; then
      up || { k BUTTON_A 1.9; up; }
      logcat -c
      if tapat "$PLAYW" probe; then sleep 1.3
        echo "blocked=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" > $OUT/${n}_play.txt
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
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false; rm -f $F/qa_nolaunch
echo "launch blocked: $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked')" >> $OUT/log.txt
mkdir -p $OUT/qa_clip; cp $F/qa_clip/${TAG}_* $OUT/qa_clip/ 2>/dev/null; rm -f $OUT/qa_clip/${TAG}_probe.json $OUT/qa_clip/${TAG}_gc.json $F/qa_tap_home.txt
echo DONE >> $OUT/log.txt

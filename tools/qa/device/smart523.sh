#!/system/bin/sh
# Batch 523 (Smart Fill evidence): every layout's OWN popup on one game, per artwork shape, isolated QA app ONLY.
# QA fixture art (voltcap_test_fixture: drawn pictures of a known shape, display-only) or the real art; settings snapshotted
# and restored; preview layouts, launches blocked. One screenshot per layout x shape: <TAG>/<SHAPE>_<LAYOUT>.png
#   sh smart523.sh TAG "SHAPES e.g. real portrait square wide" "GAME" ["LAYOUTS"]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; SHAPES=$2; GAME=$3
LAYS=${4:-"CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"}
O=/sdcard/pc/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
k() { case "$(dumpsys window | grep mCurrentFocus | tail -1)" in *com.shelf.launcher.qa*) input keyevent KEYCODE_$1; sleep ${2:-0.7};; *) echo "ABORT focus" >> $L; echo DONE >> $L; exit 2;; esac; }
PLAYW="(Play|Continue|Resume|Start|Start game|Continue game|Play Game|Press Start|Power on|Run|Play it!|Continue!|Rent & Play|Rent & Continue|Rent & Resume)"
pc() { grep -oiE '"label":"([^"]*[^A-Za-z])?'"$PLAYW"'","kind":"control"' $1 2>/dev/null | wc -l; }
dump() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.0; }
echo "start $(date +%T) real=$(pidof com.shelf.launcher) qa=$(dumpsys package $P | grep -m1 versionName)" > $L
input keyevent KEYCODE_WAKEUP; A --ez voltcap_test_nolaunch true; sleep 3
for i in 1 2 3; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; done
A --es voltcap_test_snapshot save; sleep 2; touch $F/qa_nolaunch
A --ez voltcap_test_preview true; sleep 1
for S in $SHAPES; do
  A --es voltcap_test_fixture "names=real,art=$S,meta=real"; sleep 4
  for lay in $LAYS; do
    dumpsys power | grep -q "mWakefulness=Awake" || input keyevent KEYCODE_WAKEUP
    A --ez voltcap_test_preview true; A --es voltcap_test_layout $lay; sleep 3
    case $lay in CARDS) A --es voltcap_test_pref "cards_a_plays=false"; sleep 1;; POCKET) A --es voltcap_test_pref "pq_a_plays=false"; sleep 1;;
      GREENCORE) dump gc; grep -q '"label":"SAVE VAULT"' $F/qa_clip/${TAG}_gc.json 2>/dev/null && k BUTTON_A 1.5;; esac
    A --es voltcap_test_game "$GAME"; sleep 1.5
    A --es voltcap_test_pref "popup_style_by_layout=$lay=OWN"; sleep 1
    for i in 1 2; do grep -q '^PICKER' $F/voltcap_qa_screen 2>/dev/null || break; k BUTTON_B 1.5; done
    dump home; k BUTTON_A 2.4; dump probe
    [ $(pc $F/qa_clip/${TAG}_probe.json) -gt $(pc $F/qa_clip/${TAG}_home.json) ] || { k BUTTON_A 2.4; dump probe; echo "$S $lay second A" >> $L; }
    screencap -p $O/${S}_$lay.png
    [ $(pc $F/qa_clip/${TAG}_probe.json) -gt $(pc $F/qa_clip/${TAG}_home.json) ] && k BUTTON_B 1.4
    dump probe; [ $(pc $F/qa_clip/${TAG}_probe.json) -gt $(pc $F/qa_clip/${TAG}_home.json) ] && k BUTTON_B 1.4
    echo "$S $lay" >> $L
  done
done
A --es voltcap_test_fixture off; sleep 1; A --ez voltcap_test_preview false; sleep 1
A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $L
echo "launch blocked: $(logcat -d -s VoltcapQA | grep -c 'LAUNCH blocked') real=$(pidof com.shelf.launcher)" >> $L; rm -f $F/qa_clip/${TAG}_*; echo DONE >> $L

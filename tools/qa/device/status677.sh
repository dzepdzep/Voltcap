#!/system/bin/sh
# 677d (owner 16 Nov: RA points squashed in the status bar, the profile picture twice in Console Hub): status-bar / avatar run.
# LEVEL 1 - the isolated QA app only (com.shelf.launcher.qa). Intents, QaClip dumps and screenshots only - no key, no tap.
# Each step: a home layout, a text scale (x1.0 / x1.3) and a set of conditional status pieces (TestHooks voltcap_test_status:
# RA points, notification count, the last game's trophies, pad chips). Graded on the PC by tools/qa/tools/statuscheck.py.
# usage (on the device): sh status677.sh TAG "LAYOUTS" [SCALES]  -> /sdcard/pc/TAG/{manifest.txt, log.txt, *.png, qa_clip/*.json}
# Restores what it changed: test status off, scale off, preview off (the launch block file stays, as every QA script leaves it).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=$2; SCALES=${3:-"off font=1.3,density=1.15"}
OUT=/sdcard/pc/$TAG
. "$(dirname "$0")/evidence.sh" 2>/dev/null || . /data/local/tmp/evidence.sh
mkdir -p $OUT/qa_clip; : > $OUT/log.txt
ev_start $OUT $TAG "layouts=$LAYS" "content=status677" "script_sha256=$(sha256sum "$0" | cut -d' ' -f1)"
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" > /dev/null 2>&1; }
fg() { dumpsys activity activities | grep -m1 "ResumedActivity: " | grep -q " $P/"; }
touch $F/qa_nolaunch
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 3
# (677p: QA_PREF="key=value" - one QA-app setting for this run (ui_font=PIXEL); saved first, put back at the end.)
if [ -n "$QA_PREF" ]; then A --es voltcap_test_snapshot save; sleep 2; A --es voltcap_test_pref "$QA_PREF"; sleep 3; ev_note $OUT "pref=$QA_PREF"; fi
# (RP Classic c677a: after an install the QA app opens on "Who's playing?" - every x1.0 step was taken under it. Kept player:
# B, only while the QA app is in front and the picker is what its own dump shows.)
pick() { echo "${TAG}_pick" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 2
  grep -q "Who.s playing" $F/qa_clip/${TAG}_pick.json 2>/dev/null && fg && { input keyevent KEYCODE_BUTTON_B; sleep 2; echo "picker closed (B)" >> $OUT/log.txt; }; }
pick
for SC in $SCALES; do
  A --es voltcap_test_scale "$SC"; sleep ${SCALE_WAIT:-3}
  # (RP Classic c677b: a scale change recreates the screen; on a slower handheld its first frames were still blank - the
  # script waits until the app's own dump reports a home layout before its first step.)
  for w in 1 2 3 4 5 6 7 8 9 10; do echo "${TAG}_wait" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.5
    grep -q '"homeLayout":"[A-Z]' $F/qa_clip/${TAG}_wait.json 2>/dev/null && break; done
  pick
  S=x13; [ "$SC" = off ] && S=x10
  for lay in $LAYS; do
    A --es voltcap_test_layout $lay; sleep ${LAYOUT_WAIT:-3}
    for combo in "points=12345678" "points=12345678,notes=12" "points=12345678,notes=12,trophy=3,pads=2"; do
      fg || { echo "ABORT: the QA app is not in front - nothing done" >> $OUT/log.txt; break 3; }
      A --es voltcap_test_status "$combo"; sleep 1.5
      C=$(echo "$combo" | sed 's/points=12345678/P/; s/,notes=12/N/; s/,trophy=3/T/; s/,pads=2/D/')
      N=${lay}_${S}_$C
      echo "${TAG}_$N" > $F/qa_clip_req; chmod 666 $F/qa_clip_req; sleep 1.5
      cp $F/qa_clip/${TAG}_$N.json $OUT/qa_clip/ 2>/dev/null || echo "dump missing: $N" >> $OUT/log.txt
      screencap -p $OUT/$N.png; echo "$N done" >> $OUT/log.txt
    done
  done
done
A --es voltcap_test_status off; A --es voltcap_test_scale off; sleep 2; A --ez voltcap_test_preview false; sleep 1
[ -n "$QA_PREF" ] && { A --es voltcap_test_snapshot restore; sleep 3; echo "restore: $(cut -c1-40 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $OUT/log.txt; }
ev_end $OUT
echo DONE >> $OUT/log.txt

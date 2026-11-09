#!/system/bin/sh
# Update 515: Batch 514 retest — isolated Voltcap QA app ONLY (sample profiles, fixture / pushed QA pictures, preview layouts;
# settings snapshotted and restored). sh retest515.sh TAG
#  fx    QA fixture art (wide / portrait / square) on Game shelf and Case stacks (picked pictures as a game's Box: art515.sh).
#  pick  "Who's playing?" and its PIN pad with the navigation bar shown (voltcap_test_navshown): window frames + screenshots.
#  side  Side drawer window; sheet: "Download extra art?"; flow: Case Flow moves (screenrecord for the title).
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt; ART=$F/art515
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
frames() { dumpsys window windows | awk '/Window #[0-9]+ Window\{/{w=($0 ~ /shelf\.launcher\.qa/)} w && /mAttrs=/{a=$0} w && /Frames:/{sub(/^ +/,"");print a" | "$0}' | grep "ty=APPLICATION" | sed 's/ sim=.*frame=/ frame=/;s/last=.*//;s/^ *mAttrs={//'; }
clip() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.5; cp $F/qa_clip/${TAG}_$1.json $O/ 2>/dev/null; }
echo "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher) qa=$(dumpsys package $P | grep -m1 versionName)" >> $L
A --ez voltcap_test_nolaunch true; sleep 3
A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
# (picked pictures — wrap / minimal / flat / 16:9 as a game's Box — are art515.sh: Inspect's own picture is a LABEL scan only)
for M in wide portrait square; do A --es voltcap_test_fixture "names=real,art=$M,meta=none"; sleep 3
  for LY in SHELF STACKS; do A --es voltcap_test_layout $LY --es voltcap_test_game "Shadow of the Colossus"; sleep 4; screencap -p $O/fx_${M}_$LY.png; done
done; A --es voltcap_test_fixture off; sleep 1
# pick
A --es voltcap_test_navshown on; sleep 1
for SP in 3 8:long; do A --es voltcap_test_profiles "$SP"; sleep 2.5; screencap -p $O/pick_$SP.png; echo "pick $SP $(frames)" >> $L; k 97 1; done
A --es voltcap_test_profiles "2:locked"; sleep 2.5; k 22 0.8; k 96 2; screencap -p $O/pin.png; echo "pin $(frames)" >> $L; clip pin
k 97 1; k 97 1; A --es voltcap_test_navshown off; sleep 1
# side
A --es voltcap_test_layout SIDE --es voltcap_test_game "Mario Kart 64"; sleep 3.5; k 96 2; screencap -p $O/side.png; clip side; k 97 1.5
# sheet
A --ez voltcap_test_packsask true; sleep 2.5; screencap -p $O/sheet.png; clip sheet; k 97 1.2
# flow
A --es voltcap_test_layout CASEFLOW --es voltcap_test_game "Mario Kart 64"; sleep 3.5
screenrecord --time-limit 8 $O/flow.mp4 & sleep 1; k 22 1.2; k 22 1.2; k 21 1.2; k 21 1.2; wait
A --ez voltcap_test_preview false; sleep 0.5; k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $L
echo "launches=$(logcat -d -s VoltcapQA | grep -c 'LAUNCH') end $(date +%T) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L

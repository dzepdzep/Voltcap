#!/system/bin/sh
# Update 515: QA pictures (back|spine|front wrap, minimalist sleeve, pure-green stand-in, 16:9 artwork) as two games' "Box"
# picture (voltcap_test_pick; taken off again with "clear") — Shelf / Stacks / Cover Details homes and Inspect at 0|0 and
# 35|8. Isolated QA app ONLY; snapshot restored. sh art515.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt; ART=$F/art515
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
echo "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)" >> $L
A --ez voltcap_test_nolaunch true; sleep 3; A --es voltcap_test_snapshot save; sleep 2; k 97 1
A --ez voltcap_test_preview true; sleep 1
for a in wrap_ps2 minimal flat_green wide_art; do
  for g in Shadow_of_the_Colossus Mario_Kart_64; do A --es voltcap_test_pick "$g|Box|$ART/$a.png"; sleep 1.5; done
  for LY in SHELF STACKS COVERDETAILS; do A --es voltcap_test_layout $LY --es voltcap_test_game "Shadow of the Colossus"; sleep 4; screencap -p $O/${a}_$LY.png; done
  for g in Shadow_of_the_Colossus Mario_Kart_64; do for pose in "0|0" "35|8"; do
    k 97 0.6; A --es voltcap_test_inspect "$g||${pose%|*}|${pose#*|}"; sleep 3; screencap -p "$O/${a}_insp_${g}_$(echo $pose | tr '|' '_').png"
  done; done; k 97 0.8
done
for g in Shadow_of_the_Colossus Mario_Kart_64; do A --es voltcap_test_pick "$g|Box|clear"; sleep 1.5; done
A --ez voltcap_test_preview false; sleep 0.5; k 97 0.8; A --es voltcap_test_snapshot restore; sleep 3
echo "restore: $(cat $F/voltcap_test_snapshot_verify.txt 2>/dev/null)" >> $L
echo "launches=0 end $(date +%T) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L

#!/system/bin/sh
# Update 514: "Download extra art?" (ShelfMenuDialog) on this screen, and "Who's playing?" by touch — isolated Voltcap QA app
# ONLY (the art question's answer is the QA app's own; sample profiles: nothing switched or saved). sh sheet514.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
k() { case "$(focus)" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.8};; *) echo "ABORT $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
clip() { rm -f $F/qa_clip/${TAG}_$1.json; echo "${TAG}_$1" > $F/qa_clip_req; sleep 1.5; cp $F/qa_clip/${TAG}_$1.json $O/ 2>/dev/null; }
A --ez voltcap_test_nolaunch true; sleep 3
A --ez voltcap_test_packsask true; sleep 2.5; screencap -p $O/sheet.png; clip sheet
k 97 1.2; echo "after B: $(focus)" >> $L
# Who's playing by touch: tap the second card -> the sample picker closes (a pick in the preview changes nothing)
A --es voltcap_test_profiles "3:long"; sleep 2.5; screencap -p $O/pick_touch0.png; clip pick
echo DONE >> $L

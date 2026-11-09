#!/system/bin/sh
# Update 515: the intermittent "A B A B left the window open" — isolated Voltcap QA app ONLY (launches blocked, preview layouts).
# Each round: the home in front with the window closed, one key sequence, 2.5 s, then the window state and the app's own key
# trace (VoltcapQA: homekey / winkey / win close again / reopen on press / reopen cancelled / win closed reopen=).
# A window left open is RECORDED first (state=OPEN) and only then closed with one B so the next round starts clean.
#   sh rapid515.sh TAG "LAYOUTS" ROUNDS
# Sequences (SEQS=… to pick):  retro = A B A B each after a focus check (retro513.sh: ~300-500 ms apart)
#             batch = A B A B in one `input` call (keys a few ms apart)   fast = four `input` calls back to back (~60-120 ms)
#             human = 250 ms between keys   bOpening = A, B 120 ms later   bOpen = A, B 900 ms later
#             bClosing = A, B at 900 ms, A again 150 ms later (while it flies back), B 150 ms after that
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LAYS=${2:-COVERDETAILS STACKS SHELF TILES CASEFLOW}; N=${3:-6}
O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
focus() { dumpsys window | grep mCurrentFocus | tail -1; }
guard() { case "$(focus)" in *com.shelf.launcher.qa*) return 0;; *) echo "ABORT focus $(focus)" >> $L; echo DONE >> $L; exit 2;; esac; }
wins() { dumpsys window windows | grep -c "Window #.*com.shelf.launcher.qa/"; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null; }
seq_() { case $1 in
  batch) input keyevent 96 97 96 97;;
  fast) input keyevent 96; input keyevent 97; input keyevent 96; input keyevent 97;;
  human) input keyevent 96; sleep 0.25; input keyevent 97; sleep 0.25; input keyevent 96; sleep 0.25; input keyevent 97;;
  bOpening) input keyevent 96; sleep 0.12; input keyevent 97;;
  bOpen) input keyevent 96; sleep 0.9; input keyevent 97;;
  bClosing) input keyevent 96; sleep 0.9; input keyevent 97; sleep 0.15; input keyevent 96; sleep 0.15; input keyevent 97;;
  retro) for kk in 96 97 96 97; do guard; input keyevent $kk; sleep 0.12; done;;
esac; }
echo "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher) qa=$(dumpsys package $P | grep -m1 versionName)" >> $L
A --ez voltcap_test_nolaunch true; sleep 3; A --ez voltcap_test_preview true; sleep 1
for LY in $LAYS; do
  A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 3.5
  picker && { guard; input keyevent 97; sleep 1.5; echo "$LY picker dismissed before the rounds" >> $L; }
  H=$(wins)
  for S in ${SEQS:-batch fast human bOpening bOpen bClosing retro}; do
    r=""; for i in $(seq 1 $N); do
      # every round from the same home state: the layout and Mario Kart 64 asked for again (a B that fell on the home screen
      # opens the category screen, and the next A would pick a category instead of opening the window)
      A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 2.2
      guard; [ "$(wins)" != "$H" ] && { echo "$LY $S $i not clean before ($(wins) windows)" >> $L; input keyevent 97; sleep 1.5; }
      logcat -c; seq_ $S; sleep 2.5
      W=$(wins); pk=no; picker && pk=yes
      res=C; [ "$W" = "$H" ] || res=O
      { echo "=== $LY $S $i $res windows=$W picker=$pk"; logcat -d -v time | grep -E "VoltcapQA|InputDispatch|WindowManager|InputManager|ViewRootImpl|InputEvent|Input" | grep -v " slow "; } >> $O/trace.txt
      if [ $res = C ]; then r="${r}C"; else r="${r}O"
        echo "$LY $S round $i OPEN windows=$W picker=$pk focus=$(focus)" >> $L
        screencap -p $O/${LY}_${S}_$i.png
        guard; input keyevent 97; sleep 1.5
      fi
    done
    echo "$LY $S $r" >> $L
  done
done
A --ez voltcap_test_preview false; sleep 0.5
echo "launches=$(grep -c 'LAUNCH' $O/trace.txt) end $(date +%T) real=$(pidof com.shelf.launcher)" >> $L; echo DONE >> $L

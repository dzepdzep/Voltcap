#!/system/bin/sh
# Update 514: RP Classic Game shelf frame pacing — isolated Voltcap QA app ONLY. Single D-pad steps (0.7 s apart), a burst, then
# a case pulled out into its window and put back. gfxinfo framestats for each part (fs_<TAG>_<part>.txt) + summary lines.
#   sh perf514.sh TAG [LAYOUT]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; LY=${2:-SHELF}; O=$F/perf514; mkdir -p $O
# (Batch 523: QA_FIX=true = the stand-in library on every start - the hook is not saved, a restarted QA app had none: GT78.)
A() { am start -n $P/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms ${QA_FIX:-false} "$@" >/dev/null 2>&1; }
# (Batch 523: the focus is re-read for up to 3 s first - on the GT78 it reads null for a moment after a start, and the run
# aborted on its first key.)
k() { for w in 1 2 3 4 5 6; do f=$(dumpsys window | grep mCurrentFocus | tail -1); case "$f" in *com.shelf.launcher.qa*) break;; esac; sleep 0.5; done
  case "$f" in *com.shelf.launcher.qa*) input keyevent $1; sleep ${2:-0.7};; *) echo "ABORT $f" >> $O/$TAG.log; exit 2;; esac; }
dump() { dumpsys gfxinfo $P framestats > $O/fs_${TAG}_$1.txt; dumpsys gfxinfo $P | grep -E "Total frames|Janky|percentile" >> $O/$TAG.log; echo "-- $1" >> $O/$TAG.log; }
A --ez voltcap_test_preview true; sleep 0.5
A --es voltcap_test_layout $LY --es voltcap_test_game "Mario Kart 64"; sleep 4; k 59 3
dumpsys gfxinfo $P reset >/dev/null
for i in 1 2 3 4 5 6; do k 22 0.7; done; for i in 1 2 3 4 5 6; do k 21 0.7; done; dump steps
dumpsys gfxinfo $P reset >/dev/null
for i in 1 2 3 4 5 6 7 8; do k 22 0.15; done; sleep 1.5; dump burst
dumpsys gfxinfo $P reset >/dev/null
k 96 2.0; k 97 2.0; k 96 2.0; k 97 2.0; dump window
A --ez voltcap_test_preview false; sleep 0.5; echo DONE >> $O/$TAG.log

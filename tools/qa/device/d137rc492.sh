# Update 492 (D137) — Case stacks: rebuilds per move (QaTrace "recomp" counts: Screen, StkRoot, StkStage, StkItem, StkFront,
# Spine…), frame stats and memory, over identical walks. Preview hooks only (layout / category for this session, nothing
# saved); arrows and B only — B closes the start-up "Who's playing?" keeping the profile. Nothing is launched.
# sh d137rc492.sh TAG [MOVES] [CATEGORY] [RUNS] -> /sdcard/pc/d137rc/TAG.txt (+ TAG_r<N>_fs.txt: raw frame timings)
# (No `dumpsys gfxinfo reset`: on the KONKR it aborted Voltcap in Android 11's HWUI JankTracker — "Impossible
# totalDuration 0", update 492. The frames are sampled with `gfxinfo framestats` every second instead and de-duplicated
# offline by their intended vsync: tools/qa/tools/fsmerge492.py.)
P=com.shelf.launcher; TAG=$1; N=${2:-20}; C=${3:-GBA}; R=${4:-3}; O=/sdcard/pc/d137rc; mkdir -p $O; OUT=$O/$TAG.txt
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP
am force-stop $P; sleep 2; A; sleep 10; input -d 0 keyevent KEYCODE_BUTTON_B; sleep 2
A --ez voltcap_test_preview true --es voltcap_test_layout STACKS; sleep 4
A --es voltcap_test_category "$C"; sleep 5
echo "tag=$TAG moves=$N category=$C runs=$R" > $OUT
r=1
while [ $r -le $R ]; do
  input keyevent KEYCODE_WAKEUP; sleep 1
  logcat -c; FS=$O/${TAG}_r${r}_fs.txt; rm -f $FS /data/local/tmp/fs_stop
  ( while [ ! -f /data/local/tmp/fs_stop ]; do dumpsys gfxinfo $P framestats | sed -n '/---PROFILEDATA---/,/---PROFILEDATA---/p' >> $FS; sleep 1; done ) &
  sleep 0.5
  log -t VoltcapQA -p e "WALK start"
  i=0; while [ $i -lt $N ]; do input -d 0 keyevent KEYCODE_DPAD_RIGHT; sleep 0.4; i=$((i+1)); done
  sleep 1.2; log -t VoltcapQA -p e "WALK end"; touch /data/local/tmp/fs_stop; sleep 1.5
  logcat -d -s VoltcapQA:E | grep " recomp " | sed -E 's/.* recomp [0-9]+ms //' | tr ' ' '\n' | awk -F= -v n=$N 'NF==2{s[$1]+=$2} END{for(k in s) printf "%s=%.1f ", k, s[k]/n; print ""}' > /data/local/tmp/rc.txt
  { echo "run $r per-move: $(cat /data/local/tmp/rc.txt)"
    dumpsys meminfo $P | grep -E 'TOTAL PSS' | tr -s ' '
    logcat -d -s VoltcapQA:E | grep ' locchg ' | sed -E 's/.* locchg //' | sort | uniq -c | sort -rn | head -12; } >> $OUT
  # back to the start, untimed
  i=0; while [ $i -lt $N ]; do input -d 0 keyevent KEYCODE_DPAD_LEFT; i=$((i+1)); done; sleep 2
  r=$((r+1))
done
echo DONE >> $OUT

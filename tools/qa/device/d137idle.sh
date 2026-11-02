# v0.6.66 D137: what a playing gameplay clip costs per layout, idle (your background, clips on). Per layout: 9 s to settle (the
# clip starts after 2 s on a game), then 5 s of QaTrace slow-frame lines -> count, mean total / cmd / gpu (ms).
# usage: sh d137idle.sh TAG "LAYOUTS" [game]   (game: part of a name with a clip, focused first; no gfxinfo reset: D139)
# (D136: the "Who's playing?" profile picker can come up in front after a while — B keeps the current profile, nothing else.)
picker() { uiautomator dump /sdcard/vqa_ui.xml > /dev/null 2>&1; grep -q "Who.s playing" /sdcard/vqa_ui.xml && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; echo "picker dismissed"; }; rm -f /sdcard/vqa_ui.xml; }
cad() { grep cadence $1 | sed 's/.*n=\([0-9]*\) missed=\([0-9]*\).*/\1 \2/' | awk '{n+=$1; m+=$2} END {printf "frames=%d missed=%d", n, m}'; }
A() { am start -n com.shelf.launcher/.MainActivity "$@" > /dev/null 2>&1; }
TAG=$1; LAYS=${2:-"STACKS CLASSIC TWIN_DESK"}; GAME=$3
A --ez voltcap_test_preview true; sleep 1
for lay in $LAYS; do
  picker
  echo "$lay before: $(logcat -d -s VoltcapQA | grep 'clip playing' | tail -1 | cut -c 1-18,50-)"
  logcat -c
  A --es voltcap_test_layout $lay; sleep 2
  [ -n "$GAME" ] && A --es voltcap_test_game "$GAME"
  sleep 3; input keyevent KEYCODE_SCROLL_LOCK; sleep 6
  # (A key with no job, so the home screen isn't idle: after 30 s without input the clip holds its frame — Features.clipIdlePause.)
  picker
  input keyevent KEYCODE_SCROLL_LOCK; sleep 0.5
  log -p e -t VoltcapQA "MARK measure"; sleep 5
  # (The whole log from the switch: "clip playing=… show=…" lines say whether a clip was on during the measured 5 s.)
  logcat -d -s VoltcapQA | sed -n '/MARK measure/,$p' > /sdcard/d137/${TAG}_idle_$lay.txt
  logcat -d -s VoltcapQA | grep "clip playing" | tail -2 | cut -c 1-18,50-
  grep " slow " /sdcard/d137/${TAG}_idle_$lay.txt | sed 's/.* slow \([0-9.]*\) .*cmd=\([0-9.]*\) gpu=\([0-9.]*\).*/\1 \2 \3/' |
    awk -v L=$lay -v T=$TAG '{n++; t+=$1; c+=$2; g+=$3} END {if (n) printf "%s %s slow=%d total=%.1f cmd=%.1f gpu=%.1f\n", T, L, n, t/n, c/n, g/n; else print T, L, "slow=0"}'
  echo "$TAG $lay cadence: $(cad /sdcard/d137/${TAG}_idle_$lay.txt)"
done
A --ez voltcap_test_preview false
echo DONE

# VoltCap QA (update 432) — theme stills on one layout, preview only (nothing saved; `voltcap_test_preview false` at the end puts
# the saved layout / theme back). Needs the test-hooks file.
#   [BG=ANIMATED] [KEYS="R R"] sh themes.sh RUN LAYOUT "THEME1 THEME2 …" [WAIT_S]  → out/RUN_LAYOUT_THEME.png and _b.png (2 s later)
# BG: background style for this session (voltcap_test_bg); KEYS: controller keys after the layout (e.g. to reach a game without art —
# the Classic row's hero shows the game's own art over the scene once it has loaded).
run=$1; lay=$2; list=$3; w=${4:-4}
A="am start -n com.shelf.launcher/.MainActivity"
input keyevent KEYCODE_WAKEUP
$A --ez voltcap_test_preview true --es voltcap_test_layout $lay > /dev/null 2>&1; sleep 4
[ -n "$BG" ] && $A --es voltcap_test_bg $BG > /dev/null 2>&1; sleep 1
[ -n "$KEYS" ] && sh vqa.sh seq "$KEYS" 300 > /dev/null; sleep 2
for T in $list; do
  input keyevent KEYCODE_WAKEUP
  $A --es voltcap_test_theme $T > /dev/null 2>&1; sleep $w
  screencap -p out/${run}_${lay}_$T.png; sleep 2; screencap -p out/${run}_${lay}_${T}_b.png
done
$A --ez voltcap_test_preview false > /dev/null 2>&1
echo "themes $run $lay done"

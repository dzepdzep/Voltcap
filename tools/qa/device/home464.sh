# Update 464: home screen (Pocket Quest, cartridge look; session-only preview), every solid cartridge held at YAW
# (voltcap_test_pose — the same hook on the RC and staging). The game first (its category may bring its own layout), then the
# preview layout. usage: sh home464g.sh OUT YAW NAME...
P=com.shelf.launcher; OUT=$1; Y=$2; shift; shift; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
for N in "$@"; do
  f=$(echo "$N" | tr -c 'A-Za-z0-9\n' '_')
  input keyevent KEYCODE_WAKEUP; A --es voltcap_test_game "$N"; sleep 4
  A --ez voltcap_test_preview true --es voltcap_test_layout POCKET --es voltcap_test_look CARTRIDGE_DISC; sleep 5
  A --es voltcap_test_pose "$Y"; sleep 4
  input keyevent KEYCODE_WAKEUP; sleep 1; screencap -p "$OUT/home_${f}_y$Y.png"
done

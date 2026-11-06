# Update 491 (D137, measurements only) — Case stacks walks A/B on the owner's own settings vs one draw feature off at a time.
# Temporary prefs only (voltcap_test_pref), put back by the snapshot taken first. Preview hooks; arrows and B only.
# sh d137ab491.sh STEPS CATEGORY "MODES" -> /sdcard/pc/k4w/ab_<MODE>_STACKS_<CATEGORY>.txt (k4ab489.sh output per mode)
#   MODES: base (as set), nomirror (stacks_mirror=false), nodepth (stacks_depth=false), base2 (as set, noise check)
P=com.shelf.launcher; N=${1:-25}; C=${2:-GBA}; MODES=${3:-"base nomirror nodepth base2"}; D=$(dirname $0)
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP; A --es voltcap_test_snapshot save; sleep 2
for m in $MODES; do
  A --es voltcap_test_snapshot restore; sleep 1
  case $m in nomirror) A --es voltcap_test_pref "stacks_mirror=false";; nodepth) A --es voltcap_test_pref "stacks_depth=false";; esac
  sleep 1; input keyevent KEYCODE_WAKEUP
  sh $D/k4ab489.sh $m $N STACKS $C > /dev/null 2>&1
  echo "$m: $(grep -E 'Janky frames:|90th|99th' /sdcard/pc/k4w/ab_${m}_STACKS_$C.txt | tr -s ' ' | tr '\n' ' ')"
done
A --es voltcap_test_snapshot restore; sleep 1; am force-stop $P; sleep 1; A; sleep 8; input keyevent KEYCODE_BUTTON_B
echo DONE

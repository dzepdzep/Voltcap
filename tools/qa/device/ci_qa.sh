# Connected Ignition hardware QA (QA app only). PREPARED, NOT YET RUN — runs on the handheld from /data/local/tmp.
# Every step that touches the screen goes through a gate; a failed gate stops the script with no input sent.
# usage:  sh ci_qa.sh pre                      read-only checks before install (real Voltcap must NOT be in front)
#         sh ci_qa.sh base                     record the real Voltcap's identity (run before AND after install; must match)
#         sh ci_qa.sh prefs-save               copy the QA app's own prefs to /sdcard/pc/ci/prefs_backup (restored by prefs-restore)
#         sh ci_qa.sh set INTRO LOOK           QA app only: intro FULL|SHORT|PIXEL|OFF, picker look AURORA|CONSOLE|NEON|OLED|THEME|MIDNIGHT|IGNITION
#         sh ci_qa.sh cold NAME [SECS]         cold start of the QA app, recorded (screenrecord) + launch time + frame stats
#         sh ci_qa.sh keys NAME SECS K...      recorded key run (each key gated)
#         sh ci_qa.sh prefs-restore            put the QA app's prefs back exactly as saved
P=com.shelf.launcher.qa; REAL=com.shelf.launcher; OUT=/sdcard/pc/ci; mkdir -p $OUT
awake()  { dumpsys power | grep -q "mWakefulness=Awake"; }
locked() { dumpsys window | grep -qE "mDreamingLockscreen=true|isStatusBarKeyguard=true|mShowingLockscreen=true|isKeyguardShowing=true"; }
top()    { dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity"; }
gate_pre() {   # before install / launch: awake, unlocked, the real Voltcap not in front
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  top | grep -q "$REAL/" && { echo "STOP: real Voltcap in front ($(top))"; exit 3; }
  return 0; }
gate_qa() {    # before every key: awake, unlocked, the QA app in front
  awake || { echo "STOP: screen asleep"; exit 3; }
  locked && { echo "STOP: locked"; exit 3; }
  t=$(top); echo "$t" | grep -q "$P/" || { echo "STOP: QA app not in front ($t)"; exit 3; }
  echo "$t" | grep -q "$REAL/" && { echo "STOP: real Voltcap in front"; exit 3; }
  return 0; }
key() { gate_qa; input keyevent KEYCODE_$1 || { echo "STOP: input failed"; exit 4; }; }
cmd=$1; shift
case $cmd in
  pre)  gate_pre; echo "PRE-OK $(getprop ro.product.model) $(wm size | tail -1) $(wm density | tail -1)"; top ;;
  base) dumpsys package $REAL | grep -m3 -E "versionName|versionCode|lastUpdateTime"; pm path $REAL; dumpsys package $REAL | grep -m1 -A2 "signatures" ;;
  prefs-save) mkdir -p $OUT/prefs_backup; for f in appearance voltcap_features profiles; do run-as $P cat shared_prefs/$f.xml > $OUT/prefs_backup/$f.xml || exit 5; done; ls -l $OUT/prefs_backup ;;
  set) I=$1; L=$2; am force-stop $P
       run-as $P sh -c "sed -i 's#<string name=\"intro_style\">[A-Z]*</string>#<string name=\"intro_style\">$I</string>#' shared_prefs/appearance.xml && \
         sed -i 's#<string name=\"picker_look\">[A-Z]*</string>#<string name=\"picker_look\">$L</string>#' shared_prefs/voltcap_features.xml" || exit 5
       for kv in "appearance intro_style $I" "voltcap_features picker_look $L"; do set -- $kv
         run-as $P grep -q "name=\"$2\">$3<" shared_prefs/$1.xml || { echo "MISSING: $2 is not stored yet (still the default) - set it once in the QA app's Settings, then re-run"; exit 6; }; done
       run-as $P sh -c "grep -o 'intro_style\">[A-Z]*' shared_prefs/appearance.xml; grep -o 'picker_look\">[A-Z]*' shared_prefs/voltcap_features.xml; grep -o 'startup_mode\">[A-Z_]*' shared_prefs/voltcap_features.xml" ;;
  cold) N=$1; S=${2:-8}; gate_pre; am force-stop $P; sleep 1
        screenrecord --bit-rate 16000000 --time-limit $S $OUT/$N.mp4 & R=$!; sleep 0.6
        am start -W -n $P/com.shelf.launcher.MainActivity | grep -E "TotalTime|WaitTime" > $OUT/$N.launch.txt
        sleep $((S - 2)); dumpsys gfxinfo $P > $OUT/$N.gfx.txt; wait $R 2>/dev/null
        grep -E "Total frames|Janky|50th|90th|95th|99th" $OUT/$N.gfx.txt; cat $OUT/$N.launch.txt; top ;;
  keys) N=$1; S=$2; shift 2; gate_qa; dumpsys gfxinfo $P reset > /dev/null
        screenrecord --bit-rate 16000000 --time-limit $S $OUT/$N.mp4 & R=$!; sleep 0.8
        for x in "$@"; do key $x; sleep 0.3; done; sleep 1.5; kill -INT $R 2>/dev/null; sleep 0.8
        dumpsys gfxinfo $P | grep -E "Total frames|Janky|50th|90th|95th|99th"; top ;;
  prefs-restore) am force-stop $P; for f in appearance voltcap_features profiles; do cat $OUT/prefs_backup/$f.xml | run-as $P sh -c "cat > shared_prefs/$f.xml" || exit 5; done; echo restored ;;
esac

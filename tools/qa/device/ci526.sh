#!/system/bin/sh
# Sprint 526 - Connected Ignition visual QA, the isolated QA app ONLY (com.shelf.launcher.qa). Never wakes / unlocks the handheld,
# never touches system settings; a key only while the QA app is in front (else STOP: nothing more is sent). Sample profiles at
# start-up come from `voltcap_test_startprofiles` (memory only, the QA app's own profiles untouched); the text size from
# `voltcap_test_scale` (Voltcap's drawing only). Recordings through rec.sh in 4 s segments (REC_SEG) - checked with recheck.py.
#   sh ci526.sh cold NAME INTRO LOOK SPEC SCALE SECS [KEYS...]   one recorded cold start; KEYS as "t:KEY" (seconds after start)
#   sh ci526.sh inapp NAME                                       the in-app "Who's playing?" (R1 / Settings path), no intro
#   sh ci526.sh homeback NAME                                    the HOME intent to the QA app itself (what the Home button does
#                                                                when Voltcap is the home app) - never the HOME key, which would
#                                                                bring the real Voltcap forward: no intro replay, no stage
#   sh ci526.sh burst NAME INTRO LOOK SPEC SCALE N               alternative capture: N screenshots back to back from the cold start
#   sh ci526.sh restore                                          intro_style / picker_look back (as ci525.sh)
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=/sdcard/pc/ci526; mkdir -p $O; MODE=$1
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
stop() { echo "STOP $1" >> $O/log.txt; exit 3; }
gate() { dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
  dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
  dumpsys activity activities | grep -m1 topResumedActivity | grep -q "$P/" || stop "QA app not in front"; }
# (KEY or KEY+KEY+... - several keys in one `input` call, ~10 ms apart: rapid controller input during a transition.)
k() { gate; input keyevent $(echo "$1" | tr '+' '\n' | sed 's/^/KEYCODE_/' | tr '\n' ' '); }
. /data/local/tmp/rec.sh; REC_SEG=4; REC_BITRATE=16000000
mark() { cat $F/voltcap_qa_screen 2>/dev/null | cut -d' ' -f1; }
case $MODE in
cold)
  N=$2; I=$3; L=$4; SPEC=$5; SC=$6; S=$7; shift 7
  A --es voltcap_test_intro $I --es voltcap_test_pref "picker_look=$L"; sleep 1.2
  am force-stop $P; sleep 1
  rec_start $O/$N $S
  set -- "$@"; t0=$(date +%s%N | cut -c1-13)
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_startprofiles "$SPEC" --es voltcap_test_scale "$SC" >/dev/null 2>&1
  for kv in "$@"; do t=${kv%%:*}; key=${kv##*:}
    while [ $(( $(date +%s%N | cut -c1-13) - t0 )) -lt $(echo "$t" | awk '{print int($1*1000)}') ]; do sleep 0.02; done
    k $key; echo "$N key $key at ${t}s mark=$(mark)" >> $O/log.txt; done
  while [ $(( $(date +%s%N | cut -c1-13) - t0 )) -lt $(( (S - 1) * 1000 )) ]; do sleep 0.2; done
  screencap -p $O/${N}_end.png
  dumpsys gfxinfo $P framestats > $O/$N.gfx.txt
  rec_stop
  echo "$N intro=$I look=$L spec=$SPEC scale=$SC mark=$(mark) $(grep -E 'Total frames rendered|Janky frames:|50th percentile|90th percentile|99th percentile' $O/$N.gfx.txt | tr -s ' ' | tr '\n' ' ')" >> $O/log.txt ;;
inapp)
  N=$2; gate; rec_start $O/$N 7; A --ez voltcap_who_is_playing true; sleep 3; screencap -p $O/${N}_open.png
  k DPAD_RIGHT; sleep 0.6; k BUTTON_B; sleep 2; screencap -p $O/${N}_end.png; rec_stop
  echo "$N in-app picker mark=$(mark)" >> $O/log.txt ;;
homeback)
  N=$2; gate; rec_start $O/$N 7
  am start -a android.intent.action.MAIN -c android.intent.category.HOME -n $P/com.shelf.launcher.MainActivity >/dev/null 2>&1
  sleep 3; screencap -p $O/${N}_mid.png; sleep 2.5; rec_stop
  echo "$N home intent: mark=$(mark) $(dumpsys activity activities | grep -m1 topResumedActivity | tr -s ' ')" >> $O/log.txt ;;
burst)
  N=$2; I=$3; L=$4; SPEC=$5; SC=$6; C=$7; mkdir -p $O/$N
  A --es voltcap_test_intro $I --es voltcap_test_pref "picker_look=$L"; sleep 1.2; am force-stop $P; sleep 1
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_startprofiles "$SPEC" --es voltcap_test_scale "$SC" >/dev/null 2>&1
  t0=$(date +%s%N | cut -c1-13); i=0
  while [ $i -lt $C ]; do f=$(printf %03d $i); screencap -p $O/$N/$f.png; echo "$f $(( $(date +%s%N | cut -c1-13) - t0 ))" >> $O/$N/times.txt; i=$((i+1)); done
  echo "$N burst $C shots in $(( $(date +%s%N | cut -c1-13) - t0 )) ms mark=$(mark)" >> $O/log.txt ;;
restore)
  L=$(sed 's/.*:"\([A-Z]*\)".*/\1/' /sdcard/pc/ci525/look_before.txt 2>/dev/null); [ -z "$L" ] && L=AURORA
  A --es voltcap_test_intro restore --es voltcap_test_pref "picker_look=$L" --es voltcap_test_startprofiles off --es voltcap_test_scale off
  echo "restored intro + picker_look=$L" >> $O/log.txt ;;
esac

#!/system/bin/sh
# 682bd (owner 25 Nov 21:24 - "verify profile-source notifications, explicit Underclock Off, Heat Guard status, Recent Apps
# restoration and correct profile precedence ... R1 controls"): LEVEL 1, the isolated QA app only, on its SIMULATED tuner
# hardware (TuneSim - in memory; no clock, fan, thermal file or Android setting is touched; nothing is launched).
#   sh tunesim682.sh run TAG     ~3 minutes; log + notification texts + screenshots in /sdcard/pc/TAG
# Keys / taps only while the QA app has the focus (qaguard.sh + front()); its tuner settings are put back at the end (reset).
. "$(dirname "$0")/qaguard.sh" 2>/dev/null || . /data/local/tmp/vqa/qaguard.sh 2>/dev/null || { echo "qaguard.sh missing" >&2; exit 3; }
P=com.shelf.launcher.qa; TAG=$2; O=/sdcard/pc/$TAG; mkdir -p $O; L=$O/log.txt
log() { echo "$(date +%T) $*" >> $L; }
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
S() { A --es voltcap_test_tunesim "$1"; sleep ${2:-1.5}; }
front() { dumpsys window | grep mCurrentFocus | grep -q "$P/"; }
stop() { log "STOP $*"; S reset; S off; [ "$NG" = 0 ] && pm revoke $P android.permission.POST_NOTIFICATIONS 2>/dev/null; echo DONE >> $L; exit 4; }
# The QA app's notification: its title and text as Android holds them.
note() { dumpsys notification --noredact 2>/dev/null | grep -A60 "pkg=$P" | grep -E "android.title=|android.text=|android.bigText=" | head -3 \
  | sed 's/^ *//' | tr '\n' ' ' > $O/note_$1.txt; log "note $1: $(cat $O/note_$1.txt | cut -c1-300)"; }
key() { front || stop "QA app not in front - nothing pressed"; input keyevent $1; sleep ${2:-0.8}; }
shot() { screencap -p $O/$1.png; }
[ "$1" = run ] || { echo "usage: sh tunesim682.sh run TAG"; exit 2; }
dumpsys power | grep -q "mWakefulness=Awake" || stop "asleep"
front || stop "QA app not in front"
# Window guard: the profile picker (after a restart) is passed with ONE B, then the home is confirmed; R1 / B only through it.
F=/sdcard/Android/data/$P/files; WG_LOG=$L; WG_ONABORT=onabort
onabort() { A --es voltcap_test_tunesim reset; A --es voltcap_test_tunesim off; [ -n "$LC" ] && kill $LC 2>/dev/null
  [ "$NG" = 0 ] && pm revoke $P android.permission.POST_NOTIFICATIONS 2>/dev/null; }
. "$(dirname "$0")/winguard.sh" 2>/dev/null || . /data/local/tmp/vqa/winguard.sh
wg_pass_picker; wg_home
# The tuner's own log lines (the QA app's logging fills the shared buffer within minutes): only its tag, kept in the run.
logcat -c; logcat -s VoltcapTdp:I -v time > $O/tdp.log 2>/dev/null & LC=$!
# The QA app's notifications are off on this handheld (Android 13+ asks): on for this run, off again at the end.
NG=$(dumpsys package $P | grep -c "POST_NOTIFICATIONS: granted=true"); [ "$NG" = 0 ] && pm grant $P android.permission.POST_NOTIFICATIONS 2>/dev/null
S on 3
S "heat:on|BALANCED|85"; S "global:AUTO|BALANCED"; S "own:com.qa.tunegame|OFF|BALANCED"; S "lib:com.qa.tunegame|Tune Game"
note 0_home
# 1. the game's own explicit Off with AutoTDP Balanced
S "launch:com.qa.tunegame|Tune Game|com.qa.tunegame" 3
note 1_game_own_off
# 2. hot: the heat guard steps in (temporary, apart from the profile)
S temp:97 1; sleep 26; note 2_hot
# R1 panel with the Heat guard tile, and its hold sheet
wg_key_home R1; sleep 1.5; shot 3_r1
A --es voltcap_test_clip ${TAG}_r1; sleep 2
# 3. cool: the cut comes back
S temp:60 1; sleep 40; note 4_cooled
wg_key_main B; sleep 1
# 4. back in Voltcap: the device-wide profile ("with power" + AutoTDP Balanced = Light), said as Device-wide
S end 2; note 5_device
# 5. the game from Recents (simulated foreground): its own again; an unknown app: nothing
S fg:com.qa.tunegame 3; note 6_recents_game
S fg:com.unknown.app 2; note 7_unknown_app
# 6. the game's own profile removed: precedence falls to the device-wide one while it runs
S clear:com.qa.tunegame 2; note 8_no_own
S end 2
S reset 1; S off 2
note 9_after
[ "$NG" = 0 ] && pm revoke $P android.permission.POST_NOTIFICATIONS 2>/dev/null
kill $LC 2>/dev/null
log "notifications put back: $(dumpsys package $P | grep -c 'POST_NOTIFICATIONS: granted=true') granted (was $NG)"
cp $F/qa_tunesim.log $O/qa_tunesim.log 2>/dev/null; rm -f $F/qa_tunesim.log
log "fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"
echo DONE >> $L

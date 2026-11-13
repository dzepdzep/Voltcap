#!/system/bin/sh
# Update 529, mid-game save sync - ISOLATED QA APP ONLY (com.shelf.launcher.qa). Synthetic saves only, in the QA app's own
# folder (<QA files>/qa_saves); the "upload" is the QA dry run (data/QaSaveDryRun.kt: a checked snapshot into qa_drive/).
# Never wakes / unlocks; no real save, emulator or Voltcap is touched. Steps:
#   1 one save written      2 three saves 1 s apart      3 an 8 MB memory card written in 8 slow chunks (must never go up half)
#   4 Voltcap's process ended in the background (am kill, as low memory does) - then a save: the watcher must come back
#   5 the same with the screen state as is; then off, files removed.
#   sh midsave529.sh TAG
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; S=$F/qa_saves; D=$F/qa_drive; TAG=$1; O=/sdcard/pc/$TAG
rm -rf $O; mkdir -p $O; L=$O/log.txt
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$(date +%T.%N | cut -c1-12) $*" >> $L; }
ms() { date +%s%3N; }
stop() { log "STOP $1"; exit 3; }
dumpsys power | grep -q "mWakefulness=Awake" || stop asleep
dumpsys window | grep -qE "isKeyguardShowing=true|mDreamingLockscreen=true" && stop locked
rm -rf $S $D; mkdir -p $S
logcat -c
A --ez voltcap_test_nolaunch true; sleep 4
grep -q "^PICKER" $F/voltcap_qa_screen 2>/dev/null && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }
A --es voltcap_test_savewatch on; sleep 3
md() { md5sum "$1" 2>/dev/null | cut -c1-12; }
wait_md() { # FILE MD5 SECONDS -> logs when qa_drive has it
  t=0; while [ $t -lt $(( $3 * 4 )) ]; do [ "$(md $D/$1)" = "$2" ] && { log "$1 on qa_drive after $(( $(ms) - T0 )) ms (md5 $2)"; return 0; }; sleep 0.25; t=$((t+1)); done
  log "$1 NOT on qa_drive after $3 s (have $(md $D/$1), want $2)"; return 1; }
# 1
T0=$(ms); dd if=/dev/urandom of=$S/game.srm bs=32768 count=1 2>/dev/null; log "1 write game.srm $(md $S/game.srm)"
wait_md game.srm $(md $S/game.srm) 30
# 2
T0=$(ms); for i in 1 2 3; do dd if=/dev/urandom of=$S/game.srm bs=32768 count=1 2>/dev/null; log "2 write $i $(md $S/game.srm)"; sleep 1; done
wait_md game.srm $(md $S/game.srm) 30
# 3: a memory card written slowly - the half-written card must never be the one "uploaded"
T0=$(ms); rm -f $S/Mcd001.mcr; for i in 0 1 2 3 4 5 6 7; do dd if=/dev/urandom of=$S/Mcd001.mcr bs=1048576 count=1 seek=$i conv=notrunc 2>/dev/null; sleep 0.6; done
log "3 card written $(md $S/Mcd001.mcr)"; wait_md Mcd001.mcr $(md $S/Mcd001.mcr) 40
grep Mcd001 $D/log.txt | wc -l | xargs -I{} sh -c 'echo "$(date +%T) 3 card revisions uploaded: {}" >> '$L
# 4: the process ended in the background (another app in front), then a save
am start -a android.settings.DEVICE_INFO_SETTINGS >/dev/null 2>&1  # (a read-only screen in front; nothing is pressed in it)
sleep 2; dumpsys window | grep mCurrentFocus | tail -1 >> $L
am kill $P; sleep 1; log "4 qa pid after kill: $(pidof $P || echo none)"
T0=$(ms); dd if=/dev/urandom of=$S/game.srm bs=32768 count=1 2>/dev/null; log "4 write (process gone) $(md $S/game.srm)"
wait_md game.srm $(md $S/game.srm) 120
log "4 qa pid now: $(pidof $P || echo none)"
logcat -d -s VoltcapSync > $O/trace.txt; cp $D/log.txt $O/drive_log.txt 2>/dev/null
# off, files removed, back to the QA app (over the info screen) then stopped (the handheld's own home comes back)
A; sleep 2; A --es voltcap_test_savewatch off; sleep 2; rm -rf $S $D; am force-stop $P; log "done fatal=$(logcat -d -b crash | grep -c 'FATAL EXCEPTION')"

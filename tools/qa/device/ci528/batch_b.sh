# Sprint 528 batch b (QA app only): the Intro OFF start - the classic picker's first seconds (owner: 20 % janky frames, 99th 129 ms
# on 12 Nov) measured against the same cold start with one profile (no picker) and against FULL; each recorded, gfxinfo per run.
S=/data/local/tmp/ci528.sh; O=/sdcard/pc/ci528
run1() { dumpsys power | grep -q "mWakefulness=Awake" || { input keyevent KEYCODE_WAKEUP; sleep 1.5; echo "woke the handheld before $2" >> $O/log.txt; }; sh $S "$@"; c=$?; [ $c = 3 ] && { echo "BATCH STOPPED at $2" >> $O/log.txt; exit 3; }; }
mkdir -p $O; echo "batch b start $(date +%T) serial=$(getprop ro.serialno)" >> $O/log.txt
export REC_SEG=10 REC_BITRATE=16000000
run1 cold b01_off_picker5 OFF MIDNIGHT 5:realphoto:active=1 off 9
run1 cold b02_off_single  OFF MIDNIGHT 1:realphoto off 9
run1 cold b03_off_picker5_again OFF MIDNIGHT 5:realphoto:active=1 off 9
sh $S restore
echo "batch b end $(date +%T)" >> $O/log.txt

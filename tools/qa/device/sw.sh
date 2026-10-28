# VoltCap QA — settings page stills: sh sw.sh NAME TAB [SUB] [STEPS]
#   Starts on Voltcap's home (Settings closed). Opens Settings, goes to tab TAB (1 = Home), opens it; SUB > 0 opens the
#   SUB-th entry of a menu page. Then 3×Down per still. Only Down / Left / Right / A on a tab or menu entry are pressed:
#   nothing is changed. Ends with B (and B again for a sub-page): Settings closed, back on the home.
n=$1; idx=$2; sub=${3:-0}; steps=${4:-12}; d=/data/local/tmp/sw/$n; rm -rf $d; mkdir -p $d
k() { input keyevent KEYCODE_$1; sleep ${2:-0.35}; }
k BUTTON_SELECT 2
k DPAD_UP 0.5; k DPAD_UP 0.5; k DPAD_UP 0.5; k DPAD_DOWN 0.6
for i in $(seq 1 24); do k DPAD_LEFT 0.25; done
i=1; while [ $i -lt $idx ]; do k DPAD_RIGHT 0.3; i=$((i+1)); done
sleep 0.5; screencap -p $d/00.png
k BUTTON_A 1.2
if [ $sub -gt 0 ]; then i=0; while [ $i -lt $sub ]; do k DPAD_DOWN 0.4; i=$((i+1)); done; k BUTTON_A 1.5; fi
screencap -p $d/01.png
for i in $(seq 2 $steps); do k DPAD_DOWN; k DPAD_DOWN; k DPAD_DOWN 0.6; screencap -p $d/$(printf %02d $i).png; done
k BUTTON_B 1.2; [ $sub -gt 0 ] && k BUTTON_B 1.2
screencap -p $d/zz.png
cd /data/local/tmp/sw && tar -cf ../sw_$n.tar $n && echo "sw $n ok $(ls $n | wc -l) stills"

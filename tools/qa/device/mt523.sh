#!/system/bin/sh
# Batch 523: real multi-touch on the RP Classic's touchscreen (fts_ts, /dev/input/event2, protocol B) for the Inspect pinch
# test - `input` has one pointer only. Coordinates are SCREEN pixels of the landscape display (1240 x 1080, ROTATION_90);
# MAP=a (default): panel x = 1080 - screen y, panel y = screen x;  MAP=b: panel x = screen y, panel y = 1240 - screen x.
#   sh mt523.sh pinch CX CY R0 R1 [STEPS]   two fingers on a horizontal line through CX,CY from R0 to R1 px apart (R1 > R0 = zoom in)
#   sh mt523.sh drag X0 Y0 X1 Y1 [STEPS]    one finger
#   sh mt523.sh tap X Y
D=/dev/input/event2; MAP=${MAP:-a}
px() { if [ "$MAP" = b ]; then echo $2; else echo $((1080 - $2)); fi; }
py() { if [ "$MAP" = b ]; then echo $((1240 - $1)); else echo $1; fi; }
ev() { sendevent $D $1 $2 $3; }
syn() { ev 0 0 0; }
fin() { ev 3 47 $1; [ -n "$2" ] && ev 3 57 $2; ev 3 48 10; ev 3 53 $(px $3 $4); ev 3 54 $(py $3 $4); }
up() { ev 3 47 $1; ev 3 57 -1; }
case $1 in
  pinch) CX=$2; CY=$3; R0=$4; R1=$5; N=${6:-12}
    fin 0 701 $((CX - R0 / 2)) $CY; fin 1 702 $((CX + R0 / 2)) $CY; ev 1 330 1; syn; sleep 0.05
    i=1; while [ $i -le $N ]; do r=$((R0 + (R1 - R0) * i / N)); fin 0 "" $((CX - r / 2)) $CY; fin 1 "" $((CX + r / 2)) $CY; syn; sleep 0.03; i=$((i + 1)); done
    sleep 0.1; up 0; up 1; ev 1 330 0; syn;;
  drag) X0=$2; Y0=$3; X1=$4; Y1=$5; N=${6:-10}
    fin 0 703 $X0 $Y0; ev 1 330 1; syn
    i=1; while [ $i -le $N ]; do fin 0 "" $((X0 + (X1 - X0) * i / N)) $((Y0 + (Y1 - Y0) * i / N)); syn; sleep 0.03; i=$((i + 1)); done
    up 0; ev 1 330 0; syn;;
  tap) fin 0 704 $2 $3; ev 1 330 1; syn; sleep 0.06; up 0; ev 1 330 0; syn;;
esac

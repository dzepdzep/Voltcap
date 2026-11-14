#!/system/bin/sh
# 529s: pickup529.sh at x1.0 then at x1.3 (font=1.3,density=1.15), each restored before the next. sh pickboth529.sh TAG "LAYOUTS"
D=/data/local/tmp; T=$1; L=$2
sh $D/pickup529.sh run ${T}_10 off "$L"; sh $D/pickup529.sh restore ${T}_10
sh $D/pickup529.sh run ${T}_13 "font=1.3,density=1.15" "$L"; sh $D/pickup529.sh restore ${T}_13
echo ALLDONE >> /sdcard/pc/${T}_13/log.txt

#!/system/bin/sh
# 682ba (owner 25 Nov: the v0.7.0 gallery, full run allowed for this gallery only): the gallery capture in runs of at most 15
# layouts, each inside p2review's own 15-minute deadline (winguard / qaguard unchanged), the QA snapshot put back after each
# run. A run that stops early (deadline, a guard abort) leaves its layouts uncaptured - the next run carries on with the next
# batch; the gallery shows what is missing and why. Level 1, QA app only.
#   sh galchain.sh TAGBASE SCALE NAV "LAYOUTS"     e.g.  sh galchain.sh gr10x off 1 "TIMELINE ARCADE ..."
cd "$(dirname "$0")"
BASE=$1; SC=$2; NAV=$3; LAYS=$4; part=1; n=0; batch=""
go() {
  [ -n "$batch" ] || return 0
  REC_BITRATE=2000000 GAL_NAV=$NAV sh p2review.sh gallery ${BASE}_$part "$SC" "$batch"
  sh p2review.sh restore ${BASE}_$part
  part=$((part + 1)); batch=""; n=0
}
for l in $LAYS; do batch="$batch $l"; n=$((n + 1)); [ $n -ge ${GAL_BATCH:-15} ] && go; done
go
echo "galchain $BASE done $(date +%T)" >> /sdcard/pc/galchain.log

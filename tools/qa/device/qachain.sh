#!/system/bin/sh
# 682bb (owner 25 Nov 15:35 - "finish functional hardware QA ... efficient, targeted batches"): a QA mode over many layouts in runs of
# at most N layouts, each inside its own 15-minute guard (winguard / qaguard unchanged), the QA snapshot put back after each run.
# A run that stops early leaves its layouts ungraded (the graders say INFRA / NOT TESTED); the next run carries on.
#   sh qachain.sh run    TAGBASE SCALE N "LAYOUTS"        p2review.sh run (page walk, focus, opened achievement, 120 list, armed
#                                                         destructive row, Motion Off walk, close / reopen) - N about 4
#   sh qachain.sh pop    TAGBASE SCALE N "LAYOUTS" GAME   pop529.sh run (popclip gate, the layout's own window - OWN) - N about 8;
#                                                         waits for popclip's DONE, then pop529.sh restore
# Level 1, QA app only, synthetic games, launches blocked.
# (682bc, HW-29 krb13: started as  nohup sh -c "..." > /sdcard/pc/x.txt &  every `input` / `am` the run sent failed with
# "Failure calling service input: Failed transaction" - system_server refuses a /sdcard (FUSE) file as the command's stdout. The
# guard saw no window and stopped the run, nothing pressed. Send the chain's output to /data/local/tmp, never to /sdcard.)
case "$(readlink /proc/$$/fd/1 2>/dev/null)" in /storage/*|/sdcard/*|/mnt/*) echo "qachain: stdout is a shared-storage file - input / am calls would fail; send it to /data/local/tmp" >&2; exit 2;; esac
cd "$(dirname "$0")"
KIND=$1; BASE=$2; SC=$3; N=$4; LAYS=$5; GAME=${6:-Shadow of the Colossus}; part=1; n=0; batch=""
T=/data/local/tmp
go() {
  [ -n "$batch" ] || return 0
  tag=${BASE}_$part
  case $KIND in
    run) sh p2review.sh run $tag "$SC" "$batch"; sh p2review.sh restore $tag;;
    pop) sh $T/pop529.sh run $tag "$SC" "$batch" OWN "$GAME"
         i=0; while [ $i -lt 1080 ]; do grep -q '^DONE' /sdcard/pc/$tag/log.txt 2>/dev/null && break; sleep 5; i=$((i + 1)); done
         sleep 3; sh $T/pop529.sh restore $tag;;
  esac
  echo "qachain $KIND $tag done $(date +%T) layouts:$batch" >> /sdcard/pc/qachain.log
  part=$((part + 1)); batch=""; n=0
}
for l in $LAYS; do batch="$batch $l"; n=$((n + 1)); [ $n -ge $N ] && go; done
go
echo "qachain $KIND $BASE all done $(date +%T)" >> /sdcard/pc/qachain.log

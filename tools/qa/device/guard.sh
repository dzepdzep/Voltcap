# VoltCap QA — run manifest + screen guard, sourced by sweep scripts:  . ./guard.sh   (cwd = /data/local/tmp/vqa)
# The manifest out/RUN_manifest.txt is what tools/runcheck.py validates on the host: a sweep that was interrupted, that
# recorded nothing, or that ran on the "Who's playing?" picker can then never read as a pass.
#   man_start RUN        start <epoch>                 (the host rejects recordings older than this: stale evidence)
#   man_expect RUN NAME  expect NAME                   (declared up front: a name that never gets done/skip = INFRA)
#   man_screen RUN NAME  screen NAME HOME|PICKER|UNKNOWN   (picker.sh: Voltcap's blue "Who's playing?" corner)
#   man_done / man_skip RUN NAME [why] / man_end RUN   done NAME | skip NAME why | end <epoch>
# Only writes inside out/ (QA scratch); never touches app data, saves or settings.
man_file() { echo out/$1_manifest.txt; }
man_start() { mkdir -p out; echo "start $(date +%s)" > $(man_file $1); }
man_expect() { echo "expect $2" >> $(man_file $1); }
man_done() { echo "done $2" >> $(man_file $1); }
man_skip() { r=$1; n=$2; shift 2; echo "skip $n $*" >> $(man_file $r); }
man_end() { echo "end $(date +%s)" >> $(man_file $1); }
# picker.sh prints PICKER or HOME from ONE pixel, so a black screen (display off / locked / screensaver / failed capture)
# also reads HOME (seen on the Thor, 28 Oct: a black 1920x1080 capture printed HOME). screen_state therefore also samples four
# more points of the capture picker.sh just wrote: all (near) black = UNKNOWN, never HOME.
screen_state() {
  s=$(sh ./picker.sh 2>/dev/null)
  case $s in PICKER|HOME) ;; *) echo UNKNOWN; return;; esac
  f=/data/local/tmp/pk.raw
  [ -s $f ] || { echo UNKNOWN; return; }
  w=$(od -An -tu4 -N4 $f | tr -d ' '); h=$(od -An -tu4 -j4 -N4 $f | tr -d ' ')
  [ "$w" -gt 0 ] 2>/dev/null && [ "$h" -gt 0 ] 2>/dev/null || { echo UNKNOWN; return; }
  lit=0
  for pt in "$((w / 2)) $((h / 2))" "$((w / 4)) $((h / 4))" "$((w * 3 / 4)) $((h * 3 / 4))" "$((w / 2)) $((h * 4 / 5))" "60 60"; do
    set -- $pt
    set -- $(od -An -tu1 -j $(( 16 + ($2 * w + $1) * 4 )) -N3 $f)
    [ $(( $1 + $2 + $3 )) -gt 36 ] && lit=$((lit + 1))
  done
  [ $lit -eq 0 ] && { echo UNKNOWN; return; }
  echo $s
}
man_screen() { echo "screen $2 $(screen_state)" >> $(man_file $1); }

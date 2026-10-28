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
# screen_state: picker.sh itself prints PICKER / HOME / UNKNOWN (a black or malformed capture is UNKNOWN, never HOME).
screen_state() {
  s=$(sh ./picker.sh 2>/dev/null)
  case $s in PICKER|HOME) echo $s;; *) echo UNKNOWN;; esac
}
man_screen() { echo "screen $2 $(screen_state)" >> $(man_file $1); }

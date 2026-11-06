# Update 489b (D287) — the launch guard every QA script sources before anything that could start a game (a tap or A on
# Play / Continue). `. ./qaguard.sh; qa_guard || exit 1`. It writes the block file and asks the running QA build whether a
# tap on Play would start a game (qa_launch_check -> qa_launch_state, answered within ~1 s). Only "blocked" passes; an
# old build that can't answer, Voltcap not running or "ALLOWED" all fail - the caller skips the tap. Never removes the block:
# only the final device clean-up (with the release build back) does.
QG_F=/sdcard/Android/data/com.shelf.launcher/files
qa_guard() {
  touch $QG_F/qa_nolaunch $QG_F/voltcap_test_hooks; rm -f $QG_F/qa_allow_launch $QG_F/qa_launch_state
  echo 1 > $QG_F/qa_launch_check
  qg_i=0; while [ $qg_i -lt 15 ]; do [ -s $QG_F/qa_launch_state ] && break; sleep 0.2; qg_i=$((qg_i+1)); done
  qg_s=$(cat $QG_F/qa_launch_state 2>/dev/null | tr -d '\r\n')
  if [ "$qg_s" = blocked ]; then return 0; fi
  echo "QA GUARD: launch not confirmed blocked (state='${qg_s:-no answer}') - no Play tap" >&2
  return 1
}

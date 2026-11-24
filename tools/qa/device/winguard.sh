# VoltCap QA - window guard for navigation scripts (owner 18 Nov 14:54: "Never issue R1 / L1 / A without positively confirming
# that the isolated QA app and the expected game window have focus. Abort safely if the profile picker, Android quick
# settings, the home screen or an unexpected window appears. No blind input retries"). Sourced:  . ./winguard.sh
# Needs P (com.shelf.launcher.qa), F (its external files), WG_LOG (log file), TAG; optional WG_ONABORT (a function: put the QA
# app's test state back - intents to the QA app only, never a key).
#   wg_home                 confirm: the QA app's own window has the focus, its screen mark says HOME (not the picker), no game
#                           window open (QaClip) -> WG_MAIN = that window; else ABORT
#   wg_window [GAME]        confirm: a game window of the QA app has the focus (a window of its own, not the main one), QaClip
#                           says a game window is open (GAME: that game) -> WG_WIN = that window, WG_GAME = its game; else ABORT
#   wg_key_home KEY         press KEY only while WG_MAIN still has the focus and the mark still says HOME; else ABORT
#   wg_key_win KEY          press KEY only while WG_WIN still has the focus; else ABORT
#   wg_key_main KEY         press KEY only while WG_MAIN (the QA app's own main window, confirmed by wg_home) still has the
#                           focus - for the app's in-window panels (R1 quick panel open, so the mark may not say HOME); else ABORT
#   wg_pass_picker          only at setup: the picker confirmed (mark PICKER, QA app focused) -> ONE B, then wg_home must hold
# A key is pressed once; nothing is ever retried. Every abort names what was in front.
WG_MAIN=""; WG_WIN=""; WG_GAME=""
wg_focus() { dumpsys window | grep mCurrentFocus | tail -1; }
wg_hash() { echo "$1" | sed -n 's/.*Window{\([0-9a-f]*\) .*/\1/p'; }
wg_mark() { cut -d' ' -f1 $F/voltcap_qa_screen 2>/dev/null; }
wg_abort() {
  echo "$(date +%T) ABORT $* - nothing more pressed, run stopped" >> $WG_LOG
  type "$WG_ONABORT" > /dev/null 2>&1 && $WG_ONABORT
  echo DONE >> $WG_LOG; exit 4; }
wg_what() { case "$1" in
    *NotificationShade*|*StatusBar*|*systemui*) echo "Android quick settings / notifications";;
    *SettingsPanel*|*com.android.settings*) echo "an Android settings panel";;
    *"com.shelf.launcher/"*) echo "the production Voltcap";;
    *"=null"*) echo "nothing (asleep?)";;
    *"$P/"*) echo "the QA app";;
    *) echo "another app: $(echo "$1" | sed 's/.* u0 //; s/}.*//')";;
  esac; }
wg_dr() { [ -s "$1" ] && [ "$(tail -c 1 "$1" 2>/dev/null)" = "}" ]; }
# the open game window's game from a fresh QaClip dump; "null" = none; "" = no dump (unknown - never taken as a yes)
wg_popup() { n=${TAG}_wg; rm -f $F/qa_clip/$n.json; echo "$n" > $F/qa_clip_req; chmod 666 $F/qa_clip_req 2>/dev/null; i=0
  # (682s: up to 15 s - on the emulator the dump came later than 6 s and the walk aborted on "no dump"; a wait, never a key)
  while ! wg_dr $F/qa_clip/$n.json && [ $i -lt 75 ]; do sleep 0.2; i=$((i + 1)); done
  wg_dr $F/qa_clip/$n.json || { echo ""; return; }
  grep -o '"popupGame": *[^,}]*' $F/qa_clip/$n.json | head -1 | sed 's/^"popupGame": *//; s/"//g'; }
wg_type() { dumpsys window windows | grep -A6 "Window{$1 " | grep -o "ty=[A-Z_]*" | head -1; }
wg_home() {
  f=$(wg_focus); case "$f" in *"$P/"*) ;; *) wg_abort "home expected, in front: $(wg_what "$f")";; esac
  [ "$(wg_mark)" = HOME ] || wg_abort "home expected, the QA app shows: $(wg_mark) (picker?)"
  pg=$(wg_popup); [ "$pg" = null ] || wg_abort "home expected, game window: '${pg:-no dump}'"
  h=$(wg_hash "$(wg_focus)"); [ "$(wg_type $h)" = ty=BASE_APPLICATION ] || wg_abort "home expected, focused window is $(wg_type $h)"
  WG_MAIN=$h; }
wg_window() {
  f=$(wg_focus); case "$f" in *"$P/"*) ;; *) wg_abort "game window expected, in front: $(wg_what "$f")";; esac
  pg=$(wg_popup); { [ -n "$pg" ] && [ "$pg" != null ]; } || wg_abort "game window expected, QaClip: '${pg:-no dump}'"
  [ -n "$1" ] && [ "$pg" != "$1" ] && wg_abort "game window of '$1' expected, open: '$pg'"
  h=$(wg_hash "$(wg_focus)"); [ "$(wg_type $h)" = ty=APPLICATION ] || wg_abort "game window expected, focused window is $(wg_type $h)"
  [ -n "$WG_MAIN" ] && [ "$h" = "$WG_MAIN" ] && wg_abort "game window expected, the home window has the focus"
  WG_WIN=$h; WG_GAME=$pg; }
wg_kc() { case $1 in R1) echo BUTTON_R1;; L1) echo BUTTON_L1;; R2) echo BUTTON_R2;; L2) echo BUTTON_L2;; A) echo BUTTON_A;; B) echo BUTTON_B;;
  L) echo DPAD_LEFT;; R) echo DPAD_RIGHT;; U) echo DPAD_UP;; D) echo DPAD_DOWN;; *) echo "";; esac; }
wg_key_win() { k=$(wg_kc $1); [ -n "$k" ] || wg_abort "unknown key $1"
  h=$(wg_hash "$(wg_focus)"); [ -n "$WG_WIN" ] && [ "$h" = "$WG_WIN" ] || wg_abort "before $1: the game window lost the focus to $(wg_what "$(wg_focus)")"
  input keyevent KEYCODE_$k; }
wg_key_home() { k=$(wg_kc $1); [ -n "$k" ] || wg_abort "unknown key $1"
  h=$(wg_hash "$(wg_focus)"); [ -n "$WG_MAIN" ] && [ "$h" = "$WG_MAIN" ] || wg_abort "before $1: the home lost the focus to $(wg_what "$(wg_focus)")"
  [ "$(wg_mark)" = HOME ] || wg_abort "before $1: the QA app shows $(wg_mark)"
  input keyevent KEYCODE_$k; }
wg_key_main() { k=$(wg_kc $1); [ -n "$k" ] || wg_abort "unknown key $1"
  h=$(wg_hash "$(wg_focus)"); [ -n "$WG_MAIN" ] && [ "$h" = "$WG_MAIN" ] || wg_abort "before $1: the QA app's main window lost the focus to $(wg_what "$(wg_focus)")"
  input keyevent KEYCODE_$k; }
wg_pass_picker() {
  [ "$(wg_mark)" = PICKER ] || return 0
  f=$(wg_focus); case "$f" in *"$P/"*) ;; *) wg_abort "picker expected, in front: $(wg_what "$f")";; esac
  input keyevent KEYCODE_BUTTON_B; sleep 2.5
  [ "$(wg_mark)" = HOME ] || wg_abort "after one B on the picker the QA app shows $(wg_mark)"; }
# a timed sequence inside the confirmed game window: "R1 W60 A" - every key checked against WG_WIN
wg_seq_win() { for k in $1; do case $k in W*) sleep $(awk "BEGIN{print ${k#W}/1000}");; *) wg_key_win $k;; esac; done; }
wg_seq_home() { for k in $1; do case $k in W*) sleep $(awk "BEGIN{print ${k#W}/1000}");; *) wg_key_home $k;; esac; done; }

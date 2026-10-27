# VoltCap QA (pass 5) — the 10 category looks in other modes: reduced motion (pb_animate off), a light theme (LOBBY),
# Lite performance mode. Per mode and look: the screen on opening (a) and after 3 steps (b). Settings snapshot around it;
# the theme is put back from the "theme old=" log line; Lite is a preview (nothing saved).
#   sh looksmodes.sh RUN "MOTION LIGHT LITE" "STAGE BLADES …"   → out/RUN_<MODE>_<LOOK>_a.png / _b.png
run=$1; modes=$2; list=$3
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "ABORTED (not on home)"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
logcat -c; $A --es voltcap_test_theme VOLTCAP > /dev/null 2>&1; sleep 1.5
old=$(logcat -d -s VoltcapQA | grep -o "theme old=[A-Z_0-9]*" | tail -1 | cut -d= -f2)
[ -n "$old" ] && $A --es voltcap_test_theme $old > /dev/null 2>&1
echo "theme was $old"
for M in $modes; do
  case $M in
    MOTION) $A --es voltcap_test_pref pb_animate=false > /dev/null 2>&1;;
    LIGHT)  $A --es voltcap_test_theme LOBBY > /dev/null 2>&1;;
    LITE)   logcat -c; $A --es voltcap_test_perf LITE > /dev/null 2>&1; sleep 1
            pold=$(logcat -d -s VoltcapQA | grep -o "perf old=[A-Z]*" | tail -1 | cut -d= -f2); echo "perf was $pold";;
  esac
  sleep 2
  for L in $list; do
    case $L in VAULT|CHANNEL) k=D;; *) k=R;; esac
    $A --es voltcap_test_pref pb_view=$L > /dev/null 2>&1; sleep 1
    sh vqa.sh seq "B" 900 > /dev/null; sleep 1.5
    screencap -p out/${run}_${M}_${L}_a.png
    sh vqa.sh seq "$k $k $k" 400 > /dev/null; sleep 0.25
    screencap -p out/${run}_${M}_${L}_m.png
    sleep 1.4
    screencap -p out/${run}_${M}_${L}_b.png
    sh vqa.sh seq "B" 700 > /dev/null; sleep 1
    echo "$M $L done"
  done
  case $M in
    MOTION) $A --es voltcap_test_pref pb_animate=true > /dev/null 2>&1;;
    LIGHT)  [ -n "$old" ] && $A --es voltcap_test_theme $old > /dev/null 2>&1;;
    LITE)   $A --es voltcap_test_perf ${pold:-AUTO} > /dev/null 2>&1;;
  esac
  sleep 1.5
done
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
[ -n "$old" ] && $A --es voltcap_test_theme $old > /dev/null 2>&1; sleep 1
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview false > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "looksmodes $run done"

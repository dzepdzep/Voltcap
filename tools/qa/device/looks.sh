# VoltCap QA (pass 4) — the category screen's looks, one after the other, from the home screen (B): a full-size screenshot
# on opening (a), after 7 steps (b, a console), and a recording of 4 more steps for frames.py (RUN_<LOOK>.mp4, half size,
# every frame). Voltcap's settings snapshot around it (your own look comes back).
#   sh looks.sh RUN "STAGE BLADES …"   → out/RUN_<LOOK>_a.png / _b.png / .mp4 / _qa.txt
run=$1; list=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "looks ABORTED (not on home)"; exit 1; }
A="am start -n com.shelf.launcher/.MainActivity"
$A --es voltcap_test_snapshot save > /dev/null 2>&1; sleep 1
for L in $list; do
  case $L in VAULT|CHANNEL) k=D; back=U;; DRAWERS) k=R; back=L;; *) k=R; back=L;; esac
  $A --es voltcap_test_pref pb_view=$L > /dev/null 2>&1; sleep 1
  logcat -c
  sh vqa.sh seq "B" 900 > /dev/null; sleep 1.5
  screencap -p out/${run}_${L}_a.png
  sh vqa.sh perf_reset > /dev/null
  sh vqa.sh seq "$k $k $k $k $k $k $k" 380 > /dev/null; sleep 1.6
  dumpsys gfxinfo com.shelf.launcher | sed -n '/Stats since/,/GPU HISTOGRAM/p' > out/${run}_${L}_summary.txt
  screencap -p out/${run}_${L}_b.png
  (nohup screenrecord --size 960x540 --time-limit 5 out/${run}_$L.mp4 > /dev/null 2>&1 &)
  sleep 0.8; sh vqa.sh seq "$k $k $back $back" 700 > /dev/null; sleep 1.6
  logcat -d -s VoltcapQA > out/${run}_${L}_qa.txt
  # (Back towards the start of the list: All games / Favorites / Continue playing — the symbolic objects.)
  sh vqa.sh seq "$back $back $back $back $back $back $back $back $back $back $back $back" 250 > /dev/null; sleep 1.5
  screencap -p out/${run}_${L}_c.png
  sh vqa.sh seq "B" 700 > /dev/null; sleep 1
  echo "$L done"
done
$A --es voltcap_test_snapshot restore > /dev/null 2>&1; sleep 1
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200; echo "looks $run done"

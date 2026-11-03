# Update 462: photo-made shells on / off — the same four Inspect pieces after a cold start; PSS and the frame cadence.
P=com.shelf.launcher; R=/sdcard/perf462.txt; : > $R
for mode in false true false true; do
  am start -n $P/.MainActivity --es voltcap_test_pref "photo_shells=$mode" >/dev/null; sleep 2
  am force-stop $P; sleep 1; input keyevent 224; am start -n $P/.MainActivity >/dev/null; sleep 8; input keyevent 97; sleep 2
  logcat -c
  for s in "Fool|gb|0|0" "Silver_Version|gbc_dual~m:pkmn_silver|0|0" "Prism_Version|gbc|0|0" "Emerald_Kaizo|gba|0|0"; do
    am start -n $P/.MainActivity --es voltcap_test_inspect "$s" >/dev/null; sleep 10; input keyevent 97; sleep 1; input keyevent 97; sleep 1
  done
  pss=$(dumpsys meminfo $P | grep "TOTAL PSS" | awk '{print $3}')
  miss=$(logcat -d -s VoltcapQA | grep cadence | sed 's/.*missed=\([0-9]*\).*/\1/' | awk '{s+=$1} END {print s}')
  n=$(logcat -d -s VoltcapQA | grep cadence | sed 's/.*n=\([0-9]*\) .*/\1/' | awk '{s+=$1} END {print s}')
  echo "photo_shells=$mode PSS_kB=$pss frames=$n missed=$miss" >> $R
done
am start -n $P/.MainActivity --es voltcap_test_pref "photo_shells=true" >/dev/null
echo done >> $R

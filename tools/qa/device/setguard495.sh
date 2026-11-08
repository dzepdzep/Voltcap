# Update 495 (D293) — after a QA run: which settings changed, and whether any were SAVED (they must only be "held" in a
# preview session, except snapshot restore / voltcap_test_pref). Reads logcat only; changes nothing.
# sh setguard495.sh  -> prints the setting lines; exit 1 when an unexpected SAVED change is found
L=$(logcat -d -s VoltcapQA:E | grep ' setting ')
echo "$L" | sed -E 's/.* setting //' | sort | uniq -c
echo "$L" | grep -q ' SAVED' && { echo "SAVED changes found (check they were intended: snapshot restore / test_pref)"; exit 1; }
echo "no saved setting changes"

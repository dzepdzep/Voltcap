# Update 451: keep / put back / CHECK the home screen's own state around a test (needs the voltcap_test_hooks file).
# usage: sh viewstate.sh save      → saves settings (Features), category order / hidden, selected category, focused game, profile
#        sh viewstate.sh restore   → puts them back and prints the app's own check; exit 0 only on "OK" (the profile is checked, never switched)
#        sh viewstate.sh show      → the saved view state
# Never ends a test on an empty category: Voltcap refuses one (voltcap_test_category leaves it as it was) and restore only goes back.
P=com.shelf.launcher; D=/sdcard/Android/data/$P/files
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
case "$1" in
  save)
    rm -f $D/voltcap_test_snapshot_view.json
    A --es voltcap_test_snapshot save; sleep 2
    [ -s $D/voltcap_test_snapshot_view.json ] || { echo "FAIL no view state saved (hooks file? old build?)"; exit 1; }
    echo "SAVED $(cat $D/voltcap_test_snapshot_view.json)";;
  restore)
    [ -s $D/voltcap_test_snapshot_view.json ] || { echo "FAIL nothing saved"; exit 1; }
    rm -f $D/voltcap_test_snapshot_verify.txt
    A --es voltcap_test_snapshot restore
    i=0; while [ $i -lt 20 ] && [ ! -s $D/voltcap_test_snapshot_verify.txt ]; do sleep 0.5; i=$((i + 1)); done
    r=$(cat $D/voltcap_test_snapshot_verify.txt 2>/dev/null)
    echo "${r:-FAIL no answer from Voltcap}"
    case "$r" in OK*) exit 0;; *) exit 1;; esac;;
  show) cat $D/voltcap_test_snapshot_view.json; echo;;
  *) echo "usage: viewstate.sh save|restore|show"; exit 2;;
esac

#!/system/bin/sh
# Update 511: which calm-gated effect draws 10-30 s after an input? Same as idle511.sh, with the QA battery fixture
# (voltcap_test_battery, this process only, nothing saved) set to "not charging" for the run, then back to real.
P=com.shelf.launcher.qa
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_battery "100|false" >/dev/null 2>&1; sleep 1
sh /data/local/tmp/idle511.sh nocharge 15 $*
am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_battery off >/dev/null 2>&1

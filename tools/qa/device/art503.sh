#!/system/bin/sh
# Update 503: the QA app's own fixture pictures (covers, logos, badges) for --es voltcap_test_artfix. QA app only.
# Usage (from the PC): adb push qa_art /sdcard/Android/data/com.shelf.launcher.qa/files/  then  sh art503.sh
A=/sdcard/Android/data/com.shelf.launcher.qa/files/qa_art
ls $A | wc -l
am start -n com.shelf.launcher.qa/com.shelf.launcher.MainActivity --ez voltcap_test_fixture_roms true --es voltcap_test_artfix "delay=${1:-800}" >/dev/null

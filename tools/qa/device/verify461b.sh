# verify461b.sh — Crystal Kaizo (hack matched to Crystal) no longer wears Crystal's label; Fool's Gold printed label close-up; Silver same size.
P=com.shelf.launcher; O=/sdcard/q461w; rm -rf $O; mkdir -p $O
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input keyevent KEYCODE_$1; sleep ${2:-0.8}; }
input keyevent 224; A --ez voltcap_test_nolaunch true; sleep 1
for spec in "Pokemon_Crystal_Kaizo|gbc|0|0" "Pokemon_Crystal_Version|gbc|0|0" "Pokemon_Fool|gb|0|0" "Pokemon_Silver|gb|0|0" "Pokemon_Prism|gb|0|0" "Pokemon_Joel|gbc|0|0"; do
  input keyevent 224; A --es voltcap_test_inspect "$spec"; sleep 7
  screencap -p $O/label_$(echo $spec | cut -d'|' -f1-2 | tr '|' '_').png; k BUTTON_B 1.2; k BUTTON_B 1.2
done
echo DONE > $O/DONE

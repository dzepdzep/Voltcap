# popscan.sh TAG [LAYOUTS] — update 451 popup clutter audit: each layout (preview only, nothing saved) with its CURRENT popup style
# (the owner's own choice; never changed here), A opens the window (launches blocked), a settled still, B closes. Main display only.
P=com.shelf.launcher; TAG=$1; OUT=/sdcard/ps/$TAG; rm -rf $OUT; mkdir -p $OUT
LAYS=${2:-"CLASSIC TILES HERO GRID TV DASH LIVE SIDE CROSS SPOT BAR SHELF HUB RESUME CHAN CIRCUIT LIBRARY STACKS TIMELINE ARCADE CONSOLES DISCS HERO_TILES TWIN_DESK TWIN_ICONS TWIN_SHOWCASE CRATE CARDS CUSTOM POCKET_CLASSIC POCKET_RIBBON GREENCORE POCKET CUBE MEMCARD CASEFLOW BOX STORAGE FILESELECT ORBIT AIRY NIGHT BLADES BUBBLES CAMERA PANELS MAGAZINE SHOP TERMINAL CINEMA ARCTIC COVERDETAILS ARTLIB"}
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
picker() { uiautomator dump /sdcard/vqa_ui.xml > /dev/null 2>&1; grep -q "Who.s playing" /sdcard/vqa_ui.xml && { input keyevent KEYCODE_BUTTON_B; sleep 1.5; }; rm -f /sdcard/vqa_ui.xml; }
W() { dumpsys window | grep mCurrentFocus | grep -o "Window{[0-9a-f]*" ; }
A --ez voltcap_test_preview true; A --ez voltcap_test_nolaunch true; sleep 3
for L in $LAYS; do
  A --es voltcap_test_layout $L; sleep 3.5; picker
  screencap -p $OUT/${L}_0home.png
  H=$(W); input keyevent KEYCODE_BUTTON_A; sleep 1.6
  [ "$(W)" = "$H" ] && { input keyevent KEYCODE_BUTTON_A; sleep 1.6; echo "$L: A again" >> $OUT/log.txt; }
  screencap -p $OUT/${L}_1open.png
  input keyevent KEYCODE_BUTTON_B; sleep 1.2
  [ "$(W)" != "$H" ] && { input keyevent KEYCODE_BUTTON_B; sleep 1; echo "$L: B again" >> $OUT/log.txt; }
done
A --ez voltcap_test_nolaunch false; A --ez voltcap_test_preview false
logcat -d -s VoltcapQA | grep -c "LAUNCH blocked" >> $OUT/log.txt
echo DONE >> $OUT/log.txt

# Update 493 (3D media QA on the staging build, Thor) — Inspect (scan + silver material + generated / custom shells), home
# poses and wobble, through the preview hooks only (nothing launched, saved or changed). Save Vault is done by the caller
# (R1 tap) with `pose` afterwards. Screens to /sdcard/pc/q3d493/<part>/.
P=com.shelf.launcher; D=$(dirname $0); O=/sdcard/pc/q3d493; mkdir -p $O
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
input keyevent KEYCODE_WAKEUP; sleep 1
sh $D/insp492.sh q3d493/scan "Silver" "30 60 96 105 150 200 250 300" 0 media
sh $D/insp492.sh q3d493/silver "Silver" "30 60 96 105 150 200 250 300" 0 "gbc_dual~m:pkmn_silver"
sh $D/insp492.sh q3d493/silver_up "Silver" "30 96 150 250" 30 "gbc_dual~m:pkmn_silver"
sh $D/insp492.sh q3d493/silver_dn "Silver" "30 96 150 250" -35 "gbc_dual~m:pkmn_silver"
sh $D/insp492.sh q3d493/packgb "Silver" "35 96 150 250" 0 "pack:gb"
sh $D/insp492.sh q3d493/ccgbc "Silver" "35 96 150 250" 0 "cc:GBC"
sh $D/insp492.sh q3d493/ccgba "Advance" "35 96 150 250" 0 "cc:GBA"
input -d 0 keyevent KEYCODE_BUTTON_B; sleep 1.5; input -d 0 keyevent KEYCODE_BUTTON_B; sleep 1.5
# home: Classic, cartridge look, GBC games
A --ez voltcap_test_preview true --es voltcap_test_layout CLASSIC; sleep 3
A --es voltcap_test_category GBC; sleep 3
A --ez voltcap_test_preview true --es voltcap_test_look CARTRIDGE_DISC; sleep 4
mkdir -p $O/home
for p in 30 50 70 150; do A --es voltcap_test_pose "$p"; sleep 2; screencap -p $O/home/pose$p.png; done
A --es voltcap_test_pose off; sleep 1.5
for k in 1 2 3; do screencap -p $O/home/wobble$k.png; sleep 0.35; done
echo DONE > $O/log.txt

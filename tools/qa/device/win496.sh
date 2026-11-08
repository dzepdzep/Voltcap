# Update 496 — screenshots / logs only (no A presses, no game, no sync): layout-aware windows phase 3 (Settings + Save Vault
# dry view), D293 preview paths, the achievements overlay fixture, the charging light's recomposition counts.
# sh win496.sh TAG MODE [ARG]   -> /sdcard/pc/TAG/*.png + log.txt
#   win LABEL     Settings (Home, Game look, Theme) + Save Vault (dry: no search, no sync, nothing written) -> LABEL_*.png
#   d293          previews every path (layouts, look, theme, bg, Settings open/close), ends the preview, prints setguard
#   ovl           the overlay fixture: summary, open panel at text 1.0 / 1.3 / 1.6, narrow 330 dp
#   batt          the charging light: counts per second while charging / level changes / full / Settings over it
P=com.shelf.launcher; TAG=$1; D=0; OUT=/sdcard/pc/$TAG; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input -d $D keyevent $1; sleep ${2:-0.5}; }
shot() { screencap -d $D -p $OUT/$1.png 2>/dev/null || screencap -p $OUT/$1.png; echo "$1 $(date +%T)" >> $OUT/log.txt; }
recomp() { logcat -d -s VoltcapQA:E | grep ' recomp ' | tail -${1:-4} | sed -E 's/^.* recomp //'; }
input keyevent KEYCODE_WAKEUP
case $2 in
  win)
    A --es voltcap_test_settings Home; sleep 2.5; shot ${3}_1settings
    A --es voltcap_test_settings Game_look; sleep 2.5; shot ${3}_2gamelook
    A --es voltcap_test_settings Theme; sleep 2.5; shot ${3}_3theme
    A --es voltcap_test_settings_search spacing; sleep 2.5; shot ${3}_5slider
    A --es voltcap_test_settings_search battery; sleep 2.5; shot ${3}_6switches
    k KEYCODE_BUTTON_B 1.2
    A --ez voltcap_test_vault_dry true --es voltcap_test_settings Save_Vault; sleep 3.5; shot ${3}_4vault
    k KEYCODE_BUTTON_B 1.2 ;;
  d293)
    logcat -c
    A --ez voltcap_test_preview true; sleep 1
    for L in CHAN CROSS TILES HERO SPOT CLASSIC; do A --es voltcap_test_layout $L; sleep 2; done
    A --es voltcap_test_look CASE; sleep 2
    A --es voltcap_test_theme WATERFALL; sleep 2
    A --es voltcap_test_settings Home; sleep 2; k KEYCODE_BUTTON_B 1
    A --es voltcap_test_layout CHAN; sleep 2
    A --ez voltcap_test_preview false; sleep 2.5; shot d293_after_end
    echo "== preview events"; logcat -d -s VoltcapQA:E | grep -E 'preview ended|theme old' | sed -E 's/^.*VoltcapQA: [0-9]+ ?//' | head
    sh /data/local/tmp/setguard495.sh ;;
  ovl)
    A --es voltcap_test_raoverlay "open=0,scale=1.0,width=420"; sleep 2; shot ovl_1summary
    A --es voltcap_test_raoverlay "open=1,scale=1.0,width=420"; sleep 2; shot ovl_2open
    A --es voltcap_test_raoverlay "open=1,scale=1.3,width=420"; sleep 2; shot ovl_3open_13
    A --es voltcap_test_raoverlay "open=1,scale=1.6,width=420"; sleep 2; shot ovl_4open_16
    A --es voltcap_test_raoverlay "open=0,scale=1.3,width=360"; sleep 2; shot ovl_5summary_narrow13
    A --es voltcap_test_raoverlay "open=1,scale=1.3,width=360"; sleep 2; shot ovl_6open_narrow13
    A --es voltcap_test_raoverlay off; sleep 1 ;;
  batt)
    logcat -c; A --es voltcap_test_battery "47|true"; sleep 6; echo "== charging 47 (6 s)"; recomp 4; shot batt_47
    logcat -c; A --es voltcap_test_battery "48|true"; sleep 3; echo "== level 48"; recomp 3
    logcat -c; A --es voltcap_test_battery "100|false"; sleep 3; echo "== full, not charging"; recomp 3; shot batt_full
    logcat -c; A --es voltcap_test_battery "12|true"; sleep 1; A --es voltcap_test_settings Home; sleep 4; echo "== charging behind Settings"; recomp 3; shot batt_behind
    k KEYCODE_BUTTON_B 1; A --es voltcap_test_battery off; sleep 1 ;;
esac
echo DONE >> $OUT/log.txt

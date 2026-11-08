# Update 499 — screenshots / logs only (no A on Play, no game, no sync): layout-aware windows by family, Save Vault (dry view),
# custom layout accent, overlay fixture, physical-media homes. Run between `viewstate.sh save` and `viewstate.sh restore`.
# sh win499.sh TAG MODE   -> /sdcard/pc/TAG/*.png + log.txt
#   fam     windows follow ON (test_pref, restored by the snapshot) — Channel (own colours), Channel + purple accent,
#           Arctic, Cinema, Case stacks (Shelf family): Settings + Save Vault (dry) for each; layouts are previews (held)
#   off     the same Settings + Save Vault with windows follow OFF (the default) on Channel, for the before image
#   ovl     overlay fixture: open panel at text 1.0 / 1.3 / 1.6 (D294), narrow 1.6
#   media   home screenshots of Case stacks, Game shelf, Cover details (+ 3 x ▶ with a mid-move frame)
P=com.shelf.launcher; TAG=$1; D=0; OUT=/sdcard/pc/$TAG; mkdir -p $OUT
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
k() { input -d $D keyevent $1; sleep ${2:-0.5}; }
shot() { screencap -d $D -p $OUT/$1.png 2>/dev/null || screencap -p $OUT/$1.png; echo "$1 $(date +%T)" >> $OUT/log.txt; }
pair() { # LABEL: Settings (Theme) + Save Vault dry
  A --es voltcap_test_settings Theme; sleep 2.5; shot ${1}_settings
  k KEYCODE_BUTTON_B 1.2
  A --ez voltcap_test_vault_dry true --es voltcap_test_settings Save_Vault; sleep 3.5; shot ${1}_vault
  k KEYCODE_BUTTON_B 1.2; A --ez voltcap_test_vault_dry false; sleep 0.5
}
input keyevent KEYCODE_WAKEUP
case $2 in
  off)
    A --ez voltcap_test_preview true; sleep 1; A --es voltcap_test_layout CHAN; sleep 2
    A --es voltcap_test_pref windows_follow_layout=false; sleep 1; pair off_chan ;;
  fam)
    A --ez voltcap_test_preview true; sleep 1
    A --es voltcap_test_pref windows_follow_layout=true; sleep 1
    A --es voltcap_test_layout CHAN; sleep 2; pair chan
    A --es voltcap_test_pref 'layout_colors={"CHAN":{"p":"CUSTOM","acc":-7453480}}'; sleep 1.5; pair chan_purple
    A --es voltcap_test_layout ARCTIC; sleep 2; pair arctic
    A --es voltcap_test_layout CINEMA; sleep 2; pair cinema
    A --es voltcap_test_layout STACKS; sleep 2; pair stacks ;;
  ovl)
    A --es voltcap_test_raoverlay "open=1,scale=1.0,width=420"; sleep 2; shot ovl_10
    A --es voltcap_test_raoverlay "open=1,scale=1.3,width=420"; sleep 2; shot ovl_13
    A --es voltcap_test_raoverlay "open=1,scale=1.6,width=420"; sleep 2; shot ovl_16
    A --es voltcap_test_raoverlay "open=1,scale=1.6,width=360"; sleep 2; shot ovl_16_narrow
    A --es voltcap_test_raoverlay off; sleep 1 ;;
  media)
    A --ez voltcap_test_preview true; sleep 1
    for L in STACKS SHELF COVERDETAILS; do
      A --es voltcap_test_layout $L; sleep 3; shot media_$L
      k KEYCODE_DPAD_RIGHT 0.12; shot media_${L}_mid; sleep 1; k KEYCODE_DPAD_RIGHT 0.6; k KEYCODE_DPAD_RIGHT 1.2; shot media_${L}_after
    done ;;
esac
echo DONE >> $OUT/log.txt

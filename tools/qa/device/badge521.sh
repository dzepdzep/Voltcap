#!/system/bin/sh
# Batch 521b: console badge audit - isolated Voltcap QA app ONLY. (1) the QA gallery (voltcap_test_badgegallery) page by page,
# badges view and assets view; (2) real layouts with Console badges / Logo badges on stand-in games of the consoles under
# audit (empty files in the QA app's own fixture library). QA settings snapshotted and restored.  sh badge521.sh TAG [PAGES]
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; TAG=$1; NP=${2:-15}; O=$F/$TAG; rm -rf $O; mkdir -p $O; L=$O/log.txt; R=$F/roms
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
log() { echo "$*" >> $L; }
picker() { grep -q "^PICKER $(pidof $P)" $F/voltcap_qa_screen 2>/dev/null && { input keyevent 97; sleep 1.5; log "picker dismissed"; }; }
log "start $(date +%T) $(getprop ro.product.model) real=$(pidof com.shelf.launcher)"; logcat -c
input keyevent 224; A --ez voltcap_test_nolaunch true; sleep 3; picker
A --es voltcap_test_snapshot save; sleep 2
i=0; while [ $i -lt $NP ]; do
  A --es voltcap_test_badgegallery $i; sleep 2.5; screencap -p $O/gallery_b$i.png
  A --es voltcap_test_badgegallery a$i; sleep 2.5; screencap -p $O/gallery_a$i.png
  i=$((i+1)); done
A --es voltcap_test_badgegallery off; sleep 1
# stand-ins for the consoles under audit (folder name = console key)
for d in PS5 XboxOne XboxSeries Switch2 MegaDrive TG16 Famicom SFC NeoGeoPocketColor; do mkdir -p "$R/$d"; [ -f "$R/$d/QA Badge $d (USA).iso" ] || echo "voltcap qa fixture" > "$R/$d/QA Badge $d (USA).iso"; done
A --ez voltcap_test_fixture_roms true; sleep 12
A --ez voltcap_test_preview true; sleep 1
A --es voltcap_test_fixture "names=real,art=portrait,meta=none"; sleep 1
for LOGOS in TEXT SYMBOL LOGO; do
  A --es voltcap_test_pref "console_badges=true"; A --es voltcap_test_pref "console_badge_style=$LOGOS"; sleep 1
  for LY in GRID TILES CLASSIC; do
    A --es voltcap_test_layout $LY --es voltcap_test_category ALL; sleep 4; picker; screencap -p $O/layout_${LY}_logos$LOGOS.png; log "$LY logos=$LOGOS"
  done
done
A --ez voltcap_test_preview false; sleep 1; A --es voltcap_test_snapshot restore; sleep 3
log "restore: $(cut -c1-30 $F/voltcap_test_snapshot_verify.txt 2>/dev/null)"
log "real=$(pidof com.shelf.launcher) fatal=$(logcat -d | grep -c 'FATAL EXCEPTION')"; echo DONE >> $L

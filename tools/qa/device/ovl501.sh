#!/system/bin/sh
# Update 501 (isolated QA app only): overlay fixture at text 1.0 / 1.3 / 1.6 (+ narrow) and the physical-media homes.
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; O=$F/ovl501; mkdir -p $O; rm -f $O/*.png
A(){ am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null; }
input keyevent 97; sleep 1
for sc in 1.0 1.3 1.6; do A --es voltcap_test_raoverlay "open=1,scale=$sc,width=420"; sleep 2.5; screencap -p $O/ovl_$sc.png; done
A --es voltcap_test_raoverlay "open=1,scale=1.6,width=360"; sleep 2.5; screencap -p $O/ovl_16_narrow.png
A --es voltcap_test_raoverlay off; sleep 1
for L in SHELF STACKS CLASSIC; do A --es voltcap_test_layout $L; sleep 3.5; screencap -p $O/home_$L.png; done

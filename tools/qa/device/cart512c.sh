#!/system/bin/sh
# Update 512 (b, the cartridge itself — library shell "gb"): the generated / custom cartridge path (fixture "Pokemon Brown (Hack).gb", synthetic cover; QA app only) held in
# Inspect at many turns, incl. edge-on, and pitched — screenshots into the QA app's folder (nothing saved, no game).
P=com.shelf.launcher.qa; O=/sdcard/Android/data/$P/files/c512c; mkdir -p $O; rm -f $O/*
for pose in 0:0 30:0 60:0 85:0 89:0 90:0 91:0 95:0 120:0 150:0 180:0 210:0 270:0 0:25 0:-25 45:20; do
  y=${pose%%:*}; p=${pose##*:}
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_layout SHELF >/dev/null 2>&1; sleep 1.5
  am start -n $P/com.shelf.launcher.MainActivity --es voltcap_test_inspect "Pokemon_Brown|gb|$y|$p" >/dev/null 2>&1; sleep 2.6
  screencap -p $O/pb_y${y}_p${p}.png
done
input keyevent 97; ls $O | wc -l

# Update 467 (R26) — the popup sweep under another configuration, restored at the end. On the device: setsid sh popclip_cfg.sh TAG font|density VALUE "LAYOUTS" "STYLES" "GAME" &
TAG=$1; KIND=$2; VAL=$3
if [ "$KIND" = font ]; then orig=$(settings get system font_scale); settings put system font_scale $VAL
else orig=$(wm density | grep -o 'Override density: [0-9]*' | grep -o '[0-9]*$'); wm density $VAL; fi
echo "$KIND was '$orig'" > /sdcard/pc/${TAG}_cfg.txt
sleep 3; am start -n com.shelf.launcher/.MainActivity > /dev/null 2>&1; sleep 4
sh /data/local/tmp/popclip.sh "$TAG" "$4" "$5" "$6"
if [ "$KIND" = font ]; then
  if [ -z "$orig" ] || [ "$orig" = null ]; then settings delete system font_scale; else settings put system font_scale $orig; fi
else
  if [ -z "$orig" ]; then wm density reset; else wm density $orig; fi
fi
echo "restored: font=$(settings get system font_scale) density=$(wm density | tr '\n' ' ')" >> /sdcard/pc/${TAG}_cfg.txt

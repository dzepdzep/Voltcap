#!/system/bin/sh
# Update 511: rollback test on the ISOLATED QA app only (com.shelf.launcher.qa — never the owner's Voltcap).
# Needs /data/local/tmp/qa74.apk (vc 74) and qa75.apk (vc 75), same signer. Marker: QA pref tiles_spacing, set to 137,
# read back through the test hook's "was" log after each step; the original value is put back at the end.
P=com.shelf.launcher.qa; C=$P/com.shelf.launcher.MainActivity
vc() { dumpsys package $P | grep -m1 versionCode | awk '{print $1}'; }
mark() { logcat -c; am start -n $C --es voltcap_test_pref "tiles_spacing=$1" >/dev/null 2>&1; sleep 3; logcat -d -s VoltcapTest | grep "pref tiles_spacing" | tail -1 | sed 's/.*pref/pref/'; }
echo "start $(vc)"
echo "orig: $(mark 137)"
echo "A install -r --enable-rollback vc75: $(pm install -r --enable-rollback /data/local/tmp/qa75.apk 2>&1 | tail -1) -> $(vc)"
echo "   marker: $(mark 137)"
echo "   rollbacks: $(dumpsys rollback 2>/dev/null | grep -c "$P")"
echo "B install -r vc74 (same signer, lower code): $(pm install -r /data/local/tmp/qa74.apk 2>&1 | tail -1) -> $(vc)"
echo "C install -r -d vc74: $(pm install -r -d /data/local/tmp/qa74.apk 2>&1 | tail -1) -> $(vc)"
echo "   marker: $(mark 137)"
if [ "$(vc)" = "versionCode=75" ]; then
  echo "D pm rollback-app: $(pm rollback-app $P 2>&1 | tail -1) -> $(vc)"
  sleep 3; echo "   marker: $(mark 137)"
fi
echo "end $(vc)"

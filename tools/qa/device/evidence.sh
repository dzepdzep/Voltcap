# Update 474 — run manifest that binds a device run to the exact build, device and configuration (sourced:  . ./evidence.sh).
#   ev_start OUT TAG [key=value ...]   writes OUT/manifest.txt: tag, device, display, text scale, density, the installed
#                                      Voltcap (versionName / versionCode / APK SHA-256 / the git revision the build embeds),
#                                      the script's own SHA-256 and the start time, plus any key=value the caller adds
#                                      (layouts=, styles=, game=, content=, config= ...).
#   ev_note OUT key=value              one more line (e.g. style_set CARDS_ROW_OPENS ok)
#   ev_end OUT                         finished=<epoch>  (a run without it is never graded: interrupted = INFRA)
# Read-only: it only reads system state and the installed APK. tools/qa/tools/evidence.py checks it on the host.
P=com.shelf.launcher
ev_apk() { pm path $P 2>/dev/null | sed -n 's/^package://p' | grep -m1 base.apk; }
ev_start() {
  o=$1; t=$2; shift 2; m=$o/manifest.txt; a=$(ev_apk)
  {
    echo "manifest=1"
    echo "tag=$t"
    echo "started=$(date +%s)"
    echo "serial=$(getprop ro.serialno)"
    echo "model=$(getprop ro.product.model)"
    echo "android=$(getprop ro.build.version.release)"
    echo "display=$(wm size | sed -n 's/Physical size: //p' | head -1)"
    echo "display_override=$(wm size | sed -n 's/Override size: //p' | head -1)"
    echo "density=$(wm density | sed -n 's/Physical density: //p' | head -1)"
    echo "density_override=$(wm density | sed -n 's/Override density: //p' | head -1)"
    echo "font_scale=$(settings get system font_scale)"
    echo "animator=$(settings get global animator_duration_scale)"
    echo "version_name=$(dumpsys package $P | sed -n 's/.*versionName=//p' | head -1)"
    echo "version_code=$(dumpsys package $P | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)"
    echo "apk_sha256=$([ -n "$a" ] && sha256sum "$a" | cut -d' ' -f1)"
    echo "apk_revision=$([ -n "$a" ] && unzip -p "$a" META-INF/version-control-info.textproto 2>/dev/null | sed -n 's/.*revision: "\([0-9a-f]*\)".*/\1/p' | head -1)"
    echo "pid=$(pidof $P | cut -d' ' -f1)"
    for kv in "$@"; do echo "$kv"; done
  } > $m
}
ev_note() { echo "$2" >> $1/manifest.txt; }
ev_end() { echo "finished=$(date +%s)" >> $1/manifest.txt; }

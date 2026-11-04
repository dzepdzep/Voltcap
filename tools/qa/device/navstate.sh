# navstate.sh LABEL — which bar / handle windows are visible now, per display, and the device's own setting (read-only).
echo "== $1  $(date +%T)  hide_nav_bar=$(settings get global hide_nav_bar) nav_mode=$(settings get secure navigation_mode)"
echo "focus: $(dumpsys window | grep -m1 mCurrentFocus | sed 's/.*{[0-9a-f]* u0 //; s/}//')"
dumpsys window windows | awk '/Window #[0-9]+ Window\{/{n=$0; sub(/.*u0 /,"",n); sub(/\}:/,"",n)} /mDisplayId=/{d=$0; sub(/.*mDisplayId=/,"",d); sub(/ .*/,"",d)} /isVisible=/{v=$0; sub(/.*isVisible=/,"",v); if (n ~ /NavigationBar|HomeHandle|Gesture|NavBar|nav_bar|Taskbar/) print "  d" d " " n " visible=" v}'
dumpsys window | grep -E "InsetsSource.*type=navigationBars" | sed 's/^ *//' | cut -c1-140 | sort -u | head -6

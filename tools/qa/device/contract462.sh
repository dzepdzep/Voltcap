# Update 462: the controller contract on every layout (simulated input: adb `input keyevent`, not a physical pad).
# usage: sh contract462.sh "LAYOUT ..." OUTDIR   (needs the voltcap_test_hooks file; the caller saves / restores the snapshot)
# Per layout: Select → Settings, Start → game options, R1 → quick menu, L1 → shortcuts, B → category picker (themed or
# plain), R2 / L2 → next / previous category; each closed with B. A game must never start (voltcap_test_nolaunch).
P=com.shelf.launcher; LAYS=$1; OUT=${2:-/sdcard/c462}; mkdir -p $OUT; R=$OUT/result.txt; : > $R
A() { am start -n $P/.MainActivity "$@" > /dev/null 2>&1; }
st() { logcat -d -s VoltcapQA | grep " overlay " | tail -1 | sed 's/.* overlay //'; }
key() { input keyevent $1; sleep ${2:-1.3}; }
closeall() { for i in 1 2 3; do s=$(st); case "$s" in *menu=true*|*quick=true*|*opts=true*|*short=true*|*cat=true*|*sort=true*|*pq=SYSTEMS*|*pq=DETAILS*|*gc=SYSTEMS*|*pc=CATEGORIES*|*pc=GAMES*) key 97 1.0;; *) return;; esac; done; }
A --ez voltcap_test_preview true; sleep 2
A --ez voltcap_test_nolaunch true; sleep 1
for lay in $LAYS; do
  A --es voltcap_test_layout $lay; sleep 4
  input keyevent 224
  closeall
  base=$(st | sed 's/.*cat_key=//')
  line="$lay"
  for k in 109:Select:menu=true 108:Start:opts=true 103:R1:quick=true 102:L1:short=true; do
    c=${k%%:*}; rest=${k#*:}; n=${rest%%:*}; want=${rest#*:}
    logcat -c; key $c; s=$(st)
    case "$s" in *$want*) line="$line $n=OK";; *) line="$line $n=FAIL[$s]";; esac
    closeall
  done
  logcat -c; key 97; s=$(st)
  case "$s" in *cat=true*) line="$line B=plain";; *pq=SYSTEMS*|*gc=SYSTEMS*|*pc=CATEGORIES*) line="$line B=themed";; *) line="$line B=none[$s]";; esac
  closeall
  logcat -c; key 105; k1=$(st | sed 's/.*cat_key=//'); key 104; k2=$(st | sed 's/.*cat_key=//')
  if [ -n "$k1" ] && [ "$k1" != "$base" ] && [ "$k2" = "$base" ]; then line="$line R2L2=OK"; else line="$line R2L2=?[$base>$k1>$k2]"; fi
  # rapid: Select Select B B Start B R1 B (nothing may launch, the app stays in front)
  for c in 109 109 97 97 108 97 103 97; do input keyevent $c; sleep 0.15; done; sleep 1.2; closeall
  fg=$(dumpsys activity activities | grep -m1 -E "topResumedActivity|mResumedActivity")
  case "$fg" in *$P*) line="$line rapid=OK";; *) line="$line rapid=FAIL"; echo "$line" >> $R; echo done > $OUT/DONE; exit 1;; esac
  echo "$line" >> $R
done
echo done > $OUT/DONE

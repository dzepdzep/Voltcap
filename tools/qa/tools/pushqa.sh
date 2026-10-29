#!/bin/sh
# VoltCap QA: push the device scripts to the Thor with LF endings (update 396). Windows checkouts / editors can leave CRLF in the
# working tree (git itself keeps LF, .gitattributes eol=lf); Android sh then fails with "\r: not found". This strips CRs in the
# working tree first, pushes every tools/qa/device/*.sh, and fails if a pushed copy still holds a CR.
#   sh tools/qa/tools/pushqa.sh [SERIAL]      (default: the owner's Thor, 3140972f; never another device)
export MSYS_NO_PATHCONV=1   # Git Bash on Windows would rewrite /data/... into C:/Program Files/Git/data/...
S=${1:-3140972f}
D=/data/local/tmp/vqa
cd "$(dirname "$0")/../device" || exit 2
sed -i 's/\r$//' ./*.sh
adb -s "$S" shell "mkdir -p $D/out" || exit 2
for f in ./*.sh; do adb -s "$S" push "$f" "$D/$(basename "$f")" > /dev/null 2>&1 || { echo "push failed: $f"; exit 2; }; done
bad=$(adb -s "$S" shell "grep -l \"$(printf '\r')\" $D/*.sh 2>/dev/null")
[ -z "$bad" ] || { echo "CR still in device copy: $bad"; exit 1; }
echo "pushqa: $(ls ./*.sh | wc -l) scripts pushed to $S, no CR"

# VoltCap QA — where a layout's frame time goes: atrace (gfx view input dalvik am) while the usual navigation runs,
# for this session's layout only (preview: nothing saved). Keeps Voltcap's own threads (main + RenderThread + hwui).
#   sh ltrace.sh NAME LAYOUT   → out/NAMEm.atrace → tools/systrace.py
n=$1; L=$2
sh vqa.sh hooks on > /dev/null
sh vqa.sh ready > /dev/null || { sh vqa.sh hooks off > /dev/null; echo "ltrace ABORTED"; exit 1; }
am start -n com.shelf.launcher/.MainActivity --ez voltcap_test_preview true --es voltcap_test_layout $L > /dev/null 2>&1; sleep 4
am start -n com.shelf.launcher/.MainActivity --es voltcap_test_category ALL > /dev/null 2>&1; sleep 3
sh vqa.sh seq "R L" 300 > /dev/null; sleep 1
sh vqa.sh atrace $n 9
sh vqa.sh seq "R R R R R R W400 L L L L W300 D D W300 U U" 300 > /dev/null; sleep 4
P=$(pidof com.shelf.launcher)
grep "tracing_mark_write" out/$n.atrace | grep -E "\( *$P\)" > out/${n}m.atrace
rm -f out/$n.atrace
sh vqa.sh hooks off > /dev/null; sh vqa.sh seq "B" 200
echo "ltrace $n done $(wc -l < out/${n}m.atrace) lines"

# Batch 522 final re-checks on build 522g (isolated QA app only): badge looks gallery, Tile home Edit Home independence, Bubbles.
D=/data/local/tmp; rm -f /sdcard/pc/chain522c.txt
sh $D/badge521.sh b522final > /dev/null 2>&1
sh $D/tile522b.sh t522c > /dev/null 2>&1
sh $D/pop522.sh p522bub "BUBBLES" "OWN BANNER CLASSIC" "Super Mario World" > /dev/null 2>&1
sh $D/pop522.sh p522bubs "BUBBLES TILES" "OWN" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1
echo CHAIN_DONE > /sdcard/pc/chain522c.txt

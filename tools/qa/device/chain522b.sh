# Batch 522 re-checks on the new QA build (isolated QA app only): badge looks gallery, Tile home Edit Home independence,
# Green Core keys at font x1.3, the layouts whose popups failed (names cut, Blades facts), Banner / Row styles' cut text.
D=/data/local/tmp; rm -f /sdcard/pc/chain522b.txt
sh $D/badge521.sh b522modes > /dev/null 2>&1
sh $D/tile522b.sh t522b > /dev/null 2>&1
sh $D/pop522.sh p522gc "GREENCORE LIBRARY MEMCARD SPOT" "OWN" "Super Mario World" "font=1.3,density=1.15" > /dev/null 2>&1
sh $D/pop522.sh p522fix "STORAGE BUBBLES CAMERA ORBIT BLADES" "OWN" "Super Mario World" > /dev/null 2>&1
sh $D/pop522.sh p522fix2 "CLASSIC GRID TILES HERO SHELF CROSS" "BANNER ROW_OPENS ROW_REVEAL" "Super Mario World" > /dev/null 2>&1
echo CHAIN_DONE > /sdcard/pc/chain522b.txt

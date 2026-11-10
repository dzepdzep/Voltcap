#!/system/bin/sh
# Batch 521: a fresh isolated QA app's stand-in library (empty files in ITS OWN external files/roms; nothing of the owner's),
# then the QA-only rescan. sh qasetup521.sh
P=com.shelf.launcher.qa; F=/sdcard/Android/data/$P/files; R=$F/roms
A() { am start -n $P/com.shelf.launcher.MainActivity "$@" >/dev/null 2>&1; }
for g in "gc/Donkey Konga (USA).iso" "gc/Donkey Konga 2 (USA).iso" "gc/Super Mario Sunshine (USA).iso" "psx/WWE SmackDown (USA).bin" \
  "psx/Crash Bandicoot (USA).bin" "psx/Final Fantasy VII (USA) (Disc 1).bin" "n64/Mario Kart 64 (USA).z64" "n64/The Legend of Zelda - Ocarina of Time (USA).z64" \
  "snes/Super Mario World (USA).sfc" "snes/Super Metroid (USA).sfc" "gba/Metroid Fusion (USA).gba" "gba/Pokemon Emerald (USA).gba" "gb/Tetris (World).gb" \
  "gbc/Pokemon Silver (USA).gbc" "nes/Super Mario Bros. (World).nes" "megadrive/Sonic the Hedgehog (USA, Europe).md" "psp/Daxter (USA).iso" \
  "nds/Mario Kart DS (USA).nds" "ps2/Shadow of the Colossus (USA).iso" "dreamcast/Crazy Taxi (USA).gdi" "saturn/Nights into Dreams (USA).cue" \
  "3ds/Super Mario 3D Land (USA).3ds" "wii/Wii Sports (USA).iso" "ps3/QA Case PS3 (USA).iso" "xbox360/QA Case Xbox360 (USA).iso"; do
  mkdir -p "$R/$(dirname "$g")"; [ -f "$R/$g" ] || echo "voltcap qa fixture" > "$R/$g"; done
A --ez voltcap_test_nolaunch true --ez voltcap_test_fixture_roms true; sleep 12
echo "roms=$(find $R -type f | wc -l) screen=$(cat $F/voltcap_qa_screen 2>/dev/null | head -1) real=$(pidof com.shelf.launcher)"

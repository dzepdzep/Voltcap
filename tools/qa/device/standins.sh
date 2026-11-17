#!/system/bin/sh
# 678l (H6, RP Classic j677c10 "no game matches 'Shadow of the Colossus'"): the isolated QA app's stand-in library - empty
# files with game-like names in ITS OWN external files/roms (never the owner's ROMs, saves or app). pop529.sh's setup only
# turned the fixture library on; the files themselves came from an earlier fixroms500.sh / qasetup521.sh run on that device,
# so a freshly (re)installed QA app (the Classic) scanned an empty folder. This script makes them every time (re-running is
# harmless: an existing file is left as it is). As the emulator trial learnt (529t): on Android 11+ the shell may be refused
# the app's own Android/data folder - then the files are made as the app itself (run-as: the debuggable QA build only).
# Prints one line: "standins=<files> via=<shell|run-as|none>"; exit 1 when the library is still empty.
#   sh standins.sh                (QA_STANDIN_ROOT=<dir> only for the host self-test)
P=com.shelf.launcher.qa; R=${QA_STANDIN_ROOT:-/sdcard/Android/data/$P/files/roms}
LIST='gc/Donkey Konga (USA).iso
gc/Donkey Konga 2 (USA).iso
gc/Super Mario Sunshine (USA).iso
psx/WWE SmackDown (USA).bin
psx/Crash Bandicoot (USA).bin
psx/Final Fantasy VII (USA) (Disc 1).bin
psx/Vagrant Story (USA).bin
n64/Mario Kart 64 (USA).z64
n64/Super Mario 64 (USA).z64
n64/The Legend of Zelda - Ocarina of Time (USA).z64
snes/Super Mario World (USA).sfc
snes/Super Metroid (USA).sfc
snes/Chrono Trigger (USA).sfc
snes/EarthBound (USA).sfc
snes/ActRaiser (USA).sfc
gba/Metroid Fusion (USA).gba
gba/Pokemon Emerald (USA).gba
gba/Golden Sun (USA).gba
gb/Tetris (World).gb
gbc/Pokemon Silver (USA).gbc
nes/Super Mario Bros. (World).nes
megadrive/Sonic the Hedgehog (USA, Europe).md
psp/Daxter (USA).iso
nds/Mario Kart DS (USA).nds
ps2/Shadow of the Colossus (USA).iso
ps2/Okami (USA).iso
dreamcast/Crazy Taxi (USA).gdi
saturn/Nights into Dreams (USA).cue
3ds/Super Mario 3D Land (USA).3ds
wii/Wii Sports (USA).iso
ps3/QA Case PS3 (USA).iso
xbox360/QA Case Xbox360 (USA).iso'
# (one script for both routes; the names hold no single quote, so the run-as copy can carry them inside '...')
MK="R='$R'; LIST='$LIST'"'; IFS="
"; for g in $LIST; do mkdir -p "$R/${g%/*}" 2>/dev/null && { [ -f "$R/$g" ] || echo "voltcap qa fixture" > "$R/$g"; } 2>/dev/null; done; find "$R" -type f 2>/dev/null | wc -l'
want=$(printf '%s\n' "$LIST" | wc -l)
n=$(sh -c "$MK" | tr -d ' \r'); via=shell
if [ "${n:-0}" -lt "$want" ]; then
  m=$(run-as $P sh -c "$MK" 2>/dev/null | tr -d ' \r')
  [ "${m:-0}" -gt "${n:-0}" ] && { n=$m; via=run-as; }
fi
[ "${n:-0}" -gt 0 ] || via=none
echo "standins=${n:-0} via=$via"
[ "${n:-0}" -gt 0 ]

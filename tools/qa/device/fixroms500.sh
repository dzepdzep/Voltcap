#!/system/bin/sh
# Update 500: the isolated QA app's fixture library — tiny stand-in ROM files in ITS OWN folder
# (/sdcard/Android/data/com.shelf.launcher.qa/files/roms), never the owner's ROMs or saves. Re-running is harmless.
R=/sdcard/Android/data/com.shelf.launcher.qa/files/roms
# (the QA app's own test-hooks file: some hooks still look for it; it lives only in the QA app's folder)
mkdir -p ${R%/roms}; touch ${R%/roms}/voltcap_test_hooks
mk(){ mkdir -p "$R/$1"; [ -f "$R/$1/$2" ] || echo "voltcap qa fixture" > "$R/$1/$2"; }
mk snes "Super Mario World (USA).sfc"; mk snes "The Legend of Zelda - A Link to the Past (USA).sfc"; mk snes "Super Metroid (Japan, USA).sfc"
mk n64 "Super Mario 64 (USA).z64"; mk n64 "Mario Kart 64 (USA).z64"; mk n64 "The Legend of Zelda - Ocarina of Time (USA).z64"
mk gba "Pokemon - Emerald Version (USA, Europe).gba"; mk gba "Metroid Fusion (USA).gba"
mk gb "Pokemon Brown (Hack).gb"; mk gb "Tetris (World) (Rev 1).gb"; mk gbc "Pokemon - Crystal Version (USA, Europe).gbc"
mk nes "Super Mario Bros. 3 (USA).nes"; mk nes "Mega Man 2 (USA).nes"
mk genesis "Sonic the Hedgehog 2 (World).md"; mk nds "Mario Kart DS (USA).nds"
mk psx "Crash Bandicoot (USA).chd"; mk ps2 "Shadow of the Colossus (USA).iso"; mk gc "Super Mario Sunshine (USA).iso"
ls -R $R | wc -l

# Theme review — update 427

All built-in themes rendered with `tools/themes/review_refresh.cjs` (WebGL port of the AGSL scenes, sample covers / media / trophies / paintings,
3 moments: 2, 9, 17 s, plus a "home" state with a mock row, focus ring, title and buttons). Sheets: `refresh-review/sheet-*.jpg` (not committed;
re-run the script). Numbers: title zone = mean light of the top-left band where titles sit (0–1), detail = luminance spread, motion = change between moments.
Contrast of every palette: `python tools/themes/contrast_check.py` (0 under the aims after this update).

**Read this first (update 428 review):** every class below comes from SIMULATED WebGL frames with a mock interface. KEEP and KEEP\* are
review results, not proof of device quality: readability with the real launcher, animation smoothness, frame pacing, OLED, Lite and
controller behaviour are only known where the "Device (428)" section says so.

Classes: **KEEP** good as is · **KEEP\*** the preview cannot show it (drawn in Compose): device check needed · **POLISH** a specific weakness below · **REWORK** none found.
Not in the WebGL gallery: Lobby (drawn in Compose, LobbyScene) and the two custom entries (your own packs).
Totals: KEEP 181, KEEP* 6, POLISH 20 (of 207 rendered).

| Theme | Motif | Class | Title zone | Detail | Motion | Notes |
|---|---|---|---|---|---|---|
| Volt · bright synth arps | MINIMAL | KEEP | 0.08 | 0.094 | 0.0193 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Tide · calm, watery | MINIMAL | KEEP | 0.09 | 0.098 | 0.0199 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Ember · warm and glowing | MINIMAL | KEEP | 0.08 | 0.094 | 0.0192 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Cobalt · cool, spacey | MINIMAL | KEEP | 0.07 | 0.082 | 0.0169 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Synthwave · retro night drive | MINIMAL | KEEP | 0.08 | 0.079 | 0.0161 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Matcha · soft and cosy | MINIMAL | KEEP | 0.09 | 0.099 | 0.0201 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Mono · minimal | MINIMAL | KEEP | 0.05 | 0.118 | 0.0242 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Voltcap | VOLT_INTRO | KEEP | 0.16 | 0.165 | 0.0162 |  |
| Voltcap beat | VOLT_BEAT | KEEP | 0.19 | 0.107 | 0.0076 |  |
| Voltcap 32-bit | VOLT_PIXELS | KEEP | 0.05 | 0.113 | 0.0282 |  |
| Waterfall cliffs | PIXEL_FALLS | KEEP* | 0.08 | 0.102 | 0.0265 | Preview shows the 32-bit Voltcap sky only; the waterfall frames are drawn over it in Compose (needs device check). |
| Retro bounce | RETRO_BOUNCE | KEEP | 0.08 | 0.055 | 0.0014 |  |
| Lyric cloud | LYRIC_CLOUD | POLISH | 0.11 | 0.035 | 0.0032 | Without lyrics on screen (paused / no lyrics found) it is a flat amber fog that reads as "not loaded". Needs a calm idle state (verify on device with and without lyrics). |
| Karaoke | LYRIC_KARAOKE | POLISH | 0.14 | 0.056 | 0.0220 | Without lyrics on screen (paused / no lyrics found) it is a flat amber fog that reads as "not loaded". Needs a calm idle state (verify on device with and without lyrics). |
| Lyric sheet | LYRIC_SHEET | POLISH | 0.11 | 0.035 | 0.0032 | Without lyrics on screen (paused / no lyrics found) it is a flat amber fog that reads as "not loaded". Needs a calm idle state (verify on device with and without lyrics). |
| Neon lyrics | LYRIC_NEON | KEEP | 0.11 | 0.039 | 0.0061 |  |
| Volt | SPARKS | KEEP | 0.08 | 0.045 | 0.0267 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Tide | LIQUID | KEEP | 0.18 | 0.069 | 0.0277 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Ember | FLAMES | KEEP | 0.04 | 0.125 | 0.0261 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Cobalt | ORBIT | KEEP | 0.06 | 0.113 | 0.0060 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Synthwave | RETRO_GRID | KEEP | 0.19 | 0.201 | 0.0274 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Matcha | LEAVES | KEEP | 0.14 | 0.071 | 0.0261 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Mono (OLED) | MINIMAL | KEEP | 0.05 | 0.118 | 0.0242 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Now playing | MUSIC | KEEP | 0.35 | 0.117 | 0.0170 |  |
| Spectrum | SPECTRUM | KEEP | 0.28 | 0.090 | 0.0099 |  |
| Pulse ring | PULSE_RING | KEEP | 0.26 | 0.093 | 0.0133 |  |
| Beat tunnel | BEAT_TUNNEL | KEEP | 0.13 | 0.079 | 0.0504 |  |
| Reactor core | REACTOR | KEEP | 0.16 | 0.079 | 0.0046 |  |
| Thunderstorm | THUNDER | KEEP | 0.09 | 0.043 | 0.0107 |  |
| Bloom | BLOOM | KEEP | 0.19 | 0.068 | 0.0086 |  |
| Tape deck | TAPE_DECK | KEEP | 0.24 | 0.131 | 0.0054 |  |
| Sand patterns | CHLADNI | KEEP | 0.14 | 0.071 | 0.0116 |  |
| Laser stage | LASER_STAGE | KEEP | 0.09 | 0.074 | 0.0353 |  |
| Kaleido cover | KALEIDO | KEEP | 0.20 | 0.121 | 0.0157 |  |
| Cover shatter | SHATTER | KEEP | 0.29 | 0.120 | 0.0216 |  |
| Wire terrain | TERRAIN | KEEP | 0.18 | 0.132 | 0.0251 |  |
| Fireworks | FIREWORKS | KEEP | 0.07 | 0.047 | 0.0066 |  |
| Neon puddle | NEON_PUDDLE | KEEP | 0.06 | 0.088 | 0.0209 |  |
| Vinyl spin | VINYL | KEEP | 0.37 | 0.164 | 0.0201 |  |
| Disc spin | CD_SPIN | KEEP | 0.34 | 0.110 | 0.0457 |  |
| Album lava | LAVA | KEEP | 0.31 | 0.145 | 0.0618 |  |
| Sound ribbons | RIBBONS | KEEP | 0.22 | 0.158 | 0.0278 |  |
| Disco floor | DISCO | KEEP | 0.20 | 0.176 | 0.1402 |  |
| Bass cones | SPEAKER | KEEP | 0.18 | 0.058 | 0.0135 |  |
| Spark | SPARKS | KEEP | 0.08 | 0.048 | 0.0287 |  |
| Torrent | BUBBLES | KEEP | 0.35 | 0.129 | 0.0381 |  |
| Sprout | LEAVES | KEEP | 0.14 | 0.074 | 0.0273 |  |
| Mind | PSY_WAVES | KEEP | 0.11 | 0.101 | 0.0108 |  |
| Blaze | ANIME_FLAMES | KEEP | 0.05 | 0.290 | 0.1023 |  |
| Phantom | WISPS | KEEP | 0.09 | 0.102 | 0.0255 |  |
| Frost | FROST | KEEP | 0.19 | 0.143 | 0.0755 |  |
| Terra | TERRA | KEEP | 0.20 | 0.142 | 0.0186 |  |
| Venom | VENOM | KEEP | 0.15 | 0.110 | 0.0429 |  |
| Meadow | MEADOW | POLISH | 0.56 | 0.190 | 0.0208 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Dojo | DOJO | KEEP | 0.26 | 0.187 | 0.0005 |  |
| Gale | GALE | KEEP | 0.31 | 0.206 | 0.0385 |  |
| Thicket | THICKET | KEEP | 0.18 | 0.169 | 0.0084 |  |
| Bedrock | BEDROCK | KEEP | 0.40 | 0.143 | 0.0012 |  |
| Wyrm | WYRM | KEEP | 0.12 | 0.093 | 0.0469 |  |
| Umbra | UMBRA | KEEP | 0.04 | 0.077 | 0.0086 |  |
| Alloy | ALLOY | KEEP | 0.20 | 0.112 | 0.0344 |  |
| Glimmer | GLIMMER | KEEP | 0.21 | 0.123 | 0.0227 |  |
| Pocket LCD | LCD | KEEP | 0.17 | 0.102 | 0.0437 | Palette contrast fixed in 427. |
| Pocket shell | LCD | KEEP | 0.12 | 0.068 | 0.0282 | Palette contrast fixed in 427. |
| Shinobi | LEAF_SWIRL | KEEP | 0.10 | 0.129 | 0.0252 |  |
| Power aura | AURA | KEEP | 0.13 | 0.281 | 0.0923 |  |
| Summer sky | CEL_SKY | POLISH | 0.41 | 0.203 | 0.0282 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Mecha HUD | MECHA_HUD | KEEP | 0.06 | 0.049 | 0.0240 |  |
| Moon blade | MOON_BLADE | KEEP | 0.13 | 0.211 | 0.0247 |  |
| Circuit board | CIRCUIT | KEEP | 0.07 | 0.084 | 0.0085 |  |
| Gearworks | GEARWORKS | KEEP | 0.14 | 0.147 | 0.0263 |  |
| Warp drive | WARP | POLISH | 0.04 | 0.053 | 0.0635 | Brightness swings a lot between moments (bright burst at 2 s, dim at 9 / 17 s): check the burst behind white text and its frame pacing on the Thor. |
| Prism atrium | PRISM_ATRIUM | KEEP | 0.09 | 0.107 | 0.0721 |  |
| Orbit station | ORBIT_STATION | KEEP | 0.03 | 0.054 | 0.0025 |  |
| Aurora | AURORA | KEEP | 0.27 | 0.149 | 0.1538 |  |
| Nebula | NEBULA | KEEP | 0.09 | 0.050 | 0.0195 |  |
| Pixel flow | PIXELS | KEEP | 0.17 | 0.205 | 0.1977 |  |
| Glass | SHOWCASE_GLASS | KEEP | 0.15 | 0.117 | 0.0577 |  |
| Retro wave | RETRO_GRID | KEEP | 0.15 | 0.147 | 0.0251 |  |
| Orbit | ORBIT | KEEP | 0.06 | 0.131 | 0.0074 |  |
| Digital rain | RAIN | KEEP | 0.08 | 0.101 | 0.0675 |  |
| Constellation | STARS | KEEP | 0.11 | 0.096 | 0.0167 |  |
| Liquid | LIQUID | KEEP | 0.17 | 0.068 | 0.0277 |  |
| Minimal console | MINIMAL | KEEP | 0.10 | 0.089 | 0.0181 | Classic palette family on the shared Minimal orbit scene; little distinction between them is by design (the palette is the point). |
| Sunlit shallows | CAUSTICS | KEEP | 0.26 | 0.096 | 0.0797 |  |
| Ink wash | INK | KEEP | 0.29 | 0.074 | 0.0339 |  |
| Paper layers | PAPERCUT | KEEP | 0.26 | 0.088 | 0.0142 |  |
| 16-bit valley | PARALLAX16 | KEEP | 0.12 | 0.084 | 0.0227 |  |
| Holo foil | HOLO | KEEP | 0.18 | 0.107 | 0.0628 |  |
| Campfire | CAMPFIRE | KEEP | 0.06 | 0.100 | 0.0106 |  |
| 8-bit quest | ERA_8BIT | KEEP | 0.24 | 0.166 | 0.0439 |  |
| 16-bit mode 7 | MODE7 | KEEP | 0.32 | 0.091 | 0.0546 |  |
| 32-bit polygons | PS1_POLY | KEEP | 0.10 | 0.141 | 0.0596 |  |
| 64-bit plains | N64_PLAINS | POLISH | 0.67 | 0.139 | 0.0500 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| 128-bit haze | PS2_HAZE | KEEP | 0.08 | 0.112 | 0.0630 |  |
| Blue sky cruise | BLUE_SKY | POLISH | 0.43 | 0.201 | 0.0899 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Wave menu | XMB_WAVE | KEEP | 0.17 | 0.174 | 0.0421 |  |
| Motion plaza | PLAZA | KEEP | 0.36 | 0.092 | 0.0340 |  |
| Vector arcade | VECTOR | KEEP | 0.01 | 0.051 | 0.0115 |  |
| CRT tunnel | CRT_TUNNEL | KEEP | 0.14 | 0.086 | 0.0805 |  |
| Memory card | MEMORY_CARD | KEEP | 0.11 | 0.098 | 0.0126 |  |
| Energy core | ENERGY_CORE | KEEP | 0.07 | 0.183 | 0.0136 |  |
| 32-bit racing | RACING_32 | KEEP | 0.23 | 0.104 | 0.0186 |  |
| 32-bit fire | FIRE_32 | KEEP | 0.10 | 0.223 | 0.0271 |  |
| Hero parallax | SHOWCASE_PARALLAX | KEEP* | 0.07 | 0.044 | 0.0215 | Preview shows only the no-art fallback (the real theme is drawn in Compose from the game's art). Not reviewable here: needs the device pass (and the RP Classic idle-GPU check). |
| Cinematic | SHOWCASE_CINEMA | KEEP* | 0.06 | 0.053 | 0.0249 | Preview shows only the no-art fallback (the real theme is drawn in Compose from the game's art). Not reviewable here: needs the device pass (and the RP Classic idle-GPU check). |
| Filmstrip | SHOWCASE_FILMSTRIP | KEEP* | 0.06 | 0.040 | 0.0197 | Preview shows only the no-art fallback (the real theme is drawn in Compose from the game's art). Not reviewable here: needs the device pass (and the RP Classic idle-GPU check). |
| Screenshot wall | SHOWCASE_MOSAIC | KEEP* | 0.06 | 0.039 | 0.0195 | Preview shows only the no-art fallback (the real theme is drawn in Compose from the game's art). Not reviewable here: needs the device pass (and the RP Classic idle-GPU check). |
| Arena entrance | ARENA | KEEP | 0.20 | 0.184 | 0.0145 |  |
| Specimen lab | SPECIMEN_LAB | KEEP | 0.26 | 0.144 | 0.0092 |  |
| Magical sparkle | MAGICAL | POLISH | 0.70 | 0.131 | 0.0708 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Spirit forest | SPIRIT_FOREST | KEEP | 0.49 | 0.231 | 0.0222 |  |
| Summer festival | FESTIVAL | KEEP | 0.14 | 0.151 | 0.0337 |  |
| Aura clash | AURA_CLASH | KEEP | 0.45 | 0.284 | 0.0574 |  |
| Cover wall | ART_WALL | KEEP | 0.33 | 0.201 | 0.1182 |  |
| Cover tunnel | ART_TUNNEL | KEEP | 0.45 | 0.224 | 0.1337 |  |
| Pop art cover | POP_ART | KEEP | 0.22 | 0.127 | 0.0082 |  |
| Cover ripple | COVER_RIPPLE | KEEP | 0.34 | 0.134 | 0.0158 |  |
| Blaze beat | BLAZE_BEAT | KEEP | 0.06 | 0.200 | 0.0583 |  |
| Aura clash · music | AURA_BEAT | KEEP | 0.38 | 0.246 | 0.0523 |  |
| Mountains forming | MOUNTAINS | KEEP | 0.18 | 0.168 | 0.0100 |  |
| Specimen lab · music | SPECIMEN_BEAT | KEEP | 0.20 | 0.115 | 0.0090 |  |
| 128-bit towers | TOWERS_128 | KEEP | 0.10 | 0.060 | 0.0077 |  |
| 16-bit cartridge | CARTRIDGE_16 | KEEP | 0.12 | 0.139 | 0.0014 |  |
| Handheld | HANDHELD | KEEP | 0.27 | 0.215 | 0.0124 |  |
| Pixel parade | PIXEL_PARADE | KEEP | 0.17 | 0.148 | 0.0343 |  |
| Campfire beat | CAMPFIRE_BEAT | KEEP | 0.03 | 0.158 | 0.0046 |  |
| Manga beat | MANGA_BEAT | POLISH | 0.66 | 0.286 | 0.0252 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Comic pages | COMIC_PANELS | POLISH | 0.61 | 0.215 | 0.1944 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Pixel sky | PIXEL_SKY | POLISH | 0.50 | 0.149 | 0.0391 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| CRT room | SHOWCASE_CRT | KEEP | 0.07 | 0.081 | 0.0180 |  |
| Cover flow | COVER_FLOW | KEEP | 0.24 | 0.139 | 0.0112 |  |
| Record shelf | RECORD_SHELF | KEEP | 0.22 | 0.142 | 0.0028 |  |
| Cover cube | COVER_CUBE | KEEP | 0.14 | 0.112 | 0.0166 |  |
| Floating cover | COVER_FLOAT | KEEP | 0.20 | 0.098 | 0.0109 |  |
| Holo card | HOLO_COVER | KEEP | 0.18 | 0.122 | 0.0501 |  |
| Cover halo | COVER_HALO | KEEP | 0.19 | 0.120 | 0.0063 |  |
| Big type | BIG_TYPE | KEEP | 0.13 | 0.120 | 0.0168 |  |
| Pixel cover | PIXEL_COVER | KEEP | 0.12 | 0.161 | 0.0022 |  |
| Polaroids | POLAROID | KEEP | 0.45 | 0.178 | 0.0166 |  |
| Retro TV | RETRO_TV | KEEP | 0.12 | 0.144 | 0.0025 |  |
| Jukebox | JUKEBOX | KEEP | 0.15 | 0.134 | 0.0025 |  |
| Neon frame | NEON_FRAME | KEEP | 0.15 | 0.128 | 0.0007 |  |
| Magazine | MAGAZINE | KEEP | 0.18 | 0.192 | 0.0040 |  |
| Pan & zoom | COVER_FOCUS | KEEP | 0.55 | 0.234 | 0.0448 |  |
| Disc & sleeve | DISC_SLEEVE | KEEP | 0.34 | 0.135 | 0.0206 |  |
| Album tide | ALBUM_TIDE | KEEP | 0.24 | 0.211 | 0.0018 |  |
| Still dusk | STILL_DUSK | KEEP | 0.10 | 0.137 | 0.0000 | Still by design (no motion). |
| Still horizon | STILL_HORIZON | KEEP | 0.30 | 0.098 | 0.0000 | Still by design (no motion). |
| Still glow | STILL_GLOW | POLISH | 0.15 | 0.031 | 0.0000 | Nearly black and flat (detail 0.031, lowest of all): on an OLED it is indistinguishable from a blank screen. A slightly stronger glow would keep it calm but visible. |
| Light ribbon | CALM_RIBBON | KEEP | 0.08 | 0.064 | 0.0223 |  |
| Night drift | CALM_DRIFT | KEEP | 0.16 | 0.035 | 0.0136 |  |
| Soft halo | CALM_HALO | KEEP | 0.12 | 0.056 | 0.0077 |  |
| Bold split | BOLD_SPLIT | KEEP | 0.31 | 0.130 | 0.0000 | Still by design (no motion). Palette contrast fixed in 427. |
| Bold dots | BOLD_DOTS | KEEP | 0.33 | 0.110 | 0.0000 | Still by design (no motion). |
| Bold bands | BOLD_BANDS | KEEP | 0.28 | 0.252 | 0.0000 | Still by design (no motion). |
| Sakura | PETALS | KEEP | 0.15 | 0.110 | 0.0242 |  |
| Arcade CRT | SHOWCASE_CRT | KEEP | 0.06 | 0.079 | 0.0169 |  |
| Deep sea | BUBBLES | KEEP | 0.39 | 0.150 | 0.0412 |  |
| Handheld boot | HANDHELD_BOOT | KEEP | 0.23 | 0.230 | 0.0121 |  |
| Pocket beat | POCKET_BEAT | KEEP | 0.30 | 0.176 | 0.0278 |  |
| Console disc | CONSOLE_DISC | KEEP | 0.17 | 0.131 | 0.0112 |  |
| Arcade attract | ARCADE_ATTRACT | KEEP | 0.08 | 0.098 | 0.0104 |  |
| Neon cartridge | NEON_CARTRIDGE | KEEP | 0.13 | 0.183 | 0.0265 |  |
| Data stream | DATA_STREAM | KEEP | 0.04 | 0.079 | 0.0110 |  |
| Portal gallery | PORTAL_GALLERY | KEEP | 0.18 | 0.235 | 0.1026 |  |
| Poster wall | POSTER_WALL | KEEP | 0.20 | 0.157 | 0.1019 |  |
| Gaming room | GAMING_ROOM | KEEP | 0.25 | 0.162 | 0.0026 |  |
| Trophy vault | TROPHY_VAULT | KEEP | 0.15 | 0.130 | 0.0159 |  |
| Badge bounce | BADGE_BOUNCE | KEEP* | 0.17 | 0.049 | 0.0226 | Preview shows only the velvet backdrop; the badges (or drawn stand-in trophies with none earned) are drawn by BadgeBounceLayer on the device. |
| Glass blades | CHILL_BLADES | KEEP | 0.15 | 0.146 | 0.0198 |  |
| Polar veil | CHILL_POLAR | KEEP | 0.43 | 0.205 | 0.0140 |  |
| Planet rise | CHILL_PLANET | KEEP | 0.03 | 0.105 | 0.0043 |  |
| Moon tide | CHILL_MOON | KEEP | 0.07 | 0.144 | 0.0039 |  |
| Glass orbs | CHILL_ORBS | KEEP | 0.08 | 0.068 | 0.0110 |  |
| Silk folds | CHILL_SILK | KEEP | 0.18 | 0.147 | 0.0110 |  |
| Dune hour | CHILL_DUNES | KEEP | 0.24 | 0.145 | 0.0019 |  |
| Mesh glow | CHILL_MESH | KEEP | 0.15 | 0.117 | 0.0184 |  |
| Morning light | CHILL_LIGHT | KEEP | 0.25 | 0.101 | 0.0018 |  |
| Leaf shade | CHILL_SHADE | KEEP | 0.46 | 0.085 | 0.0294 |  |
| Rainy window | CHILL_RAIN | KEEP | 0.10 | 0.069 | 0.0216 |  |
| Rooftop dusk | CHILL_CITY | KEEP | 0.14 | 0.117 | 0.0006 |  |
| Jellyfish drift | CHILL_JELLY | KEEP | 0.11 | 0.063 | 0.0094 |  |
| Firefly glade | CHILL_GLADE | KEEP | 0.18 | 0.101 | 0.0032 |  |
| Hex lattice | CHILL_HEX | KEEP | 0.09 | 0.054 | 0.0102 |  |
| Above the clouds | CHILL_CLOUDS | KEEP | 0.31 | 0.166 | 0.0044 |  |
| Koi pond | CHILL_KOI | KEEP | 0.20 | 0.107 | 0.0182 |  |
| Winter peaks | CHILL_PEAKS | KEEP | 0.10 | 0.102 | 0.0014 |  |
| Pixel world | PIXEL_WORLD | KEEP | 0.54 | 0.157 | 0.1535 |  |
| Spark · 32-bit | PX_SPARK | KEEP | 0.42 | 0.211 | 0.1409 |  |
| Torrent · 32-bit | PX_TORRENT | POLISH | 0.67 | 0.175 | 0.0227 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Sprout · 32-bit | PX_SPROUT | POLISH | 0.70 | 0.205 | 0.0110 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Mind · 32-bit | PX_MIND | KEEP | 0.14 | 0.130 | 0.0288 |  |
| Blaze · 32-bit | PX_BLAZE | KEEP | 0.06 | 0.150 | 0.0364 |  |
| Phantom · 32-bit | PX_PHANTOM | KEEP | 0.11 | 0.147 | 0.0266 |  |
| Frost · 32-bit | PX_FROST | POLISH | 0.49 | 0.227 | 0.0351 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Terra · 32-bit | PX_TERRA | POLISH | 0.61 | 0.143 | 0.1006 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Venom · 32-bit | PX_VENOM | KEEP | 0.08 | 0.156 | 0.0188 |  |
| Meadow · 32-bit | PX_MEADOW | POLISH | 0.67 | 0.150 | 0.0038 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Dojo · 32-bit | PX_DOJO | KEEP | 0.36 | 0.163 | 0.0007 |  |
| Gale · 32-bit | PX_GALE | POLISH | 0.47 | 0.175 | 0.0545 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Thicket · 32-bit | PX_THICKET | KEEP | 0.39 | 0.176 | 0.0127 |  |
| Bedrock · 32-bit | PX_BEDROCK | KEEP | 0.29 | 0.135 | 0.0023 |  |
| Wyrm · 32-bit | PX_WYRM | KEEP | 0.15 | 0.158 | 0.0268 |  |
| Umbra · 32-bit | PX_UMBRA | KEEP | 0.12 | 0.085 | 0.0038 |  |
| Alloy · 32-bit | PX_ALLOY | POLISH | 0.49 | 0.194 | 0.0468 | The top of the picture (where home titles, the clock and the category name sit) is bright: white words need the launcher's shadow there. Check on the Thor; if it fails, darken only that band in this scene. |
| Glimmer · 32-bit | PX_GLIMMER | KEEP | 0.31 | 0.150 | 0.0113 |  |

## Device (428) — Thor 3140972f, release build, Classic row, your "Colours follow: the game you're on", real library

26 themes previewed behind the real home screen (not saved; `.shots/428th2`, one frame + one 4 s later each). What the real screen showed:

| Theme | Finding on the Thor | Decision |
|---|---|---|
| Pan & zoom (COVER_FOCUS) | Paused / no music: a calm flat wash; chips, name and row clear | KEEP (device-checked; the WebGL bright title zone was the sample cover, not what shows without music) |
| Pixel world (PIXEL_WORLD) | Busy map behind the category chips; words readable, chips have their own backing | KEEP (device-checked; watch on smaller screens) |
| Lobby | **White chips, name and status on a near-white wash** (game colours give white writing) | FIXED 428: the wash follows the writing (LobbyScene) |
| Manga beat, Comic pages, Magical sparkle, Frost · 32-bit, Gale · 32-bit, Alloy · 32-bit, Blue sky cruise | White chips / name / clock over light areas | FIXED 428: `SceneTextShade` for these scenes only (status band + lower half) |
| Sprout / Torrent / Meadow / Terra · 32-bit, Pixel sky, Summer sky, Meadow, 64-bit plains | Chips and name sit over mid-tone sky / ground; readable | KEEP (device-checked); the WebGL "bright title zone" was the top-left band, which this layout does not use |
| Still glow | A visible dark gradient on the Thor (not the near-black WebGL frame) | KEEP (device-checked) |
| Warp drive | Dark tunnel; no bright burst in the two captures | KEEP (2 captures; frame pacing NOT measured) |
| Lyric cloud / Karaoke / Lyric sheet (no music) | A calm dark fog, not the flat amber of the WebGL frame | KEEP (device-checked idle state; with lyrics NOT checked) |
| Bold split, Pocket LCD | Palettes after 427: chips and name clear | KEEP (device-checked) |
| Pocket shell | Capture taken after the preview ended (invalid) | NOT CHECKED |

Not checked on the device: other layouts than the Classic row, the after-fix captures (the Thor shut down at 1 % battery during this
session), OLED, Lite, legacy fallback, frame pacing.

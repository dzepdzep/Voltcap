# Save Vault — what is stored, why pictures went missing, what changed (Batch 523g–j)

## What is stored / synced / live (before 523g)

| Data | Where | Synced across devices? |
|---|---|---|
| The saves themselves (files, memory cards, PSP SAVEDATA) | emulator folders; Drive `Voltcap Sync/Saves/<folder>/…` | yes (CloudSync, unchanged) |
| Which games have saves (capsules: key, name, console, hue, ROM path, here / not here, each save's title / place / size / date / kind) | `files/save_capsules_cache_v2.json` (rebuilt from the saves each time) | no — rebuilt on each device from what it sees |
| A save's picture, game here | the library's scraped art (`state.art`, keyed by the ROM's path), your picks (`artOverrides`), RetroAchievements icon | scraped art: **no** (per device); picks: yes (launcher-data.json) |
| A save's picture, game NOT here | a fresh look-up by the name on the memory card / file (`save_vault_art` prefs, 7-day retry) | no |
| PS2 save icons | read from the memory card itself (Ps2Icons), else PS2IODB | the card is synced, so yes |
| Thumbnails | Coil's disk cache (by URL) | no (each device downloads) |

## Why entries lost their picture

On a device without the game, the save's picture depended only on a name look-up — and memory-card names are often run
together or abbreviated ("WWESmackDown!vs.RAW"), so nothing was found. The device that HAS the game knew the right cover
but never told anyone: scraped art is per device. With nothing, every look drew two letters on a blank label / square.

## What changed

1. **VaultArtIndex** (`data/VaultArtIndex.kt`): every device records, for each game here that has saves, its console
   (canonical key), the names its saves go by (game name, file name, each save's own title — normalised to letters /
   digits) and the **web addresses** of its cover, logo and RetroAchievements icon. Kept in `files/vault_art_index.json`
   and synced as `Voltcap Sync/vault-art.json` (merged by union both ways; ~200 bytes a game; never a picture's bytes,
   never a local path; an unreadable Drive copy is left alone). A device without the game finds the entry by console + any
   of those names. Order for a save's picture: this device's art → recorded look → name look-up (only when no recorded
   cover) → designed plate.
2. **Designed fallback (VaultArtPlate)**: a restrained gradient in the console's colour (the Smart App Artwork tones),
   the console's own symbol in white (or its short key, e.g. "PSP", in whole words) and, where there's room, the game's
   name. Used on labels (memory card, SD card, floppy, capsule, 3D card fallback), Classic squares, the detail view.
3. **Icons only** is flat icons only: RetroAchievements icon → logo → recorded icon / logo → the console plate as a rounded
   tile. No cover in place of an icon, no turning badge.
4. **PS2 browser** is its own look (added last to the enum: saved choices unchanged): PS2 saves show their own 3D icon
   (PS2IODB's when the card's can't be read), every other game its logo — or, with none, its console's emblem — as a solid
   badge turning like a PS2 icon; nothing at all → the plain memory card. The old "PS2 browser style" switch still applies to
   the card looks.
5. Saves, sync folders, the capsule cache format and launcher-data.json are unchanged. The new Drive file is additive;
   older versions ignore it.

## QA (isolated QA app, `voltcap_test_vault_fixture`, display only)

Fixture: 6 games of the library with a save each (no scraped art on the GT78: the "missing artwork" case) + 6 games NOT
here: WWE SmackDown! vs. RAW (PS2), Pokemon Crystal (GBC), Metroid Prime (GameCube) with a recorded look (QA portrait
pictures standing in for synced web covers), Chrono Trigger (SNES), Wipeout 2097 (PS1), Golden Sun (GBA) with none.
`voltcap_test_vault_before` draws the same saves as before 523g. Evidence: GT78 kv523h / kv523i / kv523j (before / after
per look: grid, further down, a save opened).

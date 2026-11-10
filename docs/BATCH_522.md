# Batch 522 (10 Nov 2026, local on next/0.6.70): badge & logo audit, controller-glyph sweep, Tile home independence, GameNative / DroidDeck

Everything on the RP Classic (MC94516AQF091001112, 620×540 dp) ran in the **isolated QA app** (`com.shelf.launcher.qa`,
debug key a48b27ed…; build line `verifyQaIsolation: … = com.shelf.launcher.qa` checked before every install). The real
Voltcap (0.6.69 vc76, pid 10087 before and after every run) was never installed over, launched, cleared or changed. No system
settings were changed (font / density scaling used the QA-only `voltcap_test_scale` hook). No push, merge, publish.
QA builds: `qa-0.6.69-522a` (31622f12…), `qa-0.6.69-522b` (6453cce4…) in tools/qa/builds.json.

## 1. Console badge & logo audit

Inventory (tools/qa/tools/badge_audit.py, read from the shipped code + assets): badge wordmarks 49, badge symbols 22, folder
logos 44 (+44 PNG), mono icons 44, ES-DE system logos 65 (+65 PNG); 69 consoles. **0 broken references.** Only unused
assets after this batch: badge_logos/3DO.svg and badge_icons/3DO.webp (kept on disk).

Rendered gallery: `voltcap_test_badgegallery N|aN` (QA only) draws every console with the real `ConsoleBadge` / `PlatformLogo`
composables, grouped by maker, 15 pages × (badges view, assets view), 96 dp and 140 dp tiles, one grey. Before = bg3, after =
b522after (`Claude outputs/b522/`), plus GRID / TILES / CLASSIC with stand-in games for PS5, Xbox One, Xbox Series, Switch 2,
Mega Drive, TG16, Famicom, SFC, NGPC (empty files in the QA app's own library). Device script: tools/qa/device/badge521.sh.

Confirmed defects, fixed (verified on the after gallery):

| Defect | Fix |
|---|---|
| Famicom badge drew the NES wordmark, Mega Drive "GENESIS", TG16 / TGCD PC Engine, NGPC Neo Geo Pocket (badge ALIAS) | aliases removed; own ES-DE logo or own name |
| Mega Drive / TG16 / TGCD folders used the Genesis / PC Engine logo (PlatformLogos ALIAS) | removed; own ES-DE logos |
| "FAMICOM DISK SY…" truncated at 96 and 140 dp | short names, auto-sized, no ellipsis |
| White silhouettes: 3DO, DOS, Genesis, Mega Drive, MSX, MSX1, Pokémon mini, Sega CD, Vectrex ES-DE logos became solid white blocks in one colour | `SystemLogos.SILHOUETTE_UNREADABLE`: those show the name in the small badge and in tinted folder logos (`PlatformLogo`, `PlatformLogos.oneColour`) |
| 3DO badge-set symbol / wordmark are dark-on-white → white rectangle | 3DO out of ICON / ASPECT → "3DO" text |
| Grey "no colour" pills: PS4, PS5, Xbox 360, Xbox One, Xbox Series, Switch 2, Famicom, SFC, Mega Drive, SG-1000, TG16, TGCD, NGPC, WSC, Atari 5200 | family colours |

Needs the owner's decision (alternatives, nothing changed): one look per badge mode (text vs logo in "Console badges";
22 symbols vs 27 wordmarks in "Logo badges"); very wide wordmarks (GBA 11.2:1, Lynx 10.6, Genesis 8.6, GBC 8.3, 3DS 8.2,
Pokémon mini 8.1) small at badge height; PS5 / Xbox One / Xbox Series / Switch 2 have no artwork anywhere (BLOCKED-REF);
Genesis key's short name "Mega Drive" vs Logo badge "GENESIS"; RA icons (account-only, regional keys get none).

## 2. Controller glyphs / popups (existing popclip 53 layouts × styles, extended)

Extended, not rebuilt: QaClip dumps every InkGlyph's ink box (`"glyphs"`); `tools/qa/tools/glyphcheck.py` measures each glyph
(InkGlyph boxes + laid-out button labels A B X Y, L1–R3, ZL/ZR, LB…RT, START, SELECT, MENU, HOME, P1, arrows, PS shapes)
against the circle / pill round it on the screenshot; popclip.py has a new `glyphs` category (home, main, More, page steps).
Not graded: HIDDEN (in the dump, not drawn — under a popup), NO-SHAPE (no closed shape, a labelled button such as
"◀ Saves ▶" or "▶ PLAY", a box far bigger than a key). A label drawn on its own key (Terminal's green blocks) is measured
inside its box. Self-tests: 137 pass (GlyphCheck521 ×3).

Run p522own (OWN style, all 53 layouts, build 522a): popup gate 47 PASS / 6 FAIL — none of them glyphs:
BLADES readability, BUBBLES / CAMERA / ORBIT / STORAGE geometry (long console name cut at maxLines 1 etc., known families),
CUBE "first A opened nothing". **Glyphs: 43 measurements on 13 layouts (A, B, X, Y, R1, R2, L2, START, SELECT on Spotlight,
Memory card, Card binder, Pocket Quest, File select, Green Core, Library, Terminal, Pocket ribbon, Camera …): 0 off centre.**
Worst: Pocket ribbon START (pixel font, plain Text) 1.5 px low = 4.4 % — inside tolerance, looked at: reads centred.
Uncertain cases inspected by eye (crops): Green Core / Library outlined L2 R2, Memory card L2, Terminal keys — centred.
Runs p522scale (OWN at font ×1.3, density ×1.15) and p522sty (BANNER, ROW_OPENS, ROW_REVEAL, SHARED, CLASSIC) were started
on build 522b; results go in §6 when graded.

## 3. Tile home (RP Classic, QA app; Flip 2 not connected)

tools/qa/device/tile522.sh (dumps + screenshots per step): row moved (-90 dp), shortcuts moved, each reset alone, R1 Row
Top / Bottom / Middle.
- Reset the row: the shortcuts stay where they were put. Reset the shortcuts: the row stays at its moved place. R1 Row
  Top / Bottom / Middle: everything on screen, profile picture and status row clear, Middle = the untouched layout.
- **Confirmed:** moving (or resetting) the round shortcuts moves the game row and the category chips by 18 px and shows /
  hides the game-info line, because a moved part leaves the layout's flow (`FlowSlot` keeps its space only while editing).
  Changing that touches every layout with flow parts → options for the owner (A: keep the moved part's space so nothing
  else moves — leaves a gap; B: today's re-flow). Not changed.
- A row moved up by hand can cover the category chips (the owner's own placement; R1 Row puts it back).

## 4. GameNative and DroidDeck

Neither app is installed on the connected handhelds (RP Classic, GT78: `pm list packages` has neither); GameNative is on the
Odin 3 (not connected; not signed in, BLOCKED-OWNER since 478). **No real launch could be made this batch: launch success =
NOT TESTED.** Contracts checked against GameNative's source (master: AndroidManifest, IntentLaunchManager, ShortcutUtils,
FrontendSyncManager) and DroidDeck's release notes (0.3.1 – 0.6.9; its source isn't public).

GameNative
- Launch: explicit `app.gamenative/.MainActivity` (exported), action `app.gamenative.LAUNCH_GAME`, int `app_id` — matches.
  Fixed: Voltcap now sends exactly what GameNative's own per-game shortcut sends — `game_source=STEAM` and
  `FLAG_ACTIVITY_CLEAR_TOP` (MainActivity is singleTop; without CLEAR_TOP a GameNative left on another screen only came to
  the front instead of starting the game).
- Discovery: `.steam` frontend exports (content = AppID) → Steam category, key `steam:<id>`, deduped with hand-added /
  folder-imported AppIDs (unit tests 478). GameNative also writes `.epic`, `.gog`, `.amazon`, `.pcgame`: **not imported**
  (needs a store field + category design; GOG ids can be written as 0) → recommendation.
- `gamenative://run?...` exists in GameNative's code but no manifest filter matches it → not used (no invented links).
- Errors: missing GameNative now says so ("GameNative (…) isn't installed or didn't accept the launch") instead of
  "check the emulator set for Steam". An unknown AppID is GameNative's to handle (it returns no game; Voltcap can't see that).

DroidDeck
- Per-game shortcuts ("Add to home screen", 0.3.1+): taken as pinned shortcuts and started with
  `LauncherApps.startShortcut` (the shortcut itself, nothing rebuilt), Steam category. Needs Voltcap as home app (message).
- `.droiddeck` launch files: Play sends `droiddeck://game/<id>` to `com.droiddeck.launcher` (the id read from the file).
  Fixed: files are validated at scan (empty / non-id files were listed as games that could only fail), BOM tolerated, and
  they go in the Steam category like DroidDeck's shortcuts (were "DroidDeck" or the folder's name); the key stays the file
  path (play time etc. kept). Dedupe: a DroidDeck shortcut and a `.droiddeck` file with the same title are one game, the
  shortcut kept. Emulator rules / guards now know DroidDeck for shortcuts too (platform no longer says it).
- The `.droiddeck` format and `droiddeck://` scheme are not in DroidDeck's public notes (closed source): to be confirmed
  on a device with DroidDeck installed (`dumpsys package com.droiddeck.launcher`).
- "Intent accepted" vs "game started": Voltcap only knows Android accepted the start (`LaunchOutcome.Launched`); it records
  the session from there. Whether the game itself started is NOT verifiable from Voltcap and was NOT TESTED.
- Return: Voltcap is the home app; DroidDeck 0.6.7 returns to the launcher a game was started from when it closes.

Unit tests: GameClients522Test (ids, scan, Steam category, dedupe) + GameNative478Test; full testDebugUnitTest PASS.

## 5. Safety

- MCP install guard: **not active** — the PC tool (voltcap-adb, announced) still offers the old `install_build`
  (release / compat / debug) and no `install_production`, i.e. the pre-521 server.js. The desktop app needs a restart (or its MCP config points at another server.js). Build-side
  protections held (debug = QA package + debug key; Android refuses it over the real app).
- CLAUDE.md: stale "Allowed" lines now say "in the isolated QA app"; production installs only through the release path.
- GT78 (BW0306N230000845) identified read-only: Android 12, 960×640 @240, real Voltcap 0.6.67 vc74; its QA app
  (0.6.68-qa516a) is signed with the release key, so the debug-key QA build can't be installed over it without removing that
  QA app first (not done; owner's call).

## 6. Scaled run and the other popup styles

p522scale (OWN × 53 at font ×1.3 / density ×1.15 through `voltcap_test_scale`, build 522b): popup gate 47 PASS / 6 FAIL (after the glyphcheck fix below).
Non-glyph FAILs = the same known families as at ×1.0 (BLADES readability, BUBBLES / CAMERA / ORBIT / STORAGE geometry).
Glyphs: 35 measured on 8 layouts PASS (worst Spotlight A, 1 px = 1.8 %), **1 real defect: Green Core's header keys L2 / R2
7.5 px low (11.7 %)** — its header is a fixed 30 dp and the font-scaled Text line box outgrew the key. Fixed: GcChip draws
its label with InkGlyph (monospace face, dp-sized, ink-centred) like the other key caps. Device re-check pending (build 522c).
Three more FAILs of the first grading were false positives (glyphs of the home under a popup / page window, Library L2,
Memory card R2, Spotlight START): glyphcheck now leaves out a glyph whose window has a later window over it (self-test).

p522sty (BANNER, ROW_OPENS, ROW_REVEAL, SHARED, CLASSIC × 53 on 522b) is running on the RP Classic (≈ 2 h).

### Numbers so far
- Popup types × screens inspected: 53 layouts × OWN at ×1.0 and ×1.3 = 106 combinations, 4–5 steps each (home, popup, page,
  More, closed): 459 + 446 screenshots with their QaClip dumps.
- Controller glyphs measured: 78 (43 + 35); misaligned: 1 component (Green Core header key: L2, R2, also ◀ / SORT / SEARCH
  through the same chip), fixed. 0 misaligned at ×1.0.
- Before / after: the OWN ×1.0 InkGlyph results confirm the 521 fix (0 off centre) — the 521 Flip 2 before / after pair is
  the "before"; Green Core ×1.3 before = p522scale, after = pending.
- Navigation: A opened every popup (Cube at ×1.0: first A opened nothing, the second did; PASS at ×1.3), B closed all,
  focus on the first action and buttons (Details / More / Play taps) PASS 53 / 53 in both runs; launches blocked (QA guard).
- Not verified: Flip 2 (not connected), KONKR, Thor; the popup styles other than OWN until p522sty is graded; frame-by-frame
  motion of glyphs; R1 panel keys (QA never presses A in R1).

## 7. Batch 522 second part (owner decisions of 10 Nov) — builds 522e / 522g, RP Classic, QA app only

### Full popup sweep: 53 layouts × 6 styles (existing popclip, graded with the extended glyph check)
| Run | Build | Combinations | PASS | FAIL | SKIP (style not offered) |
|---|---|---|---|---|---|
| p522own (OWN, ×1.0) | 522a | 53 | 47 | 6 | 0 |
| p522scale (OWN, font ×1.3 / density ×1.15) | 522b | 53 | 47 | 6 | 0 |
| p522sty (BANNER, ROW_OPENS, ROW_REVEAL, SHARED, CLASSIC) | 522b | 265 | 161 | 83 | 21 |

Every FAIL was looked at; defects vs test limitations:
- **Defects, fixed:** console name cut without a mark (Storage, Camera, Orbit, Bubbles' own window — rule 11); Blades' facts
  laid out 0 px wide beside a too-wide cover; Banner style "LAST PLAY…" / "Never pla…" (52 combinations); Row opens / Row
  reveal title and console line ending in "…" (28); Green Core L2 / R2 keys 7.5 px low at font ×1.3.
- **Re-checked on 522e:** p522gc (Green Core, Library, Memory card, Spotlight at ×1.3) 4 / 4 PASS, Green Core keys dx 0 / dy 0
  (before 7.5 px); p522fix (Storage, Bubbles, Camera, Orbit, Blades ×1.0) 4 / 5 PASS — Bubbles' own window still cut → fixed
  in 522g (StyleBubbles), re-check p522bub; p522fix2 (6 layouts × Banner / Row opens / Row reveal) 16 / 16 PASS, 2 n/a.
- **Test limitations, not app defects:** Twin showcase "78 %" battery text 3 px clipped under the page that covers it (not
  visible); Cube Shared ▲▼ measured against the Play button drawn over them (glyphcheck now ignores ink touching its
  shape); Library / Memory card / Spotlight glyphs of the home under a popup window (now left out).
- **Glyphs:** 382 measured in p522sty (L1 R1 A B X Y L2 R2 START SELECT on 79 screens), 43 + 35 in the OWN runs:
  0 off centre after the Green Core fix.

### Console badges: three looks (owner decision 1)
`Features.consoleBadgeStyle` (Text / Symbol / Logo, default Logo; the old Small / Big logo switch was two logo looks):
Game look › Console badges chips and the R1 tile (Off → Text → Symbol → Logo). Text = short name only; Symbol = the set's
symbol where it has one, else short name; Logo = the console's own wordmark, else its readable ES-DE logo, else short name.
A wordmark that would be drawn under 90 % of its size in the pill (tile < 150 dp: pill 78 % of the tile; bigger: 85 %) shows
its symbol / name there and the whole wordmark on big tiles (GBA, Lynx, Genesis, GBC, 3DS, Pokémon mini compact at 96 dp,
whole at 200 dp). Regional keys never borrow a wordmark (SFC ≠ SUPER NINTENDO, Famicom ≠ NES, Mega Drive ≠ GENESIS,
WSC ≠ WonderSwan); symbols both models print stay shared. Genesis key's short name is "Genesis" (was "Mega Drive").
Unit tests Batch522Test. Gallery b522modes / b522final: Text / Symbol / Logo at 96 dp + Logo at 200 dp, every console.
PS5 / Xbox One / Xbox Series / Switch 2 artwork: docs/ARTWORK_522_NEW_CONSOLES.md (sources, owner download steps).

### Tile home (owner decision 2) — Tile-specific, other layouts unchanged
- The round buttons' room under (or over) the tiles is kept while they float (moved, or Edit Home open): measured, not drawn
  (`ReservedRoom`), so the row, chips and name stay put; the facts strip no longer appears in their place.
- Edit Home no longer moves the row at Top (the profile-picture room is kept while editing: it moved 50 dp).
- The "dropped near the tiles → back by the tiles" rule only applies to a drop made in that Edit Home session: resetting the
  row had moved the tiles close to the untouched buttons and snapped them back (found by tile522b, MIDDLE and BOTTOM).
- QA: `voltcap_test_edithome open|save|discard|reset:<id>|resetlayout:<LAYOUT>` + tile522b.sh + tile522.py (row, chips and
  the round buttons' row compared step by step at Middle / Top / Bottom; everything inside the screen). 522e run: row / chip
  steps PASS except Top Edit Home open (fixed in 522g); buttons kept their place through row reset / layout reset: FAIL at
  Middle / Bottom (fixed in 522g). **522g re-run t522c: 39 / 39 PASS** (row, chips and the buttons' row unmoved through
  buttons moved, Edit Home open, Reset this part (buttons, row), Reset this layout and Save, at Middle / Top / Bottom; nothing
  off screen). R1 Row places as in 521. RP Flip 2: not connected — NOT TESTED there.

### GameNative / DroidDeck (owner decision 3)
- GameNative `.epic` / `.gog` / `.amazon` / `.pcgame` exports imported (GameNative source: FrontendSyncManager writes the int id;
  IntentLaunchManager takes `app_id` int + `game_source` EPIC / GOG / AMAZON / CUSTOM_GAME; PluviaMain resolves it per store
  and shows "game not installed" otherwise). Categories Epic Games Store / GOG / Amazon Games / PC games; key
  `gamenative:<store>:<id>` (an Epic id equal to a Steam AppID is another game); GOG 0 skipped. Launch = the same intent with
  that store. Unit test GameNativeStores522Test. No real launch: GameNative not on a connected handheld — NOT TESTED.
- DroidDeck: format still unverified (closed source; ES-DE's Android docs not reachable from here) → no change to its launch;
  its own home-screen shortcuts stay the preferred entry (`LauncherApps.startShortcut`, nothing rebuilt).

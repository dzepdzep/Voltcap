# VoltCap master backlog — single source of remaining work

Last reconciled **11 Nov 2026 (batch 523; base reconciliation batch 520)** against main `a1f762c` (v0.6.69, versionCode 76), the batch 505–519 evidence
(project docs POPUP_BATCH_505–516, SECURITY_519_RELEASE), ROADMAP_070.md, REQUESTS_CHECKLIST.md, CARTS_468/469,
E1E2_REFS_487.md and the code. Where an older document still says OPEN, the newer evidence listed here wins. ROADMAP_070.md and
REQUESTS_CHECKLIST.md are history; update only this file.

Statuses:
- **DONE**: implemented and verified on hardware (evidence named).
- **DONE-O**: implemented; needs the owner's eyes or hands.
- **PARTIAL**: part implemented or verified; what is left is named.
- **OPEN**: reproducible and not fixed.
- **BLOCKED-HW / BLOCKED-REF / BLOCKED-OWNER**: needs a device, a photo / measurement, or an owner action or approval.
- **DEFER**: not planned (risk or scope).

## Protected baselines (regression only, never redo)

| Area | Evidence |
|---|---|
| GBC transparent-shell PCB renderer (GbBoard: mapper, ROM / SRAM, battery, crystal, contacts, traces, both PCB sides, depth in clear plastic) | 468 / 469 |
| Real cartridge photo priority + PhotoShells (RealScans464Test) | 463 / 464 |
| GB-family side geometry, PCB in Home + Inspect | 469, 512 cart512 |
| Cardboard Box 1 / 2 / 3 rows; D131 corner shortcut; D142 Twin Desk hinge / popup | 438, 4xx |
| InputContract + category parity (L2 / R2, picker, tabs; D278 / D279) | 462, 482–484 |
| 53-layout game windows; Follow layout / Instant / Motion Off; case flights | 504–508 |
| Game shelf art-flash fix, idle settle (Shelf bob, effects, focus pulse, charging light, picker) | 510, 511, 513 |
| Custom CD colour logos + regional rules | 508–510 |
| R0 / R1 / R2 settings, badge panels, FullText, full-screen profile picker | 491, 503, 514 |
| v0.6.69 ScreenScraper / IGDB through Voltcap's service (signed media, no keys in the APK) | 517–519 |
| Xbox / PlayStation case fixtures (dimensions, spines X360 / PS3 / PS4, back bands, plastics, tray) | 516 |
| Thor Ken Burns smooth drift (511; the 512 / 513 stepped and paced variants are rejected) | 513 |
| Case Stacks recomposition fixes (D137: 493 / 494) and the 13 carousel arrangements | 493–494 |

## 1. Done and verified (newest first; only what changed status recently)

| Item | Evidence |
|---|---|
| Row-style windows over the home: header / category chips / Tile home tiles at full strength under the window (duplicated title) | FIXED (523w / 523y), D301; KONKR + Flip 2 shots; final gate on 523y running |
| Banner Game info on 16:9: facts half under the edge (Flip 2 ×52); Back hint over scene keys (×4); Storage saves line < 24 dp | FIXED (523x), D299 / D300; re-grade in the 523y gate |
| QA tooling on Android 12 FUSE (GT78): request files unreadable → no dumps (kp523u INFRA 318) | FIXED (QA tools), D302 |
| Approved popup direction (lighter Roboto, Play / Details / •••, Back hint, Storage layout) | DONE-O (523t / 523u): all popups; KONKR + Flip 2 before / after; popup gates on 523u running |
| Cover details recomposed to the owner's sketch (big case upper left, angled 3D cases on a compact lower-right shelf, Endless each game once, case flight shelf ↔ big place) | DONE-O (523r / 523s): KONKR cd523s screenshots + recordings; owner review; Flip 2 / Thor NOT TESTED |
| Controller contract 16 layouts on Flip 2 and KONKR (523o) | 80 / 80 each (fc523p, kc523q) |
| Tile home independence on Flip 2 (523o) | PASS (ft523p, tile522) |
| Popup gate 523: Details in the popup's own style (Row opens / Row reveal / Shared) + Storage box layout | RP Classic p523f (×1.0, 523f): 244 PASS / 52 FAIL → Shared's story box < 24 dp × 46 FIXED 523o, verified p523or (135 PASS; Shared 46 / 46); remaining FAILs = Crate home "overlap", a checker false positive (D297). ×1.3 run p523ob (523o) — see docs/BATCH_523.md §6 |
| Coverflow details pickup / return as one object; Game shelf extraction (slide, turn in place, forward); Case flow each game once | FIXED (523f), recordings m523f (Classic) / km523f, km523l2 (GT78, Full); `Batch523PickupTest`, `Batch523CaseFlowTest` |
| Endless with few games: each game once (fill off) | FIXED (523d / 523f), e523f / ke523f (1, 2, 3, 36 games) |
| Save Vault art for saves whose game isn't on this handheld (VaultArtIndex, synced vault-art.json), console plate instead of two letters, Icons only flat, new PS2 browser look | FIXED (523g–j), GT78 kv523h–j + RP Classic v523n before / after; docs/qa523/vault_audit.md |
| Smart App Artwork (tones cached, wide / hero compositions, no 28 dp blur, neutral plate) | 523d / 523g, RP Classic a523n (JoiPlay / FolderSync / Obtainium × 12 layouts) |
| Crash switching Performance Automatic ↔ Full / Lite (`remember` inside a `when`) | FIXED (523l), GT78 flip523: 6 / 6 crashes before, 0 / 6 after |
| Deleted games still listed after a rescan (orphan .m3u / cue / gdi; empty explicit rescan kept the list) | FIXED (523g), `DeletedRoms523Test`; device test inconclusive (adb-made files unreadable to the app) |
| Storage boxes menu broke "ACHIEVEMENTS" mid-word (GT78) | FIXED (523n), lab523n |
| Smart Fill (portrait in Tiles, Hero portrait whole), Inspect zoom real multi-touch, Cinema platform icon | 523b–e, s523before / after, ins523 |
| KONKR memory soak + Case Stacks fast scroll (mem520 / perf514) | 523 konkr523c: PSS plateau after round 1 (440 MB, 66 MB released idle) — cache, not a growing leak; Case Stacks burst p90 36 → 26 ms, janky 10.4 → 3.2 %; ctl516 55 / 55 |
| Console badge / logo audit: 69 consoles, 0 broken references; wrong regional branding (Famicom→NES, Mega Drive→Genesis, TG16 / TGCD→PC Engine, NGPC→NGP badges + folder logos), "FAMICOM DISK SY…" cut, one-colour white blocks (3DO, DOS, Genesis, Mega Drive, MSX, Pokémon mini, Sega CD, Vectrex), 3DO white rectangle, grey pills (PS4 / PS5 / Xbox 360 / One / Series / Switch 2 / regional keys) | FIXED (522), RP Classic QA-app gallery before / after (15 pages × badges / assets + GRID / TILES / CLASSIC), docs/BATCH_522.md |
| Popup sweep 53 layouts × 6 styles (522) | DONE on RP Classic: p522own / p522scale / p522sty graded; every FAIL fixed or classed as a test limitation (docs/BATCH_522.md §7); re-checks p522gc / p522fix / p522fix2 / p522bub. KONKR / Flip 2 / Thor NOT TESTED |
| Controller glyph centring in popups / hint bars (InkGlyph, 521) | 522: popclip `glyphs` category + glyphcheck.py; OWN × 53 layouts on RP Classic: 43 glyph measurements on 13 layouts, 0 off centre; font ×1.3: Green Core header keys 7.5 px low → FIXED (InkGlyph), device re-check pending; 5 other styles running (docs/BATCH_522.md §6) |
| GameNative launch intent = GameNative's own shortcut (`app.gamenative.LAUNCH_GAME`, int `app_id`, `game_source=STEAM`, CLEAR_TOP); DroidDeck `.droiddeck` files validated, Steam category, shortcut / file dedupe; missing-app messages | 522, checked against GameNative's source; unit tests (GameClients522Test, GameNative478Test). Real launch NOT TESTED (neither app on a connected handheld) |
| Tile home placement on the RP Flip 2: Edit Home row move + hand-placed buttons fought R1's Row place (Middle gap, Top off-screen, Bottom on the buttons); Top hid the "All" chip under the profile pictures | FIXED (521), Flip 2 + Classic QA app Top / Middle / Bottom PASS, `Batch521Test` |
| QA install protection (process risk 5b) | FIXED (521), see 5b |
| Button letters (A B X Y, L2, START …) not centred in their circles (owner) | FIXED (521), `InkGlyph`; Flip 2 before / after |
| RP Flip 2 hardware gate (controller 16 layouts, art, Tile home) | PASS (521), 80 / 80 |
| Automatic art / details runs no longer skip a game for two weeks after a ScreenScraper timeout / busy / limit (it was recorded as "asked") | 520, `artAskCounts` + unit test; device: not forceable without changing the production service |
| Game grid ▲ ▼ with every game in one row (Endless on) stay on the game instead of jumping to (cols mod n) | 520, unit test + RP Classic q521 (100 % and 50 %: Zelda stays after ▼ and ▲) |
| Game spacing for Hero tiles and Cinema (own options + R1; 100 % = as before) | 520, unit tests + RP Classic q521 screenshots (100 / 50 / 200 %) + controller pass |
| Game spacing for the Game grid (own option + R1) | 520, RP Classic grid520 (100 / 50 / 180 %) |
| Controller contract after all 520 changes: R1, L1, Select, Start, R2 / L2 open and return focus | 520 final gate, RP Classic (QA build 10:24): 16 layouts × 5 = 80/80 PASS (CLASSIC TILES SHELF STACKS COVERDETAILS CASEFLOW GRID HERO_TILES CINEMA BOX STORAGE FILESELECT CHAN TWIN_DESK CUBE ARCTIC), 0 fatal, 0 launches, settings restored; q520 art screenshots identical to the earlier verified run (18/18, mean diff ≤ 0.07) |
| Flat case art fits like the 3D faces (CropUnlessMuchCut): a 16:9 picture you picked is shown whole on the Game shelf (Plain cover), plain PS3–PS5 / Xbox cases; covers close to the window still fill | 520, RP Classic q520 (wide fixture, before / after) |
| A picture taken for a whole wrap whose front cut fails (no folds: a 16:9 picture on a PS1 jewel case) now shows the picture, not an empty case | 520, RP Classic q520 (PS1 Crash, Voltcap's case / Real case) |
| Unmeasured wraps / unfolded box scans load hidden until their shape is known (no flash of the whole scan or its spine before the front) | 520, code + unit tests; device: no flash seen in q520 (a one-frame flash can't be proven absent by screenshots) |
| ScreenScraper account check: "Testing…" stays (was cleared after 3.5 s while ScreenScraper can take ~45 s), Connect disabled while checking, slow-answer note; status messages in account windows stay until replaced, elsewhere 3.5–10 s by length | 520, unit tests — **implemented, device NOT TESTED** (the QA app is signed in with the owner's account; signing it out to see the Connect state was not allowed) |
| Collector backs: a colour logo under 3 : 1 on the dark header bar (PS4 blue 1.8 : 1) is printed white; Xbox 360 green, PS3 / PS5 / Xbox One unchanged | 520, unit test + RP Classic xcase516 re-run (PS4 back before / after; other four backs checked) |
| v0.6.69 published; production Worker; real-provider checks; QA app on the Classic | 519 (SECURITY_519_RELEASE) |
| KONKR (= GT78) 53-layout regression, rapid A / B 96 / 96, controller contract, idle 0.5–1.3 % CPU | 516 |
| Xbox / PlayStation cases at 6 poses (dimensions, spines, bands, plastics, tray) | 516 |
| Rapid A / B on KONKR (B while opening / open / closing) — the 513 / 514 intermittent "window left open" not reproduced in 96 | 515–516 |
| E3 case print crop (D280), E4 GBC box spines, E5 Case Flow reflection, E6 controller contract | 484–485 |
| D257 / D263 / D265 / E7 owner-approved popup decisions | 480d, 487 |
| K3 Save Vault card looks; K5 CI | 488 |

## 2. Implemented, needs the owner (DONE-O)

| Item | What to check |
|---|---|
| Inspect two-finger pinch / pan (498; one-finger pause verified) | finger test |
| K2 home chips above screensaver / popups (487 fix) | does it come back on your Thor? |
| K4 custom spine flicker while scrolling (490: 82 → 0 / 90 → 0 measured) | visual check |
| Case tops / Stacks spine depth, Disc Station / Circuit popups | look approval |
| Title / logo slot remembered (475 / 503) | no visit-to-visit jump seen; no dedicated recording |
| Header backdrop cross-fade to "none" (~0.4 s) | keep or remove |
| Flat Game shelf wide pictures (520): the strips round a whole wide picture show the game's colour gradient | keep, or a plain dark plate |
| ~~Popup design lab (523m / n)~~ | APPROVED 11 Nov → implemented 523t / 523u (see section 1); Inter / Manrope stay QA-only |
| (was) **Popup design lab** | pick: (1) Play / Details / ••• hierarchy + Back as hint + one step lighter on the current font [recommended]; (2) Inter later, after its own ×1.0 / ×1.3 gate; Manrope not recommended; Storage lab layout yes. docs/qa523/popup_lab.md, Batch 523 evidence page |
| Game shelf extraction / Coverflow pickup motion (523f) | owner's eye on the device |

## 3. Genuinely open and reproducible

| Item | Status / next step |
|---|---|
| KONKR native / graphics memory growth under heavy navigation (183 → 433 MB, 383 at rest; Classic flat) | 523: soak run on the GT78 = plateau after round 1 (161 → 428 → 440 → 440 MB, 374 idle) — cache, not a growing leak; kept for a Thor / Odin comparison only. Earlier: OPEN — suspected MediaTek driver / texture caching, **not** a confirmed leak; needs a KONKR soak (BLOCKED-HW). 520: one-command soak `tools/qa/device/mem520.sh TAG [rounds]` (10 layouts × 20 moves + window per round, meminfo per round, after 60 s idle and after a critical trim; no HOME press — the real Voltcap is the KONKR's home app). Classic reference run (3 rounds): PSS 108 → 184 / 193 / 200 MB, 195 idle, 194 after trim; Java flat 10–17 MB, native 114 → 121 MB (+3 % per round, flattening) |
| Case Stacks fast scroll on KONKR: burst p90 36 ms / 10.4 % janky (Shelf 0 %) | IMPROVED (523): GT78 burst p90 26 ms / 3.2 % janky |
| Coverflow details steps on the GT78: 15–16 ms frame median (burst janky fixed 12.8 → 0.6 % in 523i) | EXPLAINED (11 Nov): UI thread 3 ms, GPU 7 ms; the rest is buffer-queue wait (latency, not work); 0.6–5 % frames miss their deadline. Removing the blur or all effects (Lite) changes nothing → no code change (523p A/B reverted). Further only with a GPU profile (developer option, owner OK). docs/BATCH_523.md §7 |
| Hero on the GT78: GPU 11–12 ms a frame, its popup window 17 ms median | OPEN, low — the heaviest layout on this GPU; next perf candidate |
| Home legend glyphs off centre on the Flip 2 (Pocket ribbon START 3 px, Spotlight SELECT) | OPEN, low (home, not popup) |
| Classic details on Hub / Resume: achievements list squeezed with 10 achievements (Flip 2) | OPEN, low |
| Airy, game without art: the card's fallback title is white on the white card (Flip 2 fe523p) | OPEN, low |
| QA checker: readcheck flags text hidden under another layer (Crate back sleeves) as overlap | OPEN (D297), tools only: the QaClip dump has no draw order |
| ScreenScraper per-game look-ups when the server is slow or refuses | PARTIAL — 520: a timeout / busy / limit / login refusal no longer reads "found nothing for X — try another name": the game's note now says what happened (`screenScraperFailureNote`, from `lookupOutcome`; the note stays on the game until the next look-up). The game shows its loading state meanwhile; the app waits 75 s through the service. Unit tests; no device run with a slow / failing answer (it can't be forced without changing the production service) |
| Cover details first flight frame (1 of 666 frames, Nova) | OPEN, low. 520 RP Classic: 2 recordings (`flash513.sh` COVERDETAILS, 690 + 784 frames): 1 one-frame event, during the fast 5-step burst — the NEW case's first frame (its no-art generated cover drawn at another texture size, then redrawn), not the leaving case's flight; 0 in the second run. Both look like first-frame texture / bounds, not motion maths. 520 code review: a case's flight reads `CoverFlight.spines`, written inside the shelf spines' graphicsLayer during the same draw pass, so a flight drawn before its spine reads last frame's slot (one-frame lag at the start). Not confirmed on a device (the Nova isn't connected; needs a frame recording) → not changed |
| K1 game spacing in the remaining compatible layouts | PARTIAL — 520: **Hero tiles** and **Cinema** got Game spacing (own option + R1; 100 % = today's 8–12 / 8 dp; RP Classic 100 / 50 / 200 % screenshots + controller pass PASS). **Game grid** has its own Game spacing (Layout options + R1, 50–250 %, 100 % = the old 14 / 12 dp; main screen only; RP Classic: 100 / 50 / 180 % screenshots, D-pad, controller pass PASS). R1 Spacing tile + slider also on Case flow, Cardboard box, Storage boxes, File select (their own pref and range; unit tests; controller pass on those layouts PASS; the R1 tile itself reached only by unit test). Carousel (a look, not a layout), Ribbon / Twin (dp gaps), Arctic / Memory card (3-step choices) keep their own controls. The other ~35 layouts have no spacing control: each needs its own design + device check |
| B5 long rows / D137 render cost | PARTIAL — see Case Stacks above |

## 3a. Checked and not a defect (520)

| Item | Finding |
|---|---|
| D-pad in the R1 panel toggling Wi-Fi / Bluetooth / DND | **No.** A tile acts only on A / Enter / tap (`R1Tile` click; edit mode moves tiles with the D-pad only while one is picked). RP Classic: 22 D-pad moves over every tile row — wifi_on, bluetooth_on, zen_mode, brightness unchanged. The risk is a test that presses A blindly; QA scripts must not. The brightness bar does change with ◀ ▶ by design (only when Android allows it). |
| Game grid: Down with fewer games than a row (Endless on) | FIXED (520): ▲ ▼ moved a whole row round the few games and landed on game (cols mod n). Now with all games in one row ▲ ▼ stay on the game (as without Endless); two or more rows unchanged. `gridLoopRowMoves`, unit test; device check in the 520 QA run |

## 3b. Upgrade to v0.6.69 (owner's handhelds)

| Item | Status |
|---|---|
| Part A read-only checks, RP Classic | DONE (520): 0.6.67 / 74 = the published APK (1586accc…), signer 88ce4b67… = 0.6.69's, normal APK → target `Voltcap-0.6.69.apk`. Old QA hooks file in the real app (harmless on 0.6.69; owner decides). Drive switches not readable read-only. Details + mixed-version Drive risk: docs/UPGRADE_069_CHECKLIST.md |
| Part A on KONKR / Thor / Odin 3 | BLOCKED-HW (not connected) |
| P1 inventory, RP Classic | DONE (520, owner-approved): read-only; results in docs/UPGRADE_069_CHECKLIST.md |
| Drive with mixed versions | Reviewed (520): no data loss possible; a 0.6.67 device can't load pictures a 0.6.69 device scraped / cleaned until it is updated |
| P2 save copy, RP Classic | DONE (520, owner-approved): 33 files / 19,185,603 bytes, 33/33 sha256 identical, new dated folder; details in docs/UPGRADE_069_CHECKLIST.md. Found: RetroArch saves are also synced by Syncthing |
| P3–P7, B1–B6 | 521: the owner updated the Flip 2 and the RP Classic to the published 0.6.69 himself (Voltcap's own updater). P3 / P4 owner steps prepared (still worth doing: a settings + layouts copy); P7 only matters for handhelds still on 0.6.67 (KONKR / Thor / Odin 3: not connected) |

## 4. Cosmetic refinements worth doing

| Item | Status |
|---|---|
| PS5 / Xbox One spine logo | BLOCKED-REF — no authentic usable artwork in the pack (documented 508 / 516); nothing invented |
| Front console band only with "Cover templates" on | by design (516) — owner decision if it should change |
| Missing CD logo assets: FDS, SG-1000, Atari 5200, Xbox One / Series, Switch 2, PS5, CD-i | BLOCKED-REF |
| Sega CD category logo | BLOCKED-HW (no Sega CD games in the fixture libraries) |
| Scan stretch / crop audit across layout families | PARTIAL — the shared flat rule fixed in 520; no layout-by-layout audit (not wanted) |
| QA app's R1 panel shows the "Not in the compatibility version" card | QA-build only (the QA app has no notification listener); not a release defect — note only |

## 5. Blocked (hardware, references, owner)

| Item | Blocker |
|---|---|
| Odin 3 navigation bar hidden automatically inside Voltcap | BLOCKED-HW (Odin 3 not connected); inspect read-only first, no guessing |
| Thor dual-screen lifecycle (D2), app handoff, remaining second-screen interactions, A4 Ribbon two-screen | BLOCKED-HW / OWNER (needs an app opened on the top screen) |
| In-game overlays (HUD, notifications, perf stats, music), D4 FPS / AutoTDP / thermal | BLOCKED-OWNER (approved game session) |
| Real RetroAchievements account path | BLOCKED-OWNER |
| GameNative real launch (D1) | BLOCKED-OWNER (GameNative not signed in; Odin 3 not connected in 522) — then: intent accepted vs game started, return to Voltcap |
| DroidDeck real launch (shortcut + `.droiddeck` file) | BLOCKED-HW (DroidDeck not on the RP Classic / GT78); confirm the `.droiddeck` / `droiddeck://game/<id>` contract with `dumpsys package com.droiddeck.launcher` |
| GameNative `.epic` / `.gog` / `.amazon` / `.pcgame` exports | DONE-O (522): imported, launched with their `game_source` (contract from GameNative's source); real launch NOT TESTED (no GameNative on a connected handheld) |
| Tile home: moving / resetting the round buttons moved the row and chips (18 px) and the facts line; Edit Home moved the row 50 dp at Top; resetting the row snapped untouched buttons back | FIXED (522e / 522g, Tile home only): buttons' room kept while they float, Top padding kept in Edit Home, drop check only for a drop. tile522b.sh + tile522.py (RP Classic); RP Flip 2 NOT TESTED (not connected) |
| Console badge looks | DONE (522, owner decision): Text / Symbol / Logo, wide wordmarks compact on small tiles, regional identities kept; RP Classic gallery b522modes / b522final |
| PS5 / Xbox One / Xbox Series / Switch 2 badge + folder artwork | BLOCKED-REF — sources and owner download steps in docs/ARTWORK_522_NEW_CONSOLES.md |
| RA icons for regional keys | OPEN (recommendation only; RA console mapping is metadata — not changed) |
| MCP install guard active in the desktop tool | BLOCKED-OWNER — restart the Claude desktop app (the tool still serves the pre-521 server.js) |
| GT78 QA app is release-key signed (0.6.68-qa516a) | DONE (523, owner decision): replaced by the debug-key QA app; real Voltcap untouched (it self-updated to 0.6.69 on its own updater) |
| B2 background clip measurement | BLOCKED-HW (no clip library on the connected devices) |
| E1 cartridges: GBA side / top, SFC + PAL rears, Famicom / NES rears, Mega Drive, double labels; E2 case calipers / box sides | BLOCKED-REF — 9 photos listed in tools/qa/E1E2_REFS_487.md |
| GBC PCB refinement for MBC1 / MBC5 layouts | BLOCKED-REF — approximate layouts stay; MBC3 + RTC (Pokémon G / S / C) is reference-based and protected |
| Light-up cartridge, owner's exact Pokémon Brown label | OWNER data |
| KONKR / Thor re-runs of A1 / A3 / A5 / B3 / C1 / C2 | BLOCKED-HW this session (only the RP Classic is connected) |
| Production backups P2–P7, real install B2–B6 | BLOCKED-OWNER (each step approved separately; docs/DATA_BACKUP_513.md, docs/REAL_INSTALL_510.md) |

## 5b. Process risks found (520)

| Risk | Status |
|---|---|
| `install_build debug` installs whatever the debug build is. With `qa_variant.properties` set to false the debug APK was `com.shelf.launcher`, same key, versionCode 76 > 74, so it **would have installed over the owner's real app**. | **FIXED (521), fail-closed:** debug = QA app by default (no file needed), QA builds signed with the debug key (Android refuses them as an update of the real app), `verifyQaIsolation` deletes any production-identity / stray APK, `guard.js` checks APK manifest + signer + USB handheld before every install and watches the real app. The install-side guard runs in the PC tool **after the owner restarts the Claude desktop app** (MCP server reload); until then the build-side and signature protections already hold. |
| QA scripts pressing A blindly in the R1 panel could toggle the real Wi-Fi / Bluetooth / DND. | Rule: QA scripts never press A in R1; R1 checks go through unit tests or a dedicated hook (521: `voltcap_test_dock` for the Row tile). The shell / push guard (521) refuses `settings put`, `svc wifi|bluetooth`, DND and screen-size commands. |
| `vqa.sh rate60` changed the panel refresh-rate setting (restored afterwards). | Removed (521). |

## 6. Deferred
New system layouts, new 3D templates, consoles without references, Studio templates, popups inside the activity window,
a separate hero surface for Ken Burns (only with a measured net win), screensaver overlay leakage (no repro since 488).

## 6b. Follow-up features (after v0.6.70; not in the release candidate)

| ID | Item | Status |
|---|---|---|
| FW1 | **Dual-screen start-up welcome (AYN Thor):** while "Who's playing?" waits at start-up, the lower screen shows the intro's own mark, "Choose a player to begin" and a seam of light under the cursor's player instead of a game layout; hands over to the normal second screen after the pick. Branch `feature/dual-welcome` (local), design + QA plan `docs/DUAL_WELCOME.md`. Look approved by the owner 11 Nov (no redesign). | **BLOCKED-HW (11 Nov, owner): AYN Thor not available over USB.** Done: implemented, type-checked, logic tests pass; Gradle-built in its own git worktree `Claude outputs\worktrees\fw1` (QA app identity + debug signer verified, no secrets). Kept: the worktree, its APK, and the verified bundles in `Claude outputs\backups` (`fw1_20261111.bundle`). Not installed anywhere — no substitute device. Resumes when the Thor is on USB: install in the QA app, `dualwelcome.sh` (6 looks + ×1.3, cold start, restore, transitions, screen off/on, Categories start-up shows none). Not hardware-verified. Separate from v0.6.70; merge only with the owner's approval after 0.6.70. |

## 7. v0.6.70 release plan (520)

| Class | Items |
|---|---|
| **Critical blockers** | (0, 523) 523y ×1.0 graded 11 Nov: KONKR 258 PASS / 36 FAIL / 3 NOT TESTED / 21 SKIP, Flip 2 284 / 13 / 0 / 21 — genuine D303 (Row opens Details, KONKR ×31), D304 (Back hint 0 px, Bar / Cross / Side ×3 each), D305 (Camera rows, KONKR) fixed in 523z (ee65096, type-checked, not built); the rest are open-low pre-existing (Flip home legend glyphs ×7, Hub / Resume classic achievements ×2) or checker false positives (D306 Arctic clock ×2). 523y ×1.3 graded 11 Nov: KONKR 201 PASS / 93 FAIL / 3 NOT TESTED / 21 SKIP (D303 ×31, D307 Details Play row 8 px past the foot ×48, D304 ×3, D305, glyph / timing ×5), Flip 2 284 / 13 / 0 / 21 (D304 ×3, D303-like facts ×2, home-layout ×1.3 text behind the popup D308 ×6, glyph ×1, D306); 523z (183aba5d, D303 / D304 / D305 / D307) built and installed in the QA app on both, re-gate v1 (14:55) stopped at 15:58 — it ran without the fixture achievements and without the CLASSIC style that D307 changes (its KONKR ×1.0 part: 145 PASS / 0 FAIL / 14 SKIP, kept as partial evidence only); **re-gate v2 `chain523z.sh` (OWN / ROW_OPENS / ROW_REVEAL / CLASSIC, fixture art + achievements)**: ×1.0 graded 17:35 — KONKR kp523z2 **198 PASS / 0 FAIL** / 14 SKIP (D303 / D304 / D305 gone, checked on screenshots), Flip 2 fp523z2 190 PASS / 7 FAIL / 1 INFRA / 14 SKIP (FAIL = known open-low only: Hub / Resume classic achievements ×2, home legend glyphs ×5; INFRA = GRID_CLASSIC dump missing, re-run pending); ×1.3 graded 19:05 — KONKR kpb523z2 189 PASS / 9 FAIL / 14 SKIP, Flip 2 fpb523z2 194 PASS / 4 FAIL / 14 SKIP: **D303 / D304 / D305 / D307 gone at ×1.3 too**; every FAIL left is outside the popups and the same in 523y (home-layout text at ×1.3 behind the window — Card binder / Cover details status clock over the console name, File select 'Never played', Stacks free-space line, Blades controller chip, Cube L2·R2 hint: D308, open low) or timing (Cube first A); Flip GRID_CLASSIC re-run fg523z PASS (the ×1.0 INFRA was a missing dump). **523z popup gates: no genuine popup FAIL left on KONKR or Flip 2 at ×1.0 or ×1.3.** Real Voltcap untouched on both (pid 19072 / 10481), 0 fatal; RP Classic ×1.3 p523ob graded (kept on the device; the Classic is away — BLOCKED-HW; whether it blocks 0.6.70 is the owner's call); (1) release build of the 523z candidate checked 11 Nov: R8 build OK, `verifyNoEmbeddedSecrets` 4 values clean, `apk_secret_scan` com.shelf.launcher release signer 88ce4b67, not debuggable, no credential fields — the 0.6.70 / versionCode 77 bump happens in `publish_release` (owner approval); (2) KONKR regression of the 520 changes DONE in batch 523 (konkr523c: `mem520.sh 3` plateau, `ctl516` 55 / 55, `q520b`, perf514); (3) owner OK to merge next/0.6.70 into main. Not blockers: everything else below |
| **Important (in 0.6.70 already, verified on the RP Classic)** | flat case art fit + failed-wrap fallback, PS4 back logo, Game grid single-row ▲▼, game spacing (Grid, Hero tiles, Cinema + R1 tile on 4 more layouts), ScreenScraper check status + failure notes |
| **Important, still open** | KONKR memory soak (`mem520.sh`), Case Stacks fast scroll on KONKR; Thor quick check of the 520 layouts |
| **Optional** | more layouts with Game spacing; Cover details first-frame texture glitch (needs evidence); header backdrop fade; scan crop audit |
| **Hardware / reference / owner** | Odin 3 nav bar, Thor second screen, in-game overlays, RA account, GameNative, E1/E2 photos, finger tests, look approvals |
| **Owner approvals** | P3–P7, B1–B6 (upgrade); merge + publish of 0.6.70 |

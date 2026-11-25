# Native runtimes: Peanuts Native (Switch) / Banana Native (Switch 2) — feature branch `claude/native-runtimes`

Owner 25 Nov 23:41: alternative external runtimes for compatible games on the EXISTING Switch / Switch 2 platforms, per console
and per game; documented frontend intents and `.nup`; no duplicate platforms or records; separate from the v0.7.0 RC.

## Sources checked (25 Nov 2026)
| Runtime | Official source | Release package | Frontend component | Game argument |
|---|---|---|---|---|
| Banana Native | git.eden-emu.dev/moceure/BananaNative (`android/FRONTENDS.md`, `NUP.md`; ba6094d, 22 Nov) | `dev.bananative.android` | `dev.bananative.android/dev.bananative.android.Launch` (alias of FrontendLaunch, no intent filters) | extra `game` = path ("most reliable"); also VIEW file:// / SAF URI, extra `title_id` |
| Peanuts Native | github.com/igawa6/peanuts-native (release 0.1.8, `peanuts/docs/LAUNCHERS.md`; 0766fc9, 25 Nov) — same author as Eden Duo | `dev.nxnative.android.peanuts` | `<pkg>/dev.peanuts.launch.LaunchActivity` (build-stable alias; upstream `<pkg>.Launch` also exists) | same forms |
`.nup`: single-file game format (github.com/a55uka/nup): base + updates + add-ons in one file, read-only for the runtimes.
Note: both projects' test builds use `dev.nxnative.android` (ambiguous) — not catalogued.

## What the branch does
- `data/NativeRuntimes.kt` (new): ids above; `romFor` — a game listed by its .nsp/.xci goes to a native runtime as the `.nup`
  of the same name beside it (the record, artwork, settings stay); `.nup` / game folder unchanged; other emulators untouched.
- `EmulatorCatalog`: "Peanuts Native" under `Switch`, "Banana Native" under `Switch2` (FILE_PATH in extra `game`).
- `EmulatorRegistry.suggestionsFor`: a console kept under its folder's spelling ("Switch 2", "switch_2") still gets Switch2's
  emulators (suggestions only; no console is renamed).
- `RomScanner`: `.nup` counts as a game in a console folder (so Switch 2 folders list them); a `.nup` beside the same game's
  dump never replaces the dump's record (format rank 45).
- Per-game selection: the existing Game settings › Emulator (update 326) — no new UI.
- Tests: `NativeRuntimesTest` (6) — catalog once per platform, launch profile, picker, romFor, scan identities.

## Not done / not verified
- Hardware launch: NOT VERIFIED — neither runtime is installed on the KONKR or RP Classic (read-only `pm list`, 25 Nov), and
  no prepared test game exists. Needs an installed runtime + a prepared (packed) game the owner owns.
- No compatibility filter in the picker yet (an .nsp without a `.nup` beside it is sent as-is; the runtime shows its own message).

# VoltCap v0.7.0 - functional and data-integrity audit (25 Nov 2026, live session 682bd)

Owner order 25 Nov 21:24 ("MANDATORY FULL FUNCTIONAL AUDIT ... prove that v0.7.0 preserves existing user data and functionality").
Branch `claude/v0674-native-pages`. Builds: QA app only (`com.shelf.launcher.qa`), production VoltCap untouched and never read
(the tool guard refuses even a read of its files). No merge, no release, no production install.

Classification: **PASS-HW** = automated and hardware verified - **PASS-AUTO** = automated only - **FIXED** = defect found and
fixed (with its verification) - **NOT TESTED** - **BLOCKER** = release blocker.

## Summary

| Area | Status | Evidence |
|---|---|---|
| Upgrade v0.6.73 -> v0.7.0 (settings kept) | PASS-HW | QA build of the v0.6.73 release code (main a401221) over QA data, restart, then the v0.7.0 QA build over it: every settings file and key compared - 0 changes on KONKR and RP Classic (only the update-check time and battery-watch counters move) |
| Backup / restore / safety snapshot | PASS-AUTO | DataIntegrity682bdTest: all 100+ settings files, every value type, per-game data and device files - backup, wipe, restore = identical; restore twice = identical; the import's safety snapshot puts the previous state back; another device's backup leaves this device's files and the appearance device keys alone; a backup without game data leaves game data alone |
| Repeated migrations | PASS-AUTO | the start-up migrations (ScreenScraper address clean-up, tuner "With power") run twice = no change; the tuner migration can no longer touch a profile saved by this build |
| Restart / rescan / layout previews / Edit Home discard | PASS-HW | persist682.sh + persistdiff.py on both handhelds (qa682bf): 0 changed keys after each step |
| Per-game / app / console / global performance profiles | FIXED + PASS-AUTO; HW on simulated tuner hardware: see below | explicit Underclock Off kept and named; one resolution rule for launch and popup; source shown everywhere (TuneOverride682bdTest 9) |
| AutoTDP vs explicit underclock | PASS-AUTO | AutoTDP moves the underclock only when it is "With power" |
| Heat Guard | FIXED (redesigned) + PASS-AUTO | presets, smoothing, hysteresis, gradual steps, recovery, firmware thermal never written (HeatGuard682bdTest 13, synthetic traces) |
| Recents: library game back in front | FIXED (new) + PASS-AUTO | only Android games named by their package; emulator games never guessed |
| R1 Heat guard tile | PASS-AUTO; HW: see below | appended after the owner's tiles; same on every layout |
| Game-specific emulator / launch settings | PASS-AUTO (new) + PASS-HW (persistence walk) | StorePersistence682bdTest; a game's own emulator, extras and an explicit off survive restart; one game's change leaves others and the console alone |
| Play time | PASS-AUTO (new) | StorePersistence682bdTest - had no test before |
| Favourites, achievements, artwork / metadata overrides, categories, collections, recomps, R1 order / focus position | PASS-AUTO (existing suites + the backup round trip) + PASS-HW (723a / 724a / 725a QA, the persistence walk) | |
| Controller navigation, page walk, destructive confirmations (28 class-C layouts) | PASS-HW x1.3 both handhelds | HW-28 walk: 56 layout runs, 0 launches, 56 confirmations armed and cancelled (never confirmed), Play in place 56 / 56, QA settings restored (diff=0), 0 crashes |
| Popup / window geometry gate (28 layouts x 2 handhelds x 2 scales) | FIXED + PASS-HW | D397 (key row stopped between keys after a tap on More) fixed; all 112 cases PASS |
| HW-28 walk at x1.0 | NOT TESTED | the popup gate ran at x1.0; the full walk only at x1.3 |
| Save Sync configuration and save-file integrity | PASS-AUTO (existing SaveMatch / SaveLayouts / 529 work); hardware NOT TESTED in this audit | QA sessions keep Drive sync paused (CloudSync.qaPaused) |
| Library rescan with a missing SD card (681r) | **BLOCKER (owner decision)** | the fix exists on `claude/v0674-rescan-fix` (HW-25 PASS except the SD-card-out case) and is not on this branch |
| Real clocks on AYN / Retroid hardware | NOT TESTED | the QA app never runs root commands (owner rule); the tuner works only through the vendor service (RP Classic, Flip 2, AYN), not on the KONKR |
| The original WWE '13 incident | NOT EXPLAINED | the real profile was not inspected (production data is off limits); the code cannot turn a saved per-game Off into Light - the label fits the device-wide profile with "With power" |

## Findings (this audit)

- D395 FIXED - QA tool: a finished run's watchdog outlived it (the device's sh held its TERM) and could have force-stopped
  the QA app over the next batch. Caught before it fired.
- D396 FIXED - QA tool: a chain logging to /sdcard got "Failed transaction" on every input / am call (guard stopped it, nothing
  pressed).
- D397 FIXED (user-facing) - a uniform key row stayed between two keys after a tap on More (Play cut ~200 px): Tile home,
  Grid, Pocket x3, Hero x2, Disc station.
- D398 FIXED - popup-gate false readings (measure-only references, Disc's long / short label, Night's turned panels).
- D399 / D400 OPEN, minor - Green core header slides at x1.3; Pocket camera ALBUM order key narrow.
- D401 FIXED - the tuner's record of this handheld's stock clocks (`tuner_stock`) was GLOBAL by default and travelled with
  backups; now INTERNAL. 31 other settings files are unlisted (GLOBAL by default); none holds sign-ins; some hold paths
  (save explorer, ROM headers) that only make sense on one device - harmless on another device, listed for a later pass.
- Performance profiles (owner 25 Nov 20:58): the notification never said whose profile it showed; a game resumed from
  Android's recents ran the device-wide profile silently; "With power" + AutoTDP Balanced = Light read like a choice. All
  three fixed.

## Heat Guard (redesign)

Audit: it reads the hottest CPU / GPU / SoC thermal zone - per-core junction sensors (RP Classic: cpu-0-0 ... cpu-1-3, gpuss),
not the battery or the skin. The RP Classic's own passive trips are 95 / 110 / 115 / 125 °C; the old guard stepped a whole
underclock preset after 8 s at 85 °C, so ordinary heavy scenes triggered it. Now: an exponential average per preset (Gentle
10 s / Balanced 6 s / Aggressive 3 s), a sustained run over the limit (20 / 12 / 6 s) before a step of 5 / 7 / 10 % off the caps
in force (little cores half), steps 40 / 30 / 20 s apart, at most 15 / 28 / 40 %, a hysteresis band of 4 / 5 / 7 °C, recovery one
step per 20 / 30 / 45 s. Limits offered stay 5 °C under the firmware's first trip (read only). The firmware's thermal control is
never written (a test scans the sources). Temporary: never written into a profile; shown apart as "Heat guard −7 % for now";
the handheld's own thermal limit is shown when a core reads back below Voltcap's cap.

## Not covered / limits

- The resume rule needs usage access; without it the game keeps the device-wide profile (the notification says so).
- Emulator and GameNative games are never recognised from the app in front (the package names no single game).

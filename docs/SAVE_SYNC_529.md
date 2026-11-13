# Save sync reliability — update 529 (P0)

**Status: fixed and tested on a development branch. Not merged, not released.**
Branch `fix/save-sync` (2 commits on top of the blur hotfix branch `hotfix/0.6.72-blur`, base `558cb1f7` = v0.6.71).
I could not prove the release safe end to end. Real two-device Drive QA is still missing (see "Not proven" below), so following
your rule I kept it off `main`.

## 1. Review findings, checked against the v0.6.71 source

| # | Finding | Verdict | Where (v0.6.71) | Fix |
|---|---|---|---|---|
| 1 | After-game uploads used ONE queue name with REPLACE. Ending game B while game A's upload waited (offline, retry backoff) dropped A's upload. | **Confirmed** | `SaveWatchWorker.kt:200` | Each game gets its own queue (`PostGameSyncWorker.workName(key)`, SHA-1 of the key). Uses `APPEND_OR_REPLACE`. |
| 2 | In-game "save now" used KEEP. A change made while an upload ran was ignored, and after 3 rounds the change was given up. | **Confirmed** | `SaveObserver.kt:191` (KEEP, 3-round loop, `sentSig`) | Per-game name with `APPEND_OR_REPLACE`, `MAX_ATTEMPTS = 8`. A change that still isn't sent becomes PENDING and goes to the per-game after-game queue, so it is never dropped. |
| 3 | `changedSaves` treated a file as unchanged when its size and date matched. | **Confirmed** | `CloudSync.kt:774-775` | Compared by content (md5, `watchHashes`) under the trust rule below. |
| 4 | The hash cache trusted size + date. Same-size saves (SRAM, memory cards) rewritten within one date step (FAT: 2 s), or on a SAF folder that reports date 0, were never seen. | **Confirmed**, reproduced as real loss in the simulation (`aSecondSaveWithinOneDateStepIsSeenOnACard` fails on the old rule: "unsent progress … replaced by a download") | `CloudSync.kt:1317`, `SyncTrees.kt:142` (SAF null date becomes 0) | `SaveChangeDetect.trusted`, the git "racy clean" rule: a cached hash is used only with a real date, the same size and date, and a date at least 3 s before the hash was taken. Otherwise the file is read again. |
| 5 | Play stopped the Drive check that opening the game's window had started, then launched on whatever this device had. | **Confirmed** | `LauncherViewModel.kt:5697-5698` | Bounded pre-launch check (`PrelaunchCheck`). Play waits for the check, at most 10 s. A save confirmed in the last 60 s starts at once. A timeout, failure, stopped check or held-back save asks **Play anyway / Retry**. A conflict opens the conflict choice. A check that times out is stopped, and its end is awaited before anything else happens. |
| 6 | The backup before an overwrite was best effort (`runCatching`), and only 3 were kept. "Use Drive's" in the conflict choice downloaded with no checksum and no backup. | **Confirmed** | `CloudSync.kt:1384-1408`, `:1072` | `SaveBackups.backup` is mandatory. It is copied while hashing, fsynced, read back, and size + md5 compared, then renamed atomically and given a sidecar JSON (device, md5, size, dates, reason, replacing md5). Retention: at least 10 per file, and nothing younger than 30 days is removed. If the backup fails, the save is not replaced. |
| 7 | A Drive reply without an md5 counted as a verified upload. | **Confirmed** | `CloudSync.kt:1375` | Drive's file is read back. Only an md5 equal to the bytes sent marks the upload synced. |
| 8 | Twins (two Drive files with one name) were picked as the newest by client-set time, with no tie-break. | **Partly confirmed.** The pick itself was rarely wrong, but see 9. | `CloudSync.kt:1441`, `DriveOnlySaves.kt:162` | `CloudSync.pickTwin`: newest, then by id. Every device now picks the same twin. DriveOnlySaves uses the same pick. |
| — | "A stopped scoped sync was reported as success" | **Dismissed.** It returns `ok = false`, so the status is ERROR. | — | (Play now treats it as not verified.) |
| — | "Saves matched by ROM stem can miss files" | **Dismissed as a loss cause.** A miss only delays sync, because the full sync covers every file. Names are never changed. | `CloudSync.kt:525-540` | — |

### New findings from this audit

| # | Finding | Fix |
|---|---|---|
| 9 | **Twin lineage.** When both handhelds uploaded the same *new* save at the same moment, one of them later saw the other device's twin as "Drive changed, mine didn't", and downloaded it over its own progress. The progress was backed up, but the replacement was silent. | `SyncDecision.decide`: if Drive's file is a different file from the one the base came from, the result is a **conflict**, not a download. Older records without an id keep the old behaviour. |
| 10 | **Multi-file saves were not a transaction.** PS2 folder cards, 3DS/Wii title folders and PSP save folders were downloaded file by file. A failure halfway (offline, bad checksum, storage full) left a save made of two devices' files. | `SaveUnitTransaction` (used by `CloudSync.pullUnit`) applies to both sync and the conflict choice. It works in three steps: (1) fetch every file and check every checksum, (2) make a verified backup of every file about to be replaced, (3) write. If a write fails, the files already written are restored from their backups. |
| 11 | "Keep this device's" in the conflict choice parked Drive's version with no check, then overwrote Drive with no "is it still that version" check. | The parked copy is checked against Drive's md5, and the push uses `expectRemoteMd5`. |

## 2. Tests (all green on the PC, `testDebugUnitTest`, BUILD SUCCESSFUL, full suite)

- `SaveSync529Test` (18 tests): one per finding. Covers the trust rule, signature, queues and policies, verified backups with sidecars, failure leaving no partial backup, retention, backup before write, strict upload check, prelaunch `needed`/`next`, a failed or stopped check asking, and twin pick.
- `SaveSyncSim529Test` (12 tests): **adversarial two- and three-handheld runs on a fake Drive** (JVM, synthetic saves only).
  - Real code under test: `SyncDecision`, `CloudSync.pickTwin`, `SaveUnitTransaction`, `SaveBackups` (on real temporary files) and `SaveChangeDetect`. The listing, push and conflict choice are modelled step by step on `CloudSync`.
  - Invariants checked after every step:
    - The latest progress of every device still exists somewhere (a device, Drive, a verified backup or a parked conflict copy).
    - A download never replaces progress that Drive never had, except by the player's explicit choice.
  - Scenarios:
    - Hand-over Flip 2 → KONKR → Flip 2.
    - Both played offline.
    - Another upload between listing and sending.
    - Two new saves uploaded at once (twins).
    - A multi-file save interrupted three ways: offline, mangled, and storage full on the 2nd write.
    - A backup that can't be made.
    - Region, hack and case variants staying separate with their exact names.
    - A save deleted on one device.
    - The FAT 2 s date step.
    - 25 random three-handheld runs × 120 steps with races, failures and conflict choices, then settling to one state.
- **Mutation check.** Each fix was reverted in a scratch copy and the tests re-run. Every revert is caught:
  - no lineage rule → 2 tests fail;
  - no restore after a failed write → 1 fails;
  - one-by-one multi-file download → 1 fails;
  - no backup before a replace → 4 fail, including the random runs;
  - no "Drive moved" check → 1 fails;
  - old size+date trust → 1 fails with a real progress loss.

## 3. Devices (read-only, nothing changed)

- **Flip 2** (USB `2037b27d`): QA handheld. No emulators and no save folders are installed, so there is nothing to sync there.
- **GT78** (USB `BW0306N230000845`): real saves, inspected read-only.
  - RetroArch: `cloud_sync_enable = "false"`. Saves are in `/sdcard/RetroArch/saves`, sorted by core.
  - `saves/` contains empty **`.stfolder` / `.stversions`**, which are Syncthing markers. No Syncthing app is installed on the GT78 now. If Syncthing runs on another handheld on the same folder, two sync engines write the same files, and that would mix devices. Please check the KONKR (I couldn't: it is not connected).
  - Pokémon Silver: every kept version was read (play time and money from the save, read-only). The live save is the furthest along: **75 h 46 min** (the save's play-time field), 14 badges. The copies in Sync backups are 72:51, 74:20 and 75:21, and the parked conflict copy from 29 Oct is 20 h. **No retained copy is ahead of the live save.**
- **Flip 2 smoke run (isolated QA app only, about 2 min):**
  - Built `assembleQa` with R8: OK.
  - Installed over the QA app; the real Voltcap was unchanged (versionCode 78).
  - Home opened. Game window → Play: the launch went straight through. Sync is off in the QA app, so Play didn't wait; it ended at the QA app's launch block.
  - No crash in logcat.
  - The QA app was stopped afterwards, so the Flip 2 is back on its normal home. The snapshot folder was untouched.
- **KONKR**: not connected, so it was not inspected and not tested.

## CI

Not run on this branch. The remote tools push only `main` and the branches `feature/popup-native`, `exp/shelf-3d-flip` and
`feature/connected-ignition`, so `fix/save-sync` can't reach GitHub from here. The same checks were run locally on the PC:
- full `testDebugUnitTest`: green;
- `assembleQa` with R8: green, secrets check clean.

## 4. Not proven / remaining risks

1. **Real Drive, two real handhelds.** The isolated QA app never syncs (`qaPaused`). Testing it against Drive needs a Google sign-in, an OAuth consent that is yours to give, ideally with a test account or test folder. The KONKR is not connected. So the fix is proven on the JVM against a model of Drive, not against Google's servers.
2. **Upload race inside the last moment** (Drive v3 has no conditional update). If another handheld uploads in the second between the "still that version?" check and Drive accepting our bytes, the other version is replaced on Drive. The random runs show it is never lost: it stays in Drive's revision history, and on that handheld it is backed up before being replaced. But it is not announced. A fix would read Drive's revision list after each upload.
3. **"Keep this device's" for a multi-file save** pushes file by file. A failure halfway leaves Drive mixed until the next sync, which sends the rest. A device that downloads in between gets that mix as one unit, still all or none locally.
4. **"Newest"** in the conflict choice uses file dates (clocks can differ). "Use Drive's" and "Keep this device's" are exact.
5. **No in-app restore of a backup yet.** Rollback today is manual: `Voltcap/Sync backups/<folder>/<path>.<yyyyMMdd-HHmmss>`, now with a sidecar `.json`, plus `Voltcap/Sync conflicts/`. An "Earlier versions" list in Save options is the next step.

## 5. Recovery opportunities (nothing was restored)

- GT78: 4 Pokémon Silver revisions in `Voltcap/Sync backups/RetroArch saves/Gambatte/` and 3 parked files from 29 Oct in `Voltcap/Sync conflicts/` (both Silver files and the `[patched]` copy). None is newer than the live save (above).
- Google Drive keeps earlier revisions of every save file (binary files: 30 days or 100 revisions), so older progress can be fetched from Drive's "Manage versions".
- Restoring anything replaces a live save. That needs your go-ahead per file.

## 6. Files

New: `data/SyncDecision.kt`, `data/SaveUnitTransaction.kt`, `data/SaveBackups.kt`, `data/SaveChangeDetect.kt`, `data/PrelaunchCheck.kt`, and the tests `SaveSync529Test.kt` and `SaveSyncSim529Test.kt`.
Changed: `data/CloudSync.kt`, `data/SaveWatchWorker.kt`, `data/SaveObserver.kt`, `data/SyncStatusStore.kt`, `data/SyncNotifier.kt`, `data/DriveOnlySaves.kt`, `ui/SaveSyncUi.kt`, `ui/SaveExplorer.kt`, `LauncherViewModel.kt` (the `src_copy` mirrors are identical).
Branch copies on the PC:
- `Claude outputs\backups\fix-save-sync.bundle` (prerequisite `558cb1f7`);
- patches in `Claude outputs\voltcap_529\save_sync\`.


## 7. Mid-game uploads (priority hotfix, 13 Nov)

**What was found (no guesses):**

| Question | Answer | Evidence |
|---|---|---|
| A. Does the emulator write the save mid-game? | **On the GT78, yes.** RetroArch 1.22.2 has `autosave_interval = "10"` (global `retroarch.cfg`, no core / game override), so SaveRAM is flushed to disk every 10 s when it changed. | `/sdcard/Android/data/com.retroarch.aarch64/files/retroarch.cfg`, read-only |
| B. Did Voltcap miss saves that were already on disk? | **Yes, whenever Android ended Voltcap's process during the game.** The watcher that sends saves within seconds (`SaveObserver`) lives in Voltcap's process. On the GT78 that process was ended while in the background (log: `Process com.shelf.launcher (pid 32106) has died: prcl BFGS`, 12 Nov 22:15). The only thing left was the session chain (`SaveWatchWorker`): first look 3 min in, then 10, 20, 40, 60 min, and a 90 s "untouched" rule that RetroArch's 10 s autosave can keep resetting. So in practice: uploaded after the game. | GT78 logcat, read-only; `SaveWatchWorker.kt` (v0.6.71) `STEPS_MIN = 3, 10, 20, 40, 60`, `STABLE_MS = 90_000` |
| Other mid-game gaps | A save read while the emulator was writing it was uploaded anyway (`push` only dropped the cached hash). A save from another handheld that arrived on Drive mid-game was silently ignored until the game ended. Every confirmed mid-game upload posted a sounding notification. | `CloudSync.push`, `syncFolder` (upload-only path), `SyncNotifier.afterBackgroundSync` |
| KONKR | Not connected - its RetroArch setting is unknown. Check it as described below. | - |

**What changed (branch `fix/save-sync`):**
- **The watcher comes back after the process is ended.** The session chain now runs every minute while the screen is on (10 min when off). Each run starts `SaveObserver` again if it is gone (`SaveObserver.resume`). WorkManager has just started the process to run it. Worst case for a save written while the process was dead: about 1 min + 4 s settle + upload, instead of 3 to 60 min or the game's end. Network is no longer required just to look.
- **No torn uploads.** The bytes sent must equal the bytes the decision was made on, read twice. Otherwise nothing is sent (`StillWriting`) and the watcher sends it once it has settled. Memory cards included.
- **Drive news during a game.** Every 3 min (screen on, nothing of ours pending) a quiet upload-only look at Drive. A newer save from another handheld is HELD: never applied while the game runs. The game's status says so ("Newer save on Google Drive from X — applied after you close the game"), with one silent notification. After the game, the normal sync brings it in with a verified backup, or shows a conflict if both changed. Play's pre-launch check (section 1, finding 5) keeps the next session from starting on a stale save.
- **Quiet during play.** A confirmed mid-game upload posts no notification; the status shows it. A save caught mid-write is not reported as a problem. Problems still notify, once.
- **Status steps:** "No new save written to disk since the game started", "New save found — waiting for the file to settle", "New save — waiting to upload", "Uploading…", "Last save uploaded and confirmed on Google Drive" (only after Drive's checksum matched), "Newer save on Google Drive…", "Save being written — sent once it settles", and offline / error / conflict as before.
- **Timing trace:** `adb logcat -s VoltcapSync` shows each step (`detected`, `settled`, `upload-start`, `verified`) with the time since the emulator's own write.
- **Tests:** `SaveSyncSim529Test` +4 two-device mid-game scenarios: several saves during one game all uploaded; a save being written is not sent; another handheld's newer save is never applied while the game runs, and a mid-game save of ours then becomes a conflict; after the game the Drive save comes in with a backup. `SaveSync529Test` +3 source checks.

**Not proven on hardware:** the upload to Drive and its verification. The isolated QA app never syncs, and a real Drive test needs your Google sign-in on it. The KONKR is not connected. RetroArch was not started on any handheld: that is your real app with your real configuration, and the Flip 2 has no emulators.

**RetroArch setting (KONKR or any handheld), safe and reversible:** In RetroArch go to *Settings → Saving → SaveRAM Autosave Interval*. Set 10 s (the GT78 already uses this). Then *Main Menu → Configuration File → Save Current Configuration*. It is the line `autosave_interval = "10"` in `retroarch.cfg`, and `"0"` turns it back off. With it off, RetroArch keeps SaveRAM in memory until you close the content: no app can upload a save that is not on disk yet. Save states are a different thing and are never synced.


### Hardware run: Flip 2, isolated QA app, synthetic saves (13 Nov, 11:40)

The QA app never talks to Drive. So the "upload" here is the dry run (`QaSaveDryRun`): a snapshot copied to `qa_drive/`, refused if the bytes changed since the decision and checked after the copy. Everything before it is the real watcher (file events, polling, settling, restart). Script: `tools/qa/device/midsave529.sh`; trace: `adb logcat -s VoltcapSync`.

| Step | Write → verified snapshot | Revisions sent |
|---|---|---|
| 1 save (32 KB) | **8.2 s** (found 4.7 s after the write, settled 8.7 s) | 1 |
| 3 saves 1 s apart | **8.0 s after the last one** | 1, the last; the two earlier ones were superseded and never sent |
| 8 MB memory card written in 8 slow chunks (≈5 s) | **8.9 s after the last chunk** | 1, the finished card; never a half-written one |
| Voltcap's process ended in the background (`am kill`, as low memory does), then a save | first run **43.5 s**: WorkManager started the process again 24 s later, then the watcher's first 15 s wait. Re-run with the immediate first look (`32711b96`, 12:03): **30.5 s**: the process came back 26 s after the write and the save was found 0.1 s later. What is left is WorkManager's own start time (the chain runs every minute; Android decides the exact moment) | 1 |

Re-run (12:02, same build plus `32711b96`): 1 save **8.2 s**; 3 saves **8.2 s after the last**, only the last sent; 8 MB card **7.5–9.1 s after the last chunk** (12.7 s after the first), one finished card; process ended **30.5 s**. Both runs: Drive itself not involved (see above).

Build check before the re-run: the PC's `SaveObserver.kt` had not received `32711b96` (Gradle reported the QA compile up to date). All 22 changed source and test files were then copied from the branch to the PC again; after that only `SaveObserver.kt` recompiled, so the rest already matched. `testDebugUnitTest` on exactly the branch content: BUILD SUCCESSFUL (10:56, 13 Nov).

No crashes. File dates on the Flip 2's shared storage come in whole seconds (`mtime=…442000`), the coarse dates the content check (finding 4) is there for. Afterwards the files were removed and the dry run switched off. The script had put a read-only Settings info screen in front, which one Back closed.

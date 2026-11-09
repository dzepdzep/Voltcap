# Supervised real-install check for v0.6.68 (prepared in batch 510; NOT run)

Written in batch 510. Nothing here has been run on the owner's install.
- Part A is read-only.
- Every step in Part B moves data. Each one needs the owner's separate, explicit approval at the moment it runs.
- Claude does not look at or change the Drive switches. The owner reads them.

## Before anything (PC, read-only)
1. **Rollback APK.** `Claude outputs\qa477\konkr_v0.6.67_installed_1586accc.apk` must be present.
   - Its sha256 must start with 1586accc (re-checked in 510).
2. **Release identity (owner-approved 512):** 0.6.68 / versionCode 75, same package and key.
   - Recovery is a roll-forward release (vc 76), not a downgrade; see docs/RELEASE_PLAN_511.md (corrected in 512).

## Part A — read-only (no upload, no download, no save change)
Run per handheld, USB only, with `-s <serial>`.

| # | Check | How | Pass |
|---|---|---|---|
| A1 | Which Voltcap is installed | `dumpsys package com.shelf.launcher` → versionName / versionCode / lastUpdateTime; `pidof com.shelf.launcher` | 0.6.67, vc 74, pid noted |
| A2 | No leftover QA hooks file in the real app | `ls -la /sdcard/Android/data/com.shelf.launcher/files/voltcap_test_hooks` | "No such file". If present: in 0.6.67 it switches the test hooks on (no sync effect); in 0.6.68 a file younger than 24 h pauses all Drive sync (D287 / D295). The owner decides whether to delete it. 511: Thor had one (8 Nov 19:14), removed in 512 after a backup; GT78 none. From 0.6.68 on, release builds ignore the file. |
| A3 | Drive state | **Owner** opens Settings › Google Drive and reads the switches and "what happened last" | Owner notes them. Claude does not view or change them |
| A4 | Test save chosen | **Owner** picks one game whose save may be used for the test and notes its in-app save time (Save Vault view only) | Noted |
| A5 | Network / battery | `dumpsys connectivity` (default network); `dumpsys battery` (level) | Online, ≥ 30 % |

## Before Part B — backups (513; see docs/DATA_BACKUP_513.md)
A settings export does **not** cover custom layouts, artwork files, profile pictures, clips, manuals, guides or game saves.
Before B2, each of these is its own approval: P2 adb copy of every save folder (read-only on the device), P3 in-app export
(game data + device setup + accounts), P4 custom layouts exported, P5 (optional) visible Voltcap folder + copy, P7 the owner
turns the launch-time sync off. A copy counts only with its checksum list on the PC.

## Part B — data-transferring
Each step needs its own explicit approval from the owner, in order. Stop at the first surprise.

| # | Step | What moves | Check after |
|---|---|---|---|
| B1 | In-app settings backup (local export) | App settings → a local file | File exists, size > 0 |
| B2 | Install the merged v0.6.68 over 0.6.67 (`install -r`, explicit APK path) | App code. Data kept | A1 shows 0.6.68; Settings, profiles and artwork unchanged |
| B3 | First start: the launch-time sync runs (if the owner's switches have it on) | Drive ↔ device | "What happened last" shows one entry, no error. No save replaced without a prompt |
| B4 | **Owner** plays the test game briefly, exits | Save → Drive (after-game sync) | Status line shows the upload. Drive copy time = new local time |
| B5 | Backup now / auto backup | Settings backup → Drive | Entry in "what happened last"; auto_backup_at moved once |
| B6 | Only if the owner wants it: restore / download one Drive-only save | Drive → device (**replaces a save**) | Done last, after B1. The owner confirms the game's save afterwards |

**What each step could change, and what protects it** (each step needs its own approval):
- **B1** writes one new file only. Protects: settings for every later step.
- **B2** replaces the app code. Data and files stay (same key, higher code). Protected by: B1, the 513 backups (P2–P5) and
  roll-forward recovery. **B2 is not independent of B3:** the install stops Voltcap and Android restarts it as the home app,
  and that start runs the launch-time sync if the switch is on. B2 is approvable on its own only after P7 (owner turned the
  launch-time sync off and read it back); otherwise B2 + B3 are one step and need P2 first.
- **B3** can upload newer local saves and download newer Drive saves, as 0.6.67 does. Protected by: the owner copies the
  important save folders aside before B2/B3, and checks "what happened last". Whether Drive keeps older versions is not verified here.
- **B4** uploads one game's save. Protected by: the copy taken before B2/B3.
- **B5** replaces the settings backup on Drive. Protected by: B1 (local copy).
- **B6** replaces a local save with the Drive copy. Protected by: copy the save file aside first (owner); done last, or skipped.

**Rollback:**
- If B2–B5 show a problem, stop syncing first (the owner turns the switch off).
- Settings come back from B1.
- If 0.6.68 itself is at fault: a roll-forward recovery release (higher code, same key), installed as an update. Never downgrade, never uninstall (RELEASE_PLAN_511.md).

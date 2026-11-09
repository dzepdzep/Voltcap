# Production data: what exists, where it lives, how to back it up and restore it (batch 513 — plan only, nothing run)

Written in batch 513 for the move from v0.6.67 (vc 74) to v0.6.68 (vc 75) on the owner's handhelds. **Nothing here has been run.**
Every step that touches the owner's real Voltcap, its data, the emulators' saves or Drive needs the owner's separate approval
at the moment it runs. Claude does not change the owner's Voltcap settings, switches or files to make a backup.

## 1. The two kinds of storage (why a settings export is not enough)
Android gives Voltcap two places:

| Place | Path | Who can read it | Survives an in-place update (same key, higher code) | Survives uninstall / "Clear data" |
|---|---|---|---|---|
| **Private app storage** | `/data/data/com.shelf.launcher/` (shared_prefs, files/) | Only Voltcap itself. adb cannot read it for a release (non-debuggable) build, and `adb backup` no longer includes app data for apps targeting Android 12+ | Yes | **No — deleted** |
| **Shared storage** | `/sdcard/...` (`/sdcard/Voltcap/`, emulator folders, `/sdcard/Android/data/com.shelf.launcher/files/`) | adb (`adb pull`, read-only) and the owner's file manager | Yes | `/sdcard/Voltcap` and emulator folders: yes. `Android/data/com.shelf.launcher`: **no** |

So the private files can only be copied out by Voltcap itself (its export / "visible Voltcap folder" features) and are lost
only by an uninstall, "Clear data" or a factory reset — none of which is part of any plan. An in-place update keeps all of it.

## 2. Inventory

| # | Data | Where (code) | Default location | Copy that exists today | How it can be backed up | Restore |
|---|---|---|---|---|---|---|
| 1 | Settings and layout preferences (look, layouts, per-layout colours, Features, R1, categories, collections, favourites, hidden apps…) | shared_prefs (`data/SettingsScopes.kt`) | private | Settings snapshots (taken before every import), Drive settings backup if the owner uses it | **In-app export** (Settings › Backup › Export to a file) — with "include game data" and "include device setup" for everything | Settings › Backup › Import (preview first; a snapshot is taken before it applies) |
| 2 | Custom layouts (Layout Studio) | `data/CustomLayouts.kt` → `files/layouts/<id>/` | private | none outside the app | In-app: each layout can be exported as a `.vclayout` (zip) to shared storage; Thor already has `/sdcard/Voltcap/Layouts` (the import drop folder) | Layout Studio › Import (from the file or the Voltcap/Layouts folder) |
| 3 | Artwork: downloaded, scanned and saved art (`saved_art/files`), the art cache, custom cart / disc labels | `data/ArtFiles.kt`, `data/ArtStorage.kt`, `data/LabelFolder.kt` | private, or `/sdcard/Voltcap/Art` if "use the visible Voltcap folder" is on; labels in `/sdcard/Voltcap/Labels` | only if the visible folder is on | Turning the visible folder on copies Art (and Guides, Music, Sounds, Data, snapshots) to `/sdcard/Voltcap` — **a settings change in the real app: owner step, needs All-files access**. Then `adb pull /sdcard/Voltcap` (read-only) | Files stay in place on update. After a loss: copy the folder back to `/sdcard/Voltcap` and turn the visible folder on; downloaded art can also be fetched again |
| 4 | Manual artwork picks (your own pictures for a game) | `data/UserArt.kt` → `files/user_art/`; the choices are prefs (art_overrides) | private | choices: in an export with "game data"; the picture files: none | Picture files: no outside copy is possible without a Voltcap feature; keep the original pictures you picked | Choices: import. Pictures: pick them again from your originals |
| 5 | Profile pictures | `data/Profiles.kt` → `files/profiles/p<id>.webp` | private | none | None from outside; keep the original pictures | Pick the picture again in the profile editor |
| 6 | Save Capsules | `data/SaveCapsules.kt` — a view over the real saves; options in prefs; a cache in files/ | prefs + private cache | options: in an export | Nothing to copy beyond the saves (#9) and the export (#1); the cache rebuilds itself | Rebuilt from the saves |
| 7 | Clips (saved game videos) | `data/ClipStore.kt` → `files/clips/*.mp4`; addresses in prefs (video_urls) | private | addresses: in an export with "game data" | Files: none from outside; they download again from their addresses | Download again (addresses come back with the import) |
| 8 | Manuals and guides | `data/Manuals.kt` → `files/manuals/`; guides `data/guide` → private or `/sdcard/Voltcap/Guides`; manual addresses in prefs (manual_urls) | private | addresses: in an export with "game data"; guides: only with the visible folder | Guides: visible folder + adb pull (see #3). Manual files: download again | Download again; guides copied back to `/sdcard/Voltcap/Guides` |
| 9 | **Game saves** | The emulators' own folders on shared storage, e.g. `RetroArch/saves`, `PSP/SAVEDATA`, `duckstation/memcards`, `NetherSX2/memcards`; app folders under `Android/data/<emulator>/files` where Android allows reading; the folders picked in Settings › Sync | shared storage (owned by the emulators, not Voltcap) | Drive copies of the synced folders (Voltcap sync); `/sdcard/Voltcap/Sync backups` and `Sync conflicts` (Voltcap keeps a copy before it replaces a save) | **`adb pull` of each save folder to the PC (read-only)** before B2/B3 — the copy that protects the sync steps. Folders the emulator keeps in `Android/data/…` may be unreadable on Android 13; then the emulator's own export, or rely on Drive | Copy the folder back with `adb push` (owner present, emulator closed) or use the emulator's import; a single replaced save also sits in `Voltcap/Sync backups` |
| 10 | Play history (play time, recently played, journal) | prefs (playtime, recently_played), `files/play_journal`, Drive `playtime-v2.json` | private + Drive | export with "game data"; Drive | Export with "game data" | Import |
| 11 | Sign-ins | encrypted prefs (`data/SecureStore`), Drive via the Google account | private | export with "include accounts" holds RA, SteamGridDB, IGDB, TheGamesDB, ScreenScraper, RAWG, RomM, metadata source and Steam | Export with "include accounts" (the file then holds keys: keep it private) | Import. **Google Drive, Discord and Spotify always sign in again on the device** (tied to the device / rotating tokens) |
| 12 | Settings snapshots, crash reports, charge log, Voltcap's own sync bookkeeping | files/settings_snapshots, files/crash_reports, `Android/data/com.shelf.launcher/files/charge_log.csv`, files/cloud_sync | private / Android/data | — | Not needed for recovery (bookkeeping rebuilds; Drive keeps the sync state) | — |

## 3. What protects each step of the real move
- **B2 (install v0.6.68 over v0.6.67)** changes the app code only. Private storage, shared storage and Drive are untouched by
  the install itself (same key, higher code — the ordinary Android update path). **But it is not independent of B3:** the
  install stops Voltcap and Android restarts it as the home app, and that start runs the launch-time sync if the owner's switch
  is on. B2 may therefore be approved on its own **only if** the owner first turns the launch-time sync off (or Drive sync off)
  and confirms it in Settings › Google Drive. Otherwise B2 and B3 are one step and need the save copies of #9 first.
- **B3 / B4 / B6** (sync, a game run, a restore) can replace saves → protected by the PC copy of the save folders (#9), taken
  first, and by Voltcap's own `Sync backups` copy.
- **B5** (Backup now) replaces the Drive settings backup → protected by the local export (#1).
- Recovery if 0.6.68 misbehaves: contain (Drive sync off), import the export, roll forward to a fixed release (vc 76) — see
  RELEASE_PLAN_511.md. Roll-forward keeps every file in #2–#8; **it is not a substitute for the copies above**: it protects
  against bad code, not against a sync that replaced a save.

## 3b. Read-only inventory taken in 513 (P1; `du` / file counts only, nothing copied or changed)
Save folders that exist today (sizes include everything in the folder):

| Thor (3140972f) | GT78 (BW0306N230000845) |
|---|---|
| RetroArch/saves 1.0 MB, 16 files; RetroArch/states 112 KB | RetroArch/saves 1.0 MB, 15 files; RetroArch/states (empty) |
| Ppsspp/PSP/SAVEDATA 806 KB, 13 files | — |
| Armsx2/memcards 17 MB, 2 cards (the same folder as ARMSX2/memcards: shared storage ignores case) | — |
| Android/data/com.github.stenzek.duckstation/files/memcards 388 KB, 3 files | — |
| Android/data/org.dolphinemu.dolphinemu/files 3.8 MB, 43 files | Android/data/org.dolphinemu.dolphinemu/files 208 KB, 3 files |
| Android/data/dev.eden.eden_emulator/files/nand/user/save 22 MB, 24 files (the app folder is 29 GB: copy only nand/user/save) | — |
| Azahar/sdmc 89 KB; citra-emu/sdmc 2.5 MB | — |
| Android/data/info.cemu.cemu 27 MB, 245 files (saves inside mlc01; copy the folder) | — |
| /sdcard/Voltcap 50 MB, 14 files (Layouts, Sync backups, Sync conflicts) | /sdcard/Voltcap 196 KB, 7 files |

Thor's real Voltcap folder `Android/data/com.shelf.launcher/files` also holds old QA leftovers from earlier batches
(`voltcap_test_snapshot*.json`, `voltcap_test_cloudhold.json`, `voltcap_qa_screen`, `label_audit/`, `tilecheck/`). They are
read only with the test hooks on, which a release build never has, so they do nothing; they were left in place (not authorised to
delete). Since the hooks file was removed (12:37 UTC, 9 Nov) only `charge_log.csv` there has changed — written by the running
0.6.67 itself; the real app was not restarted (pid 32226 throughout).

## 4. Proposed backup sequence before Part B (each line its own approval; nothing run yet)
| Step | What | Changes the owner's data? | Who |
|---|---|---|---|
| P1 | Read-only inventory: `ls`/`du` of the emulators' save folders, `/sdcard/Voltcap`, `Android/data/com.shelf.launcher` | No | Claude (adb, read-only) |
| P2 | `adb pull` of every save folder found in P1 to `Claude outputs\backup_<date>\<device>\saves\`, with a checksum list | No (reads only) | Claude after approval |
| P3 | In-app export: Settings › Backup › Export, with game data + device setup + accounts, saved to `/sdcard/Download`; then `adb pull` it | Writes one new file | Owner (Claude pulls the file after approval) |
| P4 | Custom layouts: export each from Layout Studio to `/sdcard/Voltcap/Layouts`; `adb pull` | Writes new files | Owner |
| P5 | Optional: "use the visible Voltcap folder" ON (copies Art, Guides, Music, Sounds, Data to `/sdcard/Voltcap`), then `adb pull /sdcard/Voltcap` | **Changes a setting** (and needs All-files access) | Owner decides |
| P6 | Owner notes what can't be copied from outside (profile pictures, manual picks, clips) and keeps the original pictures | No | Owner |
| P7 | Owner turns the launch-time sync off before B2 (so B2 doesn't run B3), reads the switches back | Changes a switch (owner's own) | Owner |

Verification for each copy: file count and SHA-256 list on the PC, compared with the device listing (P1). A copy only counts
when its checksum list exists and the counts match.

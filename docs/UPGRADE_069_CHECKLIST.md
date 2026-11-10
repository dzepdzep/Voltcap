# Protected upgrade of the owner's handhelds to v0.6.69 — checklist (batch 520; NOTHING here has been run)

This replaces the version-specific parts of docs/REAL_INSTALL_510.md and docs/DATA_BACKUP_513.md, which were written for
0.6.68 (vc 75). The steps and their approval rules stay the same; only the target and the checks change. **Every line marked
APPROVAL needs the owner's separate yes.** Never uninstall, clear data or downgrade: recovery is a roll-forward release
(higher versionCode, same key; docs/RELEASE_PLAN_511.md).

## Target (published 10 Nov 2026)

| File | Size | sha256 |
|---|---|---|
| `Voltcap-0.6.69.apk` | 54,198,201 B | `1101965042cc84c00fa5acceea5236fdf19a4d74d41bb6358d896d7eb8a5c835` |
| `Voltcap-0.6.69-compat.apk` | 54,198,109 B | `b27f9d7d67ddea68ed09b8aa498d35b80410a7b04a5bd0f482c0fcdf320039e1` |

- Both: com.shelf.launcher, 0.6.69, versionCode 76, signer 88ce4b67…, not debuggable.
- Use the compat APK only where compat is installed now (A1 shows which).
- 0.6.68 (vc 75) is skipped: its APKs were withdrawn. 0.6.67 (vc 74) → 0.6.69 (vc 76) is a normal update.

## What 0.6.69 does on its first start (why the backups come first)

- **One-time migration `ss_url_hygiene`:** stored ScreenScraper addresses lose their account / key parameters, and saved pictures are renamed to the cleaned addresses (no downloads; saved art is kept). Checked in the QA app on the Classic (519): 38 values changed, saved files found again. It still rewrites stored data, so P2–P4 come first.
- **ScreenScraper and IGDB go through Voltcap's service:**
  - An account saved with the owner's own typed developer key keeps going direct.
  - An account saved with the 0.6.68 built-in key (none on 0.6.67) uses the service.
  - IGDB keys typed by hand keep working (direct).
- **The launch-time Drive sync runs as before if it is on.** Never assume it is off: P7 turns it off and reads it back.

## Part A — read-only (Claude, adb, after the device is identified by serial over USB)

| Step | Check |
|---|---|
| A1 | `dumpsys package com.shelf.launcher`: versionName / versionCode / signer, and which APK (normal / compat) is installed |
| A2 | No `files/voltcap_test_hooks` in the real app (0.6.69 release builds ignore it anyway; report, don't delete) |
| A3 | Drive switches as shown in Settings (read only) — what B3 would do |
| A4 | Free storage ≥ 2 × APK size + backups |

## Backups before Part B (each its own APPROVAL; docs/DATA_BACKUP_513.md §4 for the commands)

| Step | What | Who |
|---|---|---|
| P1 | Read-only inventory of save folders, `/sdcard/Voltcap`, `Android/data/com.shelf.launcher` (`du`, counts) | Claude |
| P2 | `adb pull` of every save folder from P1 with a checksum list | Claude, APPROVAL |
| P3 | In-app export (game data + device setup + accounts) to `/sdcard/Download`, then pull | Owner exports, Claude pulls, APPROVAL |
| P4 | Custom layouts exported from Layout Studio, then pull | Owner |
| P5 | Optional: visible Voltcap folder ON and pull (changes a setting) | Owner decides |
| P6 | Owner keeps originals of what can't be copied (profile pictures, manual picks, clips) | Owner |
| P7 | Launch-time sync OFF before B2, read back | Owner |

## Part B — data-transferring (each its own APPROVAL)

| Step | What | Pass |
|---|---|---|
| B1 | Local settings export (if P3 not done today) | file > 0 B |
| B2 | Install 0.6.69 over 0.6.67 (`install -r`, explicit path, hash checked first) | A1 shows 0.6.69 / 76; settings, profiles, art unchanged; migration flag set once |
| B3 | First start with the owner's own sync choice | one entry in "What happened last", no save replaced without a prompt |
| B4 | Owner plays the test game briefly | after-game upload shown |
| B5 | Backup now / auto backup | one entry, `auto_backup_at` moved once |
| B6 | Only if wanted: restore one Drive-only save | owner confirms the save |

## Recovery

- **Contain:** turn Drive sync off.
- **Restore:** import the P3 export; put the P2 saves back only with the owner.
- **Fix:** roll forward to a fixed release (vc ≥ 77, same key).
- Never uninstall, clear data or downgrade.

## Part A results — RP Classic (MC94516AQF091001112, USB, Android 14), 10 Nov 2026, read-only

| Step | Result |
|---|---|
| A1 | **com.shelf.launcher 0.6.67 / versionCode 74**, normal APK (has the notification listener — not compat). Installed base.apk sha256 `1586accc…e119f` = the published `Voltcap-0.6.67.apk`. Signer `88ce4b67ae3825a0…` = the signer of both 0.6.69 APKs → an in-place update keeps all data. Not debuggable. First installed 12 Oct 2026, last updated 6 Nov 2026. **Target: `Voltcap-0.6.69.apk` (normal).** |
| A2 | **A QA hooks file exists in the real app:** `Android/data/com.shelf.launcher/files/voltcap_test_hooks` (0 bytes, 30 Oct 12:57), plus `voltcap_test_snapshot*.json` / `_verify.txt` / `voltcap_qa_screen` from QA runs before the separate QA app existed. In 0.6.67 the file turns the test hooks on (no Drive effect). 0.6.69 release builds ignore it (hooks need a debuggable build or the QA package), so it is harmless after the update. **Owner decides** whether it is removed (not touched). |
| A3 | Drive switches: **not readable without opening the real app's Settings** (private storage, not debuggable). Left to the owner (P7 reads them). |
| A4 | Free storage 77 GB of 101 GB. App's shared folder 52 MB, `/sdcard/Voltcap` 11 MB. All-files access not granted (the visible Voltcap folder isn't in use). |

**What the update can touch on this device** (from the 0.6.69 code, not from the private data, which can't be read without root):
- **Settings, layouts, profiles, controller mappings, libraries, play time:** not changed by the update. The migration only rewrites ScreenScraper media addresses inside the GLOBAL / DEVICE settings files.
- **Saved artwork:** files are renamed to the cleaned address names (`ArtFiles.renameSaved`): never deleted, never overwritten (a rename is skipped when the new name exists). If a rename fails, the picture is fetched again later; the old file stays.
- **Metadata:** descriptions, ratings and similar are unaffected. Clip and manual addresses are cleaned the same way.
- **Accounts:**
  - A ScreenScraper account saved on 0.6.67 includes the developer key the owner typed, and keeps working direct with it.
  - IGDB keys typed by hand keep working.
  - Nothing is signed out.

**Risk found (mixed versions over Drive):** after the update this device stores and uploads *cleaned* addresses (and signed service addresses for newly scraped art). A handheld still on 0.6.67 that receives them through Drive settings / cover sync can't download those pictures (0.6.67 expects the key inside the address). Its own saved pictures stay. **Recommendation:**
- Update all the owner's handhelds in one session, or keep Drive settings / cover sync off on the 0.6.67 ones until they are updated.
- P7 (launch-time sync off) covers the first start.

**Readiness:** A1–A4 done. Nothing blocks P1. P1 is read-only (`ls` / `du`), but like every P / B step it waits for its own approval.

## P1 results — RP Classic, 10 Nov 2026 (approved by the owner; read-only `du` / `find -type f | wc -l` / `ls`, nothing copied or changed)

| Folder | Size | Files | P2 copies it? |
|---|---|---|---|
| `RetroArch/saves` | 1.0 MB | 15 | yes |
| `RetroArch/states` | 26 KB | 1 | yes |
| `Ppsspp/PSP/SAVEDATA` (= `PPSSPP/…`, same folder: shared storage ignores case) | 806 KB | 13 | yes, once |
| `Android/data/xyz.aethersx2.android/files/memcards` (AetherSX2, PS2) | 17 MB | 2 cards | yes (readable) |
| `Android/data/org.dolphinemu.dolphinemu/files/Wii` | 59 KB | 2 | yes |
| `Android/data/org.dolphinemu.dolphinemu/files/GC` | 14 KB | 0 files | nothing to copy |
| `Android/data/com.retroarch*/files` | 143 KB / 119 KB | 3 / 1 (config only) | optional |
| `Android/data/org.ppsspp.ppssppgold` | 7 KB | 0 | — |
| `/sdcard/Voltcap` | 11 MB | 74 | yes (whole folder) |
| ├ `Art` | 8.3 MB | 29 | (in the above) |
| ├ `Data` | 967 KB | 7 | |
| ├ `Settings` | 636 KB | 1 | |
| ├ `Voltcap settings sync` | 128 KB | 1 | |
| ├ `Sync conflicts` | 107 KB | 3 | |
| ├ `Sync backups` | 75 KB | 2 | |
| ├ `Layouts` | 7.5 KB | 1 | |
| └ `Guides`, `Settings snapshots`, `Music`, `Sounds`, `Crash reports` | empty or a few KB | 0 | |
| `Android/data/com.shelf.launcher/files` | 52 MB | 9 (charge log + old QA files) | not needed (bookkeeping) |

- Not present: DuckStation, NetherSX2 / ARMSX2, Azahar / Citra, Eden / Yuzu, Cemu folders.
- The real Voltcap was not restarted (pid 31950 before and after); only its own `charge_log.csv` changed, written by the running app.
- **Note:** `/sdcard/Voltcap/Art` (29 files, 12 Oct) shows the visible Voltcap folder was in use once. P5 (turning it on) is therefore less needed here: much of the art already has a shared copy, but only as of 12 Oct.

## Drive with handhelds on different versions — what can and can't go wrong (code review of 0.6.67 tag and 0.6.69)

| Direction | What travels | Result |
|---|---|---|
| 0.6.67 → Drive → 0.6.69 | Settings sync and cover sync carry addresses **with** the developer key / account inside | 0.6.69 cleans them on the way in (`CloudSync` cover sync, `Backup` import: `SsMedia.cleanText`); the key is added again only when downloading. **No problem.** |
| 0.6.69 → Drive → 0.6.67 | Cleaned addresses (no key) and, for art scraped after the update, signed `vsig` addresses | 0.6.67 uses an address exactly as stored, so ScreenScraper refuses a picture it hasn't saved yet. **Effect:** that game shows its placeholder / another source's art on the 0.6.67 device until it's updated. Saved pictures, metadata text, play time, saves and settings values are unaffected. Nothing is overwritten back: 0.6.67 doesn't rewrite addresses. |
| Game saves (Sync) | Save files | Not affected: save files hold no ScreenScraper addresses, and the 517–519 changes did not touch save sync. |
| Metadata (descriptions, ratings, years) | Plain text | Not affected. |

**Conclusion:** no data loss and no corruption is possible from the mix. The only effect is missing pictures on a 0.6.67 device, gone once it's updated. Updating all handhelds in one session avoids it entirely.

## P2 results — RP Classic, 10 Nov 2026 (approved by the owner)

- **Copy:** `adb pull` (read-only on the handheld) into a NEW folder `Claude outputs\backup_2026-11-10_P2\RPClassic_MC94516AQF091001112\saves\`. The folder didn't exist before (checked), so nothing was overwritten; nothing on the handheld was moved, deleted or changed.

  | Source folder | Kept as |
  |---|---|
  | `RetroArch/saves` | `saves\RetroArch\saves` |
  | `RetroArch/states` | `saves\RetroArch\states` |
  | `Ppsspp/PSP/SAVEDATA` | `saves\Ppsspp\PSP\SAVEDATA` |
  | `Android/data/xyz.aethersx2.android/files/memcards` | `saves\Android_data\xyz.aethersx2.android\memcards` |
  | `Android/data/org.dolphinemu.dolphinemu/files/Wii` and `/GC` | `saves\Android_data\org.dolphinemu.dolphinemu\Wii` and `\GC` |

  The directory structure is kept, including empty folders (GC/EUR, JAP, USA; Wii/import, meta…).

- **Manifests:**
  - `SHA256SUMS_device.txt`: sha256 of the 33 ORIGINAL files, read in place with `sha256sum` on the handheld.
  - `SHA256SUMS_pc_105651.txt`: sha256 and size of every copied file, computed on the PC (`Get-FileHash`).

- **Verification:**

  | Check | Result |
  |---|---|
  | Files | 33 on the handheld = 33 copied |
  | Bytes | 19,185,603 = 19,185,603 (per folder: 1,077,264 + 9,800 + 780,267 + 17,301,504 + 16,768) |
  | Hashes | 33 / 33 identical |
  | Missing, extra, mismatched | none |
  | Permission errors | none (the AetherSX2 and Dolphin folders under `Android/data` were readable) |

- **Seen while copying:** `RetroArch/saves` contains `.stfolder` / `.stversions` — **Syncthing** syncs this folder. That is a second sync path, besides Voltcap's Drive sync, that can change saves during an update. Pause it during B2–B4, or at least know it is there.

## Prepared for approval: P3–P7 (exact operations; nothing done yet)

| Step | Who does what | Changes on the handheld | Claude's part |
|---|---|---|---|
| **P3** | The owner opens the real Voltcap › Settings › Backup › **Export**, ticks game data + device setup + accounts, and saves to `Download`. | One new file in `/sdcard/Download` (holds account keys: keep it private) | `adb pull` of that one file into `backup_2026-11-10_P2\RPClassic_…\export\`, sha256 on both sides. Nothing else read. |
| **P4** | The owner exports each custom layout from Layout Studio (one already sits in `/sdcard/Voltcap/Layouts`). | New `.vclayout` files in `/sdcard/Voltcap/Layouts` | `adb pull /sdcard/Voltcap/Layouts` + manifest. |
| **P5** (optional) | The owner decides whether to turn on "use the visible Voltcap folder" (a setting change; needs All-files access). An `Art` copy from 12 Oct already exists. | A setting and a file copy | `adb pull /sdcard/Voltcap` + manifest (read-only). |
| **P6** | The owner keeps the originals of profile pictures, picked art and clips. | none | — |
| **P7** | The owner turns the launch-time Drive sync OFF in the real Voltcap and reads the switches back. **Also pause Syncthing** for `RetroArch/saves` (found in P2) until B4 is done. | Two switches (owner's own) | Read back nothing private; only confirm with the owner. |

## Batch 521: owner steps for P3 and P4 (about 3 minutes, RP Classic)

P5: the visible Voltcap folder stays as it is (no setting changed). P7 waits until just before an approved install.

**P3 — settings backup (one file)**
1. Real Voltcap › Settings › **Backup**.
2. Tick **Include game data**, **Include this device's setup** and **Include accounts**.
3. Tap **Export to…** (not "Export": that one overwrites `Voltcap/Voltcap settings sync/voltcap-backup.json`, which a sync app may copy to other devices).
4. In the file picker choose **Download** and name it `voltcap-backup-P3-2026-11-10.json` (a `voltcap-backup.json` from earlier is already there; don't replace it). Save.
5. Tell Claude "P3 done".

**P4 — custom layouts (Layout Studio)**
1. Settings › Home › Layout options › Layout Studio options (or Settings search: "Layout Studio").
2. For each layout of your own: tap its chip, then **Save this layout**, choose **Download**, keep the suggested `<name>.vclayout`, Save.
3. Afterwards tap the layout that was picked before (tapping a chip changes which Layout Studio layout is used).
4. Tell Claude "P4 done" and how many you saved. (`Voltcap/Layouts/side-list.vclayout`, 13 Oct, is copied as well.)

**What Claude does then (read only, nothing on the handheld changes):** `adb pull` of exactly those files into a new folder
`Claude outputs\backup_2026-11-10_P3P4\RPClassic_MC94516AQF091001112\` (never over an existing backup), sha256 + size on the
handheld (`sha256sum`) and on the PC (`hash-files`), compared one by one; any mismatch or missing file stops the step. The P3 file
holds account keys: it stays only in that git-ignored folder (never in the repo, a patch, a doc, Drive or the chat), its
contents are not printed, and Claude proposes that you delete the copy in `Download` on the handheld afterwards.

**P6 inventory (read only, 10 Nov):** on the Classic the visible folder `/sdcard/Voltcap` holds Art 29 files (8.5 MB), Data 7,
Layouts 1, Settings 1, Sync backups 2, Sync conflicts 3, Voltcap settings sync 1, Crash reports 30. Not in any backup file:
profile pictures (Voltcap's private `profiles/p<id>.webp`) and art you picked from your own pictures (the originals stay in
your gallery). Checklist for you: [ ] each profile's picture original still in your gallery / PC; [ ] pictures you picked as
covers still in your gallery; [ ] nothing else. An in-place update (B-steps) keeps all private data; this is only insurance.

After P3–P7, B1–B6 follow, each with its own approval. Install the published `Voltcap-0.6.69.apk` (sha256 `1101965042cc84c0…`) only; never a development build.

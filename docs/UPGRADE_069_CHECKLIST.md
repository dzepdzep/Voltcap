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

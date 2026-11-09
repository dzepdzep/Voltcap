# v0.6.68 release plan — identity, backup coverage and recovery (written in 511, corrected in 512; nothing applied to the real install)

> **512 correction.** The 511 version of this plan said a downgrade with `adb install -r -d` was "tested" and safe.
> That test used the isolated QA package (com.shelf.launcher.qa), not the production package. It is **not** proof for the
> real Voltcap, and Android's downgrade rules differ by version and build. Downgrading is no longer the recovery plan;
> rolling forward (below) is.

## Release identity (owner-approved in 512)
- **versionName 0.6.68, versionCode 75.** Same package com.shelf.launcher. Same signing key (signer SHA-256 88ce4b67ae38…44197).
- **Release APK checks (512):** package, code, name and signer as above; not debuggable; HOME launcher present. `assembleRelease`
  output `Voltcap-0.6.68-release.apk` (see SUMMARY 512 for its SHA-256).
- **Installed today (Part A, read-only):** GT78 and Thor run 0.6.67 / vc 74, byte-identical to the rollback APK
  (sha256 1586accc…), same signer, no past signatures. Android 12 (GT78) and 13 (Thor), user builds.
- **Update path:** same signer + higher code is the ordinary Android update path, used for every earlier Voltcap release
  (0.6.66 vc 73 → 0.6.67 vc 74). App data is kept.
- **In-app updater:** it compares the GitHub tag with versionName, so the release tag must be v0.6.68.

## What a Settings backup restores — and what it doesn't
In-app export (Settings › Backup) writes every SharedPreferences file whose scope is GLOBAL. With "include device setup" it also
writes the DEVICE files; with "include game data" it also writes the game data. Format 2, unchanged since 0.6.67, so a 0.6.68
export can be read by 0.6.67.

| Covered (export) | Not covered |
|---|---|
| Look and layouts: `appearance`, `voltcap_features` (Motion, Popup windows, per-layout colours `layout_colors`, spacing, windows follow layout, every newer switch), `layout_presets`, `theme`, `ui_prefs`, `screensaver` | Sign-ins (Drive, RA, Spotify, Steam, RomM, scrapers). Only with "include accounts"; otherwise you sign in again |
| Library organisation: `categories`, `collections`, `favorites`, `shortcuts`, `hidden_apps`, `platform_order`, `physical_media`, `spine_fit` | Bookkeeping: sync status / log, achievement "seen", quick resume, crash guard (INTERNAL) |
| Game data (option): play time, recently played, art / game overrides (the choices), metadata, RA matches, clip / manual addresses | **Files:** the Art folder (downloaded, scanned and manual artwork, custom cart / disc labels), custom layouts (files/layouts), profile pictures, save capsules, clips, guides, manuals |
| Device setup (option): screen layouts, emulator configs, library folders, tuner / charge limit, R1 tiles, mixer | Game saves: these live in the emulators' folders and on Drive, not in Voltcap |

The files only stay with the app's data, which an in-place update or roll-forward keeps. The Art folder is outside it if
"use the visible Voltcap folder" is on (/sdcard/Voltcap). **An uninstall would delete everything else in the right-hand column.**
That is why no recovery step below uninstalls.

## Recovery if 0.6.68 misbehaves on the real install (safest first)
1. **Contain:** the owner turns Drive sync off in Voltcap (stops further transfers), then reports the problem.
2. **Settings only:** import the pre-install export (Settings › Backup › Import, with its preview; Voltcap takes a local
   settings snapshot before every import).
3. **Roll forward — the recovery method:** build a recovery release with a **higher** code and install it as an ordinary update.
   - Either 0.6.68 with the fix (vc 76), or the 0.6.67 code renamed 0.6.67.1 (vc 76) if 0.6.68 has to be withdrawn.
   - Same key, so app data, files and settings stay.
   - 0.6.67 code reads 0.6.68's settings safely: 0.6.68 only adds new keys (no renames, no migrations, no database); unknown
     enum values fall back to their defaults.
   - Prepared on demand, about 20 minutes: change the code and name in build.gradle.kts, assembleRelease, check package / code /
     signer with apkinfo.py, then a supervised install.
4. **Not used:** a downgrade (`install -r -d` of vc 74 over 75).
   - It worked only for the QA package on these two devices; it is not verified for the production package.
   - On some Android versions it is refused for a non-debuggable app or needs the device to be debuggable.
   - **Uninstall + reinstall is never part of the plan** (it deletes app data and files).

**Rollback is therefore NOT claimed as proven.** Roll-forward protects against bad code only; it does not protect data a
sync step changed or files lost some other way — those need the copies in docs/DATA_BACKUP_513.md (513).

 What is verified:
- The update path vc 74 → 75 keeps data (QA package, both devices). The production package uses the same mechanism and key.
- The forward-install mechanism keeps data. A recovery build at vc 76 uses the same path.

## Before Part B (owner approval per step — see REAL_INSTALL_510.md)
- Thor's leftover `voltcap_test_hooks` file was removed in 512 (backed up first; details in SUMMARY 512). From 0.6.68 on,
  release builds ignore that file entirely.
- Installing over the running app stops it, and the home app restarts it; its launch-time sync then runs if the owner's
  switch is on. The install itself is therefore a Part B (data-moving) step.

# Connected Ignition — hardware QA checklist (prepared 12 Nov, NOT YET RUN)

Status: **BLOCKED-HW.** The owner is away from the PC and handhelds. Last check: KONKR asleep; Flip 2 showing the real Voltcap.
The build is ready: `assembleDebug` from worktree `ci` (branch `feature/connected-ignition`, 7673141) succeeded on the PC on
11 Nov. The build's own gradle checks reported package `com.shelf.launcher.qa`, a debug signer (a48b27ed…, not the release key)
and no embedded secrets. Nothing has been installed.

## Hard rules (owner)
- QA app `com.shelf.launcher.qa` only. Never install over, uninstall, clear, launch or send commands to the real Voltcap (`com.shelf.launcher`).
- USB devices only: KONKR GT78 `BW0306N230000845`, Flip 2 `2037b27d` (Thor `3140972f` if connected). Never the wireless Google TV Streamer or Odin2 Portal.
- Any failed gate: stop, report exactly which gate failed, send nothing. Never touch the real Voltcap's updater or system dialogs.
- No system settings (sleep, brightness, animator scale, font size, density, refresh). Reduced motion is tested only if the owner sets it.
- QA-app preference changes are temporary: saved first, restored at the end (`ci_qa.sh prefs-save` / `prefs-restore`).
- Production v0.6.70, FW1 and `main` stay untouched. No push, merge or release.

## Steps (per handheld; KONKR first: smallest screen)
| # | Step | Tool / command | Pass when |
|---|---|---|---|
| 0 | Owner wakes the device and leaves the real Voltcap out of the foreground | owner | — |
| 1 | Identify device | `list_handhelds` | serial is the USB one above, not a `_adb-tls` / ip:port entry |
| 2 | Push helper | `push tools/qa/device/ci_qa.sh /data/local/tmp/` | file present |
| 3 | Gate before install | `shell sh /data/local/tmp/ci_qa.sh pre` | prints `PRE-OK`; otherwise STOP and report |
| 4 | Real Voltcap baseline | `shell sh …/ci_qa.sh base` → save output | — |
| 5 | Install QA build | `install_build serial=<s> variant=debug worktree=ci` | tool's own guard: QA package, not release-signed, USB, listed, production state unchanged |
| 6 | Real Voltcap unchanged | `shell sh …/ci_qa.sh base` | identical to step 4 (version, update time, path, signature) |
| 7 | Save QA prefs | `shell sh …/ci_qa.sh prefs-save` | three files saved |
| 8 | Start-up profiles on | QA app must ask "Who's playing?": `startup_mode` = PROFILES_LAYOUT (default) and ≥ 2 QA profiles | `set` output shows startup_mode; QA profile list ≥ 2 |
| 9 | Matrix: intro × look, cold start, recorded | `set FULL IGNITION` then `cold ci_full_ignition 9` … (table below) | see acceptance |
| 10 | Navigation run per look | `keys ci_nav_<look> 6 DPAD_RIGHT DPAD_RIGHT DPAD_LEFT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT` (rapid: gap 0.3 s) | glide follows each press, no lost press, wraps/stops as before |
| 11 | Confirm + return | `keys ci_pick_<look> 5 BUTTON_A` then home appears; second run `keys ci_keep 5 BUTTON_B` | home builds under the stage, stage fades, no extra popup; B keeps the last profile |
| 12 | Skip during intro | cold start, then `keys ci_skip 3 BUTTON_A` within 1 s | jumps to the settled picker (one press), no double action |
| 13 | Return to launcher | after a pick, HOME key from the QA app (`keys ci_home 3 HOME`) | QA app home, no stage replay |
| 14 | Restore | `shell sh …/ci_qa.sh prefs-restore`; `force-stop` QA app only | prefs identical to backup |
| 15 | Pull evidence | `pull /sdcard/pc/ci <PC folder>` | videos + gfx + launch times |

### Matrix (cold starts, both handhelds)
| Intro | Look | Expect |
|---|---|---|
| FULL | IGNITION | One continuous intro → picker; outline opens into the profile row; gold wire; settles with no frames drawn |
| SHORT | IGNITION | Same, short timing |
| FULL | MIDNIGHT, AURORA, CONSOLE, NEON, OLED, THEME | Continuous intro → picker in the chosen look (no wire lines except IGNITION) |
| PIXEL (32-bit) | IGNITION and MIDNIGHT | Old 32-bit intro, then the normal picker flow appears (Who's playing? still asked) |
| OFF | IGNITION and MIDNIGHT | No intro; the normal picker flow appears |

### Acceptance (all must hold; otherwise FAIL with the clip)
- No black or empty frame and no home flash between intro and picker (frame-step the recording around the hand-off).
- Focus starts on the active profile; every D-pad press moves exactly one step; rapid presses glide without queue lag.
- Selected profile clearly stronger than the others; quieter unselected rings (rev 2).
- After A: the light runs in, the home appears under the stage, the stage fades in about 380 ms; no popup over it.
- Performance from `gfxinfo`: 90th percentile ≤ 16 ms on KONKR during the stage, no frozen frames > 100 ms; record launch TotalTime per case.
- When settled: no frames drawn (gfxinfo frame count does not grow while idle for 3 s).
- Real Voltcap identical before/after (step 6). QA prefs restored (step 14).

### Not covered here (owner action needed)
- Reduced motion / animations off: needs a system setting change (not allowed without approval). Expected: no stage, the existing intro rules apply.
- Thor: not connected.

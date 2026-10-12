# Voltcap pre-release checklist

Work top to bottom. Tick each box only after checking it on a real device.

## 1. Build
- [ ] `app/build.gradle.kts`: raise `versionCode` (2 now, `versionName` 0.2.0) for every release.
- [ ] Release build is signed with the release keystore (`keystore.properties`, see GITHUB_RELEASE.md), kept out of git.
- [ ] R8 is on for release builds (Voltcap's own classes are kept whole). Test a release build: settings, sync, scraping, RA, Discord.
- [ ] The package is still `com.shelf.launcher`.
- [ ] No class, file or label uses "Switch". Real console names in ROM / platform data don't count.
- [ ] `src_copy` matches the project sources. `SwitchHome.kt` is deleted (its code moved to TileHome.kt).
- [ ] Clean build with no warnings promoted to errors. The `MainMenu` and `GameDetailsOverlay` methods stay under the method-size limit.

- [ ] Target SDK 36 / AGP 9.3 / Gradle 9.5: clean build after the update; NDK 28 installed for the Discord bridge.
- [ ] Google Drive: OAuth consent screen published, Android clients for the release + debug SHA-1 (GITHUB_RELEASE.md).

## 2. Devices to test
| Device | Why |
|---|---|
| AYN Thor | Two screens, crash-on-start history, RGB lights, root service |
| AYN Odin 2 / Portal / Mini | Root service, SN3112 stick lights, fan modes |
| Retroid Pocket Classic | Small screen, no root: FPS fallback, tile size |
| Retroid Pocket 5 / Mini | No root, Snapdragon display driver FPS |
| Any phone / generic Android | Nothing vendor-specific may crash |

## 3. Start-up and safety
- [ ] Cold start on Thor: no crash after 8–12 s. Test with the second screen on and off.
- [ ] Safe mode: force 3 crashes. The banner shows, the second-screen companion stays off, and the crash report can be exported.
- [ ] First run, including the permissions step and "Allow everything".
- [ ] Home button, Back and recents behave as a home app.

## 4. Features from this round
- [ ] Charge limit: pauses at 80 %, resumes below 77 %, survives a restart. The notification only shows while charging.
- [ ] Drive save browser and status popup.
- [ ] Background music: no gap at the loop point, no drums, every theme.
- [ ] Album-art blur slider affects every player.
- [ ] Music overlay over games: a tap opens the controls, and they close on their own.
- [ ] No session popup after a game. "Last session" shows in details.
- [ ] R1 tiles: Power tier, AutoTDP, CPU tuner (when ClusterTune or PULSE is installed).
- [ ] Inline achievements: ▲▼ scroll, A expands, RA display order.
- [ ] App drawer: hold or Start opens App info, Mark as game, Hide, Uninstall.
- [ ] FPS: "Why n/a?" lists each source. The RP Classic shows a number or a clear reason.
- [ ] Electric focus effect in the classic row and Tile home, at every card size and tile size.
- [ ] Tile size slider at 80 % and 170 %. The name, buttons and pills scale with it.
- [ ] Category pills sit above the names and align with the row (left, centre, right).
- [ ] Stats don't count Voltcap, other launchers or system apps.
- [ ] Themes: every theme, every animated scene. Lite effects shows a still frame. Check the battery drain of animated scenes over 30 minutes.

## 5. Performance tuner (root devices)
- [ ] Each tier applies. Read back `scaling_max_freq` and `max_pwrlevel`.
- [ ] Caps come back after the vendor daemon resets them mid-game (watch for about 1 minute).
- [ ] AutoTDP: level drops on a 30 fps game and rises on a demanding one. The learned level is saved.
- [ ] Fan curve and hold-temp: the fan reacts, and the "System" setting gives control back.
- [ ] Screen-off profile, apply after restart, Restore stock.
- [ ] Per-game profile: refresh rate, button layout and charge limit switch at launch and are put back after.
- [ ] The Quick Settings "Power tier" tile cycles the tiers.

## 6. Ideas list
- [ ] Thor RGB: Settings › Device lists the light driver, and colours change.
- [ ] Import from ES-DE, Pegasus and Daijisho on a real install. Nothing is removed.
- [ ] Smart categories (Never played, Not played lately) and the Backlog status.
- [ ] "What should I play?" from the hotkey and the tile button. "Something else" rolls new picks.
- [ ] Regions and duplicates: one copy per game, and played or starred copies stay.
- [ ] Share preset → paste on a second device.
- [ ] Quick resume: leave a RetroArch game and start it again. It resumes rather than rebooting.
- [ ] Manual: a PDF next to a ROM opens. A ScreenScraper manual downloads once.
- [ ] Steam achievements with a public profile, and the error text with a private one.
- [ ] RA friends list and a game with leaderboards.
- [ ] Phone remote: wrong code is refused, D-pad works, starting a game works. When switched off, the port closes.
- [ ] Diagnostics report: everything is listed, and sharing it works.

## 7. Privacy and legal
- [ ] No ROMs, BIOS files or copyrighted art are bundled.
- [ ] Theme names and art are original: no trademarks, characters or logos.
- [ ] No GPL code was copied from PULSE or ClusterTune. Only hardware facts were reused.
- [ ] API keys for Steam, RA and ScreenScraper are stored only on the device and never logged.
- [ ] The phone remote is off by default and needs the code.

## 8. Ship
- [ ] Release notes written, covering the user-visible changes from this round.
- [ ] Backup and restore of settings tested between two devices.
- [ ] Tag the commit, archive the APK and the mapping file.

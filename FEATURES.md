# Voltcap — everything that's in (as of update 43, 6 Oct 2026)

Grouped by area. Update numbers in brackets point to SUMMARY.md for the details. "Not done yet" is at the end.

---

## Library and launching
- **Sources:** ROM folders (several, deep search for console folders, multi-disc games merged), installed apps, Steam games (GameNative / Steam shortcut import), imports from ES-DE / Pegasus / Daijisho.
- **Duplicates:** region duplicates merged into one entry.
- **Organising:** categories (consoles, collections, franchises), smart categories, play statuses (Playing / Finished / Dropped / Wishlist / Backlog), and sorting and category order you choose.
- **Special categories:** Smart Continue and Rediscover; Explore shows the whole library equally [18].
- **Game states:** NEW / PLAYING / ON HOLD / COMPLETED / MASTERED / 100 %, off by default [20].
- **Finding a game:** quick jump by letter (L3) and "What should I play?". (The library grid / list view was removed in 42.)
- **Running games:** shown in the Cocoon style, with quick resume for RetroArch. Games still open pulse green [10].
- **Launch screen per game or console:** Screen layouts rules go This game → Collection → Console → Emulator → Everything else.
- **DroidDeck:** its exported launch files work as games from a ROM folder [20], and its "Add to home screen" shortcuts (any app's) can be added to Voltcap as games [43].
- **Low power while a game runs:** background work waits and Voltcap wakes up less [5].

## Home screen layouts
- **Classic row:** top / middle / bottom, "Wide for the first game only", and a game header above the row (showcase layout) [8, 13, 19].
- **Tile home:** round shortcut buttons you can move anywhere, resize and pick [22].
- **Hero carousel (vertical):** ▲ ▼ change game, ◀ ▶ flip between Overview / Achievements / History / Screenshots / Stats / Guide pages. Idle focus mode fades the UI [15].
- **Grid home:** the category fills the screen as a grid, in any card art [25].
- **TV room:** the game plays on an old TV (its clip, else its screenshots, else its art) with a music on-screen display. A console sticker banner sits above the TV (confetti in that console's controller shapes), the games beside it as cases with spines, covers only or a shelf of spines, and an info bar under it all [29]. The TV is a wood-cabinet set seen at an angle, with grille, knobs, channel number, aerial and glow [44].
- **Six more layouts** [77], each with its own options section: **Dashboard** (glass panels, round-button menu, Now playing card, Quick access, player + system status), **Live tiles** (category words, big + small flat tiles, coloured shortcut tiles), **Side menu** (dark menu with emblem and glowing orb, art behind, row at the bottom), **Cross menu** (category icons across, games as a list), **Spotlight** (art full screen, small tiles on top, big title, Play and activity cards), **Icon bar** (shortcut icons on top, square tiles, Start).
- **Card art types:** covers, wide capsules, boxes, heroes, screenshots, and cartridges / discs.
- **Your template pack** (43): real empty cartridges / cards / UMDs for 33 consoles with the label scan in the window (or the label-frame version with the cover), real discs for 9, 3D boxes for 37 (box scan or cover in the system's border, the system's spine, glass, the game you're on turning slightly).
- **Per layout:** each home layout keeps its own card artwork, and can leave out cartridges or discs [43].
- **Cartridge / disc look (consoles without a template):**
  - Drawn per console: N64, SNES, NES, GB / GBC, Virtual Boy, GBA, DS, 3DS, Switch, Vita, HuCard, Sega and Atari carts, Game Gear and small cards.
  - Discs: PS1 / Saturn dark, Dreamcast GD, Wii white, plain, and GameCube mini.
  - PSP UMD as the real pick-shaped shell with a round window.
  - The game's label or cover is printed on the shell.
  - Real-size GameCube discs, with a "Bigger GameCube discs" switch [25, 30, 31, 32].
- **Home extras (all optional):**
  - Category slide with L2 / R2 (only on the step itself) [42].
  - Cover light: a glow in the game's colours.
  - Charge-gauge battery.
  - Soft blur behind the games, with a strength setting [27, 28].
- **Edit Home:** move the music player and the round shortcuts, with global safe zones [17, 22].
- **Living Library:** activity marks on cards. Off by default, and it never reorders or hides games [18].
- **New game arrival animation,** details window growing out of the card, and a category-change style (swap / fade / slide / drop) [13].
- **Status bar:**
  - Time, battery, Wi-Fi, sync picture and music.
  - A trophy for achievements earned in the game you just left.
  - A short line after each session [15, 17].
- **Button legend** that follows the handheld's Xbox / Nintendo layout, plus the R3 all-controls card [2, 11].

## Look and feel
- **Interface styles:** Glass (Volt), Energy capsule, 32-bit console, Memory card, CRT, Neon sign, Cartridge, and Classic [24, 27, 29].
- **Volt pieces:**
  - Voltage line with sparks, accent switches, underline tabs, edge cards and the focus glow.
  - All of them in the theme's colours (accent flowing into the second colour) [24, 25].
- **Pictogram tiles** for visual choices and an icon on every settings group [24].
- **32-bit style** (Appearance › Interface style): Font Normal (32-bit headings with the 32-bit console style) or 32-bit everywhere (names, categories, status bar, a size smaller), pixel corners, pixel artwork [20, 27, 43].
- **Focus effects:** soft glow and electric frame (redone), with a lite fallback [25].
- **Density and text size:**
  - Compact / comfortable spacing.
  - UI size, and a text size that's separate from it.
  - Titles that shrink instead of showing "…" [13, 18, 22].
- **Colours:**
  - HEX boxes and a separate background colour.
  - Saturation and brightness sliders.
  - Colours from the game's art or the album cover, or the whole app in the game's colours [11, 15, 22].
- **Gesture pill:**
  - "Hide navigation bar": immersive mode in every window and popup, and again after the keyboard closes.
  - "Force-hide gesture hint": changes the device's hint setting through the handheld's root service (or a one-time adb permission), restores it on leave. No Shizuku [17, 33, 37].
- **Sounds:** UI sound sets (Console, Chill, 32-bit, Dark fantasy) that can follow the music [16].
- **Background music:** four looping tracks (Home, Haze, Memory card, Hollow) [16].
- **Intro and launch animations,** including the 32-bit intro with Volt [11, 20].
- **Screensaver,** with themes and "stay on while music plays" [13].

## Themes
- **Animated GPU scenes:** well over 100 themes in groups (Space & tech, Nature, Gaming eras, Music, Anime, Crafted & cozy, Creature types (all 18), PlayStation-style…).
- **Recent work:**
  - Power Aura: gold to blue over time.
  - Wave menu and Motion plaza with depth.
  - A long fix list (Terra, Holo foil, Neon puddle, Campfire, Venom, Paper layers, 64-bit plains, Vector arcade…) [21].
  - New themes: CRT tunnel, Memory card, 32-bit racing, Energy core and 32-bit fire [21].
  - **Waterfall cliffs:** your pixel waterfall, animated [32].
  - The brief's themes [56]: Handheld boot, Pocket beat (D-pad left, A/B right, music), Console disc, Arcade attract, Neon cartridge, Data stream, Portal gallery, Poster wall, Pixel world.
  - **Achievements & trophies** [56]: Trophy vault (32-bit hall of honour) and Badge constellations.
  - **Creature types · 32-bit** [56]: pixel versions of all 18 types.
  - **Gaming room** [56, 57]: your painted room. The monitor shows the game's hero art and screenshots, or its gameplay clip, and sits in the open space above or below the game row. The poster above it shows the game's cover.
  - **Trophy vault** [57]: your latest mastered games (gold) and unlocked achievements (silver) from RetroAchievements and Steam on the pedestals, spinning; new unlocks appear while you play.
- **Own-colour marks** [56]: ⊘ = the colour source can't change it; ◐ = it changes only a little.
- **Music-reactive themes:**
  - The player and lyrics are built into the scene.
  - Equalizer and lyrics positions, cover subject detection, Pan & zoom [1–15].
  - Spins and scrolls run on playing clocks: smooth, and they ease to a stop on pause [44].
  - Holo card with the song and artist in the foil, turning in perspective; Record shelf rebuilt as a record-store corner [44].
- **Music · lyrics group** [44]: Lyric cloud, Karaoke (fill + bouncing ball + next line), Lyric sheet (scrolling), Neon lyrics (words light up as tubes). Toggles: next line, bouncing ball.
- **Theme tools:**
  - Per-theme look, live preview and full-screen preview.
  - Favourites with rotation.
  - Scene per category, colours from the game, time of day and fade between scenes.
  - Scene style (speed, particles, darken, colour shift).
  - Theme intensity, and artwork priority (Smart / Hero / Screenshot / Cover) with an "Animated only" filter [8, 17, 19, 20].
- **Changes in 42:** Night drive removed; Retro TV now uses the CRT room's 3D TV with the song's cover (Android 13+).
- **Your own wallpaper:** a picture, GIF, WebP or video [10].
- **Performance:**
  - Lite effects and adaptive automatic quality.
  - Save power when idle, and 60 fps on high-refresh screens [8, 13, 18].

## Per game
- **Game settings › Look for this game:**
  - Background theme, artwork priority and animated artwork.
  - CRT effect, theme intensity, ambience (particles, rain, snow, embers, CRT noise, art lighting) and state [20].
- **Transitions:** Fade, Slide, Cover Flow, Pixel Dissolve, CRT Switch, Portal or Memory Card, at Fast / Normal / Cinematic speed [20].
- **Per-game screen layout, emulator and profile** (performance).

## Game details window
- **Store-page layout:**
  - Colours from the art, Play styles (Pill / Block / Ring / Capsule / Text) and Continue.
  - More for the secondary actions, Game Journey and one milestone line [8, 18, 19].
- **Gallery and info:**
  - Artwork gallery and picker, your own emulator screenshots, and video clips.
  - Manuals (scraped automatically) and guides (saved offline) [4, 11].
- **Sources:** SteamGridDB (including animated art), IGDB, RAWG, TheGamesDB and ScreenScraper.

## Achievements
- **RetroAchievements:**
  - Progress shown as "31/70 · 44 %", with rarity.
  - Missables to get, Easiest next and Almost mastered.
  - Tips, friends, leaderboards and Achievement of the Week.
  - Games not on this device open from the profile [7, 13, 15, 17].
- **Steam achievements:** with just your public profile, or with a Web API key [57].
- **Unlock animations:**
  - Capsule charge for new unlocks only; a manual pick pops the badge.
  - At 100 % the badges fly into a wall and the game gets the MASTERED stamp.
  - Replay latest [15].
- **Badge wall:**
  - Badge view toggles (name / percentage / count).
  - Achievement themes: Trophy Shelf, Vault, Capsule Pop, Poster Wall, Arcade Scoreboard, CRT Unlock, Constellation, Energy Grid… [15].
- **Separate screens:** Achievements, Stats and Profile [15].
- **Recaps:**
  - Week / month / year, with a podium and badge-wall or hero background.
  - Year in Voltcap [11, 13, 18].
- **In-game overlay:**
  - Trophies overlay (next, missables, pin an achievement).
  - Voltcap look; tap for details [11, 15, 17].

## Saves
- **Google Drive sync:**
  - Only real saves, never save states.
  - Conflicts ask you; sync before and after playing.
  - Last played syncs across devices [22].
- **Save Vault / Save Explorer:**
  - Memory cards are read: PS1 cards, the PS2 8 MB card and folder cards.
  - One capsule per game.
  - Filters: memory cards, external, played in Voltcap, hidden, on / off device.
  - Styles: Classic, Capsule, SD card, Memory card, Floppy.
  - Endless grid [15, 17, 22].

## Music
- **Players:** Spotify (Liked Songs first) and any player via media sessions, as a corner player, the big player and the Now-playing scene.
- **Lyrics:** LRCLIB (opt-in), positioned away from the cover's subject.
- **During games:** a music overlay with a blurred cover.
- **Corner visualizers,** and controller lights that pulse with the music [8, 22, 23].

## Second screen and companion
- Header: only the current page's name with subtle arrows to the others (on by default; Screens and Appearance › Second screen) [41, 42].
- **Details page:** the real case in 3D next to the story, tap it to flip open to the cartridge / disc (or that first) [43].
- **Game page:** A on a game opens its details window on the second screen as a "Game" page; arrows / swipe / B leave it. Toggle in Settings › Screens [42].
- **Music effects there:** music-theme scenes and corner visualizers move to the second screen while it's up (toggle) [42].
- **Visualizer:** album cover card optional (off by default); never a second cover over themes that show it [42].
- **Screensaver there:** black by default [42].
- **Screen layouts and rules** for a built-in second screen or a TV.
- **Second-screen home:**
  - Art pages: art, video, details and "Cartridge + details" (real media + story).
  - Achievements board with filters, list and detail.
  - Music (with Visualizer layout) and the dock; the big player opens inside the Music page, so you can keep swiping [47]. (The Games page was removed in 42.)
  - Volt header with page tabs and swipe to flip pages (Thor) [8, 22, 24].
- **Animate only on the second screen** (dual-screen devices) [11].

## Volt, the mascot
- **Your art:**
  - 27 poses from your new sheet, including stretch, loaf, prowl, idea, gaming and nine faces.
  - "Drawn" (default) or the small "Pixel" set [32].
- **Small animations:**
  - Blinks, a squash and bounce on pose change.
  - Per-pose motion (music sway, wave, happy hops, walk bounce, gaming jiggle, sleepy breathing) [32].
- **Where he shows up:**
  - Sits on your game and hops as you move (sit / peek / mix), or wanders the background (walks, prowls, loafs, plays, stretches). Off by default; every pose at the same size [42].
  - A "Volt asleep" screensaver; he never shows over the other screensavers [42].
  - Naps in a corner.
  - On the second-screen header and on the TV [24, 29, 32].
- **Guide:** first-time setup guide, tips, a help hub and "Show me around", a spotlight tour of the real home screen [15, 26].

## Device
- **Power:**
  - Performance tiers, AutoTDP, CPU tuner, fan curve and per-game profiles.
  - Per game, one tap each: underclock, fan and Xbox / Retro buttons, put back after the game [38].
  - Profiles per console (own → console → device-wide), copy a game's profile to its whole console [39].
  - Heat guard: steps the underclock down while a game runs hot, back up once it cools [39].
  - Measured per game and profile: battery % per hour, watts, temperature, frame rate [39].
  - Underclock like ClusterTune / PULSE: Off / Light / Medium / Heavy / Custom ceilings per CPU cluster and GPU, on R1, the in-game panel and Quick Settings [37].
  - FPS / performance overlay.
- **Battery:**
  - Real 80 % charge limit (held and re-checked).
  - Sleep mode for Wi-Fi / Bluetooth.
  - Battery sleep mode [4, 5, 10, 14].
- **Controller RGB lights,** including on the AYN Thor [22]. "Off" turns both sticks and the handheld's own RGB setting off (restored after); theme colour runs dimmer [42].
- **Quick Settings tiles:**
  - **Voltcap overlay** (tap cycles Off → Stats → Music → Trophies → All).
  - **Voltcap power / TDP.**
  - **Voltcap underclock** (Off → Light → Medium → Heavy → Custom) [37].
  - **Volume mixer** (opens the per-app volume card) [40].
  - Hold any of them for the in-game panel.
  - **Adding them:** Settings › Device › Quick Settings tiles (and the last setup step) shows which are in the menu, adds them (Android 13+ prompt, or all at once with the root service) and the steps by hand [45].
- **Pause music when closed:** closing the handheld pauses Spotify, other music apps and Voltcap's own music [46].
- **Underclock "With power":** the power tier / AutoTDP style picks the underclock unless you pick one [46].
- **Volume mixer:** each app's own volume (games, music, video), from Quick Settings, the R1 menu, the in-game panel or a hotkey; kept after a restart [40]. Two ways to set it (privileged helper or a sound effect on the app's audio), per-game volume, pinned apps, Android's other volumes and a Mixer check report [45]. Needs the handheld's root service (AYN / Retroid), or the DUMP permission granted once by adb for the effect method.
- **Follows Android's shade:** refresh rate, Wi-Fi, Bluetooth, DND, fan and Voltcap's own tiles show up in Voltcap at once; the tuner adopts a fan mode picked there [40].
- **In-game controls:** one in-game notification with Size / Opacity / Power, and overlays you can drag anywhere [17, 18, 23].
- **Permissions:** real permission states, and a shortcut to "Display over other apps" [18].

## Settings and system
- **Settings:** sub-pages with folding groups, compact spacing and icons [17, 22, 24]; a search bar that jumps to any option [47].
- **Hotkeys:** six hotkeys and plenty of actions [13].
- **Backup and moving to another device:**
  - Backup / restore, with an import preview that lets you untick groups.
  - Share with another device by PIN, QR code, Google Drive or file; accounts included [17, 18].
- **Other:**
  - Discord status, the phone web remote and the weekly recap.
  - Stats, BIOS check, crash guard / safe mode and a diagnostics report.

---

## Not done yet (checked against the original brief and all your messages)

**Themes from the brief:** all done [56].

**Integrations:**
- DroidDeck / PXPlay direct per-game launching needs the installed apps' exported intents (`dumpsys package` output from your device).

**Release checklist:**
- Delete `SwitchHome.kt` from `app/src`.
- Raise `versionCode`.
- Set up release signing (`keystore.properties`).
- Google Drive for other people: publish the OAuth consent screen (it's in Testing, so only listed test users can sign in) and add an Android OAuth client with the release key's SHA-1 [36].
- Run the real-device test list and write release notes.

**Not run on a real device yet (from the update notes):**
- Volt's new animations.
- The waterfall's frame rate on weak devices.
- The UMD with real label scans.
- Force-hide gesture hint through the root service (Shizuku removed in 37).
- The back gesture on each screen.
- The TV room on the Thor's lower screen.
- Update 46: TV room room + CRT, free-standing media in every layout, GBC cartridge choice, Save Vault speed / capsule crack / memory card, lid pause, underclock "With power", hero retry after unlock.
- Update 45: the build itself; the mixer's effect method and DUMP grant, per-game volume, tile prompts / "Add all", every screen after the overlap sweep (Thor lower screen, large font).
- Update 44: the lyric themes' words, the new TV, Holo card with long titles.
- Update 43: 3D boxes in a full grid (frame rate), the case flip, DroidDeck's pin request, the 32-bit font size.
- Update 42: keys / back gesture with the unfocusable second-screen window, the Game page, mixer one-line answer and dumpsys fallback, RGB Off on both sticks + system keys, Retro TV with a cover, Volt's even sizes.
- Update 41: the second screen's arrows on the Thor.
- Update 40: the mixer helper on the Thor, per-app volume with emulators, shade → Voltcap updates with the R1 menu open.
- Update 39: heat guard stepping, console profiles at launch, measurements, copy to all.
- Update 38: per-game buttons / fan / underclock, also after Voltcap was closed mid-game.
- Update 37: underclock levels on the Thor, its Quick Settings tile, mid-game tile changes.
- Update 36's fixes: Spotify sign-in with a pre-connecting browser, an interrupted Drive download, Dolphin folder sync after a failed write, sleep mode at screen-on, touch actions after changing settings.

**From your 26-point list:** all done (42–44).

**Ideas, not promised:**
- Volt reacting to a game launch or unlock toast.
- More pictograms (focus effect, other-games look, positions).

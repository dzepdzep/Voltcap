# Voltcap test checklist (26 Oct 2026, after update 335)

Tick what you've tried. ✗ + one word if it's broken, and I'll fix it.
**Me** = I can test this over USB once the Thor is authorised again. **You** = needs your hands, an account or another device.

## 1. New first start-up (being rebuilt now: update 336)
- [ ] You: Settings › About › "Run the setup again" (or a fresh install) runs through every step
- [ ] You: profiles step: name, picture, PIN, start-up mode
- [ ] You: layout & look step: browse, preview, pick
- [ ] You: RomM step with a pairing code, Drive saves step
- [ ] You: emulators step shows what's installed per console; second-screen step (Thor)
- [ ] You: "Restore from a backup / Drive" on the Welcome step still works
- [ ] Me: every step with D-pad only, then touch only

## 2. RomM (never tested against a real server)
- [ ] You: RomM running on the PC (steps in the chat)
- [ ] You: sign in with the 8-character pairing code
- [ ] You: sign in with user name + password instead
- [ ] You: Browse RomM: consoles with game counts, covers, search
- [ ] You: download one small game → lands in the right console folder, shows in the library
- [ ] You: multi-file game (a PS1 .cue/.bin) → zip unpacked into its own folder
- [ ] You: download with the screen off / Voltcap closed, then a cancelled download
- [ ] You: R1 › RomM tile appears after setup, hold it → opens the RomM settings

## 3. Profiles & PIN (332 / 334)
- [ ] You: add, rename, remove a profile; own picture from the gallery
- [ ] You: second RetroAchievements account on a second profile
- [ ] You: favourites / Continue playing / play time separate per profile
- [ ] Me: set a PIN, wrong PIN shakes, right PIN opens, B at start on a locked profile asks for it
- [ ] You: in a game › Quick menu › "Who's playing…"; the "Who's playing?" hotkey
- [ ] You: Google Drive sync while a second profile is playing (it should still sync profile 1's saves)

## 4. Status bar (333 / 335)
- [ ] Me: screenshots of round 2 (Console hub, Resume, Console stack, Cube, Memory card, Night)
- [ ] You: games at the top (bar at the bottom), music player in the bar, a narrow phone screen
- [ ] You: Settings › Look & feel › "Status bar matches the layout" off → the plain bar everywhere
- [ ] You: Edit Home: move / resize / hide the clock & icons in 3 layouts

## 5. Cases & media (331 undone)
- [ ] You: case fronts / backs look like before 331 again (PS2, Switch, GBA)
- [ ] You: discs whole inside their slot in Case stacks and the Voltcap row
- [ ] You: (optional) delete the 48 unused pack pictures in `app\src\main\res\drawable-nodpi`

## 6. Layouts (330 phases 1–4)
- [ ] You: every option value of the new layouts (Memory card's Sways / Lifts, Case flow speeds, Orbit, Airy, Night…)
- [ ] You: second screen with the new game windows open
- [ ] You: backup → restore keeps the new layout options
- [ ] You: Retroid Pocket Classic / Flip 2 / Odin: one pass through the layouts

## 7. Older, still open
- [ ] You: Eden Duo booting a Switch game (not installed on the Thor)
- [ ] You: RGB lights (can't be seen over adb)
- [ ] You: a real Steam / PS5 / Xbox game's case
- [ ] You: two-device save conflict, Bluetooth reconnect, hours of play
- [ ] You: RP Classic charging log (#99), RetroArch video driver → GL

## Then: Reddit trailer
Once 1–4 are ticked: pick the 6–8 best moments (start-up with profiles, layout switching, 3D cases, the status bars,
RomM download, Who's playing mid-game) and we script and record it.

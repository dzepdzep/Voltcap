# Hardware QA requests (physical handhelds)

Cloud work never waits for the PC or a handheld. When a change needs a real device, add a request here (and, where the
session can, a GitHub issue labelled `hardware-qa`), then carry on with other work. When the owner says the PC and handhelds
are available, a live session works through the OPEN requests with the PC's remote-device tools: isolated QA app only
(`com.shelf.launcher.qa`), production apps / settings / saves / emulator configurations untouched, device state restored after
every run, results pushed and the requirements matrix updated. A request is DONE only with a real-device PASS; an emulator
result never closes one.

Never assume the PC or devices are connected. Never wake, connect, install to or change a device without the standing
authorization in CLAUDE.md (USB only, QA app only).

Devices: Flip 2 (Retroid Pocket Flip2, 2037b27d, 1920x1080 @ 360 dpi), KONKR (GT78, BW0306N230000845, 960x640 @ 240 dpi),
RP Classic, AYN Thor (3140972f, two screens), Odin.

## Format
```
### HW-<n> <title> - OPEN | IN PROGRESS | DONE | BLOCKED
- Device(s): ...
- Build: commit <sha> on claude/qa-0672-repair; QA build = assembleQa / assembleDebug of that commit
- Why not emulator: ...
- Tests: exact gates / scripts (e.g. pop529.sh run <tag> <off|font=1.3,density=1.15> "<LAYOUTS>" OWN, then popclip.py)
- Result: (filled in by the hardware session: run tags, PASS / FAIL counts, evidence path)
```

## Requests

## Next hardware session (batched) - TODAY, 16 Nov (owner: KONKR + other handhelds; waits for "devices ready")
1. Identify every USB handheld (serial, model, Android, screen, dpi; USB not wireless - CLAUDE.md device rule). QA app only.
2. One QA build of the newest dev commit on each; register it in tools/qa/builds.json.
3. Full popup gates x1.0 + x1.3 on each device (two at a time, ~50 min each): closes HW-1 for those devices, HW-5, HW-6.
4. Record crate checklist (HW-2) and Cube first press (HW-4) on the KONKR; controller / onepress / animation checks where
   the device differs (screen size, controller).
5. Restore every device (snapshot diff=0, fatal=0), push results, update releases.json gates.
Flip 2 (unavailable): its 15 Nov gates stay HISTORICAL evidence for v0.6.73 (owner 16 Nov); its own session (popup gates on
the newest build + the charging reproduction, HW-3) waits until it is back.

### HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) - IN PROGRESS
- Device(s): Flip 2, KONKR.
- Build: c7ad53a7 (674n code = cf629e2e) - QA build installed on both 15 Nov 21:24.
- Why not emulator: the 53-layout gate's PASS needs real GPU / panel / controller; emulator evidence does not count.
- Tests: Flip 2 x1.0 `f674o`, KONKR x1.3 `k674n` (running 15 Nov from 21:28); then Flip 2 x1.3 and KONKR x1.0 on the same build
  to confirm the 674n fixes (Side menu Save sync, Bar arrows) and 674m glyphs (Arcade, Pocket ribbon).
- Result so far: Flip 2 x1.3 f674k (674k) 52/53 - Bar ◀; Flip 2 x1.0 f674o (674n) 52/53 - Side status line; KONKR x1.0 k674m (674k)
  51/53 - Arcade, Side; KONKR x1.3 k674n (674n) 51/53 - Library R2, Spot START. All five fixed in code since (674m-675e): a full
  re-run of all four on the newest build closes this request.

### HW-2 Record crate x1.3 vertical menu - verification checklist - OPEN
- Device(s): Flip 2 + KONKR (x1.0 and x1.3).
- Build: latest dev commit.
- Why not emulator: owner's checklist is about real D-pad / focus timing on the handhelds.
- Tests: every action incl. MORE reachable by D-pad; MORE once, last, no dots; More open / close restores focus; rapid A/B
  reliable; nothing clipped at x1.0 / x1.3; menu never changes size while navigating. (Visual design APPROVED by the owner 15 Nov.)

### HW-3 Flip 2 charging sparkle - reproduce first - BLOCKED - DEVICE UNAVAILABLE
- Device(s): Flip 2. Why not emulator: OEM charging switch (`input_suspend`) behaviour.
- The 674i patch is NOT on the dev line, never installed, never tested (branch claude/charge-loop-674i, docs/claude/patches/).
- Evidence conflict (owner 16 Nov): the owner saw the battery drop to ~78 %, charge back to 80 % and the sparkle only at
  80 % - one cycle, not a loop. The 15 Nov 20:20 log showed production's limit toggling `input_suspend` every 1-2 s at 79-80 %.
- Next, when the Flip 2 is back: read-only reproduction first (production Voltcap as it is, plugged in near 80 %, 15 min:
  logcat + battery state + whether the sparkle shows more than once). Only if it loops: apply the patch to a QA build and one
  supervised test with production's limit off (owner's step). If it does not loop: close the patch as not needed.

### HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px - OPEN
- Device(s): KONKR. Build: latest. Tests: popclip gate CUBE at x1.3 + onepress.

### HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) - OPEN (watch)
- Device(s): KONKR + Flip 2. 674k gates (f674k, k674m) had no rapid FAIL; keep watching in HW-1 runs before closing.

### HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) - OPEN
- Device(s): KONKR (x1.3, where k674n read dy +3 px) + Flip 2 x1.0. Build: 675e or later (QA app).
- Why not emulator: the reading came from the KONKR's real panel and backdrop at x1.3; emulator evidence never closes it.
- Tests: popclip / navclip on SPOTLIGHT home with QA traces; glyphcheck on the run folder - START / SELECT / A / X / Y must read
  source "InkGlyph+key" and PASS (limits unchanged, max(1.5 px, 5 %)); look at the crops of anything over 4 %.

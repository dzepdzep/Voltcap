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

### HW-1 Full popup gates on 674n (x1.0 + x1.3, both handhelds) - IN PROGRESS
- Device(s): Flip 2, KONKR.
- Build: c7ad53a7 (674n code = cf629e2e) - QA build installed on both 15 Nov 21:24.
- Why not emulator: the 53-layout gate's PASS needs real GPU / panel / controller; emulator evidence does not count.
- Tests: Flip 2 x1.0 `f674o`, KONKR x1.3 `k674n` (running 15 Nov from 21:28); then Flip 2 x1.3 and KONKR x1.0 on the same build
  to confirm the 674n fixes (Side menu Save sync, Bar arrows) and 674m glyphs (Arcade, Pocket ribbon).
- Result so far (674k build): Flip 2 x1.3 f674k 52/53 (Bar ◀ glyph); KONKR x1.0 k674m 51/53 (Arcade glyph = 674m, Side Save sync = 674n).

### HW-2 Record crate x1.3 vertical menu - verification checklist - OPEN
- Device(s): Flip 2 + KONKR (x1.0 and x1.3).
- Build: latest dev commit.
- Why not emulator: owner's checklist is about real D-pad / focus timing on the handhelds.
- Tests: every action incl. MORE reachable by D-pad; MORE once, last, no dots; More open / close restores focus; rapid A/B
  reliable; nothing clipped at x1.0 / x1.3; menu never changes size while navigating. (Visual design APPROVED by the owner 15 Nov.)

### HW-3 Flip 2 charge-loop fix (674i) - BLOCKED (owner)
- Device(s): Flip 2.
- Why not emulator: OEM charging switch (`input_suspend`) behaviour.
- Blocker: production Voltcap's own 80 % limit is on and toggles `input_suspend`; the QA app would fight it. A clean test needs
  production's limit off, or a production update - both the owner's decision. Nothing changed on the device.

### HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px - OPEN
- Device(s): KONKR. Build: latest. Tests: popclip gate CUBE at x1.3 + onepress.

### HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) - OPEN (watch)
- Device(s): KONKR + Flip 2. 674k gates (f674k, k674m) had no rapid FAIL; keep watching in HW-1 runs before closing.

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

## Next hardware session (batched)
Owner 16 Nov: the Flip 2 is unavailable. Split so nothing waits on it:
- **Session A - KONKR (when the PC is on):** full popup gates x1.0 + x1.3 on the newest build (2 runs, ~50 min each),
  Record crate checklist (HW-2), Cube first A (HW-4), Spot START re-measure (HW-6), rapid A/B watch (HW-5). Tell Claude
  "devices ready".
- **Session B - Flip 2 (when it is back):** its popup gates x1.0 + x1.3 (HW-1's Flip 2 half, HW-6's Flip 2 x1.0), then the
  charging test (HW-3, needs production's limit off for one supervised test).
v0.6.73 needs Session A plus Session B's popup gates, or the owner's decision to ship on KONKR evidence plus the 15 Nov Flip 2
gates (52/53 at x1.0 and x1.3).

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

### HW-3 Flip 2 charge-loop fix (674i) - BLOCKED - DEVICE UNAVAILABLE
- Device(s): Flip 2.
- Why not emulator: OEM charging switch (`input_suspend`) behaviour.
- Status (owner 16 Nov): the Flip 2 is unavailable - BLOCKED - DEVICE UNAVAILABLE, not failed (the test never ran).
  Postponed to v0.6.74 or later. The fix is kept on branch `claude/charge-loop-674i` and in
  `docs/claude/patches/charge-loop-674i.patch`; it was taken off the dev line in 675i so v0.6.73 cannot contain it.
- When the Flip 2 is back: re-apply the patch (`git apply docs/claude/patches/charge-loop-674i.patch`), QA build, then one
  supervised test with production Voltcap's own 80 % limit off (owner's step) - the QA app would otherwise fight it over
  `input_suspend`.

### HW-4 Cube first A on KONKR x1.3; L2 / R2 fact 0 px - OPEN
- Device(s): KONKR. Build: latest. Tests: popclip gate CUBE at x1.3 + onepress.

### HW-5 Rapid A/B leaves a window open once (Cards, Crate, TV) - OPEN (watch)
- Device(s): KONKR + Flip 2. 674k gates (f674k, k674m) had no rapid FAIL; keep watching in HW-1 runs before closing.

### HW-6 Spot START glyph re-measure with the key box (675e, D-674o-1) - OPEN
- Device(s): KONKR (x1.3, where k674n read dy +3 px) + Flip 2 x1.0. Build: 675e or later (QA app).
- Why not emulator: the reading came from the KONKR's real panel and backdrop at x1.3; emulator evidence never closes it.
- Tests: popclip / navclip on SPOTLIGHT home with QA traces; glyphcheck on the run folder - START / SELECT / A / X / Y must read
  source "InkGlyph+key" and PASS (limits unchanged, max(1.5 px, 5 %)); look at the crops of anything over 4 %.

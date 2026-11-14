# 529r / 529s QA evidence — owner P0: the first achievement is whole at ×1.3

Real handheld: KONKR GT78 (960×640, 240 dpi, Android 12), isolated QA app `com.shelf.launcher.qa`, synthetic QA fixtures
(names / covers / metadata, synthetic achievements "QA achievement N of game 15"), launches blocked, preview on (nothing
saved); the owner's QA snapshot put back after every run (`diff=0`). Full 960×640 viewports, status bar masked (top 36 px);
`_zoom` = 2× crop of the window. ×1.3 = `font=1.3,density=1.15`; ×1.0 = no override.

| Files | Commit | Build (APK sha256 prefix) | Run |
|---|---|---|---|
| `x13_cover_*`, `x13_shelf_*`, `x13_stacks_*` | `1450b569` | `22f0c95845f0` | sp529s13, 14 Nov 14:26 |
| `x13_storage_*`, `x13_tiles_*`, `x13_twin_desk_*`, `x13_consoles_*`, `x10_*` | `c8c3c18f` | `8745509f8703` | sp529t13 14:39, sp529t10 14:45 |

## Results (kept separate)

**Source tests (JVM, Robolectric):** `AchFirstRowRoboTest` - every layout's Achievements page in the app's theme at
640×427 dp, ×1.0 and ×1.3 via the device's own QA scale hook, 1 / 12 / 40 achievements: the first row's shown height must
equal its full height. On the code before 529r (first run, still without the app theme) 27 cases failed at ×1.3 (Coverflow
75 of 126 px, Storage 0 px, …); with the app theme and only the line-height change, 64 failed (shared pages too).  At `1450b569`: 85 pass, 2 skipped (AIRY, CASEFLOW: the test did not reach an Achievements page -
NOT TESTED), CROSS excluded (its own column). Full suite green.

**Physical device, visual (delegated review only - no owner approval):** at ×1.3 the first achievement row is whole when
the page opens on Coverflow details, Game shelf, Case stacks, Console stack, Storage boxes, Tile home and Twin desk; the
row's touch target was tapped in every run (`tap '…achievement 1…'`). ×1.0: two to three whole rows (Coverflow, Shelf,
Storage). The scroll cue is a slim bar on the list's right edge (529s); the 529r bottom chevron lay over Console stack's
last whole row and was replaced. No clipped first row, no overlap seen in these states.

**Not covered here:** NIGHT, BLADES and the shared-page layouts on hardware (JVM only); AIRY / CASEFLOW (not reached by
the JVM test); Flip 2 (USB dialog on screen); RP Classic.

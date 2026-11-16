# VoltCap handoff (read first, update last)

Updated: 2026-11-16 ~05:45 UTC, update 675g (live session: owner summary + release tracker; no app code). Previous: 675f, 675e (cloud run 03:17).
Dev branch `claude/qa-0672-repair`; production `main` = v0.6.72 (5b01ef39). Nothing released after v0.6.72; no production app
touched; KONKR production not upgraded. How to work in the cloud: `docs/claude/CLOUD_DEVELOPMENT.md`.

## State
- Cloud migration (owner request 15 Nov): repo attached to cloud sessions (clone + push verified); Android SDK setup script
  `tools/cloud/setup_android.sh`; `VOLTCAP_CLOUD=1` uses the CI Discord stub. Cloud scheduled task replaces the PC's hourly task
  once a cloud run is verified (see AUTORUN_LOG). Hardware-only work: `docs/claude/HARDWARE_QA_QUEUE.md`.
- Emulator QA (`emulator-qa-trial.yml`): every trial since 14 Nov crashed the emulator ~90 s in. 674o: x86_64 QA build, AVD at
  the profile's own size, renderer matrix (swiftshader_indirect / swangle_indirect / guest), verdict step that fails unless PASS.
  First 674o run: see AUTORUN_LOG / Actions.
- Handheld gates (674n build, c2cdfc81): Flip 2 x1.3 f674k 52/53 (674k build), Flip 2 x1.0 f674o 52/53 (Side status line
  51 px < 54 -> fixed 674o), KONKR x1.0 k674m 51/53 (674k build; both fixed since), KONKR x1.3 k674n 51/53 (Library R2 -> fixed
  674o; Spot START = checker reads a backdrop shape as the pill edge, ink measured centred by eye -> D-674o-1 below).
- CI: verify-update-310 green on 7358016a, c30d6f5a, cf629e2e, 7dbcab0c (675a), 750b669c (675b), 2fa6bc39, ddca9b66 (675c),
  e1c1ad88 (675d). 675e (Kotlin: QaClip glyph key + QaGlyphKey675eTest) was NOT compiled in the cloud (Maven 429) - CI is its
  first compile: if red, fixing it is item 0.

## Next (in order; skip what needs hardware - queue it instead)
Every run also: append ONE line to docs/claude/run_reports.jsonl (plain English for the owner, see CLOUD_DEVELOPMENT.md), keep
releases.json / owner_actions.json / progress.json true to the evidence, then `python3 tools/cloud/dashboard.py`.
When a progress.json count changes, change its source text with it.
0. CI memory (G11, 675a): green on 7dbcab0c (both runners, heavy task ran, lowest free memory 6.4 GB) - 1 green push. Keep
   checking "Verify Android update 310" on each push; G11 DONE only after several green pushes in a row. If red, first item
   again (add the growing class to heavyRoboTests in app/build.gradle.kts).
   Side finding: lazily made global Compose states crash a fresh JVM's first frame - look for other objects with lazily made
   states read by Case3D / warm-ahead threads (LookStyles fixed via Features.warmUiStores).
1. Emulator trial: start one on the 675d (or later) commit with "[emulator-qa]" (allowed from 04:31 UTC 16 Nov: last one 01:31)
   and read it (EMULATOR evidence). Run 43786748164 on ddca9b66 (675c): first A opened the window every time (no probeA/B).
   FAILs left: e529a SHELF / e529b COVERDETAILS "no Details action" + More step missing (+ e529a "extra: A opened nothing"),
   with the controls IN the pulled dump - the device's grep missed them on the two dumps that also held "Full story on Details
   ›" (10-13 KB single line). 675d: popclip.sh reads every dump one node per line (jl) and dw waits for a whole dump ("dump
   unreadable" logged). If the trial still shows "no Details action" with the control in the dump: add a probe that copies
   `jl <dump> | grep -c control` into the log to see what the device reads. Also open: e529b COVERDETAILS "rapid A B A B left
   a window open" and e529c's precondition (qa_windows=2 after e529b) - is a window really left (rapid shot / after dump) or
   is it the same read problem? shelfpages529.sh still greps dumps directly - give it jl too if it shows the same symptom.
2. D-674o-1 glyphcheck: DONE in code 675e (GlyphPill glyphs measured in their dumped key box); HW-6 re-measures on the
   handhelds. Other pills drawn by hand round an InkGlyph (Library, Pocket, Memory card, Cube...) can get the same
   QaClip.GlyphKey mark if a run shows the same walk symptom there.
3. Native popup pages for the remaining layouts; physical pickup / return animations (Disc Station etc.); D323 / D324 in-place
   Inspect; D322 pickup latency; promote warn-uniform to FAIL; Cube first-A / L2-R2 fact (HW-4).
4. Requirements matrix reconciliation (docs/REQUIREMENTS_MATRIX.md) and the full backlog (docs/MASTER_BACKLOG.md).
5. Later: +N category counters.

## Needs the owner
- (done 675f: the task prompt now says -Xmx4g / -Xmx1500m and reads + regenerates docs/claude/STATUS_DASHBOARD.md every run.)
- Retire the PC hourly task "VoltCap hourly backlog run" once the cloud task's first run is verified (Claude does it then).
- Flip 2 charge-loop fix test (HW-3): production limit off, or a production update - owner's call.
- Local-only branches `review/ai-setup-0672` (2 commits) and `feature/dual-welcome` (7 commits) are on the PC only (bundle in
  `Claude outputs\cloudmig\local_branches.bundle`); they change CI workflows, so they were not pushed. Push / keep: owner's call.
- v0.6.73, KONKR production upgrade, merges to main: separate authorization.

# VoltCap handoff (read first, update last)

Updated: 2026-11-15 ~22:30 Berlin, update 674o (cloud migration session).
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
- CI: verify-update-310 green on 7358016a, c30d6f5a, cf629e2e.

## Next (in order; skip what needs hardware - queue it instead)
1. Emulator trial: read the 674o matrix run; keep the renderer that stays up; then widen (more layouts per job, x1.0 + x1.3,
   Flip 2-like 1920x1080 @ 360 and RP Classic-like profiles, split across bounded jobs; artifacts; failures -> backlog).
2. D-674o-1 glyphcheck: Spot START pill over a backdrop shape of nearly the pill's colour - the walk ran past the pill's edge
   (dy +3 px). Make the pill's edge measurable (e.g. GlyphPill's own known size from the dump) - never loosen the threshold.
3. Native popup pages for the remaining layouts; physical pickup / return animations (Disc Station etc.); D323 / D324 in-place
   Inspect; D322 pickup latency; promote warn-uniform to FAIL; Cube first-A / L2-R2 fact (HW-4).
4. Requirements matrix reconciliation (docs/REQUIREMENTS_MATRIX.md) and the full backlog (docs/MASTER_BACKLOG.md).
5. Later: +N category counters.

## Needs the owner
- Retire the PC hourly task "VoltCap hourly backlog run" once the cloud task's first run is verified (Claude does it then).
- Flip 2 charge-loop fix test (HW-3): production limit off, or a production update - owner's call.
- Local-only branches `review/ai-setup-0672` (2 commits) and `feature/dual-welcome` (7 commits) are on the PC only (bundle in
  `Claude outputs\cloudmig\local_branches.bundle`); they change CI workflows, so they were not pushed. Push / keep: owner's call.
- v0.6.73, KONKR production upgrade, merges to main: separate authorization.

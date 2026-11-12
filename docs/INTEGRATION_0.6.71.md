# v0.6.71 release candidate: status and recommendation (12 Nov 2026; not merged, not tagged, not published)

## Candidate
- **`integration/0.6.71` @ f1a4e18** (local). It merges `feature/popup-native` @ 8137aca with `origin/main` 65f7cca. Both parents are kept; nothing is rewritten.
- **Ancestry:**
  - the published tag `v0.6.70` (GitHub: cc646b4) is on `main`;
  - `git diff cc646b4 6c6c98c -- app/` and `git diff 65f7cca cc646b4 -- app/` are both empty, so the branch's own v0.6.70 base is the published source;
  - the merge uses that base, so only main's release record and docs come in.
- `app/` of the candidate is identical to `feature/popup-native` @ 8137aca (`git diff -- app/` empty), so every app build and test of that commit is a build of the candidate's app code.
- **Excluded:**
  - Connected Ignition: no commit on the candidate;
  - the 3D Shelf: the code ships with `Shelf3D.enabled = false`, no setting, and its hooks only work in the QA app or debuggable builds.
- **Included:**
  - native popups (Storage boxes, Night menu, Tile home, Blades);
  - popup infrastructure, focus, L1 / R1 and text-fit fixes;
  - popup-first defaults for Card binder and Pocket Quest (approved). The keys are the same, and only the unset default changed (v0.6.70: `true` → now `false`), so a saved explicit choice is read back unchanged.

## Checks done
| Check | Result |
|---|---|
| Unit tests (`testDebugUnitTest`) | PASS on exp 3567df4 (its app = popup-native app + the 3D switch on). Popup-native itself: PASS at 15af3d5; 8137aca's app changes are 3D code behind the off switch + QA test-scale + Blades fade, all compiled in the s3d build |
| QA APK build, isolation, secrets | PASS (verifyQaIsolation, verifyNoEmbeddedSecrets) |
| Settings / data across the update | No preference key renamed, removed or added (Features.kt keys diffed against 6c6c98c); no manifest, resource, keep-rule, backup or data-layer change; build.gradle.kts changes are test-only |
| Native popups, Flip 2, R26 gate | ×1.0: Storage, Night, Tiles 8/8 PASS (exp daef44e); Blades 8/8 PASS after the fix (exp eb06ea7). ×1.3 (QA text scale now reaching the popup window): Storage, Night, Tiles 8/8 PASS (exp 2ac8326, strict ledger grade); Blades 8/8 PASS (exp eb06ea7). L1/R1, fast A B A B: no launches (popvid, 4 layouts) |
| Build ledger | `tools/qa/builds.json`: exp daef44e, 2ac8326, eb06ea7 with APK sha256 |
| GitHub | `feature/popup-native`, `exp/shelf-3d-flip`, `feature/connected-ignition` pushed; remote tips matched; main / tags untouched |

## Still blocking a safe merge and release
1. **Build the exact candidate** (`integration/0.6.71`) and register its APK. The PC tool has a new `int` worktree for it, active after your next desktop app restart. Then: `assembleQa` / `assembleDebug` + `testDebugUnitTest` on `int`, plus a short re-run of the popup gate on that APK (with the ledger entry, so it grades strictly).
2. **lintRelease against the correct baseline:**
   - run on popup-native 8137aca (= candidate app); result in the sprint report.
   - The v0.6.70 baseline must be measured on the published code. There is no worktree for main; the `ci` branch adds code, so it is not a clean baseline.
3. **Update compatibility on a device:** install the candidate's QA APK over a QA app holding v0.6.70-era data (settings, profiles, categories) and compare. Not done; the production app is never used for this.
4. **Remaining hardware-pending layouts:** TILES, NIGHT, STORAGE, BLADES are now done on the Flip 2. Still pending: GRID, TV, SHELF (flat path), STACKS, CARDS, POCKET. CARDS and POCKET carry the popup-first change, so they are release-relevant.
5. **Real artwork:** the QA library on the Flip 2 has fixture art only. Final visual acceptance of the popups with real covers is yours (owner review).
6. **Night ×1.3:** the Saves key scrolls below the info panel (reachable, gate PASS). Your visual call.

## Tracked separately, not in v0.6.71
Original Shelf custom-spine flicker; cold / delayed art flashes; the shared Shelf slot jump; 3D Shelf extraction quality (pending your visual approval); Connected Ignition hardware QA. All are in `docs/MASTER_BACKLOG.md` §3.

## Recommendation
Once items 1–4 are green: fast-forward `main` to the candidate. Then, on your explicit go, bump to 0.6.71 / 78 with the release tool and publish. Until then nothing touches main, tags or releases.

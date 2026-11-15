# VoltCap cloud development (owner request 15 Nov 2026)

The cloud (Claude Code cloud sessions + a cloud scheduled task) is VoltCap's primary development environment. The owner's
Windows PC is used for physical handheld QA and occasional local work only. Nothing here needs the PC to be on.

## Where work happens
| What | Where |
|---|---|
| Development branch | `claude/qa-0672-repair` (GitHub `dzepdzep/voltcap`). Production baseline: `main` = v0.6.72 (5b01ef39). |
| Pushes | fast-forward only to `claude/qa-0672-repair`. Never `main`, never force, never tags, never a release. |
| Merges to main | only through a PR the owner merges himself (branch protection + CI stay as they are). |
| Code edits | `app/src/...` directly in the cloud clone (the PC's `src_copy/` robocopy route is for the PC only). |
| Handoff | `docs/claude/HANDOFF.md` (current state, next steps) - read it first, update it last. |
| Requirements | `docs/REQUIREMENTS_MATRIX.md` (implemented / tested / approved / released / blocked). Never mark done on code alone. |
| Backlog | `docs/MASTER_BACKLOG.md`; hardware-only work in `docs/claude/HARDWARE_QA_QUEUE.md`. |
| Change log | `SUMMARY.md` (newest entry on top). |
| Run log | `docs/claude/AUTORUN_LOG.md` (one short entry per scheduled run). |

## Toolchain in a cloud session
```
bash tools/cloud/setup_android.sh          # Android SDK (platform 36, build-tools 36, NDK 28, CMake) ~1 min, idempotent
export ANDROID_HOME=$HOME/android-sdk VOLTCAP_CLOUD=1
G="--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx5g -Pkotlin.compiler.execution.strategy=in-process -PvoltcapCiWithoutDiscord=true"
./gradlew $G :app:compileDebugUnitTestKotlin                      # app + tests compile: ~7 min cold on 2 cores (verified 15 Nov)
./gradlew $G :app:testDebugUnitTest --tests 'com.shelf.launcher.ui.Batch674oTest'   # targeted tests: seconds once compiled
./gradlew --no-daemon -PvoltcapCiWithoutDiscord=true :app:assembleDebug     # isolated QA app, Discord stub
python3 tools/qa/tools/static_checks.py app/src/main/java/com/shelf/launcher
python3 tools/qa/selftest/test_qa.py       # after touching any QA tool
```
- `VOLTCAP_CLOUD=1` + `-PvoltcapCiWithoutDiscord=true` = the same Discord stub CI uses (the proprietary Discord `.aar` is a
  local-only file and is never uploaded). The stub is refused for any release task.
- No signing keys, `keystore.properties`, `screenscraper.properties`, `igdb.properties` or `voltcap_api.properties` exist in the
  cloud and none are needed for debug / QA builds and tests. Never add them.
- Maven Central can answer 429 through the cloud proxy: rerun with `--max-workers=2`; one retry, not a loop.
- Memory: the container has ~8 GB. A 3 GB heap or a separate Kotlin daemon runs out ("Not enough memory to run compilation");
  use the flags above. The full unit-test suite (1,900+ Robolectric tests) belongs to CI, not the cloud container.
- No KVM in cloud sessions: no Android emulator there. Emulator QA runs in GitHub Actions (below).

## CI
- `verify-update-310.yml` - unit tests + debug build on every push (mandatory gate; keep it green).
- `emulator-qa-trial.yml` - emulator QA of the isolated QA app; runs on `workflow_dispatch` or a push whose head commit message
  contains `[emulator-qa]`. Its last step fails unless every graded check is PASS. Emulator evidence is EMULATOR-only: it never
  counts as handheld verification.

## Scheduled cloud runs
One cloud scheduled task runs the backlog loop (see the task's prompt). Each run:
1. clone / fetch `claude/qa-0672-repair`; read `CLAUDE.md`, `docs/claude/HANDOFF.md`, the top of `SUMMARY.md`;
2. take the lock (`docs/claude/AUTORUN_LOCK.md`, below) or stop;
3. pick the next safe item that needs no hardware (`docs/MASTER_BACKLOG.md`, matrix rows marked OPEN / VERIFY);
4. implement one coherent batch + its test; run the tests / checks it touches;
5. commit, push fast-forward; check CI on the pushed commit;
6. update matrix / backlog / handoff / SUMMARY / AUTORUN_LOG; add a hardware request when a change needs a handheld;
7. release the lock (commit + push).

## Lock (one writer at a time)
`docs/claude/AUTORUN_LOCK.md` on `claude/qa-0672-repair` holds `holder:` and `since:` (UTC). A session takes it by committing
its own holder line and pushing: git refuses a push that is not a fast-forward, so of two sessions only one can win - the loser
fetches, sees the other holder and stops without changing anything. A lock older than 3 hours is stale and may be taken (say so
in AUTORUN_LOG). A live session on the PC (hardware QA) takes the same lock. `holder: none` = free.

## Never (cloud or PC)
Modify the owner's production Voltcap (`com.shelf.launcher`), real game saves or emulator configurations; publish v0.6.73 or
any release; upgrade KONKR production; merge to main; push secrets; force-push; run two writers at once; count emulator
evidence as handheld evidence.

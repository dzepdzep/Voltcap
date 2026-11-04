# v0.6.68 staging — NOT part of 0.6.67 (nothing here is built into a release)

Kept outside `app/` so main (v0.6.67, tag at 0357637) and its release assets stay untouched. Started in update 474 (QA overhaul
phase 1). `staging.patch` = `git diff` of `app/` against main; CI (`staging-ci.yml` here — copy it to `.github/workflows/staging.yml`:
Claude's remote tools may not write `.github`) applies it on a clean
checkout of main, compiles, runs the JVM unit tests, the QA self-tests and the strict static checks, and prints exactly what it
tested: main's commit, the patch's SHA-256 and the source tree hash (`tools/qa/tools/srchash.py`).

## In the patch (update 474: QA-only app code, inert without the QA hooks file)
| file | what |
|---|---|
| `ui/QaClip.kt` | the geometry dump also exports, per text: lines, maxLines, overflow height / width, ellipsis, laid-out vs box height (`TextLayoutResult`); the app's focused game, the window's game, `GivesWay` mark; the last pictures (QaArt) |
| `ui/QaArt.kt` (new) | Coil `EventListener`: per picture: hashed token (never the address), asked / decoded size, FIT / FILL, source, load ms |
| `data/ArtStorage.kt` | the listener is installed only while the hooks file exists |
| `ui/QaFixture.kt` (new) | display-only QA content: long / short names, no metadata, no art, drawn wide / square / portrait / ultrawide / low-res art (cache only). Nothing written: ROMs, artwork, categories, metadata and settings untouched |
| `ui/TestHooks.kt` | `--es voltcap_test_fixture "names=long,art=wide,meta=none" \| off` (hooks file only) |
| `ui/LauncherScreen.kt`, `MainActivity.kt` | the fixture dressing applied after the demo dressing (main + second screen) |
| test `ui/QaFixture474Test.kt` (new) | off by default = the real state itself; keys / consoles / order kept; whole long names |

## Building a QA APK from it
main + patch, then `app/build.gradle.kts` versionCode **73**, versionName `0.6.68-qa` (so the 0.6.66 RC, versionCode 73, can go
back on the handhelds without a downgrade). Record the APK's SHA-256, the patch SHA-256 and the tree hash in `tools/qa/builds.json`
before testing it. Build `qa-0.6.68-474a`: APK ccf46242…, patch a2705dbc…, tree d5dfc491…

## On the PC working tree
The three new files are listed in `.gitignore` so `git_commit_push` (git add -A on main) never commits staging code; remove those
lines when the patch is applied to main.

# v0.6.67 release gate — update 472 (verification only, 4 Nov 2026)

No code changed. Nothing published. v0.6.66 (main, c9b4248) unchanged and reinstalled on both handhelds.

## 1. Tests on the exact final source
Final staging source = cloud `dev` 6c717b7 (all 471 fixes, incl. D243 row styles and D244 Cover details).
Put on the PC as the working tree (141 changed app files); checked file by file against the source (562 byte-identical,
124 unchanged files differ only in CRLF line ends, 0 mismatches).
| check | result |
|---|---|
| testDebugUnitTest (PC, fresh run 11:22) | 759 tests, 0 failures, 0 skipped (Fixtures471Test 6/6) |
| QA self-tests `tools/qa/selftest/test_qa.py` | 98 OK, exit 0 |
| static checks `--strict --min-files 100` | PASS, exit 0 (685 Kotlin files) |
| coverage matrix `coverage.py check` | PASS, exit 0 |
| assembleRelease / assembleCompat at 0.6.67 / 74 | SUCCESS / SUCCESS (only the known Features.kt 94/101 Kotlin 2.3 warnings) |
CI: `.github/workflows/qa.yml` runs exactly the three QA steps above on pushes to `main`; `main` is the 0.6.66 source, so
CI cannot see the staging app code. The same three steps were run here on the staging tree (exit 0 each). GitHub run
status could not be read from this session (no repo API access).

## 2. Release APK identity (built, NOT published)
| file | versionName / Code | SHA-256 |
|---|---|---|
| Voltcap-0.6.67-release.apk (54 017 977 B) | 0.6.67 / 74 | c86a9b0d264232836125dfe923e7bf5671a7030f7242693610708a3b41fc63bb |
| Voltcap-0.6.67-compat.apk (54 017 885 B) | 0.6.67 / 74, no notification listener | 224f9f02e788d11e10863b2dececc266695a7ad7c5c1837b3266de32f0e0c447 |
Package com.shelf.launcher, signer SHA-256 88ce4b67ae3825a04585114aec25cfaf5023e0b55cfdb61f375d30945f344197 (same key as
0.6.65 / 0.6.66). Release APK content hash (all entries except META-INF): 8a34216a8319631d53536dcbdadca0767eaa48425cc59bcaab97c6e4d36ba654.
For comparison: the restored RC Voltcap-0.6.66-release.apk (0.6.66 / 73) = f79aa2a7c5afbaa5a494a3ddc0bebbafa038b4d95ec7332c8e91ef4dee7cc315.

**How staging becomes the release (on approval only):** main holds 0.6.66 + `next/0.6.67/` (patch, src_copy, project_copy).
1. Delete the placeholder files listed in `next/0.6.67/README.md` and `git apply --ignore-whitespace next/0.6.67/profile_picker.patch`
   (or copy next/0.6.67/src_copy + project_copy and sync); `git_status` must show exactly the 141 app files; commit.
2. `publish_release 0.6.67` (tools/release/release.ps1): raises versionCode 73 → 74, sets versionName 0.6.67 in
   app/build.gradle.kts (and project_copy), builds release + compat, commits, pushes, tags v0.6.67, creates the GitHub release.
This gate built that exact tree with exactly those two version values. After publishing, compare the attached APKs' SHA-256
with the table above; if Gradle rebuilds instead of reusing outputs the bytes can differ, then compare the content hash.
The 0.6.67 / 74 APK was NOT installed on any device (versionCode 74 cannot be downgraded back to 73 without an uninstall).

## 3. KONKR Channel · Banner
Re-run on the final staging build (k472c, KONKR, Pokémon Stadium 2): **PASS** in all six categories (open, geometry,
visual, focus, buttons, settle), checked by eye too. The 471 NOT TESTED came from that run's home / closed steps having no
buttons for the pixel check; this run graded them. Odin o472c PASS.

## 4. Smoke test (staging build = same source at 0.6.66 / 73, from Gradle cache = build G)
USB only (BW0306N230000845 KONKR GT78, b92036fb Odin 2 Portal), launches blocked.
| run | layouts × styles | result |
|---|---|---|
| k472c / o472c | Channel × Banner, Own | 2 / 2 PASS each |
| k472s / o472s | Cover details, Cardboard box, Case flow, Rental shop, Card binder × Own, Row opens | 9 PASS + 1 n/a each (Cover details · Row opens not offered) |
Row opens on Rental shop / Card binder opened as Row opens (D243 fix). Settings recorded first and read back equal
afterwards (KONKR font 1.0, timeout 60 000, brightness 128 manual, rotation auto on / 0, 240 dpi, animation 1.0 / 1.0 / unset;
Odin font 1.0, timeout 1 800 000, brightness 179 manual, rotation auto off / 1, 369 dpi, animation 1.0 / 1.0 / 1.0); Voltcap
view restored by snapshot (KONKR Case flow · Recently played · Pokémon Silver, Odin Art library · Xbox 360 · WWE). Test files
and scripts removed; RC 0.6.66 / 73 reinstalled on both. No other device touched.

## 5. Follow-ups
Visual observations and carried requests: `tools/qa/NEXT_OVERHAUL.md` (not release blockers).

## Verdict
**PASS** — v0.6.67 is ready to publish on your approval. Remaining limitations: Odin long names NOT TESTED (one game);
the items in NEXT_OVERHAUL.md; the published APK's bytes are produced by release.ps1 at publish time (compare hashes).

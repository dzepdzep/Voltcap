# VoltCap v0.6.71 — release record (13 Nov 2026)

Published under the owner's merge / release authorization (Final Autonomy Update, master sprint Phase 9).

- GitHub release: https://github.com/dzepdzep/Voltcap/releases/tag/v0.6.71. A normal release marked **Latest** (what the in-app updater offers), published 05:39:35 UTC.
- Tag `v0.6.71` → `827632f`.
- Commits on main:
  - `65f7cca` (v0.6.70 record).
  - `44ea53f`: `feature/popup-native` 002e6c9e merged (Sprints 524–528), version 0.6.71 / 78. Its tree equals the local `release/0.6.71` cf03a807.
  - `827632f`: version-identity test made bump-proof, plus the fixed CI workflow waiting in `next/ci`.
  - release.ps1 kept the staged version and had nothing more to commit.
- Not included: Connected Ignition (`feature/connected-ignition`), 3D Shelf (off, QA hooks only), FW1.

## Assets (GitHub digest = local build)

| Asset | Bytes | SHA-256 |
|---|---|---|
| Voltcap-0.6.71.apk | 54,490,858 | 4ec2ed4065ff2d3009fa977a63e6a161e0fbf832145dac8162fb0e801dea4962 |
| Voltcap-0.6.71-compat.apk | 54,490,766 | 63b8dc5fb4e9092684fe8800ea39c3147a1061fe2660f0154cb2ed8f95362606 |
| voltcap-profile-api28.dm | 19.6 KB | 96a27d5cba45010dd9d52dde84ee9ff52eb41fff133103065d22d14d11784616 |
| voltcap-profile-api31.dm | 19.3 KB | 4f436d56c79593d246e3f27486a7fd9027e9a125b098777a2c38eb29bdb5be13 |

## Release gates

| Gate | Result |
|---|---|
| Unit tests, final source | First run on 44ea53f: 2,489 tests, 1 failure, `Batch512Test.releaseIdentityIs0670Code77` (the hard-coded 0.6.70 / 77 failed on the bump, as it did on 0.6.70). Fixed to check that name and code move together (0.6.N ↔ N+7, ≥ 77). The rerun on the 827632f working tree passed (BUILD SUCCESSFUL). |
| CI for the exact commit | **QA tooling checks (`qa.yml`): green** on 44ea53f (#179) and 827632f (#180). The three earlier main commits had failed it. **Staging build (`staging.yml`): red, pre-existing.** Since update 499 it applies `next/0.6.68/staging.patch`, which is a comment stub, so `git apply` exits 128 at step 5 and the job never compiles. It failed the same way on 0.6.68, 0.6.69 and 0.6.70. The fixed workflow (builds and unit-tests main) is in `next/ci/staging.yml`. The remote tools cannot write `.github`, so the owner has to copy it (`next/ci/README.md`). The local compile and unit tests above cover what that job would have run. |
| Lint (lintRelease) | 49 errors = baseline (none new), 968 warnings (+2: `UseKtx` "use Bitmap.get" in VideoPreview.kt:218 / 222), 109 hints. |
| Security | Gradle `verifyNoEmbeddedSecrets`: 4 values, clean on both APKs. `apk_secret_scan`: no credential fields. Token / private-key pattern scan of both unpacked APKs: 0 hits. |
| APK identity | Both: com.shelf.launcher, 0.6.71 (78), signer SHA-256 88ce4b67…f344197 (same key as 0.6.70), debuggable=False. Full APK declares the notification listener; compat does not. |
| Install compatibility / data | RP Flip 2 production upgraded in place (`install_production`, pinned compat APK): 77 → 78. firstInstallTime unchanged (2026-10-19), real Voltcap back on its home with the owner's library, crash buffer 0. The app shows its own "What's new in 0.6.71" note; it was left for the owner (no input sent to the production app). Settings migration v0.6.70 → this code was compared key by key on RC4 (733/734 Features keys identical; only Fill's new versioned key). |
| Diff vs previous release | v0.6.70..v0.6.71: 157 files (85 added, 72 modified, none deleted or renamed), +38,362 / −527 lines: popup-native Sprints 524–528, tests, QA tools, docs, the v0.6.70 record and production pins. |
| Hardware, core functionality | Same app code as the Sprint 528 QA builds (feature/popup-native 5aeb212f code, 002e6c9e docs), checked on the Flip 2 and KONKR (Sprint 528 report). The signed production APK was started on the Flip 2 after the upgrade (home, no crash). |
| Rollback backup | `Claude outputs\backups\voltcap_rel0671_incr_20261113.bundle` (sha256 3838a44f…662f; refs main 827632f, v0.6.71, feature/popup-native; prerequisites v0.6.70 / a1f762c on GitHub). Restore-tested in a fresh repository (v0.6.71 tree 34a451fa). Production rollback is roll-forward only (never a downgrade). |

## Devices

- RP Flip 2 (2037b27d): real Voltcap 0.6.71 (78, compat variant), upgraded 13 Nov 06:44 CET.
- KONKR (BW0306N230000845): **not upgraded.** It was asleep, and it is never woken. It still runs 0.6.69 (76).
- Production pins: `tools/adb_mcp/production_pins.json` now also allows the two 0.6.71 APK hashes.

## Not claimed

The owner's visual approval of the native popups (Storage, Night, Tiles, Blades), Night ×1.3 and the category-look fixes is still pending. This release does not claim it.

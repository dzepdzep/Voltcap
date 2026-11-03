# Update 464 — real cartridge scans: RC v0.6.66 vs v0.6.67 staging (owner: "don't sacrifice them to improve generated ones")

Device: KONKR GT78 (USB), same library, same session-only hooks on both builds (RC installed first, then staging).
Silver, Crystal, Gold = the games' OWN ScreenScraper cartridge photos (the real-media path).

## How
- **Inspect, the way you open it** (`tools/qa/device/real464.sh`): game window › Inspect › the case opens › the cartridge taken out
  (stick clicks), held at its resting pose. Same on both builds (the RC has no hook for a set angle on this path).
- **Home screen at set angles** (`tools/qa/device/home464.sh`): Pocket Quest, cartridge look (session-only preview), every solid
  cartridge held at 0 / 45 / 75 / 90 / 180° by `voltcap_test_pose` — the same hook on the RC and staging.
- Determinism checked: the RC run twice gives 0.000 % changed pixels (Inspect front and home at 45°).
- Pixel diff: share of pixels in the cartridge's box differing by more than 8 / 255 in any channel, and the largest difference.

## Results
| Game | View | changed px | max diff |
|---|---|---|---|
| Silver | Inspect, out of its case (front) | 0.0 % | 8 |
| Silver | Home, held at 0 deg | 0.093 % | 177 |
| Silver | Home, held at 45 deg | 0.094 % | 178 |
| Silver | Home, held at 75 deg | 0.093 % | 177 |
| Silver | Home, held at 90 deg | 8.663 % | 177 |
| Silver | Home, held at 180 deg | 0.093 % | 177 |
| Crystal | Inspect, out of its case (front) | 0.758 % | 15 |
| Crystal | Home, held at 0 deg | 0.093 % | 177 |
| Crystal | Home, held at 45 deg | 0.093 % | 177 |
| Crystal | Home, held at 75 deg | 0.093 % | 177 |
| Crystal | Home, held at 90 deg | 9.65 % | 177 |
| Crystal | Home, held at 180 deg | 0.093 % | 177 |
| Gold | Inspect, out of its case (front) | 0.006 % | 9 |
| Gold | Home, held at 0 deg | 0.093 % | 177 |
| Gold | Home, held at 45 deg | 0.093 % | 177 |
| Gold | Home, held at 75 deg | 0.093 % | 177 |
| Gold | Home, held at 90 deg | 8.663 % | 243 |
| Gold | Home, held at 180 deg | 0.093 % | 177 |

Reading the table:
- **Fronts (Inspect + home 0°): unchanged.** Silver 0.000 %, Gold 0.006 % in Inspect; Crystal 0.76 % = sub-pixel edge resampling only
  (median difference 1/255, 99th percentile 10/255 — an outline in the ×8 diff image, no colour / light / art change).
- **Home 0 / 45 / 75 / 180°: 0.093 % for every game and angle = a few pixels of the top bar at the crop's top-right edge** (the same
  spot in every shot, outside the cartridge; the cartridge itself is pixel-identical). Backs (180°) identical.
- **90°: different on purpose** — on the RC the real cartridge **vanishes** edge-on (the D175 bug, Thor); staging shows the solid side
  wall (update 458's D175 fix, kept as you asked). That is the only change to real cartridges in 0.6.67.
- Sheet: `Claude outputs/qa464/qa464_real_rc_vs_staging.png` (RC row above staging row per game).

## Guard tests (`RealScans464Test`, 5 tests, green)
1. The picture choice (`typedArtFor`, `realScanOf`, `drawnLabelScan`, PhysicalOverrides — your picks) never consults PhotoShells;
   the game's own photo stays the CARTRIDGE_DISC answer when nothing was picked.
2. The real-photo drawing (`RealMediaPicture`, `rememberPhotoSolid`, RealMedia.kt, LaunchThrow.kt) never consults PhotoShells.
3. PhotoShells is only used by the drawn-shell path: CartShells `frontFor` / `cachedFrontFor` / `photoFront` (+ its cache key),
   SolidPiece `rememberShellSolid` only, Inspect only for a `~sh:` shell pick; plus the setting, the view model's photo list and the QA hook.
4. A missing / failed template can't damage anything: `photo ?: drawn`, no template → no photo front and no photo sides, a failed
   photo is remembered as failed, loading is caught and off the screen's thread, the setting off = nothing photo-made.
5. Your pack / custom-model picks are drawn before the drawn shell; hacks' photos never make templates.

## Code-level check (RC → staging diff of the real path)
GameCard.kt (picture choice): unchanged. RealMediaPicture: one QA trace line (inert unless the test-hooks file is there).
rememberPhotoSolid / InspectSlab for a real photo: only the D175 edge-on change (458). PhysicalOverrides: hack label strip (461) and
your Labels folder (460) — labels only, never the cartridge photo.

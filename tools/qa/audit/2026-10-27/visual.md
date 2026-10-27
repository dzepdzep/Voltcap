# VoltCap visual-glitch audit (1–3 frame artifacts)

Paths are relative to `src_copy/`. Key mechanism used below: `produceState` keeps its state across key changes. Only the producer restarts, and it runs at the earliest on the next frame. A `LaunchedEffect(key) { anim.snapTo(start); anim.animateTo(end) }` draws the frame where the key changes with the old/end value, then snaps. Both are confirmed by the code, and the author's own comments in GenericDisc.kt:66 and AppIcons.kt:129 note them as well.

## Verified defects

### V1 – riseOnChange shows the new info fully for 1 frame, then blanks it and rises in
- `ui/Motion.kt:235-252`. Mechanism 5. `p` stays at 1 while the key changes in composition. The new game's header/info is drawn at full alpha. Then `LaunchedEffect` runs `snapTo(0)` (alpha 0, or 0.25 with RISE, plus a 14dp drop), then animates back.
- User sees: on every focus move, the game header/info flickers (visible, gone, then rises in). Affected: the Voltcap header (`LauncherScreen.kt:2477`), TileHome:411, TvRoomHome:204/234/268, StyleDashboard:275, StyleSideMenu:233, StyleConsoleHomes:279/438, CubeHub:486, StyleLiveTiles:157.
- CONFIRMED · **high**
- Fix: reset synchronously.
  ```kotlin
  val first = remember { arrayOf(true) }
  val p = remember(key) { Animatable(if (first[0] || MotionPrefs.info()==InfoAnim.NONE) 1f else 0f) }
  SideEffect { first[0] = false }
  LaunchedEffect(key) { p.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
  ```

### V2 – categorySlide draws the new category in place for 1+ frame before the slide starts
- `ui/HomeExtras.kt:64-88`. Same mechanism. `anim` is 0 when `tick` changes, so the new row is drawn at rest at full alpha. Then `snapTo(dir)` moves it 64dp aside at 25% alpha, waits 2 frames, and slides it in.
- User sees: on every L2/R2 (on by default, `Features.categorySlide=true`), the new row flashes in place, jumps aside, then slides back. Used in 28 places (GameDock, the HeroCarousel cover wheel, …).
- CONFIRMED · **high**
- Fix: copy what `StyleCrossMenu.kt:96-109` already does.
  ```kotlin
  var seen by remember { mutableIntStateOf(tick) }
  val pending = on && tick != seen && CategorySlide.pathOwners == 0
  // in graphicsLayer:
  val v = if (pending) CategorySlide.dir.toFloat() else anim.value
  // in the LaunchedEffect, after snapTo:
  seen = tick
  ```

### V3 – the same "final state, then snap to start" pattern in layout-specific animations
All CONFIRMED · **med** (each only in its own layout or mode). The fix for each is `remember(key) { Animatable(startValue) }` (starting at the end value on the very first composition), or a "pending" flag read in the draw.
- `ui/HeroCarousel.kt:344-349`: CoverWheel `change` on `focused` (FADE/FLIP/ZOOM motion). The new focused cover is drawn unflipped/unfaded for 1 frame, then snaps.
- `ui/StyleArcade.kt:607-611`: `step` on `vf`. The new machine is drawn already stepped forward, then jumps back and steps again.
- `ui/StyleTerminal.kt:559-566`: `typed` on `cmd`. The whole new command line is shown for 1 frame, then disappears and types out.
- `ui/StyleGreenCore.kt:413-420`: `enter` on `page`. The new page is drawn in place, then jumps right and slides in.
- `ui/StyleConsoleStack.kt:425-429`: `out` on `catKey`. The new category's games are drawn fully "out", then snap into the slot.
- `ui/StylePocketCamera.kt:301-306`: `shut` on game. The new picture is shown unshuttered for 1 frame before the shutter closes.
- `ui/StyleBubbles.kt:708-713`: LiveArea `slide` on game. Drawn in place, then jumps aside.
- `ui/GameWindowBanner.kt:359-367`: `tabIn` on `tab`. The new tab's content is drawn in place, then snaps out.

### V4 – rememberSoftBitmap / rememberSoftLoad return the previous model's bitmap for 1+ frame
- `ui/RealMedia.kt:248-283` and `:328-351`. Mechanism 1. `produceState(cached, model, trim)` returns the old `state` in the frame where `model` changes, even when the new bitmap is already in `cached`. When the new one is not cached, it goes old → null → new.
- Callers: Case3D (front, spine, back, inside items), CartShells, BoxSides, PhysicalMedia, DitherArt, CustomCarts, StyleArcade/DiscStation/StorageBoxes/FileSelect/CardBinder/AiryHome.
- User sees: a 3D case or cartridge wearing the previous game's cover, spine or label for a frame. This happens wherever the composable is reused for another game (see V5).
- CONFIRMED (code) · **med**. It becomes high together with V5.
- Fix:
  ```kotlin
  val st by produceState<Pair<Any?, Bitmap?>>(model to cached, model, trim) { …; value = model to bmp }
  return cached ?: st.second.takeIf { st.first == model }
  ```
  Do the same in rememberSoftLoad: `SoftLoad` carries its model, and `start` is returned while the model differs.

### V5 – the endless (looping) carousel and dock key their slots by position, so a category change reuses the old games' compositions
- `ui/Carousel.kt:1168`: `key(old, if (lLoop) i else g.stableKey)`.
- `ui/GameDock.kt:540`: `key(if (loop) "loop:$slot" else game.stableKey)`.
- `ui/GameDock.kt:454-458`: the fixed wide slot is an `AnimatedContent(targetState = focus index)`. The same index with a new list re-runs the same content with another game.
- The author hit this before (GenericDisc.kt:66: "rows keep their slots while the category changes").
- User sees: after L2/R2 on an endless row, every visible slot keeps the old game's remembered state:
  - V4 stale bitmaps (old cover in the new case/cartridge for a frame);
  - V6 stale sticker text;
  - per-item `animateFloatAsState` aspect and mediaRoom (Carousel.kt ~1459/1484) animating over 220–240 ms from the old game's shape to the new one's (visible resizing);
  - GameCard's `reveal` Animatable (GameCard.kt:319), which is not keyed.
- CONFIRMED · **high** on endless rows, none on non-looping rows.
- Fix:
  - Carousel: `key(old, if (lLoop) "$i|${g.stableKey}" else g.stableKey)`.
  - GameDock: `key(if (loop) "loop:$slot:${game.stableKey}" else game.stableKey)`.
  - Fixed slot: wrap the content's GameCard in `key(game.stableKey) { … }`, or pass `contentKey = { v -> games[...].stableKey }`.
  - Scrolling keeps the same keys, so there is no extra rebuild.

### V6 – CartShells sticker: text for 1 frame, then the front is built from the wrong cover
- `ui/CartShells.kt:1548-1551`: `nameLate = produceState(!waitingArt, art, bmpArt)`. When the art changes it still holds the old `true`. In the first frame where `waitingArt` becomes true, `titleNow = title`, so the name is painted on the label. The producer sets it to false one frame later.
- `:1560-1576`: with V4, the first frame after a game change sees `bmpArt` = the old game's bitmap. `identity` differs, so `CartShells.frontFor(...)` builds the new title on the old cover, synchronously on the main thread.
- This matches the owner's complaint "Switch cartridges first show text quickly".
- CONFIRMED (code; it needs V4/V5 reuse or a quick art swap) · **med**
- Fix: tag the state, `produceState(art to !waitingArt, art, bmpArt)`, and treat it as false while `.first != art`. Fixing V4 removes the wrong-cover frame.

### V7 – slowZoom snaps the OLD picture back to 1.0 the moment focus moves
- `ui/Motion.kt:181-200` sits on the container of the held layers:
  - `HeroCarousel.kt:170` (`HeldLayers(...Modifier.slowZoom(gameKey))`)
  - `StyleHeroTiles.kt:213`
  - `StyleDashboard.kt:266` (on `HeldImage`)
- On a key change the `LaunchedEffect` runs `z.snapTo(1f)` right away. HeldLayers still shows the previous game's picture until the new one has loaded.
- User sees (Background motion "Quick fade + slow zoom" or Ken Burns): on each move the current background visibly jumps from zoomed (up to 25%) back to 1.0, then the next picture fades in.
- CONFIRMED · **med**
- Fix: move the zoom into each held layer, keyed per layer, as HomeChrome's HeroLayers already does with `layer.zoom`. For example, slowZoom inside the HeldLayers content using that layer's own key. Or delay `snapTo(1f)` until the new layer reports ready.

### V8 – main background (HeroLayers) blanks when the focused game's backdrop URL changes in place
- `ui/HomeChrome.kt:1458` / `:1517`. The top layer re-reads `ArtPick.backdrop(...)` from live state ("newest picture uses the latest game object"). When the URL changes for the same game, the same AsyncImage gets a new request. That happens when the hero, screenshots (SCREENSHOT/SMART priority), an override or the anim flag arrive after focusing, and on `retry`. The request has no placeholder, and the layer is already fully shown with the layers under it removed. So the background goes empty (theme colour) until the new decode, then pops in with no fade.
- Related: `HeroReadyTimeoutMillis = 1200` (`:1244`, `:1313`) marks a still-loading layer ready. It fades in an empty layer and removes the picture under it, giving the same blank-then-pop on slow loads.
- CONFIRMED (code) · **med**
- Fix:
  - Include the URL in the layer identity: `LaunchedEffect(targetKey, topUrl)`, adding a new `HeroLayer` when the URL differs. This reuses the existing held-fade logic.
  - For the timeout, don't remove the layers underneath unless this layer actually succeeded. Or set `.placeholderMemoryCacheKey(previous layer's key)`.

### V9 – focused-game logo next to the name: 0-width while loading, then pops and pushes the name
- `ui/GameCard.kt:1003-1013` (LogoAndName), used by `GameDock.kt:959` FocusedLine and GameCard.kt:858. There is `AsyncImage(model = logoUrl)` with a fixed height and only `widthIn(max)`, no size and no placeholder. While loading, Coil's painter has no intrinsic size, so the image is measured at minWidth 0. With LEFT/RIGHT placement the name shifts sideways when the logo arrives. With INSTEAD the line is empty, then the logo pops in.
- Logos are on by default (`GameTitles.showLogos = true`), and the dock prefetches no logos.
- CONFIRMED (code); the Coil measuring detail is PLAUSIBLE-high · **med-high** (Voltcap row, every new game)
- Fix:
  - Reserve the slot: `Modifier.height(h).width(logoMaxWidth)` with `alignment = CenterStart`, or a fixed aspect box.
  - Or use `FadeInImage` inside a fixed-width box.
  - Prefetch the logos of focus±2 into memory with a fixed-size request.

### V10 – Blades system preview: the old console photo cross-fades in for the new system, then fades again
- `ui/StyleBlades.kt:743-750`. `photo` is `produceState(null, platform, photosOn)` and is not reset. `Crossfade(targetState = photo to platform)` first goes to (oldPhoto, newPlatform), then to (newPhoto, newPlatform).
- CONFIRMED · **med** (Blades layout, moving between systems)
- Fix:
  ```kotlin
  produceState<Pair<String?, String?>>(platform to null, platform, photosOn) { value = platform to url }
  val photo = st.second.takeIf { st.first == platform }
  ```
  `CategoryArt.kt:150` `rememberConsolePhoto` has the same untagged pattern (its synchronous `ConsoleArt.model` usually wins) · low.

### V11 – Second screen, Screenshots mode: previous game's screenshot shown for the new game
- `ui/SecondScreenHome.kt:709-731`. `shots` (`produceState(emptyList(), game key…)`) still holds the old game's files. `pool = shots + info.shots`, and `slide` resets to 0, so `picture` = the old game's first screenshot. `AnimatedContent` transitions to it, then again to the right one.
- CONFIRMED · **med** (second screen, SCREENSHOTS art type)
- Fix: tag `shots` with `game?.stableKey`, and use `emptyList()` while the tag differs.

### V12 – rememberDithered (Pocket Camera): stale frame, and it can cache the wrong game's picture
- `ui/DitherArt.kt:30-42`. `out` is untagged (1+ frame of the old game's dithered picture). Worse, with V4 the producer may run with `src` = the old game's bitmap under the new `key` and `DitherCache.cache.put(key, …)`. Whether it does depends on whether the dither finishes before the next recomposition cancels it. If it does, the new game keeps showing the previous game's picture until LRU eviction.
- Stale frame CONFIRMED; cache poisoning PLAUSIBLE (race) · **med**
- Fix:
  - Fix V4.
  - Tag `out` with `key`.
  - Only `put` when the bitmap came from this URL: compare against `Models3D.noteSource`, or pass the source URL out of rememberSoftBitmap.

### V13 – Details hero placeholder never hits
- `ui/GameDetailsOverlay.kt:1497`: `.placeholderMemoryCacheKey(url)`. The home background's key is `"$heroUrl|hero|WxH|…"` (HomeChrome) and HeroCarousel uses `"hero:$url:…"`. A bare `url` key matches neither.
- User sees: opening details, the hero area is empty, then the image cross-fades in (the comment promises the home copy).
- CONFIRMED · **low-med**
- Fix: pass the exact key the home background used. Expose a `backdropKey(game)` helper and use it here.

### V14 – Crossfade/AnimatedContent around a bare AsyncImage dips to dark while the next picture loads
- `GameDetailsOverlay.kt:1147` (screenshots every 6 s, 1200 ms), `CategoryArt.kt:125`, `SecondScreenHome.kt:775`, `TvRoomHome.kt:447`, `StylePocketQuest.kt:795`, `StyleNightMenu.kt:204`, `SaveVaultStyles.kt:759`.
- The old picture fades out while the new AsyncImage is still empty: the "black flash" that HeldLayers (Motion.kt:126) was written to avoid.
- CONFIRMED · **low** (partly a design choice)
- Fix: use `HeldImage(...)`.

### V15 – Other untagged produceState (old value held until the new load)
All CONFIRMED · **low**.
- `BoxSides.kt:238`: box sides of the previous model while the new ones are made.
- `HeroCarousel.kt:752/807`: History page shows the previous game's sessions.
- `SceneArt.kt:161/179`: the scene's cartridge/disc and music art of the previous game until the new one decodes. This is a hold, then a hard swap.
- `ScenePainting.kt:71`: still one frame of the old painting when `sceneFade` is off; the comment's claim is wrong.
- `Case3D.kt:369`: `itemFace`, inspect only.
- Fix: tag each with its key, as AppIcons does.

### V16 – Models3D / Models3DLife stale piece and reflection
- `Models3D.kt:756` (`bmp` keeps the old angle/cover until `compose` finishes, and ignores an already-cached `first`).
- `Models3DLife.kt:185/195` (`made` / reflection untagged → reflection of the previous picture under the new one).
- Only reachable from the Save Vault screens: `Model3DArt` has no `life=true` caller, and the carousel model is `null`.
- CONFIRMED · **low**
- Fix: `return first ?: bmp.takeIf { tagMatches }`, and tag `made` with `src`.

### V17 – HeroLayers starts empty when BackdropArt enters composition
- `HomeChrome.kt:1259`. Layers are added only in `LaunchedEffect`, at alpha 0. HeldLayers instead seeds a shown layer. On entering the backdrop branch (style switch, coming back to the screen), there is 1+ frame with no background, then a 170 ms fade, even when the hero is in memory.
- CONFIRMED · **low**
- Fix: seed `layers` with a shown layer when the hero's memory key is already cached, as HeroCarouselBackdrop does.

### V18 – WallpaperAnimatedImage
- `AnimatedWallpaper.kt:79-95`. The first frames go through `StillWallpaper` (an AsyncImage with no size, so it waits for layout and is blank), then switch to an `ImageView`. `drawable` is not reset when `uri` changes, so the old GIF stays until the new one decodes.
- CONFIRMED · **low**
- Fix: key the whole thing on `uri`, and draw the still frame from the memory cache synchronously.

## Design preferences (not defects)
- `rememberArtPalette` (SceneStyle.kt:129) holds the previous palette until the new one is ready, then glides over 900 ms.
- The intended hold-old-picture behaviour in BakedBlur `keepLast`, the Case3D stand-ins, and CartShells keeping the prior front for the same identity.

## Sites checked and already correct
- AppIcons.kt:132/146/229: produceState tagged with packageName.
- BakedBlur.kt:68: tagged with src (keepLast hold is intentional).
- GenericDisc.kt:76/251: wrapped in `key(platform/path)`.
- CoverCases.kt:144/164: rememberSpine and rememberTitledSpine are peeked and tagged.
- Case3D.kt:754 rememberDerived and :987 rememberConsoleSpine: `remember(key)` state.
- Motion.kt HeldLayers / HeldImage: old picture kept until the new one's onSuccess; first layer shown at once.
- HeroCarouselBackdrop: target is a Triple(url, gameKey, fallback); first frame from the memory cache. Only V7 applies.
- HomeChrome HeroLayers on a game change: per-game layers keyed by id, fade after ready. Only V8/V17 apply.
- VideoBackdrop / VideoCrossfade: fade in on MEDIA_INFO_VIDEO_RENDERING_START, old clip faded out; no black frame.
- Non-loop keys: Carousel (stableKey), GameDock (stableKey), TileHome.kt:522 (stableKey + lap), GridHome.kt:184 (lazy key), HeroCarousel.kt:419 (stableKey#lap), StyleConsoleStack.kt:229 (stop key).
- StyleCrossMenu.kt:96-109: category slide uses a synchronous pending tick (the reference pattern for V2).
- FadeInImage (Motion.kt:275), riseOnEnter: `remember(model)` Animatable(0) and initial 0; correct.
- DetailLayers.kt:60-74: state is `remember(gameKey)`.
- SecondScreenDetails.kt:65: `remember(game.stableKey)` flip.
- StyleRecordCrate.kt:758: AnimatedContent `contentKey = stableKey`. The other `AnimatedContent(targetState = g)` sites use the Game data class, whose fields are stable, so they are effectively fine.
- ArtRequests / StyleArtRequests: fixed size and memory key, thumbnail placeholder; GameCard size animations snap in the fixed slot.
- GameCard wide reveal: `wideLoaded` keyed by wideLayer and revealed only after onSuccess.
- StyleRecordCrate.kt:451 and PocketRibbon.kt:259: snapTo happens to the current value, so there is no visible snap.

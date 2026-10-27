# VoltCap QA rules — recurring bug patterns

Each rule came from a real defect. `tools/qa/tools/static_checks.py` checks the ones that can be found in code;
the device runs (`tools/qa/README.md`) catch the rest. When Toni finds something the QA system missed, add the rule
here (what it looks like, why it happens, how to detect it, how to fix it), add a check, and search the whole app
for the same pattern.

A line marked `qa-ok: RULE (reason)` (on the line or the line before) is accepted on purpose.

## R1 STALE-PRODUCE — the previous item's picture for a frame (HIGH)
- **Looks like:** the old game's cover / console photo / case face / screenshot flashes before the right one.
- **Why:** `produceState(initial, keys)` keeps its last value when the keys change; the producer restarts but can't run
  before the next frame, and an async load keeps the old value on screen until it finishes.
- **Fix:** `produceKeyed(initial, identity, keys…)` (ui/ProduceKeyed.kt): the value belongs to `identity`; another
  identity's value is never returned. Or tag the value (`key to value`) and check the tag (AppIcons.kt, RealMedia.kt).
- **Variant STALE-FOREVER:** `produceState(cached(x), x) { if (value == null) value = load(x) }` — after a key change
  `value` is the old non-null one, so the new one is never loaded.
- **Check:** static_checks STALE-PRODUCE / STALE-FOREVER; device: `frames.py` transients on rapid navigation.

## R2 SNAP-ANIMATE — the new state drawn fully for one frame, then the animation starts (HIGH on home)
- **Looks like:** new info / a new row appears in place for a frame, then disappears and slides or fades in.
- **Why:** `LaunchedEffect(key) { anim.snapTo(start); anim.animateTo(end) }` runs after the frame where the key changed;
  that frame is drawn with the old end value (fully shown). Waiting extra frames (`withFrameNanos`) makes it longer.
- **Fix:** remember which key the animation has caught up with; while it hasn't, draw the start value
  (HomeExtras.categorySlide, Motion.riseOnChange, StyleCrossMenu). Or `remember(key) { Animatable(start) }`.
- **Found:** QA run r1 (frames 438, 698, 702 of the L2/R2 recordings: the new category's row drawn in place, full
  strength, before the slide).
- **Check:** static_checks SNAP-ANIMATE (review list); device: `frames.py` on category switches.

## R3 SLOT-KEY — a looping row keyed by place (HIGH)
- **Looks like:** after L2/R2 on an endless row, old covers in new cases, stickers with the old title, slots resizing
  from the old game's shape.
- **Why:** `key(slot)` keeps every remembered value of the slot when its game changes.
- **Fix:** key by place AND game (`"$slot|${game.stableKey}"`): scrolling keeps the keys, a new list gets new ones.

## R4 PULSE-COMPOSE — per-frame values read in composition (MED, performance)
- **Looks like:** nothing visible; frame times go up and the home screen never idles (20+ rebuilds a second).
- **Fix:** keep the State, read it in `graphicsLayer {}` / draw lambdas (`rememberFocusPulseState`, `softGlow(() -> Float)`,
  `pulseBorder`).

## R5 MAIN-DECODE — decoding pictures on the main thread (MED, stutter)
- `imageResource`, `painterResource` on big PNGs, `BitmapFactory.decode*` in composition / remember. Decode once off the
  main thread and share (RealMedia DerivedArt).

## R6 CONTROLLER — everything operable without touch
- Every interactive element: focusable (shelfFocusable / shelfClickable / padTarget), visible focus ring, A activates,
  B closes, focus returns to the opener, ◀ ▶ adjust sliders and leave them at the ends, small ranges step by 1.
- Touch-only only for drawing / pinch / trackpad, and then a button path to the same result exists.
- **Check:** static_checks TOUCH-ONLY / SLIDER-COARSE (review list); device: `focuswalk` scenario per screen.

## R7 NEVER report a pass that wasn't run
- A clean recording is not proof: note the capture rate (frames.py prints it; the Thor records every 120 Hz frame,
  other devices may not — use `vqa.sh rate60` there) and what was on screen.

## R8 QA-COST — the measuring must not change what is measured
- **Found (pass 2, D21):** a state-churn observer (every snapshot change named on the main thread) left on with the
  hook file made the home screen crawl on Toni's Thor (41 frames in a nav run, p90 1.5 s).
- **Rules:** heavy diagnostics are opt-in per run (`--ez voltcap_test_churn true`), never on just because the hook file
  exists; `vqa.sh suite`/`selftest` switch the hook file on at the start and off at the end (`vqa.sh hooks on|off`), so
  the handheld is used without QA code; every new probe gets an A/B run (hooks on vs off, same scenario) before trusting
  its numbers.

## R9 SNAP-LATE-LAYOUT — a snap that runs after the frame it is for (HIGH)
- **Looks like:** one empty or misplaced frame when a list is replaced (D17: the new category's row drawn at the old row
  position — off-screen — for one frame before the slide).
- **Why:** `LaunchedEffect(list) { pos.snapTo(new) }` runs after the frame in which the list changed.
- **Fix:** while a snap is due, read the target directly where the value is used (placement / draw lambdas).

## R10 THEME-COMPOSE — theme colours read where the colour glide re-composes everything (HIGH for home screens)
- **Found (pass 2, D22):** the theme colours glide for 600 ms after every game change. Anything that read
  `ShelfTheme.accent` / `.colors` in composition was rebuilt every frame of it: the whole home screen (a Modifier
  factory — EdgeBump — and the status-bar look read the accent in the screen's own scope) ~110×/s and every game card
  (~1000 card rebuilds/s) while navigating.
- **Why it hides:** Modifier factories and value-returning `@Composable` functions run in the CALLER's recompose scope, as
  do Box/Row/Column contents — one read in a helper rebuilds the whole caller.
- **Fix:** read `ShelfTheme.live.value` (one stable State) inside draw / graphicsLayer lambdas (`softGlowLive`,
  `pulseBorderLive`, `drawBehind { drawRect(live.value.accent) }`, `graphicsLayer { ambientShadowColor = … }`); an icon
  tinted with the accent → `AccentIcon` (its own scope); read the colour only in the branch that uses it.
- **Check:** static_checks THEME-COMPOSE; on device `--ez voltcap_test_churn true` → `inval <part>: Nx ShelfColors`
  lines (QaInval names the state that invalidated each registered part), and the `snap_colors` test fault (glide off)
  as the A/B.

## R11 WALK-SAFETY — a controller walk must leave the handheld as it found it
- **Found (pass 3):** a snake walk over the R1 panel pressed ◀ ▶ on the brightness and volume sliders (brightness 77 → 100 %,
  volume 13 → 53 %). Restored by hand.
- **Rules:** `walk.sh` / `explore.sh` save Voltcap's settings (`voltcap_test_snapshot save|restore`), the brightness and
  the media volume first and put them back at the end. The explorer presses A only on navigation rows ("›") whose
  name isn't destructive (delete, reset, sign out, restore, put everything back, Edit Home…), and stops on
  `--es voltcap_test_explore stop`. Never send keys by hand while a walk runs (it mixes into the walk's log).

## R12 STEP-READ — a "not yet" branch must still read the animation it guards
- **Found (pass 4):** Constellation's focus panel and System Vault's reader screen stayed invisible after a move.
  `LookStep.now()` returned 0 for the frame the key had changed in *without reading* the Animatable. The graphics layer
  that called it subscribed to nothing, so it was never redrawn as the animation ran.
- **Rule:** a helper used inside `graphicsLayer {}` / `drawBehind {}` reads every State unconditionally first, then
  branches (`val v = a.value; return if (pending) 0f else v`).
- **Check:** looks.sh still frame `_b` (taken 1.6 s after the last step) must show the focused system. The frames.py run
  on the move recording shows a step that never finishes as a missing object.

## R13 WINDOW-BY-GLIDE — build what's on screen, not only round the selection
- **Found (pass 4):** Pocket Worlds dropped a world that was still sliding on screen after two quick presses. The window
  was built round the selection while the glide lagged behind.
- **Rule:** a look that builds a window of items uses `lookWindow(selected, pos, reach)`, which spans both the selection
  and the glide's current place.
- **Check:** frames.py on looks.sh recordings (two quick steps): no transient at the screen's edge.

## R14 WRAP-AFTER-PARALLAX — wrap a moving object's base position, then add the parallax
- **Found (pass 4):** clouds, stars and props in Pocket Worlds jumped from one edge to the other mid-slide.
  `(base + shift).mod(W)` wraps as the shift changes.
- **Rule:** `base.mod(W) + shift`.

## R15 PATH-REUSE (tooling) — never commit a file to the PC from a reused outputs path
- **Found (pass 4):** re-committing /mnt/user-data/outputs/qa/device/looks.sh after an edit wrote the *old* content, and
  the device ran a stale script.
- **Rule:** stage every commit from a fresh `/mnt/user-data/outputs/<unique>/` directory (ship.sh does this for the
  sources), and check the pushed file on the device (`grep` for the change) before a run.

## R16 PREVIEW-LEAK (tooling) — a run's preview must end with the run
- **Found (pass 5):** `voltcap_test_preview true` lasted until Voltcap restarted. After layouts.sh / mixcon.sh / catlook.sh the
  layout or look tried stayed on screen, and anything Toni changed afterwards in that session was not saved.
- **Rule:** every script that starts a preview ends it with `--ez voltcap_test_preview false` (TestHooks → the saved
  appearance reloaded, saving on again) before `vqa.sh hooks off`.

## R17 GLIDE-COMPOSE — an animation read at the top of a looped item re-composes every item every frame
- **Found (pass 5):** Collector's Drawers (`val o by animateFloatAsState`, used for sizes and offsets in composition) ~9 %
  janky → 2 %; System Blades (`val open = 1f - abs(i - pos.value)`) 1–6 % → ~2 %; Stage, Constellation and Vault had the same.
- **Rule:** keep the State; read `.value` in graphicsLayer / draw / layout lambdas or a local `fun`; a value that changes
  the tree (show the insides or not, z-order) goes through `derivedStateOf` so it changes a few times, not every frame.
- **Check:** static_checks GLIDE-COMPOSE (review list — 46 places across the app on 2026-10-27, most of them home layouts).

## R18 TRUNCATE — never cut user-facing text short with "…" (permanent rule, CLAUDE.md #11)
- **Rule (Toni, 2026-10-27):** no `TextOverflow.Ellipsis`, no `take(n) + "…"` / `"..."` on text people read. Show all of it:
  wrap, adapt the spacing, resize to a size that stays readable (`ui/FitText.kt`), or scroll it smoothly (marquee / on focus).
- **Allowed only** when a strict hardware, system API, third-party UI or fixed-size constraint can't reasonably be changed —
  and then never silently: the whole text stays reachable through focus, scrolling, expansion or a detail view. Mark the line
  `qa-ok: TRUNCATE (why; where the full text is)`.
- **Keep** ellipses that are part of the text itself (a game's title, menu labels like "Inspect…" that open more).
- **Check:** static_checks TRUNCATE lists every `TextOverflow.Ellipsis` and every code-made "…" / "..." cut; screenshots: a
  text ending in "…" that isn't in its source string is a defect. New code must add none; existing ones are fixed as their
  screens are touched, and counted in REPORT.md until there are none left.

## R19 TRUNCATE-CLIP — a one-line text with no overflow is cut off silently (RP Classic pass, 2026-10-27)
- **Why:** `Text(..., maxLines = 1)` with no `overflow` clips the end with nothing to show text is missing — worse than "…".
  On the RP Classic's 620 dp width the Worlds sign, Channel / Vault captions and Stage plaques were cut like that.
- **Rule:** use `FullText` (shrinks to a readable size, then slides; the slide rests with the home screen) or let it wrap.
  A label with free width (placed by translation, nothing to fit) takes `softWrap = false` instead of `maxLines = 1`.
- **Check:** static_checks TRUNCATE-CLIP (review list, 289 places on 2026-10-27; fixed as screens are touched).

## R20 LAYER-BRANCH — read an animation before branching on it in a layer / draw lambda
- **Why (Tile home, Toni: "the names disappear"):** `val v = if (pending) 0f else p.value` inside `graphicsLayer {}`. While
  `pending` was true the lambda read no state, so the animation that followed never invalidated the layer: the name stayed
  at alpha 0 until something else recomposed it.
- **Rule:** read `.value` into a local first, then branch. Same in composition (a body that skips the read never re-runs).
- **Check:** static_checks LAYER-BRANCH (0 left after the fix). Device: after a move the focused name must be visible in
  the still (stills.sh / walk screenshots).

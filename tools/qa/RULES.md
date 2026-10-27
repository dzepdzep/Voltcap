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

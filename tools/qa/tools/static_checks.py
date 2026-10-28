#!/usr/bin/env python3
"""VoltCap QA — static checks for the bug patterns that caused real, user-visible defects (see tools/qa/RULES.md).

  python3 static_checks.py SRC_DIR [--json out.json] [--strict] [--min-files N]

Each rule prints file:line and why. A line (or the line before it) carrying `qa-ok: <RULE>` is accepted on purpose.
--strict exits 1 when any HIGH rule fires (for a pre-ship check). Exit 2 (infrastructure) when SRC_DIR is missing or
holds fewer than --min-files (default 1) Kotlin files: scanning nothing is not a pass. Python 3 stdlib only. Never reads ThemeShaders.kt.
"""
import json, os, re, sys

IMAGEY = re.compile(r"Bitmap|ImageBitmap|Painter|Drawable|photo|cover|art\b|Art\b|shots|url|Url|thumb|icon|Icon|face|Face|frame|Frame", re.I)


def files(src):
    for root, _, fs in os.walk(src):
        for f in fs:
            if f.endswith(".kt") and f != "ThemeShaders.kt":
                yield os.path.join(root, f)


def call_args(s, start):
    """s[start] == '(' → (args text, index after ')')."""
    d = 0
    for i in range(start, len(s)):
        c = s[i]
        if c == "(":
            d += 1
        elif c == ")":
            d -= 1
            if d == 0:
                return s[start + 1:i], i + 1
    return s[start + 1:], len(s)


def split_top(a):
    parts, d, cur = [], 0, ""
    for c in a:
        if c in "([{<":
            d += 1
        elif c in ")]}>":
            d -= 1
        if c == "," and d == 0:
            parts.append(cur); cur = ""
        else:
            cur += c
    if cur.strip():
        parts.append(cur)
    return parts


def accepted(lines, ln, rule):
    for k in (ln - 1, ln - 2):
        if 0 <= k < len(lines) and f"qa-ok: {rule}" in lines[k]:
            return True
    return False


def check(src):
    out = []
    for path in files(src):
        rel = os.path.relpath(path, src)
        s = open(path, encoding="utf-8", errors="replace").read()
        lines = s.splitlines()

        def add(rule, sev, pos, msg):
            ln = s.count("\n", 0, pos) + 1
            if not accepted(lines, ln, rule):
                out.append(dict(rule=rule, sev=sev, file=rel, line=ln, msg=msg, code=lines[ln - 1].strip()[:160]))

        # STALE-PRODUCE: keyed produceState holding a picture-like value, result not checked against its key.
        for m in re.finditer(r"produceState(<[^>(]*(?:<[^>]*>)?[^>(]*>)?\s*\(", s):
            args, end = call_args(s, m.end() - 1)
            parts = split_top(args)
            if len(parts) < 2:
                continue  # not keyed: can't go stale on a key change
            window = s[m.start(): end + 900]
            typ = (m.group(1) or "") + parts[0]
            tagged = bool(re.search(r"Pair<|to\s|\.first\s*==|\.first\s*!=|takeIf\s*\{\s*\w+\.first", window[:1200]))
            if tagged:
                continue
            body = s[end:end + 700]
            if not IMAGEY.search(typ + " " + body[:400]):
                continue
            first_stmt = re.match(r"\s*\{\s*(.*)", body, re.S)
            resets = bool(first_stmt and re.match(r"value\s*=\s*(null|emptyList|initial)", first_stmt.group(1)))
            sev = "MED" if resets else "HIGH"
            add("STALE-PRODUCE", sev, m.start(),
                "keyed produceState keeps the previous key's value until the new one loads (old picture for ≥1 frame)"
                + ("; it resets to empty first (blank, not stale)" if resets else "") + ". Tag the value with its key.")
            # stale-forever variant
            if re.search(r"if\s*\(\s*value\s*==\s*null\s*\)", body[:200]):
                add("STALE-FOREVER", "HIGH", m.start(),
                    "produceState(cached(k), k) { if (value == null) … }: after a key change value is the OLD non-null value, so the new one never loads")

        # SNAP-THEN-ANIMATE: LaunchedEffect(key) { … snapTo(start) … animateTo(end) } — the key-change frame draws the old end value.
        for m in re.finditer(r"LaunchedEffect\(([^)]*)\)\s*\{", s):
            keys = m.group(1).strip()
            if keys in ("Unit", "true", ""):
                continue
            blk = s[m.end(): m.end() + 900]
            sn = re.search(r"(\w+)\.snapTo\(", blk)
            if not sn:
                continue
            an = re.search(re.escape(sn.group(1)) + r"\.animateTo\(", blk[sn.end():])
            if not an:
                continue
            var = sn.group(1)
            after = s[m.end(): m.end() + 4000]
            guarded = bool(re.search(r"pending|seen\w*\s*!=|!=\s*seen|Tick\s*!=|tick\s*!=\s*seen", after))
            if guarded:
                continue
            add("SNAP-ANIMATE", "MED", m.start(),
                f"`{var}` is snapped to its start inside LaunchedEffect({keys[:40]}) — the frame where the key changes still draws the old end value, "
                "then it jumps (flash). Draw the start value while the effect hasn't caught up (see HomeExtras.categorySlide / Motion.riseOnChange).")

        # SLOT-KEY: a looping row keyed only by its place — a new list reuses the old items' state.
        for m in re.finditer(r"\bkey\(([^{]*)\)\s*\{", s):
            k = m.group(1)
            if re.search(r'"loop:\$slot"\s*else|if \(\w*[Ll]oop\w*\) i else|if \(\w*[Ll]oop\w*\) slot else', k):
                add("SLOT-KEY", "HIGH", m.start(), "looping row keyed only by its place: a category change reuses the old games' remembered state; add the game's key")

        # PULSE-IN-COMPOSITION: an every-frame/20 Hz value read as a plain Float in composition.
        for m in re.finditer(r"=\s*rememberFocusPulse\(", s):
            add("PULSE-COMPOSE", "LOW", m.start(), "pulse read in composition rebuilds the card ~20×/s; use rememberFocusPulseState and read it in draw lambdas")

        # MAIN-DECODE: image decodes in composable code paths.
        for m in re.finditer(r"\b(imageResource|BitmapFactory\.decode\w+)\(", s):
            pre = s[max(0, m.start() - 600): m.start()]
            if re.search(r"withContext\(|Dispatchers\.(IO|Default)|Thread\s*\{|launch\(Dispatchers", pre[-300:]):
                continue
            if "@Composable" in pre or "remember" in pre[-160:]:
                add("MAIN-DECODE", "MED", m.start(), "image decode on the main thread (in composition/remember): a frame spike the first time it shows")

        # TOUCH-ONLY: raw clickable/tap detection without a focus modifier on the same chain (outside home layouts).
        if not re.search(r"(Style\w+|Twin\w+|Pocket\w+|CubeHub|TileHome|GridHome|HeroCarousel|Carousel|GameCard|GameDock|HomeChrome)\.kt$", rel):
            for m in re.finditer(r"\.clickable\s*[({]|detectTapGestures", s):
                seg = s[max(0, m.start() - 300): m.start() + 200]
                if re.search(r"focusable|shelfFocusable|focusRing|tapOnly|padTarget|focusProperties|onKeyEvent|onPreviewKeyEvent", seg):
                    continue
                add("TOUCH-ONLY", "LOW", m.start(), "tap handler with no focus/key handling nearby — check a controller can reach and press it")

        # THEME-COMPOSE (pass 2, D22): theme colours read at the top of a Modifier factory or a value-returning composable.
        # Those run in the CALLER's recompose scope, and the colours glide every frame after each game change, so the
        # whole caller (the home screen, every card) re-composes ~100×/s. Read ShelfTheme.live.value while drawing.
        for m in re.finditer(r"@Composable(?:\s+@\w+)*\s+(?:(?:private|internal|public)\s+)*fun\s+(Modifier\.)?(\w+)\s*\(", s):
            i, depth = m.end(), 1
            while depth and i < len(s):
                depth += {"(": 1, ")": -1}.get(s[i], 0); i += 1
            head = s[i:i + 120]
            returns = bool(m.group(1)) or bool(re.match(r"\s*:\s*(?!Unit\b)[\w.<>?]", head))
            if not returns:
                continue
            body = s[i: i + 2500]
            e = re.search(r"\n(?:@Composable|(?:private |internal )?fun |/\*\*)", body)
            body = body[: e.start()] if e else body
            for r in re.finditer(r"ShelfTheme\.(accent|accentGlow|background|secondary|textMuted|colors)\b|LocalShelfColors\.current", body):
                add("THEME-COMPOSE", "MED", i + r.start(),
                    f"`{m.group(2)}` reads the theme colours in the caller's composition: the colour glide re-composes the caller every frame; "
                    "read ShelfTheme.live.value in a draw / graphicsLayer lambda (or only in the branch that needs it)")
                break


        # GLIDE-COMPOSE (pass 5, D54): an animation's value read in composition (`val open = 1f - abs(i - pos.value)` at
        # the top of a looped item) re-composes every item on every frame of the animation. Read it in graphicsLayer /
        # draw lambdas, a local `fun`, or a derivedStateOf that changes rarely. (A review list: some reads are on purpose.)
        anims = set(re.findall(r"val\s+(\w+)\s*=\s*(?:rememberLookGlide|remember\s*\{\s*Animatable|androidx\.compose\.animation\.core\.animateFloatAsState|animateFloatAsState|animateDpAsState)\b", s))
        SAFE_OWNERS = {"graphicsLayer", "drawBehind", "drawWithContent", "drawWithCache", "Canvas", "derivedStateOf", "LaunchedEffect",
                       "snapshotFlow", "offset", "layout", "onDrawBehind", "onDrawWithContent", "withFrameNanos", "animateTo", "snapTo",
                       "launch", "pointerInput", "detectDragGestures", "detectTapGestures", "onKeyEvent", "onPreviewKeyEvent",
                       "DisposableEffect", "SideEffect", "drawIntoCanvas", "withTransform", "translate", "rotate", "scale", "clipRect",
                       "onGloballyPositioned", "onSizeChanged", "remember", "produceState", "coroutineScope", "withoutReadObservation"}

        def enclosing_safe(pos):
            j = pos
            for _ in range(40):
                depth = 0
                while j > 0:
                    j -= 1
                    ch = s[j]
                    if ch == "}":
                        depth += 1
                    elif ch == "{":
                        if depth == 0:
                            break
                        depth -= 1
                if j <= 0:
                    return False
                k = j - 1
                while k > 0 and s[k] in " \t\n\r":
                    k -= 1
                if s[k] in "=,(" or s[k - 1:k + 1] == "->":
                    return True  # a lambda value or argument: read when it's called
                if s[k] == ")":
                    d = 0
                    while k > 0:
                        if s[k] == ")":
                            d += 1
                        elif s[k] == "(":
                            d -= 1
                            if d == 0:
                                break
                        k -= 1
                    k -= 1
                e2 = k + 1
                while k > 0 and (s[k].isalnum() or s[k] in "_."):
                    k -= 1
                owner = s[k + 1: e2].split(".")[-1]
                if owner in SAFE_OWNERS:
                    return True
                pre = s[max(0, j - 160): j]
                if re.search(r"\bfun\s+[\w.<>]+\s*\([^)]*\)[^{=]*$|\bget\(\)\s*$|\bobject\b[^{]*$|\bclass\b[^{]*$", pre):
                    # A helper fun's body runs when it's called; a @Composable fun's body is composition.
                    return not re.search(r"@Composable[^{}]*$", s[max(0, j - 400): j])
            return False

        if anims:
            for m in re.finditer(r"\b(" + "|".join(map(re.escape, anims)) + r")\.value\b", s):
                line = lines[s.count("\n", 0, m.start())]
                col = m.start() - (s.rfind("\n", 0, m.start()) + 1)
                if line[:col].count("{") > line[:col].count("}"):
                    continue  # a lambda opened on this line
                if re.search(r"\bfun\s+\w+\s*\([^)]*\)\s*(:\s*\w+\s*)?=", line):
                    continue  # a local fun: read when it's called
                if enclosing_safe(m.start()):
                    continue
                add("GLIDE-COMPOSE", "MED", m.start(), f"`{m.group(1)}.value` read in composition: every item re-composes on every animation frame; read it in a layer/draw lambda")


        # TRUNCATE (R18, permanent rule CLAUDE.md #11): user-facing text cut short with an ellipsis.
        for m in re.finditer(r"TextOverflow\.Ellipsis", s):
            add("TRUNCATE", "MED", m.start(), "text cut short with … — wrap, resize readably (FitText) or scroll it instead; if unavoidable, mark qa-ok: TRUNCATE with where the full text is")
        for m in re.finditer(r"\.take\([^)]*\)\s*\+\s*\"(?:…|\\u2026|\.\.\.)\"|\"(?:…|\.\.\.)\"\s*\+|\.(?:ellipsize|truncate)\w*\(|TextUtils\.ellipsize", s):
            add("TRUNCATE", "MED", m.start(), "text shortened in code with … — show the whole text (R18)")

        # TRUNCATE-CLIP (R18, RP Classic pass): a plain Text held to one line with no overflow given clips its end
        # silently (worse than an ellipsis: nothing says text is missing). FullText / FitText shrink or slide instead.
        for m in re.finditer(r"(?<![\w.])Text\(", s):
            d = 0; k = m.end() - 1
            while k < len(s):
                if s[k] == "(": d += 1
                elif s[k] == ")":
                    d -= 1
                    if d == 0: break
                k += 1
            call = s[m.start(): k + 1]
            if re.search(r"\bmaxLines\s*=\s*1\b", call) and "overflow" not in call and "qa-ok" not in call:
                add("TRUNCATE-CLIP", "LOW", m.start(), "one-line Text with no overflow: a long text is cut off silently — FullText (shrink, then slide) or wrap it")

        # LAYER-BRANCH (RP Classic pass, the vanishing Tile home name): an animation read only in one branch of a draw /
        # layer lambda. While the other branch is taken the lambda observes nothing, so the animation that follows never
        # redraws it and the frame stays stuck (an invisible name). Read the value into a local first.
        for m in re.finditer(r"=\s*if\s*\((?:[^()]|\([^()]*\))*\)\s*-?[\d.]+f\s*else\s*\w+\.value\b", s):
            add("LAYER-BRANCH", "MED", m.start(), "animation read only in the else-branch: while the if-branch is taken nothing is observed and the layer can stay stuck; read `.value` into a local first")

        # SLIDER-COARSE: D-pad stepping fixed at 5 for small ranges.
        for m in re.finditer(r"\(drag / 5f\)\.roundToInt\(\) \+ dir\) \* 5", s):
            if "step == 1" not in s[max(0, m.start() - 400): m.start()]:
                add("SLIDER-COARSE", "MED", m.start(), "D-pad steps of 5 on any range: small ranges can't be set exactly")
    return out


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # messages hold "…" / "≥"; a cp1252 console must not crash the run
    if len(sys.argv) < 2 or sys.argv[1].startswith("--"):
        sys.exit("usage: static_checks.py SRC_DIR [--json out.json] [--strict] [--all] [--min-files N]")
    src = sys.argv[1]
    min_files = int(sys.argv[sys.argv.index("--min-files") + 1]) if "--min-files" in sys.argv else 1
    if not os.path.isdir(src):
        print(f"[INFRA] static checks: no such source folder: {src}"); sys.exit(2)
    scanned = sum(1 for _ in files(src))
    if scanned < max(1, min_files):
        print(f"[INFRA] static checks: only {scanned} Kotlin files under {src} (need {max(1, min_files)}): nothing was checked")
        sys.exit(2)
    res = check(src)
    order = {"HIGH": 0, "MED": 1, "LOW": 2}
    res.sort(key=lambda r: (order[r["sev"]], r["rule"], r["file"], r["line"]))
    counts = {}
    for r in res:
        counts[(r["rule"], r["sev"])] = counts.get((r["rule"], r["sev"]), 0) + 1
    for r in res:
        if r["sev"] != "LOW" or "--all" in sys.argv:
            print(f'{r["sev"]:4} {r["rule"]:14} {r["file"]}:{r["line"]}  {r["msg"]}\n      {r["code"]}')
    print(f"\nscanned {scanned} Kotlin files")
    print("summary:", ", ".join(f"{k[0]}/{k[1]}={v}" for k, v in sorted(counts.items())))
    if "--json" in sys.argv:
        json.dump(res, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)
    if "--strict" in sys.argv and any(r["sev"] == "HIGH" for r in res):
        print("[FAIL] static checks (strict): HIGH findings above"); sys.exit(1)
    print("[PASS] static checks" + (" (strict)" if "--strict" in sys.argv else ""))

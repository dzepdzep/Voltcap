#!/usr/bin/env python3
"""VoltCap QA — opt-in element geometry check (update 369). Device-free: reads a recorded or synthetic geometry trace.

  python3 geomcheck.py TRACE.json [--json OUT.json]
  python3 geomcheck.py LOGCAT.txt --logcat [--dump TRACE.json]   (VoltcapQA lines from the app's QaGeom, see from_logcat)

TRACE.json: {"screen": [w, h], "frames": [{"t": ms, "key": "R"|null, "focused": "id"|null, "focus": rect|null,
             "elements": {"id": rect}}], "row": ["id", ...] | "auto" (every element, sorted by x each frame)?, "animating": ["id", ...]?, "limits": {...}?}   rect = [x, y, w, h]
Checks (each reports its own Result):
  size      an element keeps its width / height (animating ids excepted) within limits.size_px (default 2)
  spacing   the neighbours in "row" are evenly spaced in every frame within limits.gap_px (default 2)
  focus     the focus indicator surrounds the focused element (centre inside it, within limits.focus_px) and stays on screen
  (logcat traces set limits.skip_key_frames: the frame that carries a key is read by QaGeom before its layout, one tile
            can read x=0 there although the screen recording shows no flash - size / spacing skip that frame only)
  movement  no element jumps further than limits.jump_px (default 60) between two frames unless a key came in the last
            limits.settle_ms (default 450) — a drift or snap with no key is a defect; the glide after a key is expected
Exit codes as qalib: 0 PASS, 1 FAIL, 2 INFRA (bad / empty trace: never a pass), 3 NOT TESTED.

Update 467 — clipping gate (permanent, QA rule R26):
  python3 geomcheck.py CLIP.json --clip [--png SHOT.png] [--json OUT.json]
CLIP.json is QaClip's dump (`--es voltcap_test_clip TAG` -> <files>/qa_clip/TAG.json): per window, every control and text
node with `full` (its own box) and `shown` (the part inside all its ancestors). Results:
  clip      no control / text is cut: shown == full within clip_px (default 1.5); a control cut inside a scrolling list is
            reported separately (scroll-clip) — it is reachable only by scrolling, and FAILS when it is a window action
            (Play, Continue, Details, Back, More, Close, OK, Cancel)
  screen    every shown control lies inside the screen and its own window
  overlap   no two controls overlap by more than overlap_px (default 2) unless one holds the other
  touch     every control is at least touch_dp (default 24, WCAG 2.2 target size) dp tall and wide — smaller is a FAIL;
            under 32 dp is noted (Compose widens small targets to 48 dp where nothing else is under the finger)
  focus     a focused control is not cut and is on screen; no window shows a focused node outside itself
  pixels    (with --png) inside each window action's laid-out box, below its middle: no row where nearly the whole width
            changes at once (fill above, backdrop below) well above its bottom — a draw clip the layout can't see FAILS.
            Heuristic: never claims full coverage.
An empty dump (no windows / no controls) is INFRA, never PASS.
"""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

DEFAULTS = dict(size_px=2.0, gap_px=2.0, focus_px=6.0, jump_px=60.0, settle_ms=450.0)


def _centre(r):
    return (r[0] + r[2] / 2.0, r[1] + r[3] / 2.0)


def evaluate(trace):
    """The Results for [trace] (a dict). INFRA when the trace can't prove anything."""
    frames = trace.get("frames") if isinstance(trace, dict) else None
    if not frames or len(frames) < 2:
        return [qalib.Result("geometry trace", qalib.INFRA, "fewer than 2 frames: nothing was measured")]
    lim = dict(DEFAULTS); lim.update(trace.get("limits") or {})
    screen = trace.get("screen") or [0, 0]
    animating = set(trace.get("animating") or [])
    row = trace.get("row") or []
    size_p, gap_p, focus_p, move_p = [], [], [], []
    first = {}
    last_key_t = None
    prev = None
    for i, fr in enumerate(frames):
        t = fr.get("t", i)
        if fr.get("key"): last_key_t = t
        els = fr.get("elements") or {}
        for k, r in els.items():
            f = first.setdefault(k, r)
            if not (fr.get("key") and lim.get("skip_key_frames")) and k not in animating and (abs(r[2] - f[2]) > lim["size_px"] or abs(r[3] - f[3]) > lim["size_px"]):
                size_p.append(f"frame {i}: {k} is {r[2]:g}x{r[3]:g}, was {f[2]:g}x{f[3]:g}")
        unsettled = bool(fr.get("key")) and lim.get("skip_key_frames")  # the producer reads before that frame's layout
        shown = [] if unsettled else sorted(els.values(), key=lambda r: r[0]) if row == "auto" else [els[k] for k in row if k in els]
        if len(shown) >= 3:
            gaps = [shown[j + 1][0] - (shown[j][0] + shown[j][2]) for j in range(len(shown) - 1)]
            if max(gaps) - min(gaps) > lim["gap_px"]:
                gap_p.append(f"frame {i}: gaps in the row differ by {max(gaps) - min(gaps):g} px ({min(gaps):g}..{max(gaps):g})")
        fid, fr_rect = fr.get("focused"), fr.get("focus")
        if fid and fr_rect is not None and fid in els:
            e = els[fid]; cx, cy = _centre(fr_rect); ex, ey = _centre(e)
            if abs(cx - ex) > lim["focus_px"] + e[2] / 2.0 or abs(cy - ey) > lim["focus_px"] + e[3] / 2.0:
                focus_p.append(f"frame {i}: focus ring is not on {fid}")
            if screen[0] and (fr_rect[0] < -lim["focus_px"] or fr_rect[1] < -lim["focus_px"] or
                              fr_rect[0] + fr_rect[2] > screen[0] + lim["focus_px"] or fr_rect[1] + fr_rect[3] > screen[1] + lim["focus_px"]):
                focus_p.append(f"frame {i}: focus ring leaves the screen")
        elif fid and fr_rect is None:
            focus_p.append(f"frame {i}: {fid} is focused but no focus indicator is drawn")
        if prev is not None:
            settling = last_key_t is not None and t - last_key_t <= lim["settle_ms"]
            for k, r in els.items():
                p = prev.get(k)
                if p is None or settling: continue
                d = max(abs(r[0] - p[0]), abs(r[1] - p[1]))
                if d > lim["jump_px"]:
                    move_p.append(f"frame {i}: {k} moved {d:g} px with no key in the last {lim['settle_ms']:g} ms")
        prev = els
    out = []
    for name, probs, what in (("size", size_p, "element sizes stay constant"), ("spacing", gap_p, "row spacing stays even"),
                              ("focus", focus_p, "focus indicator on the focused element"),
                              ("movement", move_p, "no unexpected movement")):
        out.append(qalib.Result("geometry " + name, qalib.FAIL if probs else qalib.PASS,
                                "; ".join(probs[:5]) + (f" (+{len(probs) - 5} more)" if len(probs) > 5 else "") if probs else what))
    return out


_LINE = re.compile(r"VoltcapQA\s*:\s*(\d+) (geom|geomkey|geomfocus|geomscreen|geomgone)(?:| )(.*)$")


def from_logcat(text):
    """Build a trace from the app's `VoltcapQA` lines (QaGeom.kt, `--ez voltcap_test_geom true`): `geom id=x,y,w,h …` (only
    what changed), `geomkey NAME`, `geomfocus id`, `geomscreen W H`. Every geom line is one frame with every element seen so far
    that is still on the screen; the focus rectangle is the focused tile's own rectangle (the ring is drawn inside the tile,
    so ring alignment is the tile on screen). A frame that carries a key is marked with it. No geom lines = empty trace."""
    screen = [0, 0]; frames = []; els = {}; focused = None; key = None; order = {}
    for ln in text.splitlines():
        m = _LINE.search(ln)
        if not m: continue
        t, kind, rest = int(m.group(1)), m.group(2), m.group(3).strip()
        if kind == "geomscreen":
            try: screen = [int(x) for x in rest.split()[:2]]
            except ValueError: pass
        elif kind == "geomgone": els.pop(rest, None); order.pop(rest, None)
        elif kind == "geomkey": key = rest or "key"
        elif kind == "geomfocus": focused = rest
        elif rest != "off":
            for tok in rest.split():
                k, _, v = tok.partition("=")
                try: els[k] = [float(x) for x in v.split(",")]
                except ValueError: continue
                if len(els[k]) != 4: del els[k]
            if screen[0]:
                for k in [k for k, r in els.items() if r[0] + r[2] <= 0 or r[0] >= screen[0] or r[1] + r[3] <= 0 or r[1] >= screen[1]]:
                    del els[k]
            for k, r in els.items(): order[k] = r[0]
            frames.append({"t": t, "key": key, "focused": focused if focused in els else None,
                           "focus": list(els[focused]) if focused in els else None,
                           "elements": {k: list(r) for k, r in els.items()}})
            key = None
    return {"screen": screen, "frames": frames, "row": "auto", "limits": {"skip_key_frames": True}}


# touch: below touch_dp (24 dp, WCAG 2.2 "target size minimum") is a FAIL; below touch_note_dp (32) is noted — Compose
# widens a small clickable's touch area to 48 dp where nothing else is under the finger, so its drawn size is not the target.
CLIP_DEFAULTS = dict(clip_px=1.5, overlap_px=2.0, touch_dp=24.0, touch_note_dp=32.0)
# The layouts' own words for the window's actions (GameWindow.kt WinLook: play / details / back) — popclip.sh keeps the same
# lists (PLAYW / DETW; the selftest checks they match).
PLAY_WORDS = ("play", "continue", "resume", "start", "start game", "continue game", "play game", "press start", "power on",
              "run", "play it!", "continue!", "rent & play")
DETAILS_WORDS = ("details", "full details", "information", "info", "game info", "contents", "status", "sleeve notes",
                 "card text", "turn over", "summary", "the story", "type nfo", "catalogue card")
BACK_WORDS = ("back", "home", "put back", "exit", "stop", "close")
ACTIONS = re.compile(r"(?i)^(" + "|".join(re.escape(w) for w in PLAY_WORDS + DETAILS_WORDS + BACK_WORDS) +
                     r"|more|less|ok|cancel|\u22ef|\u2026|\.\.\.)$")
PLAY_RE = re.compile(r"(?i)^(" + "|".join(re.escape(w) for w in PLAY_WORDS) + r")$")


def _label(n):
    # (Labels can start with the button's glyph — "▶ Play", "◧ Inspect" — the word is what counts.)
    lab = (n.get("label") or "?").split(" #")[0].strip()
    stripped = re.sub(r"^[^\w\u22ef\u2026.]+", "", lab).strip()
    # (A key whose picture is described by the same word it shows reads "Play Play" — Airy's round keys; and Storage's
    # pointer column joins in as "▶/PLAY": the word once, after the pointer.)
    stripped = re.sub(r"^[^\w]*/", "", stripped).strip() or stripped
    w = stripped.split(" ")
    # (Update 470: in either case — Orbit's narrow keys read "Play PLAY": the icon's description, then the caps label.)
    lw = [x.lower() for x in w]
    if len(w) % 2 == 0 and lw[:len(w) // 2] == lw[len(w) // 2:]: stripped = " ".join(w[:len(w) // 2])
    return stripped or lab or "?"


def _is_action(n):
    lab = _label(n)
    # ("Play" / "Continue" with more words after them — "Play on TV"; never "Played", a fact.)
    return bool(ACTIONS.match(lab)) or bool(re.match(r"(?i)^(play|continue)(\s|$)", lab))


def _key(n):
    return (_label(n), tuple(n.get("full") or ()))


def clip_evaluate(dump, limits=None, png=None, baseline=None):
    """Results of the clipping gate for a QaClip dump (dict). INFRA without evidence.
    baseline: a dump of the same screen with no popup (popclip's closed step): its nodes — the home screen behind a popup
    drawn in the same window, the status bar — are not the popup's and are left out."""
    home = {_key(n) for w in ((baseline or {}).get("windows") or []) for n in (w.get("nodes") or [])}
    wins = (dump or {}).get("windows") or []
    # (Only the window that has the controller — the popup, a dialog — when one has it; the home screen behind a popup is
    # its own window and not what is being checked.)
    if any(w.get("focus") for w in wins): wins = [w for w in wins if w.get("focus")]
    nodes = [(w, n) for w in wins for n in (w.get("nodes") or []) if _key(n) not in home]
    if not wins or not any(n.get("kind") == "control" for _, n in nodes):
        return [qalib.Result("clip dump", qalib.INFRA, "no windows / no controls in the dump: nothing was measured")]
    lim = dict(CLIP_DEFAULTS); lim.update(limits or {})
    dens = float(dump.get("density") or 1.0)
    sw, sh = (dump.get("screen") or [0, 0])[:2]
    cut, scut, off, ovl, small, foc, near, hidden, runs = [], [], [], [], [], [], [], [], []
    for w, n in nodes:
        f, s_ = n.get("full"), n.get("shown")
        if not f or not s_ or f[2] <= 0 or f[3] <= 0: continue
        lab = _label(n); wx, wy = (w.get("at") or [0, 0])[:2]
        # (Only where the shown part stops INSIDE the node's own box: a focused control drawn a little larger — scaled up —
        # shows more than its box, which is not a cut.)
        miss = max(s_[0] - f[0], s_[1] - f[1], (f[0] + f[2]) - (s_[0] + s_[2]), (f[1] + f[3]) - (s_[1] + s_[3]), 0)
        if s_[2] <= 0 or s_[3] <= 0:
            miss = max(f[2], f[3])  # wholly cut away
        # (A tilted scene — the rental tag — reports straight boxes round turned content: a few % of the width either way.)
        tilt_px = (f[2] * 0.06 + 4) if n.get("tilt") else 0
        if miss > lim["clip_px"] + tilt_px:
            what = f"{lab} ({n.get('kind')}) cut by {miss:g} px: full {f} shown {s_}"
            # (Inside a scrolling list a node may be scrolled out — reachable. An ACTION there fails only when it is partly
            # shown at rest: a button cut across, the Stadium 2 bug; wholly scrolled away it is reachable like the rest.)
            partly = s_[2] > 0 and s_[3] > 0
            # (A paging row / box — whole chips or rows at a time, "+N" for the rest, drawn clipped — never draws a part
            # of a chip: its layout boxes run on past the drawing clip the semantics can't see.)
            if n.get("paged"): scut.append(what)
            elif n.get("scroll") and not (_is_action(n) and partly): scut.append(what)
            # (Text wholly out of view — a paging box showing whole rows in turn, Twin's facts — is not drawn cut; a
            # control wholly pushed out is.)
            elif not partly and n.get("kind") != "control": hidden.append(lab)
            else: cut.append(what)
            if n.get("focused"): foc.append(f"focused {lab} is cut")
        if n.get("kind") == "control" and s_[2] > 0 and s_[3] > 0:
            ax, ay = wx + s_[0], wy + s_[1]
            # (displayMetrics can leave out the navigation bar's strip; the window's own size is the usable screen too.)
            at = w.get("at") or [0, 0, 0, 0]
            bw, bh = max(sw, at[0] + at[2]), max(sh, at[1] + at[3])
            if sw and (ax < -1 or ay < -1 or ax + s_[2] > bw + 1 or ay + s_[3] > bh + 1):
                off.append(f"{lab} leaves the screen: {[ax, ay, s_[2], s_[3]]} on {bw}x{bh}")
            if n.get("focused") and sw and (ax + s_[2] <= 0 or ay + s_[3] <= 0 or ax >= sw or ay >= sh):
                foc.append(f"focused {lab} is off the screen")
            mn = lim["touch_dp"] * dens
            if (f[2] < mn - 0.5 or f[3] < mn - 0.5) and not n.get("scroll"):
                small.append(f"{lab} {f[2]:g}x{f[3]:g} px < {lim['touch_dp']:g} dp ({mn:g} px)")
            elif (f[2] < lim["touch_note_dp"] * dens - 0.5 or f[3] < lim["touch_note_dp"] * dens - 0.5) and not n.get("scroll"):
                near.append(f"{lab} {f[2] / dens:.0f}x{f[3] / dens:.0f} dp")
    # A scrolling box squeezed so small that it shows no whole line (Resume's facts beside a tall key column on the KONKR):
    # its short rows (one or two lines) are all partly shown or hidden, none whole.
    tiny = []
    for w in wins:
        rows = [n for n in (w.get("nodes") or []) if _key(n) not in home and n.get("scroll") and n.get("full") and n.get("shown")
                and n["full"][3] <= 48 * dens and n["full"][3] > 0]
        whole = [n for n in rows if n["shown"][3] >= n["full"][3] - lim["clip_px"] and n["shown"][2] > 0]
        part = [n for n in rows if 0 < n["shown"][3] < n["full"][3] - lim["clip_px"] and n["shown"][2] > 0]
        # (Only when the box itself is that small: the tallest thing it shows is under one of its rows — a long story
        # scrolling above a facts line, Cinema's, is a normal list.)
        seen = max([n["shown"][3] for n in (w.get("nodes") or []) if _key(n) not in home and n.get("scroll") and n.get("shown")] or [0])
        if part and not whole and seen < min(n["full"][3] for n in part):
            tiny.append(f"a scrolling box shows only part of a line ({', '.join(_label(n) for n in part[:3])}: "
                        f"{max(n['shown'][3] for n in part):g} of {part[0]['full'][3]:g} px) and no whole one")
    cut.extend(tiny)
    for w in wins:
        ctl = [n for n in (w.get("nodes") or []) if _key(n) not in home and n.get("kind") == "control" and n.get("shown") and n["shown"][2] > 0 and n["shown"][3] > 0]
        for i in range(len(ctl)):
            for j in range(i + 1, len(ctl)):
                a, b = ctl[i]["shown"], ctl[j]["shown"]
                ix = min(a[0] + a[2], b[0] + b[2]) - max(a[0], b[0]); iy = min(a[1] + a[3], b[1] + b[3]) - max(a[1], b[1])
                tol = lim["overlap_px"] + ((max(a[2], b[2]) * 0.1) if (ctl[i].get("tilt") and ctl[j].get("tilt")) else 0)
                if ix <= lim["overlap_px"] or iy <= tol: continue
                inside = lambda p, q: p[0] >= q[0] - 1 and p[1] >= q[1] - 1 and p[0] + p[2] <= q[0] + q[2] + 1 and p[1] + p[3] <= q[1] + q[3] + 1
                if inside(a, b) or inside(b, a): continue
                ovl.append(f"{_label(ctl[i])} and {_label(ctl[j])} overlap {ix:g}x{iy:g} px")
        # (Words drawn under / over a button that aren't its own: a text node crossing a control's box without being inside it.)
        txt = [n for n in (w.get("nodes") or []) if _key(n) not in home and n.get("kind") == "text" and n.get("shown") and n["shown"][2] > 0 and n["shown"][3] > 0]
        for t in txt:
            a = t["shown"]
            for c in ctl:
                b = c["shown"]
                ix = min(a[0] + a[2], b[0] + b[2]) - max(a[0], b[0]); iy = min(a[1] + a[3], b[1] + b[3]) - max(a[1], b[1])
                if ix <= lim["overlap_px"] or iy <= lim["overlap_px"]: continue
                if a[0] >= b[0] - 1 and a[1] >= b[1] - 1 and a[0] + a[2] <= b[0] + b[2] + 1 and a[1] + a[3] <= b[1] + b[3] + 1: continue
                if b[0] >= a[0] - 1 and b[1] >= a[1] - 1 and b[0] + b[2] <= a[0] + a[2] + 1 and b[1] + b[3] <= a[1] + a[3] + 1: continue
                runs.append(f"{_label(t)[:30]} / {_label(c)} {ix:g}x{iy:g} px")
    # (Update 471, owner review of the 470 PDF: Cover details with More open showed "MADE BY" / "GENRE" and their values were
    # scrolled away under the keys — every action whole, so the gate passed while the facts were missing. A fact's label
    # shown (in capitals, the facts' style) with the value right under it not shown is a FAIL: information lost at rest.)
    facts = []
    txtn = [(w, n) for w, n in nodes if n.get("kind") == "text" and n.get("full") and n.get("shown")]
    for w, lab_n in txtn:
        lt = _label(lab_n); lf, ls = lab_n["full"], lab_n["shown"]
        LAB = r"[A-Z][A-Z .&/'-]{1,21}"
        if not re.fullmatch(LAB, lt) or ls[3] < lf[3] * 0.5: continue
        # (A label with its value beside it on the same line — the Box sheet's "KIND ....... Simulator" — is not this kind.)
        if any(w2 is w and v is not lab_n and abs(v["full"][1] - lf[1]) <= 8 and v["full"][0] >= lf[0] + lf[2] - 2 for w2, v in txtn): continue
        under = [v for w2, v in txtn if w2 is w and v is not lab_n and abs(v["full"][0] - lf[0]) <= 6
                 and lf[1] + lf[3] - 8 <= v["full"][1] <= lf[1] + lf[3] + 12 and not re.fullmatch(LAB, _label(v))]
        if not under: continue
        v = min(under, key=lambda x: x["full"][1])
        if v["shown"][3] < v["full"][3] * 0.6:
            facts.append(f"{lt} shown, its value '{_label(v)[:30]}' {'hidden' if v['shown'][3] <= 0 else 'cut (%d of %d px)' % (v['shown'][3], v['full'][3])}")
    out = []
    def res(name, probs, ok, status=qalib.FAIL):
        out.append(qalib.Result(name, status if probs else qalib.PASS,
                                ("; ".join(probs[:6]) + (f" (+{len(probs) - 6} more)" if len(probs) > 6 else "")) if probs else ok))
    res("clip", cut, "no control or text is cut by its parents")
    # (Partly scrolled out of a scrolling list: reachable by scrolling — noted, not a failure; window actions never count here.)
    if hidden:
        out.append(qalib.Result("hidden-text", qalib.PASS, f"{len(hidden)} text node(s) wholly out of view (paged / folded, not drawn cut): " + ", ".join(hidden[:5])))
    out.append(qalib.Result("scroll-clip", qalib.PASS, (f"{len(scut)} node(s) partly scrolled out of their list (reachable): " + "; ".join(scut[:3])) if scut else "nothing inside a scrolling list is cut"))
    res("facts", facts, "every fact label shown has its value in view")
    res("screen", off, "every control inside the screen")
    res("overlap", ovl, "no overlapping controls")
    # (Text boxes crossing a button: noted, not failed — a text box can be taller than its drawn lines, e.g. a clipped last
    # line; the screenshot shows whether words really run under a button.)
    if runs:
        out.append(qalib.Result("text-near-controls", qalib.PASS, f"{len(runs)} text box(es) cross a control's box (check the screenshot): " + "; ".join(runs[:3])))
    res("touch", small, f"every control at least {lim['touch_dp']:g} dp" + (f"; under {lim['touch_note_dp']:g} dp (touch widened to 48 dp by Compose): " + ", ".join(near[:4]) if near else ""))
    res("focus", foc, "the focused control is whole and on screen")
    if png:
        out.append(pixel_check(dump, png, home))
    else:
        out.append(qalib.Result("pixels", qalib.NOT_TESTED, "no screenshot given (--png): drawn clipping not checked"))
    return out


def pixel_check(dump, png, home=frozenset()):
    """For each window action button: where its own fill actually ends. Read upward from the button's middle row, the fill
    should taper into a rounded end (its last rows narrow) at its laid-out bottom; a fill that stops in a straight full-width
    edge, or well short of the laid-out bottom, was cut by something the layout doesn't report. Heuristic — never claims
    full visual coverage."""
    try:
        from PIL import Image
        im = Image.open(png).convert("RGB")
    except Exception as e:
        return qalib.Result("pixels", qalib.INFRA, f"cannot read screenshot {png}: {e}")
    W, H = im.size; px = im.load()
    d = lambda a, b: sum(abs(a[k] - b[k]) for k in range(3))
    bad, n_ok = [], 0
    for w in dump.get("windows") or []:
        wx, wy = (w.get("at") or [0, 0])[:2]
        for n in w.get("nodes") or []:
            if n.get("kind") != "control" or not _is_action(n) or _key(n) in home: continue
            f, sh_ = n.get("full"), n.get("shown")
            if not f or f[3] < 12 or f[2] < 12: continue
            if sh_ and (sh_[2] <= 0 or sh_[3] <= 0): continue  # wholly scrolled away: nothing drawn to read (clip judges it)
            x0, x1 = int(wx + f[0] + f[2] * 0.08), int(wx + f[0] + f[2] * 0.92)
            y0, y1 = int(wy + f[1]), int(wy + f[1] + f[3])
            if y1 > H or x1 > W or x0 < 0 or y0 < 0:
                bad.append(f"{_label(n)} lies outside the screenshot"); continue
            # (A cut is one row where nearly the whole width changes at once — fill above, backdrop below — well above
            # the laid-out bottom; a rounded end changes a few columns per row, and text never spans the width.)
            ym = (y0 + y1) // 2
            # (Update 470: the button's own fill, read at its two side columns in its upper half, where a label rarely is. A
            # line where wide text ends — ARCADE's PRESS START in a pixel font, flat-bottomed letters across 80 % of the bar —
            # changes the whole width too, but what lies below it is still that fill; below a real cut lies something else.)
            # (Below its rounded corners; a colour also found just outside the button is backdrop, never fill.)
            out_c = [px[x, min(H - 1, y1 + 1)] for x in range(x0, x1, max(1, (x1 - x0) // 12))]
            fill = [c for c in (px[x, y] for x in (x0, x1 - 1) for y in range(y0 + max(2, int(f[3] * 0.3)), ym))
                    if all(d(c, o) > 60 for o in out_c)]
            def fill_like(y):
                # (Every shade the fill shows — a tube's scan lines alternate 250 / 210 rows on the Odin, a change across the
                # whole width at each line.)
                return sum(1 for x in range(x0, x1) if any(d(px[x, y], c) <= 40 for c in refs)) / max(1, x1 - x0) > 0.5
            refs = list({(c[0] // 8 * 8 + 4, c[1] // 8 * 8 + 4, c[2] // 8 * 8 + 4) for c in fill})
            cut_at = None
            for y in range(ym, y1 - 1):
                ch = sum(1 for x in range(x0, x1) if d(px[x, y], px[x, y + 1]) > 80) / max(1, x1 - x0)
                if ch > 0.75 and (y1 - 1 - y) > max(4, f[3] * 0.2):
                    if fill and fill_like(y + 1): continue
                    cut_at = y; break
            if cut_at is not None:
                bad.append(f"{_label(n)}: drawn edge cut straight across {y1 - 1 - cut_at} px above its laid-out bottom")
            else:
                n_ok += 1
    if not bad and not n_ok:
        return qalib.Result("pixels", qalib.NOT_TESTED, "no window action buttons to compare")
    return qalib.Result("pixels", qalib.FAIL if bad else qalib.PASS, "; ".join(bad) if bad else f"{n_ok} action button(s) drawn whole (heuristic)")


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    path = sys.argv[1]
    if "--clip" in sys.argv:
        jp = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
        png = sys.argv[sys.argv.index("--png") + 1] if "--png" in sys.argv else None
        try:
            dump = json.load(open(path, encoding="utf-8"))
        except (OSError, ValueError) as e:
            qalib.finish([qalib.Result("clip dump", qalib.INFRA, f"cannot read {path}: {e}")], "Clipping check", jp)
        qalib.finish(clip_evaluate(dump, png=png), "Clipping check", jp)
    try:
        if "--logcat" in sys.argv:
            trace = from_logcat(open(path, encoding="utf-8", errors="replace").read())
            if "--dump" in sys.argv: json.dump(trace, open(sys.argv[sys.argv.index("--dump") + 1], "w"))
        else:
            trace = json.load(open(path, encoding="utf-8"))
    except (OSError, ValueError) as e:
        qalib.finish([qalib.Result("geometry trace", qalib.INFRA, f"cannot read {path}: {e}")], "Geometry check")
    jp = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    qalib.finish(evaluate(trace), "Geometry check", jp)


if __name__ == "__main__":
    main()

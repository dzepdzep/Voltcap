#!/usr/bin/env python3
"""Update 474 — home-screen navigation checks (QA tier T5) on a device/navclip.sh run: per layout, steps s0 (start, game A),
s1, s2 (moved on: B, C), s3, s4 (moved back: B, A), each a settled screenshot + QaClip dump, plus an early screenshot (sNe).

  move      the focused game (the app's own focus) changed with every key, and came back to A at s4   (else FAIL: stuck / lost)
  jump      A's title and the focused card's box at s4 are where they were at s0 (same game, same place)        (FAIL > 2 dp)
  steady    texts and controls that are on screen at s0 and s4 and belong to no game (status bar, headers, keys) did
            not move between s0 and s4                                                                           (FAIL > 2 dp)
  art       the focused card's picture at s4 matches s0 (same game must show the same picture: stale / wrong art)
            and differs from s1's (a new game must not keep the old picture)                                     (FAIL)
  late      the early screenshot (~0.15-0.4 s after the key) already shows the card's final picture; if not, how
            late it was, Coil's own load times for the step, and pictures upscaled > 2x (low-res source)          (WARN only)
Per-layout expectations (tools/qa/nav_expect.json): "moves": a carousel whose card / title glides to a new place by design
(jump is measured against s0 only for the same game, so a carousel still has to come back), "animated_art": the focused
picture plays (clip / animation): the art check compares with a looser threshold, "skip": layout not walkable (reason).
Missing evidence (no manifest, unknown build, stale files: evidence.py) = INFRA, never PASS.
  python3 navclip.py RUN_DIR [--json OUT] [--md OUT] [--ledger builds.json] [--legacy]
"""
import glob, json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
import evidence

HERE = os.path.dirname(os.path.abspath(__file__))
EXPECT = os.path.join(os.path.dirname(HERE), "nav_expect.json")
TOL_DP = 2.0


def load(run, name):
    tag = os.path.basename(os.path.normpath(run))
    for p in (os.path.join(run, "qa_clip", f"{tag}_{name}.json"), os.path.join(run, "qa_clip", f"{name}.json")):
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding="utf-8"))
            except ValueError:
                return None
    return None


def open_shot(p):
    """A screenshot: PNG, or the raw `screencap` format (the early shots: no PNG encoding, ~0.6 s sooner) — a header of
    width, height, format (+ colour space on Android 10+) as 32-bit ints, then RGBA rows."""
    from PIL import Image
    if p.endswith(".raw"):
        b = open(p, "rb").read()
        if len(b) < 16: return None
        import struct
        w, h = struct.unpack("<II", b[:8])
        for hdr in (16, 12):
            if len(b) - hdr == w * h * 4:
                return Image.frombytes("RGBA", (w, h), b[hdr:hdr + w * h * 4]).convert("RGB")
        return None
    return Image.open(p)


def crop_sig(png, box, scale=24):
    """A small grey thumbnail of box (x, y, w, h) in png — None if the box is off the picture or too small."""
    if not png or not os.path.exists(png) or not box or box[2] < 8 or box[3] < 8:
        return None
    im = open_shot(png)
    if im is None: return None
    im = im.convert("L")
    x, y, w, h = [int(v) for v in box]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(im.width, x + w), min(im.height, y + h)
    if x1 - x0 < 8 or y1 - y0 < 8:
        return None
    c = im.crop((x0, y0, x1, y1)).resize((scale, scale))
    return list(c.get_flattened_data() if hasattr(c, "get_flattened_data") else c.getdata())


def diff(a, b):
    """Mean absolute grey difference 0..255 of two thumbnails."""
    return sum(abs(p - q) for p, q in zip(a, b)) / len(a)


def spread(a):
    m = sum(a) / len(a)
    return (sum((p - m) ** 2 for p in a) / len(a)) ** 0.5


def texts(d):
    return [t for w in (d or {}).get("windows") or [] for t in w.get("texts") or []]


def grade_layout(run, lay, exp):
    sp = os.path.join(run, "steps.txt")
    if os.path.exists(sp) and re.search(rf"^{re.escape(lay)} mark=PICKER", open(sp, encoding="utf-8", errors="replace").read(), re.M):
        return {k: (qalib.INFRA, "the walk began on the profile picker (Who's playing?): its keys never reached the home screen") for k in ("move", "jump", "steady", "art", "late")}
    d = {i: load(run, f"{lay}_s{i}") for i in range(5)}
    png = {i: os.path.join(run, f"{lay}_s{i}.png") for i in range(5)}
    early = {i: next((p for p in (os.path.join(run, f"{lay}_s{i}e.raw"), os.path.join(run, f"{lay}_s{i}e.png")) if os.path.exists(p)),
                     os.path.join(run, f"{lay}_s{i}e.png")) for i in range(1, 5)}
    out = {}
    if exp.get("skip"):
        return {k: (qalib.SKIP, "n/a: " + exp["skip"]) for k in ("move", "jump", "steady", "art", "late")}
    if any(d[i] is None or not os.path.exists(png[i]) for i in range(5)):
        return {k: (qalib.INFRA, "a step's dump or screenshot is missing") for k in ("move", "jump", "steady", "art", "late")}
    dens = float(d[0].get("density") or 1.0); tol = TOL_DP * dens
    f = [d[i].get("focusedGame") for i in range(5)]
    if None in f:
        return {k: (qalib.INFRA, "the app reported no focused game (build before update 474?)") for k in ("move", "jump", "steady", "art", "late")}
    moved = f[1] != f[0] and f[2] != f[1] and f[3] != f[2] and f[4] == f[0]
    if f[1] == f[0]:
        out["move"] = (qalib.FAIL, "the first key changed nothing (stuck focus) — or a layout of one game")
    elif not moved:
        out["move"] = (qalib.FAIL, f"focus did not come back: {[os.path.basename(x) for x in f]}")
    else:
        out["move"] = (qalib.PASS, "every key moved the focus and it came back to the first game")
    # jump: the same game's own title text (its name, as drawn) and the focused card at s0 vs s4.
    o0, o4 = d[0].get("origin"), d[4].get("origin")
    probs = []
    if o0 and o4 and max(abs(a - b) for a, b in zip(o0, o4)) > tol:
        probs.append(f"focused card {o0} → {o4}")
    t0 = {t["text"]: t["full"] for t in texts(d[0]) if t.get("full") and t["full"][3] > 0}
    t4 = {t["text"]: t["full"] for t in texts(d[4]) if t.get("full") and t["full"][3] > 0}
    name = (d[0].get("popupName") or "")
    same = [k for k in t0 if k in t4]
    moved_txt = [f"'{k[:30]}' {t0[k]} → {t4[k]}" for k in same if max(abs(a - b) for a, b in zip(t0[k], t4[k])) > tol]
    if not t0:
        out["jump"] = (qalib.NOT_TESTED, "no text layout in the dump (build before update 474)")
    elif probs or moved_txt:
        out["jump"] = (qalib.FAIL, "back on the same game, things are elsewhere: " + "; ".join((probs + moved_txt)[:5]))
    else:
        out["jump"] = (qalib.PASS, f"same game, same places ({len(same)} texts{' + the card' if o0 else ''})")
    # steady: texts on screen at every step whose words don't change (headers, keys, the clock's minute aside).
    # (Only texts that occur once per step: a word on several cards — "PLAYING" on every running app's card — is the row's.)
    common = [k for k in same if all(sum(1 for t in texts(d[i]) if t["text"] == k) == 1 for i in range(5))]
    shifts = []
    def inside(b, o):
        return o and b[0] >= o[0] - tol and b[1] >= o[1] - tol and b[0] + b[2] <= o[0] + o[2] + tol and b[1] + b[3] <= o[1] + o[3] + tol
    for k in common:
        boxes = [next(t["full"] for t in texts(d[i]) if t["text"] == k) for i in range(5)]
        # (A label drawn ON the focused card — Voltcap row's "PLAYING" chip on the Odin — goes with the card: not a fixed text.)
        if all(inside(boxes[i], d[i].get("origin")) for i in range(5)):
            continue
        if max(abs(boxes[i][j] - boxes[0][j]) for i in range(5) for j in range(4)) > tol and not exp.get("moves") \
                and k not in exp.get("moving_texts", []):
            shifts.append(f"'{k[:24]}' moves between games")
    out["steady"] = (qalib.FAIL, "; ".join(shifts[:5])) if shifts else (qalib.PASS, f"{len(common)} fixed texts stay put")
    # art: crops of the focused card.
    if f[1] == f[0]:
        out["art"] = (qalib.NOT_TESTED, "the focus never moved: no other game's picture to compare")
    elif not o0 or not o4:
        out["art"] = (qalib.NOT_TESTED, "the layout marks no focused card (GameOrigin): its picture can't be compared")
    else:
        a0, a4 = crop_sig(png[0], o0), crop_sig(png[4], o4)
        # (The same picture drawn a few px elsewhere — a case pulled out with a sway / tilt, Shelf in k474m — is the same
        # picture: the best match within ±4 % of the box counts.)
        if a0 is not None and a4 is not None:
            for dx in (-0.04, 0, 0.04):
                for dy in (-0.04, 0, 0.04):
                    sh = crop_sig(png[4], [o4[0] + o4[2] * dx, o4[1] + o4[3] * dy, o4[2], o4[3]])
                    if sh is not None and diff(a0, sh) < diff(a0, a4):
                        a4 = sh
        a1 = crop_sig(png[1], d[1].get("origin")) if d[1].get("origin") else None
        lim_same = 28 if exp.get("animated_art") else 14
        if a0 is None or a4 is None:
            out["art"] = (qalib.NOT_TESTED, "the focused card is off the screenshot")
        # (Relative as well as absolute: a lit / swaying case (Shelf) differs from itself by ~20 / 255 but from the next game's
        # box by ~3x that. Same game fails only when it is both over the layout's limit and not clearly closer to itself than
        # to the neighbour.)
        elif diff(a0, a4) > lim_same and (a1 is None or diff(a0, a4) > 0.5 * diff(a0, a1)):
            out["art"] = (qalib.FAIL, f"same game, another picture (difference {diff(a0, a4):.0f}/255): stale or wrong art")
        elif a1 is not None and diff(a0, a1) < 3 and spread(a0) > 6:
            out["art"] = (qalib.FAIL, f"the next game kept the same picture (difference {diff(a0, a1):.1f}/255): stale art")
        else:
            out["art"] = (qalib.PASS, f"same game same picture ({diff(a0, a4):.0f}), next game another ({diff(a0, a1):.0f})" if a1 else
                          f"same game same picture ({diff(a0, a4):.0f})")
    # late: early vs settled crop of the new focused card (WARN only — timing on a shared device is not a pass / fail matter).
    notes = []
    steps = {}
    sp = os.path.join(run, "steps.txt")
    if os.path.exists(sp):
        for line in open(sp, encoding="utf-8", errors="replace"):
            m = re.match(rf"{re.escape(lay)} s(\d) key=\S+ early_ms=(\d+)", line)
            if m: steps[int(m.group(1))] = int(m.group(2))
    for i in range(1, 5):
        o = d[i].get("origin")
        if not o or not os.path.exists(early[i]): continue
        e, s = crop_sig(early[i], o), crop_sig(png[i], o)
        if e is None or s is None: continue
        if diff(e, s) > 22:
            notes.append(f"s{i}: picture not final after ~{steps.get(i, 150)} ms (difference {diff(e, s):.0f})")
    slow = [im for i in range(1, 5) for im in (d[i].get("images") or []) if im.get("ms", 0) > 400 and im.get("ago", 99999) < 3000]
    if slow:
        notes.append(f"{len(slow)} picture(s) took > 400 ms to load (max {max(im['ms'] for im in slow)} ms)")
    # (Low-resolution source: decoded much smaller than the box it is drawn into — shown blurry. Coil decodes down to the
    # box, never up, so got < want / 2 on both sides means the picture itself is that small. A warning: the art is the user's.)
    lowres = {im.get("tok") for i in range(5) for im in (d[i].get("images") or [])
              if im.get("ok") and min(im.get("want") or [-1]) > 0 and min(im.get("got") or [-1]) > 0
              and im["got"][0] * 2 < im["want"][0] and im["got"][1] * 2 < im["want"][1]}
    if lowres:
        notes.append(f"{len(lowres)} picture(s) shown at over 2x their own size (low-res source)")
    out["late"] = (qalib.PASS, ("WARN: " + "; ".join(notes[:4])) if notes else "pictures final at the early screenshot")
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    run = sys.argv[1]
    exp_all = json.load(open(EXPECT, encoding="utf-8")).get("layouts", {}) if os.path.exists(EXPECT) else {}
    lays = sorted({re.sub(r"_s0$", "", os.path.basename(p)[:-4]) for p in glob.glob(os.path.join(run, "*_s0.png"))})
    if not lays:
        qalib.finish([qalib.Result("nav sweep", qalib.INFRA, f"no *_s0.png in {run}")], "Navigation")
    led = sys.argv[sys.argv.index("--ledger") + 1] if "--ledger" in sys.argv else evidence.LEDGER
    ev_st, ev_info, ev_why = evidence.check_run(run, led)
    results, rows = [], []
    graded = {lay: grade_layout(run, lay, exp_all.get(lay, {})) for lay in lays}
    # (The focus never moved in ANY layout of the run: the keys never arrived — k474n sent KEYCODE_RIGHT, which is no key, and
    # only a background clip changed the screen. A broken run, INFRA — a stuck layout shows as one layout among moving ones.)
    stuck = [l for l, g in graded.items() if g["move"][0] == qalib.FAIL and "first key changed nothing" in g["move"][1]]
    if len(lays) > 1 and len(stuck) == len(lays):
        graded = {l: {k: (qalib.INFRA, "no key moved the focus in any layout of this run: keys not delivered (broken run)") for k in g}
                  for l, g in graded.items()}
    for lay in lays:
        g = graded[lay]
        if ev_st != qalib.PASS:
            g = {k: ((qalib.NOT_TESTED, "legacy: " + v[1]) if v[0] == qalib.PASS else v) if "--legacy" in sys.argv
                 else (qalib.INFRA, "evidence: " + ev_why) for k, v in g.items()}
        ov = qalib.verdict([qalib.Result(k, v[0], v[1]) for k, v in g.items()])
        rows.append((lay, ov, g))
        results.append(qalib.Result(lay, ov, "; ".join(f"{k}={v[0]}: {v[1]}" for k, v in g.items() if v[0] != qalib.PASS or v[1].startswith("WARN")) or "all PASS"))
    print("Evidence: " + (evidence.label(ev_info) if ev_st == qalib.PASS else f"{ev_st}: {ev_why}"))
    print(qalib.report_text(results, "Navigation"))
    if "--md" in sys.argv:
        cats = ("move", "jump", "steady", "art", "late")
        with open(sys.argv[sys.argv.index("--md") + 1], "w", encoding="utf-8") as fh:
            fh.write("| layout | overall | " + " | ".join(cats) + " |\n|" + "---|" * (len(cats) + 2) + "\n")
            for lay, ov, g in rows:
                fh.write(f"| {lay} | {ov} | " + " | ".join(g[c][0] + (" (warn)" if g[c][1].startswith("WARN") else "") for c in cats) + " |\n")
    if "--json" in sys.argv:
        qalib.write_json(sys.argv[sys.argv.index("--json") + 1], results, {"evidence": {"status": ev_st, "reason": ev_why}})
    sys.exit(qalib.EXIT[qalib.verdict([r for r in results if r.status != qalib.SKIP])])


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""676a: badge collision gate (QA rule R27) - console / achievement / NEW / score badges on a game's picture, from their
ACTUAL laid-out bounds in the QaClip dumps (ui/QaClip.kt "badges" + "badgeTiles", written by ui/BadgeLane.kt).

usage: python3 tools/qa/tools/badgecheck.py DUMP.json|RUN_DIR [...] [--gap-dp 6] [--json out.json]
Per tile (one badge lane) on every dump:
  FAIL  two badges of one tile closer than --gap-dp (6 dp: their drawn pills; overlap counts as a negative gap)
  FAIL  a badge cut by its tile (its shown box smaller than its laid-out box, beyond 1 px), or off the screen
  FAIL  a text inside a badge that its own layout reports cut (overflow / ellipsis - the "texts" list; RP Nova x1.3 Grid,
        16 Nov: "6/10 · 60%" clipped inside its pill while the pill itself fitted)
  PASS  every pair clear and every badge whole
A dump without the "badges" key is from a build before the lane: INFRA (never a pass). Dumps with no tile carrying two
badges are NOT TESTED for collisions (nothing to collide) - an all-NOT-TESTED run is NOT TESTED, never PASS.
Tiles that are off the screen (not shown at all) are skipped. Exit code: qalib (0 = PASS only).
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib  # noqa: E402

TOL = 1  # px: rounding of positions / sizes


def rect(a):
    x, y, w, h = a
    return (x, y, x + w, y + h)


def gap(a, b):
    """The clear space between two rects (px); negative when they overlap (the larger of the two overlaps)."""
    ax0, ay0, ax1, ay1 = a
    bx0, by0, bx1, by1 = b
    dx = max(bx0 - ax1, ax0 - bx1)
    dy = max(by0 - ay1, ay0 - by1)
    if dx < 0 and dy < 0:
        return max(dx, dy)  # overlapping: how far
    return max(dx, dy)


def check_dump(path, gap_dp=6.0):
    name = os.path.basename(path)
    try:
        d = json.load(open(path, encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        return [qalib.Result(name, qalib.INFRA, f"unreadable dump: {e}")]
    dens = float(d.get("density") or 0)
    if dens <= 0:
        return [qalib.Result(name, qalib.INFRA, "no density in the dump")]
    sw, sh = (d.get("screen") or [0, 0])[:2]
    out = []
    seen_lane_key = False
    for w in d.get("windows", []):
        if "badges" not in w:
            return [qalib.Result(name, qalib.INFRA, "dump has no badge bounds (build before 676a's badge lane)")]
        seen_lane_key = True
        tiles = {t["lane"]: t for t in w.get("badgeTiles", [])}
        lanes = {}
        for b in w["badges"]:
            if not b.get("placed", True):
                continue
            lanes.setdefault(b["lane"], []).append(b)
        for lane, bs in lanes.items():
            t = tiles.get(lane)
            if t is not None and (t["shown"][2] <= 0 or t["shown"][3] <= 0):
                continue  # the tile is off the screen
            tag = f"{name} tile {lane}"
            bad, infra = [], []
            for b in bs:
                f, s = b["full"], b["shown"]
                if s[2] <= 0 or s[3] <= 0:
                    continue  # a badge not on the screen at all (scrolled away with its tile)
                # (f676x13, RP Nova: "full" is the laid-out size, "shown" what is on screen after the tile's own scale - an
                # unfocused tile drawn at 0.81 read as 44 "cut" badges. The same shrink both ways is the tile's scale: the badge
                # is measured at that scale; only an uneven shrink - one side trimmed - is a cut.)
                rw, rh = s[2] / max(1, f[2]), s[3] / max(1, f[3])
                if abs(rw - rh) <= 0.03 or (f[2] - s[2] <= TOL and f[3] - s[3] <= TOL):
                    r = min(1.0, (rw + rh) / 2)
                    b["_t"] = [f[0], f[1], round(f[2] * r), round(f[3] * r)]
                else:
                    b["_t"] = f
                    bad.append(f"{b['kind']} cut: laid out {f[2]}x{f[3]} px, shown {s[2]}x{s[3]}")
                x0, y0, x1, y1 = rect(b["_t"])
                if sw and sh and (x0 < -TOL or y0 < -TOL or x1 > sw + TOL or y1 > sh + TOL):
                    bad.append(f"{b['kind']} off the screen at {f}")
            for b in bs:
                x0, y0, x1, y1 = rect(b["full"])
                for t in w.get("texts", []):
                    if t.get("placed", True) is False:
                        continue  # (f676x13 Twin icons: a badge the lane left out is not drawn - its text can't be cut)
                    tx, ty, tw, th = t["full"]
                    cx, cy = tx + tw / 2, ty + th / 2
                    if not (x0 <= cx <= x1 and y0 <= cy <= y1):
                        continue
                    # The real shortfall (needW / boxW, textH / boxH), not didOverflow*: those also say yes for a 1 px rounding at
                    # scaled text (the Nova's whole "6/10" read as cut). A dump without needW (before 676a) can't tell: INFRA.
                    if t.get("ellipsis"):
                        bad.append(f"{b['kind']} text cut: {t.get('text', '')!r} ends in an ellipsis")
                    elif "needW" in t and t["needW"] - t["boxW"] > TOL:
                        bad.append(f"{b['kind']} text cut: {t.get('text', '')!r} needs {t['needW']} px, has {t['boxW']}")
                    elif "textH" in t and t["textH"] - t["boxH"] > TOL:
                        bad.append(f"{b['kind']} text cut: {t.get('text', '')!r} needs {t['textH']} px tall, has {t['boxH']}")
                    elif "needW" not in t and t.get("lines") == 1 and (t.get("ovW") or t.get("ovH")):
                        infra.append(f"{b['kind']} text {t.get('text', '')!r}: layout says overflow, no widths in this dump (before 676a)")
            need = gap_dp * dens - TOL
            for i in range(len(bs)):
                for j in range(i + 1, len(bs)):
                    g = gap(rect(bs[i].get("_t", bs[i]["full"])), rect(bs[j].get("_t", bs[j]["full"])))
                    if g < need:
                        what = "overlap" if g < 0 else "too close"
                        bad.append(f"{bs[i]['kind']} / {bs[j]['kind']} {what}: {g / dens:.1f} dp apart (min {gap_dp:g} dp)")
            if len(bs) < 2 and not bad:
                continue
            out.append(qalib.Result(tag, qalib.FAIL if bad else (qalib.INFRA if infra else qalib.PASS), "; ".join(bad or infra),
                                    evidence=", ".join(f"{b['kind']}@{b['full']}" for b in bs)))
    if not seen_lane_key:
        return [qalib.Result(name, qalib.INFRA, "dump has no windows")]
    if not out:
        out.append(qalib.Result(name, qalib.NOT_TESTED, "no tile here carries two badges"))
    return out


def main(argv):
    gap_dp, json_out, paths = 6.0, None, []
    it = iter(argv)
    for a in it:
        if a == "--gap-dp":
            gap_dp = float(next(it))
        elif a == "--json":
            json_out = next(it)
        else:
            paths.append(a)
    files = []
    for p in paths:
        files += sorted(glob.glob(os.path.join(p, "**", "*.json"), recursive=True)) if os.path.isdir(p) else [p]
    files = [f for f in files if os.path.basename(f) not in ("ledger.json",)]
    if not files:
        print("badgecheck: no dumps given - NOT TESTED")
        return qalib.EXIT[qalib.NOT_TESTED]
    results = []
    for f in files:
        results += check_dump(f, gap_dp)
    # A dump with nothing to collide doesn't hide another dump's PASS / FAIL; the run is NOT TESTED only if all are.
    real = [r for r in results if r.status != qalib.NOT_TESTED]
    return qalib.finish(real if real else results, "Badge collision gate", json_out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

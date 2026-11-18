"""682e (owner 18 Nov, a system rule): where a home layout shows its primary action row (Play) and the category chip row at the
same time, they share one visual anchor - the selected chip's left edge on the Play button's left edge (ui/HomeAnchor.kt).

usage: python3 tools/qa/tools/anchorcheck.py RUN_DIR|DUMP.json [...] [--tol DP] [--json out.json]
Per home dump (QaClip; no game window open), the marked edges (ui/StatusQa.kt HomeAnchorTag - QaClip "anchors"):
  PASS        the selected chip's left edge within TOL dp (default 2) of Play's left edge
  FAIL        both on screen and further apart than TOL - prints the gap in dp (+ = the chip to the right of Play)
  NOT TESTED  Play or the selected chip not on screen (the rule does not apply: no Play row, no chip row, or the selected chip
              off-screen) - listed with what was missing
  INFRA       a dump without the "anchors" key (a build before 682e), or no focused window
Also prints, for review by eye, dumps where a "Play" text and category names show but no anchor is marked (a layout drawing
its own Play / chips - not covered by the shared rule). Exit code: qalib (0 = PASS only).
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib  # noqa: E402


def visible(b):
    if b.get("placed", True) is False:
        return False
    s = b.get("shown") or [0, 0, 0, 0]
    return s[2] > 0 and s[3] > 0


PINNED = False   # --pinned: the 682e rule (the selected chip on Play's edge, 0 dp), for before / after of that rule


def check_dump(path, tol_dp=2.0):
    name = os.path.basename(path).replace(".json", "")
    try:
        d = json.load(open(path, encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        return [qalib.Result(name, qalib.INFRA, f"unreadable dump: {e}")], None
    if d.get("popupGame"):
        return [], None  # (a game window is open: not a home dump)
    dens = float(d.get("density") or 1.0)
    wins = [w for w in d.get("windows", []) if w.get("focus")]
    if not wins:
        return [qalib.Result(name, qalib.INFRA, "no focused window in the dump")], None
    w = wins[0]
    if "anchors" not in w:
        return [qalib.Result(name, qalib.INFRA, "dump has no anchors (a build before 682e)")], None
    lay = d.get("homeLayout") or "?"
    a = [x for x in w["anchors"] if visible(x)]
    play = [x for x in a if x.get("what") == "play"]
    chip = [x for x in a if x.get("what") == "chip"]
    texts = [t.get("text", "") for t in w.get("texts", []) if (t.get("shown") or [0, 0, 0, 0])[2] > 0]
    unmarked = None
    if not play and not chip and "Play" in texts and any(t in texts for t in ("All", "Continue playing", "Favorites", "Recently added")):
        unmarked = f"{lay}: Play and category names on screen, no anchor marked (own Play / chips) - review by eye"
    if not play or not chip:
        miss = ", ".join(x for x, ok in (("Play", play), ("selected chip", chip)) if not ok)
        return [qalib.Result(f"{lay} {name}", qalib.NOT_TESTED, f"not on screen: {miss}")], unmarked
    px, cx = play[0]["shown"][0], chip[0]["shown"][0]
    gap = (cx - px) / dens
    if PINNED:
        ok = abs(gap) <= tol_dp
        why = f"selected chip {gap:+.1f} dp from Play's left edge"
    else:
        # 682m (owner 18 Nov 14:54): the strip starts on Play's edge and scrolls naturally - the chip you're on is not pinned
        # there; it must be whole and never left of the edge (the strip's start). Neighbours: by eye on the screenshots.
        full, shown = chip[0]["full"], chip[0]["shown"]
        whole = shown[2] >= full[2] - 1 and shown[3] >= full[3] - 1
        ok = whole and gap >= -tol_dp
        why = (f"selected chip {gap:+.1f} dp right of Play's left edge, whole" if ok else
               (f"selected chip cut ({shown[2]} of {full[2]} px shown)" if not whole else f"selected chip {gap:+.1f} dp LEFT of Play's edge"))
    return [qalib.Result(f"{lay} {name}", qalib.PASS if ok else qalib.FAIL,
                         f"{why} (Play x {px}px, chip x {cx}px, density {dens:g})")], unmarked


def main(argv):
    json_out, tol, paths = None, 2.0, []
    it = iter(argv)
    for a in it:
        if a == "--json":
            json_out = next(it)
        elif a == "--pinned":
            global PINNED
            PINNED = True
        elif a == "--tol":
            tol = float(next(it))
        else:
            paths.append(a)
    files = []
    for p in paths:
        files += sorted(glob.glob(os.path.join(p, "**", "*.json"), recursive=True)) if os.path.isdir(p) else [p]
    files = [f for f in files if os.path.basename(f) not in ("ledger.json",)]
    if not files:
        print("anchorcheck: no dumps given - NOT TESTED")
        return qalib.EXIT[qalib.NOT_TESTED]
    results, review = [], []
    for f in files:
        r, u = check_dump(f, tol)
        results += r
        if u:
            review.append(u)
    for u in sorted(set(review)):
        print("REVIEW", u)
    real = [r for r in results if r.status != qalib.NOT_TESTED]
    return qalib.finish(real if real else results, "Play / category anchor (682e)", json_out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

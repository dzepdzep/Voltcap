#!/usr/bin/env python3
"""682bb (owner review of the v0.7.0 preview, HW-28 "Play stays in the same position across pages"): Play's place on a layout's
own pages, read from QaClip dumps taken on the handheld (p2review gallery / run: <TAG>_<LAYOUT>_{info,details,trophies,options}.json).

Per layout: Play found on each page (a control labelled Play / its layout's word for it, in the game window - the focused
window, not the home behind it); PASS when Details, Achievements and Options show it at the same place and size (within 2 px);
FAIL otherwise; INFRA when a page's dump is missing or shows no Play. Where Info's Play differs from the pages' that is reported
as a note (some layouts deliberately give their first page a bigger Play - Cinema - or a vertical menu); not a FAIL.
Usage: playplace_hw.py DIR [DIR ...]     (each DIR one handheld x scale; the dumps' file names start with any tag)
Exit: 0 all PASS, 1 any FAIL, 2 INFRA only."""
import glob, json, os, re, sys

PLAY = {"play", "run", "power on", "insert coin", "start", "play it", "press start", "rent play", "rent & play", "start game",
        "continue game", "play game", "play it!", "▸ play", "▶ play"}
PAGES = ("details", "trophies", "options")


def play_rect(path):
    try:
        d = json.load(open(path))
    except (OSError, ValueError):
        return None
    wins = d.get("windows", [])
    # the game window: the focused one, else the last (the popup is drawn over the home)
    cand = [w for w in wins if w.get("focus")] or wins[-1:]
    for w in cand:
        for n in w.get("nodes", []):
            lab = re.sub(r"[^a-z&!▸▶ ]", " ", str(n.get("label", "")).lower()).strip()
            lab = re.sub(r" +", " ", lab)
            words = lab.split(" ")
            half = len(words) // 2
            if n.get("kind") == "control" and (lab in PLAY or (len(words) % 2 == 0 and words[:half] == words[half:] and " ".join(words[:half]) in PLAY)):
                s = n.get("shown") or n.get("at")
                if s and s[2] > 0 and s[3] > 0:
                    return tuple(s)
    return None


def same(a, b, tol=2):
    return all(abs(x - y) <= tol for x, y in zip(a, b))


def grade_dir(d):
    out, worst = [], 0
    lays = sorted({m.group(1) for f in glob.glob(os.path.join(d, "*_details.json")) for m in [re.search(r"_([A-Z][A-Z_0-9]*)_details\.json$", f)] if m})
    for lay in lays:
        def find(page):
            g = sorted(glob.glob(os.path.join(d, "*_%s_%s.json" % (lay, page))), key=os.path.getmtime)
            return play_rect(g[-1]) if g else None
        rects = {p: find(p) for p in ("info",) + PAGES}
        pages = [rects[p] for p in PAGES]
        if any(r is None for r in pages):
            out.append("INFRA %s: Play not found on %s" % (lay, [p for p in PAGES if rects[p] is None])); worst = max(worst, 2); continue
        ok = all(same(pages[0], r) for r in pages[1:])
        note = "" if rects["info"] is None or same(rects["info"], pages[0]) else " (Info's Play %s - its first page differs)" % (rects["info"],)
        out.append("%s %s: Play %s on Details / Achievements / Options%s" % ("PASS" if ok else "FAIL", lay, pages if not ok else pages[0], note))
        if not ok:
            worst = 1 if worst != 1 else 1
    return out, worst


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    rc = 0; fails = 0; infra = 0; n = 0
    for d in sys.argv[1:]:
        lines, w = grade_dir(d)
        print("== %s" % d)
        for ln in lines:
            print(ln); n += 1
            fails += ln.startswith("FAIL"); infra += ln.startswith("INFRA")
    print("layouts graded %d, FAIL %d, INFRA %d" % (n, fails, infra))
    sys.exit(1 if fails else 2 if infra and not n - infra else 0)

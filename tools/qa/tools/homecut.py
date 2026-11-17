#!/usr/bin/env python3
"""676o: home-screen text cut gate (rule 11 / QA R18 on hardware). The popup gate grades windows; the home screen's own
texts - hints, names, info lines - were never graded, and the RP Nova x1.0 / x1.3 runs (f676x10, f676x13) showed cuts it
missed: Spotlight "OPTIONS" 127 px in 123 and "MENU" squeezed to 0, Cube "Achieve", Arcade "SHA...", Classic "4 re...".

usage: python3 tools/qa/tools/homecut.py RUN_DIR|DUMP.json [...] [--json out.json] [--allow tools/qa/homecut_allow.json]
Reads the focused window of every *_home.json QaClip dump (ui/QaClip.kt "windows" -> "texts"). A placed text FAILs when its
own layout reports an overflow (ovH) and its one-line width does not fit its box (needW > boxW + 1 px) - shrunk to nothing
(boxW 0) included - unless it slides (scroll / marquee: the whole text is reachable) or gave way by design ("giveway").
Allowed: an entry in --allow {"LAYOUT": ["text", ...]} for decoration the owner approved as cut on purpose (a watermark);
each one must carry a reason there. 676u: a text whose box is under 60 % of its line height (textH) FAILs too - cut in
height - sliding or not. 677h: a text broken across lines inside a word ("brokenWord", Latin-type scripts) FAILs. No home dump with texts: INFRA. Exit code: qalib (0 = PASS only).
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib  # noqa: E402

TOL = 1
VCUT = 0.6  # a box under 60 % of the text's own line height cuts its letters (676u)


def cut_texts(dump):
    """(text, message) for every cut text of the focused window; None when the dump carries no texts."""
    out, seen = [], False
    for w in dump.get("windows", []):
        if not w.get("focus"):
            continue
        for t in w.get("texts", []):
            seen = True
            nw, bw = t.get("needW"), t.get("boxW")
            if not t.get("placed", True):
                continue
            sh = t.get("shown") or [0, 0, 1, 1]
            if sh[2] <= 0 or sh[3] <= 0:
                continue  # (676y: not on the screen at all - a ticker's row outside its box, a scrolled-away item)
            # (676u, RP Nova x1.3 r676x13 / t676x13: texts squeezed in HEIGHT - Card binder facts drawn half high, Greencore's
            # PLAY / DETAILS 14 px of 72 - the width check never saw them. Sliding doesn't help a text cut in height, so this
            # one counts for sliding texts too. A box under 60 % of the text's line height cuts its letters; 0 px = not drawn.)
            th, bh = t.get("textH"), t.get("boxH")
            if th and bh is not None and 0 < bh < VCUT * th:
                out.append((t.get("text", "") + " (height)", f"needs {th} px, has {bh}"))
                continue
            # (677h, owner RP Classic: Storage Boxes "6 / 1 0", "Neve / r"; s677h Hub "ActRaise / r": a line break inside a word
            # reads as two other words - the shared fitters shrink first; one that still breaks FAILs.)
            if t.get("brokenWord"):
                out.append((t.get("text", "") + " (word broken)", f"broken inside a word across {t.get('lines')} lines"))
                continue
            if t.get("scroll") or t.get("giveway") or not t.get("ovH"):
                continue
            if nw is None or bw is None or nw <= bw + TOL:
                continue
            out.append((t.get("text", ""), f"needs {nw} px, has {bw}"))
    return out if seen else None


def layout_of(path):
    b = os.path.basename(path)[:-len("_home.json")]
    return b.split("_", 1)[1] if "_" in b and not b.split("_", 1)[0].isupper() else b


def check(files, allow):
    res = []
    for f in files:
        lay = layout_of(f)
        cuts = cut_texts(json.load(open(f, encoding="utf-8")))
        if cuts is None:
            res.append(qalib.Result(lay, qalib.INFRA, "home dump has no texts for its focused window"))
            continue
        ok = set(allow.get(lay, {}).keys()) if isinstance(allow.get(lay), dict) else set(allow.get(lay, []))
        bad = [c for c in cuts if c[0] not in ok]
        res.append(qalib.Result(lay, qalib.FAIL if bad else qalib.PASS,
                                "; ".join(f"{t!r} {m}" for t, m in bad) or
                                (f"{len(cuts)} allowed decoration" if cuts else "every home text whole")))
    return res


def main(argv):
    json_out, allow_path, paths = None, None, []
    it = iter(argv)
    for a in it:
        if a == "--json":
            json_out = next(it)
        elif a == "--allow":
            allow_path = next(it)
        else:
            paths.append(a)
    files = []
    for p in paths:
        files += sorted(glob.glob(os.path.join(p, "**", "*_home.json"), recursive=True)) if os.path.isdir(p) else [p]
    if not files:
        print("homecut: no home dumps given - NOT TESTED")
        return qalib.EXIT[qalib.NOT_TESTED]
    allow = json.load(open(allow_path, encoding="utf-8")) if allow_path and os.path.exists(allow_path) else {}
    return qalib.finish(check(files, allow), "Home text cut gate", json_out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

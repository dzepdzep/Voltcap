#!/usr/bin/env python3
"""VoltCap QA R24 (update 394) — category-switch visual check: a category change must never visually reload an unchanged layout.

  python3 catswitch.py OUTDIR RUN [--layouts A,B]      (OUTDIR = the pulled out/ of device/catswitch.sh)

Per layout of the manifest: the recording (L2 / R2 steps, rapid reversals) goes through frames.py with --fail-on
black,white,dip: a blank frame or a whole-screen dip while the category changes is a FAIL (the shell faded / slid / was
rebuilt). Transient flashes are listed for review (new games swapping in are legitimate; they are not a verdict).
A layout that was skipped (picker), has no recording, or is missing from the manifest is SKIP / INFRA, never PASS.
"""
import argparse, json, os, subprocess, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
from qalib import Result, PASS, FAIL, INFRA


def manifest(path):
    exp, done, skip, screen = [], set(), {}, {}
    for line in open(path, encoding="utf-8", errors="replace"):
        p = line.split()
        if len(p) >= 2 and p[0] == "expect": exp.append(p[1])
        elif len(p) >= 2 and p[0] == "done": done.add(p[1])
        elif len(p) >= 2 and p[0] == "skip": skip[p[1]] = " ".join(p[2:])
        elif len(p) >= 3 and p[0] == "screen": screen[p[1]] = p[2]
    return exp, done, skip, screen


def still(out, run, layout):
    """The layout's still (taken before the steps) as a small grey array, or None."""
    f = os.path.join(out, f"{run}_{layout}.png")
    if not os.path.exists(f):
        return None
    try:
        from PIL import Image
        import numpy as np
        return np.asarray(Image.open(f).convert("L").resize((96, 54)), dtype="float32")
    except Exception:
        return None


def same_layout_stills(out, run, layouts, limit=6.0):
    """Layouts whose stills are (nearly) the same picture: the preview did not apply (update 394: a whole run was Artwork library
    because the category default layout won on the first step) - none of them may pass."""
    import numpy as np
    st = {L: still(out, run, L) for L in layouts}
    dup = set()
    names = [L for L in layouts if st[L] is not None]
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            if float(np.abs(st[a] - st[b]).mean()) < limit:
                dup.update((a, b))
    return dup


def check_layout(out, run, layout):
    video = os.path.join(out, f"{run}_{layout}.mp4")
    if not os.path.exists(video) or os.path.getsize(video) < 20_000:
        return Result(layout, INFRA, "no recording (or too short to judge)", video)
    work = os.path.join(out, f"{run}_{layout}_frames")
    os.makedirs(work, exist_ok=True)
    here = os.path.dirname(os.path.abspath(__file__))
    r = subprocess.run([sys.executable, os.path.join(here, "frames.py"), video, "-o", work, "--fail-on", "black,white,dip"],
                       capture_output=True, text=True)
    ev = os.path.join(work, "events.json")
    if not os.path.exists(ev):
        return Result(layout, INFRA, "frames.py produced no events.json: " + (r.stderr or r.stdout)[-200:], video)
    d = json.load(open(ev))
    c = d["summary"]["counts"]
    note = f"{d['summary']['frames']} frames, {d['summary']['seconds']}s, transient {c.get('transient', 0)} (review), hitch {c.get('hitch', 0)}"
    bad = [k for k in ("black", "white", "dip") if c.get(k)]
    if bad:
        return Result(layout, FAIL, "blank / dipping frames while the category changed: " + ", ".join(f"{c[k]} {k}" for k in bad) + "; " + note, video)
    if d["summary"]["frames"] < 60:
        return Result(layout, INFRA, "too few frames: " + note, video)
    return Result(layout, PASS, "no blank frame or whole-screen dip across the category steps; " + note, video)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out"); ap.add_argument("run"); ap.add_argument("--layouts", default="")
    a = ap.parse_args()
    mf = os.path.join(a.out, f"{a.run}_manifest.txt")
    if not os.path.exists(mf):
        print(Result("manifest", INFRA, "no manifest: the sweep did not run", mf)); sys.exit(2)
    exp, done, skip, screen = manifest(mf)
    want = [x for x in a.layouts.split(",") if x] or exp
    if not want:
        print(Result("manifest", INFRA, "nothing expected", mf)); sys.exit(2)
    results = []
    dup = same_layout_stills(a.out, a.run, [L for L in want if L in done]) if len([L for L in want if L in done]) > 1 else set()
    for L in want:
        if L in dup:
            results.append(Result(L, INFRA, "its still equals another layout's: the layout did not apply (cat_layout_default?), nothing here counts")); continue
        if L in skip: results.append(Result(L, INFRA, "skipped: " + skip[L])); continue
        if L not in done: results.append(Result(L, INFRA, "never finished (interrupted sweep)")); continue
        if screen.get(L) != "HOME": results.append(Result(L, INFRA, f"screen was {screen.get(L)}, not HOME")); continue
        results.append(check_layout(a.out, a.run, L))
    for r in results: print(r)
    sys.exit(qalib.exit_code(results))


if __name__ == "__main__":
    main()

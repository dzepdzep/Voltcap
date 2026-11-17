#!/usr/bin/env python3
"""678o (HW-2, the owner's Record crate checklist - a v0.6.73 gate): grades one device/crate678.sh run (QaClip dumps).
Per scale (X10 = x1.0, one row; X13 = x1.3, the approved vertical menu):
  keys    the window's own keys (controls the home step didn't have; hints such as "B Back" left out) are ONE size in every
          step (within 1 px) and keep it while the focus moves (k0..kN): any difference FAILs
  walk    the window opens with the focus on Play; every D-pad press moves the focus to another key (one press each); MORE
          is reached
  more    A on MORE opens its page (the keys change) and B comes back to the window with the focus on MORE
  closed  B closes the window (no popup left in the closed step)
  rapid   5 rounds of A B A B at 250 ms: none left a window open, no launch was asked for (launches are blocked and counted)
A run without its manifest / build (evidence.py) or a missing dump is INFRA; a scale not in the run is NOT TESTED.
  python3 crate678.py RUN_DIR [--json out.json]          exit code: qalib (0 = PASS only)
"""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib  # noqa: E402

PLAY = re.compile(r"(?i)^(play|continue|resume|start)$")
MORE = re.compile(r"(?i)^more$")
HINT = re.compile(r"^[ABXYLR][12]?\s+\S")   # "B Back", "A Select": a button hint, not one of the window's keys
SCALES = (("X10", "x1.0"), ("X13", "x1.3"))
TOL = 1


def load(run, name):
    p = os.path.join(run, name + ".json")
    if not os.path.exists(p):
        return None
    try:
        with open(p, encoding="utf-8") as f:
            return json.load(f)
    except ValueError:
        return None


def controls(d):
    """(label, [x, y, w, h], focused) of the controls in the focused window (the last window when none says so)."""
    wins = (d or {}).get("windows") or []
    foc = [w for w in wins if w.get("focus")] or wins[-1:]
    return [(n.get("label", ""), n.get("full") or [0, 0, 0, 0], bool(n.get("focused")))
            for w in foc for n in w.get("nodes") or [] if n.get("kind") == "control"]


def focus_label(d):
    f = [l for l, _, on in controls(d) if on]
    return f[-1] if f else None


def keys(d, home_labels):
    """The window's own keys: not on the home step, not a button hint, laid out with a size."""
    return [(l, b) for l, b, _ in controls(d) if l not in home_labels and not HINT.match(l) and len(b) == 4 and b[2] > 0 and b[3] > 0]


def same(a, b):
    return abs(a[0] - b[0]) <= TOL and abs(a[1] - b[1]) <= TOL


def grade_scale(run, s, rapid_lines):
    """Results for one scale prefix [s] (pure on the run's files)."""
    out = []
    home = load(run, f"{s}_home")
    steps = []
    i = 0
    while True:
        d = load(run, f"{s}_k{i}")
        if d is None:
            break
        steps.append(d)
        i += 1
    if home is None and not steps:
        return [qalib.Result(f"{s} (all)", qalib.NOT_TESTED, "scale not in this run")]
    if home is None or not steps:
        return [qalib.Result(f"{s} (all)", qalib.INFRA, "home or first window dump missing")]
    hl = {l for l, _, _ in controls(home)}
    # keys: one size within each step, and each key keeps its size across the steps
    probs, sizes = [], {}
    for n, d in enumerate(steps):
        ks = keys(d, hl)
        if not ks:
            probs.append(f"k{n}: no window keys")
            continue
        ref = ks[0][1][2:]
        odd = [f"{l} {b[2]}x{b[3]}" for l, b in ks if not same(b[2:], ref)]
        if odd:
            probs.append(f"k{n}: not one size ({ks[0][0]} {ref[0]}x{ref[1]} vs " + ", ".join(odd) + ")")
        for l, b in ks:
            if l in sizes and not same(sizes[l], b[2:]):
                probs.append(f"k{n}: {l} changed size {sizes[l][0]}x{sizes[l][1]} -> {b[2]}x{b[3]}")
            sizes.setdefault(l, b[2:])
    k0 = keys(steps[0], hl)
    out.append(qalib.Result(f"{s} keys", qalib.FAIL if probs else qalib.PASS,
                            "; ".join(probs)[:400] if probs else f"{len(k0)} keys, one size {k0[0][1][2]}x{k0[0][1][3]} px in "
                            f"{len(steps)} steps" if k0 else "no keys"))
    # walk: Play first, one press each, MORE reached
    fl = [focus_label(d) for d in steps]
    wp = []
    if not fl[0] or not PLAY.match(re.sub(r"^\W+\s*", "", fl[0])):
        wp.append(f"opened with the focus on {fl[0]!r}, not Play")
    for n in range(1, len(fl)):
        if fl[n] == fl[n - 1]:
            wp.append(f"press {n} did not move the focus ({fl[n]!r})")
    reached = bool(fl[-1] and MORE.match(fl[-1]))
    if not reached:
        wp.append(f"MORE not reached (last focus {fl[-1]!r})")
    out.append(qalib.Result(f"{s} walk", qalib.FAIL if wp else qalib.PASS, "; ".join(wp) or " -> ".join(fl)))
    # more: its page opened, B came back to MORE
    mo, mb = load(run, f"{s}_moreopen"), load(run, f"{s}_moreback")
    if not reached:
        out.append(qalib.Result(f"{s} more", qalib.NOT_TESTED, "MORE never had the focus"))
    elif mo is None or mb is None:
        out.append(qalib.Result(f"{s} more", qalib.INFRA, "More step dumps missing"))
    else:
        mp = []
        if {l for l, _ in keys(mo, hl)} == {l for l, _ in keys(steps[-1], hl)} and focus_label(mo) == fl[-1]:
            mp.append("A on MORE changed nothing")
        fb = focus_label(mb)
        if not fb or not MORE.match(fb):
            mp.append(f"after B the focus is on {fb!r}, not MORE")
        out.append(qalib.Result(f"{s} more", qalib.FAIL if mp else qalib.PASS, "; ".join(mp) or f"page opened; B back on {fb}"))
    # closed
    cl = load(run, f"{s}_closed")
    if cl is None:
        out.append(qalib.Result(f"{s} closed", qalib.INFRA, "closed dump missing"))
    else:
        up = cl.get("popupAgeMs") is not None
        out.append(qalib.Result(f"{s} closed", qalib.FAIL if up else qalib.PASS, "a window is still up" if up else "closed"))
    # rapid
    rl = [ln for ln in rapid_lines if ln.startswith(s + " ")]
    rounds = [ln for ln in rl if " rapid" in ln]
    launches = [int(ln.rsplit("=", 1)[1]) for ln in rl if "launches=" in ln and ln.rsplit("=", 1)[1].strip().isdigit()]
    if not rounds:
        out.append(qalib.Result(f"{s} rapid", qalib.NOT_TESTED, "no rapid rounds"))
    else:
        opened = [ln.split()[1] for ln in rounds if ln.endswith(" open")]
        bad = opened + ([f"{launches[0]} launch(es) asked for"] if launches and launches[0] else [])
        out.append(qalib.Result(f"{s} rapid", qalib.FAIL if bad else qalib.PASS,
                                ("left open: " + ", ".join(bad)) if bad else f"{len(rounds)} rounds, all closed, no launch"))
    return out


def grade(run, check_evidence=True):
    if check_evidence:
        import evidence
        ev = evidence.check_run(run)
        if ev[0] != qalib.PASS:
            return [qalib.Result("evidence", qalib.INFRA, ev[2])]
    rp = os.path.join(run, "rapid.txt")
    lines = []
    if os.path.exists(rp):
        with open(rp, encoding="utf-8") as f:
            lines = [ln.strip() for ln in f]
    res = []
    for s, _ in SCALES:
        res += grade_scale(run, s, lines)
    return res


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    js = argv[argv.index("--json") + 1] if "--json" in argv else None
    return qalib.finish(grade(argv[0]), "Record crate checklist (HW-2)", js)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

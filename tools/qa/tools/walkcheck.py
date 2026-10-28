#!/usr/bin/env python3
"""VoltCap QA — controller walk report from a QaWalk log (`vqa.sh walk` / device/walk.sh → NAME_qa.txt).

  python3 walkcheck.py NAME_qa.txt [--json out.json] [--strict] [--min-steps N]

For every window the walk saw (a set of controls), it lists:
  * TOUCH-ONLY  — pressable (onClick) but not focusable at all: a controller can never reach it;
  * NOT REACHED — focusable and pressable, but focus never landed on it during the walk (unreachable, or the walk was
    too short — check by hand before calling it a defect);
  * DUPLICATE   — two controls with the same label AND the same box (a control drawn / exposed twice), and
    SAME LABEL    — several controls sharing one visible label (e.g. 6 "Play" buttons). Those are told apart by
    identity = label + their reading-order number among the same-labelled controls of that screen, and a focus event is
    matched to the control at its box, so reaching one "Play" never counts as reaching all of them;
  * FOCUS ON UNKNOWN — focus sat on something that is not in any control list of its window (focus on a non-control);
  * the key presses after which nothing had focus (focus lost) and keys that never moved focus (stuck).
Coverage: reached / reachable per window, so a walk's real coverage is reported, not the inventory.

Exit: 2 (infrastructure) for a missing / empty / crashed / key-less log (a walk that did nothing is not a pass);
with --strict 1 for any TOUCH-ONLY / NOT REACHED / DUPLICATE / FOCUS ON UNKNOWN / focus lost; otherwise 0 and the
verdict line says REPORT (nothing asserted).
"""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
from qalib import Result, PASS, FAIL, INFRA

KEY = {19: "U", 20: "D", 21: "L", 22: "R", 96: "A", 97: "B", 99: "X", 100: "Y", 102: "L1", 103: "R1", 104: "L2", 105: "R2", 108: "START", 109: "SELECT"}
NODE = re.compile(r"node (.*) @(-?\d+),(-?\d+),(\d+),(\d+) f=(\d) c=(\d) s=(\d)")
FOCUS = re.compile(r"key=(-?\d+) win=(\S+) focus=(.*)$")


def box_of(n):
    return (n["x"], n["y"], n["w"], n["h"])


def identify(nodes):
    """Give every node of ONE screen snapshot a stable identity: its label, or label#k (k = reading order, 1-based)
    when several controls share the label."""
    by = {}
    for n in nodes:
        by.setdefault(n["label"], []).append(n)
    for label, group in by.items():
        if len(group) == 1:
            group[0]["id"] = label
        else:
            for k, n in enumerate(sorted(group, key=lambda n: (n["y"], n["x"], n["w"], n["h"])), 1):
                n["id"] = f"{label}#{k}"
    return by


def parse(path):
    screens, focus, steps = [], [], []     # screens: dict(win, nodes, at); focus: dict(...); steps: [key, win, focus, moved]
    cur = None
    on = False
    for line in open(path, encoding="utf-8", errors="replace"):
        line = line.rstrip()
        mk = re.search(r"MARK key (\S+)", line)
        if mk:
            steps.append([mk.group(1), steps[-1][1] if steps else "", steps[-1][2] if steps else "-", False])
            continue
        m = re.search(r" walk (.*)$", line)
        if not m:
            continue
        t = m.group(1)
        if t.startswith("on=true"):
            on = True
        elif t.startswith("screen "):
            w = re.search(r"win=(\S+)", t)
            cur = dict(sig=t.split()[1], win=w.group(1) if w else "?", nodes=[], at=len(steps))
            screens.append(cur)
        elif t.startswith("node ") and cur is not None:
            n = NODE.match(t)
            if n:
                cur["nodes"].append(dict(label=n.group(1), x=int(n.group(2)), y=int(n.group(3)), w=int(n.group(4)), h=int(n.group(5)),
                                         f=n.group(6) == "1", c=n.group(7) == "1", s=n.group(8) == "1"))
        elif t.startswith("key="):
            k = FOCUS.match(t)
            if not k:
                continue
            win, foc = k.group(2), k.group(3)
            if steps:
                steps[-1][1], steps[-1][2], steps[-1][3] = win, foc, True
            fb = None
            if foc != "-":
                lab, _, bx = foc.rpartition(" @")
                try:
                    x, y, w_, h = [int(v) for v in bx.split(",")]
                    fb = (x, y, w_, h)
                except ValueError:
                    lab = foc
                focus.append(dict(win=win, label=lab, box=fb, at=len(screens)))
    for sc in screens:
        sc["by"] = identify(sc["nodes"])
    return dict(on=on, screens=screens, focus=focus, steps=steps)


def resolve(ev, screens, _cache):
    """The identity of the control focus is on: matched by label AND box in a snapshot of the same window (the ones
    taken just before the event first, then those after it); None when no such control is listed."""
    key = (ev["win"], ev["label"], ev["box"], ev["at"])
    if key in _cache:
        return _cache[key]
    cand = [i for i, s in enumerate(screens) if s["win"] == ev["win"] and ev["label"] in s["by"]]   # only snapshots that list the label
    order = [screens[i] for i in cand if i < ev["at"]][::-1] + [screens[i] for i in cand if i >= ev["at"]]
    found = None
    for sc in order[:60]:                   # exact label + box (the newest snapshots are the likely ones)
        for n in sc["by"][ev["label"]]:
            if ev["box"] is None or box_of(n) == ev["box"]:
                found = n["id"]; break
        if found: break
    if found is None and order:             # same label, nearest box (the panel scrolled a little)
        grp = order[0]["by"][ev["label"]]
        bx = ev["box"]
        found = grp[0]["id"] if bx is None or len(grp) == 1 else min(grp, key=lambda n: abs(n["x"] - bx[0]) + abs(n["y"] - bx[1]))["id"]
    _cache[key] = found
    return found


def analyze(path):
    """→ dict with the numbers and lists. Does not decide a verdict (see main / verdicts)."""
    p = parse(path)
    screens, steps = p["screens"], p["steps"]
    out = dict(steps=len(steps), screens=[], on=p["on"], focus_events=len(p["focus"]))
    reached = {}                              # win -> set(identity)
    unknown = []
    cache = {}
    for ev in p["focus"]:
        ident = resolve(ev, screens, cache)
        if ident is None:
            unknown.append(f'{ev["label"]} @{",".join(map(str, ev["box"])) if ev["box"] else "?"} in {ev["win"]}')
        else:
            reached.setdefault(ev["win"], set()).add(ident)
    out["focus_unknown"] = sorted(set(unknown))
    lost = [i for i, s in enumerate(steps) if s[2] == "-"]
    stuck_at = [f"#{i} {st[0]} on {st[2][:50]}" for i, st in enumerate(steps) if st[0] in ("U", "D", "L", "R") and not st[3]]
    out["stuck"] = stuck_at
    # One entry per window; its controls are merged by identity (a panel scrolls: controls come and go).
    wins = {}
    for sc in screens:
        w = wins.setdefault(sc["win"], dict(nodes={}, dup_exact=set(), same_label=set()))
        seen_box = {}
        for n in sc["nodes"]:
            w["nodes"].setdefault(n["id"], n)
            kk = (n["label"], box_of(n))
            seen_box[kk] = seen_box.get(kk, 0) + 1
        for (lab, bx), c in seen_box.items():
            if c > 1 and not lab.startswith("@"):
                w["dup_exact"].add(f'{lab} @{",".join(map(str, bx))} ×{c}')
        for lab, grp in sc["by"].items():
            if len(grp) > 1 and not lab.startswith("@") and len({box_of(g) for g in grp}) > 1:
                w["same_label"].add(f"{lab} ×{len(grp)}")
    tot_r = tot_f = 0
    for win, w in wins.items():
        nodes = list(w["nodes"].values())
        focusable = [n for n in nodes if n["f"]]
        touch_only = [n for n in nodes if n["c"] and not n["f"]]
        got = reached.get(win, set())
        rch = [n for n in focusable if n["id"] in got]
        nrc = [n for n in focusable if n["id"] not in got]
        tot_r += len(rch); tot_f += len(focusable)
        out["screens"].append(dict(win=win, controls=len(nodes), focusable=len(focusable), reached=len(rch),
                                   reached_list=sorted(n["id"] for n in rch),
                                   touch_only=[f'{n["id"]} @{",".join(map(str, box_of(n)))}' for n in touch_only],
                                   not_reached=[f'{n["id"]} @{",".join(map(str, box_of(n)))}' for n in nrc],
                                   duplicates=sorted(w["dup_exact"]), same_label=sorted(w["same_label"])))
    out["focus_lost_after"] = len(lost)
    out["no_move_presses"] = len(stuck_at)
    out["reached_total"] = f"{tot_r}/{tot_f}"
    out["windows"] = len(wins)
    return out


def verdicts(path, out, strict=False, min_steps=1):
    """Results: INFRA for a log that proves nothing, FAIL (strict) per finding class, else a PASS."""
    res = [r for r in qalib.check_log(path, must_contain=("walk on=true",)) if r.status != PASS]
    if res:
        return res
    if out["steps"] < min_steps:
        return [Result("walk", INFRA, f"only {out['steps']} key presses recorded (< {min_steps}): the walk did not run", path)]
    if not out["screens"]:
        return [Result("walk", INFRA, "no screen / control lists in the log: QaWalk saw nothing", path)]
    if out["focus_events"] == 0:
        return [Result("walk", INFRA, "no focus events: focus never landed on anything, or QaWalk was off", path)]
    find = []
    for s in out["screens"]:
        for k, label in (("touch_only", "controller cannot reach (touch only)"), ("not_reached", "never focused during the walk"),
                         ("duplicates", "control listed twice at one place")):
            for x in s[k]:
                find.append(Result(f'{s["win"]}: {x}', FAIL, label, path))
    for x in out["focus_unknown"]:
        find.append(Result(x, FAIL, "focus on something that is not a listed control", path))
    if out["focus_lost_after"]:
        find.append(Result("focus", FAIL, f"focus lost after {out['focus_lost_after']} key press(es)", path))
    if strict:
        return find or [Result("walk", PASS, f"focus reached {out['reached_total']} controls", path)]
    return [Result("walk", PASS, f"{len(find)} review item(s); focus reached {out['reached_total']} controls (not asserted without --strict)", path)]


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if len(sys.argv) < 2:
        sys.exit("usage: walkcheck.py NAME_qa.txt [--json out.json] [--strict] [--min-steps N]")
    path = sys.argv[1]
    strict = "--strict" in sys.argv
    min_steps = int(sys.argv[sys.argv.index("--min-steps") + 1]) if "--min-steps" in sys.argv else 1
    pre = qalib.check_log(path, must_contain=("walk on=true",))
    if pre[0].status != PASS:
        for r in pre: print(r)
        sys.exit(qalib.exit_code(pre))
    out = analyze(path)
    print(f'{out["steps"]} key presses, {out["windows"]} windows, focus reached {out["reached_total"]} focusable controls, '
          f'focus lost {out["focus_lost_after"]}×, presses that didn\'t move focus {out["no_move_presses"]}')
    for x in out["stuck"][:30]: print("  STUCK", x)
    for x in out["focus_unknown"][:20]: print("  FOCUS ON UNKNOWN", x)
    for s in out["screens"]:
        if not (s["touch_only"] or s["not_reached"] or s["duplicates"] or s["same_label"]): continue
        print(f'\nwindow {s["win"]}: {s["reached"]}/{s["focusable"]} reached, {s["controls"]} controls')
        for x in s["touch_only"]: print("  TOUCH-ONLY  ", x)
        for x in s["not_reached"][:40]: print("  NOT REACHED ", x)
        for x in s["duplicates"]: print("  DUPLICATE   ", x)
        for x in s["same_label"][:20]: print("  SAME LABEL  ", x, "(told apart by position)")
    if "--json" in sys.argv:
        json.dump(out, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)
    res = verdicts(path, out, strict, min_steps)
    print(qalib.report_text(res, "walk") if qalib.verdict(res) != PASS else f"[{'PASS' if strict else 'REPORT'}] {res[0].reason}")
    sys.exit(qalib.exit_code(res))


if __name__ == "__main__":
    main()

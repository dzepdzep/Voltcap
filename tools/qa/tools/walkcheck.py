#!/usr/bin/env python3
"""VoltCap QA — controller walk report from a QaWalk log (`vqa.sh walk` / device/walk.sh → NAME_qa.txt).

  python3 walkcheck.py NAME_qa.txt [--json out.json]

For every screen the walk saw (a set of controls in one window), it lists:
  * TOUCH-ONLY  — pressable (onClick) but not focusable at all: a controller can never reach it;
  * NOT REACHED — focusable and pressable, but focus never landed on it during the walk (unreachable, or the walk was
    too short — check by hand before calling it a defect);
  * the key presses after which nothing had focus (focus lost) and keys that never moved focus (stuck).
Coverage: reached / reachable per screen, so a walk's real coverage is reported, not the inventory.
"""
import json, re, sys, collections

KEY = {19: "U", 20: "D", 21: "L", 22: "R", 96: "A", 97: "B", 99: "X", 100: "Y", 102: "L1", 103: "R1", 104: "L2", 105: "R2", 108: "START", 109: "SELECT"}


def main():
    path = sys.argv[1]
    screens = {}            # sig -> dict(win, nodes[list], focused set)
    cur = None
    steps = []
    for line in open(path, errors="replace"):
        m = re.search(r" walk (.*)$", line.rstrip())
        mk = re.search(r"MARK key (\S+)", line)
        if mk:
            steps.append([mk.group(1), steps[-1][1] if steps else "", steps[-1][2] if steps else "-", False])
            continue
        if not m: continue
        t = m.group(1)
        if t.startswith("screen "):
            sig = t.split()[1]; win = re.search(r"win=(\S+)", t).group(1)
            cur = screens.setdefault(sig, dict(win=win, nodes=[], focused=set(), first=len(steps)))
            cur["nodes"] = []
        elif t.startswith("node ") and cur is not None:
            n = re.match(r"node (.*) @(\S+) f=(\d) c=(\d) s=(\d)", t)
            if n: cur["nodes"].append(dict(label=n.group(1), box=n.group(2), f=n.group(3) == "1", c=n.group(4) == "1", s=n.group(5) == "1"))
        elif t.startswith("key="):
            k = re.match(r"key=(\d+) win=(\S+) focus=(.*)$", t)
            if not k: continue
            win, foc = k.group(2), k.group(3)
            if steps:
                steps[-1][1] = win; steps[-1][2] = foc; steps[-1][3] = True
            if cur is not None and foc != "-":
                cur["focused"].add(foc.rsplit(" @", 1)[0])
    out = dict(steps=len(steps), screens=[])
    lost = [i for i, s in enumerate(steps) if s[2] == "-"]
    stuck = 0
    stuck_at = []
    for i, st in enumerate(steps):
        if st[0] in ("U", "D", "L", "R") and not st[3]:
            stuck += 1; stuck_at.append(f"#{i} {st[0]} on {st[2][:50]}")
    out["stuck"] = stuck_at
    # One entry per window (a panel scrolls: its controls come and go, so they're merged by name, not by place).
    wins = {}
    for sc in screens.values():
        w = wins.setdefault(sc["win"], dict(nodes={}, focused=set()))
        for n in sc["nodes"]:
            k = n["label"] if not n["label"].startswith("@") else n["label"]
            w["nodes"].setdefault(k, n)
        w["focused"] |= sc["focused"]
    tot_r = tot_f = 0
    for win, w in wins.items():
        nodes = list(w["nodes"].values())
        focusable = [n for n in nodes if n["f"]]
        touch_only = [n for n in nodes if n["c"] and not n["f"]]
        reached = [n for n in focusable if n["label"] in w["focused"]]
        not_reached = [n for n in focusable if n["label"] not in w["focused"]]
        tot_r += len(reached); tot_f += len(focusable)
        out["screens"].append(dict(sig=win, win=win, controls=len(nodes), focusable=len(focusable), reached=len(reached),
                                   reached_list=sorted(n["label"] for n in reached),
                                   touch_only=[f'{n["label"]} @{n["box"]}' for n in touch_only],
                                   not_reached=[f'{n["label"]} @{n["box"]}' for n in not_reached]))
    out["focus_lost_after"] = len(lost)
    out["no_move_presses"] = stuck
    out["reached_total"] = f"{tot_r}/{tot_f}"
    print(f'{out["steps"]} key presses, {len(screens)} screens, focus reached {tot_r}/{tot_f} focusable controls, '
          f'focus lost {len(lost)}×, presses that didn\'t move focus {stuck}')
    for x in out["stuck"][:30]: print("  STUCK", x)
    for s in out["screens"]:
        if not s["touch_only"] and not s["not_reached"]: continue
        print(f'\nscreen {s["sig"]} ({s["win"]}): {s["reached"]}/{s["focusable"]} reached, {s["controls"]} controls')
        for x in s["touch_only"]: print("  TOUCH-ONLY  ", x)
        for x in s["not_reached"][:40]: print("  NOT REACHED ", x)
    if "--json" in sys.argv:
        json.dump(out, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)


if __name__ == "__main__":
    main()

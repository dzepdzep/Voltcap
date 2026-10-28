#!/usr/bin/env python3
"""VoltCap QA — did the focus go where the buttons said? (VoltcapQA log of a `vqa.sh seq` run)

  python3 focuscheck.py NAME_qa.txt [--size N]

For each `MARK seq "<keys>"` it walks the keys (R/L = ±1 game, R2/L2 = next / previous category, U/D/A/B/W… ignored)
against the app's `focus <index> <category> <game>` and `category step` events that followed:
  * every ◀ ▶ must move the focus exactly one game (wrap-around on an endless row and the end of a row are allowed);
  * every R2/L2 must change the category;
  * no focus change may come without a key (focus jumping on its own, e.g. after artwork / a library refresh).
Exit code 1 when anything is wrong (for regression runs).
"""
import os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

FOCUS = re.compile(r" focus (-?\d+) (\S+) (.*)$")


def main():
    path = sys.argv[1]
    bad = [r for r in qalib.check_log(path, must_contain=("MARK seq",)) if r.status != qalib.PASS]
    if bad:  # a missing / empty / crashed log or one with no key sequence checked nothing: never a pass
        for r in bad: print(r)
        sys.exit(qalib.exit_code(bad))
    size = int(sys.argv[sys.argv.index("--size") + 1]) if "--size" in sys.argv else None
    lines = open(path, errors="replace").read().splitlines()
    problems, checked = [], 0
    i = 0
    while i < len(lines):
        m = re.search(r'MARK seq (.*)$', lines[i])
        if not m:
            i += 1; continue
        keys = [k for k in m.group(1).split() if k in ("R", "L", "R2", "L2")]
        evs = []
        j = i + 1
        while j < len(lines) and "MARK " not in lines[j]:
            f = FOCUS.search(lines[j])
            if f: evs.append(("focus", int(f.group(1)), f.group(2), f.group(3)))
            elif " category step" in lines[j]: evs.append(("cat",))
            j += 1
        # collapse repeated identical focus lines
        foc = []
        for e in evs:
            if e[0] == "cat": foc.append(e); continue
            if foc and foc[-1][0] == "focus" and foc[-1][1:3] == e[1:3]: continue
            foc.append(e)
        # previous focus = the last focus before this seq
        prev = None
        for k in range(i - 1, -1, -1):
            f = FOCUS.search(lines[k])
            if f: prev = (int(f.group(1)), f.group(2)); break
        it = iter(foc)
        cur = prev
        for key in keys:
            checked += 1
            if key in ("R2", "L2"):
                e = next(it, None)
                if e is None or e[0] != "cat":
                    problems.append(f"{key}: no category change logged"); continue
                e2 = next(it, None)
                if e2 is None or e2[0] != "focus":
                    problems.append(f"{key}: no focus after the category change"); continue
                cur = (e2[1], e2[2]); continue
            e = next(it, None)
            if e is None:
                problems.append(f"{key}: no focus change (dropped input, or the end of the row)"); continue
            if e[0] != "focus":
                problems.append(f"{key}: a category change instead of a move"); continue
            if cur is not None and e[2] == cur[1]:
                d = e[1] - cur[0]
                want = 1 if key == "R" else -1
                wrap = size is not None and abs(d) == size - 1
                if d != want and not wrap:
                    problems.append(f"{key}: focus {cur[0]} -> {e[1]} (moved {d:+d}, expected {want:+d}) in {e[2]}")
            cur = (e[1], e[2])
        rest = list(it)
        if rest:
            problems.append(f"{len(rest)} focus change(s) with no key: {[(r[1], r[2]) for r in rest if r[0]=='focus'][:5]}")
        i = j
    if checked == 0:
        print("[INFRA] no R/L/R2/L2 key presses found in any MARK seq: nothing was checked"); sys.exit(2)
    print(f"checked {checked} key presses; problems: {len(problems)}")
    for p in problems: print("  " + p)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()

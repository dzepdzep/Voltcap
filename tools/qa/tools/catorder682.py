#!/usr/bin/env python3
"""682l (owner 18 Nov: "aligning the selected chip never breaks category ordering, scrolling, focus, L2/R2 navigation or
animations"): the same catswitch walk recorded with the Play / chip anchor rule off and on (device/catswitch.sh ANCH=off|on).
  python3 catorder682.py OUTDIR RUN_OFF RUN_ON LAYOUT [LAYOUT ...]
Per layout: the QA trace's "category stop <key> <n>/<of>" lines (one per L2 / R2 press that moved) after "MARK layout" give the categories the walk visited. PASS when the on
walk visits exactly the off walk's categories in the same order and every L2 / R2 press moved one category (the count of
category lines equals the presses that can move: none lost, none doubled). Missing logs = INFRA, never PASS.
"""
import os, re, sys

def walk(path, layout):
    if not os.path.exists(path): return None
    on, seq = False, []
    for line in open(path, encoding="utf-8", errors="replace"):
        if "MARK layout" in line: on = line.strip().endswith(layout); continue
        if not on: continue
        m = re.search(r"\bcategory stop (\S+) (\d+)/(\d+)", line)
        if m: seq.append(f"{m.group(1)}({m.group(2)}/{m.group(3)})")
    return seq

def main():
    if len(sys.argv) < 5: print(__doc__); sys.exit(2)
    out, roff, ron, lays = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:]
    worst = 0
    for L in lays:
        a = walk(os.path.join(out, f"{roff}_{L}_qa.txt"), L); b = walk(os.path.join(out, f"{ron}_{L}_qa.txt"), L)
        if a is None or b is None or not a or not b:
            print(f"INFRA {L}: no category trace (off {None if a is None else len(a)}, on {None if b is None else len(b)})"); worst = max(worst, 2); continue
        if a == b: print(f"PASS {L}: {len(b)} categories, same order off / on: {' > '.join(b)}")
        else: print(f"FAIL {L}: off {' > '.join(a)} | on {' > '.join(b)}"); worst = max(worst, 1)
    sys.exit(worst)

if __name__ == "__main__": main()

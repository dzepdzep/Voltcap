#!/usr/bin/env python3
"""VoltCap QA — before / after table for two `vqa.sh suite` runs.

  python3 compare.py DIR_WITH_A_FILES A DIR_WITH_B_FILES B [--md out.md]

Reads NAME_<part>_summary.txt (gfxinfo), NAME_mem.txt and NAME_qa.txt (VoltcapQA slow-frame lines) for both runs.
Lower is better for every row except none; the change column is B − A.
"""
import os, re, statistics, sys
sys.path.insert(0, os.path.dirname(__file__))
from framestats import parse


def load(d, n):
    r = {}
    for part in ("idleactive", "idlecalm", "nav1", "nav2", "nav3", "stress1", "stress2"):
        p = os.path.join(d, f"{n}_{part}_summary.txt")
        if os.path.exists(p):
            r[part] = parse(p)
    mem = os.path.join(d, f"{n}_mem.txt")
    if os.path.exists(mem):
        t = open(mem, errors="replace").read()
        for k, pat in (("pss_mb", r"TOTAL PSS:\s+(\d+)"), ("gfx_mb", r"Graphics:\s+(\d+)"), ("java_mb", r"Java Heap:\s+(\d+)"), ("native_mb", r"Native Heap:\s+(\d+)")):
            m = re.search(pat, t)
            if m: r[k] = round(int(m.group(1)) / 1024, 1)
    qa = os.path.join(d, f"{n}_qa.txt")
    if os.path.exists(qa):
        slow = [l for l in open(qa, errors="replace") if " slow " in l]
        def f(l, k):
            m = re.search(rf"{k}=([\d.]+)", l); return float(m.group(1)) if m else 0.0
        tot = [float(re.search(r" slow ([\d.]+)", l).group(1)) for l in slow]
        r["qa_slow"] = len(slow)
        r["qa_slow_2x"] = sum(1 for t in tot if t > 2 * 8.33)
        r["qa_anim_gt8"] = sum(1 for l in slow if f(l, "anim") > 8)
        r["qa_cmd_gt8"] = sum(1 for l in slow if f(l, "cmd") > 8)
        r["qa_worst_ms"] = max(tot) if tot else 0
    return r


def row(name, a, b, fmt="{:.1f}"):
    if a is None or b is None:
        return f"| {name} | {a} | {b} | |"
    return f"| {name} | {fmt.format(a)} | {fmt.format(b)} | {b - a:+.1f} |"


def mean(r, parts, key):
    v = [r[p][key] for p in parts if p in r and key in r[p]]
    return statistics.mean(v) if v else None


if __name__ == "__main__":
    da, na, db, nb = sys.argv[1:5]
    A, B = load(da, na), load(db, nb)
    out = [f"| metric | {na} (before) | {nb} (after) | change |", "|---|---|---|---|"]
    for label, parts in (("idle, just after input", ["idleactive"]), ("idle, calm (35 s)", ["idlecalm"]),
                         ("home_nav ×3", ["nav1", "nav2", "nav3"]), ("stress ×2", ["stress1", "stress2"])):
        out.append(row(f"{label}: frames drawn", mean(A, parts, "total"), mean(B, parts, "total")))
        out.append(row(f"{label}: janky %", mean(A, parts, "janky_pct"), mean(B, parts, "janky_pct")))
        out.append(row(f"{label}: p90 ms", mean(A, parts, "p90"), mean(B, parts, "p90")))
        out.append(row(f"{label}: p99 ms", mean(A, parts, "p99"), mean(B, parts, "p99")))
        out.append(row(f"{label}: slow UI thread", mean(A, parts, "slow_ui"), mean(B, parts, "slow_ui")))
    for k in ("qa_slow", "qa_slow_2x", "qa_anim_gt8", "qa_cmd_gt8", "qa_worst_ms", "pss_mb", "gfx_mb", "java_mb", "native_mb"):
        out.append(row(k, A.get(k), B.get(k)))
    txt = "\n".join(out)
    print(txt)
    if "--md" in sys.argv:
        open(sys.argv[sys.argv.index("--md") + 1], "w").write(txt + "\n")

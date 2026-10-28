#!/usr/bin/env python3
"""VoltCap QA — one table for a `device/layouts.sh` run (every layout, the same navigation).

  python3 layoutreport.py DIR RUN [--frames] [--md out.md]

Per layout: frames drawn, janky %, p90 / p99 (gfxinfo summary), frames > 2× budget (QaTrace slow lines), the most
recompositions per second of each counted part, focus events, and with --frames the transients / blanks found by
frames.py in its recording (slow: ~30 s per layout).
"""
import json, os, re, subprocess, sys
sys.path.insert(0, os.path.dirname(__file__))
from framestats import parse


def main():
    d, run = sys.argv[1], sys.argv[2]
    rows = []; infra = []
    if not os.path.isdir(d):
        print(f"[INFRA] layoutreport: no such folder {d}"); sys.exit(2)
    for f in sorted(os.listdir(d)):
        m = re.match(re.escape(run) + r"_([A-Z_]+)_summary\.txt$", f)
        if not m: continue
        L = m.group(1)
        s = parse(os.path.join(d, f))
        q = os.path.join(d, f"{run}_{L}_qa.txt")
        slow2 = recompmax = 0; parts = {}; focus = 0; inval = 0
        if os.path.exists(q):
            t = open(q, errors="replace").read()
            for mm in re.finditer(r" slow ([\d.]+) budget=([\d.]+)", t):
                if float(mm.group(1)) > 2 * float(mm.group(2)): slow2 += 1
            for mm in re.finditer(r" recomp \d+ms (.*)", t):
                for k, v in re.findall(r"(\w+)=(\d+)", mm.group(1)):
                    parts[k] = max(parts.get(k, 0), int(v))
            focus = len(re.findall(r" focus ", t))
        r = dict(layout=L, frames=s.get("total"), janky=round(100 * s.get("janky", 0) / max(1, s.get("total", 1)), 1),
                 p90=s.get("p90"), p99=s.get("p99"), slow2x=slow2, recomp=parts, focus=focus)
        if "--frames" in sys.argv:
            v = os.path.join(d, f"{run}_{L}.mp4")
            if os.path.exists(v):
                out = os.path.join(d, f"{run}_{L}_frames")
                try:
                    pr = subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "frames.py"), v, "-o", out, "--skip-start", "0.5"],
                                        capture_output=True, timeout=900)
                    if pr.returncode not in (0, 1):   # 2 = the recording can't be trusted (missing / empty / stale / undecodable)
                        raise RuntimeError("frames.py exit %d: %s" % (pr.returncode, pr.stdout.decode("utf-8", "replace").strip()[-160:]))
                    ev = json.load(open(os.path.join(out, "events.json")))
                    c = ev["summary"]["counts"]; r["transients"] = c.get("transient", 0); r["blank"] = c.get("black", 0) + c.get("white", 0) + c.get("dip", 0)
                    r["video_fps"] = ev["summary"].get("fps"); r["hitches"] = c.get("hitch", 0)
                except Exception as e:   # never a silent 0: the cell says INFRA and the run exits 2 below
                    r["transients"] = f"INFRA {e}"; infra.append(L)
            else:
                r["transients"] = "INFRA no recording"; infra.append(L)
        rows.append(r)
    head = "| layout | frames | janky % | p90 | p99 | >2× budget | max recomp/s | focus ev | transients | blank | hitches |\n|---|---|---|---|---|---|---|---|---|---|---|"
    lines = [head]
    for r in rows:
        rc = " ".join(f"{k}={v}" for k, v in sorted(r["recomp"].items(), key=lambda kv: -kv[1])[:3])
        lines.append(f'| {r["layout"]} | {r["frames"]} | {r["janky"]} | {r["p90"]} | {r["p99"]} | {r["slow2x"]} | {rc} | {r["focus"]} | {r.get("transients","")} | {r.get("blank","")} | {r.get("hitches","")} |')
    txt = "\n".join(lines)
    print(txt)
    if "--md" in sys.argv: open(sys.argv[sys.argv.index("--md") + 1], "w").write(txt + "\n")
    json.dump(rows, open(os.path.join(d, f"{run}_layouts.json"), "w"), indent=1)
    if not rows:
        print(f"[INFRA] layoutreport: no {run}_<LAYOUT>_summary.txt files in {d}: the run produced nothing"); sys.exit(2)
    if infra:
        print("[INFRA] no usable recording for: " + ", ".join(infra)); sys.exit(2)


if __name__ == "__main__":
    main()

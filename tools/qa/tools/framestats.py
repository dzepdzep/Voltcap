#!/usr/bin/env python3
"""VoltCap QA — reads `dumpsys gfxinfo <pkg> framestats` (+ the summary) and prints frame-pacing numbers.

  python3 framestats.py RUN_framestats.txt [--json out.json]

Uses the per-frame PROFILEDATA rows (Android keeps the last ~120 frames) for the breakdown, and the whole-run
histogram/percentiles from the summary part. The budget is the frame interval Android reports (FrameInterval), so a
120 Hz screen is judged against 8.3 ms, not 16.7 ms.
"""
import json, re, statistics, sys


def parse(path):
    txt = open(path, encoding="utf-8", errors="replace").read()
    out = {}
    for k, pat in [("total", r"Total frames rendered: (\d+)"), ("janky", r"Janky frames: (\d+)"),
                   ("p50", r"50th percentile: (\d+)ms"), ("p90", r"90th percentile: (\d+)ms"),
                   ("p95", r"95th percentile: (\d+)ms"), ("p99", r"99th percentile: (\d+)ms"),
                   ("missed_vsync", r"Number Missed Vsync: (\d+)"), ("slow_ui", r"Number Slow UI thread: (\d+)"),
                   ("slow_bitmap", r"Number Slow bitmap uploads: (\d+)"), ("slow_draw", r"Number Slow issue draw commands: (\d+)"),
                   ("deadline_missed", r"Number Frame deadline missed: (\d+)"), ("gpu_p90", r"90th gpu percentile: (\d+)ms")]:
        m = re.search(pat, txt)
        if m: out[k] = int(m.group(1))
    rows = []
    sec = re.search(r"---PROFILEDATA---\s*\n(.*?)\n---PROFILEDATA---", txt, re.S)
    if sec:
        lines = [l.strip().rstrip(",") for l in sec.group(1).strip().splitlines() if l.strip()]
        head = lines[0].split(",")
        for l in lines[1:]:
            v = l.split(",")
            if len(v) < len(head): continue
            r = dict(zip(head, (int(x) if x.lstrip("-").isdigit() else 0 for x in v)))
            if r.get("Flags", 0) != 0: continue  # skip non-normal frames (first draw, etc.)
            # (Some Android versions label FrameInterval / FrameStartTime the wrong way round: the interval is the small one.)
            a, b = r.get("FrameInterval", 0), r.get("FrameStartTime", 0)
            if a > 10**9 and 0 < b < 10**9: r["FrameInterval"], r["FrameStartTime"] = b, a
            rows.append(r)
    if rows:
        iv = [r["FrameInterval"] for r in rows if r.get("FrameInterval")]
        budget = statistics.median(iv) if iv else 16_666_667
        dur = [(r["FrameCompleted"] - r["IntendedVsync"]) / 1e6 for r in rows]
        ui = [(r["SyncStart"] - r["HandleInputStart"]) / 1e6 for r in rows if r["HandleInputStart"] > 0]
        rt = [(r["FrameCompleted"] - r["SyncStart"]) / 1e6 for r in rows]
        late = [r for r in rows if r.get("FrameDeadline") and r["FrameCompleted"] > r["FrameDeadline"]]
        out["recent"] = dict(frames=len(rows), budget_ms=round(budget / 1e6, 2),
                             dur_p50=round(statistics.median(dur), 2), dur_max=round(max(dur), 2),
                             ui_p50=round(statistics.median(ui), 2) if ui else None, ui_max=round(max(ui), 2) if ui else None,
                             rt_p50=round(statistics.median(rt), 2), over_budget=sum(1 for d in dur if d > budget / 1e6),
                             deadline_missed=len(late))
    if "total" in out and "janky" in out and out["total"]:
        out["janky_pct"] = round(100 * out["janky"] / out["total"], 2)
    return out


if __name__ == "__main__":
    r = parse(sys.argv[1])
    print(json.dumps(r, indent=1))
    if "--json" in sys.argv:
        json.dump(r, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)

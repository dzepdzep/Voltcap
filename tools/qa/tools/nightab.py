#!/usr/bin/env python3
"""529j - Night menu Full / Full (legacy bar buffers) / Lite, same key sequences (device/nightab529.sh).

  python3 nightab.py RUN_DIR [--md report.md]

One row per variant and segment, both repeats side by side, janky % and frame-time percentiles from gfxinfo.
"""
import argparse, glob, os, re

KEYS = [("frames", r"Total frames rendered: (\d+)"), ("janky", r"Janky frames: \d+ \(([\d.]+)%\)"),
        ("p50", r"^50th percentile: (\d+)ms"), ("p90", r"^90th percentile: (\d+)ms"), ("p95", r"^95th percentile: (\d+)ms"),
        ("p99", r"^99th percentile: (\d+)ms"), ("gpu90", r"90th gpu percentile: (\d+)ms")]


def read(p):
    t = open(p, encoding="utf-8", errors="replace").read()
    out = {}
    for k, rx in KEYS:
        m = re.search(rx, t, re.M)
        out[k] = m.group(1) if m else "-"
    return out


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("run"); ap.add_argument("--md")
    a = ap.parse_args()
    rows = ["| variant | segment | r1 frames / janky % / p50 / p90 / p95 / p99 / gpu p90 | r2 frames / janky % / p50 / p90 / p95 / p99 / gpu p90 |",
            "|---|---|---|---|"]
    for v in ["full", "full_legacy", "lite"]:
        for s in ["nav", "cat", "pop", "idle"]:
            cells = []
            for r in ["r1", "r2"]:
                p = os.path.join(a.run, f"{v}_{r}_{s}.txt")
                if not os.path.exists(p): cells.append("NOT RUN"); continue
                d = read(p); cells.append(" / ".join(d[k] for k, _ in KEYS))
            rows.append(f"| {v} | {s} | {cells[0]} | {cells[1]} |")
    txt = "\n".join(rows); print(txt)
    if a.md: open(a.md, "w", encoding="utf-8").write(txt + "\n")


if __name__ == "__main__":
    main()

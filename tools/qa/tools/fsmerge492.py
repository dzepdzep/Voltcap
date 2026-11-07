#!/usr/bin/env python3
"""Update 492 (D137): frame times from repeated `dumpsys gfxinfo <pkg> framestats` dumps (d137rc492.sh), without the
`gfxinfo reset` that aborted Voltcap on the KONKR. Each dump lists the last ~120 frames; they're de-duplicated by their
IntendedVsync. Frame time = FrameCompleted - IntendedVsync (ns; gfxinfo's percentiles are these); janky = finished after its
FrameDeadline (the platform's own budget for that frame — on the KONKR ~2 frames, its pipeline). Only frames with Flags == 0.
Usage: fsmerge492.py file [file…]"""
import sys, statistics

def frames(path):
    seen = {}
    cols = None
    for line in open(path, errors="replace"):
        line = line.strip().rstrip(",")
        if not line or line.startswith("---"):
            continue
        parts = line.split(",")
        if parts[0] == "Flags":
            cols = {n: i for i, n in enumerate(parts)}
            continue
        if cols is None:
            continue
        try:
            if int(parts[cols["Flags"]]) != 0:
                continue
            iv = int(parts[cols["IntendedVsync"]]); fc = int(parts[cols["FrameCompleted"]])
            dl = int(parts[cols["FrameDeadline"]]) if "FrameDeadline" in cols else iv + 16_666_667
        except (ValueError, IndexError, KeyError):
            continue
        if fc > iv > 0:
            seen[iv] = ((fc - iv) / 1e6, fc > dl)
    return [seen[k] for k in sorted(seen)]

def pct(v, p):
    s = sorted(v); return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]

def summary(fr):
    v = [d for d, _ in fr]; j = sum(1 for _, late in fr if late)
    return f"frames={len(v)} janky={100 * j / max(1, len(v)):.1f}% p50={pct(v,50):.1f} p90={pct(v,90):.1f} p99={pct(v,99):.1f} ms"

if __name__ == "__main__":
    allv = []
    for f in sys.argv[1:]:
        v = frames(f); allv += v
        print(f.split("/")[-1], summary(v) if v else "no frames")
    if len(sys.argv) > 2 and allv:
        print("all", summary(allv))

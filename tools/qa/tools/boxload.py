#!/usr/bin/env python3
"""VoltCap QA (update 416): Cardboard box category-switch loading time from a screen recording.
For every category change (the category sign + info card change at once) it reports how long the box interior stays
empty (no case drawn) before the cases appear. Run:  py -3 -I boxload.py VIDEO.mp4 [W H]   (recorded 620x540 by run416a.sh).
Needs ffmpeg/ffprobe, numpy. Sign region = top-left label, case region = the box's back rows."""
import subprocess, sys, json
import numpy as np
v = sys.argv[1]; W = 124; H = 108
pts = [float(x) for x in subprocess.check_output(["ffprobe","-v","error","-select_streams","v","-show_entries","frame=pts_time","-of","csv=p=0",v]).decode().split() if x]
raw = subprocess.check_output(["ffmpeg","-v","error","-i",v,"-fps_mode","passthrough","-vf",f"scale={W}:{H},format=gray","-f","rawvideo","-"], stderr=subprocess.DEVNULL)
fr = np.frombuffer(raw, np.uint8).reshape(-1, H, W).astype(np.float32)
n = min(len(fr), len(pts)); fr = fr[:n]; pts = pts[:n]
def reg(a, y0, y1, x0, x1): return a[:, int(H*y0):int(H*y1), int(W*x0):int(W*x1)]
sign = reg(fr, .06, .20, .03, .30); cases = reg(fr, .18, .60, .20, .85)
sd = np.r_[0, np.abs(np.diff(sign, axis=0)).mean(axis=(1, 2))]
# case presence = how far the case region departs from a flat cardboard floor (its own std over the region)
cv = cases.std(axis=(1, 2))
base = np.median(cv[:5])
events = []; i = 1
while i < n:
    if sd[i] > 6:   # sign changed
        t0 = pts[i]; j = i
        while j + 1 < n and pts[j + 1] - t0 < 0.25 and sd[j + 1] > 3: j += 1
        events.append((i, t0)); i = j + 6
    else: i += 1
out = []
for k, (i, t0) in enumerate(events):
    end = events[k + 1][0] if k + 1 < len(events) else n
    ref = cases[min(i + 2, n - 1)]
    d = np.abs(cases[i:end] - ref).mean(axis=(1, 2)); ts = pts[i:end]
    first = next((ts[q] - t0 for q in range(2, len(d)) if d[q] > 3.0), None)
    last = None
    for q in range(len(d) - 1, 0, -1):
        if abs(d[q] - d[-1]) > 1.5: last = ts[min(q + 1, len(ts) - 1)] - t0; break
    out.append({"press_s": round(t0, 2), "empty_box_ms": None if first is None else round(first * 1000), "settled_ms": None if last is None else round(last * 1000), "cases_diff": round(float(d[-1]), 1)})
print(json.dumps(out, indent=1))

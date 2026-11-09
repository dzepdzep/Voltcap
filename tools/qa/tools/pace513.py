#!/usr/bin/env python3
"""Update 513: frame pacing from `dumpsys gfxinfo <pkg> framestats` dumps (kb513.sh fs_<tag>_w<n>.txt).

Per window: frames, the gaps between frames as shown (DisplayPresentTime, else FrameCompleted), and how evenly they come:
  gapMed      median gap (ms)            even  share of gaps within 2 ms of the median
  hitch       gaps > 1.5 x median + 4 ms (a visible hold of the picture)
  late p50/p90  shown time - intended vsync (ms): how far behind its own time each frame appears; jitter = p90 - p10
Also the UI-thread time per frame (input to sync) and the render thread's draw-command time.
Usage: pace513.py fs_*.txt
"""
import sys, statistics as st

def frames(path):
    hdr = None; seen = {}
    for line in open(path, errors='ignore'):
        line = line.strip().rstrip(',')
        if line.startswith('Flags'):
            hdr = line.split(','); continue
        if hdr is None or not line or not line[0].isdigit(): continue
        v = line.split(',')
        if len(v) < len(hdr): continue
        d = dict(zip(hdr, v))
        try:
            if int(d['Flags']) != 0: continue
            iv = int(d['IntendedVsync']); pr = int(d.get('DisplayPresentTime', '0') or 0)
            ui = (int(d['SyncQueued']) - int(d['HandleInputStart'])) / 1e6
            rt = (int(d['SwapBuffers']) - int(d['IssueDrawCommandsStart'])) / 1e6
        except (KeyError, ValueError):
            continue
        # Android 13 fills the present time in later (an older dump of the same frame can hold a stale value): only a
        # present time 0..200 ms after the frame's own vsync is used.
        if not (iv < pr < iv + 200_000_000): pr = seen.get(iv, (0,))[0]
        seen[iv] = (pr, ui, rt)
    return sorted(seen.items())

def pct(a, p):
    a = sorted(a); return a[min(len(a) - 1, int(p / 100 * len(a)))] if a else 0

for path in sys.argv[1:]:
    f = frames(path)
    if len(f) < 3:
        print(f"{path}: frames={len(f)}"); continue
    uis = [v[1] for _, v in f]; rts = [v[2] for _, v in f]
    f = [(iv, v[0]) for iv, v in f if v[0] > 0]
    if len(f) < 3:
        print(f"{path}: frames={len(uis)} (no present times)"); continue
    shown = [s for _, s in f]
    gaps = [(b - a) / 1e6 for a, b in zip(shown, shown[1:]) if b > a]
    gaps = [g for g in gaps if g < 250]  # an idle stretch is not a frame gap
    late = [(s - iv) / 1e6 for iv, s in f]
    med = st.median(gaps) if gaps else 0
    even = sum(1 for g in gaps if abs(g - med) <= 2) / max(1, len(gaps))
    hitch = sum(1 for g in gaps if g > 1.5 * med + 4)
    print(f"{path.split('/')[-1]}: frames={len(f)} gapMed={med:.1f} even={even*100:.0f}% hitch={hitch} "
          f"late p50={pct(late,50):.1f} p90={pct(late,90):.1f} jitter={pct(late,90)-pct(late,10):.1f} "
          f"uiMs p50={pct(uis,50):.2f} p90={pct(uis,90):.2f} rtMs p50={pct(rts,50):.2f} p90={pct(rts,90):.2f}")

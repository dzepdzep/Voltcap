#!/usr/bin/env python3
"""Update 514: where a frame's time goes, from `dumpsys gfxinfo <pkg> framestats` (perf514.sh fs_*.txt).
Per dump: frames, total (IntendedVsync -> FrameCompleted) p50/p90, UI thread (input -> sync), render thread issuing draws
(IssueDrawCommandsStart -> SwapBuffers) and GPU (SwapBuffers -> GpuCompleted) p50/p90, and the share of frames over one
16.7 ms vsync whose largest part is the GPU. Usage: pace514.py fs_*.txt"""
import sys
def pct(v, q):
    v = sorted(v); return v[min(len(v) - 1, int(q * len(v)))] if v else 0
for path in sys.argv[1:]:
    hdr = None; rows = {}
    for line in open(path, errors='ignore'):
        line = line.strip().rstrip(',')
        if line.startswith('Flags'): hdr = line.split(','); continue
        if hdr is None or not line or not line[0].isdigit(): continue
        v = line.split(',')
        if len(v) < len(hdr): continue
        d = dict(zip(hdr, v))
        try:
            if int(d['Flags']) != 0: continue
            iv = int(d['IntendedVsync'])
            tot = (int(d['FrameCompleted']) - iv) / 1e6
            ui = (int(d['SyncQueued']) - int(d['HandleInputStart'])) / 1e6
            rt = (int(d['SwapBuffers']) - int(d['IssueDrawCommandsStart'])) / 1e6
            gpu = (int(d['GpuCompleted']) - int(d['SwapBuffers'])) / 1e6 if int(d.get('GpuCompleted', '0') or 0) > 0 else 0.0
        except (KeyError, ValueError):
            continue
        rows[iv] = (tot, ui, rt, gpu)
    r = list(rows.values())
    if not r: print(path, 'no frames'); continue
    slow = [x for x in r if x[0] > 16.7]
    gpuled = sum(1 for x in slow if x[3] >= max(x[1], x[2]))
    f = lambda i: f"p50 {pct([x[i] for x in r], .5):5.1f} p90 {pct([x[i] for x in r], .9):5.1f}"
    print(f"{path.split('/')[-1]}: {len(r)} frames | total {f(0)} | ui {f(1)} | rt {f(2)} | gpu {f(3)} | >16.7ms {len(slow)} ({100*len(slow)//len(r)} %), GPU-led {gpuled}")

#!/usr/bin/env python3
"""Update 513: one-frame flashes in a screen recording (screenrecord keeps only frames that changed, with their times).

For every frame i it compares the picture with the one before and the one after (mean absolute difference over a grid,
0-255), also per block of a 16 x 12 grid (a local flash). A flash = a picture that appears for a short time and goes again: d(i-1, i) and d(i, i+1) both large while
d(i-1, i+1) is small (the screen went back to what it was, or on to what it would have been). Prints each one with its
time and saves the three frames side by side.
  flash513.py VIDEO.mp4 OUTDIR [--thr 12] [--ratio 0.5]
Exit 0 = no flash, 1 = flashes found.
"""
import sys, os, subprocess, json
import numpy as np, cv2

def frames(path):
    cap = cv2.VideoCapture(path); out = []
    while True:
        ok, f = cap.read()
        if not ok: break
        t = cap.get(cv2.CAP_PROP_POS_MSEC)
        small = cv2.resize(f, (f.shape[1] // 4, f.shape[0] // 4), interpolation=cv2.INTER_AREA).astype(np.int16)
        out.append((t, small, cv2.resize(f, (f.shape[1] // 3, f.shape[0] // 3), interpolation=cv2.INTER_AREA)))
    return out

def d(a, b): return float(np.mean(np.abs(a - b)))

def blocks(a, b, nx=16, ny=12):
    """Mean absolute difference per block (a local flash — one cover — hardly moves the whole-frame mean)."""
    h, w = a.shape[:2]; diff = np.abs(a - b).mean(axis=2)
    return diff[: h // ny * ny, : w // nx * nx].reshape(ny, h // ny, nx, w // nx).mean(axis=(1, 3))

def main():
    vid, outdir = sys.argv[1], sys.argv[2]
    thr = float(sys.argv[sys.argv.index('--thr') + 1]) if '--thr' in sys.argv else 12.0
    ratio = float(sys.argv[sys.argv.index('--ratio') + 1]) if '--ratio' in sys.argv else 0.5
    os.makedirs(outdir, exist_ok=True)
    fr = frames(vid); hits = []
    for i in range(1, len(fr) - 1):
        a, b, c = fr[i - 1][1], fr[i][1], fr[i + 1][1]
        dab, dbc, dac = d(a, b), d(b, c), d(a, c)
        bab, bbc, bac = blocks(a, b), blocks(b, c), blocks(a, c)
        local = int(np.sum((bab > 2.5 * thr) & (bbc > 2.5 * thr) & (bac < ratio * np.minimum(bab, bbc))))
        if (dab > thr and dbc > thr and dac < ratio * min(dab, dbc)) or local >= 3:
            hits.append((i, fr[i][0], dab, dbc, dac, local))
            strip = np.hstack([fr[i - 1][2], fr[i][2], fr[i + 1][2]])
            cv2.imwrite(os.path.join(outdir, f"flash_{i:04d}_{int(fr[i][0])}ms.jpg"), strip)
    for h in hits: print(f"flash frame {h[0]} t={h[1]:.0f} ms  d(prev,this)={h[2]:.1f} d(this,next)={h[3]:.1f} d(prev,next)={h[4]:.1f} blocks={h[5]}")
    print(f"{os.path.basename(vid)}: {len(fr)} frames, {len(hits)} flash(es)")
    sys.exit(1 if hits else 0)

if __name__ == '__main__': main()

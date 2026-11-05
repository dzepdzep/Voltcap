#!/usr/bin/env python3
"""VoltCap QA (update 479, B1) — edge / unused-space audit of one still per home layout (device/edges.sh).

Per still: the flat band along each edge (rows / columns whose pixels barely change: no art, no text — the screen's
background showing through a gutter) in px, and the share of the screen made of flat 40 px blocks (unused room).
A measurement aid, not a verdict: the owner's question is whether a layout *uses* the screen; a flat band >= --band px
on a side is FLAGGED for a look, never PASS / FAIL by itself. Writes <dir>/edgeaudit.txt and a contact sheet.
  python3 edgeaudit.py <dir with LAYOUT.png> [--band 20] [--density 2.25]
"""
import sys, os, argparse
import numpy as np
from PIL import Image


def flat_band(a, axis_rows, from_end, tol=3.0, limit=300):
    """How many rows (or columns) from one edge are flat (std of luminance along the line < tol and same mean)."""
    n = a.shape[0] if axis_rows else a.shape[1]
    ref = None
    for i in range(min(limit, n)):
        idx = n - 1 - i if from_end else i
        line = a[idx, :] if axis_rows else a[:, idx]
        # ignore the status bar's own icons at the top: use the middle 80 % of the line
        m = len(line); line = line[m // 10: m - m // 10]
        if line.std() > tol:
            return i
        if ref is None:
            ref = line.mean()
        elif abs(line.mean() - ref) > tol:
            return i
    return min(limit, n)


def flat_share(a, block=40, tol=2.5):
    h, w = a.shape
    tot = flat = 0
    for y in range(0, h - block + 1, block):
        for x in range(0, w - block + 1, block):
            tot += 1
            if a[y:y + block, x:x + block].std() < tol:
                flat += 1
    return flat / max(tot, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dir"); ap.add_argument("--band", type=int, default=20); ap.add_argument("--density", type=float, default=0)
    o = ap.parse_args()
    files = sorted(f for f in os.listdir(o.dir) if f.endswith(".png") and not f.startswith("edgeaudit"))
    rows = []; thumbs = []
    for f in files:
        im = Image.open(os.path.join(o.dir, f)).convert("L")
        a = np.asarray(im).astype(float)
        b = dict(L=flat_band(a, False, False), R=flat_band(a, False, True), T=flat_band(a, True, False), B=flat_band(a, True, True))
        share = flat_share(a)
        flags = [k for k, v in b.items() if v >= o.band]
        rows.append((f[:-4], b, share, flags))
        t = Image.open(os.path.join(o.dir, f)).convert("RGB"); t.thumbnail((480, 270)); thumbs.append((f[:-4], t))
    d = o.density
    out = [f"edge audit — flat band per side (px{' / dp' if d else ''}), flat 40 px blocks share; FLAG = band >= {o.band}px (look at it)"]
    for name, b, share, flags in rows:
        side = " ".join(f"{k}{v}" + (f"/{v / d:.0f}dp" if d else "") for k, v in b.items())
        out.append(f"{name:16s} {side:40s} flat {share * 100:4.0f}%  {'FLAG ' + ','.join(flags) if flags else ''}")
    txt = "\n".join(out); print(txt)
    open(os.path.join(o.dir, "edgeaudit.txt"), "w").write(txt + "\n")
    if thumbs:
        from PIL import ImageDraw
        cols = 6; rr = (len(thumbs) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * 484, rr * 290), (40, 40, 40)); dr = ImageDraw.Draw(sheet)
        for i, (n, t) in enumerate(thumbs):
            x, y = (i % cols) * 484, (i // cols) * 290
            sheet.paste(t, (x + 2, y + 18)); dr.text((x + 4, y + 3), n, fill=(255, 255, 0))
        sheet.save(os.path.join(o.dir, "edgeaudit_sheet.png"))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""529j - category strips frame by frame on the handheld (owner video VID20261113180355: categories "spawn at the edge").

  python3 chipglide.py RUN_DIR [--md report.md]

Reads each <LAYOUT>_<legacy|new>.qa.txt (VoltcapQA logcat from device/chipstrip529.sh): one `chipglide` line per placed frame
of a moving strip (WholeChipRow / Live tiles' words): k (glide progress), a (how much of each chip shows), x (each chip's x).
Per strip and step it reports the biggest one-frame rise of any chip (1.0 = a chip that appears whole in one frame: a
pop-in), the frames the move took, the biggest jump of a chip shown on two frames, and frame gaps > 25 ms.
Verdict per layout / mode: POP (some chip rose > 0.5 in one frame), GLIDE (moved, never > 0.5), STILL (no strip moved -
nothing to judge: the row fit or no strip in that layout; see the recording), NO DATA.
"""
import argparse, glob, os, re

LINE = re.compile(r"(\d+) chipglide row=(\S+) k=([\d.]+) a=([\d.,]*) x=([-\d,]*)")


def parse(path):
    rows = {}
    for ln in open(path, encoding="utf-8", errors="replace"):
        m = LINE.search(ln)
        if not m: continue
        t, row, k, a, x = m.groups()
        al = [float(v) for v in a.split(",") if v]
        xs = [int(v) for v in x.split(",") if v]
        rows.setdefault(row, []).append((int(t), float(k), al, xs))
    return rows


def grade(frames):
    """Steps of one strip: runs of frames between rests (k == 1 after k < 1, or a jump at k == 1)."""
    worst_rise, worst_jump, moving, gaps = 0.0, 0, 0, 0
    for (t0, k0, a0, x0), (t1, k1, a1, x1) in zip(frames, frames[1:]):
        if len(a0) != len(a1): continue
        rise = max([b - a for a, b in zip(a0, a1)] + [0.0])
        worst_rise = max(worst_rise, rise)
        for i in range(len(a0)):
            if a0[i] > 0 and a1[i] > 0: worst_jump = max(worst_jump, abs(x1[i] - x0[i]))
        if x0 != x1: moving += 1
        if k1 < 1.0 and t1 - t0 > 25: gaps += 1
    return worst_rise, worst_jump, moving, gaps


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("run"); ap.add_argument("--md")
    a = ap.parse_args()
    out = ["| layout | mode | strips | lines | biggest one-frame rise | biggest jump (px) | moving frames | gaps >25 ms | verdict |",
           "|---|---|---|---|---|---|---|---|---|"]
    for f in sorted(glob.glob(os.path.join(a.run, "*_*.qa.txt"))):
        name = os.path.basename(f)[:-len(".qa.txt")]
        lay, mode = name.rsplit("_", 1)
        rows = parse(f)
        n = sum(len(v) for v in rows.values())
        if not rows: out.append(f"| {lay} | {mode} | 0 | 0 | - | - | - | - | NO DATA |"); continue
        rise = jump = mov = gap = 0
        for fr in rows.values():
            r, j, m, g = grade(fr); rise = max(rise, r); jump = max(jump, j); mov += m; gap += g
        verdict = "STILL" if mov == 0 else ("POP" if rise > 0.5 else "GLIDE")
        out.append(f"| {lay} | {mode} | {len(rows)} | {n} | {rise:.2f} | {jump} | {mov} | {gap} | {verdict} |")
    txt = "\n".join(out); print(txt)
    if a.md: open(a.md, "w", encoding="utf-8").write(txt + "\n")


if __name__ == "__main__":
    main()

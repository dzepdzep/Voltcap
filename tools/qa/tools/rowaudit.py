#!/usr/bin/env python3
"""VoltCap QA (update 389) — per press, what the SELECTED card does on screen (QaGeom fc* ids from FocusedCard.kt), per frame.

  python3 rowaudit.py LOG_geom.txt [...]            one line per press and a verdict per log
Per press (▶ / ◀ key until the next key): x before, x settled, how far the selected card travelled in one frame at most
('jump'), the largest distance from its settled place after the key ('excursion'), the frames it moved over. A fixed-slot row
(Dashboard, Side menu, Voltcap row, Tile home...) must end each press at the SAME x ('drift' 0) and must not show the new card
displaced by a slot first and then glide back; a layout whose selection travels (a grid, a cross) is judged by eye ('moves').
Update 390: also `selection` (the focus events per press, any layout). Exit 0 always (a report; the verdicts are in the output): the layout kind decides what is a defect.
"""
import re, sys

def parse(path):
    pos, cur, series, keys = {}, None, [], []   # series: (t, id, x, w)
    t0 = None
    for ln in open(path, errors="ignore"):
        m = re.search(r"VoltcapQA: (\d+) (\w+) ?(.*)", ln)
        if not m: continue
        t, k, d = int(m.group(1)), m.group(2), m.group(3)
        if t0 is None: t0 = t
        t -= t0
        if k == "geomkey":
            keys.append((t, d.strip()))
        elif k == "geomgone":
            pos.pop(d.strip(), None)
            if cur == d.strip(): cur = None
        elif k == "geom":
            for p in d.split():
                a = re.match(r"(fc\d+)=(-?\d+),(-?\d+),(\d+),(\d+)", p)
                if a and int(a.group(4)) > 0:   # (a card not placed yet reports 0,0,0,0: not a position)
                    i = a.group(1); pos[i] = (int(a.group(2)), int(a.group(4)))
                    if i != cur and (cur is None or cur not in pos or True):
                        # the card that appeared last is the selected one (the old one is disposed with it)
                        if i not in [s[1] for s in series[-1:]] and i != cur: cur = i
            if cur in pos: series.append((t, cur, pos[cur][0], pos[cur][1]))
    return series, keys

def selection(path, step_by=1):
    """Update 390: works on EVERY layout (no card geometry needed): the app's own `focus N <path>` events against the keys of the
    `MARK seq` line. One event per press, a step of exactly one (a wrap only from one end to the other), the right way round."""
    seq, idx = [], []
    for ln in open(path, errors="ignore"):
        m = re.search(r"MARK seq (.*)", ln)
        if m: seq = [t for t in m.group(1).split() if t in ("R", "L")]
        m = re.search(r"VoltcapQA: \d+ focus (\d+) ", ln)
        if m: idx.append(int(m.group(1)))
    if not seq or len(idx) < 2: print(f"  selection: NO DATA (keys={len(seq)} focus events={len(idx)})"); return 2
    d = [b - a for a, b in zip(idx, idx[1:])]
    bad = []
    # (the first focus event is already the result of press 1: its start is unknown, so the changes belong to presses 2..)
    if len(d) != len(seq) - 1: bad.append(f"{len(seq)} presses but {len(idx)} focus events")
    top = max(idx)
    for n, (k, step) in enumerate(zip(seq[1:], d), 2):
        want = step_by if k == "R" else -step_by
        wrap = abs(step) > step_by and (step > 0) != (want > 0) and abs(step) >= max(2, top // 2)   # (Endless: the last game to the first)
        if step != want and not wrap: bad.append(f"press {n} ({k}): focus {idx[n-2]} -> {idx[n-1]}")
    print(f"  selection: {len(seq)} presses, {len(idx)} focus events, " + ("OK (one step each)" if not bad else "FAIL " + "; ".join(bad[:5])))
    return 0 if not bad else 1

def report(path):
    series, keys = parse(path)
    pk = [(t, k) for t, k in keys if k in ("DPAD_RIGHT", "DPAD_LEFT")]
    if not series or not pk:
        print(f"{path}: NO DATA (series={len(series)} presses={len(pk)})"); return 2
    print(f"== {path}: {len(pk)} presses")
    drifts, jumps, exc = [], [], []
    for n, (t, k) in enumerate(pk):
        end = pk[n + 1][0] if n + 1 < len(pk) else t + 1200
        end = min(end, t + 1200)
        before = [s for s in series if s[0] <= t]
        win = [s for s in series if t < s[0] <= end]
        if not before or not win: continue
        x0 = before[-1][2]; xe = win[-1][2]; w = win[-1][3] or 1
        xs = [x0] + [s[2] for s in win]
        jump = max(abs(b - a) for a, b in zip(xs, xs[1:]))
        ex = max(abs(x - xe) for x in xs[1:])
        frames = len(win)
        drifts.append(abs(xe - x0)); jumps.append(jump / w); exc.append(ex / w)
        print(f"  {k[6:]:5s} x {x0:5d} -> {xe:5d} (drift {xe-x0:+5d})  max 1-frame jump {jump:4d}px ({jump/w:4.2f} card)  excursion {ex:4d}px ({ex/w:4.2f})  {frames} frames, {end-t} ms")
    rest = [s_[2] for s_ in [([q for q in series if q[0] <= t + min(1200, (pk[n+1][0] - t if n + 1 < len(pk) else 1200))] or [(0,0,0,0)])[-1] for n, (t, k) in enumerate(pk) if (pk[n+1][0] - t if n + 1 < len(pk) else 1200) >= 1000]]
    if rest: print(f"  rests (>= 1 s after a press): selected card x in {min(rest)}..{max(rest)} ({len(rest)} rests) -> " + ("FIXED SLOT" if max(rest) - min(rest) <= 2 else "MOVES"))
    slow = sum(1 for e, j in zip(exc, jumps) if e > 0.5 and j > 0.5)
    print(f"  -> presses with the selected card displaced by > half a card then gliding back: {slow}/{len(drifts)}; max drift {max(drifts)} px")
    return 0

if __name__ == "__main__":
    # --step=N: a layout whose ▶ moves N games (Channel grid: 3 rows, column by column, N=3)
    step_by = next((int(a.split("=")[1]) for a in sys.argv[1:] if a.startswith("--step=")), 1)
    for p in [a for a in sys.argv[1:] if not a.startswith("--")]:
        report(p); selection(p, step_by)

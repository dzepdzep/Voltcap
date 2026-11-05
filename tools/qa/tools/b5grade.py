#!/usr/bin/env python3
"""VoltCap QA (update 482, B5) — grade a b5.sh run: per layout the focus steps / selected-card geometry (rowaudit.py) and
how much of the screen still changes between 0.6 s and 3 s after a press (late labels, stale art, placeholders).
  python3 b5grade.py DIR      (DIR has <L>_geom.txt, <L>_a.png, <L>_b.png)   -> one line per layout
'late' = share of the screen (in 8x8 blocks) that differs by > 24 levels; the box is where. Animated backgrounds also move:
the flagged layouts are checked by eye (side-by-side in DIR/<L>_ab.png)."""
import sys, os, re, subprocess
from PIL import Image, ImageChops
d = sys.argv[1]; here = os.path.dirname(os.path.abspath(__file__))
lays = sorted({f[:-9] for f in os.listdir(d) if f.endswith("_geom.txt")})
for L in lays:
    out = subprocess.run([sys.executable, os.path.join(here, "rowaudit.py"), os.path.join(d, L + "_geom.txt")],
                         capture_output=True, text=True).stdout
    sel = re.search(r"selection: (.*)", out); sel = sel.group(1) if sel else "?"
    jumps = re.findall(r"jump +(\d+)px", out); jmax = max(map(int, jumps)) if jumps else None
    rest = re.search(r"-> (FIXED SLOT|MOVES)", out); rest = rest.group(1) if rest else "-"
    late = "no stills"
    a, b = (os.path.join(d, f"{L}_{s}.png") for s in "ab")
    if os.path.exists(a) and os.path.exists(b):
        A = Image.open(a).convert("L"); B = Image.open(b).convert("L")
        w, h = A.size[0] // 8, A.size[1] // 8
        df = ImageChops.difference(A, B).resize((w, h), Image.BOX).point(lambda v: 255 if v > 24 else 0)
        px = df.load(); on = [(x, y) for y in range(h) for x in range(w) if px[x, y]]
        if on:
            xs, ys = [p[0] for p in on], [p[1] for p in on]
            late = f"{100*len(on)/(w*h):.1f}% box {min(xs)*8},{min(ys)*8}-{max(xs)*8+8},{max(ys)*8+8}"
            s = Image.new("RGB", (A.size[0] * 2 // 3, A.size[1] // 3)); s.paste(Image.open(a).resize((A.size[0]//3, A.size[1]//3)), (0, 0))
            s.paste(Image.open(b).resize((A.size[0]//3, A.size[1]//3)), (A.size[0]//3, 0)); s.save(os.path.join(d, L + "_ab.png"))
        else: late = "0%"
    print(f"{L:15} sel: {sel[:70]:70} | jump {jmax} {rest} | late {late}")

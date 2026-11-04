#!/usr/bin/env python3
"""Update 474 — is a QA fixture picture (QaFixture: a white frame 4 % in from every edge + a white circle in the middle) drawn
without stretching, and how much of it is cropped?  Real artwork has no known shape, so this measures FIXTURE art only.

fit(png, box) -> dict(status, stretch, sides, reason):
  The white strokes are found RELATIVE to their surroundings (brighter than the row's median and nearly grey), so a layout's
  shading / scrim over the art doesn't hide them. Long straight strokes are the frame; the largest remaining connected
  stroke is the circle.
  stretch  the circle's width / height as drawn (1.0 = round; a stretched picture draws an ellipse)
  sides    which frame lines are visible (top / bottom / left / right): a missing side = cropped there
  status   FAIL when the whole circle is visible and drawn more than 8 % out of round (a picture must never be distorted);
           PASS otherwise. Cropping is reported (a WARN in reports: a FILL crop can be the layout's design — artistic
           cropping), never a FAIL by itself. NOT TESTED when no whole fixture circle is found in the box (not fixture art,
           covered, cut by the crop, or too small to measure).
  python3 artfit.py SHOT.png X Y W H
"""
import sys

MAX_SIDE = 220   # measured on a copy at most this big (speed); ratios don't change


def _mask(png, box):
    from PIL import Image
    im = Image.open(png).convert("RGB")
    x, y, w, h = [int(v) for v in box]
    x0, y0, x1, y1 = max(0, x), max(0, y), min(im.width, x + w), min(im.height, y + h)
    if x1 - x0 < 24 or y1 - y0 < 24:
        return None, 0, 0
    c = im.crop((x0, y0, x1, y1))
    k = max(c.width, c.height) / MAX_SIDE
    if k > 1:
        c = c.resize((max(1, round(c.width / k)), max(1, round(c.height / k))))
    W, H = c.size
    px = c.load()
    m = []
    for j in range(H):
        row = [px[i, j] for i in range(W)]
        lum = [(r * 299 + g * 587 + b * 114) // 1000 for r, g, b in row]
        med = sorted(lum)[W // 2]
        m.append([lum[i] >= max(90, min(med + 40, 200)) and max(row[i]) - min(row[i]) < 48 for i in range(W)])
    return m, W, H


def _components(m, W, H):
    seen = [[False] * W for _ in range(H)]
    comps = []
    for j in range(H):
        for i in range(W):
            if m[j][i] and not seen[j][i]:
                st, pts = [(i, j)], []
                seen[j][i] = True
                while st:
                    a, b = st.pop(); pts.append((a, b))
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                        u, v = a + da, b + db
                        if 0 <= u < W and 0 <= v < H and m[v][u] and not seen[v][u]:
                            seen[v][u] = True; st.append((u, v))
                comps.append(pts)
    return comps


def fit(png, box):
    m, W, H = _mask(png, box)
    if m is None:
        return dict(status="NOT TESTED", stretch=None, sides={}, reason="box too small / off the picture")
    rows = [sum(r) / W for r in m]
    cols = [sum(m[j][i] for j in range(H)) / H for i in range(W)]
    line_r = {j for j, v in enumerate(rows) if v > 0.55}
    line_c = {i for i, v in enumerate(cols) if v > 0.55}
    band = lambda n: max(2, int(n * 0.14))
    sides = {"top": any(j < band(H) for j in line_r), "bottom": any(j >= H - band(H) for j in line_r),
             "left": any(i < band(W) for i in line_c), "right": any(i >= W - band(W) for i in line_c)}
    # The frame's lines (and a pixel either side) out; what's left: the circle, the label's letters, layout overlays.
    for j in range(H):
        for i in range(W):
            if m[j][i] and (any(abs(j - r) <= 1 for r in line_r) or any(abs(i - c) <= 1 for c in line_c)):
                m[j][i] = False
    comps = [c for c in _components(m, W, H) if len(c) >= 12]
    if not comps:
        return dict(status="NOT TESTED", stretch=None, sides=sides, reason="no fixture circle found (not QA fixture art?)")
    ring = max(comps, key=lambda c: (max(p[0] for p in c) - min(p[0] for p in c)) * (max(p[1] for p in c) - min(p[1] for p in c)))
    xs, ys = [p[0] for p in ring], [p[1] for p in ring]
    cw, ch = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1
    # (A ring cut by the crop, or touching the box's edge, isn't a whole circle: nothing to measure.)
    if min(xs) <= 0 or min(ys) <= 0 or max(xs) >= W - 1 or max(ys) >= H - 1:
        return dict(status="NOT TESTED", stretch=None, sides=sides, reason="the circle is cut by the box (cropped / zoomed): its shape can't be measured")
    if cw < 10 or ch < 10:
        return dict(status="NOT TESTED", stretch=None, sides=sides, reason="no whole circle found (a stroke too small)")
    # (Hollow in the middle = a ring; a solid blob — a label, a badge — is not the circle.)
    cx, cy = (min(xs) + max(xs)) // 2, (min(ys) + max(ys)) // 2
    if any(abs(p[0] - cx) < cw * 0.2 and abs(p[1] - cy) < ch * 0.2 for p in ring):
        return dict(status="NOT TESTED", stretch=None, sides=sides, reason="the biggest white shape is not a ring (not fixture art?)")
    # (Measured on the ring's upper half: a layout's dark scrim over the lower part of the art (Voltcap row's capsule, the
    # Odin's Classic card) fades the lower arc into the background. Width = the widest row; height = twice the distance from
    # the top of the ring to that row.)
    span = {}
    for a, b in ring:
        lo, hi = span.get(b, (a, a)); span[b] = (min(lo, a), max(hi, a))
    wmax = max(hi - lo for lo, hi in span.values())
    wide = [b for b, (lo, hi) in span.items() if hi - lo >= wmax - 1]   # the equator: the middle of the widest rows
    yw = (min(wide) + max(wide)) / 2
    half = yw - min(ys)
    if max(ys) - yw < max(3, half * 0.3):
        return dict(status="NOT TESTED", stretch=None, sides=sides,
                    reason="only the circle's upper part is visible (cut or faded below its middle): its shape can't be measured")
    est_h = 2 * half + 1 if half >= 4 else ch
    ratio = cw / max(est_h, ch if ch > est_h * 1.05 else est_h)
    cropped = [k for k, v in sides.items() if not v]
    if abs(ratio - 1) > 0.08:
        return dict(status="FAIL", stretch=round(ratio, 3), sides=sides, reason=f"stretched: the circle is drawn {ratio:.2f} : 1")
    return dict(status="PASS", stretch=round(ratio, 3), sides=sides,
                reason="not stretched" + (f"; cropped at {', '.join(cropped)}" if cropped else "; whole picture shown"))


if __name__ == "__main__":
    if len(sys.argv) != 6:
        print(__doc__); sys.exit(2)
    r = fit(sys.argv[1], [float(v) for v in sys.argv[2:6]])
    print(r)
    sys.exit({"PASS": 0, "FAIL": 1}.get(r["status"], 3))

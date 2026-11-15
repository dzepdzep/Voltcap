#!/usr/bin/env python3
"""Batch 521b: controller-glyph centring check on real screenshots (QA rule R26 add-on, used with popclip / any QaClip dump).

usage: python3 glyphcheck.py [--json out.json] [--crops DIR] PAIR...   where PAIR is shot.png:dump.json (QaClip dump of the same
screen), or a directory holding NAME.png + NAME.json pairs (popclip's <out>/<step>.png with qa_clip/<tag>_<step>.json copied in
as <step>.json).

Candidates: every InkGlyph ("glyphs" in the dump: the letter's ink box) and every laid-out text that is a controller label
(A B X Y, L1 L2 R1 R2, L3 R3, ZL ZR, LB RB LT RT, START SELECT MENU, P1, ◀ ▶ ▲ ▼ and the PlayStation shapes).
For each: the fill colour right round the ink, then a walk outward in the four directions to the edge of the circle / pill it
sits in; the ink's own box from the pixels that differ from the fill. Reported: the ink centre's offset from the shape's centre
(dx, dy in px and in % of the shape). FAIL when |offset| > max(1.5 px, 5 % of the shape's size) on either axis; NO-SHAPE when no
closed shape is found round it (a glyph drawn straight on the background — not graded), or the area is no button shape;
HIDDEN when an InkGlyph in the dump has no ink on the screenshot (covered by a popup / page); exit 1 when anything FAILs.
A passing run proves centring only for what was on those screenshots: look at the crops of anything near the limit.
"""
import json, os, re, sys
from PIL import Image

LABEL = re.compile(r'^(A|B|X|Y|L|R|L1|L2|R1|R2|L3|R3|ZL|ZR|LB|RB|LT|RT|START|SELECT|MENU|HOME|P1|◀|▶|▲|▼|✕|○|△|□|◀ ▶|▲▼)$')


def diff(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def median_colour(px):
    px = sorted(px, key=lambda c: c[0] + c[1] + c[2])
    return px[len(px) // 2]


def check(img, box, is_ink_box, text=''):
    text_is_arrow = text.strip() in ('◀', '▶', '▲', '▼', '◀ ▶', '▲▼')
    W, H = img.size
    p = img.load()
    x, y, w, h = box
    if w < 3 or h < 3 or x < 1 or y < 1 or x + w >= W - 1 or y + h >= H - 1:
        return {'verdict': 'SKIP', 'why': 'off screen / too small'}
    # fill: the colour just outside the ink box (corners and edge midpoints, 2 px out)
    ring = []
    for (sx, sy) in [(x - 2, y - 2), (x + w + 1, y - 2), (x - 2, y + h + 1), (x + w + 1, y + h + 1),
                     (x + w // 2, y - 3), (x + w // 2, y + h + 2), (x - 3, y + h // 2), (x + w + 2, y + h // 2)]:
        if 0 <= sx < W and 0 <= sy < H:
            ring.append(p[sx, sy][:3])
    if not ring:
        return {'verdict': 'SKIP', 'why': 'no fill'}
    fill = median_colour(ring)
    T = 60
    STEP = 40
    # ink box: for a plain Text the layout box is not the ink - find the ink pixels inside it
    if is_ink_box:
        ix0, iy0, ix1, iy1 = x + 1, y + 1, x + w - 2, y + h - 2
        # (Batch 522: a glyph under a popup / page is still in the dump - nothing of it on the screenshot means not shown.)
        inked = sum(1 for yy in range(iy0, iy1 + 1) for xx in range(ix0, ix1 + 1) if diff(p[xx, yy][:3], fill) > T)
        if inked < max(3, 0.06 * (ix1 - ix0 + 1) * (iy1 - iy0 + 1)):
            return {'verdict': 'HIDDEN', 'why': 'no ink on the screenshot (covered)'}
    else:
        xs, ys = [], []
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if diff(p[xx, yy][:3], fill) > T:
                    xs.append(xx); ys.append(yy)
        # (Batch 522: a label in the dump with (almost) nothing of it drawn - under a popup, scrolled away - is not shown.)
        if len(xs) < max(3, 0.06 * w * h):
            return {'verdict': 'HIDDEN', 'why': 'no ink on the screenshot (covered)'}
        ix0, ix1, iy0, iy1 = min(xs), max(xs), min(ys), max(ys)
        # (Batch 522: "ink" from edge to edge of the label's own box on either axis is a background edge or another control
        # under it, not the glyph's letter - a letter never fills its line box top to bottom.)
        if iy1 - iy0 + 1 >= 0.95 * h or (ix1 - ix0 + 1 >= 0.95 * w and iy1 - iy0 + 1 >= 0.8 * h):
            # (A label drawn on its own key - the Text's box IS the key, e.g. Terminal's green A / B / X / Y blocks: one colour
            # filling the box. Measured inside the box against the box itself.)
            inner = [p[xx, yy][:3] for yy in range(y + 1, y + h - 1, max(1, h // 8)) for xx in (x + 2, x + w - 3)]
            kf = median_colour(inner)
            if sum(1 for c in inner if diff(c, kf) <= T) >= 0.8 * len(inner) and diff(kf, fill) > T:
                ks = [(xx, yy) for yy in range(y, y + h) for xx in range(x, x + w) if diff(p[xx, yy][:3], kf) > T]
                kx = [q[0] for q in ks if x + 1 < q[0] < x + w - 2]; ky = [q[1] for q in ks if y + 1 < q[1] < y + h - 2]
                if kx and ky:
                    kx0, kx1, ky0, ky1 = min(kx), max(kx), min(ky), max(ky)
                    dx, dy = (kx0 + kx1) / 2.0 - (x + (w - 1) / 2.0), (ky0 + ky1) / 2.0 - (y + (h - 1) / 2.0)
                    limx, limy = max(1.5, 0.05 * w), max(1.5, 0.05 * h)
                    return {'verdict': 'FAIL' if abs(dx) > limx or abs(dy) > limy else 'PASS', 'dx': round(dx, 1), 'dy': round(dy, 1),
                            'dxPct': round(100 * dx / w, 1), 'dyPct': round(100 * dy / h, 1), 'shape': [x, y, w, h],
                            'ink': [kx0, ky0, kx1 - kx0, ky1 - ky0], 'fill': kf, 'why': 'label on its own key (the text box)'}
            return {'verdict': 'NO-SHAPE', 'why': 'ink fills the label box (an edge or a control behind it)'}
    cx, cy = (ix0 + ix1) / 2.0, (iy0 + iy1) / 2.0
    lim = int(3 * max(w, h) + 24)

    def walk(sx, sy, dx, dy, label_aware=False):
        # from just outside the ink, along the fill, to the first 2 px that are not the fill
        n, miss = 0, 0
        xx, yy = sx, sy
        prev = None
        while n < lim:
            xx += dx; yy += dy; n += 1
            if not (0 <= xx < W and 0 <= yy < H):
                return None
            cur = p[xx, yy][:3]
            # (Update 529c: a see-through pill over a dark picture - Spotlight's SELECT key, mlast529 - differs from the background
            # behind it by less than T, so the walk ran through its edge and measured the glyph 40 px "off centre" in a pill it sits
            # in the middle of. A sharp step between two neighbouring pixels into a region that stays steady is an edge too. Arrows
            # keep the label-aware rule below: the fill coming back right after it means a letter, not an edge.)
            if prev is not None and diff(cur, prev) > STEP and diff(cur, fill) > STEP // 2:
                steady = all(0 <= xx + dx * k < W and 0 <= yy + dy * k < H and diff(p[xx + dx * k, yy + dy * k][:3], cur) <= 18
                             for k in range(1, 4))
                back = label_aware and any(0 <= xx + dx * k < W and 0 <= yy + dy * k < H and diff(p[xx + dx * k, yy + dy * k][:3], fill) <= 12
                                           for k in range(1, 25))
                if steady and not back:
                    return n - 1
            prev = cur
            if diff(cur, fill) > T:
                miss += 1
                if miss == 2 and label_aware:
                    # (Batch 522: an edge only when the fill does not come back right after it - a letter of a label beside
                    # the glyph ("◀ Saves") is a few px of ink with the same fill behind it, a circle / pill's edge is not.)
                    run = 0
                    for k in range(1, 25):
                        qx, qy = xx + dx * k, yy + dy * k
                        if not (0 <= qx < W and 0 <= qy < H):
                            break
                        run = run + 1 if diff(p[qx, qy][:3], fill) <= T else 0
                        if run >= 3:
                            return None
                    return n - 1
                if miss == 2:
                    return n - 1
            else:
                miss = 0
        return None
    my, mx = int(round(cy)), int(round(cx))
    # (Batch 522: an arrow beside a label - "◀ Saves ▶" - would measure against the label's first letter; for arrows an
    # edge with the fill right behind it is a letter, not the shape. Letters / names keep plain edges: outlined keys.)
    arrow = text_is_arrow
    l = walk(ix0, my, -1, 0, arrow); r = walk(ix1, my, 1, 0, arrow); t = walk(mx, iy0, 0, -1, arrow); b = walk(mx, iy1, 0, 1, arrow)
    if None in (l, r, t, b):
        return {'verdict': 'NO-SHAPE', 'fill': fill, 'ink': [ix0, iy0, ix1, iy1]}
    # (Batch 522: ink touching the "shape" on a side is not a glyph in its key - it is part of something else drawn over it,
    # e.g. a popup's Play button over the home's ▲ ▼ in the same window (Cube, Shared style).)
    if min(l, r, t, b) < 2:
        return {'verdict': 'NO-SHAPE', 'why': 'ink touches the shape edge (not a glyph in its own key)', 'fill': fill, 'ink': [ix0, iy0, ix1, iy1]}
    sx0, sx1, sy0, sy1 = ix0 - l, ix1 + r, iy0 - t, iy1 + b
    scx, scy = (sx0 + sx1) / 2.0, (sy0 + sy1) / 2.0
    sw, sh = sx1 - sx0, sy1 - sy0
    # (Batch 522: a "shape" far bigger than a button - a page, a card, a popup's button the glyph happens to sit on - is not the
    # glyph's circle / pill: a circle or pill is at most ~4x the ink's height, and no wider than the ink plus 2 heights.)
    inkh = iy1 - iy0 + 1
    if sh > max(4 * inkh, inkh + 40) or sw > (ix1 - ix0 + 1) + 2.5 * sh:
        return {'verdict': 'NO-SHAPE', 'why': 'surrounding area is not a button shape (%dx%d)' % (sw, sh), 'fill': fill, 'ink': [ix0, iy0, ix1, iy1]}
    # (Batch 522: a glyph that shares its shape with other ink - "▶ PLAY", "◀ Saves", a button's own label - is a labelled
    # button, not a glyph in its own circle / pill: its centring is the label's layout, not graded here.)
    m = max(2, int(0.18 * min(sw, sh)))
    other = 0
    for yy in range(sy0 + m, sy1 - m + 1):
        for xx in range(sx0 + m, sx1 - m + 1):
            if ix0 - 1 <= xx <= ix1 + 1 and iy0 - 1 <= yy <= iy1 + 1:
                continue
            if diff(p[xx, yy][:3], fill) > T:
                other += 1
    if other > max(4, 0.03 * sw * sh):
        return {'verdict': 'NO-SHAPE', 'why': 'shares its button with other ink (a labelled button)', 'fill': fill, 'ink': [ix0, iy0, ix1, iy1]}
    dx, dy = cx - scx, cy - scy
    limx, limy = max(1.5, 0.05 * sw), max(1.5, 0.05 * sh)
    v = 'FAIL' if abs(dx) > limx or abs(dy) > limy else 'PASS'
    return {'verdict': v, 'dx': round(dx, 1), 'dy': round(dy, 1), 'dxPct': round(100 * dx / max(sw, 1), 1), 'dyPct': round(100 * dy / max(sh, 1), 1),
            'shape': [sx0, sy0, sw, sh], 'ink': [ix0, iy0, ix1 - ix0, iy1 - iy0], 'fill': fill}


def candidates(dump):
    out = []
    wins = dump.get('windows', [])
    for wi, win in enumerate(wins):
        ox, oy = win['at'][0], win['at'][1]
        # (Batch 522: windows are listed bottom to top - a glyph of a window under a later one (a popup / page dialog over
        # the home) is not what the screen shows there: left out.)
        above = [w['at'] for w in wins[wi + 1:] if len(w.get('at', [])) == 4 and (w.get('nodes') or w.get('texts'))]

        def covered(b):
            cx, cy = b[0] + b[2] / 2.0, b[1] + b[3] / 2.0
            return any(a[0] <= cx <= a[0] + a[2] and a[1] <= cy <= a[1] + a[3] for a in above)
        for g in win.get('glyphs', []):
            if g.get('placed', True):
                s = g['shown']; b = (s[0] + ox, s[1] + oy, s[2], s[3])
                if not covered(b):
                    out.append((g['text'], b, True))
        for t in win.get('texts', []):
            if LABEL.match(t['text'].strip()) and t.get('placed', True):
                s = t['shown']; b = (s[0] + ox, s[1] + oy, s[2], s[3])
                if not covered(b):
                    out.append((t['text'].strip(), b, False))
    return out


def pairs(args):
    for a in args:
        if os.path.isdir(a) and os.path.isdir(os.path.join(a, 'qa_clip')):
            # a popclip / navclip run folder: <step>.png + qa_clip/<tag>_<step>.json
            tag = os.path.basename(os.path.normpath(a))
            for f in sorted(os.listdir(a)):
                j = os.path.join(a, 'qa_clip', '%s_%s.json' % (tag, f[:-4]))
                if f.endswith('.png') and os.path.isfile(j):
                    yield os.path.join(a, f), j
        elif os.path.isdir(a):
            for f in sorted(os.listdir(a)):
                if f.endswith('.png') and os.path.isfile(os.path.join(a, f[:-4] + '.json')):
                    yield os.path.join(a, f), os.path.join(a, f[:-4] + '.json')
        elif ':' in a:
            yield tuple(a.rsplit(':', 1))


def main():
    argv = sys.argv[1:]
    jout = crops = None
    if '--json' in argv:
        i = argv.index('--json'); jout = argv[i + 1]; del argv[i:i + 2]
    if '--crops' in argv:
        i = argv.index('--crops'); crops = argv[i + 1]; del argv[i:i + 2]; os.makedirs(crops, exist_ok=True)
    results, fails = [], 0
    for png, js in pairs(argv):
        try:
            dump = json.load(open(js, encoding='utf-8'))
        except Exception as e:
            print('SKIP %s: %s' % (js, e)); continue
        img = Image.open(png).convert('RGB')
        for i, (text, box, ink) in enumerate(candidates(dump)):
            r = check(img, box, ink, text)
            r.update({'screen': os.path.basename(png), 'text': text, 'box': list(box), 'source': 'InkGlyph' if ink else 'Text'})
            results.append(r)
            if r['verdict'] == 'FAIL':
                fails += 1
            if crops and r.get('shape'):
                sx, sy, sw, sh = r['shape']; m = 6
                c = img.crop((sx - m, sy - m, sx + sw + m, sy + sh + m)).resize(((sw + 2 * m) * 6, (sh + 2 * m) * 6), Image.NEAREST)
                c.save(os.path.join(crops, '%s_%s_%02d_%s.png' % (r['verdict'], os.path.basename(png)[:-4], i, re.sub(r'\W', '_', text))))
    by = {}
    for r in results:
        by[r['verdict']] = by.get(r['verdict'], 0) + 1
    for r in results:
        if r['verdict'] in ('FAIL',) or (r['verdict'] == 'PASS' and (abs(r['dxPct']) > 4 or abs(r['dyPct']) > 4)):
            print('%-5s %-28s %-7s %-8s dx=%s (%s%%) dy=%s (%s%%) shape=%s' % (r['verdict'], r['screen'][:28], r['text'], r['source'],
                                                                         r['dx'], r['dxPct'], r['dy'], r['dyPct'], r['shape']))
    print('glyphcheck: %s' % ', '.join('%s=%d' % kv for kv in sorted(by.items())) if by else 'glyphcheck: no glyphs found')
    if jout:
        json.dump(results, open(jout, 'w'), indent=1)
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()

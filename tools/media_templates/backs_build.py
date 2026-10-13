# Update 170: the cases' printed backs from the template pack (C:\Users\User\Downloads\Templates):
# each console's "cover - back" picture (or the back part of its cover texture), its screenshot windows and the plain
# band where Voltcap prints the game's screenshots and description. Writes res/drawable-nodpi/tpl_back_<id>.webp and
# backs.json (→ ui/MediaBacks.kt).
import json, sys, numpy as np, cv2
from PIL import Image
B = sys.argv[1] if len(sys.argv) > 1 else '/mnt/user-data/uploads/Templates/'
OUT = sys.argv[2] if len(sys.argv) > 2 else 'out/'
plan = json.load(open('backs_plan.json'))

def from_texture(path):
    """The back of a cover texture: everything left of the spine (the narrow opaque strip near the middle)."""
    im = Image.open(path).convert('RGBA'); a = np.array(im)[:, :, 3]; H, W = a.shape
    frac = (a > 128).mean(0); good = frac > 0.6; c = W // 2
    idx = np.nonzero(good)[0]
    if len(idx) == 0: return None
    j = idx[np.abs(idx - c).argmin()]
    if abs(j - c) > 0.12 * W: return None
    l = j
    while l > 0 and good[l - 1]: l -= 1
    r = j
    while r < W - 1 and good[r + 1]: r += 1
    if (r - l + 1) > 0.25 * W: return None
    return im.crop((0, 0, l, H))

def analyse(im):
    a = np.array(im); al = a[:, :, 3]; H, W = al.shape
    g = cv2.cvtColor(a[:, :, :3], cv2.COLOR_RGB2GRAY).astype(np.float32)
    m = cv2.blur(g, (9, 9)); sd = np.sqrt(np.maximum(cv2.blur(g * g, (9, 9)) - m * m, 0))
    clear = al < 20
    plain = (al > 200) & (sd < 5)
    # Screenshot windows: see-through rectangles inside the picture (not touching its edge).
    wins = []
    n, lab, st, _ = cv2.connectedComponentsWithStats(clear.astype(np.uint8), connectivity=4)
    for k in range(1, n):
        x, y, w, h, ar = st[k]
        if x <= 2 or y <= 2 or x + w >= W - 2 or y + h >= H - 2: continue
        if w * h < 0.015 * W * H or ar < 0.85 * w * h: continue
        wins.append([round(x / W, 4), round(y / H, 4), round((x + w) / W, 4), round((y + h) / H, 4)])
    # (One window over another — a frame drawn inside a bigger hole: the bigger one only.)
    wins.sort(key=lambda r: -(r[2] - r[0]) * (r[3] - r[1]))
    keep = []
    for r in wins:
        if all(r[2] <= q[0] or r[0] >= q[2] or r[3] <= q[1] or r[1] >= q[3] for q in keep): keep.append(r)
    wins = keep
    wins.sort(key=lambda r: (round(r[1], 1), r[0]))
    wins = wins[:4]
    free = clear | plain
    for (x0, y0, x1, y1) in wins: free[int(y0 * H):int(y1 * H), int(x0 * W):int(x1 * W)] = False
    # The biggest plain band of rows, trying the full width and with a side strip left out (a stripe down one side).
    best = (0, 0, 0, 0.06, 0.94)
    for (fx0, fx1) in [(0.06, 0.94), (0.16, 0.94), (0.06, 0.84), (0.16, 0.84)]:
        xs, xe = int(fx0 * W), int(fx1 * W)
        rows = free[:, xs:xe].mean(1) > 0.97
        s = None
        for y in range(H + 1):
            if y < H and rows[y]:
                if s is None: s = y
            elif s is not None:
                if (y - s) * (fx1 - fx0) > best[0] * (best[4] - best[3]) * 1.15: best = (y - s, s, y, fx0, fx1)
                s = None
    band = None
    if best[0] >= 0.14 * H:
        y0, y1, fx0, fx1 = best[1], best[2], best[3], best[4]
        xs, xe = int(fx0 * W), int(fx1 * W)
        band = [fx0, round(y0 / H + 0.015, 4), fx1, round(y1 / H - 0.015, 4)]
        reg = a[y0:y1, xs:xe]; ra = reg[:, :, 3]
        over_art = (ra < 20).mean() > 0.5
        if over_art: ink = 'light'
        else:
            lum = cv2.cvtColor(reg[:, :, :3], cv2.COLOR_RGB2GRAY)[ra > 200].mean()
            ink = 'light' if lum < 140 else 'dark'
    else:
        over_art = (clear.mean() > 0.5); ink = 'light'
    return wins, band, bool(over_art), ink

table = {}
for p, v in plan.items():
    if v['back']: im = Image.open(B + v['back'][0]).convert('RGBA'); src = v['back'][0]
    elif v['tex']:
        im = from_texture(B + v['tex'][0]); src = v['tex'][0] + ' (back part)'
        if im is None: print('no back', p); continue
    else: continue
    wins, band, over_art, ink = analyse(im)
    out = im.copy(); out.thumbnail((560, 560), Image.LANCZOS)
    out.save(OUT + f'tpl_back_{p.lower()}.webp', 'WEBP', quality=84, method=6)
    table[p] = dict(src=src, aspect=round(im.width / im.height, 4), wins=wins, band=band, overArt=over_art, ink=ink)
    print(p.ljust(14), im.size, 'wins', len(wins), 'band', band, 'art' if over_art else 'printed', ink)
json.dump(table, open('backs.json', 'w'), indent=1)

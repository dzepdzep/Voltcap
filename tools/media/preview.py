import json, sys
from PIL import Image, ImageDraw
meta = json.load(open('out/meta.json'))
def fake_label(w, h):
    im = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h): d.line([(0, y), (w, y)], fill=(int(40 + 180 * y / h), int(80 + 60 * (1 - y / h)), 200 - int(120 * y / h)))
    d.ellipse([w * 0.55, h * 0.15, w * 0.95, h * 0.75], fill=(250, 210, 80))
    d.rectangle([w * 0.06, h * 0.08, w * 0.6, h * 0.22], fill=(255, 255, 255))
    return im
tiles = []
for n, m in meta.items():
    if 'label' not in m: continue
    t = Image.open(f'out/media_{n}.png')
    base = Image.new('RGBA', t.size, (48, 52, 64, 255))
    l = m['label']; x0, y0, x1, y1 = int(l[0] * t.width), int(l[1] * t.height), int(l[2] * t.width), int(l[3] * t.height)
    base.paste(fake_label(x1 - x0, y1 - y0), (x0, y0))
    base.alpha_composite(t)
    tiles.append(base)
W = sum(t.width for t in tiles) + 10 * len(tiles); H = max(t.height for t in tiles)
s = Image.new('RGB', (W, H), (48, 52, 64)); x = 0
for t in tiles: s.paste(t, (x, 0), t); x += t.width + 10
s.save('/tmp/media_sheet.png'); print(s.size)

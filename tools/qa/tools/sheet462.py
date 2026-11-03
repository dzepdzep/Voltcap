"""Update 462: the cartridge contact sheet from photo462.sh's screenshots — the same crop (the Inspect window's piece area)
for every subject and pose, one row per subject. usage: python3 sheet462.py DIR out.png [crop l,t,r,b as fractions]"""
import sys, os
from PIL import Image, ImageDraw
d, out = sys.argv[1], sys.argv[2]
crop = [float(v) for v in (sys.argv[3] if len(sys.argv) > 3 else "0.25,0.08,0.75,0.92").split(",")]
subs = [l.split(" ", 1) for l in open(os.path.join(d, "subjects.txt")).read().splitlines() if l.strip()]
poses = [("0", "front"), ("45", "45°"), ("75", "75°"), ("90", "90°"), ("180", "rear"), ("-25", "Inspect")]
cells = []
for sid, name in subs:
    row = []
    for y, _ in poses:
        im = Image.open(os.path.join(d, f"{sid}_y{y}.png")).convert("RGB")
        W, H = im.size
        row.append(im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H))))
    cells.append((name, row))
cw, ch = cells[0][1][0].size
lab = 26
sheet = Image.new("RGB", (cw * len(poses), (ch + lab) * len(cells) + lab), (14, 14, 18))
dr = ImageDraw.Draw(sheet)
for i, (_, p) in enumerate(poses): dr.text((i * cw + 6, 6), p, fill=(255, 210, 90))
for r, (name, row) in enumerate(cells):
    y0 = lab + r * (ch + lab)
    dr.text((6, y0 + 4), name, fill=(255, 210, 90))
    for i, c in enumerate(row): sheet.paste(c, (i * cw, y0 + lab))
sheet.save(out)
print(out, sheet.size)

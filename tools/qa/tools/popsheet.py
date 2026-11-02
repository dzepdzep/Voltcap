#!/usr/bin/env python3
"""v0.6.66 update 444: labelled contact sheets from device/popfinal.sh output folders.

usage: python3 popsheet.py OUT.pdf "Title" [--cover page.png ...] DIR[:Device label] [DIR[:label] ...]

One page per layout x style per device: home, the open recording at +0.15 / +0.3 / +0.6 s (ffmpeg), settled (+1.2 s),
rested (+5 s), D-pad ▶▶ and ▼, back on Play, mid-close (+0.25 s), after B, and the two rapid A/B checks. Each picture is
scaled whole (never cropped) into its cell. The run's log.txt notes (retries, windows left open) are printed under the title.
"""
import os, subprocess, sys, tempfile
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except OSError:
        return ImageFont.load_default()


def frame(mp4, t, tmp):
    out = os.path.join(tmp, f"{os.path.basename(mp4)}_{t}.png")
    r = subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", str(t), "-i", mp4, "-frames:v", "1", out], capture_output=True)
    return out if r.returncode == 0 and os.path.exists(out) else None


def cells_for(d, combo, tmp):
    p = lambda s: os.path.join(d, f"{combo}_{s}")
    cells = [("home (before A)", p("0home.png"))]
    for t in (0.65, 0.8, 1.1):  # the recording starts ~0.5 s before A
        cells.append((f"open +{t - 0.5:.2f} s", frame(p("open.mp4"), t, tmp) if os.path.exists(p("open.mp4")) else None))
    cells += [("settled +1.2 s", p("1settled.png")), ("rested +5 s", p("2rested.png")), ("D-pad ▶ ▶", p("3right2.png")),
              ("D-pad ▼", p("4down.png")), ("▲ ◀ ◀ (back)", p("5back.png"))]
    cells.append(("close +0.25 s", frame(p("close.mp4"), 0.75, tmp) if os.path.exists(p("close.mp4")) else None))
    cells += [("after B (same game?)", p("6afterB.png")), ("after A B A B", p("7rapid1.png")), ("after A B B", p("8rapid2.png"))]
    return cells


def page(title, sub, notes, cells):
    W, cols, pad = 2400, 4, 18
    cw = (W - pad * (cols + 1)) // cols
    ch = int(cw * 9 / 16)
    rows = (len(cells) + cols - 1) // cols
    head = 150 + 30 * len(notes)
    H = head + rows * (ch + 46 + pad) + pad
    img = Image.new("RGB", (W, H), (22, 24, 28))
    dr = ImageDraw.Draw(img)
    dr.text((pad, 20), title, fill=(255, 214, 102), font=font(BOLD, 40))
    dr.text((pad, 80), sub, fill=(220, 220, 220), font=font(FONT, 26))
    for i, n in enumerate(notes):
        dr.text((pad, 120 + 30 * i), "note: " + n, fill=(255, 150, 120), font=font(FONT, 24))
    for i, (label, path) in enumerate(cells):
        x = pad + (i % cols) * (cw + pad)
        y = head + (i // cols) * (ch + 46 + pad)
        dr.text((x, y), label, fill=(200, 230, 255), font=font(BOLD, 26))
        box = (x, y + 40, x + cw, y + 40 + ch)
        dr.rectangle(box, fill=(10, 10, 12))
        if path and os.path.exists(path):
            im = Image.open(path).convert("RGB")
            im.thumbnail((cw, ch))
            img.paste(im, (x + (cw - im.width) // 2, y + 40 + (ch - im.height) // 2))
        else:
            dr.text((x + 20, y + 60), "(missing)", fill=(160, 160, 160), font=font(FONT, 24))
    return img


def main():
    out, title, rest = sys.argv[1], sys.argv[2], sys.argv[3:]
    pages, dirs = [], []
    while rest:
        a = rest.pop(0)
        if a == "--cover":
            pages.append(Image.open(rest.pop(0)).convert("RGB"))
        else:
            dirs.append(a)
    with tempfile.TemporaryDirectory() as tmp:
        for spec in dirs:
            d, _, label = spec.partition(":")
            combos = sorted({f.rsplit("_", 1)[0] for f in os.listdir(d) if f.endswith("_0home.png")})
            log = open(os.path.join(d, "log.txt")).read().splitlines() if os.path.exists(os.path.join(d, "log.txt")) else []
            for c in combos:
                notes = [l for l in log if l.startswith(c + ":")]
                pages.append(page(f"{c} — {label or os.path.basename(d)}", title, notes, cells_for(d, c, tmp)))
        if not pages:
            sys.exit("no popfinal folders with *_0home.png")
        pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
    print(f"{out}: {len(pages)} pages")


if __name__ == "__main__":
    main()

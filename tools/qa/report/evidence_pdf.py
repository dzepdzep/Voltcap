"""Update 469: a readable evidence PDF from a JSON plan — sections of before/after rows of screenshots (optionally cropped
and enlarged for close-ups), with a status per row (VERIFIED / SHORTCOMING / BLOCKED / NOT TESTED) and a note.
usage: python3 evidence_pdf.py plan.json out.pdf
plan = {"title": str, "intro": [str], "sections": [{"title": str, "text": [str], "rows": [
          {"label": str, "status": str, "note": str, "images": [{"path": str, "crop": [x0,y0,x1,y1]|null, "caption": str}]}]}]}
Full-resolution originals are not embedded scaled down only: each image is placed at its crop's own resolution (up to the
page width), so close-ups stay sharp.
"""
import json, sys, os
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from PIL import Image

pdfmetrics.registerFont(TTFont("D", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DB", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
COL = {"VERIFIED": (0.10, 0.55, 0.25), "SHORTCOMING": (0.80, 0.50, 0.05), "BLOCKED": (0.75, 0.15, 0.15),
       "NOT TESTED": (0.45, 0.45, 0.45), "BEFORE": (0.3, 0.3, 0.6), "INFO": (0.2, 0.35, 0.6)}
W, H = landscape(A4)
M = 28

def wrap(c, text, font, size, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if c.stringWidth(t, font, size) > width and cur: lines.append(cur); cur = w
        else: cur = t
    if cur: lines.append(cur)
    return lines

class Doc:
    def __init__(self, out, title):
        self.c = canvas.Canvas(out, pagesize=(W, H)); self.c.setTitle(title); self.y = H - M; self.page = 1
    def new(self):
        self.c.setFont("D", 7); self.c.setFillColorRGB(0.5, 0.5, 0.5); self.c.drawRightString(W - M, 14, f"page {self.page}")
        self.c.showPage(); self.page += 1; self.y = H - M
    def need(self, h):
        if self.y - h < M: self.new()
    def text(self, s, size=10, font="D", color=(0, 0, 0), gap=3):
        for line in wrap(self.c, s, font, size, W - 2 * M):
            self.need(size + gap); self.c.setFont(font, size); self.c.setFillColorRGB(*color)
            self.c.drawString(M, self.y - size, line); self.y -= size + gap
    def row(self, r):
        imgs = []
        for im in r.get("images", []):
            if not os.path.exists(im["path"]): continue
            p = Image.open(im["path"]).convert("RGB")
            if im.get("crop"): p = p.crop(tuple(im["crop"]))
            imgs.append((p, im.get("caption", "")))
        hdr = 14
        avail = W - 2 * M
        if imgs:
            # One line of images: same height, as large as fits (never upscaled beyond 3x of the crop).
            hmax = 300 if len(imgs) <= 3 else 230
            ratio = sum(p.width / p.height for p, _ in imgs)
            h = min(hmax, (avail - 6 * (len(imgs) - 1)) / ratio, max(p.height for p, _ in imgs) * 3)
        else: h = 0
        note_lines = wrap(self.c, r.get("note", ""), "D", 8, avail)
        self.need(hdr + h + 12 + len(note_lines) * 10 + 8)
        st = r.get("status", "INFO"); col = COL.get(st, (0, 0, 0))
        self.c.setFillColorRGB(*col); self.c.roundRect(M, self.y - hdr + 2, 76, hdr - 2, 3, fill=1, stroke=0)
        self.c.setFillColorRGB(1, 1, 1); self.c.setFont("DB", 7.5); self.c.drawCentredString(M + 38, self.y - hdr + 6, st)
        self.c.setFillColorRGB(0, 0, 0); self.c.setFont("DB", 9.5); self.c.drawString(M + 84, self.y - hdr + 5, r.get("label", ""))
        self.y -= hdr + 2
        x = M
        for p, cap in imgs:
            w = p.width * h / p.height
            self.c.drawImage(ImageReader(p), x, self.y - h, w, h)
            if cap:
                self.c.setFillColorRGB(0, 0, 0); self.c.rect(x, self.y - h, min(w, self.c.stringWidth(cap, "D", 7) + 6), 10, fill=1, stroke=0)
                self.c.setFillColorRGB(1, 1, 0.6); self.c.setFont("D", 7); self.c.drawString(x + 3, self.y - h + 2.5, cap)
            x += w + 6
        self.y -= h + 4
        for line in note_lines:
            self.c.setFillColorRGB(0.15, 0.15, 0.15); self.c.setFont("D", 8); self.c.drawString(M, self.y - 8, line); self.y -= 10
        self.y -= 8

def main(plan_path, out):
    plan = json.load(open(plan_path))
    d = Doc(out, plan["title"])
    d.text(plan["title"], 18, "DB", gap=8)
    for t in plan.get("intro", []): d.text(t, 9.5)
    for s in plan["sections"]:
        d.need(60); d.y -= 6; d.text(s["title"], 13.5, "DB", (0.1, 0.2, 0.45), gap=5)
        for t in s.get("text", []): d.text(t, 9)
        for r in s.get("rows", []): d.row(r)
    d.new(); d.c.save()

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

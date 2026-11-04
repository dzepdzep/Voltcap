#!/usr/bin/env python3
"""Update 476 — before / after evidence for a fix: the same step of the same layout on the same device from two runs (two
builds), side by side at full resolution, with each run's graded popup result and its build evidence line. One PDF page per
pair; a pair whose screenshot is missing on either side says so ("NOT CAPTURED") — never another step's picture.

  python3 beforeafter.py SPEC.json OUT.pdf
SPEC: {"title": "...", "pairs": [{"defect": "D253", "layout": "CROSS", "step": "page", "device": "KONKR",
        "before": "RUN_DIR", "after": "RUN_DIR", "note": "what to look at"}], "style": "OWN"}
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import evidence
import popclip
import qalib

from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

FONT, BOLD = "DejaVu", "DejaVu-Bold"
pdfmetrics.registerFont(TTFont(FONT, "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont(BOLD, "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
COL = {qalib.PASS: (0.1, 0.55, 0.2), qalib.FAIL: (0.8, 0.1, 0.1), qalib.INFRA: (0.8, 0.45, 0.0), qalib.NOT_TESTED: (0.4, 0.4, 0.4)}


def ev_line(run):
    st, info, why = evidence.check_run(run)
    return evidence.label(info) if st == qalib.PASS else f"evidence {st}: {why}"


def wrap(c, text, x, y, w, font=FONT, size=8, lead=10):
    c.setFont(font, size)
    words, line = text.split(), ""
    for wd in words:
        t = (line + " " + wd).strip()
        if c.stringWidth(t, font, size) > w:
            c.drawString(x, y, line); y -= lead; line = wd
        else:
            line = t
    if line:
        c.drawString(x, y, line); y -= lead
    return y


def side(c, run, name, step, x, y, w, h, label):
    c.setFont(BOLD, 9); c.setFillColorRGB(0.1, 0.1, 0.1); c.drawString(x, y + h + 24, label)
    c.setFillColorRGB(0.3, 0.3, 0.3)
    wrap(c, ev_line(run), x, y + h + 6, w, size=6, lead=7)
    p = os.path.join(run, f"{name}_{step}.png")
    if not os.path.exists(p):
        c.setStrokeColorRGB(0.7, 0.7, 0.7); c.rect(x, y, w, h); c.setFont(FONT, 9)
        c.drawCentredString(x + w / 2, y + h / 2, "NOT CAPTURED (no screenshot of this step in this run)")
    else:
        ir = ImageReader(p); iw, ih = ir.getSize(); k = min(w / iw, h / ih)
        c.drawImage(ir, x + (w - iw * k) / 2, y + (h - ih * k) / 2, iw * k, ih * k)
    g = popclip.grade(run, name) if os.path.exists(os.path.join(run, f"{name}_main.png")) else {}
    yy = y - 12
    for k in popclip.CATS:
        st, why = g.get(k, (qalib.NOT_TESTED, "not graded"))
        c.setFillColorRGB(*COL.get(st, (0.3, 0.3, 0.3))); c.setFont(BOLD, 7)
        warn = st == qalib.PASS and why.startswith("WARN")
        c.drawString(x, yy, f"{k}: {'WARN' if warn else st}")
        c.setFillColorRGB(0.25, 0.25, 0.25); c.setFont(FONT, 6.5)
        t = why
        while t and c.stringWidth(t, FONT, 6.5) > w - 80:
            t = t[:-4]
        c.drawString(x + 78, yy, t + ("…" if t != why else ""))   # (the whole reason is in the run's popclip report)
        yy -= 9


def main():
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    W, H = landscape(A4)
    c = canvas.Canvas(sys.argv[2], pagesize=(W, H))
    st = spec.get("style", "OWN")
    for pr in spec["pairs"]:
        name = f"{pr['layout']}_{st}"
        c.setFont(BOLD, 14); c.setFillColorRGB(0, 0, 0)
        c.drawString(24, H - 30, f"{pr['defect']} · {pr['layout']} · {pr['step']} · {pr['device']}")
        c.setFont(FONT, 8.5); c.setFillColorRGB(0.2, 0.2, 0.2)
        wrap(c, pr.get("note", ""), 24, H - 46, W - 48)
        cw, ch = (W - 72) / 2, H - 210
        side(c, pr["before"], name, pr["step"], 24, 110, cw, ch, "BEFORE  " + os.path.basename(pr["before"]))
        side(c, pr["after"], name, pr["step"], 48 + cw, 110, cw, ch, "AFTER  " + os.path.basename(pr["after"]))
        c.setFont(FONT, 6.5); c.setFillColorRGB(0.4, 0.4, 0.4)
        c.drawString(24, 14, spec.get("title", "") + " — screenshots at their own pixels (zoom in); grades from popclip.py on each run.")
        c.showPage()
    c.save()


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Update 475 — real-device visual QA volumes (PDF): per layout, the exact build / device evidence, the graded results of
the popup gate (popclip) and the navigation check (navclip), the screenshots at FULL resolution (embedded at their own
pixels: zoom in the PDF to see every pixel; the original PNGs ship next to the PDF), numbered annotations — automatic ones
from the layout dumps (squeezed, text cut inside its box, controls cut) and the reviewer's own (notes JSON) — and the
reviewer's usability recommendations. A last page lists coverage honestly (what ran, what is NOT TESTED).

  python3 visualpdf.py SPEC.json OUT.pdf
SPEC: {"title": "...", "volume": "1 of N", "layouts": ["CLASSIC", ...],
       "devices": [{"name": "KONKR GT78", "popclip": "RUN_DIR", "navclip": "RUN_DIR", "style": "OWN"}, ...],
       "notes": "NOTES.json" (optional: {"LAYOUT": {"findings": [{"device": 0, "step": "main", "box": [x,y,w,h] | null,
                 "text": "...", "kind": "defect|recommendation|owner decision"}], "summary": "..."}}),
       "coverage_note": "..."}
A screenshot missing for a step is printed as "not captured" — never replaced by another step's picture.
"""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import evidence
import qalib
import geomcheck

from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

FONT, BOLD = "DejaVu", "DejaVu-Bold"
pdfmetrics.registerFont(TTFont(FONT, "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont(BOLD, "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"))
W, H = landscape(A4)
M = 22
COL = {qalib.PASS: (0.13, 0.55, 0.25), qalib.FAIL: (0.78, 0.12, 0.12), qalib.INFRA: (0.55, 0.35, 0.0),
       qalib.NOT_TESTED: (0.45, 0.45, 0.45), qalib.SKIP: (0.45, 0.45, 0.6)}


def load(run, name):
    tag = os.path.basename(os.path.normpath(run))
    for p in (os.path.join(run, "qa_clip", f"{tag}_{name}.json"), os.path.join(run, "qa_clip", f"{name}.json")):
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding="utf-8"))
            except ValueError:
                return None
    return None


def auto_marks(dump):
    """Boxes the dump itself proves wrong: squeezed (width, no height) and text cut inside its box (trusted reports)."""
    out = []
    if not dump:
        return out
    wins = dump.get("windows") or []
    if any(w.get("focus") for w in wins):
        wins = [w for w in wins if w.get("focus")]
    for w in wins:
        give = {(t.get("text", "")[:80], tuple(t.get("full") or [])) for t in (w.get("texts") or []) if t.get("giveway")}
        for n in w.get("nodes") or []:
            f = n.get("full") or []
            if len(f) == 4 and f[2] > 0 and f[3] <= 1 and (n.get("label") or "").strip() and (n.get("label", "")[:80], tuple(f)) not in give:
                out.append(([f[0], f[1] - 6, f[2], 12], f"squeezed to no height: '{n['label'][:40]}'"))
        for t in w.get("texts") or []:
            f, th, bh = t.get("full") or [], t.get("textH"), t.get("boxH")
            if len(f) != 4 or f[3] <= 0 or t.get("giveway") or t.get("scroll"):
                continue
            trusted = th is None or bh is None or abs(th - bh) <= 1
            tiny = th and bh is not None and bh * 2 < th / max(1, t.get("lines") or 1)
            if (t.get("ovH") and (trusted or tiny)) or (t.get("ellipsis") and trusted and (t.get("len") or 0) <= 160):
                out.append((f, f"text cut inside its own box: '{(t.get('text') or '')[:40]}'"))
    return out


from stepreason import STEP_KEY, missing_reason  # (update 476: no PDF library needed there)


def shot(c, path, x, y, w, h, marks, label, start_no, why=None):
    """The screenshot at its own pixels, scaled into the cell (aspect kept), numbered red boxes on it."""
    c.setFont(BOLD, 7.5); c.setFillColorRGB(0.15, 0.15, 0.15); c.drawString(x, y + h + 3, label)
    if not path or not os.path.exists(path):
        c.setStrokeColorRGB(0.7, 0.7, 0.7); c.rect(x, y, w, h); c.setFont(FONT, 8)
        c.drawCentredString(x + w / 2, y + h / 2, why or "NOT CAPTURED"); return start_no
    ir = ImageReader(path)
    iw, ih = ir.getSize()
    k = min(w / iw, h / ih)
    dw, dh = iw * k, ih * k
    ox, oy = x + (w - dw) / 2, y + (h - dh) / 2
    c.drawImage(ir, ox, oy, dw, dh)
    c.setStrokeColorRGB(0.6, 0.6, 0.6); c.setLineWidth(0.4); c.rect(ox, oy, dw, dh)
    no = start_no
    for box, _ in marks:
        if not box:
            continue
        bx, by, bw, bh = box
        rx, ry = ox + bx * k, oy + dh - (by + bh) * k
        c.setStrokeColorRGB(0.9, 0.1, 0.1); c.setLineWidth(1.2); c.rect(rx, ry, max(bw * k, 3), max(bh * k, 3))
        c.setFillColorRGB(0.9, 0.1, 0.1); c.circle(rx, ry + max(bh * k, 3), 5, fill=1, stroke=0)
        c.setFillColorRGB(1, 1, 1); c.setFont(BOLD, 6); c.drawCentredString(rx, ry + max(bh * k, 3) - 2, str(no))
        no += 1
    return no


def wrap(c, text, x, y, width, font=FONT, size=7.5, lead=9.5):
    c.setFont(font, size)
    words, line = text.split(), ""
    for wd in words:
        t = (line + " " + wd).strip()
        if c.stringWidth(t, font, size) > width:
            c.drawString(x, y, line); y -= lead; line = wd
        else:
            line = t
    if line:
        c.drawString(x, y, line); y -= lead
    return y


def status_cell(c, x, y, st, txt=None):
    r, g, b = COL.get(st, (0.3, 0.3, 0.3))
    c.setFillColorRGB(r, g, b); c.setFont(BOLD, 7); c.drawString(x, y, txt or st)


def grade_device(dv, lay):
    import popclip, navclip
    st = dv.get("style", "OWN")
    pr, nr = dv.get("popclip"), dv.get("navclip")
    if isinstance(nr, list):   # (several walk runs: the one that walked this layout)
        nr = next((r for r in nr if os.path.exists(os.path.join(r, f"{lay}_s0.png"))), None)
    g = popclip.grade(pr, f"{lay}_{st}") if pr and os.path.exists(os.path.join(pr, f"{lay}_{st}_main.png")) else None
    if g is not None:
        ev = evidence.check_run(pr)
        if ev[0] != qalib.PASS:
            g = {k: (qalib.INFRA, "evidence: " + ev[2]) for k in g}
    ng = None
    saved = (nr.rstrip("/") + ".json") if nr else None
    if saved and os.path.exists(saved):
        # (The grades made when the run was pulled — with its early raw shots, which are not kept.)
        lj = json.load(open(saved, encoding="utf-8")).get("layouts", {}).get(lay)
        if lj:
            return g, {k: tuple(v) for k, v in lj.items()}
    if nr and os.path.exists(os.path.join(nr, f"{lay}_s0.png")):
        exp = json.load(open(navclip.EXPECT, encoding="utf-8")).get("layouts", {}) if os.path.exists(navclip.EXPECT) else {}
        ng = navclip.grade_layout(nr, lay, exp.get(lay, {}))
        ne = evidence.check_run(nr)
        if ne[0] != qalib.PASS:
            ng = {k: (qalib.INFRA, "evidence: " + ne[2]) for k in ng}
    return g, ng


def layout_page(c, spec, lay, notes, page_no, di, dv):
    """One page per layout and device: evidence, graded results, the four steps in a 2 x 2 grid, numbered findings."""
    st = dv.get("style", "OWN")
    pr = dv.get("popclip")
    c.setFont(BOLD, 14); c.setFillColorRGB(0, 0, 0); c.drawString(M, H - M - 8, f"{lay}  ·  {dv['name']}")
    c.setFont(FONT, 8); c.drawRightString(W - M, H - M - 6, f"{spec['title']} — volume {spec.get('volume', '')} — page {page_no}")
    ev_st, ev_info, ev_why = evidence.check_run(pr) if pr else (qalib.NOT_TESTED, {}, "no popup run")
    c.setFont(FONT, 6.8)
    c.drawString(M, H - M - 21, ("Build / device: " + evidence.label(ev_info)) if ev_st == qalib.PASS else f"popup run evidence {ev_st}: {ev_why}")
    nr = dv.get("navclip")
    if isinstance(nr, list):
        nr = next((r for r in nr if os.path.exists(os.path.join(r, f"{lay}_s0.png"))), None)
    if nr:
        ne = evidence.check_run(nr)
        c.drawString(M, H - M - 30, ("Navigation run: " + evidence.label(ne[1])) if ne[0] == qalib.PASS else f"navigation run evidence {ne[0]}: {ne[2]}")
    g, ng = grade_device(dv, lay)
    y = H - M - 44
    x = M
    c.setFont(BOLD, 7.5); c.setFillColorRGB(0.1, 0.1, 0.1); c.drawString(x, y, f"Popup gate ({st})"); x += 82
    for k in ("open", "geometry", "visual", "focus", "buttons", "settle"):
        v = (g or {}).get(k, (qalib.NOT_TESTED, "not run"))
        c.setFont(FONT, 7.5); c.setFillColorRGB(0.2, 0.2, 0.2); c.drawString(x, y, k); status_cell(c, x + c.stringWidth(k, FONT, 7.5) + 3, y, v[0])
        x += 100
    y -= 11; x = M
    c.setFont(BOLD, 7.5); c.setFillColorRGB(0.1, 0.1, 0.1); c.drawString(x, y, "Navigation"); x += 82
    for k in ("move", "jump", "steady", "art", "late"):
        v = (ng or {}).get(k, (qalib.NOT_TESTED, "not run"))
        warn = v[0] == qalib.PASS and v[1].startswith("WARN")
        c.setFont(FONT, 7.5); c.setFillColorRGB(0.2, 0.2, 0.2); c.drawString(x, y, k)
        status_cell(c, x + c.stringWidth(k, FONT, 7.5) + 3, y, v[0], "WARN" if warn else None)
        x += 100
    findings, n = [], 1
    gx, gw = M, 640
    cell_w = (gw - 10) / 2
    cell_h = cell_w * 0.62
    top = y - 14
    steps = [("home", "home"), ("main", f"window ({st})"), ("more", "More"), ("page", "Details page")]
    for i, (sk, lab) in enumerate(steps):
        cx = gx + (i % 2) * (cell_w + 10)
        cy = top - (i // 2 + 1) * (cell_h + 14)
        d = load(pr, f"{lay}_{st}_{sk}") if pr else None
        marks = auto_marks(d) if sk != "home" else []
        for nt in (notes.get("findings") or []):
            if nt.get("device") == di and nt.get("step") == sk:
                marks.append((nt.get("box"), f"{nt.get('kind', 'note')}: {nt['text']}"))
        first = n
        why = missing_reason(pr, lay, st, sk)
        n = shot(c, os.path.join(pr, f"{lay}_{st}_{sk}.png") if pr else None, cx, cy, cell_w, cell_h, marks, lab, n, why)
        if why and not why.startswith("not offered"):
            findings.append(f"[–] {lab}: {why}")
        for j, (bx, t) in enumerate(m for m in marks if m[0]):
            findings.append(f"[{first + j}] {lab}: {t}")
        findings += [f"[–] {lab}: {t}" for bx, t in marks if not bx]
    for src, gg in (("popup", g or {}), ("navigation", ng or {})):
        for k, v in gg.items():
            if v[0] in (qalib.FAIL, qalib.INFRA) or (v[0] == qalib.PASS and v[1].startswith("WARN")):
                findings.append(f"{src} {k} {'WARN' if v[0] == qalib.PASS else v[0]}: {v[1][4:] if v[1].startswith('WARN') else v[1]}")
    # Right column: the reviewer's summary, then every finding.
    fx, fw = gx + gw + 12, W - M - (gx + gw + 12)
    y = top
    c.setFillColorRGB(0, 0, 0)
    if notes.get("summary"):
        y = wrap(c, "Review: " + notes["summary"], fx, y, fw, BOLD, 7.2, 9) - 4
    seen = []
    for f in findings:
        if f in seen:
            continue
        seen.append(f)
        if y < M + 12:
            c.setFont(FONT, 6.5); c.drawString(fx, y, "(more in the run's JSON report)"); break
        y = wrap(c, f, fx, y, fw, FONT, 6.5, 8) - 2
    if not seen and not notes.get("summary"):
        c.setFont(FONT, 7); c.drawString(fx, y, "No finding.")
    c.showPage()


def coverage_page(c, spec):
    c.setFont(BOLD, 14); c.drawString(M, H - M - 8, "Coverage — what this volume does and does not show")
    y = H - M - 30
    for line in (spec.get("coverage_note") or "").split("\n"):
        y = wrap(c, line, M, y, W - 2 * M, FONT, 8.5, 11)
    c.showPage()
    cp = spec.get("coverage_table")
    if cp and os.path.exists(cp):
        # (The whole sweep, every layout: tools/sweepcov.py's table — the worst status per tier, NOT TESTED where nothing ran.)
        j = json.load(open(cp, encoding="utf-8"))
        rows = list(j["table"].items())
        cols = list(rows[0][1].keys()) if rows else []
        c.setFont(BOLD, 12); c.drawString(M, H - M - 8, "Coverage — all layouts, every tier (sweepcov.py)")
        x0, y = M + 95, H - M - 28
        cw = (W - x0 - M) / max(1, len(cols))
        short = {qalib.NOT_TESTED: "NT", qalib.PASS: "PASS", qalib.FAIL: "FAIL", qalib.INFRA: "INFRA", qalib.SKIP: "n/a"}
        c.setFont(FONT, 6.5); c.setFillColorRGB(0.2, 0.2, 0.2)
        c.drawString(M, H - M - 18, "NT = NOT TESTED (did not run). Worst status per layout and tier; n/a rows (style not offered) left out.")
        c.setFont(BOLD, 5.6); c.setFillColorRGB(0, 0, 0)
        for i, col in enumerate(cols):
            d_, t_ = col.split("|", 1)
            c.drawString(x0 + i * cw, y + 6, d_[:10]); c.drawString(x0 + i * cw, y, t_[:16])
        y -= 9
        for lay, row in rows:
            c.setFont(FONT, 6.2); c.setFillColorRGB(0, 0, 0); c.drawString(M, y, lay)
            for i, col in enumerate(cols):
                status_cell(c, x0 + i * cw, y, row[col], short.get(row[col], row[col]))
            y -= 8.6
        c.setFont(BOLD, 6); c.setFillColorRGB(0, 0, 0)
        for i, col in enumerate(cols):
            t = j["totals"].get(col, {})
            c.drawString(x0 + i * cw, y - 4, " ".join(f"{short.get(k, k)} {v}" for k, v in sorted(t.items()))[:40])
        c.showPage()


def main():
    if len(sys.argv) != 3:
        print(__doc__); sys.exit(2)
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    notes = json.load(open(spec["notes"], encoding="utf-8")) if spec.get("notes") and os.path.exists(spec["notes"]) else {}
    c = canvas.Canvas(sys.argv[2], pagesize=(W, H))
    c.setTitle(spec["title"])
    c.setFont(BOLD, 20); c.drawString(M, H - 80, spec["title"])
    y = H - 110
    for line in (spec.get("intro") or "").split("\n"):
        y = wrap(c, line, M, y, W - 2 * M, FONT, 9.5, 13)
    c.showPage()
    page = 2
    for lay in spec["layouts"]:
        for di, dv in enumerate(spec["devices"]):
            layout_page(c, spec, lay, notes.get(lay, {}), page, di, dv); page += 1
    coverage_page(c, spec)
    c.save()
    print(f"wrote {sys.argv[2]}: {len(spec['layouts'])} layouts")


if __name__ == "__main__":
    main()

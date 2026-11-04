#!/usr/bin/env python3
"""Update 476 — readability and content continuity, graded SEPARATELY from geometry (QA improvements 1 and 2).

Geometry says a text is whole and inside its box; this says whether it can be read where it is drawn:
  facts      FAIL  a window text laid out with no size, or measured but never placed (a fact the window doesn't show —
                   not a deliberate give-way)
  overlap    FAIL  two texts drawn over each other (more than half of the smaller one covered) in the same window — not a
                   scrolling / paged / giving-way text (their hidden parts are clipped, not drawn)
  contrast   WARN  the text's ink against its own background in the screenshot is under 3 : 1 (WCAG large-text minimum),
                   or under 4.5 : 1 on a busy background (the ring round the box varies a lot: a screenshot / art under it)
  lowres     WARN  a picture whose decoded size is under half of what was asked (the source file itself is small: it is
                   drawn upscaled), from the QaArt records in the dump (`images`: want / got)
Heuristics read pixels, so they only WARN (never fail the gate) — except overlap, which the dump proves.
Recommendations (subjective design advice) are never produced here: those are the reviewer's notes, kept apart.

  python3 readcheck.py DUMP.json SHOT.png [HOME_DUMP.json]
"""
import json, sys

sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
import qalib


def _lum(rgb):
    def ch(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def ratio(a, b):
    hi, lo = max(a, b), min(a, b)
    return (hi + 0.05) / (lo + 0.05)


def _inter(a, b):
    x0, y0 = max(a[0], b[0]), max(a[1], b[1])
    x1, y1 = min(a[0] + a[2], b[0] + b[2]), min(a[1] + a[3], b[1] + b[3])
    return max(0, x1 - x0) * max(0, y1 - y0)


def _base_keys(baseline):
    """The texts already on the screen before the window opened (same words, same place ±4 px): the home screen under the
    veil, the status bar. Only the window's own texts are graded."""
    keys = []
    for w in (baseline or {}).get("windows") or []:
        for t in w.get("texts") or []:
            if len(t.get("shown") or []) == 4:
                keys.append(((t.get("text") or "").strip(), t["shown"]))
    return keys


def _in_base(t, keys):
    txt, b = (t.get("text") or "").strip(), t.get("shown") or []
    return any(k == txt and all(abs(a - c) <= 4 for a, c in zip(b, kb)) for k, kb in keys)


def _lcd_ghost(a, b):
    """An LCD clock's unlit segments ('88:88' under '17:07'): drawn under on purpose."""
    to8 = lambda x: "".join("8" if c.isdigit() else c for c in x.strip())
    a, b = a.strip(), b.strip()
    return a != b and len(a) == len(b) and ((to8(b) == a and to8(a) == a) or (to8(a) == b and to8(b) == b))


def _turned(t):
    """A one-line text drawn turned (a fanned card, a slanted list): its on-screen box is taller than its line — cards in a
    fan overlap by design, and the box no longer outlines the words."""
    th = t.get("textH") or 0
    return (t.get("lines") or 1) == 1 and th > 0 and t["shown"][3] > 1.5 * th


def own_texts(dump, baseline=None):
    keys = _base_keys(baseline)
    for w in dump.get("windows") or []:
        yield w, [t for t in w.get("texts") or [] if not _in_base(t, keys)]


def overlaps(dump, baseline=None):
    out = []
    for w, own in own_texts(dump, baseline):
        ts = [t for t in own if len(t.get("shown") or []) == 4 and t["shown"][2] > 2 and t["shown"][3] > 2
              and not t.get("scroll") and not t.get("giveway") and (t.get("text") or "").strip() and not _turned(t)]
        for i in range(len(ts)):
            for j in range(i + 1, len(ts)):
                a, b = ts[i]["shown"], ts[j]["shown"]
                small = min(a[2] * a[3], b[2] * b[3])
                ti, tj = ts[i], ts[j]
                if small and _inter(a, b) > 0.5 * small and ti.get("text") != tj.get("text") \
                        and not _lcd_ghost(ti.get("text") or "", tj.get("text") or ""):
                    # A watermark word (3x the other's height or more) is drawn behind on purpose: not graded.
                    if max(a[3], b[3]) >= 3 * min(a[3], b[3]):
                        continue
                    # A sliding (marquee) text's box is wider than what its clip draws, and an auto-sized text reports its
                    # unshrunk box: the overlap can't be proved from the dump — a WARN to look at, never a FAIL.
                    sure = not any(t.get("ovW") or t.get("ovH") or (t.get("textH") and t.get("boxH") and abs(t["textH"] - t["boxH"]) > 1)
                                   for t in (ti, tj))
                    out.append((a, f"'{ti['text'][:30]}' and '{tj['text'][:30]}' drawn over each other", sure))
    return out


def contrast(dump, png, baseline=None):
    """(box, ratio, busy) per text: ink = the luminance farthest from the box's median, ground = the median."""
    try:
        from PIL import Image
    except ImportError:
        return []
    im = Image.open(png).convert("RGB")
    W, H = im.size
    px = im.load()
    out = []
    for w, own in own_texts(dump, baseline):
        for t in own:
            b = t.get("shown") or []
            if len(b) != 4 or b[2] < 8 or b[3] < 6 or t.get("scroll") or t.get("giveway"):
                continue
            x, y, bw, bh = [int(v) for v in b]
            if x < 0 or y < 0 or x + bw > W or y + bh > H:
                continue
            step = max(1, int((bw * bh / 1500) ** 0.5))
            ls = sorted(_lum(px[i, j]) for j in range(y, y + bh, step) for i in range(x, x + bw, step))
            if len(ls) < 12:
                continue
            med = ls[len(ls) // 2]
            lo, hi = ls[max(0, len(ls) // 50)], ls[min(len(ls) - 1, len(ls) - 1 - len(ls) // 50)]
            ink = hi if abs(hi - med) >= abs(med - lo) else lo
            # The ring 6 px round the box: a busy picture under the text varies a lot there.
            ring = [_lum(px[i, j]) for i in range(max(0, x - 6), min(W, x + bw + 6), 3) for j in (max(0, y - 6), min(H - 1, y + bh + 5))]
            mean = sum(ring) / max(1, len(ring))
            busy = (sum((v - mean) ** 2 for v in ring) / max(1, len(ring))) ** 0.5 > 0.12
            out.append((b, ratio(ink, med), busy, (t.get("text") or "")[:30]))
    return out


def lowres(dump, k=0.5):
    out = []
    for im in dump.get("images") or []:
        want, got = im.get("want") or [], im.get("got") or []
        if len(want) == 2 and len(got) == 2 and min(got) > 0 and max(want) >= 200 and im.get("ok"):
            # The upscale the picture is drawn at: FIT fills the box on its tighter side, FILL (crop) on its looser one.
            kx, ky = want[0] / got[0], want[1] / got[1]
            up = max(kx, ky) if (im.get("scale") or "FIT") == "FILL" else min(kx, ky)
            if up > 1 / k:
                out.append(f"picture {im.get('tok', '?')}: decoded {got[0]}x{got[1]} for a {want[0]}x{want[1]} {im.get('scale') or 'FIT'} request "
                           f"({up:.1f}x upscale: small source)")
    return out


def missing(dump, baseline=None):
    """Update 477 (D266): a window text laid out with NO size — width or height 0 in its own layout, not merely clipped by a
    parent or scrolled away — is a fact the window does not show (Airy's '★ 0 / 109' on the KONKR: the chips' second line got
    no room). Texts that give way on purpose (StoryLines / WholeRows mark them) and scrolling / paged ones are left out."""
    out = []
    for w, own in own_texts(dump, baseline):
        for t in own:
            f = t.get("full") or []
            if not (t.get("text") or "").strip() or t.get("giveway") or t.get("scroll") or t.get("paged"):
                continue
            if len(f) == 4 and (f[2] <= 0 or f[3] <= 0):
                out.append(f"'{t['text'][:30]}' has no room (laid out {f[2]} x {f[3]} px): not shown")
            # (Update 477: dropped by a full row / column — measured, never placed, its box a stale one: k477e's chips.)
            elif t.get("placed") is False:
                out.append(f"'{t['text'][:30]}' not placed (a full row / column dropped it): not shown")
    return out


def evaluate(dump, png=None, step="", baseline=None):
    """[baseline]: the home step's dump — its texts (under the veil, the status bar) are not the window's and not graded."""
    rs = []
    # (The window's steps only: a home screen's tickers and rails collapse items they aren't showing by design.)
    gone = missing(dump, baseline) if step != "home" else []
    if step != "home": rs.append(qalib.Result(f"{step} facts", qalib.FAIL if gone else qalib.PASS,
                           "; ".join(gone[:4]) if gone else "every window text has room"))
    ov = overlaps(dump, baseline)
    sure = [t for _, t, k in ov if k]
    maybe = [t for _, t, k in ov if not k]
    rs.append(qalib.Result(f"{step} overlap", qalib.FAIL if sure else qalib.PASS,
                           "; ".join(sure[:4]) if sure else ("WARN: unconfirmed (sliding / auto-sized text): " + "; ".join(maybe[:3])) if maybe
                           else "no text drawn over another"))
    if png:
        low = [(b, r, busy, s) for b, r, busy, s in contrast(dump, png, baseline) if r < 3.0 or (busy and r < 4.5)]
        rs.append(qalib.Result(f"{step} contrast", qalib.PASS,
                               ("WARN: " + "; ".join(f"'{s}' {r:.1f}:1{' on a busy picture' if busy else ''}" for _, r, busy, s in low[:4])) if low
                               else "every text 3:1 or better (4.5:1 on busy pictures)"))
    else:
        rs.append(qalib.Result(f"{step} contrast", qalib.NOT_TESTED, "no screenshot"))
    lr = lowres(dump)
    rs.append(qalib.Result(f"{step} lowres", qalib.PASS, ("WARN: " + "; ".join(lr[:3])) if lr else "no picture decoded under half its request"))
    return rs


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    d = json.load(open(sys.argv[1], encoding="utf-8"))
    base = json.load(open(sys.argv[3], encoding="utf-8")) if len(sys.argv) > 3 else None
    rs = evaluate(d, sys.argv[2] if len(sys.argv) > 2 else None, baseline=base)
    for r in rs:
        print(r.status, r.name, r.reason)
    sys.exit({"PASS": 0, "FAIL": 1}.get(qalib.verdict(rs), 3))

#!/usr/bin/env python3
"""Update 427: WCAG contrast of every theme palette (ShelfColors.kt `pal(...)` entries).

   python tools/themes/contrast_check.py [--src DIR] [--json out.json]

For each theme: text / muted text on background, surface and raised surface (aim 4.5:1, normal text), and the
accent on the raised surface (aim 3:1, a meaningful control / focus indicator). Palette measurements only:
where a colour is really used decides whether a low number is a real problem (see SUMMARY 427).
Exit 0 always; the FAIL lines are the list to look at."""
import json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
src = sys.argv[sys.argv.index("--src") + 1] if "--src" in sys.argv else next(
    p for p in [os.path.join(ROOT, "src_copy"), os.path.join(ROOT, "app/src/main/java/com/shelf/launcher")] if os.path.isdir(p))
kt = open(os.path.join(src, "ui", "theme", "ShelfColors.kt"), encoding="utf-8").read()

def lum(h):
    c = [((h >> s) & 255) / 255 for s in (16, 8, 0)]
    c = [x / 12.92 if x <= 0.03928 else ((x + 0.055) / 1.055) ** 2.4 for x in c]
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

def cr(a, b):
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)

rows = []
for m in re.finditer(r"^\s+([A-Z0-9_, ]+?)\s*->\s*pal\(([^)]*)\)", kt, flags=re.M):
    vals = [int(v.strip().replace("0xFF", "0x"), 16) for v in m.group(2).split(",")]
    if len(vals) != 7: continue
    bg, surf, raised, acc, sec, text, muted = vals
    for name in [n.strip() for n in m.group(1).split(",")]:
        r = {"theme": name,
             "text/raised": cr(text, raised), "muted/bg": cr(muted, bg), "muted/surface": cr(muted, surf),
             "muted/raised": cr(muted, raised), "accent/raised": cr(acc, raised), "accent/bg": cr(acc, bg)}
        r["fail"] = [k for k, v in r.items() if isinstance(v, float) and v < (3.0 if k.startswith("accent") else 4.5)]
        rows.append(r)
for r in sorted(rows, key=lambda r: r["theme"]):
    if r["fail"]:
        print("FAIL %-22s " % r["theme"] + "  ".join("%s %.2f" % (k, r[k]) for k in r["fail"]))
print("palettes checked:", len(rows), " with a value under its aim:", sum(1 for r in rows if r["fail"]))
if "--json" in sys.argv:
    json.dump(rows, open(sys.argv[sys.argv.index("--json") + 1], "w"), indent=1)

#!/usr/bin/env python3
"""VoltCap QA (update 482, A5) — stale headings / counts after L2 / R2. Grades an a5s.sh run: per layout and step, the texts
on screen after the R2 step (<L>_s<n>.json) against the same category opened fresh (<L>_f<n>.json). Clock / battery / points
and the focused game's own texts can legitimately differ (a fresh open focuses the first game); a label that names a
category or a count on one side only is listed as SUSPECT for a look at <L>_s<n>.png.
  python3 a5stale.py DIR"""
import json, os, re, sys
d = sys.argv[1]
steps = {}
for ln in open(os.path.join(d, "log.txt")):
    m = re.match(r"(\S+) step(\d) (\S+)", ln)
    if m: steps[(m.group(1), m.group(2))] = m.group(3)
def texts(p):
    try: j = json.load(open(p))
    except Exception: return None
    out = set()
    for w in j.get("windows", []):
        for n in w.get("nodes", []):
            if n.get("kind") == "text" and n.get("label"):
                l = n["label"].strip()
                if re.fullmatch(r"\d{1,2}:\d\d( [AP]M)?|\d+%|.*\d\d:\d\d.*", l): continue
                out.add(l)
    return out
SPECIAL = {"RECENT": "Continue playing", "SMART_CONTINUE": "Smart Continue", "NEVER_PLAYED": "Never played"}
CAT = re.compile(r"\b(All|Android|Other apps|Xbox 360|Never played|Continue|Recently|Favou?rites|games?)\b|^\d+$", re.I)
for (L, n), c in sorted(steps.items()):
    s, f = texts(os.path.join(d, f"{L}_s{n}.json")), texts(os.path.join(d, f"{L}_f{n}.json"))
    if c.startswith("null|") and s is not None:
        # A special list: the fresh-open hook (S:<name>) can fall back to the previous category while the list is still
        # being built, so only the heading is checked: the list's own name on screen, no other category's name.
        want = SPECIAL.get(c.split("|")[1], c.split("|")[1]).lower()
        has = any(x.lower() == want for x in s)
        print(f"{L:15} step{n} {c:28} " + ("OK (its name is shown)" if has else "SUSPECT: its name is not on screen"))
        continue
    if s is None or f is None: print(f"{L:15} step{n} {c:28} NO DUMP"); continue
    only_s, only_f = s - f, f - s
    sus = sorted(x for x in only_s | only_f if CAT.search(x))
    print(f"{L:15} step{n} {c:28} " + ("OK" if not sus else "SUSPECT " + " | ".join(
        ("after-switch: " if x in only_s else "fresh: ") + x[:40] for x in sus)))

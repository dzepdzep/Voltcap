#!/usr/bin/env python3
"""Update 475 — honest coverage of a sweep: per layout and device, the status of every tier that ran, from the graded run
reports (popclip / navclip --json), and NOT TESTED for everything that did not run. Nothing is inferred.

  python3 sweepcov.py SPEC.json [--md OUT.md] [--json OUT.json]
SPEC: {"layouts": [...], "devices": {"KONKR": {"T1 OWN": ["run.json", ...], "T1 other styles": [...], "T5": [...],
                                             "games": N (optional: the shortest game list the T5 walk ran on)}, ...},
       "tiers": ["T1 OWN", "T1 other styles", "T5", "T2 font / size", "T3 content", "T4 pairwise", "T6 settings"]}
A layout's cell for a tier is the worst status over its rows in those reports (n/a rows — SKIP — left out); a layout no
report names is NOT TESTED.
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib


def statuses(reports, lay):
    out = []
    for p in reports:
        if not os.path.exists(p):
            continue
        j = json.load(open(p, encoding="utf-8"))
        for r in j.get("results", []):
            n = r.get("name", "")
            if n == lay or n.startswith(lay + "_") and n[len(lay) + 1:] in ("OWN", "BANNER", "CLASSIC", "ROW_OPENS", "ROW_REVEAL", "SHARED"):
                if r.get("status") != qalib.SKIP:
                    out.append(r["status"])
    return out


def main():
    spec = json.load(open(sys.argv[1], encoding="utf-8"))
    tiers = spec["tiers"]
    table, totals = {}, {}
    for dev, tr in spec["devices"].items():
        for lay in spec["layouts"]:
            for t in tiers:
                st = statuses(tr.get(t, []), lay)
                v = qalib.verdict(st) if st else qalib.NOT_TESTED
                # (Update 476, QA improvement 5: a walk over a list of one or two games can't show scrolling — its PASS
                # says so instead of standing for full scrolling coverage. "games": the shortest list the walk ran on.)
                if t.startswith("T5") and v == qalib.PASS and (tr.get("games") or 99) < 3:
                    v = f"PASS · {tr['games']} game(s): no scroll claim"
                table.setdefault(lay, {})[(dev, t)] = v
                totals.setdefault((dev, t), {}).setdefault(v, 0)
                totals[(dev, t)][v] += 1
    cols = [(d, t) for d in spec["devices"] for t in tiers]
    md = ["| layout | " + " | ".join(f"{d} {t}" for d, t in cols) + " |", "|" + "---|" * (len(cols) + 1)]
    for lay in spec["layouts"]:
        md.append(f"| {lay} | " + " | ".join(table[lay][c] for c in cols) + " |")
    md.append("| **totals** | " + " | ".join(", ".join(f"{k} {v}" for k, v in sorted(totals[c].items())) for c in cols) + " |")
    text = "\n".join(md) + "\n"
    if "--md" in sys.argv:
        open(sys.argv[sys.argv.index("--md") + 1], "w", encoding="utf-8").write(text)
    if "--json" in sys.argv:
        json.dump({"table": {l: {f"{d}|{t}": v for (d, t), v in row.items()} for l, row in table.items()},
                   "totals": {f"{d}|{t}": v for (d, t), v in totals.items()}},
                  open(sys.argv[sys.argv.index("--json") + 1], "w", encoding="utf-8"), indent=1)
    print(text)


if __name__ == "__main__":
    main()

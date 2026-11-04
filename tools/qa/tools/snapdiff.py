#!/usr/bin/env python3
"""Update 474 — QA tier T6: was the owner's configuration restored exactly?  Compares two copies of the app's own settings
snapshot (TestHooks `voltcap_test_snapshot save`: voltcap_test_snapshot.json, _appearance.json, _categories.txt, _view.json),
one saved BEFORE a run and one saved AFTER its restore. Values are never printed (they can name the owner's games / folders):
only which keys differ.
  python3 snapdiff.py BEFORE_DIR AFTER_DIR      exit 0 PASS (identical), 1 FAIL (a key changed / added / removed), 2 INFRA
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

PARTS = ("voltcap_test_snapshot.json", "voltcap_test_snapshot_appearance.json", "voltcap_test_snapshot_categories.txt",
         "voltcap_test_snapshot_view.json")


def read(p):
    raw = open(p, encoding="utf-8", errors="replace").read()
    if p.endswith(".json"):
        try:
            d = json.loads(raw)
            return d if isinstance(d, dict) else {"(whole file)": raw}
        except ValueError:
            return None
    return {f"line {i + 1}": l for i, l in enumerate(raw.splitlines())}


def diff(before, after):
    res = []
    for part in PARTS:
        a, b = os.path.join(before, part), os.path.join(after, part)
        if not os.path.exists(a) or not os.path.exists(b):
            res.append(qalib.Result(part, qalib.INFRA, "missing in " + ("BEFORE" if not os.path.exists(a) else "AFTER")))
            continue
        x, y = read(a), read(b)
        if x is None or y is None:
            res.append(qalib.Result(part, qalib.INFRA, "not valid JSON (an interrupted save?)"))
            continue
        ch = sorted(k for k in x.keys() & y.keys() if x[k] != y[k])
        add, rem = sorted(y.keys() - x.keys()), sorted(x.keys() - y.keys())
        if ch or add or rem:
            res.append(qalib.Result(part, qalib.FAIL, "; ".join(s for s in (
                ("changed: " + ", ".join(ch[:12])) if ch else "", ("added: " + ", ".join(add[:12])) if add else "",
                ("removed: " + ", ".join(rem[:12])) if rem else "") if s)))
        else:
            res.append(qalib.Result(part, qalib.PASS, f"{len(x)} entries identical"))
    return res


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__); sys.exit(2)
    qalib.finish(diff(sys.argv[1], sys.argv[2]), "Settings restored")

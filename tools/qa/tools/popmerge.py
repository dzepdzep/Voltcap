"""Update 469: merge popclip.py results of several runs — the latest run that has a combination wins (later runs are
re-checks on newer builds). usage: python3 popmerge.py out.json run1.json run2.json ... (oldest first)"""
import json, sys, collections
out, runs = sys.argv[1], sys.argv[2:]
best, src = {}, {}
for r in runs:
    d = json.load(open(r)); rs = d["results"] if isinstance(d, dict) else d
    for x in rs:
        best[x["name"]] = x; src[x["name"]] = r
c = collections.Counter(x["status"] for x in best.values())
json.dump({"runs": runs, "counts": c, "results": [dict(best[k], run=src[k]) for k in sorted(best)]}, open(out, "w"), indent=1)
print(dict(c))
for k in sorted(best):
    if best[k]["status"] not in ("PASS", "SKIP"): print(f"  [{best[k]['status']}] {k} ({src[k]}): {best[k]['reason'][:160]}")

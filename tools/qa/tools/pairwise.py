#!/usr/bin/env python3
"""Update 474 — QA tier T4: a pairwise (all-pairs) covering set over the popup / layout configuration space, and its check.

Factors (tools/qa/pairwise_model.json): layout family, popup style, font scale, display size, content fixture, device.
Every pair of values of two different factors must appear in at least one run — unless the pair can't exist (a row style on
a family whose layouts draw no row): those are listed as excluded, with the reason, never silently dropped.
Each run names a real layout: the family's layouts are used in turn (so every one of the 53 layouts gets runs), skipping any
that doesn't offer the run's style (PopupStyle.offered: row styles need a drawn row — `norow` in the model, from the
T1 sweeps of update 471 on both devices).

  python3 pairwise.py gen   [--model M] [--out runs.json] [--md runs.md]     greedy covering set (deterministic)
  python3 pairwise.py check RUNS.json [--model M]                             every coverable pair covered? uncovered listed
check exits 0 PASS (all coverable pairs covered, every run valid), 1 FAIL (a pair uncovered / an invalid run), 2 INFRA.
"""
import itertools, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

MODEL = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "pairwise_model.json")


def load(path=MODEL):
    m = json.load(open(path, encoding="utf-8"))
    fams = m["families"]
    factors = {"family": sorted(fams), **m["factors"]}
    return m, factors


def offers(m, layout, style):
    return not (style in m["row_styles"] and layout in m["norow"])


def family_offers(m, fam, style):
    return any(offers(m, l, style) for l in m["families"][fam])


def valid_pair(m, a, va, b, vb):
    """Can the two values meet in some run?"""
    d = {a: va, b: vb}
    if "family" in d and "style" in d:
        return family_offers(m, d["family"], d["style"])
    return True


def all_pairs(m, factors):
    names = list(factors)
    want, excluded = set(), []
    for a, b in itertools.combinations(names, 2):
        for va in factors[a]:
            for vb in factors[b]:
                if valid_pair(m, a, va, b, vb):
                    want.add(((a, va), (b, vb)))
                else:
                    excluded.append(f"{a}={va} x {b}={vb}: no layout of the family offers the style (no drawn row)")
    return want, excluded


def pairs_of(run, names):
    return {((a, run[a]), (b, run[b])) for a, b in itertools.combinations(names, 2)}


def run_ok(m, run):
    return family_offers(m, run["family"], run["style"])


def generate(m, factors):
    names = list(factors)
    want, excluded = all_pairs(m, factors)
    left = set(want)
    runs = []
    # (Greedy, deterministic: each new run starts from the most-needed uncovered pair, then fills the other factors one by
    # one with the value that covers the most still-uncovered pairs; ties go to the first value in the model's order.)
    while left:
        (a, va), (b, vb) = sorted(left)[0]
        run = {a: va, b: vb}
        for f in names:
            if f in run:
                continue
            best, score = None, -1
            for v in factors[f]:
                trial = dict(run, **{f: v})
                if "family" in trial and "style" in trial and not family_offers(m, trial["family"], trial["style"]):
                    continue
                s = sum(1 for g in run if (((g, run[g]), (f, v)) in left or ((f, v), (g, run[g])) in left))
                if s > score:
                    best, score = v, s
            run[f] = best
        runs.append({f: run[f] for f in names})
        left -= pairs_of(run, names)
    # A real layout per run: the family's layouts in turn, the next one that offers the run's style.
    nxt = {f: 0 for f in m["families"]}
    for r in runs:
        lays = m["families"][r["family"]]
        for k in range(len(lays)):
            lay = lays[(nxt[r["family"]] + k) % len(lays)]
            if offers(m, lay, r["style"]):
                r["layout"] = lay
                nxt[r["family"]] = (nxt[r["family"]] + k + 1) % len(lays)
                break
    return runs, excluded


def check(m, factors, runs):
    names = list(factors)
    want, excluded = all_pairs(m, factors)
    got, bad = set(), []
    for i, r in enumerate(runs):
        miss = [f for f in names if r.get(f) not in factors[f]]
        if miss:
            bad.append(f"run {i + 1}: unknown value for {', '.join(miss)}")
            continue
        lay = r.get("layout")
        if lay not in m["families"][r["family"]]:
            bad.append(f"run {i + 1}: layout {lay} is not in family {r['family']}")
        elif not offers(m, lay, r["style"]):
            bad.append(f"run {i + 1}: {lay} does not offer {r['style']} (no drawn row)")
        got |= pairs_of(r, names)
    unc = sorted(want - got)
    return unc, bad, excluded


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in ("gen", "check"):
        print(__doc__); sys.exit(2)
    mp = sys.argv[sys.argv.index("--model") + 1] if "--model" in sys.argv else MODEL
    m, factors = load(mp)
    if sys.argv[1] == "gen":
        runs, excluded = generate(m, factors)
        unc, bad, _ = check(m, factors, runs)
        n_pairs = len(all_pairs(m, factors)[0])
        used = sorted({r["layout"] for r in runs}); all_l = sorted(l for v in m["families"].values() for l in v)
        out = {"_note": "Update 474 T4 pairwise set (pairwise.py gen). Status of each run lives in the run's own report, not here.",
               "pairs": n_pairs, "excluded": excluded, "uncovered": [f"{a}={va} x {b}={vb}" for (a, va), (b, vb) in unc],
               "layouts_used": len(used), "layouts_missing": sorted(set(all_l) - set(used)), "runs": runs}
        if "--out" in sys.argv:
            json.dump(out, open(sys.argv[sys.argv.index("--out") + 1], "w", encoding="utf-8"), indent=1, ensure_ascii=False)
        if "--md" in sys.argv:
            with open(sys.argv[sys.argv.index("--md") + 1], "w", encoding="utf-8") as fh:
                fh.write(f"# T4 pairwise set — {len(runs)} runs cover all {n_pairs} coverable pairs\n\n")
                fh.write(f"Excluded (impossible) pairs: {len(excluded)}. Layouts used: {len(used)} of {len(all_l)}"
                         + (f" (missing: {', '.join(out['layouts_missing'])})" if out["layouts_missing"] else "") + ".\n\n")
                cols = ["layout"] + list(factors)
                fh.write("| # | " + " | ".join(cols) + " |\n|" + "---|" * (len(cols) + 1) + "\n")
                for i, r in enumerate(runs):
                    fh.write(f"| {i + 1} | " + " | ".join(str(r[c]) for c in cols) + " |\n")
                if excluded:
                    fh.write("\n## Excluded pairs\n" + "".join(f"- {e}\n" for e in excluded))
        print(f"{len(runs)} runs, {n_pairs} pairs, {len(unc)} uncovered, {len(excluded)} excluded, {len(bad)} invalid; "
              f"layouts used {len(used)}/{len(all_l)}")
        sys.exit(0 if not unc and not bad else 1)
    if len(sys.argv) < 3 or not os.path.exists(sys.argv[2]):
        qalib.finish([qalib.Result("pairwise set", qalib.INFRA, "no runs file")], "T4 pairwise")
    runs = json.load(open(sys.argv[2], encoding="utf-8"))
    runs = runs.get("runs", runs) if isinstance(runs, dict) else runs
    if not runs:
        qalib.finish([qalib.Result("pairwise set", qalib.INFRA, "the set has no runs")], "T4 pairwise")
    unc, bad, excluded = check(m, factors, runs)
    res = [qalib.Result("valid runs", qalib.FAIL if bad else qalib.PASS, "; ".join(bad[:6]) or f"{len(runs)} runs, each a real layout offering its style"),
           qalib.Result("pair coverage", qalib.FAIL if unc else qalib.PASS,
                        (f"{len(unc)} pair(s) uncovered: " + "; ".join(f"{a}={va} x {b}={vb}" for (a, va), (b, vb) in unc[:8])) if unc
                        else f"every coverable pair covered ({len(excluded)} impossible pair(s) excluded, listed)")]
    qalib.finish(res, "T4 pairwise")


if __name__ == "__main__":
    main()

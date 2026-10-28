#!/usr/bin/env python3
"""VoltCap QA — coverage matrix: what was really tested, from evidence, never from what the framework could do.

  python3 coverage.py collect RUN_OUT_DIR OUT.json [--jobs 4]   analyse the newest sweep recording of every layout
  python3 coverage.py render                                    write tools/qa/COVERAGE.md from the repo
  python3 coverage.py check                                     CI: data valid, COVERAGE.md up to date (exit 1 / 2)
  python3 coverage.py summary                                   markdown for the CI job summary (incl. hardware-only rows)

Inputs (all in git):
  app/src/main/java/com/shelf/launcher/data/AppearanceStore.kt   enum HomeLayout  → the layouts that really exist
  tools/qa/coverage.json                                         curated: features, manual reviews, devices, limits
  tools/qa/audit/<date>/layouts_auto.json                        output of `collect` (validated recordings + frames.py)

Rules enforced by `check`: every status is PASS / FAIL / SKIP / NOT TESTED; a PASS needs evidence text; a layout that exists in
the source but has no automated or manual evidence is listed NOT TESTED automatically; a layout in the data that does not
exist in the source is an error; COVERAGE.md must equal the generated text (regenerate with `render`).
"""
import json, os, re, subprocess, sys, glob

HERE = os.path.dirname(os.path.abspath(__file__))
QA = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(QA))
sys.path.insert(0, HERE)
import qalib
from qalib import PASS, FAIL, INFRA, SKIP, NOT_TESTED

STORE = os.path.join(ROOT, "app", "src", "main", "java", "com", "shelf", "launcher", "data", "AppearanceStore.kt")
DATA = os.path.join(QA, "coverage.json")
COMBOS = os.path.join(QA, "combos.json")
AUTO_GLOB = os.path.join(QA, "audit", "*", "layouts_auto.json")
OUT_MD = os.path.join(QA, "COVERAGE.md")
STATUSES = (PASS, FAIL, SKIP, NOT_TESTED)
PRIORITY = ["f651", "LE", "B7", "B5", "A7", "A5", "LD", "LC", "LB", "LA"]


def layouts():
    s = open(STORE, encoding="utf-8").read()
    i = s.index("enum class HomeLayout")
    j = s.index("\n}\n", i)
    return [m.group(1) for m in re.finditer(r'^\s{4}([A-Z0-9_]+)\("([^"]+)"', s[i:j], re.M)]


def labels():
    s = open(STORE, encoding="utf-8").read()
    i = s.index("enum class HomeLayout")
    j = s.index("\n}\n", i)
    return {m.group(1): m.group(2) for m in re.finditer(r'^\s{4}([A-Z0-9_]+)\("([^"]+)"', s[i:j], re.M)}


def pick_recordings(out_dir, names):
    """name -> (video, qa_log or None): the newest sweep recording of each layout by run-prefix priority."""
    best = {}
    for f in glob.glob(os.path.join(out_dir, "*.mp4")):
        b = os.path.basename(f)[:-4]
        for n in sorted(names, key=len, reverse=True):
            m = re.fullmatch(r"(.+?)_" + re.escape(n), b)
            if m:
                pr = PRIORITY.index(m.group(1)) if m.group(1) in PRIORITY else 99
                if n not in best or pr < best[n][0]:
                    qa = os.path.join(out_dir, f"{m.group(1)}_{n}_qa.txt")
                    best[n] = (pr, f, qa if os.path.exists(qa) else None)
                break
    return {n: (v, q) for n, (_, v, q) in best.items()}


def collect(out_dir, out_json, jobs=4):
    names = layouts()
    picked = pick_recordings(out_dir, names)
    vids = [picked[n][0] for n in names if n in picked]
    tmp = out_json + ".batch.json"
    work = os.path.join(os.path.dirname(os.path.abspath(out_json)), "_work")
    if vids:
        subprocess.run([sys.executable, os.path.join(HERE, "batch_frames.py"), tmp, *vids, "--jobs", str(jobs), "--workdir", work],
                       capture_output=True)
    batch = {}
    if os.path.exists(tmp):
        for r in json.load(open(tmp, encoding="utf-8"))["recordings"]:
            batch[r["video"]] = r
    res = {}
    for n in names:
        if n not in picked:
            res[n] = dict(recording=None)
            continue
        v, qa = picked[n]
        r = batch.get(v, dict(status=INFRA, reason="not analysed"))
        e = dict(recording=os.path.basename(v), status=r["status"], frames=r.get("frames"), seconds=r.get("seconds"),
                 fps=r.get("fps"), counts=r.get("counts"), reason=r.get("reason", ""))
        # focuscheck.py is NOT run per layout: its model is the plain Classic row; on the sweep D / U / grid moves it reports
        # model mismatches, not defects, and would mislead. It is used on dedicated Classic `seq` runs.
        res[n] = e
    json.dump(dict(collected_from=out_dir, layouts=res), open(out_json, "w", encoding="utf-8"), indent=1)
    for t in (tmp,):
        if os.path.exists(t):
            os.remove(t)
    return res


def load_data():
    with open(DATA, encoding="utf-8") as f:
        data = json.load(f)
    auto = {}
    for p in sorted(glob.glob(AUTO_GLOB)):        # later dates override earlier ones
        auto.update(json.load(open(p, encoding="utf-8")).get("layouts", {}))
    return data, auto


def problems(data, auto):
    errs = []
    names = set(layouts())
    for n in list(data.get("layout_manual", {})) + list(auto):
        if n not in names:
            errs.append(f"layout '{n}' in the coverage data does not exist in HomeLayout")
    for n, m in data.get("layout_manual", {}).items():
        if m.get("status") not in STATUSES:
            errs.append(f"layout_manual {n}: bad status {m.get('status')!r}")
        if m.get("status") == PASS and not m.get("evidence"):
            errs.append(f"layout_manual {n}: PASS without evidence")
    ids = set()
    for e in data.get("features", []):
        for k in ("id", "feature", "test", "device", "mode", "status", "limits"):
            if k not in e:
                errs.append(f"feature {e.get('id', '?')}: missing '{k}'")
        if e.get("id") in ids:
            errs.append(f"duplicate feature id {e.get('id')}")
        ids.add(e.get("id"))
        if e.get("status") not in STATUSES:
            errs.append(f"feature {e.get('id')}: bad status {e.get('status')!r}")
        if e.get("status") == PASS and not e.get("evidence"):
            errs.append(f"feature {e.get('id')}: PASS without evidence")
        if e.get("hardware") and e.get("status") == PASS and not e.get("device_run"):
            errs.append(f"feature {e.get('id')}: a hardware test marked PASS needs device_run (the run it passed in)")
        if e.get("mode") not in ("automated", "manual", "automated+manual"):
            errs.append(f"feature {e.get('id')}: mode must be automated / manual / automated+manual")
    return errs


def combo_problems(combos):
    """combos.json: the high-risk combination matrix. A PASS needs evidence; a unit runner must name a test that exists;
    a device PASS names the run; an unrun combination is never PASS."""
    errs, ids = [], set()
    for e in combos:
        i = e.get("id", "?")
        for k in ("id", "group", "name", "initial", "sequence", "expected", "runner", "status", "limits"):
            if k not in e:
                errs.append(f"combo {i}: missing '{k}'")
        if i in ids:
            errs.append(f"duplicate combo id {i}")
        ids.add(i)
        if e.get("status") not in STATUSES:
            errs.append(f"combo {i}: bad status {e.get('status')!r}")
        if e.get("status") == PASS and not e.get("evidence"):
            errs.append(f"combo {i}: PASS without evidence")
        r = e.get("runner", "")
        if r.startswith("unit:"):
            f, _, m = r[5:].partition("#")
            path = os.path.join(ROOT, "app", "src", "test", "java", "com", "shelf", "launcher", f)
            if not os.path.exists(path):
                errs.append(f"combo {i}: unit test file {f} does not exist")
            elif not re.search(r"fun\s+" + re.escape(m) + r"\b", open(path, encoding="utf-8").read()):
                errs.append(f"combo {i}: unit test {f}#{m} does not exist")
        elif r.startswith("device:"):
            if e.get("status") == PASS and not e.get("device_run"):
                errs.append(f"combo {i}: a device PASS needs device_run")
        elif r != "none":
            errs.append(f"combo {i}: runner must be unit:File#test, device:... or none")
        if r == "none" and e.get("status") == PASS:
            errs.append(f"combo {i}: PASS without a runner")
    return errs


def load_combos():
    if not os.path.exists(COMBOS):
        return []
    with open(COMBOS, encoding="utf-8") as f:
        return json.load(f)["combos"]


def layout_row(n, label, data, auto):
    a = auto.get(n, {})
    man = data.get("layout_manual", {}).get(n)
    if a.get("recording"):
        blank = a["status"]
        c = a.get("counts") or {}
        blank_txt = {PASS: PASS, FAIL: FAIL, INFRA: "INFRA (unusable recording)"}.get(blank, blank)
        ev = f'{c.get("transient", "?")} / {c.get("hitch", "?")}' if c else "?"
        rec = a["recording"]
    else:
        blank_txt, ev, rec = NOT_TESTED, "-", "-"
    manual = f'{man["status"]}: {man["note"]}' if man else NOT_TESTED
    return [n, label, blank_txt, ev, manual, rec]


def render():
    data, auto = load_data()
    errs = problems(data, auto) + combo_problems(load_combos())
    if errs:
        raise SystemExit("coverage data invalid:\n  " + "\n  ".join(errs))
    lab = labels()
    names = layouts()
    rows = [layout_row(n, lab[n], data, auto) for n in names]
    cnt = lambda col, pred: sum(1 for r in rows if pred(r[col]))
    L = []
    L.append("# VoltCap QA coverage matrix\n")
    L.append("Generated by `python3 tools/qa/tools/coverage.py render` from the repository (`HomeLayout`, `tools/qa/coverage.json`, "
             "`tools/qa/audit/*/layouts_auto.json`). **Do not edit by hand**; CI fails when it is out of date.\n")
    L.append("Statuses: **PASS** ran with valid evidence and found nothing; **FAIL** ran and found a defect; **SKIP** deliberately not run; "
             "**NOT TESTED** nothing ran (no device, unsafe, not built). SKIP and NOT TESTED are never counted as PASS. "
             "`INFRA` = the evidence could not be used (says nothing about the app). `REVIEW` = the tool flagged something a human must look at; "
             "it is not a verdict.\n")
    L.append("## Home layouts (from `enum HomeLayout`)\n")
    L.append(f"- Layouts in the source: **{len(names)}**")
    L.append(f"- With a validated device recording analysed by frames.py: **{cnt(2, lambda s: s in (PASS, FAIL))}** "
             f"(blank-frame scan PASS {cnt(2, lambda s: s == PASS)}, FAIL {cnt(2, lambda s: s == FAIL)}); "
             f"no usable recording / never recorded: **{cnt(2, lambda s: s == NOT_TESTED or s.startswith('INFRA'))}**")
    L.append(f"- Looked at by a human (contact sheets): **{cnt(4, lambda s: s != NOT_TESTED)}** layouts; the other "
             f"**{cnt(4, lambda s: s == NOT_TESTED)}** have no human visual verdict recorded here\n")
    L.append("| layout | name | blank-frame scan | transients / hitches | human review | recording |")
    L.append("|---|---|---|---|---|---|")
    for r in rows:
        L.append("| " + " | ".join(str(x).replace("|", "/") for x in r) + " |")
    L.append("\n`transients / hitches` are counts to look at, not verdicts: a slide, a crossfade or the intended edge-bump shake is a "
             "transient too. The blank-frame scan only fails on black / white / brightness-dip frames.\n")
    L.append("## Features and test types\n")
    L.append("| id | feature | test | device | mode | status | evidence | limits |")
    L.append("|---|---|---|---|---|---|---|---|")
    for e in data["features"]:
        hw = " (hardware, not run in CI)" if e.get("hardware") else ""
        L.append(f'| {e["id"]} | {e["feature"]} | {e["test"]}{hw} | {e["device"]} | {e["mode"]} | **{e["status"]}** | '
                 f'{e.get("evidence", "-") or "-"} | {e["limits"]} |')
    fs = data["features"]
    c = {s: sum(1 for e in fs if e["status"] == s) for s in STATUSES}
    L.append(f"\nFeature rows: {len(fs)} - " + ", ".join(f"{s} {c[s]}" for s in STATUSES) +
             f"; hardware-only rows: {sum(1 for e in fs if e.get('hardware'))}.\n")
    cs = load_combos()
    if cs:
        L.append("## High-risk combination matrix (`tools/qa/combos.json`)\n")
        L.append("Interactions between systems, not single features. `unit:` rows run in Gradle (`testDebugUnitTest`) and their test must exist; "
                 "`device:` rows are Deep QA (`tools/qa/device/combos.sh`, verdict by `tools/qa/tools/combocheck.py`) and never run in CI. "
                 "Details, evidence and the next combinations: `tools/qa/COMBOS.md`.\n")
        L.append("| id | grp | combination | initial state | sequence | expected | runner | status | evidence | limits |")
        L.append("|---|---|---|---|---|---|---|---|---|---|")
        for e in cs:
            L.append("| " + " | ".join(str(x).replace("|", "/") for x in (
                e["id"], e["group"], e["name"], e["initial"], e["sequence"], e["expected"], e["runner"],
                "**" + e["status"] + "**", e.get("evidence") or "-", e["limits"])) + " |")
        cc = {s_: sum(1 for e in cs if e["status"] == s_) for s_ in STATUSES}
        L.append(f"\nCombination rows: {len(cs)} - " + ", ".join(f"{s_} {cc[s_]}" for s_ in STATUSES) + "\n")
    L.append("## Genuinely untested (no claim made)\n")
    for e in fs:
        if e["status"] in (NOT_TESTED, SKIP):
            L.append(f'- **{e["feature"]}** ({e["test"]}): {e["limits"]}')
    for e in cs:
        if e["status"] in (NOT_TESTED, SKIP):
            L.append(f'- combination **{e["id"]} {e["name"]}**: {e["limits"]}')
    for e in cs:
        if e["status"] in (NOT_TESTED, SKIP):
            L.append(f'- combination **{e["id"]} {e["name"]}**: {e["limits"]}')
    L.append("")
    return "\n".join(L)


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cmd = sys.argv[1]
    if cmd == "collect":
        out = collect(sys.argv[2], sys.argv[3], int(sys.argv[sys.argv.index("--jobs") + 1]) if "--jobs" in sys.argv else 4)
        got = sum(1 for v in out.values() if v.get("recording"))
        print(f"collected {got}/{len(out)} layouts with a recording")
        sys.exit(0 if got else 2)
    if cmd == "render":
        txt = render()
        with open(OUT_MD, "w", encoding="utf-8", newline="\n") as f:
            f.write(txt)
        print("wrote", OUT_MD)
        return
    if cmd == "summary":
        data, auto = load_data()
        fs = data["features"]
        hw = [e for e in fs if e.get("hardware")]
        print("### QA coverage (from tools/qa/coverage.json)")
        print("| status | feature rows |")
        print("|---|---|")
        for st in STATUSES:
            print(f"| {st} | {sum(1 for e in fs if e['status'] == st)} |")
        print(f"\n**Hardware-only rows: {len(hw)}. None of them ran in this CI job** (no physical Android device on the runner); their "
              "status is the last recorded device run, never a result of this job:")
        for e in hw:
            print(f"- {e['id']} {e['feature']}: last recorded **{e['status']}**" + (f" ({e['device_run']})" if e.get("device_run") else ""))
        return
    if cmd == "check":
        try:
            txt = render()
        except SystemExit as e:
            print(f"[INFRA] {e}")
            sys.exit(2)
        if not os.path.exists(OUT_MD):
            print("[FAIL] COVERAGE.md missing: run `python3 tools/qa/tools/coverage.py render`")
            sys.exit(1)
        cur = open(OUT_MD, encoding="utf-8", newline="").read().replace("\r\n", "\n")
        if cur != txt:
            print("[FAIL] tools/qa/COVERAGE.md is out of date: run `python3 tools/qa/tools/coverage.py render` and commit it")
            sys.exit(1)
        print("[PASS] coverage data valid and COVERAGE.md up to date")
        return
    sys.exit(__doc__)


if __name__ == "__main__":
    main()

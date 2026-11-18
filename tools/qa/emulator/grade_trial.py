#!/usr/bin/env python3
"""529m - grade an emulator QA trial (run_trial.sh): the popup gate (popclip.py) per run against a temporary ledger that
names the CI-built APK as an EMULATOR build, motion bursts of the page-walk recordings, timestamped frame strips and contact
sheets. Prints markdown for the run summary. Missing captures are reported as NOT TESTED, never PASS.
(681z) Review mode: a run dispatched with review_layouts (env QA_REVIEW_LAYOUTS) is graded on ITS OWN runs - "P2:<layouts>" on
the native-page review walks p2r13 / p2r10 (grade_p2_run), any other list on the popup gates e679r13 / e679r10 - instead of
expecting the standard trial's e529a / e529b (every review run used to end NOT TESTED "no run folder pulled")."""
import glob, hashlib, json, os, re, subprocess, sys

RANK = {"FAIL": 3, "INFRA": 2, "NOT TESTED": 1, "PASS": 0}

def review_plan(review):
    """(mode, [(tag, scale)]) for the trial's QA_REVIEW_LAYOUTS value (empty = the standard trial)."""
    review = (review or "").strip()
    if review.startswith("P2:"): return "p2", [("p2r13", "x1.3"), ("p2r10", "x1.0")]
    if review: return "popup", [("e679r13", "x1.3"), ("e679r10", "x1.0")]
    return "popup", [("e529a", "x1.3"), ("e529b", "x1.0")]

P2_SHOTS = ("home", "info", "details", "trophies", "trophies_open", "options", "closed")

def grade_p2_run(run, layouts):
    """(verdict, why) of one p2review.sh run folder. The review walk's mechanics only - every layout walked, its page shots
    taken, no crash, no launch reached, nothing the script itself marked NOT TESTED, the QA app's settings put back. The look
    of the pages is the owner's review (contact sheets); a PASS here never stands for it."""
    if not os.path.isdir(run): return "NOT TESTED", "no run folder pulled"
    try: log = open(os.path.join(run, "log.txt"), encoding="utf-8", errors="replace").read()
    except OSError: return "NOT TESTED", "no log.txt in the run folder"
    m = re.search(r"STOP (.*)", log)
    if m: return "INFRA", "the walk stopped: " + m.group(1).strip()
    m = re.search(r"run done fatal=(\d+)", log)
    if not m: return "NOT TESTED", "the walk did not finish (no 'run done')"
    fails, gaps = [], []
    if int(m.group(1)) > 0: fails.append(f"{m.group(1)} FATAL EXCEPTION in the crash buffer")
    for lay in layouts:
        d = re.search(r"\b%s done \(launches: (\d+)\)" % re.escape(lay), log)
        if not d: gaps.append(f"{lay}: not walked"); continue
        if int(d.group(1)) > 0: fails.append(f"{lay}: {d.group(1)} game launch(es) reached (blocked by the QA app)")
        missing = [s for s in P2_SHOTS if not os.path.isfile(os.path.join(run, f"{lay}_{s}.png"))]
        if missing: gaps.append(f"{lay}: no shot of " + ", ".join(missing))
    for line in log.splitlines():
        if "NOT TESTED" in line or "PRECONDITION FAILED" in line or "dump missing" in line:
            gaps.append(line.split(" ", 1)[-1].strip())
    r = re.search(r"restore verify: (.*)", log)
    if not r: gaps.append("the QA app's settings restore was not logged")
    elif not r.group(1).strip().startswith("OK"): fails.append("restore verify: " + r.group(1).strip())
    b = re.search(r"owner snapshot files back: diff=(\d+)", log)
    if b and b.group(1) != "0": fails.append("the QA app's snapshot files did not come back unchanged")
    if fails: return "FAIL", "; ".join(fails + gaps)
    if gaps: return "NOT TESTED", "; ".join(gaps)
    return "PASS", "walk complete (" + ", ".join(layouts) + "); the look is the owner's review"

def worst_of(verdicts):
    return max((v[1] for v in verdicts), key=lambda v: RANK[v]) if verdicts else "NOT TESTED"

def write_verdict(out, verdicts, mode):
    with open(os.path.join(out, "verdict.txt"), "w", encoding="utf-8") as fh:
        fh.write(f"VERDICT: {worst_of(verdicts)}\n")
        for t, v, why in verdicts: fh.write(f"{t}: {v} - {why}\n")
        if mode == "p2": fh.write("P2 review: the walk's mechanics only - the pages' look is the owner's review of the sheets.\n")
        fh.write("EMULATOR evidence only - not real-handheld verification.\n")

def main():
    import numpy as np, cv2
    out, apk = sys.argv[1], sys.argv[2]
    raw = os.path.join(out, "raw")
    tools = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
    sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
    ledger = os.path.join(out, "emulator_ledger.json")
    json.dump({"builds": [{"id": "emulator-ci-" + os.environ.get("GITHUB_SHA", "local")[:8], "apk_sha256": sha, "version_code": None,
                           "base_commit": os.environ.get("GITHUB_SHA", "local")[:8], "patch_sha256": None,
                           "tree_hash": "CI stub build (no Discord SDK), EMULATOR trial", "note": "not a user build"}]}, open(ledger, "w"))
    mode, plan = review_plan(os.environ.get("QA_REVIEW_LAYOUTS", ""))
    # (674o) One verdict for the whole trial, written to verdict.txt: PASS only when every expected popup gate graded PASS (exit 0
    # and "Popup gate: PASS"); otherwise the worst of FAIL > INFRA > NOT TESTED, with one line per gate. The workflow's last step
    # fails unless the first line is "VERDICT: PASS". A missing gate is NOT TESTED, never PASS.
    verdicts = []
    if mode == "p2":
        lays = os.environ.get("QA_REVIEW_LAYOUTS", "")[3:].split()
        print("\n## P2 native-page review walks (EMULATOR)")
        for tag, sc in plan:
            v, why = grade_p2_run(os.path.join(raw, tag), lays)
            print(f"- {tag} ({sc}): {v} - {why}"); verdicts.append((tag, v, why))
    else:
        print("\n## Popup gate (EMULATOR)")
    for tag, sc in (plan if mode == "popup" else []):
        run = os.path.join(raw, tag)
        if not os.path.isdir(run):
            print(f"- {tag}: NOT TESTED (no run folder pulled)"); verdicts.append((tag, "NOT TESTED", "no run folder pulled")); continue
        r = subprocess.run([sys.executable, os.path.join(tools, "popclip.py"), run, "--ledger", ledger, "--md", os.path.join(out, f"{tag}_report.md")],
                           capture_output=True, text=True)
        lines = [l for l in r.stdout.splitlines() if l.startswith("Popup gate") or l.strip().startswith("[")]
        print(f"- {tag}: " + ("; ".join(lines[:6]) if lines else "no grade output (NOT TESTED): " + (r.stderr or "")[-300:]))
        gate = next((l for l in r.stdout.splitlines() if l.startswith("Popup gate: ")), "")
        if r.returncode == 0 and gate.startswith("Popup gate: PASS"): verdicts.append((tag, "PASS", gate))
        elif "FAIL=" in gate and not gate.split("FAIL=")[1].startswith("0"): verdicts.append((tag, "FAIL", gate))
        elif gate.startswith("Popup gate: INFRA") or ("INFRA=" in gate and not gate.split("INFRA=")[1].startswith("0")): verdicts.append((tag, "INFRA", gate))
        else: verdicts.append((tag, "NOT TESTED", gate or "no grade output"))
    write_verdict(out, verdicts, mode)
    def bursts(path):
        cap = cv2.VideoCapture(path); prev = None; ev = []
        while True:
            ok, f = cap.read()
            if not ok: break
            t = cap.get(cv2.CAP_PROP_POS_MSEC)
            s = cv2.cvtColor(cv2.resize(f, (f.shape[1] // 4, f.shape[0] // 4)), cv2.COLOR_BGR2GRAY).astype(np.int16)
            if prev is not None:
                d = float(np.abs(s - prev).mean())
                if d > 0.4: ev.append((t, d))
            prev = s
        B, cur = [], []
        for t, d in ev:
            if cur and t - cur[-1][0] > 120: B.append(cur); cur = []
            cur.append((t, d))
        if cur: B.append(cur)
        return B

    def strip(path, t0, t1, dst, title, n=12, w=320):
        cap = cv2.VideoCapture(path); fr = []
        while True:
            ok, f = cap.read()
            if not ok: break
            t = cap.get(cv2.CAP_PROP_POS_MSEC)
            if t0 <= t <= t1: fr.append((t, f))
        if not fr: return False
        tiles = []
        for t, f in fr[:n]:
            f = cv2.resize(f, (w, int(w * f.shape[0] / f.shape[1])))
            cv2.rectangle(f, (0, 0), (w, 22), (0, 0, 0), -1); cv2.putText(f, f"{int(t - t0):4d} ms", (5, 16), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1)
            tiles.append(f)
        while len(tiles) % 4: tiles.append(np.zeros_like(tiles[0]))
        img = np.vstack([np.hstack(tiles[i:i + 4]) for i in range(0, len(tiles), 4)])
        head = np.zeros((30, img.shape[1], 3), np.uint8); cv2.putText(head, "EMULATOR - " + title, (6, 21), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 1)
        cv2.imwrite(dst, np.vstack([head, img]), [cv2.IMWRITE_JPEG_QUALITY, 80]); return True

    review = os.environ.get("QA_REVIEW_LAYOUTS", "").strip()
    run = os.path.join(raw, "e529c")
    if not review: print("\n## Page motion (EMULATOR recordings; swiftshader GPU - timing is not handheld timing)")
    vids = sorted(glob.glob(os.path.join(run, "*_r1.mp4"))) if not review else []
    if not vids and not review: print("- NOT TESTED (no recordings pulled)")
    os.makedirs(os.path.join(out, "frames"), exist_ok=True)
    for v in vids:
        B = bursts(v); name = os.path.basename(v)[:-4]
        print(f"- {name}: " + (", ".join(f"t={b[0][0]:.0f} ms {b[-1][0] - b[0][0]:.0f} ms / {len(b)} frames" for b in B) or "no motion found (NOT TESTED)"))
        if B: strip(v, B[0][0][0] - 30, B[0][0][0] + 380, os.path.join(out, "frames", name + "_first_change.jpg"), f"{name} first R1 (Info -> Details)")

    print("\n## Contact sheets")
    def sheet(cells, dst, cols=3, w=420):
        tiles = []
        for p, t in cells:
            f = cv2.imread(p)
            if f is None:
                f = np.zeros((640, 960, 3), np.uint8); cv2.putText(f, "NO CAPTURE", (300, 320), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 0, 255), 3)
            f = cv2.resize(f, (w, int(w * f.shape[0] / f.shape[1])))
            cv2.rectangle(f, (0, 0), (w, 24), (0, 0, 0), -1); cv2.putText(f, t, (5, 17), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 255), 1); tiles.append(f)
        while len(tiles) % cols: tiles.append(np.zeros_like(tiles[0]))
        cv2.imwrite(dst, np.vstack([np.hstack(tiles[i:i + cols]) for i in range(0, len(tiles), cols)]), [cv2.IMWRITE_JPEG_QUALITY, 82])
        print(f"- {os.path.basename(dst)}")
    if mode == "p2":
        for lay in os.environ.get("QA_REVIEW_LAYOUTS", "")[3:].split():
            sheet([(os.path.join(raw, tag, f"{lay}_{s}.png"), f"{lay} {sc} {s}") for tag, sc in plan for s in P2_SHOTS + ("trophies_scroll", "big_trophies_far")],
                  os.path.join(out, f"sheet_p2_{lay}.jpg"), cols=9)
        return
    for tag, sc in plan:
        for lay in (review.split() if review else ("COVERDETAILS", "SHELF")):
            d = os.path.join(raw, tag)
            sheet([(os.path.join(d, f"{lay}_OWN_{s}.png"), f"{lay} {sc} {s}") for s in ("home", "main", "more", "page", "r1", "l1", "closed", "after")],
                  os.path.join(out, f"sheet_{tag}_{lay}.jpg"), cols=4)
    if review: return
    for lay in ("COVERDETAILS", "SHELF"):
        sheet([(os.path.join(run, f"{lay}_{s}.png"), f"{lay} x1.3 {s}") for s in
               ("main", "details", "trophies", "options", "back_main", "after_rapid", "motionoff_details", "t_trophies", "t_ach_tap", "t_options", "t_opt_tap", "t_main")],
              os.path.join(out, f"sheet_e529c_{lay}.jpg"), cols=4)

if __name__ == "__main__":
    main()

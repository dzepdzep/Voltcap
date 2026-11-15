#!/usr/bin/env python3
"""529m - grade an emulator QA trial (run_trial.sh): the popup gate (popclip.py) per run against a temporary ledger that
names the CI-built APK as an EMULATOR build, motion bursts of the page-walk recordings, timestamped frame strips and contact
sheets. Prints markdown for the run summary. Missing captures are reported as NOT TESTED, never PASS."""
import glob, hashlib, json, os, subprocess, sys
import numpy as np, cv2

out, apk = sys.argv[1], sys.argv[2]
raw = os.path.join(out, "raw")
tools = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
ledger = os.path.join(out, "emulator_ledger.json")
json.dump({"builds": [{"id": "emulator-ci-" + os.environ.get("GITHUB_SHA", "local")[:8], "apk_sha256": sha, "version_code": None,
                       "base_commit": os.environ.get("GITHUB_SHA", "local")[:8], "patch_sha256": None,
                       "tree_hash": "CI stub build (no Discord SDK), EMULATOR trial", "note": "not a user build"}]}, open(ledger, "w"))

print("\n## Popup gate (EMULATOR)")
for tag in ("e529a", "e529b"):
    run = os.path.join(raw, tag)
    if not os.path.isdir(run):
        print(f"- {tag}: NOT TESTED (no run folder pulled)"); continue
    r = subprocess.run([sys.executable, os.path.join(tools, "popclip.py"), run, "--ledger", ledger, "--md", os.path.join(out, f"{tag}_report.md")],
                       capture_output=True, text=True)
    lines = [l for l in r.stdout.splitlines() if l.startswith("Popup gate") or l.strip().startswith("[")]
    print(f"- {tag}: " + ("; ".join(lines[:6]) if lines else "no grade output (NOT TESTED): " + (r.stderr or "")[-300:]))

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

print("\n## Page motion (EMULATOR recordings; swiftshader GPU - timing is not handheld timing)")
run = os.path.join(raw, "e529c")
vids = sorted(glob.glob(os.path.join(run, "*_r1.mp4")))
if not vids: print("- NOT TESTED (no recordings pulled)")
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
for tag, sc in (("e529a", "x1.3"), ("e529b", "x1.0")):
    for lay in ("COVERDETAILS", "SHELF"):
        d = os.path.join(raw, tag)
        sheet([(os.path.join(d, f"{lay}_OWN_{s}.png"), f"{lay} {sc} {s}") for s in ("home", "main", "more", "page", "r1", "l1", "closed", "after")],
              os.path.join(out, f"sheet_{tag}_{lay}.jpg"), cols=4)
for lay in ("COVERDETAILS", "SHELF"):
    sheet([(os.path.join(run, f"{lay}_{s}.png"), f"{lay} x1.3 {s}") for s in
           ("main", "details", "trophies", "options", "back_main", "after_rapid", "motionoff_details", "t_trophies", "t_ach_tap", "t_options", "t_opt_tap", "t_main")],
          os.path.join(out, f"sheet_e529c_{lay}.jpg"), cols=4)

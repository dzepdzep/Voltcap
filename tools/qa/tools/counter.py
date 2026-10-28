#!/usr/bin/env python3
"""VoltCap QA — "testing the tester": what a recording really captured, using the app's frame counter (QaFault).

  python3 counter.py VIDEO.mp4 [--qa NAME_qa.txt]

With `--es voltcap_test_fault "counter,flash:N"` the app draws its own frame number (12 black/white squares, bottom-left)
on every frame and N single-frame magenta squares. This reads every video frame at full resolution and reports:
  * app frames drawn (counter range) vs app frames present in the video → the recording's true capture rate;
  * repeats (the same app frame in two video frames) and gaps (app frames never recorded);
  * the flashes seen, against the flashes the app logged (VoltcapQA `fault flash frame=`), and whether frames.py's
    generic detector found them (run frames.py on the same video and pass its events.json with --events).
"""
import argparse, json, os, re, subprocess, sys
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib


def frames(path):
    w, h = [int(x) for x in subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", path], capture_output=True, text=True).stdout.strip().split(",")]
    cmd = ["ffmpeg", "-v", "error", "-i", path] + qalib.passthrough_args() + ["-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE)
    n = w * h * 3
    while True:
        b = p.stdout.read(n)
        if len(b) < n: break
        yield np.frombuffer(b, np.uint8).reshape(h, w, 3)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video"); ap.add_argument("--qa"); ap.add_argument("--events")
    a = ap.parse_args()
    rec = qalib.check_recording(a.video)[0]
    if rec.status != qalib.PASS:
        print(rec); sys.exit(qalib.exit_code([rec]))
    seen, flashes_seen, vids = [], [], 0
    for f in frames(a.video):
        h, w, _ = f.shape
        y = h - 8
        bits = [int(f[y, 4 + b * 16 + 8].mean() > 128) for b in range(12)]
        n = sum(v << i for i, v in enumerate(bits))
        black = f[h - 18, 2].mean() < 40  # the counter's black backing is there
        seen.append(n if black else None)
        fx, fy = int(w * 0.62) + 80, int(h * 0.34) + 80
        r, g, bb = [int(x) for x in f[fy, fx]]
        if r > 200 and g < 90 and bb > 200:
            flashes_seen.append((vids, n))
        vids += 1
    nums = [x for x in seen if x is not None]
    # unwrap 12-bit wrap-around
    un, off, last = [], 0, None
    for x in nums:
        if last is not None and x < last - 2000: off += 4096
        un.append(x + off); last = x
    if not un:
        # Not a pass: nothing was measured (counter off, wrong screen, or an undecodable video).
        print("[INFRA] no frame counter found in the video (was voltcap_test_fault counter on?)"); sys.exit(2)
    lo, hi = min(un), max(un)
    present = set(un)
    missing = [i for i in range(lo, hi + 1) if i not in present]
    repeats = sum(1 for i in range(1, len(un)) if un[i] == un[i - 1])
    out = dict(video_frames=vids, with_counter=len(un), app_frames_span=hi - lo + 1, app_frames_recorded=len(present),
               captured_pct=round(100 * len(present) / (hi - lo + 1), 2), missing=len(missing), repeated=repeats,
               missing_runs=[], flashes_in_video=len(flashes_seen))
    run = []
    for m in missing:
        if run and m == run[-1] + 1: run.append(m)
        else:
            if run: out["missing_runs"].append((run[0], len(run)))
            run = [m]
    if run: out["missing_runs"].append((run[0], len(run)))
    out["missing_runs"] = out["missing_runs"][:30]
    if a.qa:
        logged = [int(m.group(1)) for m in re.finditer(r"fault flash frame=(\d+)", open(a.qa, errors="replace").read())]
        out["flashes_logged"] = len(logged)
        # The flash frame number is the same counter the frame shows: an exact match per injected flash.
        seen_nums = {n % 4096 for _, n in flashes_seen}
        got = [x for x in logged if x % 4096 in seen_nums]
        out["flashes_recorded"] = len(got)
        lost = [x for x in logged if x % 4096 not in seen_nums]
        out["flashes_lost"] = lost[:20]
        out["lost_because_frame_not_recorded"] = sum(1 for x in lost if (x % 4096) not in {u % 4096 for u in present})
    if a.events:
        ev = json.load(open(a.events))["events"]
        tr = [e["at"] for e in ev if e["kind"] == "transient"]
        hits = sum(1 for vf, _ in flashes_seen if any(abs(vf - t) <= 1 for t in tr))
        out["flashes_found_by_frames_py"] = hits
        out["frames_py_transients"] = len(tr)
    print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()

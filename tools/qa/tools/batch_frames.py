#!/usr/bin/env python3
"""VoltCap QA — run frames.py over many recordings and keep one validated summary (for the coverage matrix).

  python3 batch_frames.py OUT.json VIDEO.mp4 [VIDEO.mp4 ...] [--jobs 4] [--fail-on black,white,dip] [--workdir DIR]

Each recording is validated first (qalib.check_recording: missing / empty / truncated / undecodable = INFRA), then
analysed. Per recording the JSON holds status (PASS / FAIL / INFRA), the event counts, frames, seconds and fps.
`--fail-on` lists the unambiguous defects (default black, white, dip frames). Transients and hitches are counted
but never fail a run here: a slide or a crossfade is a transient too, so they need a human look at the contact sheets.
Exit: worst status over all recordings (0 only when every recording is PASS and at least one was given).
"""
import argparse, json, os, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
from qalib import Result, PASS, FAIL, INFRA

HERE = os.path.dirname(os.path.abspath(__file__))


def one(video, fail_on, workdir):
    name = os.path.splitext(os.path.basename(video))[0]
    out = os.path.join(workdir, name + "_frames")
    p = subprocess.run([sys.executable, os.path.join(HERE, "frames.py"), video, "-o", out, "--max-sheets", "0",
                        "--fail-on", fail_on, "--skip-start", "0.5"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    rec = dict(video=video, name=name, exit=p.returncode)
    ev = os.path.join(out, "events.json")
    if p.returncode in (0, 1) and os.path.exists(ev):
        with open(ev, encoding="utf-8") as f:
            j = json.load(f)
        s = j["summary"]
        rec.update(status=PASS if p.returncode == 0 else FAIL, frames=s["frames"], seconds=s["seconds"], fps=s["fps"],
                   counts=s["counts"], reason=j.get("reason", ""))
    else:
        rec.update(status=INFRA, reason=(p.stdout + p.stderr).strip()[-300:] or f"frames.py exit {p.returncode}")
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out"); ap.add_argument("videos", nargs="*")
    ap.add_argument("--jobs", type=int, default=4)
    ap.add_argument("--fail-on", default="black,white,dip")
    ap.add_argument("--workdir")
    a = ap.parse_args()
    work = a.workdir or tempfile.mkdtemp(prefix="qa_batch_")
    os.makedirs(work, exist_ok=True)
    with ThreadPoolExecutor(max_workers=max(1, a.jobs)) as ex:
        recs = list(ex.map(lambda v: one(v, a.fail_on, work), a.videos))
    results = [Result(r["name"], r["status"], r.get("reason", ""), r["video"]) for r in recs]
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(dict(verdict=qalib.verdict(results), counts=qalib.summarize(results), recordings=recs), f, indent=1)
    qalib.finish(results, f"{len(recs)} recordings")


if __name__ == "__main__":
    main()

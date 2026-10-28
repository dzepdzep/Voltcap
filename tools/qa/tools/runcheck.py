#!/usr/bin/env python3
"""VoltCap QA — validate a pulled device sweep (`device/layouts.sh` and any script using `device/guard.sh`).

  python3 runcheck.py OUT_DIR RUN [--min-frames N] [--json out.json] [--allow-missing-png]

Reads OUT_DIR/RUN_manifest.txt (start / expect / screen / done / skip / end lines) and checks, per expected name:
  * the sweep finished (an `end` line) and every expected name was either `done` or `skip`ped — else INFRA (interrupted);
  * `done` needs a fresh (not older than `start`), decodable, long-enough recording RUN_NAME.mp4, a gfxinfo summary
    with frames, a QaTrace log RUN_NAME_qa.txt that holds the run's `MARK layout NAME` and no crash / ANR (crash = FAIL),
    and a still RUN_NAME.png;
  * the screen at the start must be HOME (PICKER = wrong screen = FAIL; UNKNOWN / missing = INFRA).
`skip` is reported as SKIP with its reason. Nothing expected at all is NOT TESTED. Exit: 0 all PASS, else see qalib.
"""
import argparse, json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
import framestats
from qalib import Result, PASS, FAIL, INFRA, SKIP, NOT_TESTED


def read_manifest(path):
    m = dict(start=None, end=None, expect=[], done=set(), skip={}, screen={})
    for line in open(path, encoding="utf-8", errors="replace"):
        p = line.strip().split(None, 2)
        if not p:
            continue
        k = p[0]
        if k in ("start", "end") and len(p) > 1 and p[1].isdigit():
            m[k] = int(p[1])
        elif k == "expect" and len(p) > 1:
            m["expect"].append(p[1])
        elif k == "done" and len(p) > 1:
            m["done"].add(p[1])
        elif k == "skip" and len(p) > 1:
            m["skip"][p[1]] = p[2] if len(p) > 2 else "no reason given"
        elif k == "screen" and len(p) > 2:
            m["screen"][p[1]] = p[2]
    return m


def check_one(d, run, name, man, min_frames, need_png=True):
    base = os.path.join(d, f"{run}_{name}")
    t0 = man["start"]
    out = []
    scr = man["screen"].get(name)
    s = qalib.check_screen(scr if scr != "UNKNOWN" else None, "HOME", f"{name} start screen")
    if s.status != PASS:
        return [s]  # wrong screen / unknown screen: nothing recorded after it is evidence
    out += qalib.check_recording(base + ".mp4", min_frames=min_frames, not_before=t0, name=f"{name} recording")
    if os.path.exists(base + "_summary.txt"):
        r = framestats.parse(base + "_summary.txt")
        out.append(Result(f"{name} frame stats", PASS, f"{r['total']} frames, janky {r.get('janky_pct', '?')} %", base + "_summary.txt")
                   if r.get("total") else Result(f"{name} frame stats", INFRA, "gfxinfo summary holds no frames", base + "_summary.txt"))
    else:
        out.append(Result(f"{name} frame stats", INFRA, "summary missing", base + "_summary.txt"))
    out += qalib.check_log(base + "_qa.txt", must_contain=(f"MARK layout {name}",), not_before=t0, name=f"{name} QaTrace log")
    if need_png:
        png = base + ".png"
        if not os.path.exists(png) or os.path.getsize(png) < 1000:
            out.append(Result(f"{name} still", INFRA, "screenshot missing or empty", png))
        elif t0 and os.path.getmtime(png) < t0:
            out.append(Result(f"{name} still", INFRA, "STALE screenshot (older than this run)", png))
        else:
            out.append(Result(f"{name} still", PASS, "", png))
    return out


def run_checks(d, run, min_frames=30, need_png=True):
    mp = os.path.join(d, f"{run}_manifest.txt")
    if not os.path.exists(mp):
        return [Result("manifest", INFRA, "run manifest missing: the sweep never started or its output was not pulled", mp)]
    man = read_manifest(mp)
    res = []
    if man["start"] is None:
        res.append(Result("manifest", INFRA, "no start line", mp))
    if man["end"] is None:
        res.append(Result("manifest", INFRA, "no end line: the sweep was interrupted (crash, timeout, cable pulled)", mp))
    if not man["expect"]:
        return res + [Result("manifest", NOT_TESTED, "the sweep declared nothing to test", mp)]
    for name in man["expect"]:
        if name in man["skip"]:
            res.append(Result(name, SKIP, man["skip"][name], mp))
        elif name not in man["done"]:
            res.append(Result(name, INFRA, "never finished (no done / skip line): interrupted before this one", mp))
        else:
            sub = check_one(d, run, name, man, min_frames, need_png)
            worst = qalib.verdict(sub)
            bad = [r for r in sub if r.status != PASS]
            res.append(Result(name, worst, "; ".join(f"{r.name}: {r.reason}" for r in bad) or "; ".join(r.reason for r in sub if r.reason), mp))
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir"); ap.add_argument("run")
    ap.add_argument("--min-frames", type=int, default=30)
    ap.add_argument("--allow-missing-png", action="store_true")
    ap.add_argument("--json")
    a = ap.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if not os.path.isdir(a.out_dir):
        res = [Result("out dir", INFRA, "no such folder", a.out_dir)]
    else:
        res = run_checks(a.out_dir, a.run, a.min_frames, not a.allow_missing_png)
    qalib.finish(res, f"sweep {a.run}", a.json)


if __name__ == "__main__":
    main()

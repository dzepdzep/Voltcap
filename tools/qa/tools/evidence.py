#!/usr/bin/env python3
"""Update 474: is a device run's evidence bound to a known build, device and configuration, and fresh?

A run directory (pulled from the handheld) must hold manifest.txt from device/evidence.sh. check_run() returns
(status, info, reason):
  PASS   manifest complete, finished, the installed APK's SHA-256 is in tools/qa/builds.json, and every evidence file
         used (screenshots, dumps) was written between the run's start and finish
  INFRA  no manifest, interrupted (no finished=), a required field empty, an APK nobody recorded in builds.json, an
         --expect-build that doesn't match, or a stale / foreign evidence file (written before the run started or after
         it ended: left over from another run)
info carries the build record (base commit, staging patch SHA-256, source tree hash, versionName / Code) for the report.
  python3 evidence.py RUN_DIR [--ledger builds.json] [--expect-build ID]
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

HERE = os.path.dirname(os.path.abspath(__file__))
LEDGER = os.path.join(os.path.dirname(HERE), "builds.json")
REQUIRED = ("tag", "started", "finished", "serial", "model", "display", "density", "font_scale",
            "version_name", "version_code", "apk_sha256")
SLACK = 90  # seconds: device clock vs file times (pulled with tar, mtimes kept)


def read_manifest(run):
    p = os.path.join(run, "manifest.txt")
    if not os.path.exists(p):
        return None
    m, notes = {}, []
    for line in open(p, encoding="utf-8", errors="replace"):
        line = line.rstrip("\n")
        if "=" in line and " " not in line.split("=", 1)[0]:
            k, v = line.split("=", 1)
            m[k] = v
        elif line.strip():
            notes.append(line.strip())
    m["_notes"] = notes
    return m


def load_ledger(path=LEDGER):
    if not os.path.exists(path):
        return []
    return json.load(open(path, encoding="utf-8")).get("builds", [])


def evidence_files(run):
    pats = ("*.png", "*.raw", "qa_clip/*.json", "*_play.txt")
    return [f for p in pats for f in glob.glob(os.path.join(run, p))]


def check_run(run, ledger_path=LEDGER, expect_build=None, files=None):
    m = read_manifest(run)
    if m is None:
        return qalib.INFRA, {}, "no manifest.txt (run not bound to a build: evidence.sh missing or an older script)"
    missing = [k for k in REQUIRED if not m.get(k)]
    if missing:
        why = "interrupted run (no finished=)" if missing == ["finished"] else "manifest fields empty: " + ", ".join(missing)
        return qalib.INFRA, m, why
    builds = {b["apk_sha256"]: b for b in load_ledger(ledger_path)}
    b = builds.get(m["apk_sha256"])
    if b is None:
        return qalib.INFRA, m, f"installed APK {m['apk_sha256'][:12]}… is not in builds.json (unknown build)"
    if b.get("version_code") and str(b["version_code"]) != m["version_code"]:
        return qalib.INFRA, m, f"versionCode {m['version_code']} != the ledger's {b['version_code']} for that APK"
    if expect_build and b.get("id") != expect_build:
        return qalib.INFRA, m, f"build {b.get('id')} is not the expected {expect_build}"
    try:
        t0, t1 = int(m["started"]), int(m["finished"])
    except ValueError:
        return qalib.INFRA, m, "started / finished are not times"
    if t1 < t0:
        return qalib.INFRA, m, "finished before it started"
    stale = []
    for f in (files if files is not None else evidence_files(run)):
        t = os.path.getmtime(f)
        if t < t0 - SLACK or t > t1 + SLACK:
            stale.append(os.path.relpath(f, run))
    if stale:
        return qalib.INFRA, m, f"{len(stale)} evidence file(s) not from this run (stale / foreign): {', '.join(sorted(stale)[:4])}"
    info = dict(m); info["build"] = b
    return qalib.PASS, info, f"build {b.get('id')} ({m['version_name']} / {m['version_code']}), {m['model']} {m['display']} " \
                             f"{m['density']} dpi font {m['font_scale']}"


def label(info):
    b = info.get("build") or {}
    return (f"{b.get('id', '?')} apk {info.get('apk_sha256', '')[:12]} base {str(b.get('base_commit', ''))[:7]} "
            f"patch {str(b.get('patch_sha256', '') or '-')[:12]} tree {str(b.get('tree_hash', ''))[:12]} | {info.get('model', '?')} "
            f"{info.get('serial', '?')} {info.get('display', '?')} {info.get('density', '?')}dpi{('→' + info['density_override']) if info.get('density_override') else ''} "
            f"font {info.get('font_scale', '?')} | run {info.get('tag', '?')} {info.get('started', '')}–{info.get('finished', '')}")


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    run = sys.argv[1]
    led = sys.argv[sys.argv.index("--ledger") + 1] if "--ledger" in sys.argv else LEDGER
    exp = sys.argv[sys.argv.index("--expect-build") + 1] if "--expect-build" in sys.argv else None
    st, info, why = check_run(run, led, exp)
    qalib.finish([qalib.Result("evidence " + os.path.basename(os.path.normpath(run)), st, why)], "Evidence")


if __name__ == "__main__":
    main()

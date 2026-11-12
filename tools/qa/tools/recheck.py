#!/usr/bin/env python3
"""Sprint 525 (owner: "a recording must not count as complete merely because the file exists"): checks device recordings
made with tools/qa/device/rec.sh against their own log (NAME.rec.txt).

For every recording NAME.mp4 (and its segments NAME_p2.mp4 ...):
  - the file's real duration and frame count (ffprobe), and a full decode (ffmpeg -f null) for corruption;
  - compared with the time the recorder really ran (start / end in the log) and why it ended.
Grades: COMPLETE (one segment, ended by stop or its time, duration within 0.6 s of the run, decodes clean),
        PARTIAL (the recorder stopped early and rec.sh continued in segments - covered, with gaps; the gaps are listed),
        TRUNCATED (a file shorter than the recorder ran), CORRUPT (decode errors / unreadable), UNVERIFIED (no log).
Exit 0 only when every recording is COMPLETE.   usage: recheck.py DIR_OR_MP4... [--json out.json]
"""
import json, os, re, subprocess, sys

def probe(f):
    try:
        out = subprocess.run(["ffprobe", "-v", "error", "-count_packets", "-select_streams", "v:0", "-show_entries",
                              "stream=nb_read_packets,avg_frame_rate:format=duration", "-of", "json", f],
                             capture_output=True, text=True, timeout=120).stdout
        j = json.loads(out or "{}")
        dur = float(j.get("format", {}).get("duration", "nan"))
        frames = int((j.get("streams") or [{}])[0].get("nb_read_packets", 0))
    except Exception:
        return None, 0, False
    dec = subprocess.run(["ffmpeg", "-v", "error", "-i", f, "-f", "null", "-"], capture_output=True, text=True, timeout=300)
    return dur, frames, dec.returncode == 0 and not dec.stderr.strip()

def parse_log(path):
    segs = []
    for line in open(path, encoding="utf-8", errors="replace"):
        m = re.match(r"seg (\d+) file=(\S+) start=([\d.]+) end=([\d.]+) exit=(-?\d+) why=(\w+)", line.strip())
        if m:
            segs.append(dict(k=int(m[1]), file=m[2], start=float(m[3]), end=float(m[4]), exit=int(m[5]), why=m[6]))
    return segs

def grade(mp4):
    base = mp4[:-4]
    log = base + ".rec.txt"
    if not os.path.exists(log):
        dur, frames, clean = probe(mp4)
        return dict(file=os.path.basename(mp4), grade="UNVERIFIED", duration=dur, frames=frames, decodes=clean,
                    why="no rec.sh log - the file alone proves nothing")
    segs = parse_log(log)
    res = dict(file=os.path.basename(mp4), segments=[], gaps=[])
    worst = "COMPLETE"
    for i, s in enumerate(segs):
        f = os.path.join(os.path.dirname(mp4), s["file"])
        ran = s["end"] - s["start"]
        dur, frames, clean = probe(f) if os.path.exists(f) else (None, 0, False)
        g = "OK"
        if dur is None or dur != dur or not clean:
            g = "CORRUPT"
        elif dur < ran - 0.6 - (0.6 if s["why"] == "stop" else 0):  # (a stop by SIGINT can lose the last fraction of a second)
            g = "TRUNCATED"
        res["segments"].append(dict(s, duration=dur, frames=frames, decodes=clean, ran=round(ran, 2), grade=g))
        if g in ("CORRUPT", "TRUNCATED"):
            worst = g if worst != "CORRUPT" else worst
        if s["why"] == "EARLY" and worst == "COMPLETE":
            worst = "PARTIAL"
        if i > 0:
            res["gaps"].append(round(s["start"] - segs[i - 1]["end"], 2))
    if not segs:
        worst = "UNVERIFIED"
        res["why"] = "log has no finished segment (recorder still running or killed)"
    res["grade"] = worst
    return res

def main(argv):
    out = None
    if "--json" in argv:
        i = argv.index("--json"); out = argv[i + 1]; argv = argv[:i] + argv[i + 2:]
    files = []
    for a in argv:
        if os.path.isdir(a):
            files += sorted(os.path.join(a, f) for f in os.listdir(a) if f.endswith(".mp4") and not re.search(r"_p\d+\.mp4$", f))
        elif a.endswith(".mp4"):
            files.append(a)
    results = [grade(f) for f in files]
    for r in results:
        segs = r.get("segments", [])
        tail = " ".join(f"[{s['file']} ran {s['ran']}s file {s['duration'] and round(s['duration'], 2)}s {s['frames']}f {s['why']} {s['grade']}]" for s in segs)
        print(f"{r['grade']:10} {r['file']} {tail} {('gaps ' + str(r['gaps'])) if r.get('gaps') else ''} {r.get('why', '')}".rstrip())
    if out:
        json.dump(results, open(out, "w"), indent=1)
    return 0 if results and all(r["grade"] == "COMPLETE" for r in results) else 1

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

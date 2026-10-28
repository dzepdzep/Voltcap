#!/usr/bin/env python3
"""VoltCap QA — shared result model and evidence validators (stdlib only).

Every QA tool reports one of these statuses, and the process exit code follows the worst one:

  PASS        the check ran, had evidence, and the evidence is good                                  exit 0
  FAIL        the check ran and found an application defect / a failed assertion                    exit 1
  INFRA       the check could NOT run properly: missing / empty / truncated / stale evidence,
              broken tool, crash, timeout. Says nothing about the app; never a pass.                exit 2
  NOT TESTED  nothing was run for it (no device, unsafe, not implemented). Never a pass.            exit 3
  SKIP        deliberately not run (e.g. needs a hardware device in standard CI). Never a pass.      exit 3

`verdict()` of a list of results is the worst status present; an EMPTY list is NOT TESTED (an empty suite never passes).
When several statuses occur the verdict is the worst one in the order of WORST (INFRA, FAIL, NOT TESTED, SKIP, PASS).
"""
import json, os, shutil, subprocess, sys, time

PASS, FAIL, INFRA, NOT_TESTED, SKIP = "PASS", "FAIL", "INFRA", "NOT TESTED", "SKIP"
EXIT = {PASS: 0, FAIL: 1, INFRA: 2, NOT_TESTED: 3, SKIP: 3}
# Worst first: an infrastructure problem hides everything else (the run can't be trusted), then a real failure,
# then "nothing was run", and only an all-PASS list is a pass.
WORST = [INFRA, FAIL, NOT_TESTED, SKIP, PASS]

# Log lines that mean the app or the run died, wherever they appear in a captured log.
CRASH_MARKERS = ("FATAL EXCEPTION", "ANR in com.shelf.launcher", "Process com.shelf.launcher has died", "SIGSEGV", "Fatal signal")


class Result:
    def __init__(self, name, status, reason="", evidence=None):
        assert status in EXIT, status
        self.name, self.status, self.reason, self.evidence = name, status, reason, evidence

    def __repr__(self):
        return f"[{self.status}] {self.name}" + (f": {self.reason}" if self.reason else "") + (f" ({self.evidence})" if self.evidence else "")

    def as_dict(self):
        return dict(name=self.name, status=self.status, reason=self.reason, evidence=self.evidence)


def verdict(results):
    """Worst status in `results`; an empty list is NOT TESTED, never PASS."""
    if not results:
        return NOT_TESTED
    st = {r.status if isinstance(r, Result) else r for r in results}
    return next(s for s in WORST if s in st)


def exit_code(results):
    return EXIT[verdict(results)]


def summarize(results):
    c = {s: 0 for s in WORST}
    for r in results:
        c[r.status] += 1
    return c


def report_text(results, title="QA result"):
    c = summarize(results)
    lines = [f"{title}: {verdict(results)}  (" + ", ".join(f"{k}={v}" for k, v in c.items()) + f", total={len(results)})"]
    if not results:
        lines.append("  no checks ran: an empty run is NOT TESTED, not a pass")
    for r in sorted(results, key=lambda r: WORST.index(r.status)):
        if r.status != PASS:
            lines.append(f"  {r}")
    return "\n".join(lines)


def which(tool):
    return shutil.which(tool)


# ---------------------------------------------------------------------------------------------------- recordings

_PASSTHROUGH = None


def passthrough_args():
    """ffmpeg's "keep every frame, no re-timing" flag: `-fps_mode passthrough` (ffmpeg 5.1+); `-vsync 0` was removed
    in ffmpeg 8, which made the frame tools decode nothing. Older builds that don't know -fps_mode get -vsync 0."""
    global _PASSTHROUGH
    if _PASSTHROUGH is None:
        _PASSTHROUGH = ["-fps_mode", "passthrough"]
        if which("ffmpeg"):
            p = subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=s=16x16:d=0.1", "-fps_mode", "passthrough",
                                "-f", "null", "-"], capture_output=True)
            if p.returncode != 0:
                _PASSTHROUGH = ["-vsync", "0"]
    return list(_PASSTHROUGH)


def probe_video(path, timeout=60):
    """(frames, seconds, w, h) from ffprobe; raises RuntimeError (an INFRA problem) when the file can't be read."""
    if not which("ffprobe"):
        raise RuntimeError("ffprobe not installed")
    try:
        p = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-count_packets", "-show_entries",
                            "stream=width,height,nb_read_packets:format=duration", "-of", "json", path],
                           capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        raise RuntimeError("ffprobe timed out")
    if p.returncode != 0 or not p.stdout.strip():
        raise RuntimeError("ffprobe cannot read it (truncated / not a video?): " + (p.stderr.strip()[:200] or "no output"))
    j = json.loads(p.stdout)
    st = (j.get("streams") or [None])[0]
    if not st:
        raise RuntimeError("no video stream")
    secs = float((j.get("format") or {}).get("duration") or 0)
    # A cut-off file can still have its header (faststart / fragmented): the container then promises more than the
    # frames hold. The last frame's time must be near the declared length.
    q = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "packet=pts_time", "-of", "csv=p=0", path],
                       capture_output=True, text=True, timeout=timeout)
    pts = []
    for x in q.stdout.split():
        try:
            pts.append(float(x.split(",")[0]))
        except ValueError:
            pass
    # (screenrecord writes no frames while the screen is static, so a quiet tail is normal: only flag a file that holds
    # less than half of what its header declares.)
    if pts and secs and max(pts) < 0.5 * secs and secs - max(pts) > 0.25:
        raise RuntimeError(f"truncated: the container says {secs:.2f} s but the last frame is at {max(pts):.2f} s")
    return int(st.get("nb_read_packets") or 0), secs, int(st.get("width") or 0), int(st.get("height") or 0)


def check_recording(path, min_frames=10, min_seconds=0.5, min_bytes=2000, not_before=None, max_age_s=None, name=None):
    """Validate a recording as evidence. Returns a list of Result: [] never — a good file gives one PASS.

    not_before: epoch seconds (e.g. the run's start); a file older than that is STALE evidence.
    max_age_s : alternative to not_before: the file must have been written within this many seconds.
    """
    name = name or os.path.basename(path)
    if not os.path.exists(path):
        return [Result(name, INFRA, "recording missing", path)]
    if os.path.isdir(path):
        return [Result(name, INFRA, "recording path is a directory", path)]
    size = os.path.getsize(path)
    if size == 0:
        return [Result(name, INFRA, "recording is empty (0 bytes)", path)]
    if size < min_bytes:
        return [Result(name, INFRA, f"recording too small ({size} bytes < {min_bytes}): truncated?", path)]
    mt = os.path.getmtime(path)
    if not_before is not None and mt < not_before:
        return [Result(name, INFRA, f"STALE recording: written {time.strftime('%F %T', time.localtime(mt))}, "
                       f"before this run started {time.strftime('%F %T', time.localtime(not_before))}", path)]
    if max_age_s is not None and time.time() - mt > max_age_s:
        return [Result(name, INFRA, f"STALE recording: {int(time.time() - mt)} s old (limit {max_age_s} s)", path)]
    try:
        frames, secs, w, h = probe_video(path)
    except RuntimeError as e:
        return [Result(name, INFRA, str(e), path)]
    if w <= 0 or h <= 0:
        return [Result(name, INFRA, "video has no picture size", path)]
    if frames < min_frames:
        return [Result(name, INFRA, f"only {frames} frames (< {min_frames}): incomplete recording", path)]
    if secs < min_seconds:
        return [Result(name, INFRA, f"only {secs:.2f} s long (< {min_seconds} s): incomplete recording", path)]
    return [Result(name, PASS, f"{frames} frames, {secs:.1f} s, {w}x{h}", path)]


# ---------------------------------------------------------------------------------------------------- logs

def check_log(path, min_lines=1, must_contain=(), must_not_contain=CRASH_MARKERS, complete_marker=None,
              not_before=None, name=None):
    """Validate a captured log (logcat / VoltcapQA / gfxinfo dump) as evidence.

    must_contain: strings that must each appear (the run reached those steps).
    complete_marker: a line the run writes as its LAST act; absent ⇒ the run was interrupted / truncated.
    A crash marker in the log is a FAIL (the app died), not an infrastructure problem.
    """
    name = name or os.path.basename(path)
    if not os.path.exists(path):
        return [Result(name, INFRA, "log missing", path)]
    if os.path.getsize(path) == 0:
        return [Result(name, INFRA, "log is empty (0 bytes)", path)]
    if not_before is not None and os.path.getmtime(path) < not_before:
        return [Result(name, INFRA, "STALE log: older than this run", path)]
    txt = open(path, encoding="utf-8", errors="replace").read()
    lines = [l for l in txt.splitlines() if l.strip()]
    if len(lines) < min_lines:
        return [Result(name, INFRA, f"log has {len(lines)} lines (< {min_lines}): truncated?", path)]
    out = []
    for m in must_contain:
        if m not in txt:
            out.append(Result(name, INFRA, f"log incomplete: expected '{m}' never appears", path))
    if complete_marker and complete_marker not in txt:
        out.append(Result(name, INFRA, f"run interrupted: end marker '{complete_marker}' missing", path))
    for m in must_not_contain:
        if m in txt:
            out.append(Result(name, FAIL, f"crash/ANR marker in log: '{m}'", path))
    return out or [Result(name, PASS, f"{len(lines)} lines", path)]


# ---------------------------------------------------------------------------------------------------- screens

def check_screen(observed, expected, what="screen"):
    """A run that is on the wrong screen/layout/category never passes. `expected` may be one value or a collection."""
    exp = [expected] if isinstance(expected, str) else list(expected)
    if observed is None or observed == "":
        return Result(what, INFRA, f"could not tell which {what} was showing (expected {exp})")
    if observed not in exp:
        return Result(what, FAIL, f"wrong {what}: showing '{observed}', expected {exp}")
    return Result(what, PASS, f"{what} = {observed}")


def write_json(path, results, extra=None):
    d = dict(verdict=verdict(results), counts=summarize(results), results=[r.as_dict() for r in results])
    d.update(extra or {})
    with open(path, "w", encoding="utf-8") as f:
        json.dump(d, f, indent=1)


def finish(results, title, json_path=None):
    """Print the report, optionally write JSON, and exit with the right code. Never returns."""
    print(report_text(results, title))
    if json_path:
        write_json(json_path, results)
    sys.exit(exit_code(results))

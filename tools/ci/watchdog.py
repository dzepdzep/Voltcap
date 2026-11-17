#!/usr/bin/env python3
"""673e (D325): runs a command (the unit tests) and watches the machine it runs on.

GitHub's hosted runner was lost three times in a row in the unit-test step (15 Nov: 7f7a74d2, 9c9f41ac, 6308b2a4 - "the hosted
runner lost communication ... starves it for CPU/Memory"): the step's own timeout never fired, nothing after it ran, and a job's
log needs a signed-in download. This keeps the machine alive long enough to say why:
  - every minute one log line: memory available, load, tests started / ended, the test in flight;
  - before the machine runs out (MemAvailable under --min-avail-mb) or at --limit-min, public annotations - why, the tests in
    flight, the biggest processes - then the command's whole process group is stopped (TERM, KILL 15 s later), so the step
    ends and the steps after it (test_progress.py, the results upload) run;
  - at the end a notice with the lowest memory available and the highest load seen.
Exit code: the command's own, or 1 when the watchdog stopped it.
    python3 tools/ci/watchdog.py --progress app/build/test-progress.log -- ./gradlew ...
"""
import argparse
import os
import signal
import subprocess
import sys
import time


def esc(s):
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def meminfo(path="/proc/meminfo"):
    """{field: MB} from /proc/meminfo; {} where there is none (not Linux)."""
    out = {}
    try:
        with open(path) as fh:
            for ln in fh:
                k, _, v = ln.partition(":")
                p = v.split()
                if p and p[0].isdigit():
                    out[k.strip()] = int(p[0]) // 1024
    except OSError:
        pass
    return out


def progress(lines):
    """(started, ended, {test: start_ms} still running) from the unit-test listener's START / END lines."""
    started = ended = 0
    running = {}
    for ln in lines:
        p = ln.split()
        if len(p) >= 3 and p[0] == "START":
            started += 1
            running[p[1]] = int(p[2]) if p[2].isdigit() else 0
        elif len(p) >= 2 and p[0] == "END":
            ended += 1
            running.pop(p[1], None)
    return started, ended, running


def read_progress(path):
    try:
        with open(path, encoding="utf-8", errors="replace") as fh:
            return progress(fh.readlines())
    except OSError:
        return 0, 0, {}


def label(cmd):
    """A process's short name - its role, not its classpath."""
    for key, name in (("GradleWorkerMain", "Gradle worker (tests)"), ("GradleDaemon", "Gradle daemon"),
                      ("KotlinCompileDaemon", "Kotlin daemon"), ("GradleWrapperMain", "Gradle client")):
        if key in cmd:
            return name
    first = cmd.split(" ", 1)[0]
    return (os.path.basename(first) or cmd)[:40]


def biggest(n=5, proc="/proc"):
    """[(rss MB, pid, short name)] of the biggest processes."""
    rows = []
    try:
        pids = [p for p in os.listdir(proc) if p.isdigit()]
    except OSError:
        return rows
    for pid in pids:
        try:
            with open(os.path.join(proc, pid, "status")) as fh:
                rss = next((int(l.split()[1]) for l in fh if l.startswith("VmRSS:")), 0)
            with open(os.path.join(proc, pid, "cmdline"), "rb") as fh:
                cmd = fh.read().replace(b"\0", b" ").decode(errors="replace").strip()
        except (OSError, ValueError, IndexError):
            continue
        if cmd:
            rows.append((rss // 1024, int(pid), label(cmd)))
    rows.sort(reverse=True)
    return rows[:n]


def decide(avail_mb, elapsed_s, min_avail_mb, limit_s):
    """Why to stop now, or None."""
    if avail_mb is not None and avail_mb < min_avail_mb:
        return f"memory available {avail_mb} MB, under {min_avail_mb} MB"
    if elapsed_s >= limit_s:
        return f"still running after {int(elapsed_s // 60)} min (limit {int(limit_s // 60)} min)"
    return None


def stop(child, grace=15):
    try:
        pg = os.getpgid(child.pid)
    except OSError:
        return
    for sig, wait in ((signal.SIGTERM, grace), (signal.SIGKILL, 5)):
        try:
            os.killpg(pg, sig)
        except OSError:
            return
        end = time.time() + wait
        while time.time() < end:
            if child.poll() is not None:
                return
            time.sleep(0.5)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--progress", default="app/build/test-progress.log")
    ap.add_argument("--min-avail-mb", type=int, default=1500)
    ap.add_argument("--limit-min", type=float, default=35)
    ap.add_argument("--every", type=float, default=60, help="seconds between status lines")
    ap.add_argument("--poll", type=float, default=5)
    ap.add_argument("cmd", nargs=argparse.REMAINDER)
    a = ap.parse_args(argv)
    cmd = a.cmd[1:] if a.cmd[:1] == ["--"] else a.cmd
    if not cmd:
        ap.error("no command")
    t0 = time.time()
    child = subprocess.Popen(cmd, start_new_session=True)
    low = None; low_at = ""; high_load = 0.0; last = 0.0; stopped = None
    while child.poll() is None:
        time.sleep(a.poll)
        m = meminfo()
        avail = m.get("MemAvailable")
        try:
            load = os.getloadavg()[0]
        except OSError:
            load = 0.0
        high_load = max(high_load, load)
        if avail is not None and (low is None or avail < low):
            low, low_at = avail, time.strftime("%H:%M:%S")
        el = time.time() - t0
        if el - last >= a.every:
            last = el
            s, e, run = read_progress(a.progress)
            now = int(time.time() * 1000)
            fl = ", ".join(f"{n} ({(now - t) // 1000 if t else '?'} s)" for n, t in list(run.items())[:3])
            print(f"watchdog {time.strftime('%H:%M:%S')}: {int(el // 60)} min, available {avail} of {m.get('MemTotal')} MB, "
                  f"load {load:.1f}, tests {s} started / {e} ended" + (f", in flight: {fl}" if fl else ""), flush=True)
        why = decide(avail, el, a.min_avail_mb, a.limit_min * 60)
        if why and child.poll() is None:
            stopped = why
            s, e, run = read_progress(a.progress)
            now = int(time.time() * 1000)
            names = "; ".join(f"{n} for {(now - t) // 1000 if t else '?'} s" for n, t in run.items()) or "none (between tests)"
            top = "; ".join(f"{name} {rss} MB" for rss, _, name in biggest())
            print(f"::error title=Unit tests stopped by the watchdog::{esc(why)}. Tests {s} started / {e} ended. In flight: {esc(names)}. "
                  f"Biggest processes: {esc(top)}. Load {load:.1f}.", flush=True)
            stop(child)
            break
    rc = child.wait()
    print(f"::notice title=Unit-test machine::lowest memory available {low} MB (at {low_at}), highest load {high_load:.1f}, "
          f"{int((time.time() - t0) // 60)} min" + (", stopped by the watchdog" if stopped else ""), flush=True)
    return 1 if stopped else rc


if __name__ == "__main__":
    sys.exit(main())

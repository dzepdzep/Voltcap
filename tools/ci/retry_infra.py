#!/usr/bin/env python3
"""681q (CI): re-runs a Gradle command only when it failed on the package servers, never on the code.

Verify 310 went red twice on 17 Nov (c342492's sibling run, f303833) with the same cause: Maven Central answered
"Received status code 429 from server: Too Many Requests" while Gradle fetched a plugin jar - the other runner image passed the
whole suite on the same commit. A red check that says nothing about the code trains everyone to ignore red.

This runs the command, streams its output unchanged and keeps the tail. When it exits non-zero it is retried (after --wait s,
doubled each time, at most --attempts runs) ONLY if:
  - the output names a repository answer that is the server's fault: HTTP 429 / 500 / 502 / 503 / 504 on a Could not
    GET / HEAD / download / resolve line, or "Too Many Requests", and
  - the output shows no sign of the code failing: no failing test, no Kotlin / Java compile error, no failed compile / test /
    lint task, no watchdog stop.
Anything else keeps its own exit code on the first run - a real failure is never retried into green. Each retry is a public
warning annotation naming the server answer, so a green run that needed one stays visible.
    python3 tools/ci/retry_infra.py [--attempts 3] [--wait 60] -- bash ./gradlew ...
"""
import argparse
import collections
import re
import subprocess
import sys
import time

INFRA = [
    re.compile(r"Could not (?:GET|HEAD|download|resolve)\b.*(?:status code|code) (429|50[0234])\b", re.I),
    re.compile(r"Received status code (429|50[0234]) from server", re.I),
    re.compile(r"Too Many Requests()", re.I),
]
CODE = [
    re.compile(r"There were failing tests"),
    re.compile(r"\d+ tests completed, \d+ failed"),
    re.compile(r"^e: ", re.M),                                  # Kotlin compiler error
    re.compile(r"\.java:\d+: error:"),                       # javac error
    re.compile(r"Compilation (?:error|failed)", re.I),
    re.compile(r"> Task (?:\S*:)?(?:compile\w*|test\w*UnitTest|lint\w*) FAILED"),  # not mergeDebugUnitTestManifest
    re.compile(r"Unit tests stopped by the watchdog"),
]


def classify(text):
    """('infra', '<server answer>') when only the package servers failed, ('code', reason) when the code did, (None, '')."""
    for rx in CODE:
        m = rx.search(text)
        if m:
            return "code", m.group(0).strip()[:120]
    for rx in INFRA:
        m = rx.search(text)
        if m:
            code = m.group(1) or "429"
            return "infra", "HTTP %s from a package server" % code
    return None, ""


def run_once(cmd, keep):
    tail = collections.deque(maxlen=keep)
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
    for ln in p.stdout:
        sys.stdout.write(ln)
        tail.append(ln)
    sys.stdout.flush()
    return p.wait(), "".join(tail)


def main(argv=None, runner=run_once, sleep=time.sleep):
    ap = argparse.ArgumentParser()
    ap.add_argument("--attempts", type=int, default=3)
    ap.add_argument("--wait", type=float, default=60.0)
    ap.add_argument("--keep", type=int, default=4000, help="output lines kept for the verdict")
    ap.add_argument("cmd", nargs=argparse.REMAINDER)
    a = ap.parse_args(argv)
    cmd = a.cmd[1:] if a.cmd and a.cmd[0] == "--" else a.cmd
    if not cmd:
        ap.error("no command")
    wait = a.wait
    for n in range(1, max(1, a.attempts) + 1):
        rc, tail = runner(cmd, a.keep)
        if rc == 0:
            return 0
        kind, why = classify(tail)
        if kind != "infra" or n >= a.attempts:
            if kind == "infra":
                print("::error title=Package servers still failing::%s after %d runs - not a code failure" % (why, n), flush=True)
            return rc
        print("::warning title=Retrying after a package-server failure::%s (run %d of %d, no code failure in the output); "
              "waiting %d s" % (why, n, a.attempts, wait), flush=True)
        sleep(wait)
        wait *= 2
    return rc


if __name__ == "__main__":
    sys.exit(main())

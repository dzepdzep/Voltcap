#!/usr/bin/env python3
"""681q (CI): re-runs a Gradle command only when it failed on the package servers, never on the code.

Verify 310 went red twice on 17 Nov (c342492's sibling run, f303833) with the same cause: Maven Central answered
"Received status code 429 from server: Too Many Requests" while Gradle fetched a plugin jar - the other runner image passed the
whole suite on the same commit. A red check that says nothing about the code trains everyone to ignore red.

This runs the command, streams its output unchanged and keeps the tail. When it exits non-zero it is retried (after --wait s,
doubled each time, at most --attempts runs) ONLY if:
  - the output names a repository answer that is the server's fault: HTTP 429 / 500 / 502 / 503 / 504 on a Could not
    GET / HEAD / download / resolve line, or "Too Many Requests", and
  - the output shows no sign of the code failing: no failing test, no Kotlin / Java compile error, no watchdog stop, and no
    failed compile / test / lint task - except one whose every Gradle "What went wrong" block is a dependency download
    refused with 429 / 5xx (702a: the test task fails that way when Maven Central refuses a Robolectric jar; no test ran),
    and failing tests only when EVERY failed test printed is a Robolectric runtime fetch refused with 429 / 5xx and their
    number equals the "N tests completed, M failed" totals (704a: 0288490, 1134 of 1134 failures were that 429).
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
    re.compile(r"Server returned HTTP response code: (429|50[0234])\b", re.I),     # 704a: Robolectric's own fetch
]
CODE = [
    re.compile(r"There were failing tests"),
    re.compile(r"\d+ tests completed, \d+ failed"),
    re.compile(r"^e: ", re.M),                                  # Kotlin compiler error
    re.compile(r"\.java:\d+: error:"),                       # javac error
    re.compile(r"Compilation (?:error|failed)", re.I),
    re.compile(r"Unit tests stopped by the watchdog"),
]
# 702a: a failed compile / test / lint task is a code failure - unless Gradle says the task failed only because a dependency
# could not be downloaded. 9073da2 (23 Nov, ubuntu-24.04): ":app:testDebugUnitTest FAILED" because Maven Central answered 429
# for a Robolectric runtime jar while the task resolved its classpath, so no test ran - and the line above made it "code", so
# four Verify runs in a row went red without the retry ever starting.
TASK_FAILED = re.compile(r"> Task (?:\S*:)?(?:compile\w*|test\w*UnitTest|lint\w*) FAILED")  # not mergeDebugUnitTestManifest
WHAT_WENT_WRONG = re.compile(r"^\S*\s*\* What went wrong:", re.M)
BLOCK_END = re.compile(r"^\S*\s*\* (?:Try|Exception is|Get more help):", re.M)
RESOLVE = re.compile(r"Could not (?:resolve all (?:files|dependencies|artifacts)|download|get resource)\b", re.I)


# 704a: 0288490 (23 Nov, ubuntu-latest): Robolectric fetches android-all-instrumented itself when a test class starts, so a 429
# shows up as 1134 failing tests ("Failed to fetch maven artifact ... Server returned HTTP response code: 429") and a
# "There were failing tests" block - classed as code, no retry. Each failed test is counted while the output streams (the
# 4000-line tail is far shorter than 1134 stack traces).
STAMP = re.compile(r"^\d{4}-\d\d-\d\dT\S+Z ")
TEST_HEADER = re.compile(r"^(?!> Task)\S.* > .* FAILED\s*$")
TEST_INFRA = re.compile(r"(?:HTTP response code|status code):? (?:429|50[0234])\b")
TEST_TOTAL = re.compile(r"^\d+ tests completed, (\d+) failed")


class TestFailures:
    """Counts the failed tests Gradle prints and how many of them failed only on a 429 / 5xx fetch."""

    def __init__(self):
        self.headers = self.infra = self.reported = 0
        self._open = False      # inside a failed test's indented block
        self._hit = False

    def _close(self):
        if self._open and self._hit:
            self.infra += 1
        self._open = self._hit = False

    def feed(self, line):
        ln = STAMP.sub("", line.rstrip("\n"))
        if self._open and (not ln.strip() or ln[:1].isspace()):
            if TEST_INFRA.search(ln):
                self._hit = True
            return
        self._close()
        if TEST_HEADER.match(ln):
            self.headers += 1
            self._open = True
            return
        m = TEST_TOTAL.match(ln)
        if m:
            self.reported += int(m.group(1))

    def done(self):
        self._close()
        return self

    def infra_only(self):
        return self.headers > 0 and self.infra == self.headers == self.reported

    @classmethod
    def of(cls, text):
        t = cls()
        for ln in text.splitlines():
            t.feed(ln)
        return t.done()


def _failure_blocks(text):
    """The text of each Gradle "* What went wrong:" block (to the next "* Try:" / block)."""
    starts = [m.start() for m in WHAT_WENT_WRONG.finditer(text)]
    out = []
    for i, st in enumerate(starts):
        nxt = starts[i + 1] if i + 1 < len(starts) else len(text)
        end = BLOCK_END.search(text, st + 1, nxt)
        out.append(text[st:end.start() if end else nxt])
    return out


def only_downloads_failed(text, tests_infra=False):
    """True when Gradle reports failures and EVERY one is a dependency download refused with 429 / 5xx (or, with
    tests_infra, a failing-tests block whose every failed test was such a fetch)."""
    blocks = _failure_blocks(text)

    def infra_block(b):
        if tests_infra and FAILING_TESTS.search(b):
            return True
        return RESOLVE.search(b) and any(rx.search(b) for rx in INFRA)
    return bool(blocks) and all(infra_block(b) for b in blocks)


FAILING_TESTS = re.compile(r"There were failing tests")
TEST_COUNTS = re.compile(r"\d+ tests completed, \d+ failed")


def classify(text, tests=None):
    """('infra', '<server answer>') when only the package servers failed, ('code', reason) when the code did, (None, '').
    tests: a TestFailures counted over the WHOLE output (default: counted over text)."""
    tests = tests if tests is not None else TestFailures.of(text)
    tests_infra = tests.infra_only()
    for rx in CODE:
        if tests_infra and rx.pattern in (FAILING_TESTS.pattern, TEST_COUNTS.pattern):
            continue
        m = rx.search(text)
        if m:
            return "code", m.group(0).strip()[:120]
    m = TASK_FAILED.search(text)
    if m and not only_downloads_failed(text, tests_infra):
        return "code", m.group(0).strip()[:120]
    for rx in INFRA:
        m = rx.search(text)
        if m:
            code = m.group(1) or "429"
            return "infra", "HTTP %s from a package server" % code
    if tests_infra:      # the 429 stack traces scrolled out of the tail; the whole-stream count still saw every one
        return "infra", "HTTP 429 / 5xx from a package server (%d failed tests, all a Robolectric fetch)" % tests.headers
    return None, ""


def run_once(cmd, keep):
    tail = collections.deque(maxlen=keep)
    tests = TestFailures()
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
    for ln in p.stdout:
        sys.stdout.write(ln)
        tail.append(ln)
        tests.feed(ln)
    sys.stdout.flush()
    return p.wait(), "".join(tail), tests.done()


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
        res = runner(cmd, a.keep)
        rc, tail = res[0], res[1]
        if rc == 0:
            return 0
        kind, why = classify(tail, res[2] if len(res) > 2 else None)
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

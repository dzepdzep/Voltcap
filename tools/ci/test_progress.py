#!/usr/bin/env python3
"""673b: GitHub annotations from app/build/test-progress.log (written by the unit-test task's listener, app/build.gradle.kts).

CI's Verify job hung twice in :app:testDebugUnitTest (7f7a74d2: the runner lost after 53 min; 9c9f41ac: over 35 min) and a job's
log needs a signed-in download, while annotations are public. Each test that started and never ended is named as an error, each
failed test too, the slowest few as warnings, and a one-line count as a notice. Exit code 0 always: the test step itself decides
the job's result.   python3 tools/ci/test_progress.py app/build/test-progress.log
"""
import os
import sys


def parse(lines):
    """[(name, start_ms)] that never ended, [(name, ms)] ended with their time, [names] failed."""
    started, ended, failed = {}, {}, []
    for ln in lines:
        p = ln.strip().split(" ")
        if len(p) >= 3 and p[0] == "START":
            started[p[1]] = int(p[2]) if p[2].isdigit() else 0
        elif len(p) >= 4 and p[0] == "END":
            ended[p[1]] = int(p[3]) if p[3].lstrip("-").isdigit() else 0
            if p[2] == "FAILURE":
                failed.append(p[1])
    open_ = [(n, t) for n, t in started.items() if n not in ended]
    return open_, sorted(ended.items(), key=lambda kv: -kv[1]), failed


def esc(s):
    return s.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(argv):
    path = argv[1] if len(argv) > 1 else "app/build/test-progress.log"
    if not os.path.exists(path):
        print(f"::warning title=Unit-test progress::{esc(path)} not written (the test task did not start)")
        return 0
    with open(path, encoding="utf-8", errors="replace") as fh:
        open_, timed, failed = parse(fh.readlines())
    for n, t in open_:
        print(f"::error title=Unit test never ended::{esc(n)} (started at {t})")
    for n in failed[:20]:
        print(f"::error title=Unit test failed::{esc(n)}")
    for n, ms in timed[:5]:
        if ms >= 20000:
            print(f"::warning title=Slow unit test::{esc(n)} took {ms // 1000} s")
    print(f"::notice title=Unit tests::{len(timed)} ended, {len(open_)} never ended, {len(failed)} failed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

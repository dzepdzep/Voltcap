#!/usr/bin/env python3
"""717a: grades tools/qa/device/row714.sh (HW-24 714a, Tile home Row place keeps the round buttons' hand-placed spot per
place) from the run folder: log.txt (`expect STEP SHORTCUTS KEPT` before each Row tile change) and rowtile.txt (the QA app's
`rowtile <layout> <from>-><to> spot=.. shortcuts=.. kept=..` lines, one per change, in order).
PASS only when every expected step has its line, layout TILES, spot ROW, the buttons' spot and every kept spot as expected,
the settings snapshot restored OK, no crash, and the owner's Voltcap untouched (same process before / after, or none).
Missing evidence is INFRA (no verdict). usage: python3 row714.py RUN_DIR   (exit 0 PASS, 1 FAIL, 2 INFRA)"""
import os, re, sys

LINE = re.compile(r'rowtile (\S+) (\S+)->(\S+) spot=(\S+) shortcuts=(\S+) kept=(\S+)')


def read(path):
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read().splitlines()
    except OSError:
        return None


def grade(run):
    """[(verdict, text)] and the overall verdict PASS / FAIL / INFRA."""
    log, rows = read(os.path.join(run, 'log.txt')), read(os.path.join(run, 'rowtile.txt'))
    if log is None or rows is None:
        return [('INFRA', 'log.txt or rowtile.txt missing')], 'INFRA'
    out = []
    if not any(l.strip() == 'DONE' for l in log):
        out.append(('INFRA', 'run did not finish (no DONE)'))
    for l in log:
        if l.startswith('STOP'):
            out.append(('INFRA', 'run stopped: ' + l[5:]))
    expects = [l.split()[1:] for l in log if l.startswith('expect ')]
    got = [m.groups() for m in (LINE.search(r) for r in rows) if m]
    if not expects:
        out.append(('INFRA', 'no expect steps in log.txt'))
    if len(got) < len(expects):
        out.append(('INFRA', '%d rowtile lines for %d steps (an old build without the 717a line?)' % (len(got), len(expects))))
    for i, e in enumerate(expects):
        if i >= len(got) or len(e) < 3:
            break
        step, want_sc, want_kept = e[0], e[1], e[2]
        layout, frm, to, spot, sc, kept = got[i]
        if layout != 'TILES' or spot != 'ROW':
            out.append(('INFRA', '%s: layout=%s spot=%s (needs Tile home, buttons by the tiles)' % (step, layout, spot)))
            continue
        ok = (want_sc == 'any' or sc == want_sc) and (want_kept == 'any' or kept == want_kept)
        out.append(('PASS' if ok else 'FAIL', '%-4s %s->%s shortcuts=%s kept=%s%s' % (
            step, frm, to, sc, kept, '' if ok else '   (expected shortcuts=%s kept=%s)' % (want_sc, want_kept))))
    restore = next((l for l in log if l.startswith('restore:')), None)
    if restore is None or not restore[8:].strip():
        out.append(('INFRA', 'restore result missing'))
    elif not restore[8:].strip().startswith('OK'):
        out.append(('FAIL', 'settings restore: ' + restore[8:].strip()))
    tgt = next((l for l in log if l.startswith('target:')), '')
    end = next((l for l in log if l.startswith('real_after=')), None)
    if end is None:
        out.append(('INFRA', 'end line missing'))
    else:
        before = (re.search(r'real_pid=(\S*)', tgt) or [None, ''])[1]
        after = (re.search(r'real_after=(\S*)', end) or [None, ''])[1] or 'none'
        fatal = int((re.search(r'fatal=(\d+)', end) or [None, '0'])[1])
        if fatal:
            out.append(('FAIL', '%d FATAL EXCEPTION in logcat' % fatal))
        if before != after:
            out.append(('FAIL', "owner's Voltcap process changed: %s -> %s" % (before, after)))
    vs = {v for v, _ in out}
    overall = 'INFRA' if 'INFRA' in vs else 'FAIL' if 'FAIL' in vs else 'PASS' if 'PASS' in vs else 'INFRA'
    return out, overall


def main():
    if len(sys.argv) != 2:
        print(__doc__); return 2
    out, overall = grade(sys.argv[1])
    for v, t in out:
        print('%-5s %s' % (v, t))
    print('VERDICT', overall)
    return {'PASS': 0, 'FAIL': 1}.get(overall, 2)


if __name__ == '__main__':
    sys.exit(main())

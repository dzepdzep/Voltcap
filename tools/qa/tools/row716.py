#!/usr/bin/env python3
"""719a: grades tools/qa/device/row716.sh (HW-24 716a in its real combination: the Voltcap row's column never starts under the
status bar) from the run folder: log.txt (`expect STEP PLACE COMBO` before each Row place) and rowcol.txt (the QA app's
`rowcol <layout> <place> top= bottom= bar= screen= legend= header= info= cats= player=` lines, dp, one per step, in order).
PASS only when every step has its line, layout CLASSIC at the expected place, the combination really on where asked (game
header, Game info + news, every category, a song in the bar's player), the column's top at or below the status bar's bottom
edge (Middle / Bottom with the bar along the top) and on screen, the settings snapshot restored OK, no crash, and the owner's
Voltcap untouched. A column taller than the free room may spill at the bottom (716a: the top stays readable) - noted, not
failed. Missing evidence is INFRA (no verdict). usage: python3 row716.py RUN_DIR   (exit 0 PASS, 1 FAIL, 2 INFRA)"""
import os, re, sys

LINE = re.compile(r'rowcol (\S+) (\S+) top=(-?[\d.]+) bottom=(-?[\d.]+) bar=([\d.]+) screen=([\d.]+) legend=(\S+) '
                  r'header=(\S+) info=(\S+) cats=(\S+) player=(\S+)')
TOL = 0.6  # dp (rounding of px -> dp)


def read(path):
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read().splitlines()
    except OSError:
        return None


def check(step, place, combo, g):
    """(verdict, text) for one step."""
    layout, at, top, bottom, bar, screen, legend, header, info, cats, player = g
    top, bottom, bar, screen = float(top), float(bottom), float(bar), float(screen)
    head = '%-3s %-6s combo=%-3s top=%.1f bottom=%.1f bar=%.1f screen=%.1f' % (step, at, combo, top, bottom, bar, screen)
    if layout != 'CLASSIC' or at != place:
        return 'INFRA', '%s: layout=%s place=%s (needs the Voltcap row at %s)' % (step, layout, at, place)
    if combo == 'on' and (header, info, cats, player) != ('on', 'BOTH', 'FULL', 'on'):
        return 'INFRA', '%s: combination not on (header=%s info=%s cats=%s player=%s)' % (step, header, info, cats, player)
    if bottom <= top or screen <= 0:
        return 'INFRA', '%s: column not measured' % head
    if place != 'TOP' and top + TOL < bar:
        return 'FAIL', '%s   (column starts %.1f dp under the status bar)' % (head, bar - top)
    if top + TOL < 0:
        return 'FAIL', '%s   (column starts above the screen)' % head
    tall = bottom - top > screen - bar + TOL
    if bottom > screen + TOL and not tall:
        return 'FAIL', '%s   (column fits but runs %.1f dp off the bottom)' % (head, bottom - screen)
    return 'PASS', head + ('   (taller than the free room: spills at the bottom, top readable)' if tall else '')


def grade(run):
    """[(verdict, text)] and the overall verdict PASS / FAIL / INFRA."""
    log, rows = read(os.path.join(run, 'log.txt')), read(os.path.join(run, 'rowcol.txt'))
    if log is None or rows is None:
        return [('INFRA', 'log.txt or rowcol.txt missing')], 'INFRA'
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
        out.append(('INFRA', '%d rowcol lines for %d steps (an old build without the 719a line?)' % (len(got), len(expects))))
    for i, e in enumerate(expects):
        if i >= len(got) or len(e) < 3:
            break
        out.append(check(e[0], e[1], e[2], got[i]))
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

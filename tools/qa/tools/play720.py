#!/usr/bin/env python3
"""720a: grades tools/qa/device/play720.sh (HW-24 715a: the Voltcap row's added Play part moved with the real Edit Home D-pad,
and where the home's up / down then go) from the run folder: log.txt (`expect STEP WHAT` before each reach line) and reach.txt
(the QA app's `reach <layout> dock= header= added= saved= live= drawn= above= up= down= buttons= zone= edit=` lines, in order).
WHAT:  above / below  Play is drawn above / below the middle and the D-pad's choice (above=) agrees with where it is drawn
       zone           the real key just pressed on the home landed on Play (zone=HEADER)
       edit           Edit Home is open
       live+N         Edit Home open, the part being moved N grid steps (0.02) below the last saved place (negative: above)
       kept           Edit Home closed, the saved place is the one saved at the last `below` step (Discard / restart kept it)
       reset          no saved place any more, and the D-pad's choice agrees with where Play is drawn
Every line must be the Voltcap row (CLASSIC) with the header off and the Play part added, else INFRA (setup did not apply).
PASS only with every step graded PASS, the snapshot restored OK, no crash and the owner's Voltcap untouched; a STOP (a safety
gate - e.g. Edit Home not open, so no key was pressed) or missing evidence is INFRA.
usage: python3 play720.py RUN_DIR   (exit 0 PASS, 1 FAIL, 2 INFRA)"""
import os, re, sys

LINE = re.compile(r'reach (\S+) dock=(\S+) header=(\S+) added=(\S+) saved=(\S+) live=(\S+) drawn=(\S+) above=(\S+) '
                  r'up=(\S+) down=(\S+) buttons=(\d+) zone=(\S+) edit=(\S+)')
STEP = 0.02
TOL = 0.011


def read(path):
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            return f.read().splitlines()
    except OSError:
        return None


def num(v):
    return None if v == 'none' else float(v)


def check(step, what, g, ctx):
    """(verdict, text) for one step; ctx carries the last saved place across steps."""
    layout, dock, header, added, saved, live, drawn, above, up, down, buttons, zone, edit = g
    saved, live, drawn, above = num(saved), num(live), num(drawn), above == 'true'
    head = '%-4s %-6s dock=%-6s saved=%s live=%s drawn=%s above=%s zone=%s edit=%s' % (
        step, what, dock, g[4], g[5], g[6], g[7], zone, edit)
    if layout != 'CLASSIC' or header != 'off' or added != 'yes':
        return 'INFRA', '%s: layout=%s header=%s added=%s (setup did not apply)' % (step, layout, header, added)
    m = re.match(r'live([+-]\d+)$', what)
    if what in ('above', 'below', 'reset', 'kept'):
        if edit != 'off':
            return 'INFRA', head + '   (Edit Home still open)'
        if drawn is None:
            return 'FAIL', head + '   (the added Play part is not drawn)'
        if above != (drawn < 0.5):
            return 'FAIL', head + '   (the D-pad goes %s, Play is drawn %s)' % ('up' if above else 'down', 'above' if drawn < 0.5 else 'below')
        if what in ('above', 'below') and above != (what == 'above'):
            return 'FAIL', head + '   (Play expected %s the middle)' % what
        if what == 'below':
            if ctx.get('live') is not None and (saved is None or abs(saved - ctx['live']) > TOL):
                return 'FAIL', head + '   (Done did not save the moved place %.3f)' % ctx['live']
            ctx['kept'] = saved
        if what == 'kept' and (saved is None or ctx.get('kept') is None or abs(saved - ctx['kept']) > TOL):
            return 'FAIL', head + '   (saved place changed: expected %s)' % ctx.get('kept')
        if what == 'reset' and saved is not None:
            return 'FAIL', head + '   (Reset this part left a saved place)'
        ctx['saved'] = saved
        return 'PASS', head
    if what == 'zone':
        if zone != 'HEADER':
            return 'FAIL', head + '   (the key did not reach Play)'
        return 'PASS', head
    if what == 'edit':
        if edit != 'on':
            return 'INFRA', head + '   (Edit Home did not open)'
        ctx['saved'] = saved
        return 'PASS', head
    if m:
        n = int(m.group(1))
        if edit != 'on':
            return 'INFRA', head + '   (Edit Home not open)'
        base = ctx.get('saved')
        if base is None or live is None:
            return 'FAIL', head + '   (no place to compare: saved=%s live=%s)' % (base, g[5])
        want = base + n * STEP
        if abs(live - want) > TOL:
            return 'FAIL', head + '   (expected live %.3f: %+d step(s) from %.3f)' % (want, n, base)
        ctx['live'] = live
        return 'PASS', head
    return 'INFRA', '%s: unknown expectation %s' % (step, what)


def grade(run):
    """[(verdict, text)] and the overall verdict PASS / FAIL / INFRA."""
    log, rows = read(os.path.join(run, 'log.txt')), read(os.path.join(run, 'reach.txt'))
    if log is None or rows is None:
        return [('INFRA', 'log.txt or reach.txt missing')], 'INFRA'
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
        out.append(('INFRA', '%d reach lines for %d steps (an old build without the 720a line?)' % (len(got), len(expects))))
    ctx = {}
    # (682ar, x1.3 on the KONKR / RP Classic: the scale change re-created the home, which logged one reach line from before the
    # setup - every step then read its neighbour's line. Lines from before the setup (header on / Play not added) at the start
    # are skipped, never more than the extra lines there are.)
    j = 0
    while j < len(got) and len(got) - j > len(expects) and (got[j][0], got[j][2], got[j][3]) != ('CLASSIC', 'off', 'yes'):
        j += 1
    for i, e in enumerate(expects):
        if j + i >= len(got) or len(e) < 2:
            break
        out.append(check(e[0], e[1], got[j + i], ctx))
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

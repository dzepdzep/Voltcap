#!/usr/bin/env python3
"""682av (owner 25 Nov - hardware QA of 723a, R1 Focus position): grades a `p2review.sh focuspos` run folder.

PASS only when: the choice saved in the QA app's own settings is back after the QA app restarts; every choice of the Voltcap row
(Left / Middle) and the Tile home (Edges / Left / Middle / Right) is taken as asked and a press steps round them all; the
tile is hidden on the other layouts tried; the R1 panel's own prefs never change; and the game you're on sits where the
choice says while browsing (QaClip's `origin` - the focused game's card): Left near the left, Middle in the middle, Right near
the right, the same place after four moves right and four back (Edges: free). Missing evidence is INFRA, never PASS.
"""
import json, os, re, sys

def lines(d):
    p = os.path.join(d, 'focuspos.txt')
    return [re.sub(r'^.*VoltcapQA: ', '', l.strip()) for l in open(p)] if os.path.exists(p) else []

def where(d, tag, name):
    f = os.path.join(d, '%s_%s.json' % (tag, name))
    if not os.path.exists(f): return None
    j = json.load(open(f)); o = j.get('origin'); W = j['screen'][0]
    return None if not o else (o[0] + o[2] / 2.0) / W

def grade(d):
    tag = os.path.basename(os.path.normpath(d)); out = []; L = lines(d)
    if not L: return ['INFRA no focuspos.txt'], 'INFRA'
    parsed = [dict(re.findall(r'(\w+)=(\S+)', l), cmd=l.split()[1]) for l in L if l.startswith('focuspos ')]
    r1 = {p.get('r1') for p in parsed}
    out.append(('PASS' if len(r1) == 1 else 'FAIL') + ' R1 prefs unchanged (%s)' % ','.join(sorted(r1)))
    restart = [p for p in parsed if p['cmd'] == 'LOG' and p.get('layout') == 'CLASSIC' and p.get('preview') == 'false']
    if not restart: out.append('INFRA no LOG line after the restart')
    else: out.append(('PASS' if restart[-1].get('choice', '').startswith('1/') else 'FAIL') + ' saved choice back after the restart (%s)' % restart[-1].get('choice'))
    for lay, n in (('CLASSIC', 2), ('TILES', 4)):
        ps = [p for p in parsed if p.get('layout') == lay and p.get('preview') == 'true']
        direct = [p for p in ps if p['cmd'].isdigit()]
        bad = [p for p in direct if int(p['cmd']) < n and not p['choice'].startswith(p['cmd'] + '/')]
        out.append(('FAIL' if bad or not direct else 'PASS') + ' %s direct choices taken (%d)' % (lay, len(direct)))
        nx = [int(p['choice'].split('/')[0]) for p in ps if p['cmd'] == 'NEXT']
        ok = len(nx) >= n and all((b - a) % n == 1 for a, b in zip(nx, nx[1:])) and set(nx) == set(range(n))
        out.append(('PASS' if ok else 'FAIL') + ' %s a press steps round every choice %s' % (lay, nx))
        out.append(('PASS' if all(p.get('fit') == 'Show' for p in ps) else 'FAIL') + ' %s tile shown' % lay)
        want = {'CLASSIC': {0: 'L', 1: 'M'}, 'TILES': {0: None, 1: 'L', 2: 'M', 3: 'R'}}[lay]
        for c, w in want.items():
            xs = [where(d, tag, 'FP_%s_c%d_%s' % (lay, c, s)) for s in 'abc']
            if None in xs: out.append('INFRA %s choice %d: a dump missing' % (lay, c)); continue
            if w is None: out.append('PASS %s choice %d (Edges) x=%s - free by design' % (lay, c, ['%.2f' % x for x in xs])); continue
            band = {'L': (0.0, 0.42), 'M': (0.38, 0.62), 'R': (0.58, 1.0)}[w]
            moved = [x for x in xs if not band[0] <= x <= band[1]]
            out.append(('FAIL' if moved else 'PASS') + ' %s choice %d (%s) x=%s' % (lay, c, w, ['%.2f' % x for x in xs]))
    hid = [p for p in parsed if p.get('layout') not in ('CLASSIC', 'TILES')]
    if not hid: out.append('INFRA no other layout logged')
    else: out.append(('PASS' if all(p.get('fit') == 'Hide' for p in hid) else 'FAIL') + ' hidden on %s' % sorted({p['layout'] for p in hid}))
    v = 'FAIL' if any(o.startswith('FAIL') for o in out) else 'INFRA' if any(o.startswith('INFRA') for o in out) else 'PASS'
    return out, v

if __name__ == '__main__':
    o, v = grade(sys.argv[1]); print('\n'.join(o)); print('VERDICT', v)
    sys.exit(0 if v == 'PASS' else 1)

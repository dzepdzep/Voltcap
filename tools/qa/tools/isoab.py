#!/usr/bin/env python3
"""682ay (Arcade B, experimental): grade a `p2review.sh isoab` run - the same build, legacy / new by the QA-only switch, interleaved
twice per layout, 24 home moves each on the All category.

Per half: frames, slow-UI-thread frames, p50 / p90 / p95 / p99 frame time (gfxinfo delta) and the home's own rebuilds during the
walk (QaTrace "recomp ... Screen=N"). A half counts only when every focus line in it is on All (null|null) and the category had
at least 20 games - else INFRA (the walk was not on the category asked for).

Verdict per layout: GAIN when new is better than legacy in BOTH pairs on rebuilds and on p95 or slow UI frames, and never
worse by more than one histogram bucket on p99; NO GAIN otherwise. The numbers are printed either way - noise is judged from
the two pairs, never from one.
Usage: isoab.py RUN_DIR      exit 0 = graded (GAIN or NO GAIN), 2 = INFRA."""
import os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gfxdelta


def half(d, lay, var, i):
    n = 'IS_%s_%s_%d' % (lay, var, i)
    try:
        b = gfxdelta.parse(open(os.path.join(d, n + '_P_nav_gfx0.txt'), errors='ignore').read())
        a = gfxdelta.parse(open(os.path.join(d, n + '_P_nav_gfx.txt'), errors='ignore').read())
        rec = open(os.path.join(d, n + '_recomp.txt'), errors='ignore').read()
    except OSError:
        return None
    if a is None:
        return None
    w = gfxdelta.delta(b, a)
    foc = re.findall(r' focus \d+ (\S+) ', rec)
    screen = sum(int(x) for x in re.findall(r' recomp \d+ms.*?\bScreen=(\d+)', rec))
    return {'frames': w['frames'], 'slow': w.get('cnt', {}).get('Slow UI thread'),
            'p50': gfxdelta.percentile(w['hist'], 50), 'p90': gfxdelta.percentile(w['hist'], 90),
            'p95': gfxdelta.percentile(w['hist'], 95), 'p99': gfxdelta.percentile(w['hist'], 99),
            'screen': screen, 'onAll': bool(foc) and all(f == 'null|null' for f in foc)}


def grade(d):
    lays = sorted({m.group(1) for f in os.listdir(d) for m in [re.match(r'IS_(\w+?)_cat\.txt$', f)] if m})
    out, verdicts = [], {}
    for lay in lays:
        cat = open(os.path.join(d, 'IS_%s_cat.txt' % lay), errors='ignore').read()
        g = re.findall(r'category ALL games=(\d+)', cat)
        games = int(g[-1]) if g else 0
        hs = {(v, i): half(d, lay, v, i) for v in ('legacy', 'new') for i in (1, 2)}
        bad = [k for k, h in hs.items() if h is None or not h['onAll']]
        if games < 20 or bad:
            out.append('INFRA %s: All games=%d, halves not on All / missing: %s' % (lay, games, bad)); verdicts[lay] = 'INFRA'; continue
        for (v, i), h in sorted(hs.items()):
            out.append('  %s %-6s %d: frames=%d slowUI=%s p50=%s p90=%s p95=%s p99=%s rebuilds=%d'
                       % (lay, v, i, h['frames'], h['slow'], h['p50'], h['p90'], h['p95'], h['p99'], h['screen']))
        pairs = [(hs[('legacy', i)], hs[('new', i)]) for i in (1, 2)]
        fewer = all(n['screen'] < l['screen'] for l, n in pairs)
        faster = all((n['p95'] or 0) < (l['p95'] or 0) or (n['slow'] or 0) < (l['slow'] or 0) for l, n in pairs)
        tail_ok = all((n['p99'] or 0) <= (l['p99'] or 0) + 4 for l, n in pairs)
        v = 'GAIN' if fewer and faster and tail_ok else 'NO GAIN'
        verdicts[lay] = v
        out.append('%s %s (All %d games): rebuilds fewer in both pairs=%s, p95 or slow UI better in both=%s, p99 not worse=%s'
                   % (v, lay, games, fewer, faster, tail_ok))
    kb, ka = os.path.join(d, 'IS_keep_before.txt'), os.path.join(d, 'IS_keep_after.txt')
    if os.path.exists(kb) and os.path.exists(ka):
        key = lambda f: (re.findall(r' focus \d+ \S+ (\S.*)$', open(f, errors='ignore').read(), re.M) or [None])[-1]
        b, a = key(kb), key(ka)
        ok = b is not None and a == b
        out.append('%s reopens on the game it was on after a QA app restart (before %s / after %s)' % ('PASS' if ok else 'FAIL', b, a))
        verdicts['keep'] = 'PASS' if ok else 'FAIL'
    return out, verdicts


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    out, v = grade(sys.argv[1])
    print('\n'.join(out) or 'INFRA no isoab halves')
    sys.exit(2 if not v or 'INFRA' in v.values() else 1 if v.get('keep') == 'FAIL' else 0)

#!/usr/bin/env python3
"""682av (owner 25 Nov - hardware QA of 724a Recomps / 725a START multi-category): grades a `p2review.sh appcat` run folder.

PASS only when, on the app put in the QA app's own library: Recomps is not offered before the mark and is after it; the app
is listed in Recomps, Android games and Nintendo 64 at once and only once in All; the same record (key) throughout; the
choices are back after the QA app restarts; the touch tap on the Recomps row takes the mark off and puts it back; taking the
choices off one by one leaves the app in the library (same record) and empties each category; and the app's Play recorded
one launch of that record. Missing evidence is INFRA, never PASS.
"""
import os, re, sys

def parse(d):
    p = os.path.join(d, 'appcat.txt')
    if not os.path.exists(p): return [], []
    raw = [re.sub(r'^.*VoltcapQA: ', '', l.strip()) for l in open(p)]
    rows = []
    for l in raw:
        if not l.startswith('appcat '): continue
        r = dict(re.findall(r'(\w+)=(\{[^}]*\}|\[[^\]]*\]|\S+)', l)); r['cmd'] = l.split()[1]; rows.append(r)
    return rows, [l for l in raw if 'LAUNCH blocked' in l]

def touch(d):
    """The tap on the Recomps row: its tick box (the row's left end) changes on the first tap and is back on the second."""
    log = open(os.path.join(d, 'log.txt')).read() if os.path.exists(os.path.join(d, 'log.txt')) else ''
    m = re.search(r'touch on the Recomps row at (\d+),(\d+)', log)
    if not m: return 'INFRA touch not run (%s)' % ('NOT TESTED' if 'touch NOT TESTED' in log else 'no line')
    try:
        from PIL import Image, ImageChops
    except ImportError:
        return 'INFRA touch: no PIL to compare the shots'
    y = int(m.group(2)); before = None
    for i in range(1, 9):
        if os.path.exists(os.path.join(d, 'AC_cats_tap1.png')) and os.path.exists(os.path.join(d, 'AC_cats_d%d.png' % i)): before = 'AC_cats_d%d.png' % i
        if before and re.search(r'AC_cats_d%d' % i, log or '') is None: pass
    ims = {}
    for n in ('AC_cats_tap1.png', 'AC_cats_tap2.png'):
        if not os.path.exists(os.path.join(d, n)): return 'INFRA touch: %s missing' % n
        ims[n] = Image.open(os.path.join(d, n)).convert('L')
    w = ims['AC_cats_tap1.png'].size[0]
    # the tick box sits at the row's left end: a strip across the left third of the screen at the row's height
    box = (0, max(0, y - 24), w // 3, y + 24)
    diff = ImageChops.difference(ims['AC_cats_tap1.png'].crop(box), ims['AC_cats_tap2.png'].crop(box))
    changed = sum(1 for v in diff.getdata() if v > 40)
    return ('PASS' if changed > 30 else 'FAIL') + ' touch on the Recomps row: the tick differs between the two taps (%d px)' % changed

def has(r, cat): return cat in r.get('listed', '')

def grade(d):
    rows, launches = parse(d); out = []
    if not rows: return ['INFRA no appcat lines'], 'INFRA'
    # ucoll / collnew lines are about the collection or its ROM game, not the app under test
    app = [r for r in rows if not r['cmd'].startswith(('ucoll', 'collnew'))]
    keys = {r.get('key') for r in app if r.get('found') == 'true'}
    out.append(('PASS' if len(keys) == 1 and all(r.get('found') == 'true' for r in app) else 'FAIL') + ' the same record throughout (%s)' % keys)
    base = rows[0]
    out.append(('PASS' if base.get('hasRecomps') == 'false' else 'FAIL') + ' no Recomps before the mark (%s)' % base.get('hasRecomps'))
    def first(prefix): return next((r for r in rows if r['cmd'].startswith(prefix)), None)
    rc = first('recomp:') ; out.append('INFRA no recomp on' if not rc else ('PASS' if rc.get('hasRecomps') == 'true' and has(rc, 'Recomps') else 'FAIL') + ' Recomps appears when marked')
    cn = first('console:')
    if not cn: out.append('INFRA no console on')
    else:
        ok = has(cn, 'Recomps') and has(cn, 'N64') and cn.get('android') == 'true' and cn.get('inAll') == '1' and cn.get('other') == 'false'
        out.append(('PASS' if ok else 'FAIL') + ' Recomps + Android games + N64 at once, once in All, not in Other apps (listed=%s inAll=%s other=%s)' % (cn.get('listed'), cn.get('inAll'), cn.get('other')))
    logs = [r for r in rows if r['cmd'].startswith('log:')]
    out.append(touch(d))
    rest = [r for r in logs if r is not logs[0]]
    after = rest[-1] if rest else None   # the log after the restart is the last plain log
    if not after: out.append('INFRA no log after the restart')
    else: out.append(('PASS' if after.get('recomp') == 'true' and 'N64' in after.get('consoles', '') and after.get('android') == 'true' else 'FAIL') + ' kept across the QA app restart (recomp=%s consoles=%s android=%s)' % (after.get('recomp'), after.get('consoles'), after.get('android')))
    off = [r for r in rows if r['cmd'].endswith(':off') or r['cmd'].endswith(':AUTO')]
    if len(off) < 3: out.append('INFRA the take-off steps missing (%d)' % len(off))
    else:
        c, r, k = off[-3], off[-2], off[-1]
        out.append(('PASS' if not has(c, 'N64') and has(c, 'Recomps') else 'FAIL') + ' console off: out of N64, still in Recomps (%s)' % c.get('listed'))
        out.append(('PASS' if r.get('hasRecomps') == 'false' and not has(r, 'Recomps') else 'FAIL') + ' recomp off: Recomps gone (empty) (%s)' % r.get('hasRecomps'))
        out.append(('PASS' if k.get('found') == 'true' and k.get('inAll') == '1' else 'FAIL') + ' all choices off: the app still there, once in All (found=%s inAll=%s)' % (k.get('found'), k.get('inAll')))
    # (682aw) collections: the app put in an existing collection of your own (made with a ROM game), with its other choices kept;
    # taken out again - the collection and its ROM game stay
    cols = [r for r in rows if r['cmd'].startswith('coll:')]
    if len(cols) < 2: out.append('INFRA collections: %d toggle lines' % len(cols))
    else:
        a, b = cols[0], cols[-1]
        ok_in = 'QA Collection' in a.get('colls', '') and has(a, 'Recomps') and has(a, 'N64') and a.get('android') == 'true'
        ok_out = 'QA Collection' not in b.get('colls', '') and "'QA Collection': 1" in b.get('collSizes', '').replace('=', ': ').replace('QA Collection', "'QA Collection'") or ('QA Collection=1' in b.get('collSizes', ''))
        out.append(('PASS' if ok_in else 'FAIL') + ' in an existing collection too, with Recomps + Android games + N64 kept (colls=%s)' % a.get('colls'))
        out.append(('PASS' if 'QA Collection' not in b.get('colls', '') and 'QA Collection=1' in b.get('collSizes', '') else 'FAIL') + ' taken out of it: the collection and its ROM game stay (colls=%s sizes=%s)' % (b.get('colls'), b.get('collSizes')))
    log = open(os.path.join(d, 'log.txt')).read() if os.path.exists(os.path.join(d, 'log.txt')) else ''
    out.append(('PASS' if 'Collections... opened' in log else 'INFRA') + ' the Categories window\'s Collections... opens its picker (shot AC_collpicker)')
    out.append(('PASS' if len(launches) == 1 else 'INFRA' if not launches else 'FAIL') + ' its Play recorded %d launch(es) %s' % (len(launches), [l.split('blocked')[-1].strip()[:60] for l in launches]))
    v = 'FAIL' if any(o.startswith('FAIL') for o in out) else 'INFRA' if any(o.startswith('INFRA') for o in out) else 'PASS'
    return out, v

if __name__ == '__main__':
    o, v = grade(sys.argv[1]); print('\n'.join(o)); print('VERDICT', v)
    sys.exit(0 if v == 'PASS' else 1)

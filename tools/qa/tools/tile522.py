#!/usr/bin/env python3
"""Batch 522b: Tile home independence from tile522b.sh dumps (QaClip). For each dock (MIDDLE / TOP / BOTTOM) compares the
game row (the focused game's title) and the category chips ("All") between steps where only the round buttons changed;
any move over 2 px FAILs. Also checks every placed text / control is inside the screen.
usage: python3 tile522.py RUN_DIR   (exit 1 on FAIL)"""
import json, os, sys

def bar_y(png):
    """The round buttons' row: the screen row crossing the most separate icon-orange runs (13 buttons = 13+ runs; a focus
    frame or a chip is one long run). None when no row crosses 8+ runs. (The buttons have no labels in the dump.)"""
    from PIL import Image
    im = Image.open(png).convert('RGB'); W, H = im.size; px = im.load()
    best, by = 0, None
    for y in range(80, H, 2):
        runs, inside = 0, False
        for x in range(0, W):
            r, g, b = px[x, y]
            o = r > 170 and 90 < g < 200 and b < 120
            if o and not inside: runs += 1
            inside = o
        if runs > best: best, by = runs, y
    return by if best >= 8 else None


def boxes(path):
    d = json.load(open(path))
    sw, sh = d.get('screen', [0, 0])
    out, off = {}, []
    for w in d['windows']:
        for n in w['nodes']:
            if n['label'] in ('All', 'Super Mario World') and n['label'] not in out and n['shown'][2] > 0:
                out[n['label']] = n['shown']
        for t in w.get('texts', []):
            s = t['shown']
            if t.get('placed', True) and s[2] > 0 and (s[0] < -1 or s[1] < -1 or s[0] + s[2] > sw + 1 or s[1] + s[3] > max(sh, 1080) + 1):
                off.append((t['text'][:30], s))
    return out, off

def main():
    run = sys.argv[1]; fails = 0
    # (same row / chip place expected) pairs: the round buttons moved / reset, nothing else.
    pairs = [('base', 'buttons_moved'), ('both_moved', 'edit_open'), ('both_moved', 'edit_reset_buttons'),
             ('both_moved', 'saved_buttons_reset'), ('saved_row_reset', 'base')]
    for d in ('MIDDLE', 'TOP', 'BOTTOM'):
        for a, b in pairs:
            pa, pb = os.path.join(run, '%s_%s.json' % (d, a)), os.path.join(run, '%s_%s.json' % (d, b))
            if not (os.path.exists(pa) and os.path.exists(pb)):
                print('INFRA %s %s/%s missing' % (d, a, b)); fails += 1; continue
            ba, oa = boxes(pa); bb, ob = boxes(pb)
            for k in ('Super Mario World', 'All'):
                if k not in ba or k not in bb:
                    print('NOTE  %s %s: %r not on both (%s / %s)' % (d, a + '->' + b, k, k in ba, k in bb)); continue
                dy = bb[k][1] - ba[k][1]; dx = bb[k][0] - ba[k][0]
                v = 'PASS' if abs(dy) <= 2 and abs(dx) <= 2 else 'FAIL'
                fails += v == 'FAIL'
                print('%s  %-6s %-40s %-18s dx=%d dy=%d' % (v, d, a + ' -> ' + b, k, dx, dy))
        # the round buttons stay where they were put while the row is moved / reset / the layout is reset
        for a, b in [('buttons_moved', 'both_moved'), ('buttons_moved', 'saved_row_reset'), ('buttons_moved', 'saved_layout_reset')]:
            pa, pb = os.path.join(run, '%s_%s.png' % (d, a)), os.path.join(run, '%s_%s.png' % (d, b))
            if not (os.path.exists(pa) and os.path.exists(pb)):
                continue
            ya, yb = bar_y(pa), bar_y(pb)
            v = 'PASS' if ya is not None and yb is not None and abs(ya - yb) <= 4 else 'FAIL'
            fails += v == 'FAIL'
            print('%s  %-6s %-40s %-18s y=%s -> %s' % (v, d, a + ' -> ' + b, 'round buttons', ya, yb))
        for f in sorted(os.listdir(run)):
            if f.startswith(d) and f.endswith('.json'):
                _, off = boxes(os.path.join(run, f))
                for t, s in off:
                    print('FAIL  %-6s %s off screen: %r %s' % (d, f, t, s)); fails += 1
    print('tile522: %s' % ('FAIL (%d)' % fails if fails else 'PASS'))
    sys.exit(1 if fails else 0)

if __name__ == '__main__':
    main()

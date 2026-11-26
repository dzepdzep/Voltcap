#!/usr/bin/env python3
"""682ba (owner 25 Nov 12:48): the v0.7.0 visual preview - an HTML gallery + ZIP of every layout's home, Info, Details,
Achievements and Options pages, the page-walk and home-navigation recordings, on KONKR and RP Classic at x1.0 and x1.3, with
the new features, before / after comparisons, open issues and the statuses kept apart (implemented / hardware QA / owner visual
approval - never merged into one).

Input: capture folders made by `p2review.sh gallery TAG SCALE "LAYOUTS"` (one per handheld x scale), an extras folder (feature
shots, before / after pairs) and a JSON of notes (open issues, per-layout statuses).
Usage: gallery070.py OUT_DIR --cap KONKR:x1.0:DIR --cap KONKR:x1.3:DIR --cap "RP Classic:x1.0:DIR" ... --names names.txt
       [--notes notes.json] [--extras DIR] [--zip]
Images become JPEG (q82, max 1280 px wide) + 360 px thumbnails; recordings H.264 (crf 28, veryfast, 720 px wide), segments
joined. Missing captures are shown as NOT CAPTURED with the run log's reason - never silently left out.
"""
import argparse, html, json, os, re, shutil, subprocess, sys, zipfile

PAGES = [('home', 'Home'), ('info', 'Info (window)'), ('details', 'Details'), ('trophies', 'Achievements'), ('options', 'Options')]


def jpg(src, dst, width):
    from PIL import Image
    if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        return
    im = Image.open(src).convert('RGB')
    if im.width > width:
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst, 'JPEG', quality=82, optimize=True)


def clip(srcs, dst):
    """Join the recorder's segments (NAME.mp4, NAME_p2.mp4 ...) into one small H.264 file. False when none decodes."""
    srcs = [s for s in srcs if os.path.exists(s) and os.path.getsize(s) > 2000]
    if not srcs:
        return False
    if os.path.exists(dst) and os.path.getsize(dst) > 1000 and os.path.getmtime(dst) >= max(os.path.getmtime(s) for s in srcs):
        return True
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    import tempfile
    fd, lst = tempfile.mkstemp(suffix='.txt')  # (outside the gallery folder: nothing to delete there)
    os.close(fd)
    with open(lst, 'w') as f:
        for s in srcs:
            f.write("file '%s'\n" % os.path.abspath(s).replace("'", "'\\''"))
    r = subprocess.run(['ffmpeg', '-y', '-v', 'error', '-f', 'concat', '-safe', '0', '-i', lst, '-vf', 'scale=720:-2',
                        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '28', '-pix_fmt', 'yuv420p', '-an', '-movflags', '+faststart', dst],
                       capture_output=True, text=True)
    try:
        os.remove(lst)
    except OSError:
        pass
    return r.returncode == 0 and os.path.exists(dst) and os.path.getsize(dst) > 1000


def segments(d, base):
    out = [os.path.join(d, base + '.mp4')]
    k = 2
    while os.path.exists(os.path.join(d, '%s_p%d.mp4' % (base, k))):
        out.append(os.path.join(d, '%s_p%d.mp4' % (base, k))); k += 1
    return out


def reasons(d):
    """Per layout, the run log's lines that say why something is missing (precondition, abort, NOT TESTED)."""
    out = {}
    try:
        for ln in open(os.path.join(d, 'log.txt'), errors='ignore'):
            m = re.match(r'\S+ ([A-Z_0-9]+): (.*)', ln.strip())
            if m and re.search(r'FAIL|ABORT|NOT|PRECONDITION|missing', m.group(2)):
                out.setdefault(m.group(1), []).append(m.group(2))
            elif re.search(r'ABORT|STOP', ln):
                out.setdefault('_run', []).append(ln.strip())
    except OSError:
        pass
    return out


CSS = """
:root{--bg:#0f1115;--card:#171a21;--ink:#e8eaed;--muted:#9aa0a6;--line:#2a2f3a;--ok:#4caf50;--warn:#f2b233;--bad:#ef5350;--acc:#7aa7ff}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{padding:20px 24px;border-bottom:1px solid var(--line);position:sticky;top:0;background:rgba(15,17,21,.96);z-index:5}
h1{margin:0 0 4px;font-size:22px}h2{margin:28px 0 10px;font-size:19px}h3{margin:18px 0 8px;font-size:16px}
.wrap{padding:0 24px 60px}.muted{color:var(--muted)}a{color:var(--acc)}
nav a{margin-right:14px;white-space:nowrap}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}
.tile{background:var(--card);border:1px solid var(--line);border-radius:10px;overflow:hidden;text-decoration:none;color:var(--ink)}
.tile img{width:100%;display:block;aspect-ratio:3/2;object-fit:cover;background:#000}.tile div{padding:8px 10px;font-size:14px}
.badges{display:flex;flex-wrap:wrap;gap:4px;margin-top:4px}
.b{font-size:11px;padding:1px 7px;border-radius:9px;border:1px solid var(--line);white-space:nowrap}
.b.ok{border-color:var(--ok);color:var(--ok)}.b.warn{border-color:var(--warn);color:var(--warn)}.b.bad{border-color:var(--bad);color:var(--bad)}
table{border-collapse:collapse;width:100%;margin:8px 0}td,th{border:1px solid var(--line);padding:6px 8px;vertical-align:top;text-align:left}
th{background:#1d212a;font-weight:600}
.lay,h2{scroll-margin-top:120px}.lay{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px 16px;margin:18px 0}
.mat{display:grid;grid-template-columns:110px repeat(4,1fr);gap:6px;align-items:start}
.mat .h{font-size:12px;color:var(--muted);text-align:center}.mat .r{font-size:13px;color:var(--muted);padding-top:4px}
.mat img,.mat video{width:100%;border-radius:6px;border:1px solid var(--line);background:#000;display:block}
.na{font-size:12px;color:var(--warn);border:1px dashed var(--warn);border-radius:6px;padding:8px;min-height:40px}
.pair{display:grid;grid-template-columns:1fr 1fr;gap:8px}.pair img{width:100%;border-radius:6px;border:1px solid var(--line)}
.lb{position:fixed;inset:0;background:rgba(0,0,0,.92);display:none;align-items:center;justify-content:center;z-index:9}
.lb img{max-width:96vw;max-height:94vh}.lb.on{display:flex}
@media (max-width:760px){.mat{grid-template-columns:70px repeat(2,1fr)}.mat .c3,.mat .c4{display:none}}
"""

JS = """
document.addEventListener('click',e=>{const i=e.target.closest('img.z');if(i){const lb=document.getElementById('lb');lb.querySelector('img').src=i.dataset.full||i.src;lb.classList.add('on');}});
document.getElementById('lb').addEventListener('click',()=>document.getElementById('lb').classList.remove('on'));
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('out'); ap.add_argument('--cap', action='append', default=[]); ap.add_argument('--names', required=True)
    ap.add_argument('--notes'); ap.add_argument('--extras'); ap.add_argument('--zip', action='store_true')
    a = ap.parse_args()
    names = [ln.strip().split('|', 1) for ln in open(a.names) if '|' in ln]
    caps = []
    for c in a.cap:
        dev, sc, d = c.split(':', 2); caps.append((dev, sc, d))
    notes = json.load(open(a.notes)) if a.notes else {}
    out = a.out; media = os.path.join(out, 'media'); os.makedirs(media, exist_ok=True)
    why = {i: reasons(d) for i, (_, _, d) in enumerate(caps)}
    cover = {}; counts = {'shots': 0, 'missing': 0, 'clips': 0}
    body = []
    per_lay_status = notes.get('layouts', {})
    for key, label in names:
        cells = []
        for pg, pname in PAGES:
            row = ['<div class="r">%s</div>' % pname]
            for i, (dev, sc, d) in enumerate(caps):
                src = os.path.join(d, '%s_%s.png' % (key, pg))
                if os.path.exists(src):
                    rel = 'media/%s/%s_%s_%s' % (key, re.sub(r'\W+', '', dev + sc), key, pg)
                    jpg(src, os.path.join(out, rel + '.jpg'), 1280); jpg(src, os.path.join(out, rel + '_t.jpg'), 360)
                    if pg == 'home' and key not in cover:
                        cover[key] = rel + '_t.jpg'
                    row.append('<img class="z c%d" loading="lazy" src="%s_t.jpg" data-full="%s.jpg" alt="%s %s %s %s">' % (i + 1, rel, rel, html.escape(label), pname, dev, sc))
                    counts['shots'] += 1
                else:
                    r = '; '.join(why[i].get(key, [])[:2]) or 'not captured'
                    row.append('<div class="na c%d">NOT CAPTURED - %s</div>' % (i + 1, html.escape(r)))
                    counts['missing'] += 1
            cells.append(''.join(row))
        for kind, kname in (('walk', 'Page walk (R1 x4)'), ('nav', 'Home navigation')):
            row = ['<div class="r">%s</div>' % kname]; anyv = False
            for i, (dev, sc, d) in enumerate(caps):
                rel = 'media/%s/%s_%s_%s.mp4' % (key, re.sub(r'\W+', '', dev + sc), key, kind)
                if clip(segments(d, '%s_%s' % (key, kind)), os.path.join(out, rel)):
                    row.append('<video class="c%d" src="%s" controls muted loop playsinline preload="none"></video>' % (i + 1, rel)); anyv = True
                    counts['clips'] += 1
                else:
                    row.append('<div class="na c%d">%s</div>' % (i + 1, 'not recorded' if kind == 'nav' else 'NOT RECORDED'))
            if anyv or kind == 'walk':
                cells.append(''.join(row))
        st = per_lay_status.get(key, {})
        badges = ''.join('<span class="b %s">%s</span>' % (cls, html.escape(t)) for cls, t in [
            ('ok' if st.get('impl', 'yes') == 'yes' else 'bad', 'Implemented: ' + st.get('impl', 'yes')),
            ('ok' if st.get('hw', '').startswith('PASS') else 'warn', 'Hardware QA: ' + st.get('hw', 'NOT TESTED')),
            ('bad', 'Owner visual approval: ' + st.get('approved', 'NOT APPROVED'))])
        head = ''.join('<div class="h c%d">%s %s</div>' % (i + 1, html.escape(dev), sc) for i, (dev, sc, _) in enumerate(caps))
        body.append('<section class="lay" id="%s"><h3>%s <span class="muted">(%s)</span></h3><div class="badges">%s</div>%s'
                    '<div class="mat"><div></div>%s%s</div></section>' % (
                        key, html.escape(label), key, badges,
                        ('<p class="muted">%s</p>' % html.escape(st['note'])) if st.get('note') else '',
                        head, ''.join(cells)))
    tiles = ''.join('<a class="tile" href="#%s"><img loading="lazy" src="%s" alt=""><div>%s<div class="badges"><span class="b bad">not approved</span></div></div></a>'
                    % (k, cover.get(k, ''), html.escape(l)) for k, l in names)
    # features, before / after, open issues - from the notes and the extras folder
    extra_html = []
    ex = a.extras
    for sec in notes.get('sections', []):
        parts = ['<h2 id="%s">%s</h2>' % (sec['id'], html.escape(sec['title']))]
        if sec.get('text'):
            parts.append('<p>%s</p>' % html.escape(sec['text']))
        for it in sec.get('items', []):
            imgs = []
            for f in it.get('images', []):
                src = os.path.join(ex, f) if ex else f
                if os.path.exists(src):
                    rel = 'media/extras/' + re.sub(r'[^\w.-]+', '_', f).rsplit('.', 1)[0]
                    jpg(src, os.path.join(out, rel + '.jpg'), 1280)
                    imgs.append('<figure style="margin:0"><img class="z" src="%s.jpg"><figcaption class="muted">%s</figcaption></figure>'
                                % (rel, html.escape(os.path.basename(f))))
                else:
                    imgs.append('<div class="na">missing: %s</div>' % html.escape(f))
            parts.append('<div class="lay"><h3>%s</h3><p>%s</p><div class="pair">%s</div></div>' % (
                html.escape(it.get('title', '')), html.escape(it.get('text', '')), ''.join(imgs)))
        if sec.get('table'):
            t = sec['table']
            parts.append('<table><tr>%s</tr>%s</table>' % (''.join('<th>%s</th>' % html.escape(h) for h in t[0]),
                         ''.join('<tr>%s</tr>' % ''.join('<td>%s</td>' % html.escape(str(c)) for c in r) for r in t[1:])))
        extra_html.append(''.join(parts))
    summary = notes.get('summary', '')
    page = """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>VoltCap v0.7.0 visual preview</title><style>%s</style></head><body>
<header><h1>VoltCap v0.7.0 visual preview</h1><div class="muted">%s</div>
<nav><a href="#layouts">53 layouts</a>%s<a href="#statuses">Statuses</a></nav></header><div class="wrap">
<h2 id="statuses">How to read this</h2><p><b>Implemented</b>, <b>hardware QA</b> and <b>owner visual approval</b> are separate.
A screenshot here is a visual capture on the handheld (QA app, synthetic games) - it is not a functional hardware test and it is
not your approval. Every layout is <span class="b bad">NOT APPROVED</span> until you approve it.</p>
<p class="muted">Captures: %d screenshots, %d recordings, %d not captured (shown as NOT CAPTURED with the reason).</p>
%s
<h2 id="layouts">All 53 layouts</h2><p class="muted">Each layout: Home, Info, Details, Achievements, Options and the page walk on KONKR x1.0 / x1.3 and RP Classic x1.0 / x1.3 (narrow screens: KONKR only); home navigation at x1.0. Tap a picture for full size.</p><div class="grid">%s</div>%s</div>
<div class="lb" id="lb"><img alt=""></div><script>%s</script></body></html>""" % (
        CSS, html.escape(summary), ''.join('<a href="#%s">%s</a>' % (s['id'], html.escape(s['title'])) for s in notes.get('sections', [])),
        counts['shots'], counts['clips'], counts['missing'], ''.join(extra_html), tiles, ''.join(body), JS)
    open(os.path.join(out, 'index.html'), 'w').write(page)
    print('gallery: %d screenshots, %d recordings, %d not captured -> %s' % (counts['shots'], counts['clips'], counts['missing'], out))
    if a.zip:
        z = out.rstrip('/') + '.zip'
        with zipfile.ZipFile(z, 'w', zipfile.ZIP_DEFLATED) as zf:
            for root, _, files in os.walk(out):
                for f in files:
                    p = os.path.join(root, f)
                    # (pictures and clips are compressed already - stored as they are, the page deflated)
                    zf.write(p, os.path.relpath(p, os.path.dirname(out.rstrip('/')) or '.'),
                             compress_type=zipfile.ZIP_STORED if f.endswith(('.jpg', '.mp4')) else zipfile.ZIP_DEFLATED)
        print('zip:', z, os.path.getsize(z) // 1024 // 1024, 'MB')


if __name__ == '__main__':
    main()

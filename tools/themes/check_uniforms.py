#!/usr/bin/env python3
"""Checks ThemeShaders.uniforms against what the GLSL compiler keeps (headless WebGL): run after gen.py.
Any "ours-but-inactive" uniform would crash Android 13 when set. uArt/uCover/uLabel/uHistory/uShot are pictures (ThemeShaders.children)."""
import os, re, json, sys
from playwright.sync_api import sync_playwright
HERE=os.path.dirname(os.path.abspath(__file__))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE,'..','..','src_copy')
kt=open(os.path.join(src,'ui','ThemeShaders.kt'), encoding='utf-8').read()
blk=kt[kt.index('fun uniforms'):]; blk=blk[:blk.index('\n    }\n')]
ours={}
for names,rest in re.findall(r'^\s+(ThemeMotif\.\w+(?:, ThemeMotif\.\w+)*) -> (.*)',blk,flags=re.M):
    for m in re.findall(r'ThemeMotif\.(\w+)',names): ours[m]=set(re.findall(r'"(\w+)"',rest))
with sync_playwright() as p:
    b=p.chromium.launch(executable_path=os.environ.get("CHROME") or None, args=["--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
    from pathlib import Path
    pg=b.new_page(); pg.goto(Path(HERE, 'preview.html').as_uri())
    res=pg.evaluate("""() => { const out={}; for (const m of Object.keys(DATA.scenes)) { try { const p=program(m); const n=gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS); const a=[]; for(let i=0;i<n;i++){a.push(gl.getActiveUniform(p,i).name.replace('[0]',''));} out[m]=a; } catch(e){ out[m]='ERR '+e.message; } } return out; }""")
    b.close()
failed = False
for m,a in sorted(res.items()):
    if isinstance(a,str): print(m,a[:100]); failed = True; continue
    act=set(x.replace('Tex','') for x in a)
    o=ours.get(m,set())
    extra=o-act; missing=act-o-{'uArt','uCover','uLabel','uHistory','uShot'}
    if extra or missing: print(m,'ours-but-inactive',sorted(extra),'active-not-ours',sorted(missing)); failed = True
print('checked',len(res))
sys.exit(1 if failed else 0)

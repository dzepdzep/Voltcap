"""How much each theme's scene changes when its colours change (run gen.py first).
   python tools/themes/color_check.py  -> one line per theme (mean change per channel, 0-255), then the motifs
   that change under 4.0 for every theme using them: paste those into AppTheme.OWN_COLOR_MOTIFS (ShelfColors.kt)."""
import json, sys
from playwright.sync_api import sync_playwright
import os
HERE=os.path.join(os.path.dirname(os.path.abspath(__file__)), 'preview.html')
with sync_playwright() as p:
    b=p.chromium.launch(args=["--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
    pg=b.new_page(viewport={"width":200,"height":120})
    pg.goto('file://'+HERE+'?theme=VOLT&t=1&w=64&h=36')
    pg.wait_for_function("document.title==='done' || document.getElementById('err').textContent.length>0", timeout=60000)
    res=pg.evaluate("""()=>{
      const out={}; const W=192,H=108;
      const read=()=>{const px=new Uint8Array(W*H*4); gl.readPixels(0,0,W,H,gl.RGBA,gl.UNSIGNED_BYTE,px); return px;};
      for (const th of DATA.themes) {
        if (DATA.reactive.includes(th.motif) || th.motif.startsWith('SHOWCASE')) continue;
        let tot=0;
        for (const t of [3, 8]) {
          draw(th,t,W,H); const a=read();
          const alt={...th, colors: th.colors.map((c,i)=> i==0 ? [c[2]*0.6+0.05, c[0]*0.5+0.1, c[1]*0.6+0.1] : [c[2], c[0], c[1]].map(v=>1-v*0.8))};
          draw(alt,t,W,H); const b2=read();
          let d=0; for(let i=0;i<a.length;i+=4){ d+=Math.abs(a[i]-b2[i])+Math.abs(a[i+1]-b2[i+1])+Math.abs(a[i+2]-b2[i+2]); }
          tot+=d/(W*H*3);
        }
        out[th.name]=[th.motif, tot/2];
      }
      return out;}""")
    b.close()
items=sorted(res.items(), key=lambda kv: kv[1][1])
for n,(m,v) in items: print(f"{v:6.1f} {n} {m}")
by = {}
for n, (m, v) in res.items(): by.setdefault(m, []).append(v)
print('mostly own colours (< 4.0):', ', '.join(sorted(m for m, vs in by.items() if max(vs) < 4.0)))

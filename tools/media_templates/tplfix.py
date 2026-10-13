# Update 169: the cartridge templates without the moulded / printed brand marks, the console's logo in their place.
import cv2, numpy as np
from PIL import Image
SRC='/tmp/claude-0/apk/dec/res/drawable-nodpi/'
LOG='/tmp/claude-0/r3d/logos68/logos/'
# name: (text rects to clean [(x0,y0,x1,y1)], logo, logo box (cx,cy,w,h), threshold, logo tint or None, logo alpha)
JOBS={
 'gb':          ([(60,54,318,92)], 'gb', (188,72,210,26), 10, None, 0.85),
 'gbc_black':   ([(60,54,318,92)], 'gbc', (188,72,210,26), 10, None, 0.9),
 'gba':         ([(112,26,318,54)], 'gba', (215,40,190,22), 10, (232,234,238), 0.8),
 'gbc':         ([(66,60,294,104)], 'gbc', (180,82,190,30), 14, None, 0.9),
 'gamegear':    ([(130,342,266,392)], 'gamegear', (198,367,118,34), 8, None, 0.9),
 'neogeopocket':([(36,332,404,378)], 'ngpc', (220,355,170,30), -1, None, 0.95),
 'psp':         ([(132,165,292,250),(12,336,94,368),(336,326,404,380)], 'psp', (212,208,130,44), 20, None, 1.0),
 'psvita':      ([(56,56,236,98)], 'psvita', (148,78,150,28), 25, (255,255,255), 1.0),
 'wonderswan':  ([(116,8,304,30)], 'wonderswancolor', (210,19,150,18), -1, None, 0.95),
 'pokemonmini': ([(92,90,300,130)], 'pokemini', (196,110,160,26), 14, None, 0.95),
}
FLAT={'psp':[(346,336,396,370)]}
def clean(img, rects, thr):
    rgb=img[:,:,:3].copy(); a=img[:,:,3]
    for (x0,y0,x1,y1) in rects:
        roi=rgb[y0:y1,x0:x1]
        g=cv2.cvtColor(roi,cv2.COLOR_RGB2GRAY).astype(np.int16)
        bg=cv2.medianBlur(cv2.cvtColor(roi,cv2.COLOR_RGB2GRAY),21).astype(np.int16)
        # (thr < 0: a flat moulded surface — the whole patch, so the faintest relief goes too)
        m=(np.abs(g-bg)>thr).astype(np.uint8)*255 if thr>=0 else np.full(g.shape,255,np.uint8)
        m=cv2.dilate(m,np.ones((3,3),np.uint8),iterations=2)
        m[a[y0:y1,x0:x1]<20]=0
        full=np.zeros(a.shape,np.uint8); full[y0:y1,x0:x1]=m
        rgb=cv2.inpaint(rgb,full,6,cv2.INPAINT_TELEA)
    out=img.copy(); out[:,:,:3]=rgb; return out
def put_logo(im, name, box, tint, alpha):
    cx,cy,w,h=box
    lg=Image.open(LOG+name+'.webp').convert('RGBA')
    k=min(w/lg.width,h/lg.height); lg=lg.resize((max(1,int(lg.width*k)),max(1,int(lg.height*k))),Image.LANCZOS)
    if tint:
        arr=np.array(lg); arr[:,:,:3]=tint; lg=Image.fromarray(arr)
    if alpha<1:
        arr=np.array(lg); arr[:,:,3]=(arr[:,:,3]*alpha).astype(np.uint8); lg=Image.fromarray(arr)
    im.alpha_composite(lg,(int(cx-lg.width/2),int(cy-lg.height/2)))
    return im
for n,(rects,logo,box,thr,tint,al) in JOBS.items():
    src=np.array(Image.open(SRC+f'tpl_cart_{n}.webp').convert('RGBA'))
    out=clean(src,rects,thr)
    # (Small printed marks on a plain surface: filled with the surface's own colour, then smoothed by the inpaint.)
    for (x0,y0,x1,y1) in FLAT.get(n,[]):
        edge=np.concatenate([out[y0,x0:x1,:3],out[y1-1,x0:x1,:3],out[y0:y1,x0,:3],out[y0:y1,x1-1,:3]])
        out[y0:y1,x0:x1,:3]=np.median(edge,axis=0).astype(np.uint8)
    out=Image.fromarray(out)
    out=put_logo(out,logo,box,tint,al)
    out.save(f'tplfix/tpl_cart_{n}.webp',lossless=True,quality=100,method=6)
print('ok')

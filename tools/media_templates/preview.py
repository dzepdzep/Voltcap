import json, numpy as np, cv2, sys
from PIL import Image, ImageDraw, ImageFont
OUT='/home/claude/work/proj/res/drawable-nodpi/'
T={e['p']:e for e in json.load(open('table.json'))}
def cover(w=300,h=420):
    im=Image.new('RGB',(w,h)); d=ImageDraw.Draw(im)
    for y in range(h): d.line([(0,y),(w,y)],fill=(30+y*150//h,60,200-y*120//h))
    d.ellipse([w*0.2,h*0.3,w*0.8,h*0.7],fill=(250,200,40)); d.text((20,20),'GAME TITLE',fill=(255,255,255))
    return im.convert('RGBA')
def warp(dst,img,quad,W,H):
    src=np.float32([[0,0],[img.width,0],[img.width,img.height],[0,img.height]])
    q=np.float32([[x*W,y*H] for x,y in quad])
    Mx=cv2.getPerspectiveTransform(src,q)
    out=cv2.warpPerspective(np.array(img),Mx,(W,H),flags=cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT,borderValue=(0,0,0,0))
    dst.alpha_composite(Image.fromarray(out))
def box(p):
    e=T[p]['box']; ov=Image.open(OUT+f'tpl_box_{p.lower()}.webp').convert('RGBA'); W,H=ov.size
    canvas=Image.new('RGBA',(W,H),(0,0,0,0))
    cv=cover()
    fr=e.get('frame')
    if fr:
        f=Image.open(OUT+f'tpl_front_{p.lower()}.webp').convert('RGBA'); comp=Image.new('RGBA',f.size,(0,0,0,255))
        x0,y0,x1,y1=[int(v*s) for v,s in zip(fr['win'],[f.width,f.height,f.width,f.height])]
        comp.alpha_composite(cv.resize((x1-x0,y1-y0)),(x0,y0)); comp.alpha_composite(f); cv=comp
    try: sp=Image.open(OUT+f'tpl_spine_{p.lower()}.webp').convert('RGBA')
    except Exception: sp=Image.new('RGBA',(40,400),(40,40,50,255))
    if e['hspine']: sp=sp.rotate(90,expand=True)
    warp(canvas,sp,e['spine'],W,H); warp(canvas,cv,e['front'],W,H); canvas.alpha_composite(ov)
    return canvas
def cart(p,label=False):
    e=T[p]['label' if label else 'cart']; k='cartl' if label else 'cart'
    sh=Image.open(OUT+f'tpl_{k}_{p.lower()}.webp').convert('RGBA'); W,H=sh.size
    m=Image.open(OUT+f'tpl_{k}m_{p.lower()}.webp').convert('RGBA').resize((W,H)).split()[3]
    cv=cover(); x0,y0,x1,y1=[int(v*s) for v,s in zip(e['win'],[W,H,W,H])]
    lay=Image.new('RGBA',(W,H),(0,0,0,0)); lay.alpha_composite(cv.resize((x1-x0,y1-y0)),(x0,y0))
    a=np.minimum(np.array(lay.split()[3]),np.array(m)); lay.putalpha(Image.fromarray(a))
    lay.alpha_composite(sh); return lay
ps=sys.argv[1].split(','); mode=sys.argv[2]
ims=[]
for p in ps:
    try: ims.append(box(p) if mode=='box' else cart(p, mode=='label'))
    except Exception as ex: print(p,ex)
cell=260; sheet=Image.new('RGBA',(cell*8,cell*((len(ims)+7)//8)),(90,90,100,255))
for i,im in enumerate(ims):
    im.thumbnail((cell-8,cell-8)); sheet.alpha_composite(im,((i%8)*cell+4,(i//8)*cell+4))
sheet.convert('RGB').save(f'/tmp/tpl/prev_{mode}.jpg',quality=82)

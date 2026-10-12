import json, numpy as np, cv2, os
from PIL import Image
from mapping import M
B='/mnt/user-data/uploads/Templates/'
OUT='/home/claude/work/proj/res/drawable-nodpi/'
os.makedirs(OUT,exist_ok=True)
W=json.load(open('windows.json')); BOX=json.load(open('box_raw.json')); TEX=json.load(open('tex.json'))
def rid(p): return p.lower()
def save(im,name,maxdim,q=86):
    im=im.copy(); im.thumbnail((maxdim,maxdim),Image.LANCZOS)
    im.save(OUT+name+'.webp','WEBP',quality=q,method=4); return im.size
def maskimg(m,size,maxdim=160):
    a=Image.fromarray(m).resize(size,Image.BILINEAR)
    im=Image.new('RGBA',size,(255,255,255,0)); im.putalpha(a); return save(im,None,maxdim) if False else im
def quads(pts,w,h):
    P=np.array(pts,float); n=len(P)
    best=None
    for i in range(n):
        a,b,c,d=P[i],P[(i+1)%n],P[(i-1)%n],P[(i+2)%n]
        outer=np.linalg.norm(a-b); short=np.linalg.norm(a-c)+np.linalg.norm(b-d)
        if outer< 0.3*max(w,h): continue
        if best is None or short<best[0]: best=(short,i)
    i=best[1]
    a,b,c,d=P[i],P[(i+1)%n],P[(i-1)%n],P[(i+2)%n]
    rest=[P[j] for j in range(n) if j not in (i,(i+1)%n,(i-1)%n,(i+2)%n)]
    front=[c,d]+rest
    F=np.array(front); s=F.sum(1); df=F[:,0]-F[:,1]
    TL,BR,TR,BL=F[s.argmin()],F[s.argmax()],F[df.argmax()],F[df.argmin()]
    # spine: outer edge a-b, inner edge c-d (c next to a, d next to b)
    horizontal_spine = abs(a[0]-b[0]) > abs(a[1]-b[1])
    if not horizontal_spine:
        top_o,bot_o=(a,b) if a[1]<b[1] else (b,a); top_i,bot_i=(c,d) if c[1]<d[1] else (d,c)
        if top_o[0] < top_i[0]: spine=[top_o,top_i,bot_i,bot_o]   # spine on the left: TL,TR,BR,BL
        else: spine=[top_i,top_o,bot_o,bot_i]
    else:
        l_o,r_o=(a,b) if a[0]<b[0] else (b,a); l_i,r_i=(c,d) if c[0]<d[0] else (d,c)
        if l_o[1] > l_i[1]: spine=[l_i,r_i,r_o,l_o]  # spine at the bottom
        else: spine=[l_o,r_o,r_i,l_i]
    norm=lambda q:[[round(x/w,4),round(y/h,4)] for x,y in q]
    return norm([TL,TR,BR,BL]),norm(spine),horizontal_spine
def texparts(path,landscape):
    im=Image.open(path).convert('RGBA'); a=np.array(im)[:,:,3]; H,Wd=a.shape
    frac=(a>128).mean(0)
    c=Wd//2; good=frac>0.6
    # nearest good column to centre, then grow
    idx=np.nonzero(good)[0]
    if len(idx)==0: return None,None,None
    j=idx[np.abs(idx-c).argmin()]
    if abs(j-c)>0.12*Wd: return None,None,None
    l=j; r=j
    while l>0 and good[l-1]: l-=1
    while r<Wd-1 and good[r+1]: r+=1
    if (r-l+1)>0.25*Wd or (r-l+1)<0.02*Wd: return None,None,None
    spine=im.crop((l,0,r+1,H))
    front=im.crop((r+1,0,Wd,H))
    fa=np.array(front)[:,:,3]
    if (fa>128).mean()<0.02: front=None
    win=None
    if front is not None:
        t=(fa<128).astype(np.uint8)
        n,lab,st,_=cv2.connectedComponentsWithStats(t,connectivity=4)
        if n>1:
            k=1+int(np.argmax(st[1:,4])); x,y,ww,hh,ar=st[k]
            if ar>0.15*fa.size: win=[round(x/front.width,4),round(y/front.height,4),round((x+ww)/front.width,4),round((y+hh)/front.height,4)]
        if win is None: front=None
    return spine,front,win
table=[]
for p,(fo,c,cl,d,b) in M.items():
    e=dict(p=p)
    if c and W[p].get('cart'):
        im=Image.open(B+fo+'/'+c).convert('RGBA'); sz=save(im,f'tpl_cart_{rid(p)}',420)
        m=np.load(f'masks/{p}_cart.npy'); mk=Image.new('RGBA',(m.shape[1],m.shape[0]),(255,255,255,0)); mk.putalpha(Image.fromarray(m)); save(mk,f'tpl_cartm_{rid(p)}',160,80)
        e['cart']=dict(aspect=round(im.width/im.height,4),win=[round(x,4) for x in W[p]['cart']['rect']])
    if cl and W[p].get('label'):
        im=Image.open(B+fo+'/'+cl).convert('RGBA'); save(im,f'tpl_cartl_{rid(p)}',420)
        m=np.load(f'masks/{p}_label.npy'); mk=Image.new('RGBA',(m.shape[1],m.shape[0]),(255,255,255,0)); mk.putalpha(Image.fromarray(m)); save(mk,f'tpl_cartlm_{rid(p)}',160,80)
        e['label']=dict(aspect=round(im.width/im.height,4),win=[round(x,4) for x in W[p]['label']['rect']])
    if d:
        im=Image.open(B+fo+'/'+d).convert('RGBA'); save(im,f'tpl_disc_{rid(p)}',420); e['disc']=True
    if b:
        im=Image.open(B+fo+'/'+b).convert('RGBA'); w,h=im.size; save(im,f'tpl_box_{rid(p)}',560)
        fq,sq,hs=quads(BOX[p]['pts'],w,h)
        e['box']=dict(aspect=round(w/h,4),front=fq,spine=sq,hspine=bool(hs))
        if p in TEX and not hs:
            sp,fr,win=texparts(B+TEX[p],False)
            if sp is not None:
                sp.thumbnail((200,560),Image.LANCZOS); sp.save(OUT+f'tpl_spine_{rid(p)}.webp','WEBP',quality=86,method=4); e['box']['spineTex']=True
            if fr is not None:
                save(fr,f"tpl_front_{rid(p)}",420); e['box']['frame']=dict(aspect=round(fr.width/fr.height,4),win=win)
    table.append(e)
json.dump(table,open('table.json','w'),indent=1)
tot=sum(os.path.getsize(OUT+f) for f in os.listdir(OUT)); print(len(os.listdir(OUT)),'files',tot//1024,'KB')
for e in table: print(e['p'], {k:(v if k!='box' else {kk:vv for kk,vv in v.items() if kk in ('hspine','spineTex','frame')}) for k,v in e.items() if k!='p'})

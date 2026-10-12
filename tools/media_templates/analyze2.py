import json, numpy as np, cv2
from PIL import Image
from mapping import M
B='/mnt/user-data/uploads/Templates/'
def window(path, thr=128):
    im=np.array(Image.open(path).convert('RGBA')); a=im[:,:,3]
    h,w=a.shape
    op=(a>=thr).astype(np.uint8)
    pts=cv2.findNonZero(op); hull=cv2.convexHull(pts)
    hm=np.zeros_like(op); cv2.fillConvexPoly(hm,hull,1)
    # shrink hull a little so edge anti-alias gaps don't count
    hm=cv2.erode(hm,np.ones((5,5),np.uint8))
    cand=((a<thr)&(hm>0)).astype(np.uint8)
    n,lab,st,_=cv2.connectedComponentsWithStats(cand,connectivity=4)
    if n<=1: return None
    i=1+int(np.argmax(st[1:,4]))
    if st[i,4] < 0.04*w*h: return None
    m=(lab==i).astype(np.uint8)*255
    x,y,ww,hh=st[i,0],st[i,1],st[i,2],st[i,3]
    return dict(size=[w,h],rect=[x/w,y/h,(x+ww)/w,(y+hh)/h],fill=float(st[i,4]/(ww*hh)),mask=m)
res={}
for p,(fo,c,cl,d,b) in M.items():
    r={}
    for k,f in (('cart',c),('label',cl)):
        if f:
            wnd=window(B+fo+'/'+f)
            if wnd:
                np.save(f'masks/{p}_{k}.npy',wnd.pop('mask'))
            r[k]=wnd
    res[p]=r
json.dump(res,open('windows.json','w'),indent=1)
for p,r in res.items():
    print(p,{k:(None if v is None else ([round(x,3) for x in v['rect']],round(v['fill'],2))) for k,v in r.items()})

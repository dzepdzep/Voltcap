import json, numpy as np, cv2, sys
from PIL import Image, ImageDraw
from mapping import M
B='/mnt/user-data/uploads/Templates/'
res={}
items=[]
for p,(fo,c,cl,d,b) in M.items():
    if not b: continue
    im=np.array(Image.open(B+fo+'/'+b).convert('RGBA')); a=im[:,:,3]; h,w=a.shape
    m=(a>8).astype(np.uint8)
    m=cv2.morphologyEx(m,cv2.MORPH_CLOSE,np.ones((9,9),np.uint8))
    cs,_=cv2.findContours(m,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
    c=max(cs,key=cv2.contourArea)
    hull=cv2.convexHull(c)
    peri=cv2.arcLength(hull,True)
    best=None
    for eps in np.linspace(0.002,0.05,60):
        ap=cv2.approxPolyDP(hull,eps*peri,True)
        if len(ap)<=6: best=ap; break
    pts=best.reshape(-1,2).tolist()
    res[p]=dict(size=[w,h],pts=pts)
    items.append((p,B+fo+'/'+b,pts))
    print(p,(w,h),len(pts),pts)
json.dump(res,open('box_raw.json','w'))
cell=260; cols=8; rows=(len(items)+cols-1)//cols
sheet=Image.new('RGB',(cols*cell,rows*cell),(255,0,255)); dr=ImageDraw.Draw(sheet)
for n,(p,f,pts) in enumerate(items):
    im=Image.open(f).convert('RGBA'); s=min((cell-10)/im.width,(cell-10)/im.height)
    t=im.resize((int(im.width*s),int(im.height*s))); bg=Image.new('RGBA',t.size,(255,0,255,255)); bg.alpha_composite(t)
    x0=(n%cols)*cell+5; y0=(n//cols)*cell+5; sheet.paste(bg.convert('RGB'),(x0,y0))
    for i,(x,y) in enumerate(pts):
        dr.ellipse([x0+x*s-4,y0+y*s-4,x0+x*s+4,y0+y*s+4],fill=(0,255,0)); dr.text((x0+x*s+5,y0+y*s),str(i),fill=(0,0,0))
    dr.text((x0,y0+cell-20),p,fill=(0,0,0))
sheet.save('/tmp/tpl/boxes.jpg',quality=80)

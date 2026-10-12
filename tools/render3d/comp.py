import json,sys,numpy as np
from PIL import Image
def homog(src,dst):
    A=[];b=[]
    for (x,y),(u,v) in zip(src,dst):
        A+= [[x,y,1,0,0,0,-u*x,-u*y],[0,0,0,x,y,1,-v*x,-v*y]]; b+=[u,v]
    h=np.linalg.solve(np.array(A,float),np.array(b,float)); return np.append(h,1).reshape(3,3)
def compose(mid,yaw,cover):
    d=f'pack/{mid}'; meta=json.load(open(d+'/meta.json'))
    base=np.asarray(Image.open(f'{d}/{yaw}.webp').convert('RGBA')).astype(float)/255
    gain=np.asarray(Image.open(f'{d}/{yaw}_g.webp').convert('RGBA')).astype(float)/255
    H,W=base.shape[:2]
    cv=Image.open(cover).convert('RGB'); cw,ch=cv.size; asp=meta['art']
    if cw/ch>asp: nw=int(ch*asp); cv=cv.crop(((cw-nw)//2,0,(cw-nw)//2+nw,ch))
    else: nh=int(cw/asp); cv=cv.crop((0,(ch-nh)//2,cw,(ch-nh)//2+nh))
    q=meta['angles'][str(yaw)]; dst=[(q[i]*W,q[i+1]*H) for i in range(0,8,2)]
    sw,sh=cv.size; src=[(0,0),(sw,0),(sw,sh),(0,sh)]
    Hm=homog(dst,src)  # output->input for PIL
    warped=np.asarray(cv.transform((W,H),Image.PERSPECTIVE,Hm.flatten()[:8],Image.BILINEAR)).astype(float)/255
    layer=warped*gain[...,:3]*gain[...,3:4]
    bp=base[...,:3]*base[...,3:4]
    out=np.clip(bp+layer,0,1); oa=np.clip(base[...,3:4]+gain[...,3:4],0,1)
    rgb=np.where(oa>0,out/np.maximum(oa,1e-6),0)
    return Image.fromarray((np.concatenate([rgb,oa],2)*255).astype(np.uint8),'RGBA')
if __name__=='__main__':
    mid=sys.argv[1]; cover=sys.argv[2]
    ims=[compose(mid,y,cover) for y in [-60,-20,0,20,60]]
    W=sum(i.width for i in ims); S=Image.new('RGBA',(W,ims[0].height),(30,34,42,255)); x=0
    for i in ims: S.alpha_composite(i,(x,0)); x+=i.width
    S.save(f'comp_{mid}.png')

import numpy as np
from PIL import Image, ImageFilter
rng=np.random.default_rng(7)
N=512
def pnoise(n, beta, seed):
    r=np.random.default_rng(seed)
    f=np.fft.fftfreq(n)
    fx,fy=np.meshgrid(f,f)
    k=np.sqrt(fx**2+fy**2); k[0,0]=1
    spec=(r.normal(size=(n,n))+1j*r.normal(size=(n,n)))/k**beta
    spec[0,0]=0
    x=np.real(np.fft.ifft2(spec))
    x=(x-x.mean())/x.std()
    return x
def aniso(n, seed, sx=1.0, sy=30.0):
    # fibres: noise stretched along x (periodic)
    r=np.random.default_rng(seed)
    f=np.fft.fftfreq(n)
    fx,fy=np.meshgrid(f,f)
    g=np.exp(-((fx*n/sx)**2*0.0+(fx*sy)**2*40 + (fy*n)**2*0.0004))
    spec=(r.normal(size=(n,n))+1j*r.normal(size=(n,n)))*np.exp(-(fx**2)*2000)*(1/(1+np.abs(fy)*n*0.05))
    x=np.real(np.fft.ifft2(spec)); return (x-x.mean())/x.std()
def fibres(n, count, seed):
    r=np.random.default_rng(seed)
    img=np.zeros((n,n))
    for _ in range(count):
        x0,y0=r.uniform(0,n,2); L=r.uniform(4,22); a=r.normal(0,0.35)+ (0 if r.random()<0.7 else r.uniform(0,np.pi))
        v=r.choice([-1,1])*r.uniform(0.3,1.0)
        for t in np.linspace(0,L,int(L*2)):
            xi=int(x0+np.cos(a)*t)%n; yi=int(y0+np.sin(a)*t)%n
            img[yi,xi]+=v
    return img
def specks(n,count,seed):
    r=np.random.default_rng(seed); img=np.zeros((n,n))
    for _ in range(count):
        x,y=r.integers(0,n,2); img[y,x]-=r.uniform(0.5,1.5)
        if r.random()<0.3: img[y,(x+1)%n]-=0.5
    return img
def blur_wrap(a, rad):
    t=np.tile(a,(3,3)); im=Image.fromarray(((t-t.min())/(t.max()-t.min())*255).astype(np.uint8))
    im=im.filter(ImageFilter.GaussianBlur(rad)); b=np.asarray(im).astype(float)/255*(t.max()-t.min())+t.min()
    n=a.shape[0]; return b[n:2*n,n:2*n]
def make(base, var, name, fib=1.0, ribs=0.0):
    low=pnoise(N,1.6,1)*0.5+pnoise(N,1.1,2)*0.25
    fb=blur_wrap(fibres(N,9000,3),0.6)*0.35*fib
    sp=blur_wrap(specks(N,900,4),0.5)*0.6
    grain=pnoise(N,0.3,5)*0.12
    h=low*0.35+fb+grain
    if ribs>0:
        y=np.arange(N); rib=np.sin(y/N*2*np.pi*48)[:,None]*np.ones((1,N))
        h=h+rib*ribs
    # baked light from top-left
    gx=np.roll(h,-1,1)-np.roll(h,1,1); gy=np.roll(h,-1,0)-np.roll(h,1,0)
    shade=1+ (-gx*0.6 - gy*0.8)*0.18
    col=np.array(base,float)[None,None,:]/255
    tone=1+low[...,None]*var*0.08+fb[...,None]*0.10+sp[...,None]*0.18+grain[...,None]*0.04
    out=col*tone*shade[...,None]
    out=np.clip(out,0,1)
    Image.fromarray((out*255).astype(np.uint8)).save(name, quality=90)
make((186,140,92),1.0,'box_kraft.webp')
make((196,150,100),1.0,'box_kraft_wall.webp',ribs=0.35)
make((228,224,214),0.6,'box_white.webp',fib=0.5)
make((232,228,218),0.6,'box_white_wall.webp',fib=0.5,ribs=0.3)
# flute edge strip: liner / fluting / liner, tileable in x
W,H=256,32
img=np.zeros((H,W,3))
liner=np.array([190,146,96])/255; dark=np.array([60,40,22])/255; flute=np.array([170,126,80])/255
img[:]=dark
img[0:5]=liner; img[H-5:H]=liner*0.92
x=np.arange(W); yy=np.arange(H)[:,None]
wave=(H/2)+ (H/2-6)*np.sin(x/W*2*np.pi*16)
d=np.abs(yy-wave[None,:])
mask=d<2.2
img[mask]=flute[None,:]*np.ones((mask.sum(),1)) if False else flute
img=img*(1+pnoise(W,1.0,9)[:H,:W,None]*0.05)
Image.fromarray((np.clip(img,0,1)*255).astype(np.uint8)).save('box_flute.webp',quality=92)
print('ok')

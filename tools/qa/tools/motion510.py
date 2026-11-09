import sys,subprocess,numpy as np
# per-frame mean |diff| of a screenrecord, grayscale 160 px wide; prints the series and max ratio vs median of moving frames
f=sys.argv[1]
w=160
out=subprocess.run(['ffmpeg','-v','error','-i',f,'-vf',f'scale={w}:-2,format=gray','-f','rawvideo','-'],capture_output=True).stdout
probe=subprocess.run(['ffprobe','-v','error','-select_streams','v','-show_entries','stream=width,height','-of','csv=p=0',f],capture_output=True,text=True).stdout.split(',')
W,H=int(probe[0]),int(probe[1]); h=int(round(H*w/W/2)*2)
a=np.frombuffer(out,np.uint8); n=len(a)//(w*h); a=a[:n*w*h].reshape(n,h,w).astype(np.int16)
d=[float(np.abs(a[i]-a[i-1]).mean()) for i in range(1,n)]
print(n,'frames'); print(' '.join(f'{x:.2f}' for x in d))

"""Flash detector: a frame that differs from both neighbours while the neighbours match each other = one-frame flash.
usage: py -3 -I flash412.py FRAMES_DIR [threshold]"""
import sys, os
from PIL import Image, ImageChops, ImageStat
d = sys.argv[1]; th = float(sys.argv[2]) if len(sys.argv) > 2 else 6.0
fs = sorted(f for f in os.listdir(d) if f.endswith('.png'))
im = [Image.open(os.path.join(d, f)).convert('L').resize((160, 90)) for f in fs]
def diff(a, b): return ImageStat.Stat(ImageChops.difference(a, b)).mean[0]
n = 0; mx = 0
for i in range(1, len(im) - 1):
    a, b, c = diff(im[i-1], im[i]), diff(im[i], im[i+1]), diff(im[i-1], im[i+1])
    mx = max(mx, a)
    if a > th and b > th and c < min(a, b) * 0.5:
        n += 1; print('FLASH frame', fs[i], round(a, 1), round(b, 1), round(c, 1))
print('frames', len(im), 'flashes', n, 'max step diff', round(mx, 1))

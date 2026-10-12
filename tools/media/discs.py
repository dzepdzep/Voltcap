import numpy as np, json
from PIL import Image
from scipy import ndimage

def disc(name, rim=(196, 200, 208), hub=(214, 218, 226), size=320, pad=20, label_r=(0.235, 0.955), gd_ring=False, dark=False):
    SS = 2
    N = (size + 2 * pad) * SS
    yy, xx = np.mgrid[0:N, 0:N]
    c = N / 2
    x = (xx + 0.5 - c) / (size * SS / 2); y = (yy + 0.5 - c) / (size * SS / 2)
    r = np.hypot(x, y); ang = np.arctan2(y, x)
    col = np.zeros((N, N, 3)); a = np.zeros((N, N))
    disc_m = r <= 1.0
    # the data side seen through the clear edge: metallic with a rainbow sheen
    hue = (ang * 2 / np.pi + r * 0.6) % 1.0
    import colorsys
    rb = np.dstack([np.vectorize(lambda h: colorsys.hsv_to_rgb(h, 0.35, 1.0)[i])(hue[::8, ::8]) for i in range(3)])
    rb = np.kron(rb, np.ones((8, 8, 1)))[:N, :N]
    base = np.array(rim) / 255.0
    metal = base * (0.75 + 0.25 * np.cos(ang * 2 + 0.6))[..., None]
    col[disc_m] = (metal * 0.7 + rb * 0.3)[disc_m]
    a[disc_m] = 1.0
    # the clear outer lip
    lip = (r > 0.975) & disc_m
    col[lip] = col[lip] * 0.7 + 0.3; 
    # hub: clear plastic with molded rings, and the hole
    hubm = r < label_r[0]
    col[hubm] = np.array(hub) / 255.0
    a[hubm] = 0.82
    for rr, w, k in [(0.205, 0.008, 0.75), (0.16, 0.012, 0.8), (0.12, 0.006, 0.9)]:
        ring = np.abs(r - rr) < w
        col[ring] *= k; a[ring] = 0.9
    hole = r < 0.075
    a[hole] = 0.0
    hole_edge = (r >= 0.075) & (r < 0.09)
    col[hole_edge] = 0.55; a[hole_edge] = 0.95
    # the label window (the app draws the label behind it): transparent annulus, a printed-edge shade and gloss
    lab = (r >= label_r[0]) & (r <= label_r[1])
    edge = np.clip(1 - np.minimum(r - label_r[0], label_r[1] - r) / 0.02, 0, 1)
    gloss = np.exp(-((ang + 2.2) / 0.35) ** 2) * np.clip((r - 0.3) / 0.6, 0, 1) * 0.22 + np.exp(-((ang - 0.9) / 0.25) ** 2) * 0.10
    shade = edge * 0.35
    col[lab] = np.where((gloss > shade)[lab][:, None], 1.0, 0.0)
    a[lab] = np.maximum(gloss, shade)[lab]
    if gd_ring:
        g = np.abs(r - 0.33) < 0.035
        col[g & lab] = np.array([0.80, 0.70, 0.45]); a[g & lab] = 0.85
        lab &= ~g
    # soft drop shadow
    m = (r <= 1.0).astype(float)
    sh = ndimage.gaussian_filter(np.roll(m, int(6 * SS), axis=0), 8 * SS) * 0.5
    sh[r <= 1.0] = 0   # only round the disc, never over its label
    out_a = a + sh * (1 - a)
    out_c = np.where(a[..., None] > 0, col * (a / np.maximum(out_a, 1e-6))[..., None], 0)
    img = np.dstack([np.clip(out_c, 0, 1), np.clip(out_a, 0, 1)])
    im = Image.fromarray((img * 255).astype(np.uint8), 'RGBA').resize((size + 2 * pad, size + 2 * pad), Image.LANCZOS)
    im.save(f'out/media_{name}.png')
    T = size + 2 * pad
    return {'w': T, 'h': T, 'pad': pad / T, 'labelInner': label_r[0], 'labelOuter': label_r[1]}

if __name__ == '__main__':
    meta = json.load(open('out/meta.json'))
    meta['disc'] = disc('disc')
    meta['disc_dark'] = disc('disc_dark', rim=(40, 40, 48), hub=(200, 204, 212))
    meta['disc_white'] = disc('disc_white', rim=(232, 232, 236))
    meta['disc_gd'] = disc('disc_gd', gd_ring=True)
    meta['disc_mini'] = disc('disc_mini', rim=(186, 180, 206), label_r=(0.3, 0.95))
    json.dump(meta, open('out/meta.json', 'w'), indent=1)
    print({k: v for k, v in meta.items() if k.startswith('disc')})

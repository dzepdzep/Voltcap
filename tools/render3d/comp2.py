import json, sys, numpy as np
from PIL import Image
from comp import homog
L = '/mnt/user-data/uploads/VoltCap_3D_Shape_Library_v6_8_Complete--VoltCap_v6_Refined--v6/branding/logos/'

def lin(u): return np.where(u <= 0.04045, u / 12.92, ((u + 0.055) / 1.055) ** 2.4)
def srgb(c): return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(np.maximum(c, 0), 1 / 2.4) - 0.055)
def hexc(h): return np.array([int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)])
def img(p): return np.asarray(Image.open(p).convert('RGBA')).astype(float) / 255

MID = float(lin(np.array(0.5)))
def aces(x): return np.clip(x * (2.51 * x + 0.03) / (x * (2.43 * x + 0.59) + 0.14), 0, 1)
def T(x): return srgb(aces(np.maximum(x, 0)))
XS = np.linspace(0, 40, 400001); YS = T(XS)
def Tinv(y): return np.interp(np.minimum(y, YS[-1]), YS, XS)

def tinted(d, yaw, shell, badge, meta):
    t = meta['tint']; lv = t['levels']
    S = [img(f'{d}/{yaw}_t{l}.webp') for l in lv]
    bw = img(f'{d}/{yaw}_b.webp')[..., :1]
    if not t['clear']:
        t0, tm = S
        c = (1 - bw) * lin(shell) + bw * lin(badge)
        x0 = Tinv(t0[..., :3]); xb = (Tinv(tm[..., :3]) - x0) / MID
        return T(x0 + c * xb) * t0[..., 3:4], t0[..., 3:4]
    u = (1 - bw) * shell + bw * badge
    us = np.array(lv) / 100
    def pw(vals, uu):  # piecewise linear in sRGB level
        out = np.zeros(uu.shape)
        for k in range(len(us) - 1):
            f = np.clip((uu - us[k]) / (us[k + 1] - us[k]), 0, 1)
            inseg = (uu >= us[k]) & (uu <= us[k + 1]) if k == 0 else (uu > us[k]) & (uu <= us[k + 1])
            out = np.where(inseg, vals[k] * (1 - f) + vals[k + 1] * f, out)
        return out
    P = [x[..., :3] * x[..., 3:4] for x in S]
    col = pw(P, u)
    um = np.broadcast_to(u.mean(2, keepdims=True), u.shape[:2] + (1,))
    a = pw([x[..., 3:4] for x in S], um)
    return np.clip(col, 0, 1), np.clip(a, 0, 1)

MODE = 'exact'
def tinted_q(d, yaw, shell, badge, meta):
    t0, tm, t1 = (img(f'{d}/{yaw}_{n}.webp') for n in ('t0', 'tm', 't1'))
    bw = img(f'{d}/{yaw}_b.webp')[..., :1]
    u = (1 - bw) * shell + bw * badge                       # per pixel, per channel (sRGB 0..1)
    P = [x[..., :3] * x[..., 3:4] for x in (t0, tm, t1)]   # premultiplied
    A = [x[..., 3:4] for x in (t0, tm, t1)]
    def q(v0, vm, v1, t): return v0 + t * (4 * vm - 3 * v0 - v1) + t * t * (2 * v1 - 4 * vm + 2 * v0)
    col = q(*P, u)
    a = A[0] + (A[2] - A[0]) * lin(u).mean(2, keepdims=True)
    return np.clip(col, 0, 1), np.clip(a, 0, 1)

def compose(mid, yaw, cover, shell, badge, sysid, root='t68'):
    d = f'{root}/{mid}'; meta = json.load(open(d + '/meta.json')); t = meta['tint']
    shell, badge = hexc(shell), hexc(badge)
    col, a = tinted(d, yaw, shell, badge, meta)
    H, W = col.shape[:2]
    # art
    gain = img(f'{d}/{yaw}_gw.webp' if t['clear'] else f'{d}/{yaw}_g.webp')
    if t['clear']:
        tr = t['trans']; ratio = srgb(tr * lin(shell)) / max(srgb(np.array(tr)), 1e-3)
        gain = np.concatenate([gain[..., :3] * ratio, gain[..., 3:]], 2)
    cv = Image.open(cover).convert('RGB'); cw, ch = cv.size; asp = meta['art']
    if cw / ch > asp: nw = int(ch * asp); cv = cv.crop(((cw - nw) // 2, 0, (cw - nw) // 2 + nw, ch))
    else: nh = int(cw / asp); cv = cv.crop((0, (ch - nh) // 2, cw, (ch - nh) // 2 + nh))
    qd = meta['angles'][str(yaw)]; dst = [(qd[i] * W, qd[i + 1] * H) for i in range(0, 8, 2)]
    sw, sh = cv.size
    warped = np.asarray(cv.transform((W, H), Image.PERSPECTIVE, homog(dst, [(0, 0), (sw, 0), (sw, sh), (0, sh)]).flatten()[:8], Image.BILINEAR)).astype(float) / 255
    col = np.clip(col + warped * gain[..., :3] * gain[..., 3:4], 0, 1); a = np.clip(a + gain[..., 3:4], 0, 1)
    # logo: fitted 96% x 90% in its plate, lit like it
    lq = t['logo'].get(str(yaw))
    if lq:
        lg = Image.open(L + sysid + '.png').convert('RGBA'); asp = t['logoAspect']
        rw, rh = 1000, 1000 / asp
        s = min(rw * .96 / lg.width, rh * .90 / lg.height); w, h = lg.width * s, lg.height * s
        x0, y0 = (rw - w) / 2, (rh - h) / 2
        plate = Image.new('RGBA', (1000, int(rh)), (0, 0, 0, 0)); plate.alpha_composite(lg.resize((int(w), int(h)), Image.LANCZOS), (int(x0), int(y0)))
        dst = [(lq[i] * W, lq[i + 1] * H) for i in range(0, 8, 2)]
        pw = np.asarray(plate.transform((W, H), Image.PERSPECTIVE, homog(dst, [(0, 0), (plate.width, 0), (plate.width, plate.height), (0, plate.height)]).flatten()[:8], Image.BILINEAR)).astype(float) / 255
        la = pw[..., 3:4]; lc = pw[..., :3] * t['ls'][str(yaw)]
        col = col * (1 - la) + lc * la; a = a * (1 - la) + la
    rgb = np.where(a > 0, col / np.maximum(a, 1e-6), 0)
    return Image.fromarray((np.concatenate([np.clip(rgb, 0, 1), a], 2) * 255).astype(np.uint8), 'RGBA')

if __name__ == '__main__':
    mid, yaw, shell, badge, sysid = sys.argv[1:6]
    d = f't68/{mid}'; meta = json.load(open(d + '/meta.json'))
    col, a = tinted(d, yaw, hexc(shell), hexc(badge), meta)
    em = np.concatenate([np.where(a > 0, col / np.maximum(a, 1e-6), 0), a], 2)
    tr = img(f'truth_{mid}.png')
    m = (tr[..., 3] > 0.05) | (a[..., 0] > 0.05)
    pe = em[..., :3] * em[..., 3:4]; pt = tr[..., :3] * tr[..., 3:4]
    err = np.abs(pe - pt)[m] * 255; ea = np.abs(em[..., 3] - tr[..., 3])[m] * 255
    print(mid, 'rgb err mean %.2f p99 %.1f max %.0f | alpha mean %.2f max %.0f' % (err.mean(), np.percentile(err, 99), err.max(), ea.mean(), ea.max()))
    bg = Image.new('RGBA', Image.open(f'truth_{mid}.png').size, (40, 44, 52, 255))
    e = Image.fromarray((np.clip(em, 0, 1) * 255).astype(np.uint8), 'RGBA')
    A = bg.copy(); A.alpha_composite(e); B = bg.copy(); B.alpha_composite(Image.open(f'truth_{mid}.png').convert('RGBA'))
    C = bg.copy(); C.alpha_composite(compose(mid, yaw, 'cover.png', shell, badge, sysid))
    S = Image.new('RGBA', (A.width * 3, A.height)); S.paste(A, (0, 0)); S.paste(B, (A.width, 0)); S.paste(C, (A.width * 2, 0))
    S.save(f'cmp_{mid}.png')

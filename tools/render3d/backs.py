# Update 166: the back label's corners per angle, added to each finished piece's meta.json ("backs", "backAspect"),
# from the same camera as render.js (the crop box recovered from the art window's corners already in meta.json).
import json, math, sys, os
import numpy as np
A = '/mnt/user-data/uploads/all'
V = json.load(open(A + '/VoltCap_v6_Refined/v6/manifest.json'))['models']
BBm = json.load(open(A + '/VoltCap_Blank_Branding_Variants/manifest.json'))['models']
SL = json.load(open(A + '/VoltCap_v6_Refined/v6/artwork_slots.json'))
PLAN = json.load(open('/tmp/claude-0/r3d166/glbplan.json'))
SIZE, PITCH, FOV = 1024, 6, 20

def bounds(id):
    f = PLAN[id]
    for m in (BBm if 'blank_branding' in f else V):
        if m['file'].split('/')[-1] == f.split('/')[-1]: return np.array(m['measured_bounds_mm']) / 1000
    raise KeyError(id)

def project(lohi, yaw, pts):
    lo, hi = lohi
    c = (lo + hi) / 2; r = np.linalg.norm(hi - lo) / 2
    dist = r / math.sin(FOV * math.pi / 360) * 1.02
    out = []
    for p in pts:
        x, y, z = np.array(p) - c
        t = math.radians(yaw); cs, sn = math.cos(t), math.sin(t)
        x, z = cs * x + sn * z, -sn * x + cs * z
        t = math.radians(PITCH); cs, sn = math.cos(t), math.sin(t)
        y, z = cs * y - sn * z, sn * y + cs * z
        z -= dist
        f = 1 / math.tan(FOV * math.pi / 360)
        nx, ny = f * x / -z, f * y / -z
        out += [(nx * 0.5 + 0.5) * SIZE, (1 - (ny * 0.5 + 0.5)) * SIZE]
    return out

def rect(b, back=False):
    lo, hi = np.array(b[0]) / 1000, np.array(b[1]) / 1000; z = (lo[2] + hi[2]) / 2
    if back: return [[hi[0], hi[1], z], [lo[0], hi[1], z], [lo[0], lo[1], z], [hi[0], lo[1], z]]
    return [[lo[0], hi[1], z], [hi[0], hi[1], z], [hi[0], lo[1], z], [lo[0], lo[1], z]]

def run(id, root):
    d = f'{root}/{id}'; mp = d + '/meta.json'
    meta = json.load(open(mp)); sl = SL[id]
    lohi = bounds(id)
    # crop box: least squares on norm = (px - x0) / cw over every art corner of every angle
    X, Y = [], []
    for k, q in meta['angles'].items():
        px = project(lohi, int(k), rect(sl['front_art']['bounds_mm']))
        for i in range(4):
            X.append((px[2 * i], q[2 * i])); Y.append((px[2 * i + 1], q[2 * i + 1]))
    def fit(P):
        a = np.array(P); M = np.stack([a[:, 1], np.ones(len(a))], 1)
        (cw, x0), res, *_ = np.linalg.lstsq(M, a[:, 0], rcond=None)
        err = np.abs(M @ [cw, x0] - a[:, 0]).max()
        return x0, cw, err
    x0, cw, ex = fit(X); y0, ch, ey = fit(Y)
    t = meta.get('tint')
    # A logo plate without geometry of its own (3ds_card_real): its corners from the slots instead.
    if t and t.get('logoAspect', 0) == 0 and 'console_logo' in sl:
        t['logoAspect'] = sl['console_logo']['image_aspect_ratio']
        for k in list(meta['angles']):
            px = project(lohi, int(k), rect(sl['console_logo']['bounds_mm']))
            t['logo'][k] = [round((px[i] - x0) / cw, 5) if i % 2 == 0 else round((px[i] - y0) / ch, 5) for i in range(8)]
            t['ls'].setdefault(k, 0.8)
    if 'back_label' not in sl:
        if '--write' in sys.argv: json.dump(meta, open(mp, 'w'), separators=(',', ':'))
        return id, 'no back label', ex, ey
    bl = sl['back_label']
    backs = {}
    for yaw in meta['yaws']:
        if math.cos(math.radians(yaw)) > -0.15: continue
        px = project(lohi, yaw, rect(bl['bounds_mm'], back=True))
        backs[str(yaw)] = [round((px[i] - x0) / cw, 5) if i % 2 == 0 else round((px[i] - y0) / ch, 5) for i in range(8)]
    meta['backs'] = backs; meta['backAspect'] = bl['image_aspect_ratio']
    if '--write' in sys.argv: json.dump(meta, open(mp, 'w'), separators=(',', ':'))
    return id, len(backs), round(ex, 2), round(ey, 2)

if __name__ == '__main__':
    root = '/tmp/claude-0/r3d166/pack611'
    for id in sorted(os.listdir(root)):
        if os.path.exists(f'{root}/{id}/meta.json'): print(*run(id, root))

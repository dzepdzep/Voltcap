"""
Realistic cartridge / disc templates for Voltcap's drawn physical media.
Each template is a PNG: the plastic shell lit like a real object (bevels, recessed label pocket, grip grooves,
specular light, ambient occlusion, faint plastic grain, soft drop shadow) with a transparent window where the
game's label goes. The app draws the label behind the window. Label windows are printed as fractions.
"""
import numpy as np, json
from PIL import Image
from scipy import ndimage

SS = 2   # supersampling

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1); return t * t * (3 - 2 * t)

class Shell:
    def __init__(s, W, H, pad=28):
        s.W, s.H, s.pad = W, H, pad
        s.w, s.h = (W + 2 * pad) * SS, (H + 2 * pad) * SS
        yy, xx = np.mgrid[0:s.h, 0:s.w]
        s.x = (xx + 0.5) / SS - pad; s.y = (yy + 0.5) / SS - pad   # shell coordinates (px of the final shell)
        s.height = np.zeros((s.h, s.w))
        s.mask = np.zeros((s.h, s.w), bool)
        s.color = np.zeros((s.h, s.w, 3))
        s.hole = np.zeros((s.h, s.w), bool)
        s.extra = []   # (mask, rgb, alpha) painted on top after lighting

    # shapes in shell px
    def rrect(s, x0, y0, x1, y1, r):
        cx = np.clip(s.x, x0 + r, x1 - r); cy = np.clip(s.y, y0 + r, y1 - r)
        return (s.x - cx) ** 2 + (s.y - cy) ** 2 <= r * r
    def poly(s, pts):
        from matplotlib.path import Path
        return Path(pts).contains_points(np.c_[s.x.ravel(), s.y.ravel()]).reshape(s.h, s.w)
    def circle(s, cx, cy, r): return (s.x - cx) ** 2 + (s.y - cy) ** 2 <= r * r

    def body(s, m, rgb, bevel=7.0):
        s.mask |= m
        d = ndimage.distance_transform_edt(m) / SS
        s.height = np.maximum(s.height, np.where(m, smooth(0, bevel, d) * 1.0, 0))
        s.color[m] = np.array(rgb) / 255.0
    def raise_(s, m, amount, bevel=3.0, rgb=None):
        d = ndimage.distance_transform_edt(m) / SS
        s.height += np.where(m, smooth(0, bevel, d) * amount, 0)
        if rgb is not None: s.color[m & s.mask] = np.array(rgb) / 255.0
    def pocket(s, m, depth=0.35, wall=3.0):
        d = ndimage.distance_transform_edt(m) / SS
        s.height -= np.where(m, smooth(0, wall, d) * depth, 0)
    def groove(s, x0, y0, x1, y1, width=1.6, depth=0.28):
        # a straight groove (distance to the segment)
        px, py = s.x - x0, s.y - y0; dx, dy = x1 - x0, y1 - y0
        t = np.clip((px * dx + py * dy) / (dx * dx + dy * dy), 0, 1)
        dist = np.hypot(px - t * dx, py - t * dy)
        s.height -= depth * np.exp(-(dist / width) ** 2) * s.mask
    def window(s, x0, y0, x1, y1, r=3.0, mask=None):
        """The label window: a rounded rectangle, or any [mask] (its bounding box is where the label goes)."""
        s.label = (x0, y0, x1, y1, r)
        s.hole |= s.rrect(x0, y0, x1, y1, r) if mask is None else mask

    def render(s, name, shine=0.55, grain=0.025, gloss=True):
        h = ndimage.gaussian_filter(s.height, 0.6 * SS)
        gy, gx = np.gradient(h * 6.0 * SS)
        n = np.dstack([-gx, -gy, np.ones_like(h)]); n /= np.linalg.norm(n, axis=2, keepdims=True)
        L = np.array([-0.45, -0.65, 0.62]); L /= np.linalg.norm(L)
        V = np.array([0, 0, 1.0]); Hh = (L + V); Hh /= np.linalg.norm(Hh)
        diff = np.clip(n @ L, 0, 1)
        spec = np.clip(n @ Hh, 0, 1) ** 40 * shine
        # ambient occlusion: how much lower a point is than its surroundings
        ao = np.clip(1 - (ndimage.gaussian_filter(h, 5 * SS) - h) * 1.6, 0.45, 1)
        yn = (s.y - s.y.min()) / (s.y.max() - s.y.min())
        light = (0.42 + 0.7 * diff) * ao * (1.04 - 0.1 * yn)
        rng = np.random.default_rng(len(name))
        noise = ndimage.gaussian_filter(rng.standard_normal((s.h, s.w)), 0.7) * grain
        col = s.color * light[..., None] + spec[..., None] * 0.9 + noise[..., None]
        col = np.clip(col, 0, 1)
        alpha = s.mask.astype(float)
        # the label window: see-through, with the pocket's inner shadow and a gloss over it
        if s.hole.any():
            inner = ndimage.distance_transform_edt(s.hole) / SS
            edge_dark = np.clip(1 - inner / 5.0, 0, 1) ** 1.6
            shade_dir = np.clip(1 - (s.y - s.label[1]) / 6.0, 0, 1) * 0.6 + np.clip(1 - (s.x - s.label[0]) / 6.0, 0, 1) * 0.4
            a = np.clip(edge_dark * 0.55 + shade_dir * 0.25, 0, 0.75)
            if gloss:
                u = (s.x - s.label[0]) / max(1, s.label[2] - s.label[0]) + (s.y - s.label[1]) / max(1, s.label[3] - s.label[1]) * 0.6
                band = np.exp(-((u - 0.35) / 0.12) ** 2) * 0.10 + np.exp(-((u - 0.55) / 0.05) ** 2) * 0.06
            else:
                band = 0
            col[s.hole] = np.where((a > 0.02)[s.hole][:, None], 0.0, col[s.hole])
            alpha[s.hole] = a[s.hole]
            white = (band if np.ndim(band) else np.zeros_like(a))
            gl = s.hole & (white > a)
            col[gl] = 1.0; alpha[gl] = white[gl]
        for m, rgb, a in s.extra:
            col[m] = np.array(rgb) / 255.0; alpha[m] = a
        # soft drop shadow under the shell
        sh = ndimage.gaussian_filter(np.roll(s.mask.astype(float), int(7 * SS), axis=0), 9 * SS) * 0.55
        sh[s.hole] = 0   # the shadow falls round the shell, never over its own label
        out_a = alpha + sh * (1 - alpha)
        out_c = np.where(alpha[..., None] > 0, col * (alpha / np.maximum(out_a, 1e-6))[..., None], 0)
        img = np.dstack([np.clip(out_c, 0, 1), np.clip(out_a, 0, 1)])
        im = Image.fromarray((img * 255).astype(np.uint8), 'RGBA').resize((s.W + 2 * s.pad, s.H + 2 * s.pad), Image.LANCZOS)
        im.save(f'out/media_{name}.png')
        x0, y0, x1, y1, r = s.label if s.hole.any() else (0, 0, 0, 0, 0)
        Wt, Ht = s.W + 2 * s.pad, s.H + 2 * s.pad
        return {'w': Wt, 'h': Ht, 'label': [round((x0 + s.pad) / Wt, 4), round((y0 + s.pad) / Ht, 4), round((x1 + s.pad) / Wt, 4), round((y1 + s.pad) / Ht, 4)], 'r': r}

from gen_media import *

def n64():
    s = Shell(360, 232)
    s.body(s.rrect(0, 0, 360, 200, 22) | s.rrect(14, 140, 346, 232, 8), (98, 100, 108))
    s.pocket(s.rrect(30, 16, 330, 150, 7))
    s.window(38, 22, 322, 144, 3)
    for y in np.arange(166, 222, 9): s.groove(70, y, 290, y)
    for x in (12, 348): s.groove(x, 28, x, 140, width=1.4, depth=0.22)
    return s

def snes():
    s = Shell(300, 236)
    s.body(s.rrect(0, 14, 300, 236, 14) | s.rrect(26, 0, 274, 60, 26), (156, 156, 164))
    s.pocket(s.rrect(34, 26, 266, 150, 6))
    s.window(40, 32, 260, 144, 3)
    for y in np.arange(168, 226, 9): s.groove(56, y, 244, y)
    return s

def nes():
    s = Shell(240, 280)
    s.body(s.rrect(0, 0, 240, 280, 8), (126, 126, 132))
    s.raise_(s.rrect(0, 0, 240, 22, 6), 0.15)
    s.pocket(s.rrect(18, 26, 222, 156, 5))
    s.window(24, 32, 216, 150, 2)
    for y in np.arange(176, 268, 10): s.groove(16, y, 224, y, depth=0.32)
    return s

def gb(name, rgb):
    s = Shell(228, 258)
    s.body(s.poly([(8, 0), (190, 0), (228, 30), (228, 250), (220, 258), (8, 258), (0, 250), (0, 8)]) | s.rrect(0, 0, 40, 40, 8), rgb)
    for y in (10, 18, 26): s.groove(22, y, 170, y, width=1.3)
    s.pocket(s.rrect(22, 44, 206, 232, 7))
    s.window(28, 50, 200, 226, 3)
    return s

def gba():
    s = Shell(300, 182)
    s.body(s.rrect(0, 20, 300, 182, 10) | s.rrect(36, 0, 264, 50, 12), (68, 70, 78))
    s.pocket(s.rrect(36, 34, 264, 160, 6))
    s.window(42, 40, 258, 154, 3)
    s.groove(20, 172, 280, 172, width=1.2)
    return s

def ds(three=False):
    s = Shell(262 if three else 250, 236)
    m = s.rrect(0, 0, 250, 236, 10)
    if three: m |= s.rrect(234, 24, 262, 104, 8)
    m &= ~s.rrect(-4, 14, 8, 50, 2)
    s.body(m, (64, 66, 72) if not three else (56, 58, 64))
    s.pocket(s.rrect(18, 16, 232, 192, 7))
    s.window(24, 22, 226, 186, 3)
    for y in (206, 214, 222): s.groove(60, y, 190, y, width=1.2)
    return s

def card(name, rgb, cut='right', W=172, H=250):
    s = Shell(W, H)
    c = 26
    if cut == 'right':
        m = s.poly([(10, 0), (W - c, 0), (W, c), (W, H - 10), (W - 10, H), (10, H), (0, H - 10), (0, 10)]) | s.rrect(0, 0, 30, 30, 10) | s.rrect(0, H - 30, W, H, 10)
    else:
        m = s.poly([(c, 0), (W - 10, 0), (W, 10), (W, H - 10), (W - 10, H), (10, H), (0, H - 10), (0, c)]) | s.rrect(W - 30, 0, W, 30, 10) | s.rrect(0, H - 30, W, H, 10)
    s.body(m, rgb, bevel=5)
    s.pocket(s.rrect(12, 12, W - 12, H - 46, 6), depth=0.25)
    s.window(16, 16, W - 16, H - 50, 3)
    for y in (H - 30, H - 22): s.groove(30, y, W - 30, y, width=1.1)
    return s

def hucard():
    s = Shell(160, 252)
    s.body(s.rrect(0, 0, 160, 252, 10), (214, 215, 220), bevel=3)
    s.window(10, 10, 150, 206, 6)
    for i in range(10):
        x = 18 + i * 13
        s.extra.append((s.rrect(x, 218, x + 9, 242, 1.5), (214, 176, 74), 1.0))
    return s

def black_cart(W=262, H=292):
    s = Shell(W, H)
    m = s.rrect(0, 0, W, H, 10) & ~s.rrect(W / 2 - 22, -10, W / 2 + 22, 7, 3)
    s.body(m, (34, 35, 40))
    s.pocket(s.rrect(22, 22, W - 22, H * 0.68, 6))
    s.window(28, 28, W - 28, H * 0.68 - 6, 3)
    for y in np.arange(H * 0.76, H - 14, 10): s.groove(36, y, W - 36, y, depth=0.3)
    return s

def game_gear():
    s = Shell(262, 196)
    s.body(s.rrect(0, 0, 262, 196, 10), (30, 31, 36))
    s.pocket(s.rrect(20, 16, 242, 144, 6))
    s.window(26, 22, 236, 138, 3)
    for y in (158, 168, 178): s.groove(50, y, 212, y)
    return s

def atari():
    s = Shell(262, 262)
    s.body(s.rrect(0, 0, 262, 262, 8), (28, 28, 30))
    for x in np.arange(22, 245, 13): s.groove(x, 10, x, 88, width=2.2, depth=0.4)
    s.pocket(s.rrect(18, 100, 244, 248, 5))
    s.window(24, 106, 238, 242, 2)
    return s

def umd():
    # The UMD as it really looks: a white-grey shell shaped like a guitar pick (flat top with round corners,
    # sides closing into a round bottom), a big clear round window with the disc behind it, "PSP" moulded along
    # the top and the little grey tab at the lower left. The game's label is the disc itself (a full circle).
    from scipy.spatial import ConvexHull
    W = H = 256
    s = Shell(W, H)
    cx, cy = W / 2, 134
    def pick(inset):
        pts = []
        for t in np.linspace(0, 2 * np.pi, 180, endpoint=False):
            pts.append((cx + (124 - inset) * np.cos(t), cy + (120 - inset) * np.sin(t)))
            for ox in (44, W - 44):
                pts.append((ox + (18 - inset) * np.cos(t), 20 + (18 - inset) * np.sin(t)))
        pts = np.array(pts); hull = ConvexHull(pts)
        return s.poly(pts[hull.vertices])
    s.body(pick(0), (214, 216, 220), bevel=7)
    s.height -= (pick(6) & ~pick(8)) * 0.18                               # the seam round the face
    win = s.circle(cx, cy, 106)
    s.pocket(s.circle(cx, cy, 110) & ~win, 0.3, 2.0)                       # the window's moulded lip
    # "PSP" moulded into the top edge
    from PIL import ImageDraw, ImageFont
    try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15 * SS)
    except Exception: font = ImageFont.load_default()
    txt = Image.new('L', (s.w, s.h), 0); d = ImageDraw.Draw(txt)
    d.text(((44 + s.pad) * SS, (6 + s.pad) * SS), 'PSP', fill=255, font=font)
    s.raise_(np.array(txt) > 128, 0.22, bevel=1.0)
    # the grey tab at the lower left
    tab = s.rrect(14, 166, 26, 184, 5)
    s.raise_(tab, -0.2, bevel=1.5, rgb=(112, 116, 126))
    # the clear rim of the window round the disc: tinted plastic over the disc's edge
    rim = win & ~s.circle(cx, cy, 101)
    s.window(cx - 106, cy - 106, cx + 106, cy + 106, 0, mask=win & ~rim)
    s.extra.append((rim, (60, 64, 72), 0.78))
    return s

SHELLS = {
    'n64': n64, 'snes': snes, 'nes': nes,
    'gb': lambda: gb('gb', (172, 172, 178)), 'gbc': lambda: gb('gbc', (44, 46, 54)), 'vboy': lambda: gb('vboy', (86, 86, 94)),
    'gba': gba, 'ds': ds, 'ds3': lambda: ds(True),
    'switch': lambda: card('switch', (46, 47, 53), 'right'), 'vita': lambda: card('vita', (24, 25, 28), 'left', 180, 244),
    'small_card': lambda: card('small_card', (52, 54, 62), 'right', 190, 250),
    'hucard': hucard, 'black_cart': black_cart, 'game_gear': game_gear, 'atari': atari, 'umd': umd,
}

if __name__ == '__main__':
    import sys
    meta = {}
    names = sys.argv[1:] or list(SHELLS)
    for n in names:
        meta[n] = SHELLS[n]().render(n)
        print(n, meta[n])
    json.dump(meta, open('out/meta.json', 'w'), indent=1)

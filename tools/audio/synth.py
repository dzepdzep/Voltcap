"""Small offline synth used to render Voltcap's intro cue and theme music loops.

Everything is additive/FM synthesis in numpy, with a convolution reverb built
from shaped noise. Output: stereo float arrays at SR.
"""
import numpy as np
from scipy import signal

SR = 44100
rng = np.random.default_rng(7)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12.0)


def note_num(name):
    names = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
    n = names[name[0]]
    i = 1
    while i < len(name) and name[i] in '#b':
        n += 1 if name[i] == '#' else -1
        i += 1
    octave = int(name[i:])
    return 12 * (octave + 1) + n


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def env_adsr(n, a, d, s, r_start, r, sr=SR):
    """Attack a, decay d to level s, release r beginning at r_start seconds (sample-based)."""
    t = np.arange(n) / sr
    e = np.where(t < a, t / max(a, 1e-4), s + (1 - s) * np.exp(-(t - a) / max(d, 1e-4)))
    rel = np.clip((t - r_start) / max(r, 1e-4), 0, 1)
    e = e * np.where(t > r_start, np.cos(rel * np.pi / 2) ** 2, 1.0)
    return e


class Mix:
    def __init__(self, seconds):
        self.buf = np.zeros((2, int(seconds * SR) + 1))

    def add(self, x, start_sec, pan=0.0, gain=1.0):
        """x mono or stereo; pan -1..1 (equal-power)."""
        i = int(round(start_sec * SR))
        if i >= self.buf.shape[1]:
            return
        if x.ndim == 1:
            l = np.cos((pan + 1) * np.pi / 4)
            r = np.sin((pan + 1) * np.pi / 4)
            x = np.vstack([x * l * 1.4142, x * r * 1.4142])
        j = min(self.buf.shape[1], i + x.shape[1])
        if i < 0:
            x = x[:, -i:]
            i = 0
        self.buf[:, i:j] += x[:, : j - i] * gain


# ---------------------------------------------------------------- instruments

def epiano(freq, dur, vel=1.0, bright=1.0, decay=None):
    """FM electric piano (Rhodes-like): 1:1 FM with decaying index plus a tine."""
    tail = 1.2
    t = t_axis(dur + tail)
    if decay is None:
        decay = np.clip(2.6 * (220 / freq) ** 0.5, 0.6, 3.5)
    idx = bright * (1.3 * vel + 0.4) * np.exp(-t / 0.35) + 0.25 * bright
    mod = np.sin(2 * np.pi * freq * t)
    car = np.sin(2 * np.pi * freq * t + idx * mod)
    tine_f = freq * 14.0
    tine = 0.0
    if tine_f < 15000:
        tine = 0.12 * bright * vel * np.sin(2 * np.pi * tine_f * t) * np.exp(-t / 0.018)
    amp = np.exp(-t / decay) * np.minimum(1, t / 0.003)
    rel = np.clip((t - dur) / 0.35, 0, 1)
    amp *= np.where(t > dur, np.cos(rel * np.pi / 2) ** 2, 1)
    # slight tremolo shimmer
    y = (car * amp + tine) * vel
    return y


def additive(freq, dur, harmonics, a=0.01, d=0.3, s=0.7, r=0.4, detune_cents=0.0, vib=0.0):
    t = t_axis(dur + r + 0.05)
    y = np.zeros_like(t)
    ph_mod = 0.0
    if vib > 0:
        ph_mod = vib * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.6)
    for k, amp in enumerate(harmonics, start=1):
        f = freq * k
        if f > 16000:
            break
        y += amp * np.sin(2 * np.pi * f * t * (1 + detune_cents / 1731.0) + k * ph_mod)
    return y * env_adsr(len(t), a, d, s, dur, r)


def pad(freq, dur, bright=0.5, attack=1.2, release=2.0, voices=4, width=0.8):
    """Warm detuned pad (additive saw-ish with rolled-off highs), stereo."""
    n_h = 10
    harm = [(1.0 / k) * np.exp(-(k - 1) * (1.2 - bright)) for k in range(1, n_h + 1)]
    t = t_axis(dur + release)
    out = np.zeros((2, len(t)))
    det = np.linspace(-9, 9, voices)
    for v, c in enumerate(det):
        f = freq * 2 ** (c / 1200)
        y = np.zeros_like(t)
        phase0 = rng.random() * 2 * np.pi
        for k, a in enumerate(harm, start=1):
            if f * k > 12000:
                break
            y += a * np.sin(2 * np.pi * f * k * t + phase0 * k)
        pan = (v / (voices - 1) * 2 - 1) * width if voices > 1 else 0
        out[0] += y * np.cos((pan + 1) * np.pi / 4)
        out[1] += y * np.sin((pan + 1) * np.pi / 4)
    e = env_adsr(len(t), attack, 1.0, 1.0, dur, release)
    # slow breathing
    e *= 0.85 + 0.15 * np.sin(2 * np.pi * 0.11 * t + rng.random() * 6)
    return out * e / voices


def bell(freq, dur=2.5, vel=1.0, kind='glass'):
    t = t_axis(dur)
    if kind == 'glass':
        parts = [(1.0, 1.0, 1.0), (2.756, 0.35, 0.45), (5.404, 0.14, 0.25), (8.933, 0.06, 0.12)]
    elif kind == 'music_box':
        parts = [(1.0, 1.0, 1.0), (3.0, 0.22, 0.3), (5.97, 0.1, 0.12), (11.2, 0.04, 0.05)]
    else:  # celesta-ish
        parts = [(1.0, 1.0, 1.0), (2.0, 0.3, 0.4), (4.0, 0.1, 0.2), (6.8, 0.04, 0.08)]
    base_dec = np.clip(1.6 * (880 / freq) ** 0.4, 0.4, 3.0)
    y = np.zeros_like(t)
    for ratio, amp, dk in parts:
        f = freq * ratio
        if f > 16000:
            continue
        y += amp * np.sin(2 * np.pi * f * t) * np.exp(-t / (base_dec * dk))
    y *= np.minimum(1, t / 0.002)
    return y * vel


def marimba(freq, vel=1.0):
    t = t_axis(1.6)
    y = (np.sin(2 * np.pi * freq * t) * np.exp(-t / 0.45)
         + 0.35 * np.sin(2 * np.pi * freq * 3.93 * t) * np.exp(-t / 0.08)
         + 0.08 * np.sin(2 * np.pi * freq * 9.23 * t) * np.exp(-t / 0.02))
    click = rng.standard_normal(len(t)) * np.exp(-t / 0.003) * 0.08
    y = y + click
    return y * np.minimum(1, t / 0.0015) * vel


def fm_pluck(freq, vel=1.0, ratio=2.0, index=2.5, decay=0.5):
    t = t_axis(decay * 5)
    idx = index * np.exp(-t / (decay * 0.35))
    y = np.sin(2 * np.pi * freq * t + idx * np.sin(2 * np.pi * freq * ratio * t))
    return y * np.exp(-t / decay) * np.minimum(1, t / 0.002) * vel


def square_pluck(freq, dur, vel=1.0, cutoff=2400, decay=0.25):
    """Synthwave arp: odd harmonics with a decaying 'filter' (harmonic rolloff over time)."""
    t = t_axis(dur + 0.3)
    y = np.zeros_like(t)
    for k in range(1, 30, 2):
        f = freq * k
        if f > 14000:
            break
        # time-varying lowpass: harmonic amplitude fades faster for higher k
        cut = cutoff * (0.25 + 0.75 * np.exp(-t / decay))
        amp = (1.0 / k) / (1 + (f / cut) ** 4)
        y += amp * np.sin(2 * np.pi * f * t)
    e = env_adsr(len(t), 0.002, decay, 0.25, dur, 0.12)
    return y * e * vel


def bass(freq, dur, vel=1.0, drive=1.3):
    t = t_axis(dur + 0.15)
    y = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(2 * np.pi * 2 * freq * t) + 0.08 * np.sin(2 * np.pi * 3 * freq * t)
    y = np.tanh(drive * y) / np.tanh(drive)
    e = env_adsr(len(t), 0.008, 0.4, 0.75, dur, 0.1)
    return y * e * vel


def flute(freq, dur, vel=1.0):
    t = t_axis(dur + 0.3)
    y = additive(freq, dur, [1, 0.28, 0.12, 0.05], a=0.09, d=0.4, s=0.85, r=0.25, vib=0.0025)
    breath = rng.standard_normal(len(y))
    b, a = signal.butter(2, [freq * 0.8 / (SR / 2), min(0.99, freq * 3 / (SR / 2))], btype='band')
    breath = signal.lfilter(b, a, breath) * 0.12
    e = env_adsr(len(y), 0.05, 0.3, 0.6, dur, 0.2)
    return (y + breath * e) * vel


# ---------------------------------------------------------------- drums

def kick(vel=1.0, f0=110, f1=44, dec=0.28):
    t = t_axis(0.6)
    f = f1 + (f0 - f1) * np.exp(-t / 0.035)
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = np.sin(ph) * np.exp(-t / dec)
    y += 0.25 * rng.standard_normal(len(t)) * np.exp(-t / 0.002)
    return np.tanh(1.5 * y) * vel


def snare(vel=1.0, tone=190, dec=0.14, brush=False):
    t = t_axis(0.5)
    n = rng.standard_normal(len(t))
    b, a = signal.butter(2, [1200 / (SR / 2), 9000 / (SR / 2)], btype='band')
    n = signal.lfilter(b, a, n)
    if brush:
        y = n * np.exp(-t / 0.09) * np.minimum(1, t / 0.012) * 0.8
    else:
        y = n * np.exp(-t / dec) + 0.5 * np.sin(2 * np.pi * tone * t) * np.exp(-t / 0.05)
    return y * vel


def hat(vel=1.0, dec=0.03):
    t = t_axis(0.25)
    n = rng.standard_normal(len(t))
    b, a = signal.butter(4, 7000 / (SR / 2), btype='high')
    return signal.lfilter(b, a, n) * np.exp(-t / dec) * vel


def shaker(vel=1.0):
    t = t_axis(0.12)
    n = rng.standard_normal(len(t))
    b, a = signal.butter(2, [5000 / (SR / 2), 12000 / (SR / 2)], btype='band')
    e = np.minimum(1, t / 0.015) * np.exp(-t / 0.035)
    return signal.lfilter(b, a, n) * e * vel


def noise_swell(sec, lo=300, hi=6000, curve=2.0):
    """Filtered noise whose band sweeps up and swells (riser)."""
    n = int(sec * SR)
    out = np.zeros(n)
    blocks = 64
    edges = np.linspace(0, n, blocks + 1).astype(int)
    white = rng.standard_normal(n)
    zi = None
    for bi in range(blocks):
        p = (bi + 0.5) / blocks
        fc = lo * (hi / lo) ** (p ** curve)
        b, a = signal.butter(2, min(0.99, fc / (SR / 2)), btype='low')
        seg = white[edges[bi]:edges[bi + 1]]
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        y, zi = signal.lfilter(b, a, seg, zi=zi)
        out[edges[bi]:edges[bi + 1]] = y
    t = np.arange(n) / n
    return out * (t ** curve)


# ---------------------------------------------------------------- effects

def make_ir(seconds=3.0, low_t60=2.8, mid_t60=2.2, high_t60=1.0, predelay=0.02, seed=3):
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = np.zeros((2, n))
    bands = [(None, 400, low_t60), (400, 3000, mid_t60), (3000, None, high_t60)]
    for ch in range(2):
        w = r.standard_normal(n)
        acc = np.zeros(n)
        for lo, hi, t60 in bands:
            if lo is None:
                b, a = signal.butter(2, hi / (SR / 2), 'low')
            elif hi is None:
                b, a = signal.butter(2, lo / (SR / 2), 'high')
            else:
                b, a = signal.butter(2, [lo / (SR / 2), hi / (SR / 2)], 'band')
            acc += signal.lfilter(b, a, w) * np.exp(-6.91 * t / t60)
        # soft onset (diffuse build-up)
        acc *= np.minimum(1, t / 0.03)
        ir[ch] = acc
    pd = int(predelay * SR)
    ir = np.pad(ir, ((0, 0), (pd, 0)))[:, :n]
    ir /= np.sqrt(np.sum(ir ** 2) / 2)
    return ir


_ir_cache = {}


def reverb(x, mix=0.3, **kw):
    key = tuple(sorted(kw.items()))
    if key not in _ir_cache:
        _ir_cache[key] = make_ir(**kw)
    ir = _ir_cache[key]
    if x.ndim == 1:
        x = np.vstack([x, x])
    wet = np.vstack([
        signal.fftconvolve(x[0], ir[0])[: x.shape[1]],
        signal.fftconvolve(x[1], ir[1])[: x.shape[1]],
    ])
    # highpass the wet a little so the low end stays clean
    b, a = signal.butter(2, 180 / (SR / 2), 'high')
    wet = signal.lfilter(b, a, wet, axis=1)
    return x * (1 - mix * 0.35) + wet * mix


def pingpong(x, delay_sec, fb=0.4, mix=0.3, repeats=6, lp=5000):
    if x.ndim == 1:
        x = np.vstack([x, x])
    d = int(delay_sec * SR)
    out = x.copy()
    b, a = signal.butter(1, lp / (SR / 2), 'low')
    cur = x.copy()
    for k in range(1, repeats + 1):
        cur = signal.lfilter(b, a, cur, axis=1) * fb
        shifted = np.zeros_like(x)
        if k * d < x.shape[1]:
            # alternate sides
            src = cur[::-1] if k % 2 == 1 else cur
            shifted[:, k * d:] = src[:, : x.shape[1] - k * d]
        out += shifted * mix
    return out


def lowpass(x, fc, order=2):
    b, a = signal.butter(order, min(0.99, fc / (SR / 2)), 'low')
    return signal.lfilter(b, a, x, axis=-1)


def highpass(x, fc, order=2):
    b, a = signal.butter(order, fc / (SR / 2), 'high')
    return signal.lfilter(b, a, x, axis=-1)


def master(x, target_rms_db=-19.0, peak_db=-1.0, drive=1.0, hp=True):
    if hp:
        x = highpass(x, 28)
    rms = np.sqrt(np.mean(x ** 2))
    x = x * (10 ** (target_rms_db / 20) / max(rms, 1e-9))
    # gentle soft clip above ~ -3 dB
    thr = 10 ** (-3 / 20)
    ax = np.abs(x)
    over = ax > thr
    x = np.where(over, np.sign(x) * (thr + (1 - thr) * np.tanh((ax - thr) / (1 - thr))), x)
    pk = np.max(np.abs(x))
    lim = 10 ** (peak_db / 20)
    if pk > lim:
        x *= lim / pk
    return x

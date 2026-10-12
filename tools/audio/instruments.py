"""More instruments for Voltcap's music and interface sounds (added 4 Oct 2026):
felt piano, choir, bowed strings, harp, church bell, crystal (FM), vibraphone,
fretless bass, water drops, wind, and a "32-bit sampler" treatment.

Everything is plain additive / FM synthesis in numpy, like synth.py, and every
function is deterministic (its own seeded random generator), so re-rendering
gives the same files."""
import numpy as np
from scipy import signal
from synth import SR, t_axis, env_adsr, lowpass, highpass


def _rng(*key):
    return np.random.default_rng(abs(hash(tuple(round(float(k), 4) for k in key))) % (2 ** 32))


def faded(x, frac=0.25, max_sec=0.5):
    """Fades the last part of a sound to silence, so cutting the buffer never clicks."""
    x = np.asarray(x, dtype=float)
    n = x.shape[-1]
    f = max(2, int(min(n * frac, max_sec * SR)))
    env = np.ones(n)
    env[-f:] = np.cos(np.linspace(0, np.pi / 2, f)) ** 2
    return x * env


def fbell(freq, dur=2.5, vel=1.0, kind='glass'):
    """synth.bell with its end faded (synth.bell stops while still ringing)."""
    from synth import bell
    return faded(bell(freq, dur, vel, kind))


def _release(t, dur, rel):
    r = np.clip((t - dur) / max(rel, 1e-4), 0, 1)
    return np.where(t > dur, np.cos(r * np.pi / 2) ** 2, 1.0)


def felt_piano(freq, dur, vel=0.7, soft=0.7, release=0.6, detune=0.8):
    """Soft felt piano: two slightly detuned strings, stretched partials, a
    quick 'prompt' decay into a long, quiet aftersound, a muffled hammer.
    dur = how long the key (or pedal) holds the note before the damper."""
    base = float(np.clip(4.2 * (261.6 / freq) ** 0.55, 1.0, 9.0))
    total = min(dur + release, base * 2.2 + release) + 0.05
    t = t_axis(total)
    y = np.zeros_like(t)
    B = 0.00032
    tilt = 1.25 + 0.9 * soft - 0.5 * vel
    for k in range(1, 13):
        fk = k * freq * np.sqrt(1 + B * k * k)
        if fk > 12000:
            break
        amp = 1.0 / k ** tilt
        dk = base / (1 + 0.55 * (k - 1))
        env = 0.55 * np.exp(-t / (0.22 * dk)) + 0.45 * np.exp(-t / dk)
        for s, c in ((0, -detune), (1, detune)):
            y += 0.5 * amp * env * np.sin(2 * np.pi * fk * (1 + c / 1731.0) * t + 0.7 * k * s)
    r = _rng(freq, dur, vel)
    hammer = r.standard_normal(len(t)) * np.exp(-t / 0.006) * 0.03 * vel
    hammer = lowpass(hammer, min(4000, freq * 3), order=2)
    y = y + hammer
    y *= np.minimum(1, t / 0.004)
    y *= _release(t, dur, release)
    return faded(y, 0.15, 0.4) * vel


_FORMANTS = {
    'a': [(730, 1.0, 90), (1090, 0.55, 100), (2440, 0.22, 140)],
    'o': [(570, 1.0, 80), (840, 0.45, 90), (2410, 0.1, 130)],
    'u': [(320, 1.0, 60), (800, 0.28, 80), (2240, 0.06, 120)],
    'e': [(530, 1.0, 80), (1840, 0.45, 110), (2480, 0.25, 140)],
}


def choir(freq, dur, vowel='a', attack=0.8, release=1.6, voices=4, spread=9.0, vib=0.0035,
          bright=1.0, width=0.8, seed=0):
    """A soft sampled-style choir ('aah', 'ooh'): saw-like voices shaped by vowel
    formants, each with its own vibrato and detune. Stereo."""
    t = t_axis(dur + release)
    out = np.zeros((2, len(t)))
    r = _rng(freq, dur, seed, ord(vowel))
    forms = _FORMANTS[vowel]
    for v in range(voices):
        cents = (v / max(1, voices - 1) * 2 - 1) * spread + r.uniform(-2, 2)
        f = freq * 2 ** (cents / 1200)
        rate = r.uniform(4.6, 5.6)
        ph = r.uniform(0, 2 * np.pi)
        vib_t = vib * np.sin(2 * np.pi * rate * t + ph) * np.minimum(1, t / 0.9)
        drift = 1 + 0.12 * np.sin(2 * np.pi * r.uniform(0.07, 0.2) * t + r.uniform(0, 6))
        pm = 2 * np.pi * f * np.cumsum(vib_t) / SR
        y = np.zeros_like(t)
        k = 1
        while f * k < 6500 * bright:
            fk = f * k
            g = 0.015
            for F, a, bw in forms:
                g += a / (1 + ((fk - F) / bw) ** 2)
            phase = 2 * np.pi * fk * t + k * pm + r.uniform(0, 6)
            y += (g / k ** 0.6) * np.sin(phase)
            k += 1
        y *= drift
        pan = (v / max(1, voices - 1) * 2 - 1) * width if voices > 1 else 0.0
        out[0] += y * np.cos((pan + 1) * np.pi / 4)
        out[1] += y * np.sin((pan + 1) * np.pi / 4)
    e = env_adsr(len(t), attack, 1.0, 1.0, dur, release)
    return out * e / voices * 0.5


def strings(freq, dur, attack=1.2, release=1.8, voices=3, bright=0.5, vib=0.003, width=0.7, seed=0):
    """Bowed string section: detuned saws with a soft top and a slow vibrato. Stereo."""
    t = t_axis(dur + release)
    out = np.zeros((2, len(t)))
    r = _rng(freq, dur, seed, 7)
    cutoff = 900 + 3200 * bright
    for v in range(voices):
        cents = (v / max(1, voices - 1) * 2 - 1) * 7 + r.uniform(-1.5, 1.5)
        f = freq * 2 ** (cents / 1200)
        vib_t = vib * np.sin(2 * np.pi * r.uniform(4.8, 5.8) * t + r.uniform(0, 6)) * np.minimum(1, t / 1.2)
        pm = 2 * np.pi * f * np.cumsum(vib_t) / SR
        y = np.zeros_like(t)
        k = 1
        while f * k < 9000:
            fk = f * k
            g = (1.0 / k) / (1 + (fk / cutoff) ** 2.2)
            y += g * np.sin(2 * np.pi * fk * t + k * pm + r.uniform(0, 6))
            k += 1
        pan = (v / max(1, voices - 1) * 2 - 1) * width if voices > 1 else 0.0
        out[0] += y * np.cos((pan + 1) * np.pi / 4)
        out[1] += y * np.sin((pan + 1) * np.pi / 4)
    e = env_adsr(len(t), attack, 1.0, 1.0, dur, release)
    return out * e / voices


def harp(freq, vel=0.8, ring=None, pos=0.13, dark=0.0):
    """Plucked harp string: pluck-position spectrum, high partials dying first."""
    base = ring or float(np.clip(3.4 * (220.0 / freq) ** 0.5, 0.7, 6.0))
    t = t_axis(base * 2.5)
    y = np.zeros_like(t)
    for k in range(1, 16):
        fk = k * freq * (1 + 0.00008 * k * k)
        if fk > 11000:
            break
        amp = abs(np.sin(np.pi * k * pos)) / k ** (1.0 + 0.6 * dark)
        dk = base / (1 + 0.9 * (k - 1))
        y += amp * np.sin(2 * np.pi * fk * t) * np.exp(-t / dk)
    y *= np.minimum(1, t / 0.0015)
    return faded(y, 0.3, 0.6) * vel


def church_bell(freq, vel=1.0, length=9.0):
    """A big, distant bell (minor-third tierce). freq = the strike note."""
    t = t_axis(length)
    parts = [(0.5, 0.55, 1.0), (1.0, 0.8, 0.7), (1.19, 0.55, 0.55), (1.5, 0.3, 0.42),
             (2.0, 0.55, 0.36), (2.52, 0.22, 0.26), (2.67, 0.16, 0.22), (3.01, 0.14, 0.16),
             (4.16, 0.08, 0.1), (5.43, 0.05, 0.07)]
    base = float(np.clip(7.0 * (220.0 / freq) ** 0.35, 2.5, 12.0))
    y = np.zeros_like(t)
    for ratio, amp, dk in parts:
        f = freq * ratio
        if f > 12000:
            continue
        beat = 1 + 0.004 * np.sin(2 * np.pi * 0.6 * ratio * t)
        y += amp * np.sin(2 * np.pi * f * t) * np.exp(-t / (base * dk)) * beat
    r = _rng(freq, 3)
    strike = lowpass(r.standard_normal(len(t)) * np.exp(-t / 0.01), 3500) * 0.15
    y = (y + strike) * np.minimum(1, t / 0.002)
    return faded(y, 0.35, 3.0) * vel


def crystal(freq, vel=0.8, decay=1.4, ratio=3.5, index=1.6):
    """Glassy FM bell with an inharmonic shimmer — 'crystal'."""
    t = t_axis(decay * 4)
    idx = index * np.exp(-t / (decay * 0.3)) + 0.25
    y = np.sin(2 * np.pi * freq * t + idx * np.sin(2 * np.pi * freq * ratio * t))
    y += 0.25 * np.sin(2 * np.pi * freq * 2.0 * t) * np.exp(-t / (decay * 0.4))
    return faded(y * np.exp(-t / decay) * np.minimum(1, t / 0.002) * vel, 0.25, 0.5)


def vibes(freq, vel=0.8, decay=1.6, tremolo=4.8):
    """Vibraphone: bar partials (1, 4, 10) plus a motor tremolo."""
    t = t_axis(decay * 3.5)
    y = (np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)
         + 0.22 * np.sin(2 * np.pi * freq * 3.99 * t) * np.exp(-t / (decay * 0.18))
         + 0.05 * np.sin(2 * np.pi * freq * 9.9 * t) * np.exp(-t / (decay * 0.05)))
    trem = 1 - 0.28 * (0.5 + 0.5 * np.sin(2 * np.pi * tremolo * t))
    return faded(y * trem * np.minimum(1, t / 0.0015) * vel, 0.25, 0.5)


def fretless(freq, dur, vel=0.8, slide=-40.0):
    """Fretless-ish bass: slides into the note, a soft 'mwah' as the tone opens."""
    t = t_axis(dur + 0.25)
    cents = slide * np.exp(-t / 0.05)
    f = freq * 2 ** (cents / 1200)
    ph = 2 * np.pi * np.cumsum(f) / SR
    open_ = 0.25 + 0.35 * (1 - np.exp(-t / 0.08)) * np.exp(-t / 0.6)
    y = np.sin(ph) + open_ * np.sin(2 * ph) + 0.4 * open_ ** 2 * np.sin(3 * ph)
    y = np.tanh(1.1 * y) / np.tanh(1.1)
    e = env_adsr(len(t), 0.012, 0.6, 0.7, dur, 0.18)
    return y * e * vel


def drop(freq, vel=0.6):
    """A water drop: a sine that bends up fast and dies (the 'plink')."""
    t = t_axis(0.35)
    f = freq * (1 + 0.6 * (1 - np.exp(-t / 0.018)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * np.exp(-t / 0.07) * np.minimum(1, t / 0.001) * vel


def wind(seconds, seed=1, lo=180, hi=900, rate=0.09):
    """Breathing wind: noise through a band that wanders slowly. Stereo."""
    n = int(seconds * SR)
    r = np.random.default_rng(seed)
    out = np.zeros((2, n))
    blocks = max(8, int(seconds * 12))
    edges = np.linspace(0, n, blocks + 1).astype(int)
    for ch in range(2):
        white = r.standard_normal(n)
        zi = None
        ph = r.uniform(0, 6)
        for bi in range(blocks):
            p = (edges[bi] + edges[bi + 1]) / 2 / SR
            fc = lo * (hi / lo) ** (0.5 + 0.5 * np.sin(2 * np.pi * rate * p + ph + ch))
            b, a = signal.butter(2, [fc * 0.6 / (SR / 2), min(0.99, fc * 1.6 / (SR / 2))], btype='band')
            seg = white[edges[bi]:edges[bi + 1]]
            if zi is None:
                zi = np.zeros(max(len(a), len(b)) - 1)
            yseg, zi = signal.lfilter(b, a, seg, zi=zi)
            out[ch, edges[bi]:edges[bi + 1]] = yseg
    tt = np.arange(n) / SR
    breath = 0.55 + 0.45 * np.sin(2 * np.pi * rate * 0.7 * tt) ** 2
    return out * breath


def sampled(x, rate=22050, lp=9500):
    """'32-bit sampler' treatment: the sound is held at a lower sample rate (the
    grainy edge of short console samples), then softened."""
    x = np.asarray(x, dtype=float)
    n = x.shape[-1]
    idx = (np.floor(np.arange(n) * rate / SR) * SR / rate).astype(int)
    idx = np.clip(idx, 0, n - 1)
    y = x[..., idx]
    return lowpass(y, lp, order=2)


def air(seconds, seed=2, level=1.0, lo=2500, hi=9000):
    """Very soft high 'air' hiss that swells slowly. Stereo."""
    n = int(seconds * SR)
    r = np.random.default_rng(seed)
    w = r.standard_normal((2, n))
    b, a = signal.butter(2, [lo / (SR / 2), hi / (SR / 2)], btype='band')
    w = signal.lfilter(b, a, w, axis=1)
    tt = np.arange(n) / SR
    return w * (0.6 + 0.4 * np.sin(2 * np.pi * 0.05 * tt) ** 2) * level

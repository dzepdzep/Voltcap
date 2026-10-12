"""Navigation sound sets for Voltcap (4 Oct 2026), one per new music mood:

  chill    soft and airy, a calm console home screen (goes with Home / Haze)
  retro32  'sampled' late-90s console blips with a short metallic room (goes with Memory card · 32-bit)
  dark     harp, crystal and a distant bell (goes with Hollow · dark fantasy)

Each set: move, select, back, open, launch -> out/ui_<set>_<sound>.ogg (stereo,
44.1 kHz). Loudness is matched to the in-app Console set (its sounds are
synthesised in UiSounds.kt; ported below only to measure them), so switching sets
never jumps in volume. The move sound is the softest: it plays on every step.

python ui_sounds.py
"""
import os
import subprocess
import numpy as np
from synth import *
from instruments import *
import soundfile_writer as sw


# ---------------------------------------------------------------- reference: the Console set (UiSounds.kt)

def _k_pluck(freq, sec, decay, level=1.0, glide=None):
    glide = glide or freq
    n = int(SR * sec)
    t = np.arange(n) / SR
    f = freq + (glide - freq) * np.minimum(1, t / sec)
    ph = np.cumsum(2 * np.pi * f / SR)
    return (np.sin(ph) + 0.18 * np.sin(2 * ph)) * np.minimum(1, t / 0.004) * np.exp(-t / decay) * level


def _k_room(dry, delay, gain, repeats):
    step = int(delay * SR)
    out = np.zeros(len(dry) + step * repeats)
    g = 1.0
    for r in range(repeats + 1):
        out[r * step:r * step + len(dry)] += dry * g
        g *= gain
    return out


def _k_layer(sec, *parts):
    out = np.zeros(int(SR * sec))
    for st, p in parts:
        o = int(st * SR)
        m = min(len(p), len(out) - o)
        out[o:o + m] += p[:m]
    return out


def _k_norm(x):
    return x * 0.85 / np.abs(x).max()


def console_ref():
    return {
        'move': _k_norm(_k_room(_k_pluck(1175, 0.06, 0.016), 0.028, 0.22, 2)),
        'select': _k_norm(_k_room(_k_layer(0.3, (0, _k_pluck(660, 0.25, 0.07)), (0, _k_pluck(990, 0.25, 0.06, 0.6)),
                                           (0.045, _k_pluck(1320, 0.22, 0.06, 0.35))), 0.05, 0.25, 3)),
        'back': _k_norm(_k_room(_k_layer(0.25, (0, _k_pluck(587, 0.22, 0.06, glide=540)), (0, _k_pluck(880, 0.18, 0.04, 0.3))),
                                0.05, 0.22, 3)),
    }


def short_loudness(x):
    """Loudest 60 ms (K-ish weighted RMS) — how loud a click 'feels'."""
    m = x.mean(axis=0) if x.ndim == 2 else x
    b, a = signal.butter(2, 150 / (SR / 2), 'high')
    m = signal.lfilter(b, a, m)
    w = int(0.06 * SR)
    e = np.convolve(m ** 2, np.ones(w) / w, mode='valid')
    return 10 * np.log10(e.max() + 1e-12)


# ---------------------------------------------------------------- helpers

def plus(a, b):
    """a + b for sounds of different lengths."""
    n = max(a.shape[-1], b.shape[-1])
    pa = np.pad(a, [(0, 0)] * (a.ndim - 1) + [(0, n - a.shape[-1])])
    pb = np.pad(b, [(0, 0)] * (b.ndim - 1) + [(0, n - b.shape[-1])])
    return pa + pb


class FadeMix(Mix):
    """Every part gets its end faded before it's mixed in (no clicks from cut tails)."""
    def add(self, x, start_sec, pan=0.0, gain=1.0):
        super().add(faded(x, 0.2, 0.25), start_sec, pan=pan, gain=gain)


def canvas(sec):
    return FadeMix(sec)


def finish(x, sec, fade=0.12):
    x = x[:, :int(sec * SR)]
    n = x.shape[1]
    f = int(fade * SR)
    env = np.ones(n)
    env[-f:] = np.cos(np.linspace(0, np.pi / 2, f)) ** 2
    x = x * env
    x = highpass(x, 60)
    return x


def room(x, mix, seconds, t60=None, bright=0.5, predelay=0.008):
    t60 = t60 or seconds * 0.8
    return reverb(x, mix=mix, seconds=seconds, low_t60=t60 * 1.1, mid_t60=t60, high_t60=t60 * (0.35 + 0.5 * bright),
                  predelay=predelay)


def sine_pluck(freq, sec, decay, level=1.0, h2=0.12, attack=0.003, glide=None):
    t = t_axis(sec)
    f = freq if glide is None else freq + (glide - freq) * (1 - np.exp(-t / (sec * 0.3)))
    ph = 2 * np.pi * np.cumsum(np.ones_like(t) * f) / SR
    return (np.sin(ph) + h2 * np.sin(2 * ph)) * np.minimum(1, t / attack) * np.exp(-t / decay) * level


def pulse(freq, sec, duty=0.3, decay=0.05, level=1.0, glide=None):
    t = t_axis(sec)
    f = freq if glide is None else freq + (glide - freq) * (1 - np.exp(-t / 0.02))
    ph = np.cumsum(np.ones_like(t) * f) / SR % 1.0
    y = np.where(ph < duty, 1.0, -1.0)
    y = lowpass(y, 5000, order=1)
    return y * np.exp(-t / decay) * np.minimum(1, t / 0.0015) * level


def whoosh(sec, lo=500, hi=3500, level=1.0, seed=3, reverse=False):
    x = noise_swell(sec, lo=lo, hi=hi, curve=1.6)
    t = np.arange(len(x)) / len(x)
    env = np.sin(np.pi * t) ** 1.5 if not reverse else t ** 2.2 * np.minimum(1, (1 - t) / 0.06)
    x = x / (np.abs(x).max() + 1e-9) * env
    return x * level


# ---------------------------------------------------------------- chill

def chill():
    s = {}
    m = canvas(0.6)
    m.add(sine_pluck(880, 0.12, 0.022, h2=0.1, attack=0.004, glide=905), 0.0)
    m.add(sine_pluck(880 * 2.76, 0.05, 0.008, level=0.06), 0.0)
    s['move'] = finish(room(lowpass(m.buf, 5000), 0.3, 0.9, t60=0.6, bright=0.4), 0.55)

    m = canvas(1.6)
    for i, (f, pn, lv) in enumerate(((N2('E6'), -0.2, 1.0), (N2('B6'), 0.2, 0.7))):
        m.add(felt_piano(f, 0.25, vel=0.55, soft=0.4) * lv, i * 0.045, pan=pn)
        m.add(fbell(f * 2, 0.9, 0.18, 'glass'), i * 0.045 + 0.01, pan=-pn)
    m.add(sine_pluck(N2('E5'), 0.6, 0.18, level=0.25), 0.0)
    s['select'] = finish(room(m.buf, 0.42, 1.8, t60=1.4, bright=0.5), 1.5)

    m = canvas(1.3)
    for i, (f, pn) in enumerate(((N2('B5'), 0.2), (N2('E5'), -0.2))):
        m.add(felt_piano(f, 0.2, vel=0.5, soft=0.7) * (1.0 - 0.15 * i), i * 0.05, pan=pn)
    s['back'] = finish(room(lowpass(m.buf, 4500), 0.38, 1.5, t60=1.1, bright=0.35), 1.2)

    m = canvas(1.6)
    w = whoosh(0.32, lo=600, hi=4200, level=0.22)
    m.add(np.vstack([w, np.roll(w, 97)]), 0.0)
    for n, pn in (('E5', -0.4), ('B5', 0.4), ('D#6', 0.0)):
        m.add(additive(N2(n), 0.18, [1, 0.15], a=0.12, d=0.3, s=0.6, r=0.4) * 0.22, 0.04, pan=pn)
    m.add(felt_piano(N2('G#6'), 0.3, vel=0.5, soft=0.4) * 0.8, 0.16, pan=0.15)
    m.add(fbell(N2('B6'), 1.0, 0.2, 'glass'), 0.2, pan=-0.3)
    s['open'] = finish(room(m.buf, 0.45, 2.0, t60=1.6, bright=0.5), 1.5)

    m = canvas(3.4)
    chord = sum(pad(N2(n), 0.7, bright=0.45, attack=0.35, release=1.8, width=1.0) for n in ('E3', 'B3', 'E4', 'G#4', 'B4', 'D#5'))
    m.add(chord * 0.5, 0.0)
    m.add(additive(N2('E2'), 0.8, [1, 0.2], a=0.3, d=0.6, s=0.8, r=1.2) * 0.35, 0.0)
    w = whoosh(0.45, lo=300, hi=3000, level=0.15)
    m.add(np.vstack([w, np.roll(w, 131)]), 0.0)
    for k, n in enumerate(('B5', 'E6', 'G#6', 'B6', 'D#7', 'E7')):
        m.add(fbell(N2(n), 1.4, 0.4 - 0.03 * k, 'glass') * 0.5, 0.2 + k * 0.055, pan=-0.6 + 0.24 * k)
    m.add(felt_piano(N2('E5'), 1.2, vel=0.55, soft=0.5) * 0.6, 0.2, pan=0.0)
    s['launch'] = finish(room(m.buf, 0.5, 3.0, t60=2.4, bright=0.5), 3.3, fade=0.5)
    return s


def N2(name):
    return hz(note_num(name))


# ---------------------------------------------------------------- 32-bit

def spu_room(x, mix=0.3, seconds=1.2):
    """A short, slightly metallic room: a diffuse tail plus a few early echoes."""
    y = room(x, mix, seconds, t60=seconds * 0.75, bright=0.3, predelay=0.012)
    for d, g in ((0.031, 0.18), (0.047, 0.12), (0.071, 0.08)):
        k = int(d * SR)
        y[:, k:] += x[:, :-k] * g * mix * 2 if x.shape[1] > k else 0
    return y


def retro32():
    s = {}
    crunch = lambda x, rate=11025, lp=7000: sampled(x, rate=rate, lp=lp)
    m = canvas(0.4)
    m.add(crunch(plus(pulse(N2('G6'), 0.09, duty=0.4, decay=0.024, glide=N2('G6') * 0.97), 0.55 * pulse(N2('G5'), 0.08, duty=0.5, decay=0.022))), 0.0)
    s['move'] = finish(spu_room(m.buf, 0.18, 0.5), 0.35)

    m = canvas(1.2)
    for i, (n, pn) in enumerate((('C6', -0.25), ('G6', 0.25))):
        x = plus(vibes(N2(n), 0.8, decay=0.35, tremolo=0.01), 0.5 * epiano(N2(n), 0.15, vel=0.6, bright=1.0)[:int(0.8 * SR)])
        m.add(crunch(x, rate=22050, lp=8500), i * 0.06, pan=pn)
    m.add(crunch(pulse(N2('C7'), 0.08, duty=0.25, decay=0.02, level=0.25)), 0.06)
    s['select'] = finish(spu_room(m.buf, 0.32, 1.2), 1.1)

    m = canvas(1.0)
    for i, (n, pn) in enumerate((('G5', 0.25), ('C5', -0.25))):
        x = plus(vibes(N2(n), 0.75, decay=0.3, tremolo=0.01), 0.4 * epiano(N2(n), 0.12, vel=0.5, bright=0.7)[:int(0.7 * SR)])
        m.add(crunch(x, rate=22050, lp=7000), i * 0.065, pan=pn)
    s['back'] = finish(spu_room(m.buf, 0.3, 1.0), 0.9)

    m = canvas(1.2)
    w = whoosh(0.2, lo=1500, hi=9000, level=0.35, reverse=True)
    m.add(crunch(np.vstack([w, np.roll(w, 53)]), rate=16000, lp=8000), 0.0)
    m.add(crunch(plus(fbell(N2('E6'), 0.9, 0.6, 'celesta'), 0.5 * fbell(N2('B6'), 0.9, 0.4, 'celesta')), rate=22050, lp=9000), 0.18, pan=0.1)
    s['open'] = finish(spu_room(m.buf, 0.32, 1.2), 1.1)

    m = canvas(2.6)
    for n in ('C4', 'E4', 'G4', 'C5'):
        m.add(crunch(choir(N2(n), 0.6, vowel='a', attack=0.12, release=1.2, voices=3, spread=6, seed=4), rate=22050, lp=8000)
              * 0.55, 0.05)
    m.add(crunch(additive(N2('C2'), 0.5, [1, 0.3, 0.1], a=0.01, d=0.3, s=0.5, r=0.6), rate=11025, lp=3000) * 0.45, 0.0)
    for k, n in enumerate(('C6', 'E6', 'G6', 'C7', 'E7')):
        m.add(crunch(vibes(N2(n), 0.6, decay=0.6, tremolo=4.0), rate=22050, lp=9000) * 0.45, 0.05 + k * 0.065,
              pan=-0.5 + 0.25 * k)
    s['launch'] = finish(spu_room(m.buf, 0.38, 1.8), 2.5, fade=0.4)
    for k in s:
        s[k] = lowpass(s[k], 10000, order=2)
    return s


# ---------------------------------------------------------------- dark fantasy

def dark():
    s = {}
    m = canvas(0.7)
    m.add(harp(N2('A4'), 0.9, ring=0.22, dark=0.8, pos=0.2), 0.0)
    m.add(harp(N2('A3'), 0.25, ring=0.15, dark=1.0, pos=0.2), 0.0)
    s['move'] = finish(room(lowpass(m.buf, 3800), 0.25, 1.2, t60=0.8, bright=0.2), 0.65)

    m = canvas(1.9)
    m.add(harp(N2('D5'), 0.8, ring=0.6, dark=0.4), 0.0, pan=-0.2)
    m.add(harp(N2('A5'), 0.65, ring=0.55, dark=0.4), 0.04, pan=0.2)
    m.add(crystal(N2('D6'), 0.35, decay=0.7), 0.06, pan=0.05)
    m.add(strings(N2('D3'), 0.25, attack=0.05, release=0.5, bright=0.2) * 0.35, 0.0)
    s['select'] = finish(room(lowpass(m.buf, 6000), 0.4, 2.2, t60=1.6, bright=0.25), 1.8)

    m = canvas(1.4)
    m.add(harp(N2('A4'), 0.75, ring=0.45, dark=0.6), 0.0, pan=0.2)
    m.add(harp(N2('D4'), 0.75, ring=0.5, dark=0.6), 0.06, pan=-0.2)
    t = t_axis(0.25)
    thud = np.sin(2 * np.pi * np.cumsum(70 + 60 * np.exp(-t / 0.03)) / SR) * np.exp(-t / 0.06)
    m.add(thud * 0.25, 0.0)
    s['back'] = finish(room(lowpass(m.buf, 4500), 0.38, 1.8, t60=1.3, bright=0.2), 1.3)

    m = canvas(2.0)
    w = whoosh(0.4, lo=200, hi=1800, level=0.3)
    m.add(np.vstack([w, np.roll(w, 211)]), 0.0)
    for k, n in enumerate(('D4', 'F4', 'A4', 'D5')):
        m.add(harp(N2(n), 0.55 + 0.08 * k, ring=0.6, dark=0.5), 0.12 + k * 0.03, pan=-0.45 + 0.3 * k)
    m.add(church_bell(N2('D5'), 0.5, length=1.8) * 0.5, 0.2, pan=0.0)
    s['open'] = finish(room(lowpass(m.buf, 5500), 0.45, 2.5, t60=1.9, bright=0.25), 1.9)

    m = canvas(3.6)
    m.add(church_bell(N2('D4'), 1.0, length=3.5) * 0.7, 0.0)
    for n in ('D4', 'F4', 'A4', 'D5'):
        m.add(choir(N2(n), 0.9, vowel='a', attack=0.35, release=1.8, voices=4, spread=7, seed=7) * 0.9, 0.08)
    m.add(choir(N2('D3'), 0.9, vowel='u', attack=0.3, release=1.6, voices=3, seed=8) * 0.8, 0.08)
    t = t_axis(1.2)
    boom = np.sin(2 * np.pi * np.cumsum(42 + 50 * np.exp(-t / 0.05)) / SR) * np.exp(-t / 0.35)
    m.add(boom * 0.6, 0.0)
    for k, n in enumerate(('A4', 'D5', 'F5', 'A5', 'D6')):
        m.add(harp(N2(n), 0.5, ring=0.9, dark=0.4), 0.15 + k * 0.05, pan=-0.5 + 0.25 * k)
    s['launch'] = finish(room(lowpass(m.buf, 6000), 0.5, 3.4, t60=2.6, bright=0.25), 3.5, fade=0.6)
    return s


# ---------------------------------------------------------------- level matching & export

# How loud each set sits next to the Console set (dB): the chill set a little softer.
SET_TRIM = {'chill': -2.0, 'retro32': -0.5, 'dark': -1.5}
# The move sound plays on every step: softer than the rest. Launch may be a bit fuller.
SOUND_TRIM = {'move': -3.0, 'select': 0.0, 'back': -1.0, 'open': -0.5, 'launch': 0.5}


def export(name, sounds, ref):
    os.makedirs('out', exist_ok=True)
    for k, x in sounds.items():
        target = short_loudness(ref['select']) + SET_TRIM[name] + SOUND_TRIM[k]
        g = 10 ** ((target - short_loudness(x)) / 20)
        x = x * g
        pk = np.abs(x).max()
        if pk > 0.89:  # never above -1 dBFS
            x *= 0.89 / pk
        path = f'out/ui_{name}_{k}'
        sw.write_wav(path + '.wav', x)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path + '.wav', '-c:a', 'libvorbis', '-q:a', '5', path + '.ogg'],
                       check=True)
        print(f'{name:8s} {k:7s} {x.shape[1] / SR:4.2f}s short {short_loudness(x):6.1f} dB peak {20 * np.log10(np.abs(x).max()):5.1f}')


if __name__ == '__main__':
    ref = console_ref()
    for k, x in ref.items():
        print(f'console  {k:7s} short {short_loudness(x):6.1f} dB')
    export('chill', chill(), ref)
    export('retro32', retro32(), ref)
    export('dark', dark(), ref)

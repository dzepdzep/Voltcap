"""Voltcap intro cue, timed to VoltcapIntro.kt (FULL): sparks trace 0.18-1.05 s,
hit at 1.05 s, letters 1.28 s + i*0.055, shine 1.9-2.4 s, fade 2.3-2.7 s."""
import numpy as np
from synth import *

HIT = 1.05
LEN = 4.0
mix = Mix(LEN)
dry_fx = Mix(LEN)   # goes through the big reverb
dry = Mix(LEN)      # stays dry

# --- 1. the charge-up: two sparks racing (soft rising tones L/R), crackle, noise riser
t0 = 0.18
dur = HIT - t0
t = t_axis(dur)
p = t / dur
ease = np.where(p < 0.5, 4 * p ** 3, 1 - (-2 * p + 2) ** 3 / 2)
for side, det in ((-0.55, 1.0), (0.55, 1.012)):
    f = (380 + 1100 * ease) * det
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = (np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)) * (0.15 + 0.85 * p ** 1.5) * np.minimum(1, t / 0.08)
    dry_fx.add(tone * 0.08, t0, pan=side)
# crackle: tiny bright clicks, getting denser
r = np.random.default_rng(11)
n_clicks = 70
times = t0 + dur * np.sort(r.random(n_clicks) ** 0.6)
for tc in times:
    c = r.standard_normal(int(0.004 * SR)) * np.exp(-np.arange(int(0.004 * SR)) / (0.0008 * SR))
    c = highpass(c, 3000)
    dry_fx.add(c * 0.25 * (0.3 + 0.7 * (tc - t0) / dur), tc, pan=r.uniform(-0.7, 0.7))
riser = noise_swell(dur + 0.02, lo=250, hi=9000, curve=2.2)
dry_fx.add(np.vstack([riser, np.roll(riser, 211)]) * 0.15, t0)

# --- 2. the hit: sub drop, bright A-add9 chord (e-piano + pad + glass bells), a zap
dry.add(kick(1.0, f0=130, f1=38, dec=0.45) * 0.9, HIT)
chord = ['A2', 'E3', 'A3', 'C#4', 'E4', 'B4']
for i, n in enumerate(chord):
    dry_fx.add(epiano(hz(note_num(n)), 1.2, vel=0.75, bright=1.1) * 0.22, HIT + 0.004 * i, pan=(i - 2.5) / 4)
padc = sum(pad(hz(note_num(n)), 1.35, bright=0.75, attack=0.02, release=1.6) for n in ['A3', 'E4', 'C#5', 'E5'])
dry_fx.add(padc * 0.30, HIT)
for n, pn in (('E6', -0.3), ('A6', 0.3), ('C#7', 0.0)):
    dry_fx.add(bell(hz(note_num(n)), 2.6, 0.5, 'glass') * 0.10, HIT + 0.01, pan=pn)
# zap: short bright buzz sweeping down
zt = t_axis(0.09)
zf = 180 + 2400 * np.exp(-zt / 0.018)
zph = 2 * np.pi * np.cumsum(zf) / SR
zap = (np.sign(np.sin(zph)) * 0.5 + r.standard_normal(len(zt)) * 0.5) * np.exp(-zt / 0.03)
dry_fx.add(lowpass(zap, 6000) * 0.16, HIT - 0.005)

# --- 3. the letters: seven quick plucked bells rising (A major pentatonic)
letters = ['A5', 'B5', 'C#6', 'E6', 'F#6', 'A6', 'B6']
for i, n in enumerate(letters):
    tt = 1.28 + i * 0.055
    dry_fx.add(fm_pluck(hz(note_num(n)), 0.55, ratio=3.0, index=1.2, decay=0.35) * 0.075, tt, pan=-0.6 + i * 0.2)
    dry_fx.add(bell(hz(note_num(n)), 1.2, 0.35, 'music_box') * 0.05, tt, pan=-0.6 + i * 0.2)

# --- 4. the shine: a sparkling glissando of tiny chimes + the chord settling (Aadd9 -> Esus)
for k in range(12):
    tt = 1.92 + k * 0.038
    n = note_num('E6') + [0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24, 26][k]
    dry_fx.add(bell(hz(n), 0.8, 0.25, 'celesta') * 0.045, tt, pan=-0.8 + k * 0.14)
settle = sum(pad(hz(note_num(n)), 1.1, bright=0.45, attack=0.5, release=1.4) for n in ['A2', 'E3', 'B3', 'E4', 'F#4', 'A4'])
dry_fx.add(settle * 0.22, 1.75)
dry.add(bass(hz(note_num('A1')), 1.3, 0.8) * 0.22, HIT)

wet = reverb(dry_fx.buf, mix=0.55, seconds=3.2, low_t60=2.6, mid_t60=2.3, high_t60=1.2, predelay=0.018)
out = wet + dry.buf
# fade the very end
fade = np.ones(out.shape[1])
fe = int(0.6 * SR)
fade[-fe:] = np.cos(np.linspace(0, np.pi / 2, fe)) ** 2
out *= fade
out = master(out, target_rms_db=-17.5, peak_db=-1.0)

import soundfile_writer as sw
sw.write_wav('out/voltcap_intro.wav', out)
print('peak', np.max(np.abs(out)), 'rms dB', 20 * np.log10(np.sqrt(np.mean(out ** 2))))

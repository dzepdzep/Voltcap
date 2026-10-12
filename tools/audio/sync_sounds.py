"""Voltcap's save-sync cues, in the same voice as the intro (glass bells, warm
e-piano, soft room). Short, gentle, never louder than a menu sound.

sync_done   - saves are safe: a quick rising sparkle that settles on a bright fifth (~1 s)
sync_alert  - something needs you: two soft falling marimba notes, a little lower (~0.9 s)
"""
import numpy as np
from synth import *
import soundfile_writer as sw


def done():
    m = Mix(1.6)
    fx = Mix(1.6)
    # three-note lift (E6 -> G#6 -> B6) and a soft chord bloom underneath
    for i, (n, pn) in enumerate((('E6', -0.35), ('G#6', 0.0), ('B6', 0.35))):
        fx.add(bell(hz(note_num(n)), 1.3, 0.55 + 0.15 * i, 'glass') * 0.22, 0.0 + i * 0.07, pan=pn)
    for n in ('E4', 'B4', 'E5'):
        fx.add(epiano(hz(note_num(n)), 0.5, vel=0.45, bright=0.6) * 0.12, 0.14)
    # a tiny shimmer on top
    for k in range(6):
        fx.add(bell(hz(note_num('E7') + [0, 4, 7, 12, 7, 4][k]), 0.5, 0.25, 'celesta') * 0.035, 0.2 + k * 0.03, pan=-0.6 + 0.24 * k)
    out = reverb(fx.buf, mix=0.42, seconds=1.8, low_t60=1.4, mid_t60=1.2, high_t60=0.7, predelay=0.012) + m.buf
    return out


def alert():
    fx = Mix(1.4)
    for i, (n, pn) in enumerate((('C#5', -0.2), ('A4', 0.2))):
        fx.add(marimba(hz(note_num(n)), 0.8) * 0.3, i * 0.16, pan=pn)
        fx.add(bell(hz(note_num(n) + 12), 0.9, 0.3, 'music_box') * 0.08, i * 0.16, pan=pn)
    fx.add(epiano(hz(note_num('A3')), 0.45, vel=0.4, bright=0.5) * 0.12, 0.16)
    return reverb(fx.buf, mix=0.35, seconds=1.6, low_t60=1.2, mid_t60=1.0, high_t60=0.6, predelay=0.01)


def finish(x, rms_db):
    n = x.shape[1]
    fade = np.ones(n)
    fe = int(0.25 * SR)
    fade[-fe:] = np.cos(np.linspace(0, np.pi / 2, fe)) ** 2
    return master(x * fade, target_rms_db=rms_db, peak_db=-3.0)


if __name__ == '__main__':
    sw.write_wav('out/sync_done.wav', finish(done(), -22.0))
    sw.write_wav('out/sync_alert.wav', finish(alert(), -22.0))
    print('ok')

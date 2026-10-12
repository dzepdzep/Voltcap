"""Four more theme music loops for Voltcap (4 Oct 2026), same rules as music.py:
rendered twice back to back through the effects, the second pass kept, so every
reverb / delay tail runs over the loop point and the loop is seamless. No
percussion.

  home    Home · console chill   — a calm, PlayStation-style home screen: felt piano, wide pads, glass sparkles
  haze    Haze · 128-bit dream   — misty, watery and a little mysterious: crystal arps, 'ooh' voices, drops
  memory  Memory card · 32-bit   — a late-90s console menu: sampled e-piano, 'aah' choir, fretless bass, vibes
  hollow  Hollow · dark fantasy  — a cold ruin at night: harp, low strings, distant choir and bell, wind

python music_more.py [home haze memory hollow]  ->  out/bgm_<name>.ogg
"""
import sys
import subprocess
import numpy as np
from synth import *
from instruments import *
import soundfile_writer as sw
from music import Song, pad_bar

N = note_num


class MSong(Song):
    def render(self, verb_mix=0.35, verb=None, delay_time=None, delay_fb=0.35, delay_mix=0.3,
               rms_db=-21.0, pad_verb=0.5, lp=None, eq=(-4.0, 4.0), post=None, pad_delay=False):
        verb = verb or dict(seconds=3.2, low_t60=2.8, mid_t60=2.3, high_t60=1.1, predelay=0.02)
        b = self.buses
        dly = b['delay'].buf
        if delay_time:
            dly = pingpong(dly, delay_time, fb=delay_fb, mix=delay_mix, repeats=8)
        wet = reverb(b['verb'].buf + dly, mix=verb_mix, **verb)
        pads = reverb(b['pad'].buf, mix=pad_verb, **verb)
        out = wet + pads + b['dry'].buf
        if lp:
            out = lowpass(out, lp, order=1)
        lo_g, hi_g = 10 ** (eq[0] / 20), 10 ** (eq[1] / 20)
        out = out + (lo_g - 1) * lowpass(out, 160) + (hi_g - 1) * highpass(out, 2800)
        out = highpass(out, 28)
        if post:
            out = post(out)  # over the whole render, before the slice: no seam
        i0 = int(round(self.L * SR))
        i1 = i0 + int(round(self.L * SR))
        return master(out[:, i0:i1], target_rms_db=rms_db, peak_db=-1.5, hp=False)

    def overlap(self, bus, make, hop_bars, gain=1.0):
        """Adds a long texture as windowed segments (2 hops long, every hop): the
        sin^2 windows add up to a constant, so there's no seam anywhere."""
        hop = hop_bars * 4 * self.beat
        seg = make(2 * hop)
        n = seg.shape[-1]
        w = np.sin(np.pi * np.arange(n) / n) ** 2
        for bar in range(0, self.bars, hop_bars):
            self.add(bus, seg * w, bar, 0, gain=gain)


def C(*names):
    return [N(n) for n in names]


def humanize(r, ms=12):
    return r.uniform(-ms, ms) / 1000.0


# ------------------------------------------------------------------ home

def theme_home():
    """Home · console chill. E major with lydian colour, 66 bpm, 16 bars (~58 s):
    soft felt piano over wide pads, a few glass sparkles, a low hum, air."""
    s = MSong(66, 16)
    chords = [  # one per 2 bars: bass, voicing
        ('E2', C('B2', 'G#3', 'D#4', 'F#4', 'B4')),     # Emaj9
        ('C#2', C('G#2', 'E3', 'B3', 'D#4', 'F#4')),    # C#m9(11)
        ('A1', C('E2', 'C#3', 'G#3', 'B3', 'D#4')),     # Amaj9#11
        ('D#2', C('F#2', 'B2', 'F#3', 'C#4', 'F#4')),   # Badd9/D#
        ('C#2', C('E3', 'G#3', 'B3', 'D#4')),           # C#m9
        ('A1', C('E3', 'G#3', 'C#4', 'E4')),            # Amaj7
        ('F#1', C('C#3', 'E3', 'A3', 'B3', 'G#4')),     # F#m11
        ('B1', C('F#2', 'E3', 'A3', 'C#4')),            # Bsus4 -> B7 in bar 15
    ]
    r = np.random.default_rng(66)
    for ci, (bass_n, v) in enumerate(chords):
        bar = ci * 2
        root = N(bass_n)
        # pads: a warm synth bed and a very quiet string layer, 2 bars each (the last chord: Bsus4, then B7)
        if ci == 7:
            pad_bar(s, bar, (root, v), bars=1, gain=0.085, bright=0.32, attack=1.8, release=2.0, octave=0, width=1.0)
            pad_bar(s, bar + 1, (root, C('F#2', 'D#3', 'A3', 'C#4')), bars=1, gain=0.085, bright=0.32, attack=1.2,
                    release=3.2, octave=0, width=1.0)
        else:
            pad_bar(s, bar, (root, v), bars=2, gain=0.085, bright=0.32, attack=1.8, release=3.2, octave=0, width=1.0)
        s.add('pad', strings(hz(v[-1] + 12), 8 * s.beat, attack=2.0, release=3.0, bright=0.25), bar, 0, gain=0.035)
        # low hum
        s.add('dry', bass(hz(root), 7.6 * s.beat, 0.5, drive=1.0), bar, 0, gain=0.13)
        # felt piano: a slow broken chord, held with the pedal to the next chord
        low = root + 12 if root < N('C2') else root
        end = bar * 4 + 8
        pattern = [(0, 0.0, low), (0, 1.0, v[1]), (0, 1.5, v[2]), (0, 2.5, v[3]),
                   (1, 0.5, v[2]), (1, 2.0, v[-1]), (1, 3.0, v[3])]
        if ci == 7:  # the last bar turns Bsus4 into B7 (D#)
            pattern[-2] = (1, 2.0, N('D#4'))
            pattern[-1] = (1, 3.0, N('F#4'))
        for db, beat, n in pattern:
            at = (bar + db) * 4 + beat
            hold = (end - at) * s.beat + 0.4
            s.add('verb', felt_piano(hz(n), hold, vel=r.uniform(0.32, 0.45), soft=0.9), bar + db, beat + humanize(r) / s.beat,
                  pan=(n - 60) / 40, gain=0.16)
        # glass sparkles, far away
        for k in range(2):
            if r.random() < 0.6:
                n = int(r.choice(v[-3:])) + 24
                s.add('delay', fbell(hz(n), 2.6, r.uniform(0.3, 0.55), 'glass'), bar + r.integers(0, 2),
                      float(r.choice([0.5, 1.5, 2.5, 3.5])), pan=r.uniform(-0.8, 0.8), gain=0.022)
    mel = [(0, 1, 'B4', .5), (0, 1.5, 'D#5', .5), (0, 2, 'F#5', 2),
           (1, 1.5, 'E5', .5), (1, 2, 'D#5', .5), (1, 2.5, 'B4', 1.5),
           (2, 1, 'G#5', 1), (2, 2, 'F#5', .5), (2, 2.5, 'E5', 1.5),
           (3, 2, 'D#5', 2),
           (4, 1, 'C#5', .5), (4, 1.5, 'E5', .5), (4, 2, 'G#5', 1), (4, 3, 'D#5', 1),
           (5, 1, 'E5', 3),
           (6, 1, 'B4', .5), (6, 1.5, 'C#5', .5), (6, 2, 'F#5', 2),
           (7, 1.5, 'E5', .5), (7, 2, 'D#5', 2),
           (8, 0.5, 'E5', .5), (8, 1, 'G#5', 1.5), (8, 2.5, 'B5', 1.5),
           (9, 1, 'D#5', .5), (9, 1.5, 'E5', .5), (9, 2, 'G#5', 2),
           (10, 1, 'C#6', 1), (10, 2, 'B5', .5), (10, 2.5, 'G#5', 1.5),
           (11, 2, 'E5', 2),
           (12, 1, 'A5', 1), (12, 2, 'G#5', .5), (12, 2.5, 'E5', 1.5),
           (13, 1.5, 'C#5', .5), (13, 2, 'B4', 2),
           (14, 1, 'C#5', .5), (14, 1.5, 'E5', .5), (14, 2, 'F#5', 2),
           (15, 1, 'F#5', 1), (15, 2, 'D#5', 2)]
    for bar, beat, n, ln in mel:
        vel = 0.5 + 0.1 * (beat in (1, 2)) + r.uniform(-0.04, 0.04)
        s.add('delay', felt_piano(hz(N(n)), ln * s.beat * 1.7 + 0.3, vel=vel, soft=0.75), bar, beat + humanize(r, 8) / s.beat,
              pan=0.12, gain=0.2)
    s.overlap('verb', lambda sec: air(sec, seed=5, lo=2000, hi=6000), 4, gain=0.005)
    return s.render(verb_mix=0.5, delay_time=0.75 * s.beat, delay_fb=0.38, delay_mix=0.24,
                    rms_db=-22.0, pad_verb=0.65, eq=(-4.0, 2.0), lp=9000,
                    verb=dict(seconds=4.6, low_t60=4.0, mid_t60=3.4, high_t60=1.7, predelay=0.03))


# ------------------------------------------------------------------ haze

def theme_haze():
    """Haze · 128-bit dream. F# minor / D lydian, 72 bpm, 16 bars (~53 s):
    'ooh' voices and a slow pad, crystal arpeggios in a ping-pong delay, water
    drops, a glassy lead with long notes, a deep sub."""
    s = MSong(72, 16)
    chords = [
        ('D2', C('A2', 'F#3', 'C#4', 'E4', 'G#4')),     # Dmaj9#11
        ('F#1', C('C#3', 'A3', 'E4', 'G#4')),           # F#m9
        ('B1', C('F#2', 'D3', 'A3', 'E4')),             # Bm11
        ('C#2', C('G#2', 'F#3', 'B3', 'D#4')),          # C#9sus4
        ('D2', C('F#3', 'A3', 'C#4', 'E4', 'G#4')),     # Dmaj9#11
        ('E2', C('B2', 'G#3', 'F#4', 'B4')),            # Eadd9
        ('F#1', C('E3', 'A3', 'C#4', 'G#4')),           # F#m9
        ('C#2', C('G#2', 'F#3', 'B3', 'D#4')),          # C#9sus4
    ]
    r = np.random.default_rng(72)
    for ci, (bass_n, v) in enumerate(chords):
        bar = ci * 2
        root = N(bass_n)
        pad_bar(s, bar, (root, v), bars=2, gain=0.07, bright=0.22 + 0.15 * (ci % 2), attack=2.2, release=3.4,
                octave=0, width=1.0)
        top = v[-2:]
        for n in top:
            s.add('pad', choir(hz(n), 7.6 * s.beat, vowel='u', attack=2.0, release=2.6, voices=4, seed=ci),
                  bar, 0, gain=0.06)
        s.add('dry', additive(hz(root), 7.6 * s.beat, [1, 0.12], a=0.6, d=1.0, s=0.9, r=1.2), bar, 0, gain=0.12)
        # crystal arpeggio on 8ths (some left out), drifting across the stereo field
        tones = sorted(set(v[1:] + [n + 12 for n in v[1:]]))
        seq = [0, 2, 4, 3, 5, 4, 2, 1]
        for db in range(2):
            notes = list(tones)
            for e in range(8):
                if r.random() < (0.35 if db == 0 else 0.5):
                    continue
                n = notes[seq[e] % len(notes)] + 12
                s.add('delay', crystal(hz(n), r.uniform(0.35, 0.6), decay=1.2), bar + db, e * 0.5,
                      pan=np.sin((bar + db) * 1.3 + e * 0.7) * 0.7, gain=0.022)
        # a drop of water now and then
        for db in range(2):
            if r.random() < 0.45:
                n = int(r.choice([N('F#5'), N('A5'), N('C#6'), N('E6'), N('F#6')]))
                s.add('verb', drop(hz(n), r.uniform(0.4, 0.7)), bar + db, float(r.choice([0.75, 1.25, 2.75, 3.25])),
                      pan=r.uniform(-0.9, 0.9), gain=0.03)
    lead = [(0, 2, 'C#6', 2), (1, 0, 'A5', 1.5), (1, 1.5, 'G#5', .5), (1, 2, 'E5', 2),
            (2, 1, 'A5', 1), (2, 2, 'C#6', 2), (3, 0, 'B5', 3),
            (4, 2, 'D6', 1), (4, 3, 'C#6', 1), (5, 0, 'A5', 3),
            (6, 1, 'G#5', 1), (6, 2, 'B5', 2), (7, 0, 'G#5', 3),
            (8, 2, 'C#6', 1), (8, 3, 'E6', 1), (9, 0, 'F#6', 2), (9, 2, 'E6', 2),
            (10, 1, 'B5', 1), (10, 2, 'G#5', 2), (11, 0, 'F#5', 3),
            (12, 2, 'A5', 1), (12, 3, 'G#5', 1), (13, 0, 'E5', 3),
            (14, 1, 'F#5', 1), (14, 2, 'G#5', 2), (15, 0, 'F#5', 2), (15, 2, 'D#5', 2)]
    for bar, beat, n, ln in lead:
        x = additive(hz(N(n)), ln * s.beat, [1, 0.14, 0.05, 0.02], a=0.22, d=0.8, s=0.75, r=0.9, vib=0.003)
        s.add('delay', x, bar, beat, pan=-0.1, gain=0.05)
    s.overlap('verb', lambda sec: air(sec, seed=9, lo=1800, hi=6000), 4, gain=0.005)
    return s.render(verb_mix=0.55, delay_time=0.75 * s.beat, delay_fb=0.46, delay_mix=0.34,
                    rms_db=-22.0, pad_verb=0.7, eq=(-4.0, 3.0), lp=10000,
                    verb=dict(seconds=5.0, low_t60=4.4, mid_t60=3.8, high_t60=2.0, predelay=0.04))


# ------------------------------------------------------------------ memory (32-bit)

def theme_memory():
    """Memory card · 32-bit. D-flat major, 84 bpm, 16 bars (~46 s): the sound of a
    late-90s console menu — everything 'sampled' (held at a lower sample rate),
    a warm e-piano, an 'aah' choir, a fretless bass, a vibraphone tune, and a
    short, slightly metallic reverb."""
    s = MSong(84, 16, swing=0.54)
    prog = [
        ('Db2', C('F3', 'Ab3', 'C4', 'Eb4')),      # Dbmaj9
        ('F2', C('Ab3', 'C4', 'Eb4', 'G4')),       # Fm9
        ('Gb2', C('Bb3', 'Db4', 'F4', 'Ab4')),     # Gbmaj9
        ('Ab2', C('Gb3', 'Bb3', 'Db4', 'F4')),     # Ab13sus
        ('Db2', C('Ab3', 'C4', 'Eb4', 'F4')),      # Dbmaj9
        ('Bb1', C('Db3', 'F3', 'Ab3', 'C4')),      # Bbm9
        ('Eb2', C('Gb3', 'Bb3', 'Db4', 'F4')),     # Ebm9
        ('Ab2', C('Gb3', 'Bb3', 'Db4', 'Eb4')),    # Ab7sus
        ('Gb2', C('F3', 'Bb3', 'Db4', 'Ab4')),     # Gbmaj9
        ('F2', C('Ab3', 'C4', 'Eb4')),             # Fm7
        ('Eb2', C('Db3', 'Gb3', 'Bb3', 'F4')),     # Ebm9
        ('Db2', C('C4', 'Eb4', 'F4', 'Ab4')),      # Dbmaj9
        ('Bb1', C('Ab3', 'Db4', 'F4', 'C5')),      # Bbm9
        ('Eb2', C('Gb3', 'Bb3', 'Db4', 'F4')),     # Ebm9
        ('Gb2', C('Bb3', 'Db4', 'F4', 'Ab4')),     # Gbmaj9
        ('Ab2', C('Gb3', 'Bb3', 'Db4', 'F4')),     # Ab13sus
    ]
    r = np.random.default_rng(84)
    smp = lambda x, rate=22050: sampled(x, rate=rate, lp=9000)
    for bar, (bass_n, v) in enumerate(prog):
        root = N(bass_n)
        # e-piano comping, a little syncopated
        for beat, ln, vf in [(0, 1.2, 1.0), (1.5, 0.8, 0.65), (2.75, 1.1, 0.8)]:
            if bar % 4 == 3 and beat == 2.75:
                continue
            for i, n in enumerate(v):
                x = smp(epiano(hz(n), ln * s.beat, vel=0.5 * vf, bright=0.55))
                s.add('verb', x, bar, beat + 0.01 * i / s.beat, pan=(i - 1.5) * 0.2, gain=0.1)
        # 'aah' choir, one per bar, soft
        for n in v[1:3]:
            s.add('pad', smp(choir(hz(n + 12), 3.6 * s.beat, vowel='a', attack=0.5, release=1.0, voices=3,
                                   spread=6, bright=0.8, seed=bar)), bar, 0, gain=0.07)
        # sampled string pad underneath
        s.add('pad', smp(strings(hz(v[0]), 3.8 * s.beat, attack=0.6, release=1.0, bright=0.35)), bar, 0, gain=0.035)
        # fretless bass
        s.add('dry', smp(fretless(hz(root), 1.6 * s.beat, 0.85)), bar, 0, gain=0.2)
        s.add('dry', smp(fretless(hz(root + 7), 0.9 * s.beat, 0.6, slide=-25)), bar, 2.5, gain=0.15)
        if bar % 2 == 1:
            s.add('dry', smp(fretless(hz(root + 12), 0.45 * s.beat, 0.5, slide=-60)), bar, 3.5, gain=0.12)
    mel = [(0, 0, 'F5', 1), (0, 1, 'Ab5', .5), (0, 1.5, 'C6', 1.5), (0, 3, 'Bb5', 1),
           (1, 0.5, 'Ab5', 1.5), (1, 2, 'Eb5', 1), (1, 3, 'F5', 1),
           (2, 0, 'Bb5', 1.5), (2, 1.5, 'Ab5', .5), (2, 2, 'F5', 1), (2, 3, 'Db5', 1),
           (3, 0, 'Eb5', 3),
           (4, 0, 'Ab5', .5), (4, 0.5, 'C6', .5), (4, 1, 'Eb6', 2), (4, 3, 'Db6', 1),
           (5, 0, 'C6', 1.5), (5, 1.5, 'Bb5', .5), (5, 2, 'F5', 2),
           (6, 0.5, 'Gb5', .5), (6, 1, 'F5', .5), (6, 1.5, 'Eb5', 1.5), (6, 3, 'Db5', 1),
           (7, 0, 'Eb5', 2), (7, 2.5, 'Gb5', .5), (7, 3, 'Ab5', 1),
           (8, 0, 'Bb5', 2), (8, 2, 'Ab5', .5), (8, 2.5, 'F5', 1.5),
           (9, 0.5, 'Ab5', 1), (9, 1.5, 'C6', 2.5),
           (10, 0, 'Bb5', 1), (10, 1, 'Gb5', 1), (10, 2, 'F5', 2),
           (11, 0.5, 'F5', 3.5),
           (12, 0, 'Db6', 1), (12, 1, 'C6', .5), (12, 1.5, 'Bb5', 1.5), (12, 3, 'Ab5', 1),
           (13, 0, 'Gb5', 1.5), (13, 1.5, 'F5', .5), (13, 2, 'Eb5', 2),
           (14, 0, 'F5', 1), (14, 1, 'Ab5', 1), (14, 2, 'Db6', 2),
           (15, 0, 'Bb5', 1.5), (15, 1.5, 'Ab5', .5), (15, 2, 'Eb5', 2)]
    for bar, beat, n, ln in mel:
        x = smp(vibes(hz(N(n)), 0.55 + 0.15 * (beat == int(beat)), decay=1.3), rate=18900)
        s.add('delay', x, bar, beat, pan=0.15, gain=0.085)
    # a little music-box run into the turnarounds
    for bar in (7, 15):
        for k, n in enumerate(C('Ab5', 'Bb5', 'Db6', 'Eb6', 'F6', 'Ab6')):
            s.add('delay', smp(fbell(hz(n), 1.2, 0.35, 'music_box'), rate=16000), bar, 2 + k * 0.25,
                  pan=-0.5 + k * 0.2, gain=0.025)

    def post(x):
        # the whole mix through a 22 kHz-era output: soft top, nothing above ~11 kHz
        return lowpass(x, 11000, order=4)

    return s.render(verb_mix=0.36, delay_time=0.75 * s.beat, delay_fb=0.35, delay_mix=0.28,
                    rms_db=-21.0, pad_verb=0.45, eq=(-3.5, 2.0), post=post,
                    verb=dict(seconds=2.6, low_t60=2.0, mid_t60=1.7, high_t60=0.8, predelay=0.012))


# ------------------------------------------------------------------ hollow (dark fantasy)

def theme_hollow():
    """Hollow · dark fantasy. D minor (a dark E-flat from Phrygian), 58 bpm,
    16 bars (~66 s): harp arpeggios, low strings and a D drone, a distant choir,
    a far bell, a lonely wooden flute, cold wind."""
    s = MSong(58, 16)
    bars = [  # one chord per bar: bass, voicing
        ('D2', C('A2', 'D3', 'F3', 'E4')), ('D2', C('A2', 'D3', 'F3', 'E4')),         # Dm(add9)
        ('Bb1', C('F2', 'D3', 'A3', 'E4')), ('Bb1', C('F2', 'D3', 'A3', 'E4')),       # Bbmaj7#11
        ('G1', C('D3', 'G3', 'Bb3', 'E4')), ('G1', C('D3', 'G3', 'Bb3', 'E4')),       # Gm6
        ('A1', C('E3', 'A3', 'D4')), ('A1', C('E3', 'A3', 'C#4')),                    # Asus4 -> A
        ('D2', C('A2', 'F3', 'D4', 'A4')), ('D2', C('A2', 'F3', 'D4', 'A4')),         # Dm
        ('Eb2', C('Bb2', 'G3', 'D4', 'G4')), ('Eb2', C('Bb2', 'G3', 'D4', 'G4')),     # Ebmaj7
        ('Bb1', C('D3', 'G3', 'Bb3', 'D4')), ('Bb1', C('D3', 'G3', 'Bb3', 'D4')),     # Gm/Bb
        ('A1', C('E3', 'G3', 'Bb3', 'D4')), ('A1', C('E3', 'G3', 'C#4', 'E4')),       # A7sus4(b9) -> A7
    ]
    r = np.random.default_rng(58)
    # the drone: low D, always there
    s.overlap('pad', lambda sec: strings(hz(N('D2')), sec - 0.5, attack=2.5, release=0.4, bright=0.12, voices=3)
              + strings(hz(N('D1')), sec - 0.5, attack=2.5, release=0.4, bright=0.1, voices=2), 4, gain=0.07)
    for bar, (bass_n, v) in enumerate(bars):
        root = N(bass_n)
        first = bar == 0 or bars[bar - 1] != bars[bar]
        span = 2 if (bar + 1 < 16 and bars[bar + 1] == bars[bar]) else 1
        if first:
            s.add('pad', strings(hz(v[0]), span * 4 * s.beat, attack=1.6, release=2.6, bright=0.22, seed=bar)
                  + strings(hz(v[1]), span * 4 * s.beat, attack=1.8, release=2.6, bright=0.22, seed=bar + 1), bar, 0, gain=0.05)
            s.add('dry', additive(hz(root), span * 4 * s.beat - 0.2, [1, 0.3, 0.08], a=0.5, d=1.0, s=0.85, r=1.0), bar, 0, gain=0.1)
            if bar >= 8:  # the choir comes in for the second half
                for n in v[-2:]:
                    s.add('pad', choir(hz(n - 12 if n > N('G4') else n), span * 4 * s.beat, vowel='u', attack=1.8,
                                       release=2.4, voices=4, spread=7, seed=bar), bar, 0, gain=0.07)
            if bar in (10,):  # a high 'aah' lifts the E-flat
                s.add('pad', choir(hz(N('G4')), 8 * s.beat, vowel='a', attack=2.2, release=3.0, voices=5, seed=99),
                      bar, 0, gain=0.035)
        # harp: up through the chord on 8ths, quieter and sparser on the chord's 2nd bar
        tones = sorted(set(v + [n + 12 for n in v]))
        eighths = [0, 1, 2, 3, 4, 5, 6] if first else [0, 2, 3, 5]
        order = [0, 1, 2, 3, 4, 5, 3]
        for k, e in enumerate(eighths):
            n = tones[order[e % len(order)] % len(tones)]
            s.add('verb', harp(hz(n), r.uniform(0.45, 0.62) * (1.0 if first else 0.8), dark=0.3), bar, e * 0.5,
                  pan=-0.4 + 0.12 * e, gain=0.12)
    # a far bell at the top of each half
    s.add('verb', church_bell(hz(N('D4')), 0.6, length=10.0), 0, 0, pan=-0.3, gain=0.035)
    s.add('verb', church_bell(hz(N('A3')), 0.55, length=10.0), 8, 0, pan=0.3, gain=0.03)
    mel = [(1, 2, 'A4', 1), (1, 3, 'C5', 1),
           (2, 0, 'D5', 3), (2, 3, 'E5', 1),
           (3, 0, 'F5', 2), (3, 2, 'E5', 1), (3, 3, 'D5', 1),
           (4, 0, 'E5', 2.5), (4, 2.5, 'D5', .5), (4, 3, 'Bb4', 1),
           (5, 0, 'A4', 4),
           (6, 2, 'D5', 1), (6, 3, 'E5', 1),
           (7, 0, 'C#5', 3.5),
           (9, 2, 'F5', 1), (9, 3, 'A5', 1),
           (10, 0, 'G5', 2), (10, 2, 'F5', 1), (10, 3, 'Eb5', 1),
           (11, 0, 'D5', 4),
           (12, 0, 'Bb4', 1), (12, 1, 'D5', 1), (12, 2, 'G5', 2),
           (13, 0, 'F5', 1), (13, 1, 'D5', 1), (13, 2, 'Bb4', 2),
           (14, 0, 'A4', 2), (14, 2, 'Bb4', 1), (14, 3, 'G4', 1),
           (15, 0, 'A4', 2), (15, 2, 'C#5', 2)]
    for bar, beat, n, ln in mel:
        x = lowpass(flute(hz(N(n)), ln * s.beat, 0.6), 2600)
        s.add('delay', x, bar, beat, pan=0.12, gain=0.06)
    s.overlap('dry', lambda sec: wind(sec, seed=13, lo=160, hi=700), 4, gain=0.02)
    return s.render(verb_mix=0.55, delay_time=1.0 * s.beat, delay_fb=0.35, delay_mix=0.25,
                    rms_db=-22.0, pad_verb=0.6, eq=(-3.0, 1.5), lp=8500,
                    verb=dict(seconds=5.5, low_t60=5.0, mid_t60=4.0, high_t60=1.5, predelay=0.05))


THEMES = {'home': theme_home, 'haze': theme_haze, 'memory': theme_memory, 'hollow': theme_hollow}

if __name__ == '__main__':
    import os
    os.makedirs('out', exist_ok=True)
    for name in sys.argv[1:] or list(THEMES):
        x = THEMES[name]()
        sw.write_wav(f'out/bgm_{name}.wav', x)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'out/bgm_{name}.wav', '-c:a', 'libvorbis', '-q:a', '4',
                        f'out/bgm_{name}.ogg'], check=False)
        print(name, 'seconds', round(x.shape[1] / SR, 2), 'peak', round(float(np.max(np.abs(x))), 3))

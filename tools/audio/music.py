"""Theme music loops for Voltcap. Each theme: tempo, chord progression, a
handful of parts. Rendered twice back to back through the effects and the
second pass kept, so reverb/delay tails wrap and the loop is seamless.

No percussion (2026-10-01): background music should stay out of the way, so
kicks, snares, hats, shakers and pulses are left out of every theme. Set
PERCUSSION = True to bring the old drum parts back."""
import sys
import numpy as np
from synth import *
import soundfile_writer as sw

N = note_num

# Drums / shakers / heartbeat pulses in the themes (off: calmer, no beat to fight a game's sound).
PERCUSSION = False


def chord(bass, *notes):
    return (N(bass), [N(n) for n in notes])


class Song:
    def __init__(self, bpm, bars, swing=0.5):
        self.bpm = bpm
        self.beat = 60.0 / bpm
        self.bars = bars
        self.swing = swing
        self.L = bars * 4 * self.beat
        self.tail = 5.0
        total = 2 * self.L + self.tail
        self.buses = {k: Mix(total) for k in ('dry', 'verb', 'delay', 'drums', 'pad')}
        self.kicks = []

    def t(self, bar, beat):
        """Seconds for bar (0-based) and beat (0-based, may be fractional) with swing on 8ths."""
        whole = int(beat)
        frac = beat - whole
        if abs(frac - 0.5) < 1e-6:
            frac = self.swing
        return (bar * 4 + whole + frac) * self.beat

    def add(self, bus, x, bar, beat, pan=0.0, gain=1.0):
        if bus == 'drums' and not PERCUSSION:
            return
        for p in (0, 1):
            self.buses[bus].add(x, self.t(bar, beat) + p * self.L, pan=pan, gain=gain)

    def kick(self, bar, beat, x, gain=1.0):
        if not PERCUSSION:
            return
        self.add('drums', x, bar, beat, gain=gain)
        for p in (0, 1):
            self.kicks.append(self.t(bar, beat) + p * self.L)

    def render(self, verb_mix=0.35, verb=None, delay_time=None, delay_fb=0.35, delay_mix=0.3,
               duck=0.0, rms_db=-20.0, drum_verb=0.12, pad_verb=0.5, lp=None, eq=(-4.0, 4.0)):
        verb = verb or dict(seconds=3.2, low_t60=2.8, mid_t60=2.3, high_t60=1.1, predelay=0.02)
        b = self.buses
        dly = b['delay'].buf
        if delay_time:
            dly = pingpong(dly, delay_time, fb=delay_fb, mix=delay_mix, repeats=7)
        send = b['verb'].buf + dly
        wet = reverb(send, mix=verb_mix, **verb)
        pads = reverb(b['pad'].buf, mix=pad_verb, **verb)
        drums = reverb(b['drums'].buf, mix=drum_verb, **verb)
        music = wet + pads + b['dry'].buf
        if duck > 0 and self.kicks:
            n = music.shape[1]
            g = np.ones(n)
            env_len = int(0.28 * SR)
            shape = 1 - duck * np.exp(-np.arange(env_len) / (0.07 * SR))
            for k in self.kicks:
                i = int(k * SR)
                j = min(n, i + env_len)
                if i < n:
                    g[i:j] = np.minimum(g[i:j], shape[: j - i])
            music = music * g
        out = music + drums
        if lp:
            out = lowpass(out, lp, order=1)
        # Tone: less boom (handheld speakers can't play it anyway), a bit more sparkle.
        # Filtered over the whole render (not the slice), so no filter start-up at the loop point.
        lo_g, hi_g = 10 ** (eq[0] / 20), 10 ** (eq[1] / 20)
        out = out + (lo_g - 1) * lowpass(out, 160) + (hi_g - 1) * highpass(out, 2800)
        out = highpass(out, 28)
        i0 = int(round(self.L * SR))
        i1 = i0 + int(round(self.L * SR))
        loop = out[:, i0:i1]
        return master(loop, target_rms_db=rms_db, peak_db=-1.5, hp=False)


def comp_epiano(s, bar, ch, pattern, vel=0.6, bright=0.8, gain=0.16, bus='verb'):
    """Chord stabs on the given beats: list of (beat, length_in_beats, velocity factor)."""
    for beat, ln, vf in pattern:
        for i, n in enumerate(ch[1]):
            s.add(bus, epiano(hz(n), ln * s.beat, vel=vel * vf, bright=bright), bar, beat + 0.008 * i / s.beat,
                  pan=(i - (len(ch[1]) - 1) / 2) * 0.18, gain=gain)


def pad_bar(s, bar, ch, bars=1, gain=0.12, bright=0.45, attack=1.0, release=2.2, octave=12, width=0.8):
    notes = ch[1] + ([n + octave for n in ch[1][-2:]] if octave else [])
    x = sum(pad(hz(n), bars * 4 * s.beat, bright=bright, attack=attack, release=release, width=width) for n in notes)
    s.add('pad', x, bar, 0, gain=gain / max(1, len(notes)) * 4)


def melody(s, notes, inst, bus='delay', gain=0.1, pan=0.0):
    """notes: (bar, beat, name_or_midi, length_beats[, vel])."""
    for item in notes:
        bar, beat, n, ln = item[:4]
        vel = item[4] if len(item) > 4 else 1.0
        m = N(n) if isinstance(n, str) else n
        s.add(bus, inst(hz(m), ln * s.beat, vel), bar, beat, pan=pan, gain=gain)


def motif_melody(prog, scale_pcs, seed, register=(N('A4'), N('E6')), density=0.55, bars=None):
    """Motif-based generator: a 2-bar rhythm/contour motif repeated over the
    progression, snapped to chord tones on strong beats, scale tones elsewhere;
    variation on the 2nd and 4th repeat."""
    r = np.random.default_rng(seed)
    bars = bars or len(prog)
    # rhythm: 8th grid over 2 bars
    grid = [i * 0.5 for i in range(16)]
    rhythm = []
    for g in grid:
        strong = g % 2 == 0
        if r.random() < (density + (0.25 if strong else -0.1)):
            rhythm.append(g)
    if not rhythm:
        rhythm = [0, 2, 4.5]
    contour = np.cumsum(r.choice([-2, -1, -1, 1, 1, 2, 0], size=len(rhythm)))
    lo, hi = register
    center = (lo + hi) // 2
    out = []
    scale = [p for p in range(lo, hi + 1) if p % 12 in scale_pcs]
    for rep in range(bars // 2):
        vary = rep % 2 == 1
        base_bar = rep * 2
        prev = None
        for k, g in enumerate(rhythm):
            if vary and r.random() < 0.3:
                continue
            bar = base_bar + int(g // 4)
            beat = g % 4
            ch = prog[bar % len(prog)]
            target = center + contour[k] * 2 + (3 if vary else 0)
            strong = beat in (0, 2)
            cands = [p for p in scale if (p % 12) in {n % 12 for n in ch[1]}] if strong else scale
            if not cands:
                cands = scale
            p = min(cands, key=lambda c: abs(c - target) + (0.5 if prev is not None and abs(c - prev) > 7 else 0))
            nxt = rhythm[k + 1] if k + 1 < len(rhythm) else 8
            ln = max(0.5, min(2.0, (nxt - g) if nxt > g else 1.0))
            out.append((bar, beat, p, ln, 0.75 + 0.25 * strong))
            prev = p
        # let each 2-bar phrase end on a long chord tone
    return out


def drums_lofi(s, bars, kick_gain=0.5, snare_gain=0.25, hat_gain=0.06, brush=True):
    for bar in range(bars):
        s.kick(bar, 0, kick(0.9, f0=95, f1=45, dec=0.3), gain=kick_gain)
        s.kick(bar, 2.5, kick(0.7, f0=95, f1=45, dec=0.25), gain=kick_gain * 0.7)
        s.add('drums', snare(0.8, brush=brush), bar, 1, gain=snare_gain)
        s.add('drums', snare(0.8, brush=brush), bar, 3, gain=snare_gain)
        for e in range(8):
            s.add('drums', hat(0.6 + 0.4 * (e % 2 == 0), dec=0.025), bar, e * 0.5, pan=0.25, gain=hat_gain)


# ------------------------------------------------------------------ themes

def theme_volt():
    """Voltcap's own: warm, electric, a little uplifting. A minor, 88 bpm, lo-fi groove."""
    s = Song(88, 16, swing=0.56)
    Am9 = chord('A2', 'E3', 'G3', 'B3', 'C4')
    Fmaj9 = chord('F2', 'A3', 'C4', 'E4', 'G4')
    Cadd9 = chord('C3', 'E3', 'G3', 'D4')
    G6 = chord('G2', 'D3', 'E3', 'B3')
    Dm9 = chord('D3', 'F3', 'A3', 'C4', 'E4')
    E7s = chord('E2', 'A3', 'B3', 'D4')
    Em7 = chord('E2', 'D3', 'G3', 'B3')
    F7s11 = chord('F2', 'A3', 'B3', 'E4')
    prog = [Am9, Fmaj9, Cadd9, G6, Am9, Fmaj9, Dm9, E7s,
            Fmaj9, G6, Em7, Am9, Dm9, Cadd9, F7s11, E7s]
    for bar, ch in enumerate(prog):
        comp_epiano(s, bar, ch, [(0, 1.5, 1.0), (1.5, 1.0, 0.7), (3.5, 0.5, 0.55)], vel=0.55, bright=0.7, gain=0.15)
        pad_bar(s, bar, ch, gain=0.05, bright=0.35, attack=0.9, release=1.8)
        root = ch[0]
        s.add('dry', bass(hz(root), 1.4 * s.beat, 0.9), bar, 0, gain=0.24)
        s.add('dry', bass(hz(root + 7), 0.7 * s.beat, 0.7), bar, 2, gain=0.18)
        s.add('dry', bass(hz(root + 12), 0.4 * s.beat, 0.6), bar, 3.5, gain=0.14)
    drums_lofi(s, 16, kick_gain=0.42, snare_gain=0.16, hat_gain=0.045)
    for bar in range(16):
        for e in range(16):
            if e % 2 == 1:
                s.add('drums', shaker(0.5), bar, e * 0.25, pan=-0.35, gain=0.03)
    mel = [(0, 0.5, 'E5', 1), (0, 1.5, 'G5', 0.5), (0, 2, 'A5', 1.5),
           (1, 0.5, 'G5', 0.5), (1, 1, 'E5', 1), (1, 2.5, 'C5', 1.5),
           (2, 1.5, 'D5', 0.5), (2, 2, 'E5', 0.5), (2, 2.5, 'G5', 1.5),
           (3, 1, 'B4', 1), (3, 2, 'D5', 2),
           (4, 0.5, 'E5', 1), (4, 1.5, 'A5', 0.5), (4, 2, 'B5', 1), (4, 3, 'C6', 1),
           (5, 0, 'B5', 0.5), (5, 0.5, 'A5', 1), (5, 2, 'G5', 1), (5, 3, 'E5', 1),
           (6, 1, 'F5', 1), (6, 2, 'E5', 0.5), (6, 2.5, 'D5', 1.5),
           (7, 0.5, 'B4', 1), (7, 1.5, 'D5', 0.5), (7, 2, 'E5', 2),
           # second half: answered an octave lower on glass bells, sparser
           (8, 1, 'C5', 1), (8, 2, 'E5', 1), (8, 3, 'G5', 1),
           (9, 0.5, 'B4', 1.5), (9, 2, 'D5', 2),
           (10, 1, 'E5', 0.5), (10, 1.5, 'G5', 0.5), (10, 2, 'B5', 2),
           (11, 0.5, 'A5', 1), (11, 2, 'E5', 2),
           (12, 1, 'F5', 1), (12, 2, 'A5', 1), (12, 3, 'C6', 1),
           (13, 0.5, 'B5', 1), (13, 2, 'G5', 2),
           (14, 1, 'A5', 0.5), (14, 1.5, 'B5', 0.5), (14, 2, 'E5', 2),
           (15, 1, 'D5', 1), (15, 2, 'B4', 2)]
    lead = lambda f, d, v: fm_pluck(f, v, ratio=2.0, index=1.4, decay=0.42)
    melody(s, mel[:25], lead, bus='delay', gain=0.13, pan=0.1)
    melody(s, mel[25:], lambda f, d, v: bell(f, 2.2, v, 'glass'), bus='delay', gain=0.1, pan=-0.1)
    return s.render(verb_mix=0.32, delay_time=0.75 * s.beat, delay_fb=0.38, delay_mix=0.32, duck=0.25,
                    rms_db=-20.0, drum_verb=0.1)


def theme_tide():
    """Water: slow, glassy bells over a wash of pads and gentle waves. D major, 72 bpm."""
    s = Song(72, 16)
    D = chord('D2', 'A3', 'C#4', 'E4', 'F#4')
    Bm = chord('B1', 'F#3', 'A3', 'D4', 'E4')
    G = chord('G2', 'B3', 'D4', 'F#4', 'A4')
    A = chord('A2', 'E3', 'A3', 'C#4', 'D4')
    Em = chord('E2', 'G3', 'B3', 'D4', 'F#4')
    prog = [D, Bm, G, A, D, Bm, Em, A, G, D, Em, Bm, G, A, D, A]
    for bar, ch in enumerate(prog):
        pad_bar(s, bar, ch, gain=0.09, bright=0.35, attack=1.6, release=2.8)
        s.add('dry', bass(hz(ch[0]), 3.6 * s.beat, 0.6, drive=1.0), bar, 0, gain=0.22)
        # arpeggio of glass bells, 8ths, up and down the chord
        tones = sorted(set(ch[1] + [n + 12 for n in ch[1]]))
        seq = tones[:6] + tones[4:1:-1]
        for e in range(8):
            if e in (3, 7) and bar % 2:
                continue
            n = seq[e % len(seq)] + 12
            s.add('delay', bell(hz(n), 2.4, 0.55 + 0.25 * (e % 4 == 0), 'glass'), bar, e * 0.5,
                  pan=np.sin(e * 0.8 + bar) * 0.6, gain=0.035)
    # waves: noise swells every 2 bars
    for bar in range(0, 16, 2):
        L = 8 * s.beat
        t = t_axis(L)
        nz = rng.standard_normal((2, len(t)))
        nz = lowpass(nz, 900 + 500 * np.sin(bar), order=2)
        env = np.sin(np.pi * t / L) ** 2
        s.add('verb', nz * env * 0.05, bar, 0)
    mel = motif_melody(prog, {2, 4, 6, 9, 11}, seed=4, register=(N('F#4'), N('D6')), density=0.35)
    melody(s, mel, lambda f, d, v: flute(f, d, v * 0.8), bus='delay', gain=0.05)
    return s.render(verb_mix=0.5, delay_time=0.75 * s.beat, delay_fb=0.45, delay_mix=0.35,
                    rms_db=-21.0, pad_verb=0.6, eq=(-5.0, 6.0),
                    verb=dict(seconds=4.0, low_t60=3.6, mid_t60=3.0, high_t60=1.6, predelay=0.03))


def theme_ember():
    """Embers: cozy, jazzy lo-fi by the fire. D minor, 76 bpm, brushes, soft crackle."""
    s = Song(76, 16, swing=0.6)
    Dm9 = chord('D2', 'F3', 'A3', 'C4', 'E4')
    Gm9 = chord('G2', 'F3', 'A3', 'Bb3', 'D4')
    C9 = chord('C2', 'E3', 'Bb3', 'D4', 'G4')
    Fmaj7 = chord('F2', 'E3', 'A3', 'C4', 'E4')
    Bbmaj7 = chord('Bb1', 'D3', 'A3', 'C4', 'F4')
    Em7b5 = chord('E2', 'D3', 'G3', 'Bb3', 'D4')
    A7s = chord('A1', 'D3', 'G3', 'B3', 'E4')
    A7b9 = chord('A1', 'C#3', 'G3', 'Bb3', 'E4')
    prog = [Dm9, Gm9, C9, Fmaj7, Bbmaj7, Em7b5, A7s, A7b9] * 2
    for bar, ch in enumerate(prog):
        comp_epiano(s, bar, ch, [(0, 2.5, 0.9), (2.5, 1.2, 0.6)], vel=0.5, bright=0.5, gain=0.17)
        pad_bar(s, bar, ch, gain=0.035, bright=0.25, attack=1.2, release=2.0, octave=0)
        s.add('dry', bass(hz(ch[0] + 12), 1.8 * s.beat, 0.8, drive=1.1), bar, 0, gain=0.3)
        s.add('dry', bass(hz(ch[0] + 12 + 7), 0.9 * s.beat, 0.6, drive=1.1), bar, 2.5, gain=0.22)
    drums_lofi(s, 16, kick_gain=0.36, snare_gain=0.22, hat_gain=0.035, brush=True)
    # fireplace crackle
    r = np.random.default_rng(5)
    for k in range(260):
        tt = r.random() * s.L
        c = r.standard_normal(int(0.003 * SR)) * np.exp(-np.arange(int(0.003 * SR)) / (0.0006 * SR))
        for p in (0, 1):
            s.buses['dry'].add(highpass(c, 2500) * r.uniform(0.01, 0.05), tt + p * s.L, pan=r.uniform(-0.8, 0.8))
    mel = motif_melody(prog, {2, 4, 5, 7, 9, 10, 0}, seed=11, register=(N('D4'), N('A5')), density=0.45)
    melody(s, mel, lambda f, d, v: epiano(f, d, v * 0.7, bright=1.2), bus='delay', gain=0.09, pan=0.15)
    return s.render(verb_mix=0.28, delay_time=0.5 * s.beat, delay_fb=0.3, delay_mix=0.22, duck=0.2,
                    rms_db=-20.5, lp=9000)


def theme_cobalt():
    """Deep blue: spacious and still, open fifths, sparse bells, a slow pulse. E minor, 64 bpm."""
    s = Song(64, 16)
    Em = chord('E2', 'B2', 'G3', 'D4', 'F#4')
    C = chord('C2', 'G2', 'E3', 'B3', 'D4')
    G = chord('G2', 'D3', 'B3', 'D4', 'A4')
    D = chord('D2', 'A2', 'F#3', 'A3', 'E4')
    Am = chord('A1', 'E2', 'C3', 'G3', 'B3')
    prog = [Em, Em, C, C, G, G, D, D, Am, Am, C, C, Em, Em, D, D]
    for bar in range(0, 16, 2):
        ch = prog[bar]
        pad_bar(s, bar, ch, bars=2, gain=0.11, bright=0.3, attack=2.4, release=3.5, width=1.0)
        s.add('dry', bass(hz(ch[0]), 7.5 * s.beat, 0.5, drive=1.0), bar, 0, gain=0.24)
    r = np.random.default_rng(9)
    for bar, ch in enumerate(prog):
        # a soft heartbeat pulse (percussion: only with PERCUSSION on)
        if PERCUSSION:
            s.add('dry', kick(0.5, f0=70, f1=40, dec=0.35), bar, 0, gain=0.18)
        for k in range(3):
            if r.random() < 0.55:
                n = r.choice(ch[1]) + 24
                beat = r.choice([0.5, 1, 1.5, 2, 2.5, 3, 3.5])
                s.add('delay', bell(hz(n), 3.0, r.uniform(0.4, 0.8), 'glass'), bar, beat, pan=r.uniform(-0.7, 0.7), gain=0.04)
    mel = motif_melody(prog, {4, 6, 7, 9, 11, 2}, seed=21, register=(N('B3'), N('G5')), density=0.25)
    melody(s, mel, lambda f, d, v: additive(f, d, [1, 0.2, 0.05], a=0.15, d=0.6, s=0.6, r=0.8, vib=0.002),
           bus='verb', gain=0.07, pan=-0.1)
    return s.render(verb_mix=0.55, delay_time=1.0 * s.beat, delay_fb=0.45, delay_mix=0.35,
                    rms_db=-22.0, pad_verb=0.7, eq=(-5.0, 8.0),
                    verb=dict(seconds=4.5, low_t60=4.0, mid_t60=3.4, high_t60=1.8, predelay=0.04))


def theme_synthwave():
    """Restrained retro: warm pads, 16th arps with ping-pong, gated snare, pumping. A minor, 100 bpm."""
    s = Song(100, 16)
    Am = chord('A2', 'E3', 'A3', 'C4', 'E4')
    F = chord('F2', 'C3', 'A3', 'C4', 'F4')
    C = chord('C3', 'G3', 'C4', 'E4', 'G4')
    G = chord('G2', 'D3', 'G3', 'B3', 'D4')
    Dm = chord('D3', 'A3', 'D4', 'F4', 'A4')
    E = chord('E2', 'B2', 'E3', 'G#3', 'B3')
    prog = [Am, Am, F, F, C, C, G, G, Am, Am, F, F, Dm, C, G, E]
    for bar, ch in enumerate(prog):
        pad_bar(s, bar, ch, gain=0.08, bright=0.8, attack=0.25, release=1.2)
        root = ch[0]
        for e in range(8):
            s.add('dry', bass(hz(root - 12 + (12 if e % 2 else 0)), 0.42 * s.beat, 0.85, drive=1.8), bar, e * 0.5, gain=0.12)
        v = ch[1]
        arp = v + v[-2:0:-1]
        for sx in range(16):
            n = arp[sx % len(arp)] + 12
            s.add('delay', square_pluck(hz(n), 0.22 * s.beat, 0.6 + 0.3 * (sx % 4 == 0), cutoff=2600, decay=0.12),
                  bar, sx * 0.25, pan=0.3 if sx % 2 else -0.3, gain=0.05)
        for q in range(4):
            s.kick(bar, q, kick(0.9, f0=120, f1=48, dec=0.22), gain=0.24)
        if PERCUSSION:
            s.add('verb', snare(0.9, tone=200, dec=0.12), bar, 1, gain=0.2)
            s.add('verb', snare(0.9, tone=200, dec=0.12), bar, 3, gain=0.2)
        for e in range(8):
            s.add('drums', hat(0.7, dec=0.02), bar, e * 0.5 + 0.5 if e < 7 else 3.5, pan=0.3, gain=0.035)
    mel = motif_melody(prog, {9, 11, 0, 2, 4, 5, 7}, seed=33, register=(N('A4'), N('E6')), density=0.4)
    melody(s, mel, lambda f, d, v: additive(f, d, [1, 0.5, 0.33, 0.25, 0.2, 0.16], a=0.02, d=0.3, s=0.7, r=0.3, vib=0.003),
           bus='delay', gain=0.045, pan=0.0)
    return s.render(verb_mix=0.33, delay_time=0.75 * s.beat, delay_fb=0.4, delay_mix=0.3, duck=0.45,
                    rms_db=-20.0, drum_verb=0.08, lp=11000)


def theme_matcha():
    """Tea garden: woody marimba, a breathy flute, soft shaker. C major pentatonic, 70 bpm."""
    s = Song(70, 16, swing=0.55)
    Cmaj9 = chord('C3', 'E3', 'G3', 'B3', 'D4')
    Am7 = chord('A2', 'E3', 'G3', 'C4', 'E4')
    Fmaj9 = chord('F2', 'A3', 'C4', 'E4', 'G4')
    G6 = chord('G2', 'D3', 'E3', 'B3', 'D4')
    Dm7 = chord('D3', 'F3', 'A3', 'C4', 'E4')
    Em7 = chord('E2', 'D3', 'G3', 'B3', 'E4')
    prog = [Cmaj9, Am7, Fmaj9, G6, Cmaj9, Em7, Fmaj9, G6, Am7, Em7, Fmaj9, Cmaj9, Dm7, G6, Cmaj9, G6]
    r = np.random.default_rng(17)
    for bar, ch in enumerate(prog):
        pad_bar(s, bar, ch, gain=0.05, bright=0.3, attack=1.4, release=2.2, octave=0)
        s.add('dry', bass(hz(ch[0]), 1.8 * s.beat, 0.6, drive=1.0), bar, 0, gain=0.24)
        s.add('dry', bass(hz(ch[0] + 7), 1.0 * s.beat, 0.5, drive=1.0), bar, 2, gain=0.18)
        tones = sorted(ch[1] + [n + 12 for n in ch[1]])
        pat = [0, 2, 4, 3, 5, 4, 2, 1]
        for e, k in enumerate(pat):
            if r.random() < 0.18:
                continue
            s.add('verb', marimba(hz(tones[k % len(tones)] + 12), 0.6 + 0.3 * (e % 2 == 0)), bar, e * 0.5,
                  pan=-0.4 + 0.1 * e, gain=0.06)
        for e in range(8):
            s.add('drums', shaker(0.6 + 0.3 * (e % 2)), bar, e * 0.5 + 0.25 * 0, pan=0.4, gain=0.035)
        s.add('drums', kick(0.5, f0=80, f1=48, dec=0.2), bar, 0, gain=0.2)
    mel = motif_melody(prog, {0, 2, 4, 7, 9}, seed=8, register=(N('E4'), N('D6')), density=0.4)
    melody(s, mel, lambda f, d, v: flute(f, d, v), bus='delay', gain=0.07, pan=0.1)
    return s.render(verb_mix=0.35, delay_time=0.75 * s.beat, delay_fb=0.35, delay_mix=0.25,
                    rms_db=-20.5, drum_verb=0.15, eq=(-5.0, 7.0))


def theme_mono():
    """OLED black: almost nothing — a low drone, soft piano notes far apart. A minor, 56 bpm."""
    s = Song(56, 8)
    Am = chord('A1', 'E2', 'A2', 'E3', 'B3')
    F = chord('F1', 'C2', 'A2', 'E3', 'B3')
    C = chord('C2', 'G2', 'E3', 'G3', 'D4')
    G = chord('G1', 'D2', 'B2', 'D3', 'A3')
    prog = [Am, Am, F, F, C, C, G, G]
    for bar in range(0, 8, 2):
        ch = prog[bar]
        pad_bar(s, bar, ch, bars=2, gain=0.08, bright=0.15, attack=3.0, release=4.0, octave=0)
    r = np.random.default_rng(2)
    for bar, ch in enumerate(prog):
        for k in range(2):
            if r.random() < 0.7:
                n = r.choice(ch[1][2:]) + 12
                s.add('verb', epiano(hz(n), 1.5 * s.beat, r.uniform(0.35, 0.6), bright=0.7),
                      bar, r.choice([0, 1, 1.5, 2, 3]), pan=r.uniform(-0.5, 0.5), gain=0.12)
    return s.render(verb_mix=0.6, rms_db=-23.0, pad_verb=0.7, eq=(-3.0, 9.0),
                    verb=dict(seconds=5.0, low_t60=4.5, mid_t60=3.8, high_t60=2.0, predelay=0.04))


THEMES = {
    'volt': theme_volt, 'tide': theme_tide, 'ember': theme_ember, 'cobalt': theme_cobalt,
    'synthwave': theme_synthwave, 'matcha': theme_matcha, 'mono': theme_mono,
}

if __name__ == '__main__':
    names = sys.argv[1:] or list(THEMES)
    for name in names:
        x = THEMES[name]()
        sw.write_wav(f'out/bgm_{name}.wav', x)
        # Vorbis q4: small, and the loop length is kept to the sample (checked with ffprobe).
        import subprocess
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', f'out/bgm_{name}.wav', '-c:a', 'libvorbis', '-q:a', '4',
                        f'out/bgm_{name}.ogg'], check=False)
        print(name, 'seconds', round(x.shape[1] / SR, 1), 'peak', round(float(np.max(np.abs(x))), 3))

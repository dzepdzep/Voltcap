"""Checks a loop: decoded length, seam (high-frequency jump at the loop point vs
elsewhere), loudness (RMS, K-ish weighted), spectral balance, stereo width."""
import sys, subprocess
import numpy as np
from scipy import signal

SR = 44100


def decode(path):
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 's16le', '-ac', '2', '-ar', str(SR), '-'],
                         capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.int16).astype(float).reshape(-1, 2).T / 32768
    return x


def kw(x):
    # rough K-weighting: high shelf + highpass
    b, a = signal.butter(2, 100 / (SR / 2), 'high')
    y = signal.lfilter(b, a, x, axis=-1)
    b2, a2 = signal.butter(1, 1500 / (SR / 2), 'high')
    return y + 0.6 * signal.lfilter(b2, a2, y, axis=-1)


def seam(x):
    # differentiate twice (clicks stand out), compare the loop point to the rest
    y = np.concatenate([x, x], axis=1)
    d = np.abs(np.diff(y, n=2, axis=1)).max(axis=0)
    n = x.shape[1]
    w = d[n - 40:n + 40].max()
    blocks = d[: (len(d) // 80) * 80].reshape(-1, 80).max(axis=1)
    return w, float(np.percentile(blocks, 50)), float(np.percentile(blocks, 99))


def report(path):
    x = decode(path)
    rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
    k = 20 * np.log10(np.sqrt(np.mean(kw(x) ** 2)) + 1e-12)
    m = x.mean(axis=0)
    f, p = signal.welch(m, SR, nperseg=8192)
    tot = p.sum()
    band = lambda lo, hi: 10 * np.log10(p[(f >= lo) & (f < hi)].sum() / tot + 1e-12)
    cent = (f * p).sum() / tot
    corr = np.corrcoef(x[0], x[1])[0, 1]
    w, med, p99 = seam(x)
    # level over time: 2 s windows, spread (dB)
    win = 2 * SR
    lv = [20 * np.log10(np.sqrt(np.mean(x[:, i:i + win] ** 2)) + 1e-12) for i in range(0, x.shape[1] - win, win)]
    print(f'{path.split("/")[-1]:20s} {x.shape[1] / SR:6.2f}s rms {rms:6.1f} K {k:6.1f} peak {20*np.log10(np.abs(x).max()):5.1f} '
          f'cent {cent:6.0f}Hz  <150 {band(20,150):5.1f} 150-2k {band(150,2000):5.1f} 2-6k {band(2000,6000):5.1f} >6k {band(6000,20000):5.1f} '
          f'corr {corr:4.2f} seam {w:.4f} (med {med:.4f} p99 {p99:.4f}) lvl {min(lv):.1f}..{max(lv):.1f}')


if __name__ == '__main__':
    for p in sys.argv[1:]:
        report(p)

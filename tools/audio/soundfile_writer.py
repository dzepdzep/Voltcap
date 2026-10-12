import numpy as np
from scipy.io import wavfile
def write_wav(path, x, sr=44100):
    x = np.asarray(x)
    if x.ndim == 2: x = x.T
    wavfile.write(path, sr, (np.clip(x, -1, 1) * 32767).astype(np.int16))

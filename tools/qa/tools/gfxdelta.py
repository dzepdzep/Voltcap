#!/usr/bin/env python3
"""682ag: frame times of one p2review walk from two `dumpsys gfxinfo <pkg>` dumps (before / after) - the counters and the
HISTOGRAM are cumulative, so the walk is their difference. Prints frames, janky %, p50 / p90 / p95 / p99 (ms).
Usage: gfxdelta.py BEFORE.txt AFTER.txt   (exit 2 when a dump is missing or unreadable - INFRA, not a result)"""
import re, sys


def parse(text):
    """{'frames': int, 'janky': int, 'hist': {ms: count}} from the first (the app's own) section of a gfxinfo dump."""
    m = re.search(r'Total frames rendered:\s*(\d+)', text)
    j = re.search(r'Janky frames:\s*(\d+)', text)
    h = re.search(r'HISTOGRAM:\s*([^\n]+)', text)
    if not (m and j and h):
        return None
    hist = {int(a): int(b) for a, b in re.findall(r'(\d+)ms=(\d+)', h.group(1))}
    return {'frames': int(m.group(1)), 'janky': int(j.group(1)), 'hist': hist}


def delta(before, after):
    """The walk's own frames: after minus before (a before taken in another process life - fewer frames - counts from zero)."""
    if before is None or after['frames'] < before['frames']:
        before = {'frames': 0, 'janky': 0, 'hist': {}}
    hist = {k: v - before['hist'].get(k, 0) for k, v in after['hist'].items()}
    return {'frames': after['frames'] - before['frames'], 'janky': after['janky'] - before['janky'], 'hist': hist}


def percentile(hist, p):
    total = sum(hist.values())
    if total <= 0:
        return None
    need = total * p / 100.0; run = 0
    for ms in sorted(hist):
        run += hist[ms]
        if run >= need:
            return ms
    return max(hist)


def summary(d):
    f = d['frames']
    return {'frames': f, 'janky_pct': round(100.0 * d['janky'] / f, 1) if f else None,
            'p50': percentile(d['hist'], 50), 'p90': percentile(d['hist'], 90), 'p95': percentile(d['hist'], 95), 'p99': percentile(d['hist'], 99)}


if __name__ == '__main__':
    try:
        b = parse(open(sys.argv[1]).read()); a = parse(open(sys.argv[2]).read())
    except (OSError, IndexError):
        print('INFRA: dump missing'); sys.exit(2)
    if a is None:
        print('INFRA: no gfxinfo counters in the after dump'); sys.exit(2)
    print(summary(delta(b, a)))

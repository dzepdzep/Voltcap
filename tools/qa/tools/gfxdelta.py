#!/usr/bin/env python3
"""682ag: frame times of one p2review walk from two `dumpsys gfxinfo <pkg>` dumps (before / after) - the counters and the
HISTOGRAM are cumulative, so the walk is their difference. Prints frames, janky %, p50 / p90 / p95 / p99 (ms).
Usage: gfxdelta.py BEFORE.txt AFTER.txt [--detail]   (--detail: + slow UI thread / issue draw / missed vsync / GPU p50-p99)
        (exit 2 when a dump is missing or unreadable - INFRA, not a result)"""
import re, sys


def parse(text):
    """{'frames': int, 'janky': int, 'hist': {ms: count}} from the first (the app's own) section of a gfxinfo dump."""
    m = re.search(r'Total frames rendered:\s*(\d+)', text)
    j = re.search(r'Janky frames:\s*(\d+)', text)
    h = re.search(r'HISTOGRAM:\s*([^\n]+)', text)
    if not (m and j and h):
        return None
    hist = {int(a): int(b) for a, b in re.findall(r'(\d+)ms=(\d+)', h.group(1))}
    # (682aj, owner 18:23 - Arcade profiling: where the time goes. The renderer's own counters and its GPU histogram, when the
    # device prints them; missing ones stay missing - never a zero that looks like a result.)
    cnt = {k: int(v) for k, v in re.findall(r'Number (Missed Vsync|Slow UI thread|Slow bitmap uploads|Slow issue draw commands|Frame deadline missed):\s*(\d+)', text)}
    g = re.search(r'GPU HISTOGRAM:\s*([^\n]+)', text)
    gpu = {int(a): int(b) for a, b in re.findall(r'(\d+)ms=(\d+)', g.group(1))} if g else None
    return {'frames': int(m.group(1)), 'janky': int(j.group(1)), 'hist': hist, 'cnt': cnt, 'gpu': gpu}


def delta(before, after):
    """The walk's own frames: after minus before (a before taken in another process life - fewer frames - counts from zero)."""
    if before is None or after['frames'] < before['frames']:
        before = {'frames': 0, 'janky': 0, 'hist': {}, 'cnt': {}, 'gpu': {}}
    hist = {k: v - before['hist'].get(k, 0) for k, v in after['hist'].items()}
    cnt = {k: v - before.get('cnt', {}).get(k, 0) for k, v in after.get('cnt', {}).items()}
    gpu = None if after.get('gpu') is None else {k: v - (before.get('gpu') or {}).get(k, 0) for k, v in after['gpu'].items()}
    return {'frames': after['frames'] - before['frames'], 'janky': after['janky'] - before['janky'], 'hist': hist, 'cnt': cnt, 'gpu': gpu}


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


def detail(d):
    """summary + the renderer's counters (slow UI thread = main-thread work: measure / layout / composition; slow issue draw
    commands = RenderThread recording; GPU percentiles = the GPU's own time per frame)."""
    s = summary(d)
    c = d.get('cnt') or {}
    s.update({'slow_ui': c.get('Slow UI thread'), 'slow_draw': c.get('Slow issue draw commands'), 'missed_vsync': c.get('Missed Vsync'),
              'deadline_missed': c.get('Frame deadline missed')})
    if d.get('gpu'):
        s.update({'gpu_p50': percentile(d['gpu'], 50), 'gpu_p90': percentile(d['gpu'], 90), 'gpu_p99': percentile(d['gpu'], 99)})
    return s


if __name__ == '__main__':
    try:
        b = parse(open(sys.argv[1]).read()); a = parse(open(sys.argv[2]).read())
    except (OSError, IndexError):
        print('INFRA: dump missing'); sys.exit(2)
    if a is None:
        print('INFRA: no gfxinfo counters in the after dump'); sys.exit(2)
    print(detail(delta(b, a)) if '--detail' in sys.argv else summary(delta(b, a)))

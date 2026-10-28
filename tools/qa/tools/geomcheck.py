#!/usr/bin/env python3
"""VoltCap QA — opt-in element geometry check (update 369). Device-free: reads a recorded or synthetic geometry trace.

  python3 geomcheck.py TRACE.json [--json OUT.json]

TRACE.json: {"screen": [w, h], "frames": [{"t": ms, "key": "R"|null, "focused": "id"|null, "focus": rect|null,
             "elements": {"id": rect}}], "row": ["id", ...]?, "animating": ["id", ...]?, "limits": {...}?}   rect = [x, y, w, h]
Checks (each reports its own Result):
  size      an element keeps its width / height (animating ids excepted) within limits.size_px (default 2)
  spacing   the neighbours in "row" are evenly spaced in every frame within limits.gap_px (default 2)
  focus     the focus indicator surrounds the focused element (centre inside it, within limits.focus_px) and stays on screen
  movement  no element jumps further than limits.jump_px (default 60) between two frames unless a key came in the last
            limits.settle_ms (default 450) — a drift or snap with no key is a defect; the glide after a key is expected
Exit codes as qalib: 0 PASS, 1 FAIL, 2 INFRA (bad / empty trace: never a pass), 3 NOT TESTED.
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib

DEFAULTS = dict(size_px=2.0, gap_px=2.0, focus_px=6.0, jump_px=60.0, settle_ms=450.0)


def _centre(r):
    return (r[0] + r[2] / 2.0, r[1] + r[3] / 2.0)


def evaluate(trace):
    """The Results for [trace] (a dict). INFRA when the trace can't prove anything."""
    frames = trace.get("frames") if isinstance(trace, dict) else None
    if not frames or len(frames) < 2:
        return [qalib.Result("geometry trace", qalib.INFRA, "fewer than 2 frames: nothing was measured")]
    lim = dict(DEFAULTS); lim.update(trace.get("limits") or {})
    screen = trace.get("screen") or [0, 0]
    animating = set(trace.get("animating") or [])
    row = trace.get("row") or []
    size_p, gap_p, focus_p, move_p = [], [], [], []
    first = {}
    last_key_t = None
    prev = None
    for i, fr in enumerate(frames):
        t = fr.get("t", i)
        if fr.get("key"): last_key_t = t
        els = fr.get("elements") or {}
        for k, r in els.items():
            f = first.setdefault(k, r)
            if k not in animating and (abs(r[2] - f[2]) > lim["size_px"] or abs(r[3] - f[3]) > lim["size_px"]):
                size_p.append(f"frame {i}: {k} is {r[2]:g}x{r[3]:g}, was {f[2]:g}x{f[3]:g}")
        shown = [els[k] for k in row if k in els]
        if len(shown) >= 3:
            gaps = [shown[j + 1][0] - (shown[j][0] + shown[j][2]) for j in range(len(shown) - 1)]
            if max(gaps) - min(gaps) > lim["gap_px"]:
                gap_p.append(f"frame {i}: gaps in the row differ by {max(gaps) - min(gaps):g} px ({min(gaps):g}..{max(gaps):g})")
        fid, fr_rect = fr.get("focused"), fr.get("focus")
        if fid and fr_rect is not None and fid in els:
            e = els[fid]; cx, cy = _centre(fr_rect); ex, ey = _centre(e)
            if abs(cx - ex) > lim["focus_px"] + e[2] / 2.0 or abs(cy - ey) > lim["focus_px"] + e[3] / 2.0:
                focus_p.append(f"frame {i}: focus ring is not on {fid}")
            if screen[0] and (fr_rect[0] < -lim["focus_px"] or fr_rect[1] < -lim["focus_px"] or
                              fr_rect[0] + fr_rect[2] > screen[0] + lim["focus_px"] or fr_rect[1] + fr_rect[3] > screen[1] + lim["focus_px"]):
                focus_p.append(f"frame {i}: focus ring leaves the screen")
        elif fid and fr_rect is None:
            focus_p.append(f"frame {i}: {fid} is focused but no focus indicator is drawn")
        if prev is not None:
            settling = last_key_t is not None and t - last_key_t <= lim["settle_ms"]
            for k, r in els.items():
                p = prev.get(k)
                if p is None or settling: continue
                d = max(abs(r[0] - p[0]), abs(r[1] - p[1]))
                if d > lim["jump_px"]:
                    move_p.append(f"frame {i}: {k} moved {d:g} px with no key in the last {lim['settle_ms']:g} ms")
        prev = els
    out = []
    for name, probs, what in (("size", size_p, "element sizes stay constant"), ("spacing", gap_p, "row spacing stays even"),
                              ("focus", focus_p, "focus indicator on the focused element"),
                              ("movement", move_p, "no unexpected movement")):
        out.append(qalib.Result("geometry " + name, qalib.FAIL if probs else qalib.PASS,
                                "; ".join(probs[:5]) + (f" (+{len(probs) - 5} more)" if len(probs) > 5 else "") if probs else what))
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    path = sys.argv[1]
    try:
        trace = json.load(open(path, encoding="utf-8"))
    except (OSError, ValueError) as e:
        qalib.finish([qalib.Result("geometry trace", qalib.INFRA, f"cannot read {path}: {e}")], "Geometry check")
    jp = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    qalib.finish(evaluate(trace), "Geometry check", jp)


if __name__ == "__main__":
    main()

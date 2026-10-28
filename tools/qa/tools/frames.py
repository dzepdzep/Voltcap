#!/usr/bin/env python3
"""VoltCap QA — frame-by-frame inspection of a screen recording.

Finds the things a normal screenshot can't:
  * transient frames: 1–3 frames that look like neither the frames before nor after (a wrong cover / background /
    UI state flashing), per screen region, so a stale cover in one slot is caught even while the rest moves;
  * black / white / near-empty frames and sudden brightness dips;
  * hitches: frames missing from the recording while the picture is moving (dropped / late frames);
  * repeated frames inside motion (judder).

Each event gets a contact sheet (the frames around it, the region boxed) so it can be looked at, plus events.json.

  python3 frames.py VIDEO.mp4 [-o OUTDIR] [--grid 8x5] [--scale 320] [--marks marks.txt]

Needs ffmpeg/ffprobe, numpy, Pillow. Works for any refresh rate (frame times are read from the file; the screen's
vsync period is taken from the most common frame interval).

How the transient test works: for a window of k frames (k = 1..3) between frame a = i-1 and c = i+k, each region is
compared. Smooth motion and fades move "on the way" from a to c, so the in-between frames are no further from a and c
than a and c are from each other. A flash is far from BOTH ends while the ends are close (A B A), or far from the
straight blend of the ends (A X C, with X not between them). Score = distance(frame, blend(a, c)) / (distance(a, c) + noise).
"""
import argparse, json, os, subprocess, sys
import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
from qalib import Result, PASS, FAIL, INFRA

EVENT_KINDS = ("transient", "black", "white", "dip", "hitch", "repeat")


def probe_times(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "frame=pts_time",
                          "-of", "csv=p=0", path], capture_output=True, text=True).stdout
    vals = []
    for x in out.split():
        try:
            vals.append(float(x.split(",")[0]))  # "N/A" (a frame without a timestamp) is dropped, not fatal
        except ValueError:
            pass
    return vals


def probe_size(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height",
                          "-of", "csv=p=0", path], capture_output=True, text=True).stdout.strip().split(",")
    return int(out[0]), int(out[1])


def _decode(path, vf, passthrough_flag):
    cmd = ["ffmpeg", "-v", "error", "-i", path] + passthrough_flag + ["-vf", vf, "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    return subprocess.run(cmd, capture_output=True)


def read_frames(path, scale):
    """Every frame, scaled to `scale` px wide, as (n, h, w, 3) uint8. Raises RuntimeError when nothing can be decoded.

    ffmpeg 7+/8+/9 removed `-vsync`; `-fps_mode passthrough` replaced it (older builds only know `-vsync 0`). The
    recent Thor / emulator recordings made frames.py decode ZERO frames on a new ffmpeg, which then crashed with an
    IndexError on times[-1]; both flags are tried and an empty decode is reported, never indexed.
    """
    w, h = probe_size(path)
    sw = scale; sh = int(round(h * scale / w / 2)) * 2
    vf = f"scale={sw}:{sh}:flags=area"
    raw = b""
    err = ""
    for flag in (["-fps_mode", "passthrough"], ["-vsync", "0"]):
        p = _decode(path, vf, flag)
        err = p.stderr.decode("utf-8", "replace")
        if p.stdout:
            raw = p.stdout
            break
    n = len(raw) // (sw * sh * 3)
    if n == 0:
        raise RuntimeError("ffmpeg decoded no frames" + (": " + err.strip().splitlines()[-1][:200] if err.strip() else ""))
    return np.frombuffer(raw[: n * sw * sh * 3], np.uint8).reshape(n, sh, sw, 3), (w, h)


def full_frame(path, idx, times, out_png, width=640):
    t = times[idx]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-ss", f"{max(0, t - 0.0005):.4f}", "-i", path, "-frames:v", "1",
                    "-vf", f"scale={width}:-2", out_png])


def region_dist(a, b, gh, gw):
    """Mean abs difference per grid cell, 0..255."""
    d = np.abs(a.astype(np.float32) - b.astype(np.float32)).mean(axis=-1)
    H, W = d.shape
    ch, cw = H // gh, W // gw
    d = d[: ch * gh, : cw * gw].reshape(gh, ch, gw, cw).mean(axis=(1, 3))
    return d


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("video")
    ap.add_argument("-o", "--out", default=None)
    ap.add_argument("--grid", default="8x5", help="regions WxH")
    ap.add_argument("--scale", type=int, default=320)
    ap.add_argument("--min", type=float, default=10.0, help="minimum region change (0-255) to count")
    ap.add_argument("--ratio", type=float, default=1.6, help="transient score threshold")
    ap.add_argument("--max-sheets", type=int, default=40)
    ap.add_argument("--skip-start", type=float, default=0.3, help="seconds ignored at the start")
    ap.add_argument("--fail-on", default="", help="comma list of event kinds (%s) that make the run FAIL (exit 1); "
                    "default: report only" % ",".join(EVENT_KINDS))
    ap.add_argument("--not-before", type=float, default=None, help="epoch seconds: an older recording is stale evidence")
    ap.add_argument("--max-age", type=float, default=None, help="seconds: an older recording is stale evidence")
    ap.add_argument("--min-frames", type=int, default=10)
    args = ap.parse_args()
    gw, gh = [int(x) for x in args.grid.lower().split("x")]
    out = args.out or os.path.splitext(args.video)[0] + "_frames"
    os.makedirs(out, exist_ok=True)

    # Exit codes: 0 PASS (nothing in --fail-on found), 1 FAIL (a --fail-on event), 2 INFRA (the recording can't be
    # trusted: missing, empty, truncated, stale, undecodable), 3 nothing analysable. Never a silent pass.
    def stop(res, reason=None):
        qalib.write_json(os.path.join(out, "events.json"), [res], dict(summary=dict(video=os.path.basename(args.video)), events=[]))
        print(res)
        sys.exit(qalib.exit_code([res]))

    rec = qalib.check_recording(args.video, min_frames=args.min_frames, not_before=args.not_before, max_age_s=args.max_age)
    if rec[0].status != PASS:
        stop(rec[0])
    times = probe_times(args.video)
    try:
        F, (W, H) = read_frames(args.video, args.scale)
    except RuntimeError as e:
        stop(Result(os.path.basename(args.video), INFRA, str(e), args.video))
    n = min(len(F), len(times))
    if n < args.min_frames or len(times) < args.min_frames:
        stop(Result(os.path.basename(args.video), INFRA,
                    f"decoded {len(F)} frames but {len(times)} timestamps: not enough to analyse", args.video))
    if abs(len(F) - len(times)) > max(2, len(times) // 50):
        print(f"warning: {len(F)} frames decoded vs {len(times)} timestamps; analysing the first {n}", file=sys.stderr)
    F = F[:n]; times = times[:n]
    iv = np.diff(times) * 1000
    vs = float(np.median(iv)) if len(iv) else 16.7
    lum = F.mean(axis=(1, 2, 3))
    events = []

    # pairwise region distances to neighbours (k=1) — computed lazily for k>1
    def rd(i, j):
        return region_dist(F[i], F[j], gh, gw)

    start = next((i for i, t in enumerate(times) if t - times[0] >= args.skip_start), 0)
    noise = 2.5
    flagged = set()
    for k in (1, 2, 3):
        for i in range(max(1, start), n - k):
            a, c = i - 1, i + k
            dac = rd(a, c)
            worst = None
            for m in range(i, i + k):
                frac = (m - a) / (c - a)
                blend = (F[a].astype(np.float32) * (1 - frac) + F[c].astype(np.float32) * frac)
                dm = region_dist(F[m].astype(np.float32), blend, gh, gw)
                score = dm / (dac + noise)
                hit = (dm > args.min) & (score > args.ratio)
                if hit.any():
                    r, cc = np.unravel_index(np.argmax(np.where(hit, score, 0)), score.shape)
                    s = float(score[r, cc])
                    if worst is None or s > worst[0]:
                        worst = (s, int(r), int(cc), float(dm[r, cc]), float(dac[r, cc]), int(np.count_nonzero(hit)))
            if worst and not any((i + o) in flagged for o in range(-2, k + 2)):
                s, r, cc, dm, dac0, cells = worst
                events.append(dict(kind="transient", frames=k, at=i, t=round(times[i] - times[0], 3), score=round(s, 2),
                                   region=[cc, r], change=round(dm, 1), ends_diff=round(dac0, 1), cells=cells))
                for m in range(i, i + k):
                    flagged.add(m)

    # black / white / dips
    for i in range(max(1, start), n):
        if lum[i] < 6 and lum[i - 1] > 20:
            events.append(dict(kind="black", at=i, t=round(times[i] - times[0], 3), lum=round(float(lum[i]), 1)))
        elif lum[i] > 248 and lum[i - 1] < 200:
            events.append(dict(kind="white", at=i, t=round(times[i] - times[0], 3), lum=round(float(lum[i]), 1)))
        elif i + 1 < n and lum[i] < 0.6 * min(lum[i - 1], lum[i + 1]) and min(lum[i - 1], lum[i + 1]) > 25:
            events.append(dict(kind="dip", at=i, t=round(times[i] - times[0], 3), lum=round(float(lum[i]), 1),
                               around=round(float(min(lum[i - 1], lum[i + 1])), 1)))

    # hitches & repeats while moving
    whole = np.array([np.abs(F[i].astype(np.int16) - F[i - 1].astype(np.int16)).mean() for i in range(1, n)])
    moving = whole > 1.0
    for i in range(1, n - 1):
        gap = (times[i] - times[i - 1]) * 1000
        if i - 1 < len(moving) and i < len(moving) and moving[i - 1] and moving[min(i, len(moving) - 1)] and gap > 1.6 * vs and times[i] - times[0] > args.skip_start:
            events.append(dict(kind="hitch", at=i, t=round(times[i] - times[0], 3), gap_ms=round(gap, 1), vsync_ms=round(vs, 2)))
    for i in range(2, n - 2):
        if whole[i - 1] < 0.15 and whole[i - 2] > 2.0 and whole[i] > 2.0:
            events.append(dict(kind="repeat", at=i, t=round(times[i] - times[0], 3)))

    events.sort(key=lambda e: e["at"])
    # contact sheets
    cw, ch = W // gw, H // gh
    sheets = 0
    for e in events:
        if sheets >= args.max_sheets:
            break
        if e["kind"] not in ("transient", "black", "white", "dip"):
            continue
        k = e.get("frames", 1)
        idxs = list(range(max(0, e["at"] - 2), min(n, e["at"] + k + 2)))
        tiles = []
        for j in idxs:
            p = os.path.join(out, f"_f{j}.png")
            full_frame(args.video, j, times, p, 480)
            if not os.path.exists(p):
                continue  # the sheet is only a convenience; the event itself is already recorded
            im = Image.open(p).convert("RGB")
            if e["kind"] == "transient":
                sx = im.width / W
                x0, y0 = e["region"][0] * cw * sx, e["region"][1] * ch * sx
                ImageDraw.Draw(im).rectangle([x0, y0, x0 + cw * sx, y0 + ch * sx], outline=(255, 0, 255), width=3)
            ImageDraw.Draw(im).text((6, 6), f"#{j} {times[j]-times[0]:.3f}s" + (" <" if e["at"] <= j < e["at"] + k else ""), fill=(255, 255, 0))
            tiles.append(im)
            os.remove(p)
        if not tiles:
            continue
        sheet = Image.new("RGB", (sum(t.width for t in tiles) + 4 * len(tiles), tiles[0].height), (40, 0, 40))
        x = 0
        for t in tiles:
            sheet.paste(t, (x, 0)); x += t.width + 4
        name = f"ev_{e['at']:05d}_{e['kind']}.png"
        sheet.save(os.path.join(out, name))
        e["sheet"] = name
        sheets += 1

    summary = dict(video=os.path.basename(args.video), frames=n, seconds=round(times[-1] - times[0], 3),
                   vsync_ms=round(vs, 2), fps=round(1000 / vs, 1) if vs > 0 else 0,
                   counts={k: sum(1 for e in events if e["kind"] == k) for k in EVENT_KINDS})
    fail_kinds = [k for k in args.fail_on.split(",") if k]
    bad = [k for k in fail_kinds if summary["counts"].get(k)]
    res = Result(os.path.basename(args.video), FAIL if bad else PASS,
                 ("found " + ", ".join(f"{summary['counts'][k]} {k}" for k in bad)) if bad else
                 f"{n} frames analysed, none of [{args.fail_on}] found", args.video)
    json.dump(dict(summary=summary, events=events, verdict=res.status if fail_kinds else "REPORT", reason=res.reason),
              open(os.path.join(out, "events.json"), "w"), indent=1)
    print(json.dumps(summary))
    for e in events:
        print(e)
    if not fail_kinds:  # inspection mode: nothing was asserted, so no PASS is claimed
        print(f"[REPORT] {os.path.basename(args.video)}: {n} frames analysed, {len(events)} events to review "
              "(no --fail-on, so no verdict)")
        sys.exit(0)
    print(res)
    sys.exit(qalib.exit_code([res]))


if __name__ == "__main__":
    main()

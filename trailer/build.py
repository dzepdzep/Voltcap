#!/usr/bin/env python3
"""
Voltcap trailer builder.

  python3 build.py cuts.json OUT.mp4 [--no-captions] [--fps 60]

cuts.json:
{
  "clips_dir": "...", "music": ".../bgm_volt.ogg", "intro_sound": ".../voltcap_intro.ogg",
  "bpm": 88,
  "shots": [ {"file": "01_iconbar.mp4", "in": 1.2, "beats": 8, "caption": "PS5-style icon bar"}, ... ],
  "intro": {"file": "99_intro.mp4", "start": null, "hold_at": 2.25, "hold": 2.6}
}

Every shot is cut on the beat of the music (beats = length). Each clip is fitted into 16:9 (4:3 handheld clips get a
blurred, darkened copy of themselves behind them). Captions are small lower-left labels in Voltcap's colours. The
music fades over the last bar, then our intro plays with its own sound and holds on the wordmark.
"""
import json, os, subprocess, sys, tempfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
FONT = "/usr/share/fonts/opentype/inter/InterDisplay-SemiBold.otf"
CREAM = (255, 246, 214)
ORANGE = (245, 154, 44)
YELLOW = (255, 216, 74)


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(" ".join(cmd) + "\n" + r.stderr[-3000:])
        raise SystemExit(1)
    return r


def probe(path):
    r = run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
             "stream=width,height,r_frame_rate:format=duration", "-of", "json", path])
    j = json.loads(r.stdout)
    s = j["streams"][0]
    return s["width"], s["height"], float(j["format"]["duration"])


def caption_png(text, path):
    """A lower-left label: an orange-to-yellow bar, then the text in cream with a soft shadow."""
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    font = ImageFont.truetype(FONT, 46)
    x, y = 96, H - 150
    bar_w, bar_h = 8, 58
    tw = font.getbbox(text)[2]
    # Shadow
    sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(sh)
    d.text((x + 28 + 2, y + 4 + 3), text, font=font, fill=(0, 0, 0, 200))
    d.rectangle([x - 20, y - 18, x + 28 + tw + 40, y + bar_h + 18], fill=(0, 0, 0, 90))
    sh = sh.filter(ImageFilter.GaussianBlur(14))
    img.alpha_composite(sh)
    d = ImageDraw.Draw(img)
    for i in range(bar_h):
        t = i / (bar_h - 1)
        c = tuple(int(ORANGE[k] + (YELLOW[k] - ORANGE[k]) * (1 - t)) for k in range(3))
        d.line([(x, y + i), (x + bar_w, y + i)], fill=c + (255,))
    d.text((x + 28, y - 2), text, font=font, fill=CREAM + (255,))
    img.save(path)


def fit_filter(src_w, src_h):
    """Clip into 1920x1080: fitted foreground, blurred dark copy behind when the aspect differs."""
    ar = src_w / src_h
    if abs(ar - W / H) < 0.02:
        return f"scale={W}:{H}:flags=lanczos,setsar=1"
    return (f"split=2[bgs][fgs];"
            f"[bgs]scale={W}:{H}:force_original_aspect_ratio=increase:flags=bilinear,crop={W}:{H},"
            f"gblur=sigma=40,eq=brightness=-0.12:saturation=1.2[bg];"
            f"[fgs]scale={W}:{H}:force_original_aspect_ratio=decrease:flags=lanczos[fg];"
            f"[bg][fg]overlay=(W-w)/2:(H-h)/2,setsar=1")


def render_shot(shot, clips_dir, dur, fps, captions, tmp, idx):
    src = os.path.join(clips_dir, shot["file"])
    sw, sh, sdur = probe(src)
    start = float(shot.get("in", 0.5))
    if start + dur > sdur:  # too short: start earlier, else slow it down to fill the beat
        start = max(0.0, sdur - dur)
    speed = min(1.0, (sdur - start) / dur) if sdur - start < dur else 1.0
    out = os.path.join(tmp, f"s{idx:02d}.mp4")
    vf = f"[0:v]{fit_filter(sw, sh)},fps={fps}"
    if speed < 1.0:
        vf += f",setpts=PTS/{speed:.4f}"
    vf += ",format=yuv420p"
    inputs = ["-ss", f"{start:.3f}", "-t", f"{dur / 1.0 * speed + 0.05:.3f}", "-i", src]
    cap = shot.get("caption") if captions else None
    if cap:
        png = os.path.join(tmp, f"c{idx:02d}.png")
        caption_png(cap, png)
        inputs += ["-loop", "1", "-t", f"{dur:.3f}", "-i", png]
        fo = max(0.0, dur - 0.35)
        vf += (f"[v0];[1:v]format=rgba,fade=t=in:st=0.15:d=0.3:alpha=1,fade=t=out:st={fo:.3f}:d=0.3:alpha=1[cap];"
               f"[v0][cap]overlay=0:0:format=auto,format=yuv420p")
    vf += "[v]"
    run(["ffmpeg", "-y", "-hide_banner", *inputs, "-filter_complex", vf, "-map", "[v]", "-t", f"{dur:.4f}",
         "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "14", "-r", str(fps), out])
    return out


def find_intro_start(path):
    """The first frame where the screen goes dark (the intro's black stage) after the settings screen."""
    r = run(["ffmpeg", "-hide_banner", "-i", path, "-vf", "scale=160:-2,signalstats,metadata=print:key=lavfi.signalstats.YAVG",
             "-f", "null", "-"])
    times, vals = [], []
    t = None
    for line in r.stderr.splitlines():
        if "pts_time:" in line:
            t = float(line.split("pts_time:")[1].split()[0])
        elif "YAVG=" in line and t is not None:
            times.append(t); vals.append(float(line.split("YAVG=")[1]))
    if not vals:
        return 0.0
    if vals[0] < 30:  # starts already dark: the recording began with the intro
        return 0.0
    for tt, v in zip(times, vals):
        if v < 22:
            return max(0.0, tt - 1 / 30)
    return 0.0


def render_intro(intro, clips_dir, fps, tmp):
    src = os.path.join(clips_dir, intro["file"])
    sw, sh, sdur = probe(src)
    start = intro.get("start")
    if start is None:
        start = find_intro_start(src)
    hold_at = float(intro.get("hold_at", 2.25))
    hold = float(intro.get("hold", 2.6))
    total = hold_at + hold
    out = os.path.join(tmp, "intro.mp4")
    vf = (f"[0:v]{fit_filter(sw, sh)},fps={fps},trim=0:{hold_at:.3f},setpts=PTS-STARTPTS,"
          f"tpad=stop_mode=clone:stop_duration={hold:.3f},fade=t=out:st={total - 0.8:.3f}:d=0.8,format=yuv420p[v]")
    run(["ffmpeg", "-y", "-hide_banner", "-ss", f"{start:.3f}", "-i", src, "-filter_complex", vf, "-map", "[v]",
         "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "14", "-r", str(fps), out])
    return out, total


def main():
    cfg_path, out_path = sys.argv[1], sys.argv[2]
    captions = "--no-captions" not in sys.argv
    fps = int(sys.argv[sys.argv.index("--fps") + 1]) if "--fps" in sys.argv else 60
    cfg = json.load(open(cfg_path))
    base = os.path.dirname(os.path.abspath(cfg_path))
    for k in ("clips_dir", "music", "intro_sound"):  # paths are relative to cuts.json
        cfg[k] = os.path.normpath(os.path.join(base, cfg[k]))
    missing = [s["file"] for s in cfg["shots"] if not os.path.exists(os.path.join(cfg["clips_dir"], s["file"]))]
    if missing:
        print("skipping (not recorded yet): " + ", ".join(missing))
    cfg["shots"] = [s for s in cfg["shots"] if s["file"] not in missing]
    beat = 60.0 / cfg.get("bpm", 88)
    tmp = tempfile.mkdtemp(prefix="trailer_")
    parts, t = [], 0.0
    for i, shot in enumerate(cfg["shots"]):
        dur = shot["beats"] * beat
        parts.append(render_shot(shot, cfg["clips_dir"], dur, fps, captions, tmp, i))
        t += dur
        print(f"shot {i + 1}: {shot['file']} {dur:.2f}s (ends {t:.2f})", flush=True)
    clips_end = t
    intro = cfg.get("intro")
    intro_len = 0.0
    if intro and os.path.exists(os.path.join(cfg["clips_dir"], intro["file"])):
        p, intro_len = render_intro(intro, cfg["clips_dir"], fps, tmp)
        parts.append(p)
    lst = os.path.join(tmp, "list.txt")
    with open(lst, "w") as f:
        for p in parts:
            f.write(f"file '{p}'\n")
    video = os.path.join(tmp, "video.mp4")
    run(["ffmpeg", "-y", "-hide_banner", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", video])
    total = clips_end + intro_len
    bar = beat * 4
    # Music under the clips, fading over the last bar; the intro sound at the start of the intro.
    af = (f"[1:a]atrim=0:{clips_end:.3f},afade=t=in:st=0:d=0.3,afade=t=out:st={max(0, clips_end - bar):.3f}:d={bar:.3f},"
          f"apad=whole_dur={total:.3f}[m];")
    inputs = ["-i", video, "-i", cfg["music"]]
    if intro_len > 0:
        delay = int((clips_end + float(intro.get("sound_offset", 0.0))) * 1000)
        inputs += ["-i", cfg["intro_sound"]]
        af += f"[2:a]adelay={delay}|{delay},apad=whole_dur={total:.3f}[s];[m][s]amix=inputs=2:normalize=0[mix];"
    else:
        af += "[m]anull[mix];"
    af += f"[mix]atrim=0:{total:.3f},loudnorm=I=-14:TP=-1.5:LRA=11,aresample=48000[a]"
    run(["ffmpeg", "-y", "-hide_banner", *inputs, "-filter_complex", af, "-map", "0:v", "-map", "[a]",
         "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-profile:v", "high",
         "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-t", f"{total:.3f}", out_path])
    print(f"done: {out_path} ({total:.2f}s)")


if __name__ == "__main__":
    main()

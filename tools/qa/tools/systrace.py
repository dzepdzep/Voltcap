#!/usr/bin/env python3
"""VoltCap QA — where frame time goes, from an on-device `atrace` text capture (no Perfetto UI / trace_processor needed).

  sh vqa.sh atrace NAME SECONDS   (on the handheld, while a scenario runs)  →  out/NAME.atrace
  python3 systrace.py NAME.atrace [--pid PID] [--top 30] [--frames 15]

Reads the ftrace text (tracing_mark_write B/E/C lines), rebuilds the nested sections per thread and prints:
  * the app's main-thread frames (Choreographer#doFrame): count, median, p95, p99, max, how many over the panel budget;
  * the slowest frames with their biggest child sections (what made that frame slow);
  * sections by total time / count / max on the main thread and the RenderThread (vcTrace names "VC:…", Compose's
    "Recomposer:…", "Compose:…", "measure", "layout", "draw", "Upload …", "shader compile", "Bitmap decode" …).
"""
import argparse, collections, re, statistics

LINE = re.compile(r"^\s*(?P<task>.+?)-(?P<tid>\d+)\s+(?:\(\s*(?P<tgid>[\d-]+)\)\s+)?\[\d+\]\s+\S+\s+(?P<ts>\d+\.\d+): tracing_mark_write: (?P<msg>.*)$")


def parse(path, pid=None):
    stacks = collections.defaultdict(list)
    slices = []  # (tid, task, name, start, end, depth, parent_index)
    tnames = {}
    for line in open(path, errors="replace"):
        m = LINE.match(line)
        if not m:
            continue
        msg = m.group("msg"); tid = int(m.group("tid")); ts = float(m.group("ts"))
        tnames[tid] = m.group("task").strip()
        if msg.startswith("B|"):
            parts = msg.split("|", 2)
            if pid and parts[1] != str(pid):
                continue
            name = parts[2] if len(parts) > 2 else "?"
            stacks[tid].append((name, ts, len(slices)))
            slices.append([tid, tnames[tid], name, ts, None, len(stacks[tid]) - 1, stacks[tid][-2][2] if len(stacks[tid]) > 1 else -1])
        elif msg.startswith("E"):
            if stacks[tid]:
                name, st, idx = stacks[tid].pop()
                slices[idx][4] = ts
    return [s for s in slices if s[4] is not None], tnames


def pct(v, p):
    if not v: return 0
    v = sorted(v); return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("trace"); ap.add_argument("--pid", type=int); ap.add_argument("--top", type=int, default=30)
    ap.add_argument("--frames", type=int, default=12); ap.add_argument("--budget", type=float, default=8.33)
    a = ap.parse_args()
    slices, tn = parse(a.trace, a.pid)
    # main thread = the thread with Choreographer#doFrame slices of the app (most of them)
    frame_threads = collections.Counter(s[0] for s in slices if s[2].startswith("Choreographer#doFrame"))
    if not frame_threads:
        print("no Choreographer#doFrame slices — was the app traced (atrace -a com.shelf.launcher)?"); return
    main_tid = frame_threads.most_common(1)[0][0]
    rt_tid = next((t for t, n in tn.items() if n.startswith("RenderThread") and any(s[0] == t for s in slices)), None)
    frames = [s for s in slices if s[0] == main_tid and s[2].startswith("Choreographer#doFrame") and s[5] == 0]
    durs = [(s[4] - s[3]) * 1000 for s in frames]
    print(f"main thread {tn.get(main_tid)}({main_tid}); frames {len(frames)}  median {statistics.median(durs):.2f} ms  "
          f"p95 {pct(durs,95):.2f}  p99 {pct(durs,99):.2f}  max {max(durs):.2f}  over {a.budget} ms: {sum(1 for d in durs if d > a.budget)}")
    children = collections.defaultdict(list)
    for i, s in enumerate(slices):
        if s[6] >= 0: children[s[6]].append(i)
    idx_of = {id(s): i for i, s in enumerate(slices)}

    def walk(i, depth, out, limit=4):
        if depth > limit: return
        for c in sorted(children.get(i, []), key=lambda c: -(slices[c][4] - slices[c][3]))[:5]:
            d = (slices[c][4] - slices[c][3]) * 1000
            if d < 0.5: continue
            out.append("  " * depth + f"{d:7.2f}  {slices[c][2][:110]}")
            walk(c, depth + 1, out, limit)

    print(f"\nslowest {a.frames} frames (start s, ms):")
    for s in sorted(frames, key=lambda s: -(s[4] - s[3]))[:a.frames]:
        i = idx_of[id(s)]
        out = []
        walk(i, 1, out)
        print(f"{s[3]:.3f}  {(s[4]-s[3])*1000:6.2f}  {s[2]}")
        print("\n".join(out))
    for label, tid in (("MAIN", main_tid), ("RENDER", rt_tid)):
        if tid is None: continue
        agg = collections.defaultdict(lambda: [0, 0.0, 0.0])
        for s in slices:
            if s[0] != tid: continue
            d = (s[4] - s[3]) * 1000
            n = re.sub(r"\d{3,}", "#", s[2])[:80]
            g = agg[n]; g[0] += 1; g[1] += d; g[2] = max(g[2], d)
        print(f"\n{label} sections by total ms (count, total, max):")
        for n, (c, t, mx) in sorted(agg.items(), key=lambda kv: -kv[1][1])[:a.top]:
            print(f"{t:9.1f}  {c:6d}  {mx:7.2f}  {n}")


if __name__ == "__main__":
    main()

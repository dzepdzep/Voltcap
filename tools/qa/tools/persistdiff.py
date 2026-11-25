#!/usr/bin/env python3
"""682bd: what changed between two copies of the QA app's settings (persist682.sh snap_*.json, or snap_*.tar).

  python3 persistdiff.py RUN_DIR            every consecutive pair of snap_*.tar in RUN_DIR
Prints, per step, each file / key added, removed or changed. Keys that change on their own by design (time stamps, counters,
session state) are listed under VOLATILE and do not count. Exit 0 = no other change anywhere, 1 = changes, 2 = no input.
"""
import glob, json, os, re, sys, tarfile
import xml.etree.ElementTree as ET

# Bookkeeping that moves by itself: the clock, counts, caches of what was seen.
VOLATILE = re.compile(r"(_at$|_ms$|_time$|time$|stamp|_since|last_|_last|count|seen|session|warm|cache|_ts$|^ts_|recent|"
                      r"open_game|open_since|playtime_own|launch|crash|frame|battery|lastScan|scan_|_v\d+$|"
                      # the tuner's own records of a (simulated) play session: what it cost, what AutoTDP learned
                      r"^measured$|^learned$|"
                      # when the update check last ran
                      r"^last$)", re.I)
VOLATILE_FILES = {"session", "crash_guard", "migrations", "sync_status", "launch_context", "achievement_seen", "running_games",
                  "emulator_guard", "quick_resume", "game_extras", "sync_log", "theme_rotation_state", "other_devices",
                  "WebViewChromiumPrefs", "charge_log", "battery_log", "frame_watch", "art_cache", "metadata_cache",
                  # the battery watcher's own running totals (CPU / wall time in front, screen off)
                  "battery_watch"}


def load(tar):
    if tar.endswith(".json"):  # QaPrefsDump: {file: {key: "type:value"}}
        return json.load(open(tar, encoding="utf-8"))
    out = {}
    with tarfile.open(tar) as t:
        for m in t.getmembers():
            if not m.isfile() or not m.name.endswith(".xml"):
                continue
            name = os.path.basename(m.name)[:-4]
            try:
                root = ET.fromstring(t.extractfile(m).read())
            except ET.ParseError:
                out[name] = {"<unreadable>": "?"}
                continue
            vals = {}
            for e in root:
                k = e.get("name")
                if e.tag == "set":
                    vals[k] = "set:" + "|".join(sorted((c.text or "") for c in e))
                elif e.tag == "string":
                    vals[k] = "s:" + (e.text or "")
                else:
                    vals[k] = e.tag + ":" + str(e.get("value"))
            out[name] = vals
    return out


def diff(a, b):
    real, vol = [], []
    for f in sorted(set(a) | set(b)):
        x, y = a.get(f, {}), b.get(f, {})
        for k in sorted(set(x) | set(y)):
            if x.get(k) == y.get(k):
                continue
            what = "added" if k not in x else "removed" if k not in y else "changed"
            line = f"{f} › {k}: {what}" + ("" if what != "changed" else f" ({str(x[k])[:60]} -> {str(y[k])[:60]})")
            (vol if f in VOLATILE_FILES or VOLATILE.search(k) else real).append(line)
    return real, vol


def main(argv):
    if len(argv) < 2:
        print(__doc__); return 2
    snaps = sorted(glob.glob(os.path.join(argv[1], "snap_*.tar")) + glob.glob(os.path.join(argv[1], "snap_*.json")))
    if len(snaps) < 2:
        print("INFRA: fewer than two copies in", argv[1]); return 2
    bad = 0
    loaded = [(os.path.basename(s)[5:].rsplit(".", 1)[0], load(s)) for s in snaps]
    for (n1, a), (n2, b) in zip(loaded, loaded[1:]):
        real, vol = diff(a, b)
        print(f"== {n1} -> {n2}: {'PASS' if not real else 'CHANGED'} ({len(real)} changes, {len(vol)} volatile)")
        for r in real:
            print("   ", r)
        bad += len(real)
    first, last = loaded[0], loaded[-1]
    real, vol = diff(first[1], last[1])
    print(f"== overall {first[0]} -> {last[0]}: {'PASS' if not real else 'CHANGED'} ({len(real)} changes, {len(vol)} volatile)")
    for r in real:
        print("   ", r)
    return 0 if bad == 0 and not real else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))

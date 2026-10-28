#!/usr/bin/env python3
"""combocheck.py - verdict for one combination-matrix device run (device/combos.sh), from its VoltcapQA log.

  python3 combocheck.py LOG [--need-tiles] [--need-stops N]

LOG lines used (logcat -s VoltcapQA, written by the app with the test hooks on, and by combos.sh):
  <ms> category tiles <n>: <key>,<key>,...      the category screen's tiles (every time they are rebuilt)
  <ms> category stop <key> <i>/<n>              an L2 / R2 step: the stop reached, its place, how many stops there are
  COMBO pid_before=<p> pid_after=<p> crashes=<n> focus=<package>      written by combos.sh around the run

Checks (all must hold for PASS):
  * the app process survived (pid_before == pid_after), no crash buffer lines, Voltcap still in front;
  * every stop reached is a tile on the category screen, and the stops' total equals the tile count (one list, update 367);
  * --need-tiles: a tiles line exists; --need-stops N: at least N stop lines exist (the run really navigated).
Exit 0 PASS, 1 FAIL, 2 INFRA (missing / unusable evidence: never a PASS). Prints one line per finding, then the verdict.
"""
import re
import sys


def parse(text):
    tiles, stops, combo = None, [], {}
    for ln in text.splitlines():
        m = re.search(r"category tiles (\d+): ?(.*)$", ln)
        if m:
            keys = [k for k in m.group(2).split(",") if k]
            if len(keys) != int(m.group(1)):
                return None, None, None, f"tiles line says {m.group(1)} but lists {len(keys)}"
            tiles = keys
            continue
        m = re.search(r"category stop (\S+) (\d+)/(\d+)\s*$", ln)
        if m:
            stops.append((m.group(1), int(m.group(2)), int(m.group(3))))
            continue
        if "COMBO " in ln:
            combo.update(dict(re.findall(r"(\w+)=(\S+)", ln.split("COMBO ", 1)[1])))
    return tiles, stops, combo, None


def verdict(text, need_tiles=False, need_stops=0):
    out = []
    tiles, stops, combo, bad = parse(text)
    if bad:
        return 2, [f"INFRA: {bad}"]
    for k in ("pid_before", "pid_after", "crashes", "focus"):
        if k not in combo:
            return 2, [f"INFRA: no COMBO {k} line (combos.sh did not finish or the log was cut)"]
    if need_tiles and tiles is None:
        return 2, ["INFRA: no 'category tiles' line (hooks off, or the category screen never opened)"]
    if len(stops) < need_stops:
        return 2, [f"INFRA: {len(stops)} stop lines, {need_stops} wanted (the run did not navigate)"]
    fail = []
    if combo["pid_before"] != combo["pid_after"]:
        fail.append(f"app restarted or crashed (pid {combo['pid_before']} -> {combo['pid_after']})")
    if combo["crashes"] != "0":
        fail.append(f"{combo['crashes']} lines in the crash buffer")
    if combo["focus"] != "com.shelf.launcher":
        fail.append(f"Voltcap not in front at the end (focus {combo['focus']})")
    if tiles is not None and stops:
        extra = sorted({k for k, _, _ in stops if k not in tiles})
        if extra:
            fail.append(f"L2/R2 stopped on categories the category screen does not list: {extra}")
        totals = sorted({n for _, _, n in stops})
        if totals != [len(tiles)]:
            fail.append(f"L2/R2 stop total {totals} != {len(tiles)} tiles")
        seen = {k for k, _, _ in stops}
        if len(stops) >= 10: out.append(f"info: {len(stops)} steps reached {len(seen)} of {len(tiles)} categories; not reached (empty ones are stepped past): "
                   f"{[k for k in tiles if k not in seen]}")
    return (1 if fail else 0), out + [f"FAIL: {f}" for f in fail]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    a = sys.argv
    try:
        text = open(a[1], encoding="utf-8", errors="replace").read()
    except OSError as e:
        print(f"INFRA: cannot read {a[1]}: {e}")
        sys.exit(2)
    code, lines = verdict(text, "--need-tiles" in a, int(a[a.index("--need-stops") + 1]) if "--need-stops" in a else 0)
    print("\n".join(lines))
    print({0: "PASS", 1: "FAIL", 2: "INFRA"}[code])
    sys.exit(code)


if __name__ == "__main__":
    main()

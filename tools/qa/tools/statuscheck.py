#!/usr/bin/env python3
"""677d (owner 16 Nov, permanent: readable status information, no duplicate UI): status-bar fit + duplicate-avatar gate, from the
ACTUAL laid-out bounds in the QaClip dumps (ui/QaClip.kt "statusBars", "avatars", "texts"; tags from ui/StatusQa.kt).

usage: python3 tools/qa/tools/statuscheck.py RUN_DIR|DUMP.json [...] [--json out.json]
Per dump, the focused window:
  FAIL  a text inside the status cluster squeezed (its one-line width doesn't fit its box: needW > boxW + 1 px, sliding or not -
        RP Classic "RA points squashed"), or cut in height (box under 60 % of its line height), or two of its texts over each
        other (> 2 px both ways), or the cluster cut by the screen
  FAIL  more than one picture of the playing profile on screen (avatars tagged "bar" / "console" / "channel")
  NOT TESTED  no status cluster in the dump (the bar is off) - the avatar check still runs
  INFRA a dump without the "statusBars" key (a build before 677d)
Separate from badgecheck.py (tile badges). Exit code: qalib (0 = PASS only).
"""
import glob, json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib  # noqa: E402

TOL = 1
VCUT = 0.6


def rect(a):
    x, y, w, h = a
    return (x, y, x + w, y + h)


def inside(pt, r):
    return r[0] <= pt[0] <= r[2] and r[1] <= pt[1] <= r[3]


def visible(b):
    # (677h, RP Classic c677an: StatusFit's measured-only probe levels stay in the unmerged semantics tree with a stale
    # bounds - "placed": false - and are not on screen: neither a second picture nor a cut cluster.)
    if b.get("placed", True) is False:
        return False
    s = b.get("shown") or [0, 0, 0, 0]
    return s[2] > 0 and s[3] > 0


def check_dump(path):
    name = os.path.basename(path)
    try:
        d = json.load(open(path, encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        return [qalib.Result(name, qalib.INFRA, f"unreadable dump: {e}")]
    sw, sh = (d.get("screen") or [0, 0])[:2]
    wins = [w for w in d.get("windows", []) if w.get("focus")]
    if not wins:
        return [qalib.Result(name, qalib.INFRA, "no focused window in the dump")]
    out = []
    for w in wins:
        if "statusBars" not in w:
            return [qalib.Result(name, qalib.INFRA, "dump has no status-bar bounds (build before 677d)")]
        avs = [a for a in w.get("avatars", []) if visible(a)]
        if len(avs) > 1:
            out.append(qalib.Result(f"{name} avatars", qalib.FAIL,
                                    f"{len(avs)} pictures of the profile on screen: " + ", ".join(f"{a['where']}@{a['shown']}" for a in avs)))
        else:
            out.append(qalib.Result(f"{name} avatars", qalib.PASS, f"{len(avs)} profile picture"))
        for a in w.get("avatars", []):
            f, s_ = a["full"], a.get("shown") or [0, 0, 0, 0]
            if f[2] > 0 and visible(a) and (f[2] - s_[2] > TOL or f[3] - s_[3] > TOL):
                out.append(qalib.Result(f"{name} avatars", qalib.FAIL, f"{a['where']} picture cut: laid out {f[2]}x{f[3]}, shown {s_[2]}x{s_[3]}"))
        bars = [b for b in w["statusBars"] if visible(b)]
        if not bars:
            out.append(qalib.Result(f"{name} status", qalib.NOT_TESTED, "no status bar on screen"))
            continue
        for b in bars:
            bad = []
            f, s = b["full"], b["shown"]
            if f[2] - s[2] > TOL or f[3] - s[3] > TOL:
                bad.append(f"cluster cut: laid out {f[2]}x{f[3]} px, shown {s[2]}x{s[3]}")
            if sw and sh and (s[0] < -TOL or s[1] < -TOL or s[0] + s[2] > sw + TOL or s[1] + s[3] > sh + TOL):
                bad.append(f"cluster off the screen at {s}")
            br = rect(f)
            texts = []
            for t in w.get("texts", []):
                if t.get("placed", True) is False:
                    continue
                x, y, tw, th = t["full"]
                if not inside((x + tw / 2, y + th / 2), br):
                    continue
                # (s677n, RP Nova narrowed: the profile initial squeezed to 0 px wide - not drawn at all - and the points broken
                # onto two lines ("1234 / 5678"); a status text is one line, at its own width.)
                if (t.get("lines") or 1) > 1:
                    bad.append(f"{t.get('text', '')!r} broken onto {t['lines']} lines")
                if t.get("needW") and t.get("boxW") == 0:
                    bad.append(f"{t.get('text', '')!r} squeezed to nothing (needs {t['needW']} px)")
                if not visible(t):
                    continue
                texts.append(t)
                nw, bw = t.get("needW"), t.get("boxW")
                if nw is not None and bw is not None and nw > bw + TOL:
                    bad.append(f"{t.get('text', '')!r} squeezed: needs {nw} px, has {bw}")
                tH, bH = t.get("textH"), t.get("boxH")
                if tH and bH is not None and 0 < bH < VCUT * tH:
                    bad.append(f"{t.get('text', '')!r} cut in height: {bH} px of {tH}")
                if t.get("ellipsis"):
                    bad.append(f"{t.get('text', '')!r} ends in an ellipsis")
            for i in range(len(texts)):
                for j in range(i + 1, len(texts)):
                    a, c = rect(texts[i]["shown"]), rect(texts[j]["shown"])
                    ox = min(a[2], c[2]) - max(a[0], c[0]); oy = min(a[3], c[3]) - max(a[1], c[1])
                    if ox > 2 and oy > 2:
                        bad.append(f"{texts[i].get('text', '')!r} over {texts[j].get('text', '')!r} ({ox}x{oy} px)")
            out.append(qalib.Result(f"{name} status", qalib.FAIL if bad else qalib.PASS, "; ".join(bad) or f"{len(texts)} text(s) whole",
                                    evidence=f"cluster@{f}"))
    return out


def main(argv):
    json_out, paths = None, []
    it = iter(argv)
    for a in it:
        if a == "--json":
            json_out = next(it)
        else:
            paths.append(a)
    files = []
    for p in paths:
        files += sorted(glob.glob(os.path.join(p, "**", "*.json"), recursive=True)) if os.path.isdir(p) else [p]
    files = [f for f in files if os.path.basename(f) not in ("ledger.json",)]
    if not files:
        print("statuscheck: no dumps given - NOT TESTED")
        return qalib.EXIT[qalib.NOT_TESTED]
    results = []
    for f in files:
        results += check_dump(f)
    real = [r for r in results if r.status != qalib.NOT_TESTED]
    return qalib.finish(real if real else results, "Status bar + avatar gate", json_out)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

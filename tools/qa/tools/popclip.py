#!/usr/bin/env python3
"""VoltCap QA — popup gate (update 467, QA rule R26). Reads one popclip.sh run (screenshots + QaClip dumps) and grades every
layout x popup style in SEPARATE categories — a popup is PASS only when every applicable category passes:

  open      the popup opened (its main step shows a Play / Continue action) and B closed it again (closed step shows none)
  geometry  geomcheck --clip on the main, More and page steps: nothing cut, on screen, no overlaps, touch size, focus whole
  visual    geomcheck's pixel check on the same steps (each action button drawn down to its laid-out bottom)
  focus     the main step has a focused control and it is the first action (Play / Continue)
  buttons   a TAP on More (where there is one) changed the controls, a TAP (touch) on the
            Details action changed the page (a different set of controls), and a TAP on Play asked for the launch (blocked,
            <n>_play.txt); no Details action and no Play tap = NOT TESTED
  settle    the steps were captured after their animation settled and the closed step left no popup action behind
            (frame-by-frame animation is NOT covered here — reported as such)

Missing screenshots / dumps are INFRA, a style the layout doesn't offer (a row style without a row, from the dump's hasRow)
is SKIP (n/a) — never PASS.
  python3 popclip.py RUN_DIR [--json OUT.json] [--md OUT.md] [--ledger builds.json] [--expect-build ID] [--legacy]\nUpdate 474: a run counts only with its manifest.txt (device/evidence.sh) bound to a build in tools/qa/builds.json (evidence.py);\notherwise every combination is INFRA (--legacy: an old run is graded but its PASSes are NOT TESTED).
RUN_DIR holds <n>_{main,more,page,closed}.png from popclip.sh and qa_clip/<TAG>_<n>_<step>.json (or <n>_<step>.json).
Exit: 0 all PASS, 1 any FAIL, 2 INFRA, 3 NOT TESTED (qalib).
"""
import glob, json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import qalib
import geomcheck
import readcheck

STEPS = ("home", "main", "more", "page", "closed")
NOSHOTS = "--steps-without-shots" in sys.argv
ROW = ("ROW_OPENS", "ROW_REVEAL", "SHARED")
CATS = ("open", "geometry", "visual", "focus", "buttons", "settle", "readability")
ACT = geomcheck.PLAY_RE  # the window's Play / Continue in every layout's own word


def labels(d):
    return [geomcheck._label(n) for w in (d or {}).get("windows") or [] for n in w.get("nodes") or [] if n.get("kind") == "control"]


def covering_sheet(page, main):
    """True when the page step has a control the main step didn't, covering >= 90 % of the screen (a full-screen sheet)."""
    sw, sh = ((page or {}).get("screen") or [0, 0])[:2]
    if sw <= 0 or sh <= 0: return False
    mk = {geomcheck._key(n) for w in (main or {}).get("windows") or [] for n in w.get("nodes") or []}
    for w in page.get("windows") or []:
        for n in w.get("nodes") or []:
            f = n.get("full") or []
            if n.get("kind") == "control" and len(f) == 4 and geomcheck._key(n) not in mk and f[2] * f[3] >= 0.9 * sw * sh:
                return True
    return False


def under_sheet(page, main, tol=16):
    """The page's nodes that are the main step's (same label, box within [tol] px: a focus scale moves them a little)."""
    mn = [(geomcheck._label(n), n.get("full") or []) for w in (main or {}).get("windows") or [] for n in w.get("nodes") or []]
    out = []
    for w in page.get("windows") or []:
        for n in w.get("nodes") or []:
            f = n.get("full") or []
            if len(f) == 4 and any(l == geomcheck._label(n) and len(g) == 4 and all(abs(a - b) <= tol for a, b in zip(f, g)) for l, g in mn):
                out.append(n)
    return out


def own_labels(d, home):
    """The controls of d that the home step didn't have (Custom's home has a Play and a Details button of its own)."""
    hk = {geomcheck._key(n) for w in (home or {}).get("windows") or [] for n in w.get("nodes") or []}
    return [geomcheck._label(n) for w in (d or {}).get("windows") or [] for n in w.get("nodes") or []
            if n.get("kind") == "control" and geomcheck._key(n) not in hk]


def load(run, name):
    tag = os.path.basename(os.path.normpath(run))
    for p in (os.path.join(run, "qa_clip", tag + "_" + name + ".json"), os.path.join(run, "qa_clip", name + ".json"), os.path.join(run, name + ".json")):
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding="utf-8"))
            except ValueError:
                return None
    return None


def grade(run, n):
    out = {}
    png = {s: os.path.join(run, f"{n}_{s}.png") for s in STEPS}
    # (A step counts only with its screenshot from this run: a dump without one is left over from an earlier run.)
    # (--steps-without-shots: a run pulled without the big step screenshots — the Odin's — keeps its step dumps; those
    # steps' pixels are then NOT TESTED.)
    d = {s: load(run, f"{n}_{s}") if (os.path.exists(png[s]) or (NOSHOTS and s != "main")) else None for s in STEPS}
    if d["main"] is None or not os.path.exists(png["main"]):
        return {k: (qalib.INFRA, "no main screenshot / dump") for k in CATS}
    # A row style (Opens / Reveal / Shared) where the layout draws no row is not offered (PopupStyle.offered): SKIP, n/a.
    style = n.rsplit("_", 1)[-1] if n.rsplit("_", 1)[-1] in ("OWN", "BANNER", "CLASSIC") else next((s for s in ROW if n.endswith("_" + s)), "")
    if style in ROW and not any((d[s] or {}).get("hasRow") for s in STEPS):
        return {k: (qalib.SKIP, "n/a: row style, this layout draws no row (not offered)") for k in CATS}
    ml = labels(d["main"])
    # (Against the home step when there is one: a home screen with its own Play button — Custom — isn't a popup.)
    # (A key hint's letter in front — Card binder's "A Play" chip — is the button, not the word.)
    acts = lambda dd: sorted(l for l in labels(dd) if ACT.match(re.sub(r"^[ABXY]\s+", "", l)))
    home_act = acts(d["home"]) if d["home"] else []
    opened = len(acts(d["main"])) > len(home_act)
    lp = os.path.join(run, "log.txt")
    # (Whole log lines: "CLASSIC_OWN …" must not match "POCKET_CLASSIC_OWN …".)
    loglines = set(open(lp, encoding="utf-8").read().splitlines()) if os.path.exists(lp) else set()
    first_a = f"{n} first A opened nothing" in loglines
    closed_ok = d["closed"] is not None and len(acts(d["closed"])) <= len(home_act)
    # A layout whose home screen shows its own Play key (Pocket's "A Play Game", Card binder's "A Play"): A plays from there
    # and opens no popup — the popup isn't reachable by A, so nothing about it was tested.
    if not opened and f"{n} A plays" in loglines:
        return {k: (qalib.NOT_TESTED, "A starts the game here (launch blocked): this layout opens no popup on A") for k in CATS}
    if not opened and home_act and d["home"] is not None:
        return {k: (qalib.NOT_TESTED, f"A plays from the home screen's own key ({home_act[0]}): no popup opened by A") for k in CATS}
    out["open"] = (qalib.FAIL, "the first A opened nothing (a second A did)") if opened and first_a else \
        (qalib.PASS, "opened, B closed") if opened and closed_ok else \
        (qalib.FAIL, ("did not open (no Play / Continue)" if not opened else "still open after B B")) if d["closed"] is not None or not opened else \
        (qalib.INFRA, "no closed-step dump")
    # (Update 474: the window must show the game you were on — the app's own focus at the home step vs the game the
    # window holds. Dumps before 474 carry neither: not checked there.)
    hf, pg = (d["home"] or {}).get("focusedGame"), (d["main"] or {}).get("popupGame")
    # (Update 474: the run is on the game it names, or it measured something else — the Odin run on "WWE" whose home was on
    # the RetroArch app: every category INFRA (not this content), not a pass or a fail of the window.)
    gfp = os.path.join(run, "game.txt")
    want_g = re.sub(r"[^a-z0-9]", "", open(gfp, encoding="utf-8").read().lower()) if os.path.exists(gfp) else ""
    hit = re.search(r"-> (.+)$", open(os.path.join(run, "log.txt"), encoding="utf-8", errors="replace").read(), re.M) if os.path.exists(os.path.join(run, "log.txt")) else None
    want_full = re.sub(r"[^a-z0-9]", "", hit.group(1).lower()) if hit else want_g
    if hf and want_g and want_full not in re.sub(r"[^a-z0-9]", "", hf.lower()) and want_g not in re.sub(r"[^a-z0-9]", "", hf.lower()):
        return {k: (qalib.INFRA, f"the home screen was on another game ('{hf[-40:]}'), not the run's '{want_g}': this content was not tested") for k in CATS}
    if opened and hf and pg and hf != pg and not hf.endswith(pg) and not pg.endswith(hf):
        out["open"] = (qalib.FAIL, f"the window shows another game: home on '{hf[-40:]}', window '{pg[-40:]}'")
    if not opened:
        for k in CATS[1:]:
            out[k] = (qalib.NOT_TESTED, "popup did not open")
        return out
    geo, vis = [], []
    for s in ("main", "more", "page"):
        if d[s] is None:
            if s == "main": geo.append(qalib.Result(s, qalib.INFRA, "no dump"))
            continue
        base = d["home"] or d["closed"]
        # (Update 469: a page that is a full-screen sheet over the popup — Bubbles' LiveArea opens the full details over itself
        # in the same window — leaves the popup's own keys composed under it; they are covered, not overlapping. Only when
        # the page brings a new control covering >= 90 % of the screen are the main step's nodes left out too.)
        if s == "page" and d["main"] is not None and covering_sheet(d["page"], d["main"]):
            base = {"windows": ((base or {}).get("windows") or []) + [{"nodes": under_sheet(d["page"], d["main"])}]}
        rs = geomcheck.clip_evaluate(d[s], png=png[s] if os.path.exists(png[s]) else None, baseline=base)
        for r in rs:
            (vis if r.name == "pixels" else geo).append(qalib.Result(f"{s} {r.name}", r.status, r.reason))
    def fold(rs):
        bad = [r for r in rs if r.status != qalib.PASS]
        st = qalib.verdict(rs)
        return (st, "; ".join(f"{r.name}: {r.reason}" for r in bad)[:400] if bad else "all steps pass")
    out["geometry"] = fold(geo)
    # (Update 476, QA improvement 1: readability / continuity graded on its own — text drawn over text (FAIL, from the dump),
    # contrast on the screenshot and small source pictures (WARN: pixel heuristics). The home step is read too (its own
    # texts on its own pictures); the window's steps only for the window's own texts.)
    rd = []
    for s in ("home", "main", "more", "page"):
        if d[s] is None:
            continue
        rd += readcheck.evaluate(d[s], png[s] if os.path.exists(png[s]) else None, step=s,
                                 baseline=None if s == "home" else (d["home"] or d["closed"]))
    out["readability"] = (qalib.verdict(rd), "; ".join(f"{r.name}: {r.reason}" for r in rd
                          if r.status != qalib.PASS or r.reason.startswith("WARN"))[:400] or "all steps pass")
    if out["readability"][0] == qalib.PASS and "WARN" in out["readability"][1]:
        out["readability"] = (qalib.PASS, "WARN: " + out["readability"][1].replace("WARN: ", ""))
    out["visual"] = fold(vis)
    foc = [geomcheck._label(x) for w in d["main"].get("windows") or [] for x in w.get("nodes") or [] if x.get("focused")]
    # (The home screen's game can stay focused under a popup drawn in the same window; the popup's own focus is what counts.)
    act = [x for x in foc if ACT.match(x)]
    out["focus"] = (qalib.PASS, f"focus on {act[0]}") if act else \
        (qalib.FAIL, f"no window action has the controller focus (focused: {', '.join(foc) or 'nothing'})")
    more_l = labels(d["more"]) if d["more"] else None
    page_l = labels(d["page"]) if d["page"] else None
    has_more = any(re.search(r"(?i)(more|\u22ef)$", l) for l in own_labels(d["main"], d["home"]))
    log = loglines
    probs, notes = [], []
    if has_more:
        if more_l is None and f"{n} More not on screen" in log: notes.append("More is scrolled out of view: not tapped")
        elif more_l is None: probs.append("More shown but its step is missing")
        elif sorted(more_l) == sorted(ml): probs.append("a tap on More changed nothing")
        else: notes.append("a tap on More opens more")
    else:
        notes.append("no More here")
    # The touch tap on Play (popclip.sh, launches blocked): it must ask for the launch.
    pf = os.path.join(run, f"{n}_play.txt")
    if os.path.exists(pf):
        txt = open(pf, encoding="utf-8").read()
        m = re.search(r"blocked=(\d+)", txt)
        # (Update 469: the blocked launch must be for the game the run is on — popclip.sh writes the logged key and game.txt.)
        keys = re.findall(r"^key=(.*)$", txt, re.M)
        gf = os.path.join(run, "game.txt")
        want = re.sub(r"[^a-z0-9]", "", open(gf, encoding="utf-8").read().lower()) if os.path.exists(gf) else ""
        wrong = [k for k in keys if want and want not in re.sub(r"[^a-z0-9]", "", k.lower())]
        if m and int(m.group(1)) > 0 and wrong: probs.append("a tap on Play asked to launch another game")
        elif m and int(m.group(1)) > 0: notes.append("a tap on Play asks for the launch (blocked)" + (" of the right game" if keys and want else ""))
        else: probs.append("a tap on Play asked for no launch")
    else:
        notes.append("Play tap not recorded")
    played = any(x.startswith("a tap on Play asks") for x in notes)
    if page_l is not None:
        # (Every word on screen, not only the controls: a tab page — Storage's DETAILS — can keep the same keys.)
        words = lambda dd: sorted(geomcheck._label(x) for w in (dd or {}).get("windows") or [] for x in w.get("nodes") or [])
        if words(d["page"]) == words(d["main"]): probs.append("a tap on Details changed nothing")
        else: notes.append("a tap on Details opens its page")
    elif any(re.match(r"(?i)^(" + "|".join(re.escape(w) for w in geomcheck.DETAILS_WORDS) + r")$", l) for l in own_labels(d["main"], d["home"])):
        probs.append("Details shown but its tapped page is missing")
    else:
        notes.append("no Details action (a window that is the details itself)")
    # FAIL on any broken button; PASS only with the Play tap proven; otherwise NOT TESTED (never a pass on missing evidence).
    out["buttons"] = (qalib.FAIL, "; ".join(probs)) if probs else \
        (qalib.PASS, "; ".join(notes)) if played else (qalib.NOT_TESTED, "; ".join(notes))
    out["settle"] = (qalib.PASS, "settled stills whole, nothing left after close (frame-by-frame animation not covered)") if closed_ok and out["geometry"][0] == qalib.PASS \
        else (out["geometry"][0] if out["geometry"][0] != qalib.PASS else qalib.FAIL, "see geometry" if out["geometry"][0] != qalib.PASS else "popup action left after close")
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    run = sys.argv[1]
    names = sorted({os.path.basename(p)[:-len("_main.png")] for p in glob.glob(os.path.join(run, "*_main.png"))})
    if not names:
        qalib.finish([qalib.Result("popup sweep", qalib.INFRA, f"no *_main.png in {run}")], "Popup gate")
    # (Update 474: every result is bound to the run's manifest — exact build, device, configuration, fresh files. Without a
    # valid binding nothing passes: INFRA; --legacy grades an old run but its PASSes become NOT TESTED.)
    import evidence
    legacy = "--legacy" in sys.argv
    led = sys.argv[sys.argv.index("--ledger") + 1] if "--ledger" in sys.argv else evidence.LEDGER
    exp = sys.argv[sys.argv.index("--expect-build") + 1] if "--expect-build" in sys.argv else None
    ev_st, ev_info, ev_why = evidence.check_run(run, led, exp)
    notes = (ev_info or {}).get("_notes") or []
    style_set = {}
    for x in notes:
        p = x.split()
        if len(p) == 3 and p[0] == "style_set":
            style_set[p[1]] = p[2] != "0"
    rows, results = [], []
    for n in names:
        g = grade(run, n)
        # (D242: "not offered" counts only when the app confirmed the style was set for this combination.)
        if all(v[0] == qalib.SKIP for v in g.values()) and ev_info.get("manifest") and not style_set.get(n):
            g = {k: (qalib.INFRA, "n/a claimed but the app never confirmed the style was set (D242)") for k in g}
        if ev_st != qalib.PASS:
            if legacy:
                g = {k: ((qalib.NOT_TESTED, "legacy run, not bound to a build: " + v[1]) if v[0] == qalib.PASS else v) for k, v in g.items()}
            else:
                g = {k: (qalib.INFRA, "evidence: " + ev_why) if v[0] != qalib.SKIP else (qalib.INFRA, "evidence: " + ev_why) for k, v in g.items()}
        overall = qalib.verdict([qalib.Result(k, v[0], v[1]) for k, v in g.items()])
        rows.append((n, overall, g))
        results.append(qalib.Result(n, overall, "; ".join(f"{k}={v[0]}" for k, v in g.items() if v[0] != qalib.PASS) or "all categories PASS"))
    if "--md" in sys.argv:
        cats = ("open", "geometry", "visual", "focus", "buttons", "settle")
        with open(sys.argv[sys.argv.index("--md") + 1], "w", encoding="utf-8") as fh:
            fh.write("| layout_style | overall | " + " | ".join(cats) + " |\n|" + "---|" * (len(cats) + 2) + "\n")
            for n, ov, g in rows:
                fh.write(f"| {n} | {ov} | " + " | ".join(g[c][0] for c in cats) + " |\n")
            fh.write("\n## Problems\n")
            for n, ov, g in rows:
                for c in cats:
                    if g[c][0] != qalib.PASS: fh.write(f"- {n} {c} {g[c][0]}: {g[c][1]}\n")
    jp = sys.argv[sys.argv.index("--json") + 1] if "--json" in sys.argv else None
    # n/a rows (SKIP) are listed but don't decide the verdict; the applicable ones do (none applicable = NOT TESTED).
    appl = [r for r in results if r.status != qalib.SKIP]
    print("Evidence: " + (evidence.label(ev_info) if ev_st == qalib.PASS else f"{ev_st}: {ev_why}"))
    print(f"Popup gate (applicable, n/a rows left out): {qalib.verdict(appl)}")
    print(qalib.report_text(results, "Popup gate"))
    if jp:
        qalib.write_json(jp, results, {"evidence": {"status": ev_st, "reason": ev_why,
                                                    "label": evidence.label(ev_info) if ev_st == qalib.PASS else None,
                                                    "manifest": {k: v for k, v in (ev_info or {}).items() if k not in ("_notes", "build")},
                                                    "build": (ev_info or {}).get("build")}})
    sys.exit(qalib.EXIT[qalib.verdict(appl)])


if __name__ == "__main__":
    main()

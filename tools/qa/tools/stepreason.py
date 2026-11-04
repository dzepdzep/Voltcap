#!/usr/bin/env python3
"""Update 476 (QA improvement 3): why a step of a popup run has no screenshot — the layout doesn't offer it (n/a), the
evidence is missing / the tool couldn't reach it (NOT CAPTURED), or the run never happened (NOT TESTED). No PDF library
needed (visualpdf.py and the CI self-tests both use it)."""
import json, os, re, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geomcheck


def load(run, name):
    tag = os.path.basename(os.path.normpath(run))
    for p in (os.path.join(run, "qa_clip", f"{tag}_{name}.json"), os.path.join(run, "qa_clip", f"{name}.json")):
        if os.path.exists(p):
            try:
                return json.load(open(p, encoding="utf-8"))
            except ValueError:
                return None
    return None


STEP_KEY = {"more": re.compile(r"(?i)(more|\u22ef)$"), "page": re.compile(r"(?i)^(details|info|game info|type nfo|summary|the story|catalogue card|card text|sleeve notes|contents|turn over|status)$")}


def missing_reason(run, lay, style, step):
    """Update 475 (QA improvement 3): why a step has no screenshot — the layout doesn't offer it (n/a), or the evidence is
    missing / the tool couldn't reach it (NOT CAPTURED), or the run never happened (NOT TESTED). Never guessed as a pass."""
    if not run or not os.path.isdir(run):
        return "NOT TESTED: no run"
    name = f"{lay}_{style}"
    log = ""
    lp = os.path.join(run, "log.txt")
    if os.path.exists(lp):
        log = open(lp, encoding="utf-8", errors="replace").read()
    if os.path.exists(os.path.join(run, f"{name}_{step}.png")):
        return None
    if load(run, f"{name}_{step}") is not None:
        return "NOT CAPTURED: the dump exists, the screenshot is missing (evidence failure)"
    main = load(run, f"{name}_main")
    if main is None:
        return "NOT CAPTURED: the window itself was not recorded"
    labs = [geomcheck._label(x) for w in main.get("windows") or [] for x in w.get("nodes") or []]
    key = STEP_KEY.get(step)
    if key is not None and not any(key.search((l or "").strip()) for l in labs):
        return "not offered: this layout's window has no " + ("More key" if step == "more" else "Details key")
    if step == "more" and f"{name} More not on screen" in log:
        return "NOT CAPTURED: More is scrolled out of view (the tool could not tap it)"
    return "NOT CAPTURED: the key exists but no step was recorded (tool / evidence failure)"



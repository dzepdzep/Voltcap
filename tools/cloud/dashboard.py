#!/usr/bin/env python3
"""Recount docs/claude/STATUS_DASHBOARD.md's generated sections from the evidence files (owner request 16 Nov 2026).

Sources (nothing else is counted):
  docs/REQUIREMENTS_MATRIX.md      P / G rows: the Status column
  docs/REQUEST_COVERAGE_529.md     A-F rows (13 Nov snapshot): the Impl and Next-action columns
  docs/claude/HARDWARE_QA_QUEUE.md HW-<n> requests and their status word
  docs/claude/progress.json        per-area evidence counts (hand-kept, each with its source)

Only the text between "<!-- GEN:<name> -->" and "<!-- /GEN:<name> -->" is replaced; every other line of the dashboard is
hand-written and kept. A percentage is printed only where both numbers are counted integers; everything else is shown as is
(UNKNOWN stays UNKNOWN).
  python3 tools/cloud/dashboard.py            rewrite the generated sections
  python3 tools/cloud/dashboard.py --check    exit 1 if the dashboard is out of date (for CI / the next run)
"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
P = lambda *a: os.path.join(ROOT, *a)
DASH = P("docs", "claude", "STATUS_DASHBOARD.md")

# Status word -> bucket. First match wins (most specific first).
BUCKETS = [
    ("BLOCKED-OWNER", "waiting for owner"), ("BLOCKED-HW", "waiting for hardware"), ("BLOCKED-REF", "blocked (references)"),
    ("BLOCKED", "blocked (other)"), ("DEFER", "deferred by owner"), ("DONE", "done"), ("APPROVED", "done"),
    ("VERIFY", "implemented, verification pending"), ("TESTED", "implemented, verification pending"), ("WATCH", "implemented, verification pending"),
    ("IN PROGRESS", "in progress"), ("OPEN", "not started / open"), ("NOT STARTED", "not started / open"),
]
ORDER = ["done", "implemented, verification pending", "in progress", "not started / open", "waiting for hardware",
         "waiting for owner", "blocked (references)", "blocked (other)", "deferred by owner", "unclassified"]


def bucket(text):
    """The LEADING status word decides ("VERIFY - ... before DONE" is VERIFY, not DONE)."""
    t = re.sub(r"[*_`]", "", text).strip().upper()
    t = t.replace("DONE IN CODE", "IN PROGRESS")  # code is not a finished requirement
    for word, b in BUCKETS:
        if t.startswith(word):
            return b
    for word, b in BUCKETS:  # a status word later in the cell, e.g. "Your library ... - BLOCKED-OWNER (your eyes)"
        if re.search(r"\b" + re.escape(word) + r"\b", t) and word not in ("DONE", "APPROVED"):
            return b
    return "unclassified"


# Older requests (13 Nov) name what they wait for in words; these phrases decide when no status word is there.
OWNER_WORDS = r"\byour\b|owner|approval|sign-in|decision|\bpick\b|say if|answer"
HW_WORDS = r"Flip 2|KONKR|Thor|Odin|RP Classic|on USB|hardware|sweep|screenshot|frames\.py|popclip"


def table_rows(path, pat):
    rows = []
    if not os.path.exists(path):
        return rows
    for line in open(path, encoding="utf-8"):
        if re.match(pat, line):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            rows.append(cells)
    return rows


def matrix_counts():
    rows = table_rows(P("docs", "REQUIREMENTS_MATRIX.md"), r"^\| [PG]\d+ \|")
    out = {}
    for r in rows:
        status = r[-1]
        b = bucket(status)
        # an owner-blocked row that is otherwise tested keeps its owner bucket; that is what the owner must see
        out.setdefault(b, []).append(r[0])
    return rows, out


def coverage_counts():
    """A-F rows of the 13 Nov snapshot. A row counts as closed only when its next action is none / '—' and it says DONE."""
    rows = table_rows(P("docs", "REQUEST_COVERAGE_529.md"), r"^\| [A-F]\d+ \|")
    out = {}
    for r in rows:
        nxt = r[-1]
        impl = r[2] if len(r) > 2 else ""
        if nxt.strip() in ("none", "—", "-") and "DONE" in (impl + " " + r[-2]).upper():
            b = "done"
        else:
            b = bucket(nxt)
            if b == "unclassified":
                if re.search(OWNER_WORDS, nxt, re.I): b = "waiting for owner"
                elif re.search(HW_WORDS, nxt, re.I): b = "waiting for hardware"
                elif re.search(r"DONE|IMPL|PARTIAL", impl, re.I): b = "in progress"
                else: b = "not started / open"
        out.setdefault(b, []).append(r[0])
    return rows, out


def hw_requests():
    path = P("docs", "claude", "HARDWARE_QA_QUEUE.md")
    reqs = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            m = re.match(r"^### (HW-\d+) (.*?) - (OPEN.*|IN PROGRESS.*|DONE.*|BLOCKED.*?)\s*$", line)
            if m:
                reqs.append((m.group(1), m.group(2), m.group(3)))
    return reqs


def pct(a, b):
    return f" ({100 * a // b} %)" if isinstance(a, int) and isinstance(b, int) and b > 0 and not isinstance(a, bool) else ""


def gen_overall():
    mrows, m = matrix_counts()
    crows, c = coverage_counts()
    total = len(mrows) + len(crows)
    lines = [f"Tracked requirement rows: **{total}** = {len(mrows)} in the requirements matrix (programme + recent) + "
             f"{len(crows)} older requests (A-F, status as of 13 Nov, re-verified only when touched).", "",
             "| Bucket | Matrix rows | Older requests (13 Nov) | Total |", "|---|---|---|---|"]
    for b in ORDER:
        mm, cc = len(m.get(b, [])), len(c.get(b, []))
        if mm or cc:
            lines.append(f"| {b} | {mm} | {cc} | {mm + cc}{pct(mm + cc, total)} |")
    lines.append("")
    lines.append("Percentages are shares of the tracked rows, not of the whole project: the full number of requirements is UNKNOWN "
                 "(not every old request has been reconciled into a row yet).")
    for b in ("waiting for hardware", "waiting for owner"):
        ids = m.get(b, []) + c.get(b, [])
        if ids:
            lines.append(f"- {b}: {', '.join(ids)}")
    return "\n".join(lines)


def gen_areas():
    d = json.load(open(P("docs", "claude", "progress.json"), encoding="utf-8"))
    cols = [("total", "Total"), ("impl", "Implemented"), ("unit", "Unit-tested"), ("emulator", "Emulator-tested"),
            ("device", "Real-device verified"), ("owner", "Owner approved"), ("released", "Released")]
    out = [f"_Evidence file: docs/claude/progress.json ({d.get('updated', '?')}). Real-device and emulator columns are separate; "
           "emulator evidence never counts as device evidence._", ""]
    for a in d["areas"]:
        out.append(f"**{a['area']}**")
        t = a.get("total")
        for k, label in cols:
            v = a.get(k, "UNKNOWN")
            out.append(f"- {label}: {v}{pct(v, t) if k != 'total' else ''}")
        out.append(f"- Remaining / blocked: {a.get('remaining', 'UNKNOWN')}")
        out.append(f"- Source: {a.get('source', '-')}")
        out.append("")
    return "\n".join(out).rstrip()


def gen_hw():
    reqs = hw_requests()
    if not reqs:
        return "No hardware requests recorded."
    out = ["| Request | Status |", "|---|---|"]
    for i, title, st in reqs:
        out.append(f"| {i} {title} | {st} |")
    open_ = [r for r in reqs if not r[2].startswith("DONE")]
    out.append("")
    out.append(f"Open hardware requests: {len(open_)} of {len(reqs)}.")
    return "\n".join(out)


GENS = {"overall": gen_overall, "areas": gen_areas, "hw": gen_hw}


# ---------------- owner summary + release tracker (owner request 16 Nov 2026) ----------------

def load_json(*parts):
    return json.load(open(P(*parts), encoding="utf-8"))


def run_reports():
    path = P("docs", "claude", "run_reports.jsonl")
    out = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line:
                r = json.loads(line)
                # 677a: a live session wrote {"run", "at", "summary"} instead of {"update", "utc", "plain"}; read both shapes.
                r.setdefault("update", r.get("run", "?"))
                r.setdefault("utc", r.get("at", "1970-01-01T00:00Z"))
                r.setdefault("plain", r.get("summary", ""))
                out.append(r)
    return out


def _utc(s):
    import datetime
    s = s.replace("Z", "+00:00")
    if len(s) == 22:  # 2026-11-16T04:23+00:00
        s = s[:16] + ":00" + s[16:]
    return datetime.datetime.fromisoformat(s)


def bar(n, total, width=10):
    if not (isinstance(n, int) and isinstance(total, int)) or isinstance(n, bool) or total <= 0:
        return f"`{'?' * width}` {n if n not in ('', None) else 'UNKNOWN'}"
    full = round(width * n / total)
    return f"`{'█' * full}{'░' * (width - full)}` {n}/{total}"


ITEM_DONE_BLOCK = re.compile(r"not run|re-test|NOT TESTED|need|pending", re.I)


def item_verified(it):
    dev, own = str(it.get("device", "")), str(it.get("owner", ""))
    if it.get("kind") != "app":
        return False
    return dev.startswith("PASS") and not ITEM_DONE_BLOCK.search(dev) and (own.startswith("APPROVED") or own == "not needed")


def is_published(rel, current):
    """A release whose version is the current stable one (releases.json 'current') is out: it is never 'ready' again."""
    return bool(current) and str(rel.get("version", "")).strip() == str(current.get("version", "")).strip()


def release_status(rel, current=None):
    """Derived, never typed: PUBLISHED (its version is the current stable, releases.json 'current') > DEVELOPMENT (an item not built) > BLOCKED (a gate FAIL / BLOCKED) > TESTING (a gate not PASS
    other than the owner's own steps) > READY FOR OWNER APPROVAL (only the upgrade install and the owner's GO are left)."""
    if is_published(rel, current):
        return "PUBLISHED"
    if any(not it.get("impl") for it in rel["items"]):
        return "DEVELOPMENT"
    states = [g["state"].upper() for g in rel["gates"]]
    if any(st in ("FAIL", "BLOCKED") for st in states):
        return "BLOCKED"
    # HISTORICAL = an older build's evidence the owner chose not to block on; it never blocks, it is listed under risks
    left = [g for g in rel["gates"] if g["state"].upper() not in ("PASS", "HISTORICAL")]
    if all(re.search(r"owner GO|Upgrade install", g["gate"], re.I) for g in left):
        return "READY FOR OWNER APPROVAL"
    return "TESTING"


def gen_release():
    d = load_json("docs", "claude", "releases.json")
    cur = d["current"]
    out = [f"**Current stable:** {cur['version']} ({cur['commit']}, {cur['date']}) - {cur['note']}", ""]
    for rel in d["releases"]:
        st = release_status(rel, cur)
        items = rel["items"]
        apps = [i for i in items if i.get("kind") == "app"]
        impl = [i for i in apps if i.get("impl")]
        ver = [i for i in apps if item_verified(i)]
        untested = [i for i in impl if i.get("kind") == "app" and not str(i.get("device", "")).startswith("PASS")]
        appr = [i for i in items if re.search(r"review needed|decision needed", str(i.get("owner", "")), re.I)]
        out += [f"### {rel['version']} - **{st}**",
                f"- Base: {rel['base']}. Scope: {rel['scope']}.",
                f"- Release window: {rel['window']}",
                f"- App fixes and features: {len(apps)} ({len(impl)} implemented, {len(ver)} fully verified, "
                f"{len(untested)} implemented but not yet tested on a handheld, {len(appr)} need your look or decision)", "",
                "| Fix / feature | Handheld evidence | Your approval |", "|---|---|---|"]
        for i in items:
            out.append(f"| {i['what']} | {i.get('device', '-')} | {i.get('owner', '-')} |")
        out += ["", "| Gate | State | Evidence |", "|---|---|---|"]
        for g in rel["gates"]:
            out.append(f"| {g['gate']} | {g['state']} | {g['evidence']} |")
        for rk in rel.get("risks", []):
            out.append(f"- **Risk / known gap:** {rk}")
        for ex in rel.get("excluded", []):
            out.append(f"- **Left out on purpose:** {ex['what']} - {ex['why']}. Kept: {ex['kept']}.")
        reasons = [g["gate"] + f" ({g['state']})" for g in sorted(rel["gates"], key=lambda g: 0 if g["state"].upper() in ("BLOCKED", "FAIL") else 1) if g["state"].upper() not in ("PASS", "HISTORICAL")]
        if st == "PUBLISHED":
            out += ["", f"Published: this is the current stable release ({cur['commit']}, {cur['date']}). Nothing to approve.", ""]
            continue
        out += ["", "Why it cannot ship yet: " + ("; ".join(reasons) if reasons else "nothing - waiting for your GO") + ".",
                "Merging, the production install and publishing always wait for your explicit GO.", ""]
    return "\n".join(out).rstrip()


SUMMARY_AREAS = [("Home layouts (popup gate)", "Home layouts"), ("Native popup pages", "Native pages"),
                 ("Physical pickup / return animations", "Pickup animations"), ("Category styles (looks)", "Category styles"),
                 ("Save Vault + Save Sync 529", "Save Vault + Sync")]


def commit_of(rep):
    """A run can't know its own hash before it commits: '(this commit)' is resolved from git (the commit whose subject starts
    with '<update>:'), so the next regeneration fills it in."""
    c = str(rep.get("commit", ""))
    if c and not c.startswith("("):
        return c
    import subprocess
    try:
        h = subprocess.run(["git", "-C", ROOT, "log", "-1", "--format=%h", "--grep", "^" + re.escape(rep["update"]) + ":"],
                           capture_output=True, text=True, timeout=20).stdout.strip()
    except Exception:
        h = ""
    return h or "this update (being pushed)"


def gen_summary():
    reps = run_reports()
    prog = {a["area"]: a for a in load_json("docs", "claude", "progress.json")["areas"]}
    rels = load_json("docs", "claude", "releases.json")
    acts = load_json("docs", "claude", "owner_actions.json")["actions"]
    out = []
    if reps:
        last = reps[-1]
        out += [f"**1. LATEST UPDATE** - {last['update']} · {last['utc'].replace('T', ' ').replace('Z', '')} UTC · commit `{commit_of(last)}`"
                + (f" · CI: {last['ci']}" if last.get("ci") else "") + (f" · effort: {last['effort']}" if last.get("effort") else ""), "",
                f"**2. WHAT CLAUDE JUST FIXED** - {last['plain']}", ""]
        t_last = _utc(last["utc"])
        recent = [r for r in reps if (t_last - _utc(r["utc"])).total_seconds() <= 86400]
        recent.sort(key=lambda r: r["utc"], reverse=True)
        recent.sort(key=lambda r: -int(r.get("importance", 1)))  # stable: newest first within the same importance
        out.append("**3. LAST 24 HOURS** (most important first)")
        label = {"app": "App", "ci": "CI / build", "qa": "QA tools", "docs": "Docs"}
        for r in recent[:5]:
            out.append(f"- **{label.get(r.get('kind'), r.get('kind'))}** ({r['update']}): {r['plain']}")
        out.append("")
    out += ["**4. PROJECT PROGRESS** (implemented / verified on handhelds / approved by you)", "",
            "| Area | Implemented | Verified on handhelds | Approved by you |", "|---|---|---|---|"]
    for key, name in SUMMARY_AREAS:
        a = prog.get(key)
        if not a:
            continue
        t = a.get("total")
        out.append(f"| {name} | {bar(a.get('impl'), t)} | {bar(a.get('device'), t)} | {bar(a.get('owner'), t)} |")
    emu = prog.get("Cloud emulator QA coverage")
    if emu:
        home = prog.get("Home layouts (popup gate)", {})
        out.append(f"| Emulator QA (layouts) | {bar(home.get('emulator'), home.get('total'))} | emulator never counts as handheld | - |")
    out.append("")
    if reps and reps[-1].get("next"):
        nx = reps[-1]["next"]
        out.append(f"**5. WORKING ON NEXT** - now: {nx[0]}" + ("" if len(nx) < 2 else "; then: " + "; ".join(nx[1:3])))
        out.append("")
    urgent = [a for a in acts if a.get("urgent")]
    if urgent:
        out.append("**6. WHAT YOU NEED TO DO** - " + " · ".join(f"{a['what']} ({a['why']})" for a in urgent))
    else:
        out.append(f"**6. WHAT YOU NEED TO DO** - **NONE urgent.** When convenient: {len(acts)} items (see Owner actions below); "
                   f"cloud work continues without them.")
    out.append("")
    pending = [r for r in rels["releases"] if not is_published(r, rels.get("current"))]
    if not pending:
        out.append(f"**7. NEXT RELEASE** - current {rels['current']['version']} (published) · no next release planned yet")
    for n, rel in enumerate(pending):
        st = release_status(rel, rels.get("current"))
        if n:  # later releases: one short line each
            out.append(f"- later: **{rel['version']}: {st}** - {rel['scope']} ({rel['window'].split(' - ')[0]})")
            continue
        apps = [i for i in rel["items"] if i.get("kind") == "app"]
        ver = [i for i in apps if item_verified(i)]
        left = [g for g in rel["gates"] if g["state"].upper() not in ("PASS", "HISTORICAL")]
        left.sort(key=lambda g: 0 if g["state"].upper() in ("BLOCKED", "FAIL") else 1)
        need = "yes - when it is ready" if st != "READY FOR OWNER APPROVAL" else "**yes - now (RELEASE READY)**"
        out.append(f"**7. NEXT RELEASE** - current {rels['current']['version']} · **{rel['version']}: {st}** · "
                   f"{len(ver)} of {len(apps)} app fixes fully verified · {len(left)} gates left"
                   + (f" (blocking: {left[0]['gate']})" if left and left[0]['state'].upper() in ('BLOCKED', 'FAIL') else
                      (f" (next: {left[0]['gate']})" if left else ""))
                   + f" · your approval needed: {need} · window: {rel['window'].split(' - ')[0]}")
    return "\n".join(out).rstrip()


def gen_owner():
    acts = load_json("docs", "claude", "owner_actions.json")["actions"]
    out = ["| # | What I need from you | Why | Device | Urgent? | Blocks |", "|---|---|---|---|---|---|"]
    for n, a in enumerate(acts, 1):
        out.append(f"| {n} | {a['what']} | {a['why']} | {a['device']} | {'YES' if a.get('urgent') else 'no'} | {a['blocks']} |")
    out.append("")
    out.append("Cloud development continues without any of these. v0.6.73 / KONKR production upgrade / merges to main need your "
               "separate GO.")
    return "\n".join(out)


GENS.update({"summary": gen_summary, "release": gen_release, "owner": gen_owner})


def render(text):
    for name, fn in GENS.items():
        a, b = f"<!-- GEN:{name} -->", f"<!-- /GEN:{name} -->"
        if a not in text or b not in text:
            raise SystemExit(f"dashboard: marker {a} / {b} missing")
        head, rest = text.split(a, 1)
        _, tail = rest.split(b, 1)
        text = head + a + "\n" + fn() + "\n" + b + tail
    return text


def main():
    cur = open(DASH, encoding="utf-8").read()
    new = render(cur)
    if "--check" in sys.argv:
        if new != cur:
            print("STATUS_DASHBOARD.md is out of date: run python3 tools/cloud/dashboard.py")
            sys.exit(1)
        print("STATUS_DASHBOARD.md up to date")
        return
    if new != cur:
        open(DASH, "w", encoding="utf-8").write(new)
    print("STATUS_DASHBOARD.md regenerated")


if __name__ == "__main__":
    main()

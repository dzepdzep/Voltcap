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

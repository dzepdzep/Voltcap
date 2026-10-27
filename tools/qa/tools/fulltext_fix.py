#!/usr/bin/env python3
"""Rewrite Text(..., overflow = TextOverflow.Ellipsis, ...) calls into FullText(...) where the arguments allow it.
Usage: fulltext_fix.py FILE...   (prints what changed / skipped; keeps CRLF)"""
import re, sys

ALLOWED = {"color", "fontSize", "modifier", "maxLines", "fontWeight", "fontFamily", "letterSpacing", "lineHeight", "textAlign", "style"}
DROP = {"overflow", "autoSize", "softWrap"}


def split_args(body):
    out, d, cur, q = [], 0, "", None
    i = 0
    while i < len(body):
        ch = body[i]
        if q:
            cur += ch
            if ch == "\\":
                cur += body[i + 1]; i += 2; continue
            if ch == q: q = None
        elif ch in "\"'":
            q = ch; cur += ch
        elif ch in "([{":
            d += 1; cur += ch
        elif ch in ")]}":
            d -= 1; cur += ch
        elif ch == "," and d == 0:
            out.append(cur); cur = ""
        else:
            cur += ch
        i += 1
    if cur.strip(): out.append(cur)
    return out


def find_close(s, k):
    d, q = 0, None
    while k < len(s):
        ch = s[k]
        if q:
            if ch == "\\": k += 2; continue
            if ch == q: q = None
            # string templates ${...} are kept inside the string scan
        elif ch == '"':
            q = ch
        elif ch == "(":
            d += 1
        elif ch == ")":
            d -= 1
            if d == 0: return k
        k += 1
    return -1


def fix(path):
    s = open(path, newline="").read()
    changed = skipped = 0
    pos = 0
    out = []
    for m in re.finditer(r"(?<![\w.])Text\(", s):
        if m.start() < pos: continue
        k = find_close(s, m.end() - 1)
        if k < 0: continue
        body = s[m.end(): k]
        if "Ellipsis" not in body or "qa-ok" in body:
            continue
        args = split_args(body)
        if not args: continue
        first = args[0].strip()
        if "=" in first.split("(")[0] and not first.startswith('"'):
            skipped += 1; continue
        if "buildAnnotatedString" in first or "Annotated" in first:
            skipped += 1; continue
        named, ok = [], True
        for a in args[1:]:
            mm = re.match(r"\s*(\w+)\s*=", a)
            if not mm: ok = False; break
            n = mm.group(1)
            if n in DROP: continue
            if n not in ALLOWED: ok = False; break
            named.append(a)
        names = {re.match(r"\s*(\w+)", a).group(1) for a in named}
        if not ok or "color" not in names or "fontSize" not in names:
            skipped += 1; continue
        lead = args[0]
        if "maxLines" not in names:
            named.append(" maxLines = Int.MAX_VALUE")
        new = "FullText(" + ",".join([lead] + named).rstrip() + (")" if not body.rstrip().endswith(",") else ")")
        # keep a trailing newline/indent before ')' when the call was multi-line
        tail_ws = re.search(r"(\s*)$", body).group(1)
        new = "FullText(" + ",".join([lead] + named).rstrip() + tail_ws + ")"
        out.append(s[pos:m.start()]); out.append(new); pos = k + 1
        changed += 1
    out.append(s[pos:])
    if changed:
        open(path, "w", newline="").write("".join(out))
    print(f"{path}: {changed} changed, {skipped} skipped")


for f in sys.argv[1:]:
    fix(f)

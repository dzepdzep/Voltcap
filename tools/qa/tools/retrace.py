#!/usr/bin/env python3
"""VoltCap QA — turn R8's short names in QaSampler / crash stacks back into real ones.

  python3 retrace.py mapping.txt NAME_qa.txt [--only stall,decode]

mapping.txt: app/build/outputs/mapping/release/mapping.txt of THE SAME build. Rewrites every `cls.method:line` frame.
"""
import re, sys, collections


def load(path):
    cls = {}; meth = collections.defaultdict(list); cur = None; orig = None
    for line in open(path, errors="replace"):
        if line.startswith("#"): continue
        if not line.startswith(" "):
            m = re.match(r"(\S+) -> (\S+):", line)
            if m: orig, cur = m.group(1), m.group(2); cls[cur] = orig
            continue
        m = re.match(r"\s+(?:(\d+):(\d+):)?(\S+) ([^\s(]+)\(([^)]*)\)(?::(\d+)(?::(\d+))?)? -> (\S+)", line)
        if m and cur:
            a, b, ret, name, args, o1, o2, obf = m.groups()
            meth[(cur, obf)].append((int(a) if a else None, int(b) if b else None, name, int(o1) if o1 else None, orig))
    return cls, meth


def frame(cls, meth, c, m, ln):
    cands = meth.get((c, m), [])
    pick = None
    for a, b, name, o1, owner in cands:
        if a is not None and a <= ln <= b:
            line = o1 + (ln - a) if o1 is not None else ln
            pick = f"{name.rsplit('.',1)[-1] if '.' in name else name}:{line}"
            own = name.rsplit('.', 1)[0] if '.' in name else owner
            return f"{own.rsplit('.',1)[-1]}.{pick}"
    if cands:
        a, b, name, o1, owner = cands[0]
        return f"{cls.get(c, c).rsplit('.',1)[-1]}.{name}"
    return f"{cls.get(c, c).rsplit('.',1)[-1]}.{m}:{ln}"


def main():
    cls, meth = load(sys.argv[1])
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    for line in open(sys.argv[2], errors="replace"):
        if only and not any(f" {o} " in line for o in only): continue
        def sub(mo):
            full, ln = mo.group(1), int(mo.group(2))
            c, m = full.rsplit(".", 1)
            return frame(cls, meth, c, m, ln)
        print(re.sub(r"([\w$.]+\.[\w$<>]+):(-?\d+)", sub, line.rstrip()))


if __name__ == "__main__":
    main()

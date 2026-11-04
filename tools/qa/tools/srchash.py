#!/usr/bin/env python3
"""Update 474: one hash for the app source a build was made from (QA evidence binding).

usage: python3 srchash.py [ROOT]          prints  <sha256>  <files>
Hashes app/build.gradle.kts, app/src/main/** and app/src/test/** (sorted paths; CRLF read as LF, so a Windows checkout and a
Linux one give the same hash). The same source always gives the same hash; any changed, added or removed file changes it.
CI prints it for the tree it tested; tools/qa/builds.json records it next to each APK's SHA-256; popclip.py resolves a
run's APK SHA-256 through builds.json to this hash, the base commit and the staging patch.
"""
import hashlib, os, sys

PARTS = ("app/build.gradle.kts", "app/src/main", "app/src/test")


def files(root):
    out = []
    for p in PARTS:
        full = os.path.join(root, p)
        if os.path.isfile(full):
            out.append(p)
        elif os.path.isdir(full):
            for d, dirs, fs in os.walk(full):
                dirs[:] = sorted(x for x in dirs if x not in ("build", ".gradle"))
                for f in fs:
                    out.append(os.path.relpath(os.path.join(d, f), root).replace(os.sep, "/"))
    return sorted(out)


def tree_hash(root="."):
    h = hashlib.sha256()
    fl = files(root)
    for rel in fl:
        data = open(os.path.join(root, rel), "rb").read().replace(b"\r\n", b"\n")
        h.update(rel.encode() + b"\0" + hashlib.sha256(data).digest())
    return h.hexdigest(), len(fl)


if __name__ == "__main__":
    hx, n = tree_hash(sys.argv[1] if len(sys.argv) > 1 else ".")
    print(hx, n)

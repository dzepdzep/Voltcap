#!/usr/bin/env python3
"""Update 456: freeze a release candidate so no later build can overwrite it.

Every Gradle build writes app/build/outputs/apk/<variant>/Voltcap-<version>-<variant>.apk under the SAME name, so a
staging build silently replaced the signed v0.6.66 RC (its original file, SHA-256 45c6d2ae..., is lost). Run this right
after building a release candidate:

    python tools/release/freeze_rc.py 0.6.67 rc1            # release (+ compat when built) of the current build
    python tools/release/freeze_rc.py --verify               # re-check every frozen file against SHA256SUMS.txt

It copies the APKs (and their .dm profiles) to release_candidates/<version>/Voltcap-<version>-<label>-<variant>-<sha8>.apk,
marks them read-only and appends their SHA-256 to release_candidates/SHA256SUMS.txt (committed; the APKs themselves are
git-ignored). It never overwrites: a label already frozen for that version is refused. Gradle never writes into
release_candidates/, so staging builds can't touch a frozen candidate. Publish from the frozen file, not a rebuild.
"""
import datetime, hashlib, json, os, shutil, stat, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
STORE = os.path.join(ROOT, "release_candidates")
SUMS = os.path.join(STORE, "SHA256SUMS.txt")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def verify():
    bad = 0
    if not os.path.exists(SUMS):
        print("no SHA256SUMS.txt yet"); return 0
    for line in open(SUMS, encoding="utf-8"):
        if not line.strip() or line.startswith("#"):
            continue
        digest, rel = line.split()[:2]
        p = os.path.join(STORE, rel)
        if not os.path.exists(p):
            print(f"MISSING  {rel}"); bad += 1
        elif sha256(p) != digest:
            print(f"CHANGED  {rel}"); bad += 1
        else:
            print(f"OK       {rel}")
    return 1 if bad else 0


def freeze(version, label, note):
    if any(c in label for c in r'\/:*?"<>| '):
        sys.exit("label: letters, digits, - and _ only")
    out = os.path.join(STORE, version)
    os.makedirs(out, exist_ok=True)
    if any(f"-{label}-" in n for n in os.listdir(out)):
        sys.exit(f"{version} {label} is already frozen - refusing to overwrite (pick a new label, e.g. rc2)")
    done = []
    for variant in ("release", "compat"):
        d = os.path.join(ROOT, "app", "build", "outputs", "apk", variant)
        meta = os.path.join(d, "output-metadata.json")
        if not os.path.exists(meta):
            continue
        m = json.load(open(meta, encoding="utf-8"))
        el = m["elements"][0]
        if el.get("versionName") != version:
            sys.exit(f"{variant}: the build is {el.get('versionName')}, not {version} - nothing frozen")
        apk = os.path.join(d, el["outputFile"])
        digest = sha256(apk)
        name = f"Voltcap-{version}-{label}-{variant}-{digest[:8]}.apk"
        dst = os.path.join(out, name)
        shutil.copy2(apk, dst)
        extra = [dst]
        for bp in m.get("baselineProfiles", []):
            for f in bp.get("baselineProfiles", []):
                src = os.path.join(d, f.replace("/", os.sep))
                if os.path.exists(src):
                    dm = os.path.join(out, name[:-4] + f"-api{bp.get('minApi')}.dm")
                    shutil.copy2(src, dm); extra.append(dm)
        for p in extra:
            os.chmod(p, stat.S_IREAD | stat.S_IRGRP | stat.S_IROTH)
        when = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")
        with open(SUMS, "a", encoding="utf-8") as s:
            for p in extra:
                s.write(f"{sha256(p)}  {version}/{os.path.basename(p)}  # {when} versionCode {el.get('versionCode')} {note}\n")
        done.append((name, digest))
    if not done:
        sys.exit("no release build found under app/build/outputs/apk - build first")
    for n, d in done:
        print(f"frozen {version}/{n}  sha256 {d}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if a[:1] == ["--verify"]:
        sys.exit(verify())
    if len(a) < 2:
        sys.exit(__doc__)
    freeze(a[0], a[1], " ".join(a[2:]))

#!/usr/bin/env python3
"""Batch 517: check an APK for confidential values and report its identity, without printing any value.

  python3 tools/release/apk_secret_scan.py APK [APK...] [--props DIR] [--denylist FILE]

Values checked (only their NAMES are printed):
  screenscraper.properties devPassword / devId, igdb.properties clientSecret / clientId (from --props, default: project root),
  every line of --denylist (default: secret_denylist.txt in --props when present).
Also: BuildConfig credential field names, package / version (from the binary manifest is not parsed: use aapt on the PC),
and the v2/v3 signing certificate's SHA-256 (to compare with the release key's 88ce4b67…).
Exit code 1 when a confidential value or field is found.
"""
import hashlib, os, struct, sys, zipfile

FIELDS = [b"SS_DEV_PASSWORD", b"SS_DEV_ID", b"IGDB_CLIENT_SECRET", b"IGDB_CLIENT_ID"]


def props(path):
    out = {}
    if os.path.isfile(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def manifest_identity(apk):
    """package, versionCode, versionName, debuggable from the binary AndroidManifest.xml (minimal AXML reader)."""
    data = zipfile.ZipFile(apk).read("AndroidManifest.xml")
    pos, strings, out = 8, [], {}
    while pos < len(data):
        typ, hsize, size = struct.unpack_from("<HHI", data, pos)
        if typ == 0x0001:  # string pool
            count, _, flags, s_start, _ = struct.unpack_from("<IIIII", data, pos + 8)
            utf8 = flags & 0x100
            offs = struct.unpack_from("<%dI" % count, data, pos + 28)
            for o in offs:
                q = pos + s_start + o
                if utf8:
                    n = data[q]; q += 2 if n & 0x80 else 1
                    n = data[q]; q += 1
                    if n & 0x80: n = ((n & 0x7F) << 8) | data[q]; q += 1
                    strings.append(data[q:q + n].decode("utf-8", "replace"))
                else:
                    n = struct.unpack_from("<H", data, q)[0]; q += 2
                    strings.append(data[q:q + 2 * n].decode("utf-16le", "replace"))
        elif typ == 0x0102:  # start element
            name = strings[struct.unpack_from("<I", data, pos + 20)[0]]
            astart, asize, acount = struct.unpack_from("<HHH", data, pos + 24)
            for i in range(acount):
                a = pos + 16 + astart + i * asize
                an = strings[struct.unpack_from("<I", data, a + 4)[0]]
                raw = struct.unpack_from("<i", data, a + 8)[0]
                dtype, dval = data[a + 15], struct.unpack_from("<I", data, a + 16)[0]
                val = strings[raw] if raw >= 0 else (dval if dtype != 0x12 else bool(dval))
                if name == "manifest" and an in ("package", "versionCode", "versionName"): out[an] = val
                if name == "application" and an == "debuggable": out["debuggable"] = val
        pos += size if typ != 0x0003 else hsize
    out.setdefault("debuggable", False)
    return out


def signer_sha256(apk):
    data = open(apk, "rb").read()
    eocd = data.rfind(b"PK\x05\x06")
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]
    if data[cd_off - 16:cd_off] != b"APK Sig Block 42":
        return None
    size = struct.unpack_from("<Q", data, cd_off - 24)[0]
    start = cd_off - size - 8
    pos, end = start + 8, cd_off - 24
    while pos < end:
        ln, bid = struct.unpack_from("<QI", data, pos)
        if bid in (0x7109871A, 0xF05368C0):  # v2, v3
            blk = data[pos + 12:pos + 8 + ln]
            # signers: len-prefixed seq; first signer; signed data; digests; certificates; first cert
            p = 4                       # signers sequence length
            p += 4                      # first signer length
            sd_len = struct.unpack_from("<I", blk, p)[0]; p += 4
            sd = blk[p:p + sd_len]
            q = 0
            dl = struct.unpack_from("<I", sd, q)[0]; q += 4 + dl   # digests
            q += 4                                                  # certificates sequence length
            cl = struct.unpack_from("<I", sd, q)[0]; q += 4
            return hashlib.sha256(sd[q:q + cl]).hexdigest()
        pos += 8 + ln
    return None


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    opt = dict(a.split("=", 1) for a in argv if a.startswith("--") and "=" in a)
    root = opt.get("--props", os.getcwd())
    ss, ig = props(os.path.join(root, "screenscraper.properties")), props(os.path.join(root, "igdb.properties"))
    denied = {}
    for name, v in (("screenscraper.devPassword", ss.get("devPassword")), ("screenscraper.devId", ss.get("devId")),
                    ("igdb.clientSecret", ig.get("clientSecret")), ("igdb.clientId", ig.get("clientId"))):
        if v and len(v) >= 4:
            denied[name] = v.encode()
    dl = opt.get("--denylist", os.path.join(root, "secret_denylist.txt"))
    if os.path.isfile(dl):
        for i, line in enumerate(open(dl, encoding="utf-8")):
            line = line.strip()
            if len(line) >= 6 and not line.startswith("#"):
                denied[f"denylist line {i + 1}"] = line.encode()
    bad = False
    for apk in args:
        z = zipfile.ZipFile(apk)
        blob = b"".join(z.read(n) for n in z.namelist())
        hits = [n for n, v in denied.items() if v in blob]
        fields = [f.decode() for f in FIELDS if f in blob]
        sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
        ident = manifest_identity(apk)
        print(f"{os.path.basename(apk)}: {ident.get('package')} {ident.get('versionName')} ({ident.get('versionCode')}) debuggable={ident.get('debuggable')}")
        print(f"  sha256 {sha[:16]}…  signer {(signer_sha256(apk) or 'unknown')[:16]}…  "
              f"checked {len(denied)} values: {'FOUND ' + ', '.join(hits) if hits else 'none found'}; "
              f"credential fields: {', '.join(fields) if fields else 'none'}")
        bad |= bool(hits or fields)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

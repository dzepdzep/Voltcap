#!/usr/bin/env python3
"""Release check without the Android SDK: package, versionCode / versionName and the signer certificate's SHA-256 of an APK.

usage: python3 apkinfo.py APK [APK ...]
Reads the binary AndroidManifest.xml (AXML) and the APK Signature Scheme v2 / v3 block (cert of the first signer).
v0.6.65 / v0.6.66 signer: 88ce4b67ae3825a04585114aec25cfaf5023e0b55cfdb61f375d30945f344197.
"""
import hashlib, struct, sys, zipfile


def axml_manifest(data):
    """package, versionCode, versionName from the <manifest> element of a binary AndroidManifest.xml."""
    pos, strings, res_ids = 8, [], []
    while pos < len(data):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if ctype == 0x0001:  # string pool
            count, _, flags, s_start = struct.unpack_from("<IIII", data, pos + 8)
            utf8 = flags & 0x100
            offs = struct.unpack_from(f"<{count}I", data, pos + 28)
            base = pos + s_start
            for o in offs:
                p = base + o
                if utf8:
                    n = data[p]; p += 2 if n & 0x80 else 1
                    ln = data[p]
                    if ln & 0x80: ln = ((ln & 0x7F) << 8) | data[p + 1]; p += 2
                    else: p += 1
                    strings.append(data[p:p + ln].decode("utf-8", "replace"))
                else:
                    ln = struct.unpack_from("<H", data, p)[0]; p += 2
                    if ln & 0x8000: ln = ((ln & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]; p += 2
                    strings.append(data[p:p + ln * 2].decode("utf-16le", "replace"))
        elif ctype == 0x0180:
            res_ids = list(struct.unpack_from(f"<{(csize - 8) // 4}I", data, pos + 8))
        elif ctype == 0x0102:  # start element
            name = strings[struct.unpack_from("<i", data, pos + 20)[0]]
            astart, asize, acount = struct.unpack_from("<HHH", data, pos + 24)
            if name == "manifest":
                out = {}
                for i in range(acount):
                    a = pos + 16 + astart + i * asize
                    _, nidx, raw, _, _, dtype, dval = struct.unpack_from("<iiiHBBI", data, a)
                    key = strings[nidx] if nidx >= 0 else "?"
                    rid = res_ids[nidx] if 0 <= nidx < len(res_ids) else 0
                    key = {0x0101021B: "versionCode", 0x0101021C: "versionName"}.get(rid, key)
                    out[key] = strings[raw] if raw >= 0 else (dval if dtype in (0x10, 0x11) else strings[dval] if dtype == 3 else dval)
                return out
        pos += csize
    return {}


def signer_sha256(path):
    """SHA-256 of the first signer's certificate in the v2 (or v3) signing block."""
    raw = open(path, "rb").read()
    eocd = raw.rfind(b"PK\x05\x06")
    cd_off = struct.unpack_from("<I", raw, eocd + 16)[0]
    if raw[cd_off - 16:cd_off] != b"APK Sig Block 42":
        return None
    size = struct.unpack_from("<Q", raw, cd_off - 24)[0]
    p, end = cd_off - size + 8 - 8, cd_off - 24
    p = cd_off - (size + 8) + 8
    blocks = {}
    while p < end:
        ln, bid = struct.unpack_from("<QI", raw, p)
        blocks[bid] = raw[p + 12:p + 8 + ln]
        p += 8 + ln
    blk = blocks.get(0x7109871A) or blocks.get(0xF05368C0)
    if blk is None:
        return None
    # signers(len) > signer(len) > signed data(len) > digests(len, …) > certificates(len) > cert(len)
    q = 4 + 4  # skip signers seq len, signer len
    q += 4  # signed data len
    dlen = struct.unpack_from("<I", blk, q)[0]; q += 4 + dlen
    q += 4  # certificates seq len
    clen = struct.unpack_from("<I", blk, q)[0]; q += 4
    return hashlib.sha256(blk[q:q + clen]).hexdigest()


for apk in sys.argv[1:]:
    with zipfile.ZipFile(apk) as z:
        m = axml_manifest(z.read("AndroidManifest.xml"))
        has_listener = b"NotificationListenerService" in z.read("AndroidManifest.xml") or "BIND_NOTIFICATION_LISTENER_SERVICE".encode("utf-16le") in z.read("AndroidManifest.xml")
    print(f"{apk}\n  package={m.get('package')} versionCode={m.get('versionCode')} versionName={m.get('versionName')}"
          f" notificationListener={'yes' if has_listener else 'no'}\n  signer sha256={signer_sha256(apk)}")

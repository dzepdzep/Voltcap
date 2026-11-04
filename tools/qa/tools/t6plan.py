#!/usr/bin/env python3
"""Update 474 — QA tier T6 plan: every user-settable setting (settings_inventory.py CSV) split into device test groups.
Per group the device run is: snapshot save → copy (BEFORE) → set each key to another value (voltcap_test_pref, preview) →
screenshot + QaClip dump per key → snapshot restore → snapshot save → copy (AFTER) → snapdiff.py BEFORE AFTER must PASS.
Not set by QA at all (listed, NOT TESTED on device): account / sync / internal state (AUTH, INTERNAL), and device / power
stores (charge limit, tuner, performance, battery watch, mixer, sleep radios) — owner rule: no charge-limit or system changes.
  python3 t6plan.py INVENTORY.csv [--md OUT.md] [--json OUT.json] [--size 20]
"""
import collections, csv, json, re, sys

NEVER_FILES = {"perf_settings", "tuner", "battery_watch", "mixer_meta", "sleep_radios", "gesture_hint", "continue_apps_scan",
               "gamenative_settings", "r1_tiles"}


def plan(rows, size=20):
    settable, skipped = [], collections.Counter()
    for r in rows:
        ui = int(r.get("ui_writes") or 0) + int(r.get("r1_writes") or 0)
        if not ui:
            skipped["no settings UI (internal / derived)"] += 1
        elif r.get("backup_scope") in ("AUTH", "INTERNAL"):
            skipped["account / internal state: never set by QA"] += 1
        elif r.get("prefs_file") in NEVER_FILES or re.search(r"charge|limit_|tuner|governor", r.get("key", "")):
            skipped["device / power setting: never set by QA (owner rule)"] += 1
        else:
            settable.append(r)
    by = collections.defaultdict(list)
    for r in settable:
        k = r["key"]
        pre = re.split(r"[_.]", k)[0]
        by[(r["prefs_file"], pre)].append(k)
    # (Small prefixes are folded into one "misc" group per file so the groups stay near the target size.)
    groups, misc = [], collections.defaultdict(list)
    for (f, pre), keys in sorted(by.items()):
        if len(keys) < 4:
            misc[f] += keys
        else:
            for i in range(0, len(keys), size):
                groups.append({"file": f, "name": f"{f}:{pre}" + (f"#{i // size + 1}" if len(keys) > size else ""), "keys": sorted(keys[i:i + size])})
    for f, keys in sorted(misc.items()):
        keys = sorted(keys)
        for i in range(0, len(keys), size):
            groups.append({"file": f, "name": f"{f}:misc#{i // size + 1}", "keys": keys[i:i + size]})
    return groups, skipped, len(settable)


def main():
    if len(sys.argv) < 2:
        print(__doc__); sys.exit(2)
    rows = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
    size = int(sys.argv[sys.argv.index("--size") + 1]) if "--size" in sys.argv else 20
    groups, skipped, n = plan(rows, size)
    print(f"{len(rows)} settings in the inventory, {n} settable by QA in {len(groups)} groups; not set: " +
          ", ".join(f"{v} {k}" for k, v in skipped.items()))
    if "--json" in sys.argv:
        json.dump({"groups": groups, "not_set": skipped}, open(sys.argv[sys.argv.index("--json") + 1], "w", encoding="utf-8"), indent=1)
    if "--md" in sys.argv:
        with open(sys.argv[sys.argv.index("--md") + 1], "w", encoding="utf-8") as fh:
            fh.write(f"# T6 settings groups (update 474 plan; nothing run yet = NOT TESTED)\n\n{len(rows)} settings in the inventory; "
                     f"**{n} settable by QA in {len(groups)} groups** of ≤ {size}.\n\nNot set by QA: " +
                     "; ".join(f"{v} {k}" for k, v in skipped.items()) + ".\n\nPer group: snapshot save → BEFORE copy → set each key "
                     "(preview) → screenshot + dump → snapshot restore → save → AFTER copy → `snapdiff.py BEFORE AFTER` must PASS.\n\n"
                     "| # | group | keys | status |\n|---|---|---|---|\n")
            for i, g in enumerate(groups):
                fh.write(f"| {i + 1} | {g['name']} | {len(g['keys'])} | NOT TESTED |\n")


if __name__ == "__main__":
    main()

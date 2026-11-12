#!/usr/bin/env python3
"""Read-only settings inventory (owner request, 12 Nov): every pref declared in data/Features.kt with its key, type, default,
its own description, where it is read / written, whether a settings screen shows it, and mechanical flags (never read,
no settings screen, layout-only, similar keys). Nothing is changed. Usage: inventory.py SRC_ROOT OUT.md OUT.json"""
import re, sys, os, json, collections
root, out_md, out_json = sys.argv[1], sys.argv[2], sys.argv[3]
feat = open(os.path.join(root, 'data/Features.kt')).read()
lines = feat.split('\n')
decl = re.compile(r'^\s*(?:private\s+|internal\s+)?val\s+(\w+)\s*=\s*(BoolPref|IntPref|EnumPref|StringPref|SetPref|BoxRowsPref)\("([^"]+)"\s*,?\s*(.*)$')
prefs = []
for i, l in enumerate(lines):
    m = decl.match(l)
    if not m: continue
    name, typ, key, rest = m.groups()
    default = rest.split(')')[0].split(',')[0].strip() if typ != 'EnumPref' else rest.split(',')[0].strip()
    # its own description: the comment block right above it
    doc = []; j = i - 1
    while j >= 0 and (lines[j].strip().startswith(('*', '/**', '//', '*/'))):
        doc.insert(0, lines[j].strip().lstrip('/*').strip()); j -= 1
    desc = ' '.join(d for d in doc if d).strip()
    prefs.append(dict(name=name, type=typ, key=key, default=default, desc=desc[:300], line=i + 1))
# usage
files = {}
for dp, _, fs in os.walk(root):
    for f in fs:
        if f.endswith('.kt') and f != 'ThemeShaders.kt':
            p = os.path.join(dp, f); files[os.path.relpath(p, root)] = open(p, errors='ignore').read()
settings_ui = [f for f in files if re.search(r'(Settings|QuickSettings|Options)\w*\.kt$', f) or f.endswith('SettingsDialog.kt')]
for p in prefs:
    rx = re.compile(r'(?:Features|\bF)\.' + re.escape(p['name']) + r'\b|\b' + re.escape(p['name']) + r'\.(?:value|state|toggle|setByName)')
    used = {f: len(rx.findall(t)) for f, t in files.items() if f != 'data/Features.kt'}
    used = {f: n for f, n in used.items() if n}
    inside = len(re.findall(r'\b' + re.escape(p['name']) + r'\b', feat)) - 1   # other uses inside Features.kt itself
    p['refs'] = sum(used.values()) + max(0, inside)
    p['files'] = sorted(used, key=lambda f: -used[f])
    p['in_settings_ui'] = any(f in settings_ui for f in used)
    style = [f for f in used if re.search(r'/Style\w+\.kt$|/\w+Home\.kt$|WinScene', f)]
    p['layout_files'] = style
    outside_ui = {f: n for f, n in used.items() if f not in settings_ui}
    p['read_outside_settings'] = sum(outside_ui.values()) + max(0, inside)
    flags = []
    if p['refs'] == 0: flags.append('NEVER-USED')
    elif p['read_outside_settings'] == 0: flags.append('SETTINGS-ONLY')
    if not p['in_settings_ui']: flags.append('NO-SETTINGS-SCREEN')
    if used and len(used) == len(style): flags.append('LAYOUT-ONLY')
    p['flags'] = flags
# similar keys (same stem after a layout prefix)
stem = collections.defaultdict(list)
for p in prefs:
    s = re.sub(r'^[a-z]{1,6}_', '', p['key']); stem[s].append(p['key'])
for p in prefs:
    s = re.sub(r'^[a-z]{1,6}_', '', p['key'])
    if len(stem[s]) > 1: p['similar'] = [k for k in stem[s] if k != p['key']]
json.dump(prefs, open(out_json, 'w'), indent=1)
c = collections.Counter(f for p in prefs for f in p['flags'])
with open(out_md, 'w') as o:
    o.write('# Settings inventory (read-only, generated)\n\n')
    o.write(f'Generated from `data/Features.kt` ({len(prefs)} prefs) by `tools/settings_audit/inventory.py`. Nothing was changed: no setting removed, renamed or migrated.\n\n')
    o.write('Flags are mechanical leads for the owner review, not verdicts: NEVER-USED = nothing references it; SETTINGS-ONLY = only a settings screen touches it (shown and saved, but no other code reads it - a strong candidate for an option that does nothing, to confirm by hand); '
            'NO-SETTINGS-SCREEN = no settings screen file touches it (hidden, set elsewhere, or set by a layout\'s own menu); LAYOUT-ONLY = only layout files read it; '
            'similar = keys with the same stem under another prefix (possible duplicates across layouts).\n\n')
    o.write('## Checked by hand (12 Nov)\n\n'
            '- `box_sides_chosen` (SETTINGS-ONLY): written when you pick Box sides, read nowhere - an internal marker left over, no effect you can see. Candidate to retire (owner review).\n'
            '- `pb_follow_home`, `platform_media_scrape`, `pq_sounds`, `pq_sound_volume` (SETTINGS-ONLY): read by working code that lives in files named *Settings / *Options (PlatformBrowserSettings.kt, PlatformOptions.kt, PocketQuestSettings.kt) - in use; the flag is the file-name heuristic.\n'
            '- `art_source_cover / _wide / _hero / _logo`: read by LauncherViewModel (in use).\n'
            '- Not attempted here (needs the owner review): conflicts between settings, layout-by-layout compatibility, wording vs real effect.\n\n')
    o.write('| Flag | Count |\n|---|---|\n' + ''.join(f'| {k} | {v} |\n' for k, v in c.most_common()) + '\n')
    o.write('| Pref (Features.…) | Key | Type | Default | Read in (top 3) | Flags | Similar keys | Its own description |\n|---|---|---|---|---|---|---|---|\n')
    for p in prefs:
        o.write(f"| {p['name']} | `{p['key']}` | {p['type'].replace('Pref','')} | `{p['default'][:24]}` | {', '.join(f.split('/')[-1] for f in p['files'][:3]) or '—'} | {' '.join(p['flags']) or '—'} | {', '.join(p.get('similar', [])[:3]) or '—'} | {p['desc'][:160].replace('|','/')} |\n")
print(len(prefs), dict(c))

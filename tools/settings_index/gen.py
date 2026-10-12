"""
Builds ui/SettingsIndex.kt: every setting Voltcap shows in Settings (switches, choices, sliders, groups), with the
section, sub-page and fold group it lives in, for the Settings search. Run from the project root after changing
settings screens:  python tools/settings_index/gen.py   (reads src_copy/ui, writes src_copy/ui/SettingsIndex.kt)
"""
import os, re, sys, json

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'src_copy'
UI = os.path.join(ROOT, 'ui')
OUT = os.path.join(UI, 'SettingsIndex.kt')

src = {}
for dp, _, fs in os.walk(UI):
    for f in fs:
        if f.endswith('.kt') and f not in ('ThemeShaders.kt', 'SettingsIndex.kt'):
            src[os.path.join(dp, f)] = open(os.path.join(dp, f), encoding='utf-8').read()

def skip_string(s, i):
    # s[i] == '"'; returns index after the string (handles escapes and ${...} roughly)
    if s.startswith('"""', i):
        j = s.find('"""', i + 3)
        return len(s) if j < 0 else j + 3
    i += 1
    depth = 0
    while i < len(s):
        c = s[i]
        if c == '\\': i += 2; continue
        if c == '$' and i + 1 < len(s) and s[i+1] == '{': depth += 1; i += 2; continue
        if c == '}' and depth: depth -= 1; i += 1; continue
        if c == '"' and not depth: return i + 1
        i += 1
    return i

def match(s, i, open_c, close_c):
    # s[i] == open_c; returns index of matching close
    d = 0
    while i < len(s):
        c = s[i]
        if c == '"': i = skip_string(s, i); continue
        if s.startswith('//', i):
            j = s.find('\n', i); i = len(s) if j < 0 else j; continue
        if s.startswith('/*', i):
            j = s.find('*/', i); i = len(s) if j < 0 else j + 2; continue
        if c == open_c: d += 1
        elif c == close_c:
            d -= 1
            if d == 0: return i
        i += 1
    return len(s) - 1

def first_string(args):
    m = re.search(r'"((?:[^"\\$]|\\.)*)"', args)
    return m.group(1) if m else None

def strings(args):
    return re.findall(r'"((?:[^"\\$]|\\.)*)"', args)

# function bodies
funcs = {}
for path, s in src.items():
    for m in re.finditer(r'\bfun\s+(?:[\w.<>?, ]+\.)?(\w+)\s*\(', s):
        name = m.group(1)
        p = m.end() - 1
        e = match(s, p, '(', ')')
        k = e + 1
        while k < len(s) and s[k] in ' \t\n:' or (k < len(s) and re.match(r'[\w.<>?, ]', s[k]) and s[k] != '{' and s[k] != '='):
            if s[k] in '{=': break
            k += 1
        if k < len(s) and s[k] == '{':
            b = match(s, k, '{', '}')
            funcs.setdefault(name, []).append((s, k, b))

CALL = re.compile(r'\b([A-Z]\w*)\s*\(')
ITEM = re.compile(r'\b(SwitchRow|RowChoice|PercentSlider|SectionLabel|SettingsHeading|MenuRow|CheckChip|SliderRow|ToggleRow|ChoiceRow)\s*\(')
SCOPE = re.compile(r'\b(CollapsibleSection|FoldGroup)\s*\(')

entries = []
seen_calls = set()

def args_of(s, i):
    e = match(s, i, '(', ')')
    return s[i+1:e], e

def walk(s, a, b, cat, sub, fold, foldTitle, depth):
    if depth > 9: return
    i = a
    while i < b:
        # nearest next token among scope / item / call
        ms = SCOPE.search(s, i, b); mi = ITEM.search(s, i, b); mc = CALL.search(s, i, b)
        cands = [m for m in (ms, mi, mc) if m]
        if not cands: break
        m = min(cands, key=lambda x: (x.start(), 0 if x is ms else 1 if x is mi else 2))
        p = m.end() - 1
        args, e = args_of(s, p)
        if m is ms:
            title = first_string(args)
            # its content: trailing lambda or the last lambda argument
            k = e + 1
            while k < b and s[k] in ' \t\n': k += 1
            if k < b and s[k] == '{':
                ce = match(s, k, '{', '}'); cs = k
            else:
                cs = p; ce = e
            if title:
                if m.group(1) == 'CollapsibleSection':
                    entries.append((title, cat, title, None, None))
                    walk(s, cs + 1, ce, cat, title, None, None, depth)
                else:
                    km = re.search(r'key\s*=\s*"([^"]+)"', args)
                    key = km.group(1) if km else title
                    entries.append((title, cat, sub, key, title))
                    walk(s, cs + 1, ce, cat, sub, key, title, depth)
            else:
                walk(s, cs + 1, ce, cat, sub, fold, foldTitle, depth)
            i = ce + 1
            continue
        if m is mi:
            ss = strings(args)
            if ss:
                label = ss[0]
                if m.group(1) in ('SwitchRow', 'MenuRow') and len(ss) >= 1:
                    label = ss[0]
                if len(label) > 1 and not label.startswith('$'):
                    entries.append((label, cat, sub, fold, foldTitle))
            # walk the args too (lambdas inside)
            walk(s, p + 1, e, cat, sub, fold, foldTitle, depth)
            i = e + 1
            continue
        # a call: into the function, if it's one of ours
        name = m.group(1)
        if name in funcs and name not in ('SettingsDialog',):
            key = (name, cat, sub, fold)
            if key not in seen_calls:
                seen_calls.add(key)
                for (fs, fa, fb) in funcs[name]:
                    walk(fs, fa + 1, fb, cat, sub, fold, foldTitle, depth + 1)
        i = p + 1

# the sections: the `when (category.label)` branches in SettingsDialog
sd_path = [p for p in src if p.endswith('SettingsDialog.kt')][0]
s = src[sd_path]
w = s.index('when (category.label) {')
wb = match(s, s.index('{', w), '{', '}')
branches = list(re.finditer(r'\n\s*"([A-Za-z ]+)"\s*->', s[w:wb]))
for n, m in enumerate(branches):
    a = w + m.end()
    b = w + branches[n + 1].start() if n + 1 < len(branches) else wb
    walk(s, a, b, m.group(1), None, None, None, 0)

# tidy: drop very generic labels, keep the first place each label appears in each section
skip = {'Off', 'On', 'Custom', 'Style', 'Size', 'Opacity', 'Colour', 'Color', 'Speed', 'Strength', 'Show', 'Motion', 'Background'}
out, keys = [], set()
for (label, cat, sub, fold, foldTitle) in entries:
    label = label.replace('\\n', ' ').strip()
    if not label or label in skip or len(label) > 70: continue
    k = (label.lower(), cat)
    if k in keys: continue
    keys.add(k)
    out.append((label, cat, sub, fold, foldTitle))

def kq(x):
    return 'null' if x is None else json.dumps(x, ensure_ascii=False).replace('$', '\\$')

lines = ',\n'.join(f'        E({kq(l)}, {kq(c)}, {kq(su)}, {kq(fo)}, {kq(ft)})' for (l, c, su, fo, ft) in out)
kt = f'''package com.shelf.launcher.ui

/*
 * GENERATED by tools/settings_index/gen.py from the settings screens: {len(out)} settings with the section, sub-page
 * and fold group each one is in (Settings › search). Don't edit by hand; run the script again after changing settings.
 */
internal object SettingsIndex {{
    class E(val label: String, val section: String, val subpage: String?, val foldKey: String?, val foldTitle: String?)

    val all: List<E> = listOf(
{lines}
    )
}}
'''
open(OUT, 'w', encoding='utf-8').write(kt)
print(len(out), 'settings ->', OUT)
from collections import Counter
print(Counter(c for _, c, _, _, _ in out))

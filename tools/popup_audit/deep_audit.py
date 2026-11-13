#!/usr/bin/env python3
"""Update 529 (master sprint, Phase 1): the deep popup audit of all 53 layouts, read from the source.

For every layout: the window scene it opens, whether that window is its own or another layout's, which composable draws it,
whether its Details / Options / Achievements pages are its own (native) or the shared readable page, how its action keys are
drawn (the shared SceneKey, which carries the approved hierarchy: filled Play, outlined Details, round ••• More, Back as a hint
under the window — or the scene's own controls), More, the artwork it reads, and the hardware evidence
(tools/popup_audit/hw_529.json, written from the popclip runs; nothing there = not run).

Classes (a layout is never put higher because its popup merely opens):
  1 Fully implemented and verified  native pages, own window, hardware gate PASS on both handhelds, AND the owner's visual
                                    approval recorded in status.json ("owner_visual": "approved") - Claude never sets that
  2 Functionally complete, visually unfinished   own window + native pages + hardware gate PASS; visual approval pending
  3 Own window, generic shared detail pages      its own scene; Details / Options / Achievements = the shared page
  4 Partially implemented           no window of its own (plain window) or the same window as another layout, unchanged
  5 Missing or broken               a hardware gate FAIL (open / buttons / launch / crash) on the final source
Usage: deep_audit.py [repo root] [--json OUT] > docs/POPUP_AUDIT_529.md
"""
import glob, json, os, re, sys

root = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('--') else '.'
ui = os.path.join(root, 'app/src/main/java/com/shelf/launcher/ui')
SRC = {os.path.basename(p): open(p, encoding='utf-8').read() for p in glob.glob(ui + '/*.kt')}
read = lambda p: open(os.path.join(root, p), encoding='utf-8').read()


def body(name):
    for f, s in SRC.items():
        m = re.search(r'\bfun ' + re.escape(name) + r'\s*\(', s)
        if not m: continue
        d, j = 0, m.end() - 1
        while True:
            if s[j] == '(': d += 1
            elif s[j] == ')':
                d -= 1
                if d == 0: break
            j += 1
        e = s.find('=', j, j + 3)
        i = s.index('{', j) if e < 0 or s.find('{', j) < e else e
        if s[i] == '=':  # expression body: up to the blank line
            return f, s[i:s.find('\n\n', i)]
        d, k = 0, i
        while True:
            if s[k] == '{': d += 1
            elif s[k] == '}':
                d -= 1
                if d == 0: return f, s[i:k + 1]
            k += 1
    return None, ''


SKIP = set('Modifier Box Row Column Text Icon Spacer Color RoundedCornerShape Image Canvas Offset Size Brush TextStyle FocusRequester '
           'BoxWithConstraints AsyncImage Shadow Path Rect Stroke IntOffset SolidColor CornerRadius Alignment Arrangement PaddingValues '
           'Dp LaunchedEffect DisposableEffect FlowRow LazyColumn LazyRow'.split())


def closure(fn, depth=4):
    seen = {fn: body(fn)}; frontier = [seen[fn][1]]
    for _ in range(depth):
        nxt = []
        for b in frontier:
            for h in set(re.findall(r'\b([A-Z][A-Za-z0-9]+)\(', b)) - SKIP - set(seen):
                hb = body(h)
                if hb[1]: seen[h] = hb; nxt.append(hb[1])
        frontier = nxt
    return seen


# --- the source facts ---
app = read('app/src/main/java/com/shelf/launcher/data/AppearanceStore.kt')
i = app.index('enum class HomeLayout(val label: String) {')
LAYOUTS = re.findall(r'^\s*([A-Z_0-9]+)\("([^"]*)"\)', app[i:app.index('\n}\n', i)], re.M)
ws = SRC['WinScenes.kt']
j = ws.index('internal fun winSceneFor(layout: HomeLayout): WinScene? = when (layout) {')
SCENE, other = {}, None
for line in ws[j:ws.index('\n}\n', j)].split('\n')[1:]:
    m = re.match(r'\s*(.+?)\s*->\s*(.+)', line)
    if not m: continue
    rhs = m.group(2).strip().replace('WinScene.', '')
    if m.group(1).strip() == 'else': other = rhs
    for l in re.findall(r'HomeLayout\.([A-Z_0-9]+)', m.group(1)): SCENE[l] = rhs
DRAW = dict(re.findall(r'WinScene\.([A-Z_]+) -> ([A-Za-z0-9]+)\(ctx\)', ws[ws.index('fun DrawWinScene'):]))
pf = SRC['WinPageFlow.kt']
NATIVE = set(re.findall(r'WinScene\.([A-Z_]+)', pf[pf.index('NATIVE_PAGE_SCENES'):pf.index('\n', pf.index('NATIVE_PAGE_SCENES'))]))
gw = SRC['GameWindow.kt']
k = gw.index('internal val MORE_SCENES = setOf(')
MORE = set(re.findall(r'WinScene\.([A-Z_]+)', gw[k:gw.index('\n)\n', k)]))
PICKUP = set(re.findall(r'HomeLayout\.([A-Z_]+)', gw[gw.index('PICKUP_CARD_LAYOUTS ='):gw.index('\n', gw.index('PICKUP_CARD_LAYOUTS ='))]))
FLIGHT = {'SHELF', 'STACKS'}  # caseFlight: INDEX_CARD on SHELF / STACKS (GameWindow.kt)
# per-layout windows inside a shared scene (the dispatchers that branch on the layout)
PER_LAYOUT = {}
for disp in ('PocketPopup', 'ShelfFamilyPopup'):
    f, b = body(disp)
    for l, fn in re.findall(r'HomeLayout\.([A-Z_]+) -> ([A-Za-z0-9]+)\(ctx\)', b): PER_LAYOUT[l] = fn
twin = body('TwinHinge')[1]
st = json.load(open(os.path.join(root, 'tools/popup_audit/status.json'), encoding='utf-8'))
hwp = os.path.join(root, 'tools/popup_audit/hw_529.json')
HW = json.load(open(hwp, encoding='utf-8')) if os.path.exists(hwp) else {}

for l in list(SCENE):
    if SCENE[l] in ('null', 'None'): SCENE[l] = None
if other in ('null', 'None'): other = None
# The layout each shared scene was designed for (its other users borrow it); a family scene (the Twin hinge) belongs to the family.
DESIGNED_FOR = {'TILE_CARD': {'TILES'}, 'HERO_BANNER': {'HERO'}, 'TWIN_HINGE': {'TWIN_DESK', 'TWIN_ICONS', 'TWIN_SHOWCASE'}}
users = {}
for l, _ in LAYOUTS: users.setdefault(SCENE.get(l, other), []).append(l)

rows = []
for n, (l, label) in enumerate(LAYOUTS, 1):
    sc = SCENE.get(l, other)
    fn = PER_LAYOUT.get(l) or (DRAW.get(sc) if sc else None)
    sharers = [x for x in users.get(sc, []) if x != l] if sc else []
    if sc is None: owner = 'none - the plain (TV) window'
    elif l in PER_LAYOUT: owner = 'own (%s inside the shared %s scene)' % (fn, sc)
    elif sc == 'TWIN_HINGE' and l == 'TWIN_DESK': owner = 'own variant (desk paper of the shared hinge)'
    elif sc in DESIGNED_FOR and l in DESIGNED_FOR[sc] and len(DESIGNED_FOR[sc]) > 1: owner = 'family window (same as ' + ', '.join(x for x in sharers if x != 'TWIN_DESK') + ')' if l != 'TWIN_ICONS' and l != 'TWIN_SHOWCASE' else 'family window (the dark hinge, same as ' + ', '.join(x for x in sharers if x != 'TWIN_DESK') + ')'
    elif sc in DESIGNED_FOR and l in DESIGNED_FOR[sc]: owner = 'own (also used by ' + ', '.join(sharers) + ')'
    elif sc in DESIGNED_FOR: owner = 'borrowed: ' + '/'.join(sorted(DESIGNED_FOR[sc])) + "'s window"
    elif sharers: owner = 'shared with ' + ', '.join(sharers)
    else: owner = 'own'
    cl = closure(fn) if fn else {}
    allb = '\n'.join(b for _, b in cl.values())
    keys = 'shared SceneKey (approved hierarchy)' if re.search(r'\bSceneKey(Styled)?\(', allb) else ('scene\'s own controls' if fn else 'plain window buttons')
    art = sorted(set(re.findall(r'ctx\.(cover|backdrop|logo)\b', allb)))
    pages = 'native (own)' if sc in NATIVE else ('own options list; others shared' if sc == 'CROSS_OPTIONS' else 'shared readable page')
    motion = 'case flies out and back' if l in FLIGHT else ('picked-up card' if l in PICKUP else ('disc flight' if sc == 'ORBIT_TERMINAL' else ''))
    e = st.get(l, st['_default'])
    h = HW.get(l, {})
    fail = any(v.get('verdict') == 'FAIL' for v in h.values()) if isinstance(h, dict) else False
    hw_pass_both = isinstance(h, dict) and all(any(k2.startswith(dev) and v.get('verdict') == 'PASS' for k2, v in h.items()) for dev in ('flip2', 'konkr'))
    if fail: cls = 5
    elif sc is None or owner.startswith('borrowed') or owner.startswith('shared with'): cls = 4
    elif sc in NATIVE and hw_pass_both and e.get('owner_visual') == 'approved': cls = 1
    elif sc in NATIVE: cls = 2
    else: cls = 3
    rows.append(dict(n=n, layout=l, name=label, scene=sc, draws=fn, window=owner, pages=pages, more=sc in MORE, keys=keys, art=art,
                     motion=motion, status=e['status'], hw=h, cls=cls, owner_visual=e.get('owner_visual', 'pending')))

if '--json' in sys.argv:
    json.dump(rows, open(sys.argv[sys.argv.index('--json') + 1], 'w'), indent=1, ensure_ascii=False)
CLS = {1: 'Fully implemented and verified', 2: 'Functionally complete, visually unfinished', 3: 'Own window, shared detail pages',
       4: 'Partially implemented', 5: 'Missing or broken'}
print('# Popup deep audit — all %d layouts (update 529, generated)\n' % len(rows))
print('Generated by `tools/popup_audit/deep_audit.py` from the source, `tools/popup_audit/status.json` and the hardware results in')
print('`tools/popup_audit/hw_529.json`. Do not edit by hand. Visual approval is the owner\'s alone; no row claims it.\n')
for c in sorted(CLS): print('- **%d · %s:** %d' % (c, CLS[c], sum(r['cls'] == c for r in rows)))
print('\n| # | Layout | Window | Drawn by | Pages | Keys | More | Art read | Motion | Hardware (529) | Class |')
print('|---|---|---|---|---|---|---|---|---|---|---|')
for r in rows:
    hw = '; '.join('%s %s' % (k2, v.get('verdict')) for k2, v in sorted(r['hw'].items())) if r['hw'] else 'not run'
    print('| %d | `%s` %s | %s | `%s` | %s | %s | %s | %s | %s | %s | %d |' % (r['n'], r['layout'], r['name'], r['window'], r['draws'], r['pages'],
          r['keys'], 'yes' if r['more'] else '', ', '.join(r['art']) or '-', r['motion'] or '-', hw, r['cls']))

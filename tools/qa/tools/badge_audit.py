#!/usr/bin/env python3
"""Batch 521b / 523: console badge / logo coverage audit, read from the shipped code and assets (no device needed).

usage: python3 tools/qa/tools/badge_audit.py [--json out.json] [--md out.md]
For every platform key Voltcap names (data/ConsoleNames.kt) it resolves, exactly as ui/ConsoleBadge.kt does (Batch 522/523):
  Text look    the console's SHORT name in the pill, never a logo
  Symbol look  BadgeLogos.symbol -> badge_icons/<key>.webp, else the short name
  Logo look    BadgeLogos.wordmark when it fits and reads (showsWordmark, at a 96 dp and a 200 dp tile), else the compact
               form (symbol, else name); no wordmark: the ES-DE logo with a measured form (ESDE_FORM) when it reads,
               else the name. Silhouette-unreadable ES-DE logos are never used.
  folder logo  PlatformLogos.url -> platform_logos/<key>.svg, else SystemLogos.bundled, else none (name)
  mono icon    PlatformLogos.plain -> platform_icons_mono/<key>.png
  RA icon      ConsoleLogos.raIcon: the RA console name it would look up (icons need an RA account; not shipped)
"Open" flags = what is still true today. Defects found and fixed in Batches 521b-523 are listed separately (HISTORY) and are
re-checked: a fixed defect that comes back is reported as REGRESSION.
"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
SRC = os.path.join(ROOT, 'app/src/main/java/com/shelf/launcher')
ASSETS = os.path.join(ROOT, 'app/src/main/assets')


def read(rel):
    return open(os.path.join(SRC, rel), encoding='utf-8').read()


def kv_map(text, start_pat):
    """`"a" to "b"` / `"a" to 1.2f` / `"a" to listOf("x","y")` pairs inside the first mapOf( after start_pat."""
    i = text.index(start_pat)
    j = text.index('mapOf(', i) + 6
    depth, k = 1, j
    while depth:
        c = text[k]
        depth += c == '('
        depth -= c == ')'
        k += 1
    body = re.sub(r'//[^\n]*', '', text[j:k - 1])
    out = {}
    for m in re.finditer(r'"([^"]+)"\s+to\s+(listOf\(([^)]*)\)|"([^"]*)"|([0-9.]+)f)', body):
        if m.group(3) is not None:
            out[m.group(1)] = re.findall(r'"([^"]+)"', m.group(3))
        elif m.group(4) is not None:
            out[m.group(1)] = m.group(4)
        else:
            out[m.group(1)] = float(m.group(5))
    return out


def set_of(text, start_pat):
    i = text.index(start_pat)
    j = text.index('setOf(', i) + 6
    k = text.index(')', j)
    return set(re.findall(r'"([^"]+)"', text[j:k]))


def assets(d, ext):
    p = os.path.join(ASSETS, d)
    return {f[:-len(ext)] for f in os.listdir(p) if f.endswith(ext)} if os.path.isdir(p) else set()


cb, pl, sl, cn, ra, cl = (read('ui/ConsoleBadge.kt'), read('ui/PlatformLogos.kt'), read('data/SystemLogos.kt'),
                          read('data/ConsoleNames.kt'), read('data/RaMatchStore.kt'), read('data/ConsoleLogos.kt'))
NAMES = kv_map(cn, 'private val names')
SHORT = kv_map(cn, 'private val shortNames')
SHARED = {'SFC', 'WonderSwanColor', 'Pico8', 'MAME', 'FBNeo'}  # one emblem both models print / one console under two keys
B_ASPECT = kv_map(cb, 'private val ASPECT')
B_ALIAS = kv_map(cb, 'private val ALIAS')
B_ICON = kv_map(cb, 'private val ICON')
P_KEYS = set_of(pl, 'private val KEYS')
P_ALIAS = kv_map(pl, 'private val ALIAS')
ESDE = kv_map(sl, 'private val ESDE')
BUNDLED = set_of(sl, 'private val BUNDLED')
NOT_AUTH = set_of(sl, 'NOT_AUTHENTIC')
BLOCK = set_of(sl, 'SILHOUETTE_UNREADABLE')  # one-colour block logos -> the name (Batch 522)
LETTER = kv_map(cb, 'private val LETTERING')
W_ALIAS = kv_map(cb, 'private val WORDMARK_ALIAS')
_ef = cb[cb.index('private val ESDE_FORM'):]
_ef = _ef[:_ef.index('\n    )')]
ESDE_FORM = {m.group(1): (float(m.group(2)), float(m.group(3))) for m in re.finditer(r'"([^"]+)"\s+to\s+\(([0-9.]+)f\s+to\s+([0-9.]+)f\)', _ef)}
RA = kv_map(ra, 'PLATFORM_TO_RA_CONSOLE')
RA_EXTRA = kv_map(cl, 'private val EXTRA')
A_BADGE_LOGOS, A_BADGE_ICONS = assets('badge_logos', '.svg'), assets('badge_icons', '.webp')
A_PLAT, A_PLAT_PNG, A_MONO = assets('platform_logos', '.svg'), assets('platform_logos_png', '.png'), assets('platform_icons_mono', '.png')
A_SYS, A_SYS_PNG = assets('system_logos', '.svg'), assets('system_logos_png', '.png')

MAKERS = [
    ('Nintendo', ['NES', 'Famicom', 'FDS', 'SNES', 'SFC', 'N64', 'GameCube', 'Wii', 'WiiU', 'Switch', 'Switch2', 'GB', 'GBC', 'GBA',
                  'NDS', '3DS', 'VirtualBoy', 'PokemonMini']),
    ('Sony', ['PS1', 'PS2', 'PS3', 'PS4', 'PS5', 'PSP', 'PSVita']),
    ('Microsoft', ['Xbox', 'Xbox360', 'XboxOne', 'XboxSeries']),
    ('Sega', ['SG1000', 'MasterSystem', 'Genesis', 'MegaDrive', 'SegaCD', '32X', 'Saturn', 'Dreamcast', 'GameGear', 'Naomi']),
    ('NEC / Hudson', ['PCEngine', 'PCEngineCD', 'TG16', 'TGCD']),
    ('SNK', ['NeoGeo', 'NeoGeoCD', 'NeoGeoPocket', 'NeoGeoPocketColor']),
    ('Atari', ['Atari2600', 'Atari5200', 'Atari7800', 'Lynx', 'Jaguar']),
    ('Bandai', ['WonderSwan', 'WonderSwanColor']),
    ('Other', ['3DO', 'CDi', 'ColecoVision', 'Intellivision', 'Vectrex', 'Atomiswave', 'Arcade', 'C64', 'Amiga', 'MSX', 'DOS',
               'ScummVM', 'PICO8', 'Pico8', 'Android']),
    ('PC / stores', ['Steam', 'Epic', 'GOG', 'Amazon', 'PC']),
]
ALIAS_KEYS = {'Pico8': 'PICO8'}  # a second key for the same console (counted once as a console)
STORES = {'Steam', 'Epic', 'GOG', 'Amazon', 'PC'}
listed = {k for _, ks in MAKERS for k in ks}
extra = sorted(k for k in NAMES if k not in listed and k not in ('Disc', 'App'))
if extra:
    MAKERS.append(('Unsorted', extra))


def fits(aspect, h, cap):
    lh = h * 1.15
    return (cap - lh * 0.7) / (lh * 0.62 * aspect) >= 0.9


def art_h(aspect, letter, h, cap):
    lh = h * 1.15
    boost = min(1.3, max(1.0, (0.75 / min(1.0, max(0.1, letter))) ** 0.5))
    return min(lh * 0.62 * boost, (cap - lh * 0.7) / aspect)


def shows(aspect, letter, tile):
    h = min(20.0, max(11.0, tile * 0.13))
    cap = tile * (0.78 if tile < 150 else 0.85)
    return fits(aspect, h, cap) and art_h(aspect, letter, h, cap) * letter >= h * 0.26


# (Batch 521b-523) defects found by the audits and fixed; each is re-checked below - one that comes back is a REGRESSION.
HISTORY = [
    ('Famicom, Mega Drive, TG16 / TGCD, NGPC badges wore NES / GENESIS / PC Engine / Neo Geo Pocket lettering', '521b'),
    ('Mega Drive / TG16 / TGCD folders used the Genesis / PC Engine logo', '521b'),
    ('"FAMICOM DISK SY..." cut at 96 / 140 dp', '521b'),
    ('one-colour block ES-DE logos (3DO, DOS, Genesis, Mega Drive, MSX, Pokémon mini, Sega CD, Vectrex)', '522a'),
    ('3DO set art drew a white rectangle', '522a'),
    ('grey "no colour" pills (PS4, PS5, Xbox 360 / One / Series, Switch 2, regional models)', '522a'),
    ('Text look drew ES-DE logos (text and logos mixed in one look)', '522c'),
    ('Logo look mixed symbols and wordmarks', '522c'),
    ('very wide wordmarks shrunk in small badges (GBA, Lynx, Genesis, GBC, 3DS, Pokémon mini)', '522c / 522e'),
    ('emblem + small-word wordmarks read smaller than the others (N64, GameCube, Xbox, NES, Master System...)', '523'),
    ('Logo-look ES-DE fallback in a fixed-width pill (TurboGrafx, NGPC, WSC tiny)', '523'),
    ('ScummVM / Steam ES-DE logos unreadable in one colour', '523'),
    ('name / compact pills lower than the artwork pills in the Symbol and Logo looks', '523'),
]


def resolve(p):
    r = {'key': p, 'name': NAMES.get(p, p), 'short': SHORT.get(p, p).upper()}
    esde = [n for n in ESDE.get(p, []) if n in BUNDLED]
    r['system_logo'] = esde[0] if esde else None
    r['text_badge'] = ('text', r['short'])
    sk = B_ALIAS.get(p, p)
    r['symbol_badge'] = ('symbol', 'badge_icons/%s.webp' % sk) if sk in B_ICON else ('text', r['short'])
    wk = W_ALIAS.get(p, p)
    letter = LETTER.get(wk, 1.0)
    r['wordmark'] = 'badge_logos/%s.svg' % wk if wk in B_ASPECT else None
    r['aspect'] = B_ASPECT.get(wk)
    r['lettering'] = letter
    usable_esde = esde[0] if esde and esde[0] not in BLOCK else None

    def logo_at(tile):
        if r['wordmark']:
            if shows(r['aspect'], letter, tile):
                return ('wordmark', r['wordmark'])
            return r['symbol_badge'] if r['symbol_badge'][0] == 'symbol' else ('text', r['short'])
        if usable_esde and usable_esde in ESDE_FORM and shows(ESDE_FORM[usable_esde][0], ESDE_FORM[usable_esde][1], tile):
            return ('es-de logo', 'system_logos/%s.svg' % usable_esde)
        return ('text', r['short'])
    r['logo_96'], r['logo_200'] = logo_at(96), logo_at(200)
    pk = P_ALIAS.get(p, p)
    r['folder_logo'] = ('platform_logos/%s.svg' % pk) if pk in P_KEYS else (('system_logos/%s.svg' % esde[0]) if esde else None)
    r['mono_icon'] = ('platform_icons_mono/%s.png' % pk) if pk in P_KEYS and pk in A_MONO else None
    r['ra_console'] = RA.get(p) or RA_EXTRA.get(p)
    f, reg = [], []
    # still-true facts (open)
    if not r['wordmark'] and r['logo_200'][0] == 'text':
        f.append('no logo artwork: name in every look' if r['symbol_badge'][0] == 'text' else 'no wordmark: name in the Logo look')
    if r['wordmark'] and r['logo_96'][0] != 'wordmark':
        f.append('compact (%s) at 96 dp, wordmark on big tiles' % r['logo_96'][0])
    if r['logo_200'][0] == 'es-de logo':
        f.append('Logo look uses the ES-DE logo (no set wordmark)')
    if not r['folder_logo']:
        f.append('no folder logo')
    if p in SHARED and sk != p and r['symbol_badge'][0] == 'symbol':
        f.append('shares the %s emblem (printed on both models)' % sk)
    # regressions of fixed defects
    if wk != p and wk not in ('PICO8', 'Arcade'):
        reg.append('wordmark borrowed from %s' % wk)
    if p not in SHARED and sk != p and r['symbol_badge'][0] == 'symbol':
        reg.append('symbol borrowed from %s' % sk)
    if borrowed_folder(p, pk):
        reg.append('folder logo shows %s branding' % pk)
    for look in ('logo_96', 'logo_200'):
        if r[look][0] == 'es-de logo' and r['system_logo'] in BLOCK:
            reg.append('one-colour block logo drawn')
    for path in [r['symbol_badge'][1] if r['symbol_badge'][0] == 'symbol' else None,
                 r['logo_200'][1] if r['logo_200'][0] != 'text' else None, r['folder_logo'], r['mono_icon']]:
        if path and not os.path.isfile(os.path.join(ASSETS, path)):
            reg.append('BROKEN REFERENCE %s' % path)
    for n in ESDE.get(p, []):
        if n in NOT_AUTH and n in BUNDLED and r['system_logo'] == n:
            f.append('uses not-authentic %s' % n)
    r['flags'], r['regressions'] = f, reg
    return r


def borrowed_folder(p, pk):
    return pk != p and pk in P_KEYS and p not in SHARED


rows = [(m, [resolve(k) for k in ks]) for m, ks in MAKERS]
keys = [r['key'] for _, rs in rows for r in rs]
summary = {
    'assets': {'badge_logos': len(A_BADGE_LOGOS), 'badge_icons': len(A_BADGE_ICONS), 'platform_logos': len(A_PLAT),
               'platform_logos_png': len(A_PLAT_PNG), 'platform_icons_mono': len(A_MONO), 'system_logos': len(A_SYS),
               'system_logos_png': len(A_SYS_PNG)},
    'unused_assets': {
        'badge_logos': sorted(A_BADGE_LOGOS - set(B_ASPECT)), 'badge_icons': sorted(A_BADGE_ICONS - set(B_ICON)),
        'platform_logos': sorted(A_PLAT - P_KEYS), 'system_logos': sorted(A_SYS - BUNDLED)},
    'code_without_asset': {
        'badge_logos': sorted(set(B_ASPECT) - A_BADGE_LOGOS), 'badge_icons': sorted(set(B_ICON) - A_BADGE_ICONS),
        'platform_logos': sorted(P_KEYS - A_PLAT), 'platform_logos_png': sorted(P_KEYS - A_PLAT_PNG),
        'system_logos': sorted(BUNDLED - A_SYS), 'system_logos_png': sorted(BUNDLED - A_SYS_PNG)},
    # (Batch 523: one count, explained - the docs said 69, the old gallery header 73.)
    'platform_keys': len(keys),
    'consoles': len([k for k in keys if k not in ALIAS_KEYS and k not in STORES]),
    'alias_keys': sorted(k for k in keys if k in ALIAS_KEYS),
    'pc_store_categories': sorted(k for k in keys if k in STORES),
    'regressions': sum(len(r['regressions']) for _, rs in rows for r in rs),
}
if '--json' in sys.argv:
    json.dump({'summary': summary, 'rows': rows, 'history': HISTORY}, open(sys.argv[sys.argv.index('--json') + 1], 'w'), indent=1)


def cell(v):
    return v[0] if v[0] == 'text' and False else ('"%s"' % v[1] if v[0] == 'text' else '%s %s' % (v[0], os.path.basename(v[1])))


lines = ['## Coverage (current code)', '',
         '| Maker | Key | Text | Symbol | Logo 96 dp | Logo 200 dp | Folder logo | Mono | RA name | Open (still true) | Regression |',
         '|---|---|---|---|---|---|---|---|---|---|---|']
for m, rs in rows:
    for r in rs:
        lines.append('| %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |' % (
            m, r['key'], cell(r['text_badge']), cell(r['symbol_badge']), cell(r['logo_96']), cell(r['logo_200']),
            os.path.basename(r['folder_logo'] or '-'), os.path.basename(r['mono_icon'] or '-'), r['ra_console'] or '-',
            '; '.join(r['flags']) or '-', '; '.join(r['regressions']) or '-'))
lines += ['', '## Fixed defects (history, re-checked above: a fixed one that comes back shows under Regression)', '',
          '| Defect | Fixed in |', '|---|---|'] + ['| %s | %s |' % h for h in HISTORY]
md = '\n'.join(lines)
if '--md' in sys.argv:
    open(sys.argv[sys.argv.index('--md') + 1], 'w').write(md + '\n')
print(json.dumps(summary, indent=1))
print(md)
sys.exit(1 if summary['regressions'] else 0)

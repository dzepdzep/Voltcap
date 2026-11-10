#!/usr/bin/env python3
"""Batch 521b: console badge / logo coverage audit, read from the shipped code and assets (no device needed).

usage: python3 tools/qa/tools/badge_audit.py [--json out.json] [--md out.md]
For every console key Voltcap names (data/ConsoleNames.kt) it resolves, exactly as the app's code does:
  text badge   ConsoleBadge with Logos OFF: SystemLogos.bundled(platform) -> an ES-DE logo in the pill, else the NAME as text
  logo badge   ConsoleBadge with Logos ON: BadgeLogos.of -> symbol (badge_icons) or wordmark (badge_logos), else as above
  folder logo  PlatformLogos.url -> platform_logos/<key>.svg, else SystemLogos.bundled, else none (name)
  mono icon    PlatformLogos.plain -> platform_icons_mono/<key>.png
  RA icon      ConsoleLogos.raIcon: the RA console name it would look up (icons need an RA account; not shipped)
and flags: missing, falls back to another console's asset (regional variants), symbol / wordmark mix, very wide logos.
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
]
listed = {k for _, ks in MAKERS for k in ks}
extra = sorted(k for k in NAMES if k not in listed and k not in ('Disc', 'App'))
if extra:
    MAKERS.append(('Unsorted', extra))


def resolve(p):
    r = {'key': p, 'name': NAMES.get(p, p)}
    esde = [n for n in ESDE.get(p, []) if n in BUNDLED]
    r['system_logo'] = esde[0] if esde else None
    # text badge (Logos off): an ES-DE logo whenever one is bundled, else the console's name as text
    # (Batch 522: not a logo that turns into a solid block drawn in one colour - SystemLogos.SILHOUETTE_UNREADABLE)
    r['text_badge'] = ('logo', 'system_logos/%s.svg' % esde[0]) if esde and esde[0] not in BLOCK else ('text', SHORT.get(p, p).upper())
    bk = B_ALIAS.get(p, p)
    if bk in B_ICON:
        r['logo_badge'] = ('symbol', 'badge_icons/%s.webp' % bk, bk)
    elif bk in B_ASPECT:
        r['logo_badge'] = ('wordmark', 'badge_logos/%s.svg' % bk, bk)
    else:
        r['logo_badge'] = r['text_badge'][:1] + (r['text_badge'][1], None)
    r['wordmark'] = 'badge_logos/%s.svg' % bk if bk in A_BADGE_LOGOS else None
    r['aspect'] = B_ASPECT.get(bk)
    pk = P_ALIAS.get(p, p)
    r['folder_logo'] = ('platform_logos/%s.svg' % pk) if pk in P_KEYS else (('system_logos/%s.svg' % esde[0]) if esde else None)
    r['mono_icon'] = ('platform_icons_mono/%s.png' % pk) if pk in P_KEYS and pk in A_MONO else None
    r['ra_console'] = RA.get(p) or RA_EXTRA.get(p)
    # flags
    f = []
    borrowed = lambda k: k is not None and k != p
    if borrowed(B_ALIAS.get(p)) and r['logo_badge'][0] in ('symbol', 'wordmark'):
        f.append(('shares the %s emblem' if p in SHARED else 'logo badge shows %s branding') % B_ALIAS[p])
    if borrowed(P_ALIAS.get(p)) and pk in P_KEYS and p not in SHARED:
        f.append('folder logo shows %s branding' % P_ALIAS[p])
    if r['text_badge'][0] == 'logo':
        f.append('text badge draws a logo')
    if r['aspect'] and r['aspect'] >= 8:
        f.append('very wide wordmark (%.1f:1)' % r['aspect'])
    if r['logo_badge'][0] in ('text', 'logo'):
        f.append('no badge-set logo')
    if not r['folder_logo']:
        f.append('no folder logo')
    if not r['mono_icon']:
        f.append('no mono icon')
    if esde and esde[0] in BLOCK:
        f.append('ES-DE logo unreadable in one colour (name shown there)')
    for path in [r['text_badge'][1] if r['text_badge'][0] == 'logo' else None, r['logo_badge'][1] if r['logo_badge'][0] != 'text' else None,
                 r['folder_logo'], r['mono_icon']]:
        if path and not os.path.isfile(os.path.join(ASSETS, path)):
            f.append('BROKEN REFERENCE %s' % path)
    for n in ESDE.get(p, []):
        if n in NOT_AUTH and n in BUNDLED and r['system_logo'] == n:
            f.append('uses not-authentic %s' % n)
    r['flags'] = f
    return r


rows = [(m, [resolve(k) for k in ks]) for m, ks in MAKERS]
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
    'consoles': sum(len(r) for _, r in rows),
}
if '--json' in sys.argv:
    json.dump({'summary': summary, 'rows': rows}, open(sys.argv[sys.argv.index('--json') + 1], 'w'), indent=1)
lines = ['| Maker | Console | Text badge (Logos off) | Logo badge (Logos on) | Folder logo | Mono | RA name | Flags |', '|---|---|---|---|---|---|---|---|']
for m, rs in rows:
    for r in rs:
        tb = r['text_badge'][0] + (': ' + os.path.basename(r['text_badge'][1]) if r['text_badge'][0] == 'logo' else ': "%s"' % r['text_badge'][1])
        lb = r['logo_badge'][0] + (': ' + os.path.basename(r['logo_badge'][1]) if r['logo_badge'][0] != 'text' else '')
        lines.append('| %s | %s | %s | %s | %s | %s | %s | %s |' % (m, r['key'], tb, lb, os.path.basename(r['folder_logo'] or '-'),
                                                                os.path.basename(r['mono_icon'] or '-'), r['ra_console'] or '-', '; '.join(r['flags'])))
md = '\n'.join(lines)
if '--md' in sys.argv:
    open(sys.argv[sys.argv.index('--md') + 1], 'w').write(md + '\n')
print(json.dumps(summary, indent=1))
print(md)

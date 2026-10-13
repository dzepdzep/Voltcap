#!/usr/bin/env python3
"""Builds the animated theme scenes from tools/themes/scenes/*.agsl.

  python tools/themes/gen.py  ->  src/ui/ThemeShaders.kt   (the app's copy, Android 13+)
                                  tools/themes/preview.html (all scenes in a browser, WebGL)

Each scene file is AGSL kept to the GLSL ES 1.0 subset, so the preview runs the exact same text.
"//#include name" pulls in scenes/_name.agsl; every scene gets _prelude.agsl first.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCENES = os.path.join(HERE, "scenes")
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))


def _src_root():
    """The app's source root (the package root holding ui/). Default: the repo's src (or src_copy) next to
    tools/; override with --src DIR or the VOLTCAP_SRC environment variable (a copy of the tools elsewhere)."""
    if "--src" in sys.argv:
        i = sys.argv.index("--src")
        return os.path.abspath(sys.argv[i + 1])
    if os.environ.get("VOLTCAP_SRC"):
        return os.path.abspath(os.environ["VOLTCAP_SRC"])
    return os.path.join(ROOT, "src") if os.path.isdir(os.path.join(ROOT, "src")) else os.path.join(ROOT, "src_copy")


SRC = _src_root()
KT_OUT = os.path.join(SRC, "ui", "ThemeShaders.kt")
HTML_OUT = os.path.join(HERE, "preview.html")
COLORS_KT = os.path.join(SRC, "ui", "theme", "ShelfColors.kt")

# ThemeMotif name -> scene file
MOTIFS = [
    ("SPARKS", "sparks"), ("LIQUID", "liquid"), ("FLAMES", "flames"), ("ORBIT", "orbit"),
    ("RETRO_GRID", "retro_grid"), ("LEAVES", "leaves"), ("MINIMAL", "minimal"),
    ("BUBBLES", "bubbles"), ("PSY_WAVES", "psy_waves"), ("WISPS", "wisps"), ("LCD", "lcd"),
    ("LEAF_SWIRL", "leaf_swirl"), ("AURA", "aura"), ("AURORA", "aurora"), ("NEBULA", "nebula"),
    ("PIXELS", "pixels"), ("GLASS", "glass"), ("RAIN", "rain"), ("STARS", "stars"),
    ("PETALS", "petals"), ("SCANLINES", "scanlines"), ("MUSIC", "music"),
    ("ANIME_FLAMES", "anime_flames"), ("CEL_SKY", "cel_sky"), ("MECHA_HUD", "mecha_hud"),
    ("MOON_BLADE", "moon_blade"), ("SPECTRUM", "spectrum"), ("PULSE_RING", "pulse_ring"),
    ("BEAT_TUNNEL", "beat_tunnel"), ("CIRCUIT", "circuit"), ("GEARWORKS", "gearworks"),
    ("FROST", "frost"), ("TERRA", "terra"), ("VENOM", "venom"),
    ("MEADOW", "meadow"), ("DOJO", "dojo"), ("GALE", "gale"), ("THICKET", "thicket"), ("BEDROCK", "bedrock"), ("WYRM", "wyrm"), ("UMBRA", "umbra"), ("ALLOY", "alloy"), ("GLIMMER", "glimmer"),
    ("REACTOR", "reactor"), ("THUNDER", "thunder"), ("BLOOM", "bloom"),
    ("TAPE_DECK", "tape_deck"), ("CHLADNI", "chladni"),
    ("LASER_STAGE", "laser_stage"), ("KALEIDO", "kaleido"), ("SHATTER", "shatter"),
    ("TERRAIN", "terrain"), ("FIREWORKS", "fireworks"), ("NEON_PUDDLE", "neon_puddle"),
    ("VINYL", "vinyl"), ("CD_SPIN", "cd_spin"), ("LAVA", "lava"), ("RIBBONS", "ribbons"),
    ("DISCO", "disco"), ("SPEAKER", "speaker"),
    ("ERA_8BIT", "era_8bit"), ("MODE7", "mode7"), ("PS1_POLY", "ps1_poly"), ("PS2_HAZE", "ps2_haze"),
    ("N64_PLAINS", "n64_plains"), ("XMB_WAVE", "xmb_wave"), ("PLAZA", "plaza"),
    ("VECTOR", "vector"), ("BLUE_SKY", "blue_sky"), ("WARP", "warp"),
    ("CAUSTICS", "caustics"), ("INK", "ink"), ("PAPERCUT", "papercut"), ("PARALLAX16", "parallax16"),
    ("HOLO", "holo"), ("CAMPFIRE", "campfire"),
    ("ARENA", "arena"), ("PRISM_ATRIUM", "prism_atrium"), ("ORBIT_STATION", "orbit_station"),
    ("ART_WALL", "art_wall"), ("ART_TUNNEL", "art_tunnel"), ("POP_ART", "pop_art"), ("COVER_RIPPLE", "cover_ripple"),
    ("BLAZE_BEAT", "blaze_beat"), ("SPECIMEN_LAB", "specimen_lab"), ("MAGICAL", "magical"),
    ("SPIRIT_FOREST", "spirit_forest"), ("FESTIVAL", "festival"), ("AURA_CLASH", "aura_clash"),
    ("AURA_BEAT", "aura_beat"),
    ("MOUNTAINS", "mountains"),
    ("SPECIMEN_BEAT", "specimen_beat"),
    ("TOWERS_128", "towers_128"),
    ("CARTRIDGE_16", "cartridge_16"),
    ("HANDHELD", "handheld"),
    ("PIXEL_PARADE", "pixel_parade"),
    ("CAMPFIRE_BEAT", "campfire_beat"),
    ("MANGA_BEAT", "manga_beat"),
    ("COMIC_PANELS", "comic_panels"),
    ("PIXEL_SKY", "pixel_sky"),
    ("COVER_FLOW", "cover_flow"),
    ("RECORD_SHELF", "record_shelf"),
    ("COVER_CUBE", "cover_cube"),
    ("COVER_FLOAT", "cover_float"),
    ("HOLO_COVER", "holo_cover"),
    ("COVER_HALO", "cover_halo"),
    ("BIG_TYPE", "big_type"),
    ("PIXEL_COVER", "pixel_cover"),
    ("POLAROID", "polaroid"),
    ("RETRO_TV", "retro_tv"),
    ("JUKEBOX", "jukebox"),
    ("NEON_FRAME", "neon_frame"),
    ("MAGAZINE", "magazine"),
    ("COVER_FOCUS", "cover_focus"),
    ("CRT_TUNNEL", "crt_tunnel"), ("MEMORY_CARD", "memory_card"), ("ENERGY_CORE", "energy_core"), ("RACING_32", "racing32"), ("FIRE_32", "fire32"),
    ("VOLT_INTRO", "voltcap_intro"), ("VOLT_BEAT", "voltcap_beat"), ("VOLT_PIXELS", "voltcap_pixels"),
    ("RETRO_BOUNCE", "retro_bounce"), ("LYRIC_CLOUD", "lyric_cloud"),
    ("LYRIC_KARAOKE", "lyric_karaoke"), ("LYRIC_SHEET", "lyric_cloud"), ("LYRIC_NEON", "lyric_neon"),
    ("DISC_SLEEVE", "disc_sleeve"),
    ("ALBUM_TIDE", "album_tide"),
    # Existing scenes previously absent from this generator; keep regeneration exhaustive.
    ("CHILL_BLADES", "chill_blades"),
    ("CHILL_POLAR", "chill_polar"),
    ("CHILL_PLANET", "chill_planet"),
    ("CHILL_MOON", "chill_moon"),
    ("CHILL_ORBS", "chill_orbs"),
    ("CHILL_SILK", "chill_silk"),
    ("CHILL_DUNES", "chill_dunes"),
    ("CHILL_MESH", "chill_mesh"),
    ("CHILL_LIGHT", "chill_light"),
    ("CHILL_SHADE", "chill_shade"),
    ("CHILL_RAIN", "chill_rain"),
    ("CHILL_CITY", "chill_city"),
    ("CHILL_JELLY", "chill_jelly"),
    ("CHILL_GLADE", "chill_glade"),
    ("CHILL_HEX", "chill_hex"),
    ("CHILL_CLOUDS", "chill_clouds"),
    ("CHILL_KOI", "chill_koi"),
    ("CHILL_PEAKS", "chill_peaks"),
    ("HANDHELD_BOOT", "handheld_boot"),
    ("POCKET_BEAT", "pocket_beat"),
    ("CONSOLE_DISC", "console_disc"),
    ("ARCADE_ATTRACT", "arcade_attract"),
    ("NEON_CARTRIDGE", "neon_cartridge"),
    ("DATA_STREAM", "data_stream"),
    ("PORTAL_GALLERY", "portal_gallery"),
    ("POSTER_WALL", "poster_wall"),
    ("TROPHY_VAULT", "trophy_vault"),
    ("BADGE_STARS", "badge_stars"),
    ("PIXEL_WORLD", "pixel_world"),
    ("BADGE_BOUNCE", "badge_bounce"),
    ("PX_SPARK", "px_spark"),
    ("PX_TORRENT", "px_torrent"),
    ("PX_SPROUT", "px_sprout"),
    ("PX_MIND", "px_mind"),
    ("PX_BLAZE", "px_blaze"),
    ("PX_PHANTOM", "px_phantom"),
    ("PX_FROST", "px_frost"),
    ("PX_TERRA", "px_terra"),
    ("PX_VENOM", "px_venom"),
    ("PX_MEADOW", "px_meadow"),
    ("PX_DOJO", "px_dojo"),
    ("PX_GALE", "px_gale"),
    ("PX_THICKET", "px_thicket"),
    ("PX_BEDROCK", "px_bedrock"),
    ("PX_WYRM", "px_wyrm"),
    ("PX_UMBRA", "px_umbra"),
    ("PX_ALLOY", "px_alloy"),
    ("PX_GLIMMER", "px_glimmer"),
    ("GAMING_ROOM", "gaming_room"),
    ("STILL_DUSK", "still_dusk"), ("STILL_HORIZON", "still_horizon"), ("STILL_GLOW", "still_glow"),
    ("CALM_RIBBON", "calm_ribbon"), ("CALM_DRIFT", "calm_drift"), ("CALM_HALO", "calm_halo"),
    ("BOLD_SPLIT", "bold_split"), ("BOLD_DOTS", "bold_dots"), ("BOLD_BANDS", "bold_bands"),
]


# Motifs that share another one's scene in the app (PIXEL_FALLS draws its frames over the 32-bit Voltcap sky).
ALSO = {"VOLT_PIXELS": ["PIXEL_FALLS"]}


# Update 158: motifs drawn in Compose with no shader at all (Lobby, LobbyScene.kt): "" / empty sets, so the whens stay
# exhaustive without an else.
NO_SCENE = ["LOBBY"]


def _case(motif):
    return ", ".join("ThemeMotif." + m for m in [motif] + ALSO.get(motif, []))


# Game showcase motifs are drawn in Compose from the game's own art (ShowcaseScene); the shader here is
# only what shows while a game has no art yet (and the Android 12 fallback's stand-in).
SHOWCASE = [("SHOWCASE_PARALLAX", "showcase_bg"), ("SHOWCASE_CINEMA", "showcase_bg"),
            ("SHOWCASE_FILMSTRIP", "showcase_bg"), ("SHOWCASE_MOSAIC", "showcase_bg"),
            ("SHOWCASE_GLASS", "glass"), ("SHOWCASE_CRT", "crt_room")]


def read(name):
    with open(os.path.join(SCENES, name + ".agsl"), encoding="utf-8") as f:
        return f.read()


def build(scene, body_only=False):
    body = read(scene)
    def inc(m):
        return read("_" + m.group(1))
    body = re.sub(r"^//#include (\w+)\s*$", inc, body, flags=re.M)
    src = read("_prelude") + "\n" + body
    if body_only:
        return body
    if "$" in src or '"""' in src:
        sys.exit(scene + ": '$' or triple quotes would break the Kotlin raw string")
    return src


def theme_palettes():
    """(name, label, motif, [bg, surface, accent, secondary]) for every AppTheme, read from ShelfColors.kt."""
    kt = open(COLORS_KT, encoding="utf-8").read()
    themes = re.findall(r'^\s{4}([A-Z0-9_]+)\("([^"]+)"(?:, ThemeMotif\.([A-Z0-9_]+))?', kt, flags=re.M)
    pals = {}
    for name, args in re.findall(r"^\s+([A-Z0-9_]+) -> pal\(([^)]*)\)", kt, flags=re.M):
        v = [a.strip() for a in args.split(",")]
        pals[name] = [v[0], v[1], v[3], v[4]]
    for name, block in re.findall(r"^\s+([A-Z0-9_]+) -> ShelfColors\((.*?)\n\s+\)", kt, flags=re.M | re.S):
        get = lambda k: re.search(k + r" = Color\((0x[0-9A-Fa-f]+)\)", block).group(1)
        pals[name] = [get("background"), get("surface"), get("accent"), get("secondary")]
    out = []
    for name, label, motif in themes:
        if name not in pals:
            continue
        cols = [[int(h[-6:-4], 16) / 255, int(h[-4:-2], 16) / 255, int(h[-2:], 16) / 255] for h in pals[name]]
        out.append({"name": name, "label": label, "motif": motif or "MINIMAL", "colors": cols})
    return out


FUNC_RE = re.compile(r"^[A-Za-z_][\w]*\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*\{", re.M)


def _reachable(src):
    """Bodies of main() and every function it reaches, comments removed."""
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    bodies = {}
    for m in FUNC_RE.finditer(src):
        start = src.index("{", m.end() - 1)
        depth, i = 0, start
        while i < len(src):
            if src[i] == "{":
                depth += 1
            elif src[i] == "}":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        bodies[m.group(1)] = src[start:i + 1]
    seen, todo = set(), ["main"]
    while todo:
        f = todo.pop()
        if f in seen or f not in bodies:
            continue
        seen.add(f)
        for name in bodies:
            if name not in seen and re.search(r"\b" + name + r"\s*\(", bodies[f]):
                todo.append(name)
    return [_drop_dead_locals(bodies[f]) for f in seen]


_DECL = re.compile(r"\b(?:float|float2|float3|float4|half|half2|half3|half4|int|bool)\s+(\w+)\s*=\s*([^;]*);")


def _drop_dead_locals(body):
    """Removes local variables that are never read (the compiler drops them, and with them any
    uniform only they read)."""
    changed = True
    while changed:
        changed = False
        for d in _DECL.finditer(body):
            rest = body[:d.start()] + body[d.end():]
            if not re.search(r"\b" + d.group(1) + r"\b", rest):
                body = rest
                changed = True
                break
    return body


def used_uniforms(src):
    """Plain (float) uniforms the scene really reads. The AGSL compiler drops unused ones, and on
    Android 13 setting a uniform the shader doesn't have aborts the app like a missing child does."""
    reach = _reachable(src)
    names = re.findall(r"uniform\s+(?:float|float2|float3|float4|half|half2|half3|half4|int)\s+(\w+)\s*;", src)
    return sorted(n for n in names if any(re.search(r"\b" + n + r"\b", b) for b in reach))


def used_children(src):
    """Child shaders (uniform shader X) the scene really samples, following calls from main().

    The AGSL compiler drops an unused child, and on Android 13 setting a child the shader doesn't have
    aborts the app (not a catchable exception), so the app sets only the ones listed here.
    """
    reach = _reachable(src)
    kids = re.findall(r"uniform\s+shader\s+(\w+)\s*;", src)
    return sorted(k for k in kids if any(k + ".eval(" in b for b in reach))


def write_kotlin(sources):
    lines = [
        "package com.shelf.launcher.ui",
        "",
        "import com.shelf.launcher.ui.theme.ThemeMotif",
        "",
        "// GENERATED by tools/themes/gen.py from tools/themes/scenes/*.agsl - edit those, then re-run.",
        "",
        "/** AGSL source of each theme's animated scene (Android 13+, see ThemeScene). */",
        "internal object ThemeShaders {",
        "    fun source(motif: ThemeMotif): String = when (motif) {",
    ]
    lines += [f"        ThemeMotif.{m} -> \"\"" for m in NO_SCENE]
    for motif, scene in MOTIFS + SHOWCASE:
        lines.append(f"        {_case(motif)} -> PRELUDE + {scene.upper()}")
    lines.append("    }")
    lines.append("")
    lines.append("    /** Child shaders each scene really samples (only these may be set; see gen.py used_children). */")
    lines.append("    fun children(motif: ThemeMotif): Set<String> = when (motif) {")
    lines += [f"        ThemeMotif.{m} -> emptySet()" for m in NO_SCENE]
    for motif, scene in MOTIFS + SHOWCASE:
        kids = used_children(sources[scene])
        lines.append(f"        {_case(motif)} -> " + ("setOf(" + ", ".join('"' + k + '"' for k in kids) + ")" if kids else "emptySet()"))
    lines.append("    }")
    lines.append("")
    lines.append("    /**")
    lines.append("     * Plain uniforms each scene really reads (see gen.py used_uniforms). The compiler drops the others and")
    lines.append("     * on Android 13 setting one of those aborts the whole app, so only these may be set.")
    lines.append("     */")
    lines.append("    fun uniforms(motif: ThemeMotif): Set<String> = when (motif) {")
    lines += [f"        ThemeMotif.{m} -> emptySet()" for m in NO_SCENE]
    for motif, scene in MOTIFS + SHOWCASE:
        us = used_uniforms(sources[scene])
        lines.append(f"        {_case(motif)} -> " + ("setOf(" + ", ".join('"' + u + '"' for u in us) + ")" if us else "emptySet()"))
    lines.append("    }")
    lines.append("")
    lines.append('    private const val PRELUDE = """')
    lines.append(read("_prelude").rstrip())
    lines.append('"""')
    lines.append("")
    for scene in dict.fromkeys(sc for _, sc in MOTIFS + SHOWCASE):
        lines.append(f'    private const val {scene.upper()} = """')
        lines.append(build(scene, body_only=True).rstrip())
        lines.append('"""')
        lines.append("")
    lines.append("}")
    with open(KT_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def reactive_motifs():
    """ThemeMotif entries declared music-reactive (``NAME(true, …)``) in ShelfColors.kt."""
    kt = open(COLORS_KT, encoding="utf-8").read()
    return re.findall(r"^\s{4}([A-Z0-9_]+)\(true\b", kt, flags=re.M)


def write_html(sources):
    tpl = open(os.path.join(HERE, "preview_template.html"), encoding="utf-8").read()
    data = {"scenes": {m: sources[s] for m, s in MOTIFS + SHOWCASE if s in sources}, "themes": [t for t in theme_palettes() if any(m == t["motif"] and s in sources for m, s in MOTIFS + SHOWCASE)],
            "reactive": reactive_motifs()}
    with open(HTML_OUT, "w", encoding="utf-8") as f:
        f.write(tpl.replace("/*DATA*/null", json.dumps(data)))


if __name__ == "__main__":
    srcs = {scene: build(scene) for _, scene in MOTIFS + SHOWCASE if os.path.exists(os.path.join(SCENES, scene + ".agsl"))}
    missing = [s for _, s in MOTIFS + SHOWCASE if s not in srcs]
    if missing and "--partial" not in sys.argv:
        sys.exit("missing scenes: " + ", ".join(missing))
    if not missing:
        write_kotlin(srcs)
    write_html(srcs)
    print("scenes:", len(srcs), "->", HTML_OUT, "" if missing else "and " + KT_OUT)

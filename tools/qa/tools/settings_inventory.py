#!/usr/bin/env python3
"""
VoltCap settings inventory (static, stdlib only).

Usage:
    python3 qa/tools/settings_inventory.py [SRC_DIR] [-o OUT.csv] [--stale OUT_stale.csv]

SRC_DIR is the Kotlin source root (the folder holding LauncherViewModel.kt, data/, ui/ ...).
Default: ./app/src/main/java/com/shelf/launcher if it exists, else the current folder.

What it extracts
  * Features.kt       every `val x = BoolPref/EnumPref/StringPref/IntPref/SetPref("key", default ...)`
                      (prefs file "voltcap_features")
  * AppearanceStore.kt every AppearanceSettings field: load key (loadSettings), save key (save), default
                      (prefs file "appearance")
  * other stores       every prefs.getX("key", default) in a file that opens a SharedPreferences file
                      (heuristic accessor = enclosing fun/val)

For each setting it counts (comments stripped, ThemeShaders.kt skipped):
  behaviour_reads   reads outside settings UI / R1 / backup+preset infrastructure
  ui_reads          reads inside settings UI (show the value, conditions)
  ui_writes         writes or controls bound to the pref inside settings UI
  r1_reads/r1_writes same, inside the R1 quick-settings files
  other_writes      writes elsewhere (migrations, hotkeys, view model)
  searchable        a label string near a UI control matches an entry of ui/SettingsIndex.kt
  backup_scope      GLOBAL / DEVICE / AUTH / INTERNAL as data/SettingsScopes.kt would classify file+key
  backed_up         yes (GLOBAL), device-only (DEVICE: only with "device setup"), no (AUTH/INTERNAL)
and flags: unread_but_ui, no_ui, load_save_key_mismatch, not_saved, not_loaded, duplicate_key, stale_read.

The counts are static heuristics (name matching, enclosing-function classification). Treat
"unread" rows as candidates to confirm by reading the code.
"""
import argparse
import collections
import csv
import os
import re
import sys

SKIP_FILES = {"ThemeShaders.kt"}

# Settings UI: whole files ...
UI_FILE_RE = re.compile(r"(Settings|SettingsExtras|SettingsIndex|PresetsCard|FirstRunDialog|"
                        r"BackupProfilesCard|SettingsShareCard)[\w]*\.kt$")
R1_FILE_RE = re.compile(r"(QuickSettings|R1TileRules|R1Extras|R1\w*)\.kt$")
# ... and composables in other files whose name says they are a settings page.
UI_FUNC_RE = re.compile(r"(Settings|SettingsCard|SettingsPage|SettingsSection|SettingsRows?|Options|OptionsCard|Prefs)$")
# Objects / classes inside a settings file that are behaviour helpers (e.g. a synth or a store in the same file).
UI_HOLDER_RE = re.compile(r"Settings|Index|Dialog|Ui$|State$")
# Files that touch every pref generically (backup, presets, previews, the index itself): not behaviour.
INFRA_FILE_RE = re.compile(r"(LayoutPresets|LayoutPresetStore|PresetPreview|SettingsIndex|SettingsScopes|Backup|"
                           r"SettingsTransfer|ResetAndCompat)\.kt$")

PREF_TYPES = ("BoolPref", "EnumPref", "StringPref", "IntPref", "SetPref")
FUN_DECL_RE = re.compile(r"^(\s*)(?:@[\w.]+(?:\([^)]*\))?\s+)*(?:(?:internal|private|public|protected|override|suspend|inline|"
                         r"operator|tailrec|open|actual)\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(")


# ---------------------------------------------------------------- lexing

def strip_comments(text):
    """Blanks // and /* */ comments (keeps strings and newlines, so line numbers hold)."""
    out = []
    i, n = 0, len(text)
    depth_block = 0
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if depth_block:
            if c == "*" and nxt == "/":
                depth_block -= 1
                out.append("  ")
                i += 2
                continue
            if c == "/" and nxt == "*":
                depth_block += 1
                out.append("  ")
                i += 2
                continue
            out.append("\n" if c == "\n" else " ")
            i += 1
            continue
        if c == "/" and nxt == "/":
            while i < n and text[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if c == "/" and nxt == "*":
            depth_block = 1
            out.append("  ")
            i += 2
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append(text[i:j])
            i = j
            continue
        if c == '"':
            j = i + 1
            tdepth = 0
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text.startswith("${", j):
                    tdepth += 1
                    j += 2
                    continue
                if tdepth and text[j] == "}":
                    tdepth -= 1
                elif not tdepth and text[j] in '"\n':
                    break
                j += 1
            out.append(text[i:j + 1])
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'" and text[j] != "\n":
                j += 2 if text[j] == "\\" else 1
            out.append(text[i:j + 1])
            i = j + 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


STR_RE = re.compile(r'"((?:[^"\\\n]|\\.)*)"')


def split_args(s):
    """Top-level comma split of an argument list (s starts right after '(')."""
    args, depth, cur, i = [], 0, [], 0
    in_str = False
    while i < len(s):
        c = s[i]
        if in_str:
            cur.append(c)
            if c == "\\":
                cur.append(s[i + 1] if i + 1 < len(s) else "")
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
            cur.append(c)
        elif c in "([{":
            depth += 1
            cur.append(c)
        elif c in ")]}":
            if depth == 0:
                args.append("".join(cur).strip())
                return args, i
            depth -= 1
            cur.append(c)
        elif c == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    args.append("".join(cur).strip())
    return args, len(s)


def balanced_block(text, start):
    """Returns the index just after the bracket matching text[start] ('(' or '{')."""
    open_c = text[start]
    close_c = {"(": ")", "{": "}"}[open_c]
    depth, i, n = 0, start, len(text)
    in_str = False
    while i < n:
        c = text[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
        elif c == '"':
            in_str = True
        elif c == open_c:
            depth += 1
        elif c == close_c:
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return n


class Source:
    def __init__(self, root, path):
        self.root = root
        self.path = path
        self.rel = os.path.relpath(path, root).replace(os.sep, "/")
        self.base = os.path.basename(path)
        with open(path, encoding="utf-8", errors="replace") as f:
            self.raw = f.read()
        self.code = strip_comments(self.raw)
        self.lines = self.code.split("\n")
        self.raw_lines = self.raw.split("\n")
        self.line_starts = [0]
        for ln in self.lines:
            self.line_starts.append(self.line_starts[-1] + len(ln) + 1)
        self._funcs = None
        self.is_ui = bool(UI_FILE_RE.search(self.base)) and not R1_FILE_RE.search(self.base)
        self.is_r1 = bool(R1_FILE_RE.search(self.base))
        self.is_infra = bool(INFRA_FILE_RE.search(self.base))

    def line_of(self, offset):
        lo, hi = 0, len(self.line_starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if self.line_starts[mid] <= offset:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    def funcs(self):
        if self._funcs is None:
            self._funcs = []
            for i, ln in enumerate(self.lines, 1):
                m = FUN_DECL_RE.match(ln)
                if m and len(m.group(1).expandtabs()) <= 8:
                    self._funcs.append((i, m.group(2)))
        return self._funcs

    def enclosing_fun(self, line):
        name = ""
        for ln, nm in self.funcs():
            if ln > line:
                break
            name = nm
        return name

    def holders(self):
        if getattr(self, "_holders", None) is None:
            self._holders = []
            for m in re.finditer(r"^(\s{0,4})(?:(?:internal|private|public|data|enum|sealed|abstract|open|inner)\s+)*(?:object|class)\s+(\w+)[^{\n]*\{", self.code, re.M):
                end = balanced_block(self.code, m.end() - 1)
                self._holders.append((self.line_of(m.start()), self.line_of(end), m.group(2)))
        return self._holders

    def holder_of(self, line):
        best = None
        for a, b, nm in self.holders():
            if a <= line <= b and (best is None or a >= best[0]):
                best = (a, b, nm)
        return best[2] if best else ""

    def context(self, line):
        """'ui', 'r1', 'infra' or 'code' for a reference on [line]."""
        if self.is_r1:
            return "r1"
        if self.is_infra:
            return "infra"
        if self.is_ui:
            h = self.holder_of(line)
            if h and not UI_HOLDER_RE.search(h):
                return "code"
            return "ui"
        fn = self.enclosing_fun(line)
        if fn and UI_FUNC_RE.search(fn):
            return "ui"
        return "code"

    def strings_near(self, line, before=4, after=1):
        lo = max(1, line - before)
        hi = min(len(self.lines), line + after)
        out = []
        for i in range(lo, hi + 1):
            out += STR_RE.findall(self.lines[i - 1])
        return out


def load_sources(root):
    out = []
    for d, _, files in os.walk(root):
        for f in sorted(files):
            if f.endswith(".kt") and f not in SKIP_FILES:
                out.append(Source(root, os.path.join(d, f)))
    return out


# ---------------------------------------------------------------- settings model

class Setting:
    FIELDS = ["store", "prefs_file", "key", "name", "type", "default", "decl", "save_key", "behaviour_reads",
              "ui_reads", "ui_writes", "r1_reads", "r1_writes", "other_writes", "infra_refs", "searchable",
              "index_label", "backup_scope", "backed_up", "flags", "ui_at", "r1_at", "reader_at", "writer_at",
              "stale_at"]

    def __init__(self, store, prefs_file, key, name, typ, default, decl):
        self.store, self.prefs_file, self.key, self.name = store, prefs_file, key, name
        self.type, self.default, self.decl = typ, default, decl
        self.save_key = ""
        self.counts = collections.Counter()
        self.at = collections.defaultdict(list)
        self.labels = set()
        self.flags = []
        self.index_label = ""
        self.backup_scope = ""
        self.stale = []
        self.read_sites = []

    def hit(self, kind, src, line):
        self.counts[kind] += 1
        if kind == "code_read":
            self.read_sites.append((src, line))
        loc = "%s:%d" % (src.rel, line)
        if len(self.at[kind]) < 6 and loc not in self.at[kind]:
            self.at[kind].append(loc)

    def row(self):
        c = self.counts
        bu = {"GLOBAL": "yes", "DEVICE": "device-only", "AUTH": "no", "INTERNAL": "no"}.get(self.backup_scope, "?")
        return {
            "store": self.store, "prefs_file": self.prefs_file, "key": self.key, "name": self.name, "type": self.type,
            "default": self.default, "decl": self.decl, "save_key": self.save_key,
            "behaviour_reads": c["code_read"], "ui_reads": c["ui_read"], "ui_writes": c["ui_write"],
            "r1_reads": c["r1_read"], "r1_writes": c["r1_write"], "other_writes": c["code_write"],
            "infra_refs": c["infra_read"] + c["infra_write"],
            "searchable": "yes" if self.index_label else ("no" if (c["ui_write"] or c["r1_write"]) else ""),
            "index_label": self.index_label, "backup_scope": self.backup_scope, "backed_up": bu,
            "flags": ";".join(self.flags),
            "ui_at": " ".join(self.at["ui_write"] or self.at["ui_read"]),
            "r1_at": " ".join(self.at["r1_write"] or self.at["r1_read"]),
            "reader_at": " ".join(self.at["code_read"]),
            "writer_at": " ".join(self.at["code_write"]),
            "stale_at": " ".join(self.stale[:6]),
        }


# ---------------------------------------------------------------- Features

FEAT_DECL_RE = re.compile(r"^\s*(?:@[\w.]+\s+)*(?:(?:private|internal|public)\s+)?val\s+(\w+)\s*(?::[^=]+)?=\s*(" +
                          "|".join(PREF_TYPES) + r")\(")


def extract_features(src):
    out = []
    for i, ln in enumerate(src.lines, 1):
        m = FEAT_DECL_RE.match(ln)
        if not m:
            continue
        name, typ = m.group(1), m.group(2)
        start = src.line_starts[i - 1] + m.end()
        args, _ = split_args(src.code[start:start + 4000])
        key = STR_RE.match(args[0].strip()) if args else None
        if not key:
            continue
        default = args[1] if len(args) > 1 else ("emptySet()" if typ == "SetPref" else "")
        if typ == "IntPref" and len(args) > 2:
            default += " [" + args[2] + "]"
        t = {"BoolPref": "Boolean", "IntPref": "Int", "StringPref": "String", "SetPref": "Set<String>"}.get(typ)
        if typ == "EnumPref":
            t = "enum " + (args[2].replace(".values()", "").replace("enumValues", "").strip() if len(args) > 2 else "?")
        out.append(Setting("Features", "voltcap_features", key.group(1), name, t, default, "%s:%d" % (src.rel, i)))
    return out


# ---------------------------------------------------------------- AppearanceSettings

def find_block(src, header_re):
    m = re.search(header_re, src.code)
    if not m:
        return None
    open_idx = m.end() - 1
    return open_idx, balanced_block(src.code, open_idx)


def extract_appearance(src):
    code = src.code
    blk = find_block(src, r"data class AppearanceSettings\s*\(")
    if not blk:
        return [], {}
    a, b = blk
    fields = collections.OrderedDict()
    args, _ = split_args(code[a + 1:b])
    pos = a + 1
    for arg in args:
        m = re.match(r"(?:@[\w.]+\s+)*(?:val|var)\s+(\w+)\s*:\s*([^=]+?)\s*=\s*(.+)$", arg.strip(), re.S)
        off = code.find(arg, pos) if arg else pos
        if off >= 0:
            pos = off + len(arg)
        if m:
            fields[m.group(1)] = (m.group(2).strip(), re.sub(r"\s+", " ", m.group(3).strip())[:80], src.line_of(max(off, 0)))
    # load keys
    load_keys, save_keys = {}, {}
    lb = find_block(src, r"fun loadSettings\(\)\s*:\s*AppearanceSettings\s*=\s*AppearanceSettings\s*\(")
    if lb:
        largs, _ = split_args(code[lb[0] + 1:lb[1]])
        for arg in largs:
            m = re.match(r"(\w+)\s*=\s*(.+)$", arg.strip(), re.S)
            if not m:
                continue
            km = re.search(r"(?:get(?:Boolean|Int|String|Long|Float|StringSet)|enumPref|contains|forLayout)\(\s*\"([^\"]+)\"", m.group(2))
            load_keys[m.group(1)] = km.group(1) if km else ""
    sb = find_block(src, r"fun save\(s: AppearanceSettings\)\s*\{")
    save_body_range = None
    if sb:
        body = code[sb[0]:sb[1]]
        save_body_range = (src.line_of(sb[0]), src.line_of(sb[1]))
        for m in re.finditer(r"put(?:Boolean|Int|String|Long|Float|StringSet)\(\s*\"([^\"]+)\"\s*,\s*([^\n]*)", body):
            fm = re.search(r"\bs\.(\w+)", m.group(2))
            if fm and fm.group(1) not in save_keys:
                save_keys[fm.group(1)] = m.group(1)
        for m in re.finditer(r"if\s*\(s\.(\w+)[^)]*\)\s*put\w+\(\s*\"([^\"]+)\"", body):
            save_keys.setdefault(m.group(1), m.group(2))
    out = []
    for name, (typ, default, line) in fields.items():
        s = Setting("AppearanceSettings", "appearance", load_keys.get(name) or save_keys.get(name, ""), name, typ, default,
                    "%s:%d" % (src.rel, line))
        s.save_key = save_keys.get(name, "")
        if name not in load_keys:
            s.flags.append("not_loaded")
        if name not in save_keys:
            s.flags.append("not_saved")
        if load_keys.get(name) and save_keys.get(name) and load_keys[name] != save_keys[name]:
            s.flags.append("load_save_key_mismatch(%s/%s)" % (load_keys[name], save_keys[name]))
        out.append(s)
    excl = [(blk and src.line_of(a), blk and src.line_of(b))]
    if lb:
        excl.append((src.line_of(lb[0]), src.line_of(lb[1])))
    if save_body_range:
        excl.append(save_body_range)
    return out, excl


# ---------------------------------------------------------------- other stores

PREFS_OPEN_RE = re.compile(r"getSharedPreferences\(\s*(\"[^\"]+\"|[\w.]+)\s*,")
CONST_RE = re.compile(r"(?:const\s+)?val\s+(\w+)\s*(?::\s*String)?\s*=\s*\"([^\"]+)\"")
GET_RE = re.compile(r"\.get(Boolean|Int|String|Long|Float|StringSet)\(\s*(\"[^\"]+\"|[A-Z_][A-Z0-9_]*)\s*,\s*")


def prefs_files_of(src, consts):
    names = []
    for m in PREFS_OPEN_RE.finditer(src.code):
        tok = m.group(1)
        if tok.startswith('"'):
            names.append((m.start(), tok.strip('"')))
        elif tok.split(".")[-1] in consts:
            names.append((m.start(), consts[tok.split(".")[-1]]))
    return names


def extract_other_stores(sources, skip):
    out = []
    for src in sources:
        if src.base in skip:
            continue
        consts = dict(CONST_RE.findall(src.code))
        files = prefs_files_of(src, consts)
        if not files:
            continue
        uniq = sorted({f for _, f in files})
        seen = set()
        for m in GET_RE.finditer(src.code):
            tok = m.group(2)
            key = tok.strip('"') if tok.startswith('"') else consts.get(tok)
            if not key:
                continue
            args, _ = split_args(src.code[m.end():m.end() + 300])
            default = (args[0] if args else "")[:60]
            line = src.line_of(m.start())
            pf = uniq[0] if len(uniq) == 1 else resolve_receiver(src, m.start(), files, consts)
            if (pf, key) in seen:
                continue
            seen.add((pf, key))
            acc = enclosing_accessor(src, line)
            out.append(Setting(os.path.splitext(src.base)[0], pf, key, acc, m.group(1), default, "%s:%d" % (src.rel, line)))
    return out


def resolve_receiver(src, pos, files, consts):
    """Which prefs file a `recv.getX(` call reads, for files that open more than one."""
    def file_in(text):
        m = PREFS_OPEN_RE.search(text)
        if m:
            tok = m.group(1)
            return tok.strip('"') if tok.startswith('"') else consts.get(tok.split(".")[-1])
        for h, f in helpers.items():
            if re.search(r"\b" + re.escape(h) + r"\s*\(", text):
                return f
        return None
    helpers = {}
    for hm in re.finditer(r"fun\s+(\w+)\s*\([^)]*\)[^=\n]*=\s*[^\n]*getSharedPreferences\(\s*(\"[^\"]+\"|[\w.]+)", src.code):
        tok = hm.group(2)
        f = tok.strip('"') if tok.startswith('"') else consts.get(tok.split(".")[-1])
        if f:
            helpers[hm.group(1)] = f
    for hm in re.finditer(r"(?:val|var)\s+(\w+)\s*(?::[^=]+)?(?:=|by\s+lazy\s*\{)\s*[^\n]*getSharedPreferences\(\s*(\"[^\"]+\"|[\w.]+)", src.code):
        tok = hm.group(2)
        f = tok.strip('"') if tok.startswith('"') else consts.get(tok.split(".")[-1])
        if f:
            helpers.setdefault(hm.group(1), f)
    before = src.code[max(0, pos - 200):pos]
    rm = re.search(r"(\w+)\s*(\([^()]*\))?\s*$", before)
    if rm:
        tok = rm.group(1)
        if tok in helpers:
            return helpers[tok]
        # a local: val p = prefs(c) / val p = context.getSharedPreferences(...)
        back = src.code[max(0, pos - 3000):pos]
        for am in reversed(list(re.finditer(r"(?:val|var)\s+" + re.escape(tok) + r"\b[^\n]*", back))):
            f = file_in(am.group(0))
            if f:
                return f
    counts = collections.Counter(f for _, f in files)
    return counts.most_common(1)[0][0]


def enclosing_accessor(src, line):
    for i in range(line, 0, -1):
        ln = src.lines[i - 1]
        m = re.match(r"\s*(?:(?:private|internal|public|override)\s+)*(?:fun\s+(?:[\w.]+\.)?(\w+)|va[lr]\s+(\w+))", ln)
        if m:
            return m.group(1) or m.group(2)
    return ""


# ---------------------------------------------------------------- SettingsScopes

class Scopes:
    def __init__(self, src):
        self.sets = {}
        code = src.code if src else ""
        for nm in ("GLOBAL_FILES", "DEVICE_FILES", "AUTH_FILES", "INTERNAL_FILES"):
            m = re.search(nm + r"\s*=\s*setOf\(", code)
            if m:
                end = balanced_block(code, m.end() - 1)
                self.sets[nm] = set(STR_RE.findall(code[m.end():end]))
            else:
                self.sets[nm] = set()
        self.auth_re = self._pattern(code, "AUTH_PATTERN") or re.compile(r"(_encrypted$|credential|secret|token|password|oauth|api_?key|_auth$)", re.I)
        self.internal_re = self._pattern(code, "INTERNAL_PATTERN") or re.compile(r"^(WebView|androidx\.|com\.google\.|__)", re.I)
        # DEVICE_KEYS: per file, startsWith / == literals inside its lambda.
        self.device_keys = {}
        m = re.search(r"DEVICE_KEYS[^=]*=\s*mapOf\(", code)
        if m:
            end = balanced_block(code, m.end() - 1)
            body = code[m.end():end]
            for fm in re.finditer(r"\"(\w+)\"\s+to\s+\{", body):
                lam_end = balanced_block(body, fm.end() - 1)
                lam = body[fm.end():lam_end]
                self.device_keys[fm.group(1)] = (re.findall(r"startsWith\(\"([^\"]+)\"\)", lam),
                                                 re.findall(r"==\s*\"([^\"]+)\"", lam),
                                                 re.findall(r"==\s*\w+\.(KEY_\w+)", lam))

    @staticmethod
    def _pattern(code, name):
        m = re.search(name + r"\s*=\s*Regex\(\s*\n?\s*\"((?:[^\"\\]|\\.)*)\"", code)
        if not m:
            return None
        pat = m.group(1).encode().decode("unicode_escape")
        try:
            return re.compile(pat, re.I)
        except re.error:
            return None

    def scope_of_file(self, name):
        if name in self.sets["INTERNAL_FILES"] or self.internal_re.search(name):
            return "INTERNAL"
        if name in self.sets["AUTH_FILES"] or self.auth_re.search(name):
            return "AUTH"
        if name in self.sets["DEVICE_FILES"]:
            return "DEVICE"
        return "GLOBAL"

    def scope_of(self, name, key, const_values=None):
        s = self.scope_of_file(name)
        if s == "GLOBAL" and name in self.device_keys:
            starts, eqs, consts = self.device_keys[name]
            cv = {const_values.get(c) for c in consts} if const_values else set()
            if any(key.startswith(p) for p in starts) or key in eqs or key in cv:
                return "DEVICE"
        return s


# ---------------------------------------------------------------- SettingsIndex

def norm_label(s):
    s = re.sub(r"\$\{[^}]*\}|\$\w+", "", s)
    s = re.sub(r"\([^)]*\)", "", s)
    s = re.sub(r"[^\w ]+", " ", s.lower())
    return " ".join(s.split())


def load_index(src):
    labels = set()
    if src:
        for m in re.finditer(r"\bE\(\s*\"((?:[^\"\\]|\\.)*)\"", src.code):
            n = norm_label(m.group(1))
            if n:
                labels.add(n)
    return labels


# ---------------------------------------------------------------- reference scan

WRITE_TAIL = re.compile(r"^\s*(?:\.state)?\.value\s*(?:=(?!=)|\+=|-=)|^\s*\.(?:toggle|setByName)\(|^\s*\.state\.value\s*=(?!=)")
READ_TAIL = re.compile(r"^\s*\.(?:value|state|asFlow|default)\b")
INFRA_TAIL = re.compile(r"^\s*\.key\b")


def scan_features(sources, feats, features_src):
    by_name = {s.name: s for s in feats}
    decl_lines = {int(s.decl.rsplit(":", 1)[1]) for s in feats}
    init_ranges = []
    if features_src:
        for hdr in (r"fun init\(context: Context\)\s*\{", r"fun reload\(context: Context\)\s*\{",
                    r"private fun migrate\w*\([^)]*\)\s*\{", r"fun snapshot\(", r"fun restore\("):
            for m in re.finditer(hdr, features_src.code):
                br = features_src.code.find("{", m.end() - 1)
                init_ranges.append((features_src.line_of(m.start()), features_src.line_of(balanced_block(features_src.code, br))))
    names_alt = "|".join(sorted(by_name, key=len, reverse=True))
    for src in sources:
        aliases = set(re.findall(r"\bval\s+(\w+)\s*=\s*(?:com\.shelf\.launcher\.data\.)?Features\s*$", src.code, re.M))
        quals = ["(?:com\\.shelf\\.launcher\\.data\\.)?Features"] + [re.escape(a) for a in aliases]
        if src is features_src:
            pat = re.compile(r"(?<![\w.])(?:(?:Features)\.)?(" + names_alt + r")\b(?!\s*[(:=]\s*(?:" + "|".join(PREF_TYPES) + r"))")
        else:
            pat = re.compile(r"(?<![\w.])(?:" + "|".join(quals) + r")\.(" + names_alt + r")\b")
        for m in pat.finditer(src.code):
            s = by_name.get(m.group(1))
            if not s:
                continue
            line = src.line_of(m.start())
            if src is features_src:
                if line in decl_lines and src.lines[line - 1].lstrip().startswith(("val ", "private val", "internal val")) \
                        and re.search(r"val\s+" + re.escape(s.name) + r"\b", src.lines[line - 1]):
                    continue
                tail = src.code[m.end():m.end() + 40]
                if not (READ_TAIL.match(tail) or WRITE_TAIL.match(tail)):
                    continue  # bare identifiers in Features.kt are mostly other things with the same name
            tail = src.code[m.end():m.end() + 40]
            is_write = bool(WRITE_TAIL.match(tail))
            prev = src.code[max(0, m.start() - 12):m.start()]
            delegate = bool(re.search(r"\bby\s+$", prev))
            bare = not READ_TAIL.match(tail) and not is_write and not INFRA_TAIL.match(tail)
            ctx = src.context(line)
            if src is features_src:
                ctx = "infra" if any(a <= line <= b for a, b in init_ranges) else "code"
            if INFRA_TAIL.match(tail):
                ctx = "infra"
            if ctx in ("ui", "r1"):
                kind = ctx + ("_write" if (is_write or bare or delegate) else "_read")
                if kind.endswith("_write"):
                    s.labels.update(src.strings_near(line))
            elif ctx == "infra":
                kind = "infra_write" if is_write else "infra_read"
            else:
                kind = "code_write" if is_write else "code_read"
            s.hit(kind, src, line)


def scan_appearance(sources, app, app_src, excl):
    if not app:
        return
    names = {s.name: s for s in app}
    names_alt = "|".join(sorted(names, key=len, reverse=True))
    read_pat = re.compile(r"(?<!Features)(?<!\bF)\.(" + names_alt + r")\b(?!\s*=(?!=))")
    copy_pat = re.compile(r"\.copy\(")
    for src in sources:
        skip_lines = set()
        if src is app_src:
            for a, b in excl:
                if a and b:
                    skip_lines.update(range(a, b + 1))
        for m in read_pat.finditer(src.code):
            if re.search(r"(current|LocalDensity|resources\.displayMetrics|displayMetrics|config|metrics)$", src.code[max(0, m.start() - 24):m.start()]):
                continue
            line = src.line_of(m.start())
            if line in skip_lines:
                continue
            ctx = src.context(line)
            s = names[m.group(1)]
            kind = {"ui": "ui_read", "r1": "r1_read", "infra": "infra_read"}.get(ctx, "code_read")
            s.hit(kind, src, line)
        # Extension properties / functions on AppearanceSettings read the fields unqualified.
        for em in re.finditer(r"^(?:(?:internal|private|public|inline)\s+)*(?:val|fun)\s+(?:<[^>]*>\s*)?AppearanceSettings\.\w+", src.code, re.M):
            start_line = src.line_of(em.start())
            end_line = start_line
            while end_line < len(src.lines) and (end_line == start_line or not re.match(r"\S", src.lines[end_line]) or src.lines[end_line].startswith(("}", ")"))):
                end_line += 1
            body = "\n".join(src.lines[start_line - 1:end_line])
            for bm in re.finditer(r"(?<![\w.])(" + names_alt + r")\b(?!\s*=(?!=))", body):
                line = start_line + body.count("\n", 0, bm.start())
                if line in skip_lines:
                    continue
                ctx = src.context(line)
                names[bm.group(1)].hit({"ui": "ui_read", "r1": "r1_read", "infra": "infra_read"}.get(ctx, "code_read"), src, line)
        for m in copy_pat.finditer(src.code):
            end = balanced_block(src.code, m.end() - 1)
            args, _ = split_args(src.code[m.end():end])
            off = m.end()
            for arg in args:
                am = re.match(r"(\w+)\s*=(?!=)", arg)
                if am and am.group(1) in names:
                    p = src.code.find(arg, off)
                    line = src.line_of(p if p >= 0 else m.start())
                    if line in skip_lines:
                        continue
                    ctx = src.context(line)
                    kind = {"ui": "ui_write", "r1": "r1_write", "infra": "infra_write"}.get(ctx, "code_write")
                    s = names[am.group(1)]
                    s.hit(kind, src, line)
                    if kind in ("ui_write", "r1_write"):
                        s.labels.update(src.strings_near(line, before=6, after=2))


def scan_other(sources, others):
    """Accessor name references outside the defining file (very rough)."""
    by_acc = collections.defaultdict(list)
    for s in others:
        if s.name and len(s.name) > 3 and s.name not in ("load", "get", "read", "all", "prefs", "value", "init"):
            by_acc[s.name].append(s)
    if not by_acc:
        return
    pat = re.compile(r"(?<![\w])(" + "|".join(sorted(by_acc, key=len, reverse=True)) + r")\b")
    for src in sources:
        for m in pat.finditer(src.code):
            for s in by_acc[m.group(1)]:
                if s.decl.startswith(src.rel + ":"):
                    continue
                line = src.line_of(m.start())
                ctx = src.context(line)
                tail = src.code[m.end():m.end() + 30]
                is_write = bool(re.match(r"\s*=(?!=)", tail)) or m.group(1).startswith("set")
                kind = {"ui": "ui", "r1": "r1", "infra": "infra"}.get(ctx, "code") + ("_write" if is_write or ctx in ("ui", "r1") and "(" in tail[:2] else "_read")
                s.hit(kind, src, line)
                if kind in ("ui_write", "r1_write"):
                    s.labels.update(src.strings_near(line))


STALE_PATTERNS = [
    # class / object level vals copying a live pref
    (re.compile(r"^\s{0,4}(?:(?:private|internal|protected)\s+)?val\s+\w+(?:\s*:\s*[\w<>?]+)?\s*=\s*(?:com\.shelf\.launcher\.data\.)?Features\.(\w+)\.value\b"), "class-level val copy"),
    (re.compile(r"by\s+lazy\s*\{[^}]*Features\.(\w+)\.value"), "lazy copy"),
    (re.compile(r"remember\s*\{\s*[^}]*?Features\.(\w+)\.value[^}]*\}"), "remember{} without key"),
    (re.compile(r"remember\s*\{\s*mutableStateOf\(\s*(?:com\.shelf\.launcher\.data\.)?Features\.(\w+)\.value"), "remember(mutableStateOf(copy))"),
]


def scan_stale(sources, feats):
    by_name = {s.name: s for s in feats}
    hits = []
    for src in sources:
        for i, ln in enumerate(src.lines, 1):
            for pat, why in STALE_PATTERNS:
                for m in pat.finditer(ln):
                    s = by_name.get(m.group(1))
                    if s:
                        loc = "%s:%d" % (src.rel, i)
                        s.stale.append(loc)
                        hits.append((s.key, s.name, loc, why, src.raw_lines[i - 1].strip()[:160]))
    return hits


# ---------------------------------------------------------------- main

def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("src", nargs="?", help="Kotlin source root")
    ap.add_argument("-o", "--out", default="settings_inventory.csv")
    ap.add_argument("--stale", default=None, help="also write stale-copy candidates to this CSV")
    ap.add_argument("--no-other", action="store_true", help="only Features + AppearanceSettings")
    a = ap.parse_args(argv)
    root = a.src or ("app/src/main/java/com/shelf/launcher" if os.path.isdir("app/src/main/java/com/shelf/launcher") else ".")
    if not os.path.isdir(root):
        sys.exit("no such folder: %s" % root)
    sources = load_sources(root)
    by_base = {}
    for s in sources:
        by_base.setdefault(s.base, s)
    fsrc, asrc = by_base.get("Features.kt"), by_base.get("AppearanceStore.kt")
    if not fsrc or not asrc:
        sys.exit("Features.kt / AppearanceStore.kt not found under %s" % root)
    scopes = Scopes(by_base.get("SettingsScopes.kt"))
    index = load_index(by_base.get("SettingsIndex.kt"))

    feats = extract_features(fsrc)
    app, excl = extract_appearance(asrc)
    others = [] if a.no_other else extract_other_stores(sources, {"Features.kt", "AppearanceStore.kt", "Backup.kt",
                                                                    "SettingsTransfer.kt", "ResetAndCompat.kt"})
    scan_features(sources, feats, fsrc)
    scan_appearance(sources, app, asrc, excl)
    scan_other(sources, others)
    stale = scan_stale(sources, feats)

    consts = {}
    for s in sources:
        if s.base == "PlaytimeStore.kt":
            consts.update(dict(re.findall(r"(KEY_\w+)\s*=\s*\"([^\"]+)\"", s.code)))
    allset = feats + app + others
    # Readers that sit only in functions nobody calls (declared once, never referenced).
    words = collections.Counter()
    for src in sources:
        words.update(re.findall(r"[A-Za-z_]\w*", src.code))
    for st in feats + app:
        if not st.read_sites:
            continue
        live = False
        dead_fns = set()
        for src, line in st.read_sites:
            fn = src.enclosing_fun(line)
            ln_txt = src.lines[line - 1] if line - 1 < len(src.lines) else ""
            decl = next((src.lines[l - 1] for l, n in src.funcs() if n == fn and l <= line), "")
            if not fn or words[fn] > 1 or "override" in decl:
                live = True
                break
            dead_fns.add("%s:%s" % (src.rel, fn))
        if not live:
            st.flags.append("readers_uncalled(%s)" % ",".join(sorted(dead_fns)))
    # duplicates (same prefs file + key declared twice)
    seen = collections.defaultdict(list)
    for s in allset:
        if s.key and (s.store in ("Features", "AppearanceSettings") or s.prefs_file in ("voltcap_features", "appearance")):
            seen[(s.prefs_file, s.key)].append(s)
    for (pf, key), lst in seen.items():
        if len(lst) > 1:
            for s in lst:
                s.flags.append("duplicate_key(%s)" % ",".join(x.store + "." + x.name for x in lst if x is not s))
    for s in allset:
        s.backup_scope = scopes.scope_of(s.prefs_file, s.key, consts) if s.prefs_file else "?"
        for lab in s.labels:
            n = norm_label(lab)
            if n and n in index:
                s.index_label = lab
                break
        c = s.counts
        has_ui = c["ui_write"] or c["r1_write"]
        if s.store in ("Features", "AppearanceSettings"):
            if has_ui and not c["code_read"]:
                s.flags.append("unread_but_ui")
            if not has_ui and not c["code_write"]:
                s.flags.append("no_ui")
            if not has_ui and not c["code_read"] and not c["ui_read"] and not c["r1_read"]:
                s.flags.append("unused")
        if s.stale:
            s.flags.append("stale_copy")
        if s.backup_scope in ("AUTH", "INTERNAL") and s.store in ("Features", "AppearanceSettings"):
            s.flags.append("not_backed_up")

    os.makedirs(os.path.dirname(os.path.abspath(a.out)) or ".", exist_ok=True)
    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=Setting.FIELDS)
        w.writeheader()
        for s in allset:
            w.writerow(s.row())
    if a.stale:
        with open(a.stale, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["key", "name", "at", "why", "code"])
            w.writerows(stale)

    core = [s for s in allset if s.store in ("Features", "AppearanceSettings")]
    cnt = lambda pred: sum(1 for s in core if pred(s))
    print("settings: %d (Features %d, AppearanceSettings %d) + %d keys in %d other prefs files"
          % (len(core), len(feats), len(app), len(others), len({s.prefs_file for s in others})))
    print("  read by behaviour code: %d   unread: %d" % (cnt(lambda s: s.counts["code_read"]), cnt(lambda s: not s.counts["code_read"])))
    print("  UI-written but unread (candidates): %d" % cnt(lambda s: "unread_but_ui" in s.flags))
    print("  no UI (settings / R1): %d   fully unused: %d" % (cnt(lambda s: "no_ui" in s.flags), cnt(lambda s: "unused" in s.flags)))
    print("  with UI, not in SettingsIndex: %d" % cnt(lambda s: (s.counts["ui_write"] or s.counts["r1_write"]) and not s.index_label))
    print("  backup: GLOBAL %d, DEVICE-only %d, not backed up %d" % (
        cnt(lambda s: s.backup_scope == "GLOBAL"), cnt(lambda s: s.backup_scope == "DEVICE"),
        cnt(lambda s: s.backup_scope in ("AUTH", "INTERNAL"))))
    print("  other prefs files not backed up: %s" % ", ".join(sorted({s.prefs_file for s in others if s.backup_scope in ("AUTH", "INTERNAL")})))
    print("  duplicate keys: %d   load/save mismatches: %d   not saved: %d   stale copies: %d" % (
        sum(1 for s in allset if any(f.startswith("duplicate_key") for f in s.flags)),
        cnt(lambda s: any(f.startswith("load_save") for f in s.flags)), cnt(lambda s: "not_saved" in s.flags), len(stale)))
    print("wrote %s" % a.out)


if __name__ == "__main__":
    main()

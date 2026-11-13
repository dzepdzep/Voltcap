#!/usr/bin/env python3
"""Update 529: tools/popup_audit/hw_529.json from graded popclip runs. Usage:
  hw_from_runs.py OUT.json DEVICE:SCALE:RUN.json[:FP=LAYOUT_STYLE,...] ...
Each RUN.json is popclip.py --json output. A combination is recorded as device_scale_style -> {verdict, reason, run}; a FAIL that a
reviewer confirmed as a checker false positive (screenshot inspected) is passed as FP=<LAYOUT_STYLE> and recorded as
"PASS (checker false positive: ...)" with the original reason kept."""
import json, os, sys
out = {}
for arg in sys.argv[2:]:
    parts = arg.split(':')
    dev, scale, path = parts[0], parts[1], parts[2]
    fps = set(parts[3][3:].split(',')) if len(parts) > 3 and parts[3].startswith('FP=') else set()
    d = json.load(open(path, encoding='utf-8'))
    run = os.path.basename(path)[:-5]
    for r in d['results']:
        n = r['name']
        style = next(s for s in ('ROW_OPENS', 'ROW_REVEAL', 'SHARED', 'OWN', 'BANNER', 'CLASSIC') if n.endswith('_' + s))
        lay = n[:-len(style) - 1]
        v, why = r['status'], r['reason']
        if v == 'FAIL' and n in fps: v, why = 'PASS', 'checker false positive (screenshot inspected): ' + why
        out.setdefault(lay, {})['%s_x%s_%s' % (dev, scale, style)] = dict(verdict=v, reason=why, run=run)
json.dump(out, open(sys.argv[1], 'w', encoding='utf-8'), indent=1, ensure_ascii=False, sort_keys=True)
print(len(out), 'layouts')

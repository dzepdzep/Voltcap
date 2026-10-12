# Writes a stub R class (com.shelf.launcher.R) with every R.<type>.<name> the sources use, so the check
# doesn't depend on a stale R.jar. Missing resources themselves are only caught by the real Gradle build.
import re,sys,os,collections
src=sys.argv[1]; out=sys.argv[2]
used=collections.defaultdict(set)
for root,_,fs in os.walk(src):
    for f in fs:
        if f.endswith('.kt') and f!='ThemeShaders.kt':
            for t,n in re.findall(r'\bR\.(drawable|raw|font|string|mipmap|xml|color|style|id|layout|dimen|array)\.([A-Za-z0-9_]+)', open(os.path.join(root,f),encoding='utf-8').read()):
                used[t].add(n)
os.makedirs(os.path.dirname(out),exist_ok=True)
with open(out,'w') as o:
    o.write('package com.shelf.launcher;\npublic final class R {\n')
    i=0x7f000001
    for t in sorted(used):
        o.write(f'  public static final class {t} {{\n')
        for n in sorted(used[t]): o.write(f'    public static final int {n} = {i};\n'); i+=1
        o.write('  }\n')
    o.write('}\n')

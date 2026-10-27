import sys,re,collections
sys.path.insert(0,'/tmp/claude-0/audit')
import settings_inventory as si
root=sys.argv[1]
srcs=si.load_sources(root)
FUN=re.compile(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(')
unused=collections.defaultdict(list)  # fn -> [(idx, pname, file:line)]
decl_params={}
for s in srcs:
    c=s.code
    for m in FUN.finditer(c):
        start=m.end()-1
        end=si.balanced_block(c,start)
        args,_=si.split_args(c[start+1:end])
        # body
        rest=c[end:end+400]
        bm=re.match(r'\s*(?::\s*[^{=]+?)?\s*(\{|=)',rest,re.S)
        if not bm: continue
        if bm.group(1)=='{':
            bstart=end+bm.end()-1; bend=si.balanced_block(c,bstart)
        else:
            bstart=end+bm.end(); nl=c.find('\nfun ',bstart); bend=bstart+min(2000, (nl-bstart) if nl>0 else 2000)
            # expression body: up to next blank top-level line
            mm=re.search(r'\n(?=\S)|\n\s*\n',c[bstart:bstart+4000]); bend=bstart+(mm.start() if mm else 4000)
        body=c[bstart:bend]
        pre=c[max(0,m.start()-60):m.start()]
        if re.search(r'\b(override|abstract|expect|external|open)\b',pre): continue
        names=[]
        for i,a in enumerate(args):
            pm=re.match(r'(?:@[\w.]+(?:\([^)]*\))?\s+)*(?:vararg\s+|noinline\s+|crossinline\s+)?(\w+)\s*:',a.strip())
            if not pm: names.append(None); continue
            p=pm.group(1); names.append(p)
            if p in('_',): continue
            if not re.search(r'\b'+re.escape(p)+r'\b',body):
                unused[m.group(1)].append((i,p,'%s:%d'%(s.rel,s.line_of(m.start()))))
        decl_params[(m.group(1),s.rel)]=names
print('unused params:',sum(len(v) for v in unused.values()))
# setting reads passed into unused params
import csv
rows=list(csv.DictReader(open(sys.argv[2])))
names={r['name'] for r in rows if r['store'] in('Features','AppearanceSettings')}
pat=re.compile(r'\b('+'|'.join(sorted(unused,key=len,reverse=True))+r')\s*\(')
hits=[]
for s in srcs:
    c=s.code
    for m in pat.finditer(c):
        if re.search(r'fun\s+(?:<[^>]*>\s*)?$',c[max(0,m.start()-30):m.start()]): continue
        fn=m.group(1)
        end=si.balanced_block(c,m.end()-1)
        args,_=si.split_args(c[m.end():end])
        for idx,p,where in unused[fn]:
            expr=None
            for j,a in enumerate(args):
                nm=re.match(r'(\w+)\s*=(?!=)\s*(.*)',a,re.S)
                if nm and nm.group(1)==p: expr=nm.group(2)
            if expr is None and idx<len(args) and not re.match(r'\w+\s*=(?!=)',args[idx]): expr=args[idx]
            if not expr: continue
            for f in re.findall(r'\.(\w+)\b',expr):
                if f in names:
                    hits.append((f,fn,p,where,'%s:%d'%(s.rel,s.line_of(m.start()))))
for h in sorted(set(hits)): print('setting %s -> %s(%s) unused param [decl %s] call %s'%h)

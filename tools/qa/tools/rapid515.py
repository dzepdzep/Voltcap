#!/usr/bin/env python3
"""Update 515: what each rapid515.sh round did, from its trace.txt (the app's VoltcapQA key / window lines).
Per round: keys sent (by the sequence), key presses the app saw (activity `key BUTTON_*` + window `winkey … KeyDown`),
windows opened (`win sysdim`), closes asked (`win close again=`), re-opens on press, cancelled re-opens, and the result.
A round where the app saw fewer presses than were sent LOST a key; the result says whether a window was left open.
Usage: rapid515.py trace.txt [more…]"""
import sys, re, collections
SENT = {'batch': 4, 'fast': 4, 'human': 4, 'retro': 4, 'bOpening': 2, 'bOpen': 2, 'bClosing': 4}
for path in sys.argv[1:]:
    rounds = []; cur = None
    for line in open(path, errors='ignore'):
        m = re.match(r'=== (\S+) (\S+) (\d+) ([CO])', line)
        if m: cur = dict(lay=m[1], seq=m[2], i=int(m[3]), res=m[4], seen=0, opened=0, close=0, reopen=0, cancel=0, home=0, win=0); rounds.append(cur); continue
        if cur is None: continue
        if re.search(r' key BUTTON_[AB] ', line): cur['seen'] += 1; cur['home'] += 1
        elif re.search(r'winkey 9[67] KeyDown', line): cur['seen'] += 1; cur['win'] += 1
        if 'win sysdim' in line: cur['opened'] += 1
        if 'win close again' in line: cur['close'] += 1
        if 'win reopen on press' in line: cur['reopen'] += 1
        if 'win reopen cancelled' in line: cur['cancel'] += 1
    agg = collections.defaultdict(lambda: [0, 0, 0, 0])
    for r in rounds:
        a = agg[(r['lay'], r['seq'])]; a[0] += 1; a[1] += r['res'] == 'O'; a[2] += r['seen'] < SENT.get(r['seq'], 0); a[3] += r['opened'] > 0
    print(path)
    for (lay, seq), (n, o, lost, op) in sorted(agg.items()):
        print(f"  {lay:13s} {seq:9s} rounds={n} open-left={o} key-lost={lost} window-opened={op}")
    for r in rounds:
        if r['res'] == 'O' or r['seen'] < SENT.get(r['seq'], 0):
            print(f"  ! {r['lay']} {r['seq']} {r['i']} {r['res']} sent={SENT.get(r['seq'])} seen={r['seen']} (home {r['home']}, window {r['win']}) opened={r['opened']} close={r['close']} reopen={r['reopen']} cancel={r['cancel']}")

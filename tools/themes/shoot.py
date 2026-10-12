#!/usr/bin/env python3
"""Renders still frames of the scenes with headless Chromium: shoot.py OUTDIR [THEME ...] [--t 3,9] [--size 960x540]"""
import os, sys
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
args = [a for a in sys.argv[1:]]
times = [6.0]; size = (960, 540)
if "--t" in args: i = args.index("--t"); times = [float(x) for x in args[i+1].split(",")]; del args[i:i+2]
if "--size" in args: i = args.index("--size"); size = tuple(int(x) for x in args[i+1].split("x")); del args[i:i+2]
out = args[0]; themes = args[1:]
os.makedirs(out, exist_ok=True)
url = "file://" + os.path.join(HERE, "preview.html")
with sync_playwright() as p:
    b = p.chromium.launch(args=["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    pg = b.new_page(viewport={"width": size[0], "height": size[1]})
    if not themes:
        pg.goto(url); themes = pg.evaluate("DATA.themes.map(t => t.name)")
    for th in themes:
        for t in times:
            pg.goto(f"{url}?theme={th}&t={t}&w={size[0]}&h={size[1]}")
            pg.wait_for_function("document.title === 'done' || document.title === 'error'", timeout=60000)
            if pg.title() == "error":
                print(th, "ERROR", pg.inner_text("#err")); break
            pg.locator(".stage").screenshot(path=os.path.join(out, f"{th}_{t:g}.png"))
    b.close()
print("ok")

"""Renders one still frame of a scene from preview.html (run gen.py first).
   python tools/themes/shoot_frame.py out.png THEME_NAME seconds [width height]
   Set CHROME to a Chromium path if Playwright's own browser isn't installed."""
import os, sys, asyncio
from playwright.async_api import async_playwright
# usage: shoot.py out.png THEME t [w h] [extra query]
async def main():
    out, theme, t = sys.argv[1], sys.argv[2], sys.argv[3]
    w = sys.argv[4] if len(sys.argv) > 4 else "1280"; h = sys.argv[5] if len(sys.argv) > 5 else "720"
    extra = sys.argv[6] if len(sys.argv) > 6 else ""
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path=os.environ.get("CHROME") or None, args=["--use-gl=angle","--use-angle=swiftshader","--enable-unsafe-swiftshader"])
        pg = await b.new_page(viewport={"width": int(w), "height": int(h)})
        msgs=[]
        pg.on("console", lambda m: msgs.append(m.text))
        from pathlib import Path
        await pg.goto(Path(os.path.dirname(os.path.abspath(__file__)), "preview.html").as_uri() + f"?theme={theme}&t={t}&w={w}&h={h}{extra}")
        try:
            await pg.wait_for_function("document.title==='done' || document.getElementById('err').textContent.length>0", timeout=60000)
        except Exception as e: print("timeout", e)
        e = await pg.evaluate("document.getElementById('err').textContent")
        if e: print("ERR", e[:3000])
        el = await pg.query_selector("#gl")
        await el.screenshot(path=out)
        await b.close()
asyncio.run(main())

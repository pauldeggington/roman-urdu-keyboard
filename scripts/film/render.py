import sys, asyncio, os
from playwright.async_api import async_playwright
# usage: render.py out_dir fps [t1 t2 ...]  (times given: stills only)
out = sys.argv[1]; fps = int(sys.argv[2]); times = [float(x) for x in sys.argv[3:]]
os.makedirs(out, exist_ok=True)
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={'width':1920,'height':1080})
        await pg.goto('file://' + os.path.abspath(os.environ.get('FILM', 'index.html')) + '?render')
        await pg.evaluate('document.fonts.ready')
        await pg.wait_for_timeout(500)
        if times:
            for t in times:
                await pg.evaluate(f'window.seek({t})')
                await pg.screenshot(path=f'{out}/s_{t:06.2f}.png')
        else:
            dur = await pg.evaluate('DUR')
            n = int(dur*fps)+1
            for i in range(n):
                await pg.evaluate(f'window.seek({i/fps})')
                await pg.screenshot(path=f'{out}/f_{i:05d}.png')
        await b.close()
asyncio.run(main())

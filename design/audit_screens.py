#!/usr/bin/env python3
"""Render every screen with Playwright and measure layout rules.

Checks per screen and width (360, 412):
  - button heights are 44 or 56 only
  - no native select / date / checkbox / search inputs
  - no horizontal overflow
  - no clipped text (scrollWidth > clientWidth on text-bearing elements)
  - top bar / page title y positions are the same across screens
  - thumbnail heights equal their widths and are equal within a grid
Writes PNGs (412 wide) to design/screens/*.png and prints a table.
"""
import json
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
SCREENS = sorted((HERE / "screens").glob("*.html"))
WIDTHS = [360, 412]

JS = r"""
() => {
  const r = {};
  const btns = [...document.querySelectorAll('.btn, .ibtn')].map(b => Math.round(b.getBoundingClientRect().height));
  r.buttonHeights = [...new Set(btns)].sort((a,b)=>a-b);
  r.native = document.querySelectorAll('select, input[type=date], input[type=checkbox], input[type=search]').length;
  r.overflowX = document.documentElement.scrollWidth > window.innerWidth;
  const clipped = [];
  document.querySelectorAll('.btn span, .t-body, .t-label, .t-cap, .t-title, .t-strong, .t-timer, .chip, .dd-btn span, .title, .seg button, .tool span, .dur, .parts').forEach(e => {
    if (e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).display !== 'inline') clipped.push(e.textContent.trim().slice(0,30));
    const pr = e.closest('.btn, .row, .chip, .seg button, .rec-pill');
    if (pr) { const a = e.getBoundingClientRect(), b = pr.getBoundingClientRect(); if (a.right > b.right + 1 || a.left < b.left - 1) clipped.push('overflows parent: ' + e.textContent.trim().slice(0,30)); }
  });
  const ov = (x, y) => { const a = x.getBoundingClientRect(), b = y.getBoundingClientRect(); return a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1; };
  document.querySelectorAll('.seg').forEach(g => { const bs = [...g.children]; for (let i = 1; i < bs.length; i++) if (ov(bs[i-1], bs[i])) clipped.push('overlap in segmented: ' + bs[i].textContent); });
  document.querySelectorAll('.clip').forEach(c => { const lab = c.querySelector(':scope > span'); c.querySelectorAll('.handle').forEach(h => { if (lab && ov(lab, h)) clipped.push('clip label under handle: ' + lab.textContent); }); });
  document.querySelectorAll('.row').forEach(rw => { const m = rw.querySelector('.main'); const tr = rw.lastElementChild; if (m && tr && tr !== m && ov(m, tr)) clipped.push('row text under control: ' + m.textContent.slice(0,30)); });
  r.clipped = clipped;
  const tb = document.querySelector('.topbar'); r.topbarY = tb ? Math.round(tb.getBoundingClientRect().top) : null;
  const pt = document.querySelector('.page-title'); r.titleY = pt ? Math.round(pt.getBoundingClientRect().top) : null;
  const first = document.querySelector('.page-title + *, .preview'); r.firstBlockY = first ? Math.round(first.getBoundingClientRect().top) : null;
  const th = [...document.querySelectorAll('.grid .thumb')].map(t => { const b = t.getBoundingClientRect(); return [Math.round(b.width), Math.round(b.height)]; });
  r.thumbSquare = th.every(([w,h]) => Math.abs(w-h) <= 1);
  r.thumbSizes = [...new Set(th.map(x => x[1]))];
  r.rowHeights = [...new Set([...document.querySelectorAll('.row')].map(e => Math.round(e.getBoundingClientRect().height)))].sort((a,b)=>a-b);
  r.font = getComputedStyle(document.body).fontFamily.split(',')[0];
  return r;
}
"""


def main():
    results = []
    with sync_playwright() as p:
        b = p.chromium.launch(executable_path="/usr/bin/google-chrome", args=["--no-sandbox"])
        for w in WIDTHS:
            pg = b.new_page(viewport={"width": w, "height": 892}, device_scale_factor=2)
            for s in SCREENS:
                pg.goto(s.as_uri())
                pg.evaluate("document.fonts.ready")
                pg.wait_for_timeout(50)
                m = pg.evaluate(JS)
                m.update(screen=s.stem, width=w)
                results.append(m)
                if w == 412:
                    pg.screenshot(path=str(s.with_suffix(".png")), full_page=True)
            pg.close()
        b.close()

    bad = 0
    print(f"{'screen':24} {'w':>3} {'btn h':10} {'nat':>3} {'ovX':>4} {'clip':>4} {'topY':>4} {'titleY':>6} {'1stY':>5} {'rows':10} {'thumbs':8}")
    for r in results:
        ok_btn = set(r["buttonHeights"]) <= {44, 56}
        issues = (not ok_btn) + (r["native"] > 0) + r["overflowX"] + (len(r["clipped"]) > 0) + (not r["thumbSquare"])
        bad += issues
        print(f"{r['screen']:24} {r['width']:>3} {str(r['buttonHeights']):10} {r['native']:>3} {str(r['overflowX'])[0]:>4} "
              f"{len(r['clipped']):>4} {str(r['topbarY']):>4} {str(r['titleY']):>6} {str(r['firstBlockY']):>5} "
              f"{str(r['rowHeights']):10} {str(r['thumbSizes']):8}")
        for c in r["clipped"]:
            print("    clipped:", c)
    fonts = {r["font"] for r in results}
    tops = {r["topbarY"] for r in results if r["topbarY"] is not None}
    titles = {r["titleY"] for r in results if r["titleY"] is not None}
    print("fonts:", fonts, "| topbar y:", tops, "| page title y:", titles)
    bad += (len(tops) > 1) + (len(titles) > 1)
    (HERE / "audit.json").write_text(json.dumps(results, indent=1))
    print("RESULT:", "PASS" if bad == 0 else f"FAIL ({bad})")
    sys.exit(0 if bad == 0 else 1)


if __name__ == "__main__":
    main()

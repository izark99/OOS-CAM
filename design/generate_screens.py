#!/usr/bin/env python3
"""Generate every OOS Cam mock-up screen from shared components.

Only design-system tokens (var(--...)) are used for colors and sizes.
Run: python3 design/generate_screens.py   -> design/screens/*.html
"""
from pathlib import Path

OUT = Path(__file__).resolve().parent / "screens"
TOKENS = "../design-system/oos-cam/tokens.css"

# ------------------------------------------------------------------ icons
# Simple stroke icons drawn for OOS Cam (24x24, stroke 2), in the Lucide manner.
ICONS = {
    "settings": '<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"/>',
    "torch": '<path d="M8 2h8v5l-2 4v11h-4V11L8 7z"/><path d="M12 14v2"/>',
    "mic": '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 10a7 7 0 0 0 14 0M12 17v5"/>',
    "mic-off": '<path d="M3 3l18 18M9 9v1a3 3 0 0 0 5 2M15 9V5a3 3 0 0 0-6 0M5 10a7 7 0 0 0 11 5.7M19 10a7 7 0 0 1-.5 2.6M12 17v5"/>',
    "flip": '<path d="M4 7h12l-3-3M20 17H8l3 3"/><circle cx="12" cy="12" r="2"/>',
    "moon": '<path d="M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z"/>',
    "back": '<path d="M15 5l-7 7 7 7"/>',
    "close": '<path d="M6 6l12 12M18 6L6 18"/>',
    "check": '<path d="M5 12l5 5 9-10"/>',
    "chev-down": '<path d="M6 9l6 6 6-6"/>',
    "play": '<path d="M8 5l11 7-11 7z"/>',
    "scissors": '<circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M8.5 7.5L20 18M8.5 16.5L20 6"/>',
    "gauge": '<path d="M4 16a8 8 0 1 1 16 0"/><path d="M12 16l4-5"/>',
    "volume-x": '<path d="M4 9h4l5-4v14l-5-4H4zM16 9l5 6M21 9l-5 6"/>',
    "share": '<path d="M12 3v12M7 8l5-5 5 5M5 14v6h14v-6"/>',
    "trash": '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>',
    "grip": '<path d="M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01"/>',
    "sd": '<path d="M7 2h8l4 4v16H7zM10 6v3M13 6v3"/>',
    "alert": '<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18h.01"/>',
    "search": '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
    "film": '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4"/>',
    "camera": '<path d="M3 8h4l2-3h6l2 3h4v12H3z"/><circle cx="12" cy="13" r="4"/>',
    "bell": '<path d="M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4"/>',
    "plus": '<path d="M12 5v14M5 12h14"/>',
    "order": '<path d="M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4"/>',
}


def icon(name, size=24, cls="ic"):
    return (f'<svg class="{cls}" width="{size}" height="{size}" viewBox="0 0 24 24" fill="none" '
            f'stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" '
            f'aria-hidden="true">{ICONS[name]}</svg>')


# ------------------------------------------------------------------ css
CSS = """
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { background: var(--color-bg-camera); }
body { font: var(--font-body); color: var(--color-text); -webkit-font-smoothing: antialiased; }
.phone { position: relative; width: 100%; min-height: 892px; overflow: hidden; background: var(--color-bg); }
.phone.cam { background: var(--color-bg-camera); }
.t-display { font: var(--font-display); }
.t-title { font: var(--font-title); }
.t-body { font: var(--font-body); }
.t-strong { font: var(--font-body-strong); }
.t-label { font: var(--font-label); }
.t-cap { font: var(--font-caption); }
.t-timer { font: var(--font-timer); font-variant-numeric: tabular-nums; }
.c2 { color: var(--color-text-secondary); }
.c3 { color: var(--color-text-tertiary); }
.cd { color: var(--color-danger-text); }
.ca { color: var(--color-accent); }
.ic { flex: none; display: block; }

.status { height: 24px; padding: 0 var(--space-4); display: flex; align-items: center; justify-content: space-between; font: var(--font-caption); color: var(--color-text); }

/* glass */
.glass { background: var(--color-glass); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); border: 1px solid var(--color-glass-border); }
.solid { background: var(--color-glass-solid); border: 1px solid var(--color-glass-border); }

/* buttons: two heights only */
.btn { display: inline-flex; align-items: center; justify-content: center; gap: var(--space-2); border: 0; cursor: pointer; white-space: nowrap; font: var(--font-label); font-weight: 600; color: var(--color-text); border-radius: var(--radius-pill); transition: transform var(--motion-fast) var(--easing); }
.btn.md { height: var(--button-md); padding: 0 var(--space-4); }
.btn.lg { height: var(--button-lg); padding: 0 var(--space-6); font: var(--font-body-strong); }
.btn.full { width: 100%; }
.btn.accent { background: var(--color-accent); color: var(--color-text-on-accent); }
.btn.glassb { background: var(--color-glass); backdrop-filter: blur(var(--glass-blur)); border: 1px solid var(--color-glass-border); }
.btn.raised { background: var(--color-surface-raised); }
.btn.plain { background: transparent; color: var(--color-accent); }
.btn.plaindanger { background: transparent; color: var(--color-danger-text); }
.btn.danger { background: var(--color-danger); color: var(--color-text); }
.btn:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.ibtn { width: var(--button-md); height: var(--button-md); border-radius: var(--radius-pill); display: inline-flex; align-items: center; justify-content: center; color: var(--color-text); border: 0; background: transparent; cursor: pointer; flex: none; }
.ibtn.glassb { background: var(--color-glass); backdrop-filter: blur(var(--glass-blur)); border: 1px solid var(--color-glass-border); }
.ibtn.on { color: var(--color-accent); }

/* top bar */
.topbar { height: var(--topbar); padding: 0 var(--space-2); display: flex; align-items: center; gap: var(--space-2); }
.topbar .title { flex: 1; font: var(--font-title); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.topbar .title.center { text-align: center; }
.page-title { padding: var(--space-2) var(--space-4) var(--space-4); font: var(--font-display); }

/* camera */
.scene { position: absolute; inset: 0; background:
  radial-gradient(120% 70% at 30% 35%, var(--demo-scene-2), transparent 60%),
  radial-gradient(90% 60% at 80% 70%, var(--demo-scene-1), transparent 65%),
  var(--demo-scene-3); }
.cam-top { position: absolute; top: 32px; left: var(--space-4); right: var(--space-4); display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.pillgroup { display: flex; align-items: center; gap: var(--space-1); padding: 0 var(--space-1); height: var(--button-md); border-radius: var(--radius-pill); }
.chip { height: 32px; padding: 0 var(--space-3); border-radius: var(--radius-pill); display: inline-flex; align-items: center; gap: var(--space-1); font: var(--font-caption); color: var(--color-text); }
.rec-pill { position: absolute; top: 88px; left: 50%; transform: translateX(-50%); height: var(--button-md); padding: 0 var(--space-4); border-radius: var(--radius-pill); display: flex; align-items: center; gap: var(--space-2); white-space: nowrap; }
.dot { width: 10px; height: 10px; border-radius: 50%; background: var(--color-record); flex: none; }
.cam-bottom { position: absolute; left: 0; right: 0; bottom: 0; height: 200px; background: var(--color-bg-camera); display: flex; flex-direction: column; align-items: center; justify-content: flex-end; padding-bottom: var(--space-8); gap: var(--space-5); }
.cam-row { width: 100%; padding: 0 var(--space-8); display: flex; align-items: center; justify-content: space-between; }
.shutter { width: var(--shutter); height: var(--shutter); border-radius: 50%; border: 4px solid var(--color-text); display: flex; align-items: center; justify-content: center; background: transparent; cursor: pointer; }
.shutter i { display: block; width: 60px; height: 60px; border-radius: 50%; background: var(--color-record); transition: all var(--motion-base) var(--easing); }
.shutter.rec i { width: 28px; height: 28px; border-radius: 8px; }
.lib-thumb { width: var(--button-md); height: var(--button-md); border-radius: var(--radius-sm); background: linear-gradient(135deg, var(--demo-scene-1), var(--demo-scene-2)); border: 2px solid var(--color-text); }
.hint { font: var(--font-caption); color: var(--color-text-secondary); }

/* sheet */
.scrim { position: absolute; inset: 0; background: var(--color-scrim); }
.sheet { position: absolute; left: var(--space-2); right: var(--space-2); bottom: var(--space-2); border-radius: var(--radius-lg); padding: var(--space-6) var(--space-4) var(--space-4); display: flex; flex-direction: column; gap: var(--space-4); }
.sheet .grab { width: 36px; height: 4px; border-radius: 2px; background: var(--color-track); margin: calc(-1 * var(--space-3)) auto 0; }
.sheet-actions { display: flex; flex-direction: column; gap: var(--space-2); }

/* lists */
.group { margin: 0 var(--space-4) var(--space-6); }
.group-h { font: var(--font-caption); color: var(--color-text-secondary); text-transform: uppercase; letter-spacing: .04em; padding: 0 var(--space-4) var(--space-2); }
.card { background: var(--color-surface); border-radius: var(--radius-md); overflow: visible; }
.row { min-height: var(--list-row); padding: 0 var(--space-4); display: flex; align-items: center; gap: var(--space-3); }
.row + .row { border-top: 1px solid var(--color-divider); }
.row .main { flex: 1; min-width: 0; }
.row > :not(.main) { flex: none; }
.row .main .t-body { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* controls */
.switch { border: 0; cursor: pointer; width: 52px; height: 32px; border-radius: var(--radius-pill); background: var(--color-track); position: relative; flex: none; }
.switch::after { content: ""; position: absolute; top: 2px; left: 2px; width: 28px; height: 28px; border-radius: 50%; background: var(--color-text); transition: left var(--motion-fast) var(--easing); }
.switch.on { background: var(--color-success); }
.switch.on::after { left: 22px; }
.cbox { background: transparent; cursor: pointer; padding: 0; width: 24px; height: 24px; border-radius: 50%; border: 2px solid var(--color-text); display: inline-flex; align-items: center; justify-content: center; flex: none; color: var(--color-text-on-accent); }
.cbox.on { background: var(--color-accent); border-color: var(--color-accent); }
.seg { display: flex; padding: var(--space-1); gap: var(--space-1); border-radius: var(--radius-pill); background: var(--color-surface-raised); }
.seg button { flex: 1; height: 36px; padding: 0 var(--space-3); border: 0; border-radius: var(--radius-pill); background: transparent; color: var(--color-text-secondary); font: var(--font-label); font-weight: 600; cursor: pointer; white-space: nowrap; }
.seg button.on { background: var(--color-text); color: var(--color-bg); }
.dd { position: relative; }
.dd-btn { height: var(--button-md); padding: 0 var(--space-3); border-radius: var(--radius-sm); background: var(--color-surface-raised); display: inline-flex; align-items: center; gap: var(--space-2); color: var(--color-text); font: var(--font-label); border: 0; cursor: pointer; white-space: nowrap; }
.dd-btn.open { outline: 2px solid var(--color-focus); }
.dd-menu { position: absolute; right: 0; top: calc(var(--button-md) + var(--space-1)); min-width: 200px; border-radius: var(--radius-md); padding: var(--space-1); z-index: 5; }
.dd-opt { height: var(--button-md); padding: 0 var(--space-3); display: flex; align-items: center; justify-content: space-between; border-radius: var(--radius-sm); font: var(--font-label); white-space: nowrap; gap: var(--space-4); }
.dd-opt.sel { background: var(--color-track); }
.searchf { height: var(--button-md); border-radius: var(--radius-sm); background: var(--color-surface-raised); display: flex; align-items: center; gap: var(--space-2); padding: 0 var(--space-3); color: var(--color-text-tertiary); font: var(--font-body); }

/* library */
.sess-h { display: flex; align-items: baseline; justify-content: space-between; padding: var(--space-4) var(--space-4) var(--space-2); }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--thumb-gap); }
.thumb { position: relative; aspect-ratio: 1 / 1; background: linear-gradient(140deg, var(--demo-scene-1), var(--demo-scene-3)); overflow: hidden; }
.thumb.v2 { background: linear-gradient(140deg, var(--demo-scene-2), var(--demo-scene-3)); }
.thumb .dur { position: absolute; right: var(--space-1); bottom: var(--space-1); font: var(--font-caption); padding: 0 var(--space-1); border-radius: 6px; background: var(--color-scrim); }
.thumb .parts { position: absolute; left: var(--space-1); bottom: var(--space-1); font: var(--font-caption); padding: 0 var(--space-1); border-radius: 6px; background: var(--color-scrim); }
.thumb .sel { position: absolute; right: var(--space-2); top: var(--space-2); }
.thumb.picked::after { content: ""; position: absolute; inset: 0; border: 3px solid var(--color-accent); }
.thumb .bad { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-1); background: var(--color-scrim); color: var(--color-warning); font: var(--font-caption); }
.bottom-bar { position: absolute; left: var(--space-2); right: var(--space-2); bottom: var(--space-2); height: var(--tabbar); border-radius: var(--radius-pill); display: flex; align-items: center; justify-content: space-between; padding: 0 var(--space-1) 0 var(--space-2); gap: var(--space-1); }

/* editor */
.preview { margin: 0 var(--space-4); aspect-ratio: 16 / 9; border-radius: var(--radius-md); background: linear-gradient(140deg, var(--demo-scene-2), var(--demo-scene-1)); position: relative; display: flex; align-items: center; justify-content: center; }
.time-row { display: flex; justify-content: space-between; align-items: center; padding: var(--space-3) var(--space-4); }
.timeline { position: relative; margin: 0 var(--space-4); height: 88px; }
.track { position: absolute; left: 0; right: 0; top: 16px; height: 56px; display: flex; gap: var(--space-1); }
.clip { position: relative; height: 56px; border-radius: var(--radius-sm); display: flex; align-items: flex-end; padding: var(--space-1) var(--space-2); font: var(--font-caption); overflow: hidden; }
.clip.a { background: var(--color-clip-1); flex: 75; }
.clip.b { background: var(--color-clip-2); flex: 60; }
.clip.sel { padding-left: 22px; }
.clip.sel { outline: 3px solid var(--color-accent); outline-offset: 0; }
.clip .speed { position: absolute; top: 0; bottom: 0; background: var(--color-speed-range); border-left: 2px solid var(--color-accent); border-right: 2px solid var(--color-accent); }
.clip .speed span { position: absolute; top: var(--space-1); left: 50%; transform: translateX(-50%); color: var(--color-text); }
.handle { position: absolute; top: 0; width: 14px; height: 56px; background: var(--color-accent); display: flex; align-items: center; justify-content: center; color: var(--color-text-on-accent); }
.handle.l { left: 0; border-radius: var(--radius-sm) 0 0 var(--radius-sm); }
.handle.r { right: 0; border-radius: 0 var(--radius-sm) var(--radius-sm) 0; }
.playhead { position: absolute; top: 0; bottom: 0; width: 2px; background: var(--color-text); }
.toolbar { position: absolute; left: var(--space-2); right: var(--space-2); bottom: var(--space-2); height: var(--tabbar); border-radius: var(--radius-pill); display: flex; align-items: center; justify-content: space-around; padding: 0 var(--space-1); }
.tool { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; width: 64px; height: var(--button-lg); border-radius: var(--radius-md); color: var(--color-text); font: var(--font-caption); border: 0; background: transparent; cursor: pointer; }
.tool.on { color: var(--color-accent); }

/* feedback */
.progress { height: 6px; border-radius: 3px; background: var(--color-track); overflow: hidden; }
.progress i { display: block; height: 100%; background: var(--color-accent); }
.toast { border-radius: var(--radius-pill); padding: 0 var(--space-4); height: var(--button-md); display: inline-flex; align-items: center; gap: var(--space-2); font: var(--font-label); }
.skel { background: var(--color-surface-raised); position: relative; overflow: hidden; }
.skel::after { content: ""; position: absolute; inset: 0; background: linear-gradient(90deg, transparent, var(--color-track), transparent); }
.badge { display: inline-flex; align-items: center; height: 24px; padding: 0 var(--space-2); border-radius: var(--radius-pill); font: var(--font-caption); background: var(--color-surface-raised); }
.empty { display: flex; flex-direction: column; align-items: center; text-align: center; gap: var(--space-3); padding: var(--space-8) var(--space-6); }
.panel { background: var(--color-surface); border-radius: var(--radius-md); padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); }
.stack { display: flex; flex-direction: column; gap: var(--space-4); padding: 0 var(--space-4) var(--space-8); }
.hrow { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.dialog { border-radius: var(--radius-lg); padding: var(--space-6) var(--space-4) var(--space-4); display: flex; flex-direction: column; gap: var(--space-3); text-align: center; }
.dialog .acts { display: flex; gap: var(--space-2); margin-top: var(--space-2); }
.dialog .acts .btn { flex: 1; }
"""


# ------------------------------------------------------------------ components
def btn(label, size="md", kind="accent", ic=None, full=False, extra=""):
    k = {"accent": "accent", "glass": "glassb", "raised": "raised", "plain": "plain",
         "plain-danger": "plaindanger", "danger": "danger"}[kind]
    i = icon(ic, 20) if ic else ""
    return f'<button class="btn {size} {k}{" full" if full else ""} {extra}">{i}<span>{label}</span></button>'


def ibtn(ic, label, glass=False, on=False):
    return (f'<button class="ibtn{" glassb" if glass else ""}{" on" if on else ""}" aria-label="{label}">'
            f'{icon(ic)}</button>')


def status():
    # top inset only: the system draws the real status bar
    return '<div class="status" aria-hidden="true"></div>'


def topbar(title, left=None, right="", center=False):
    l = left or ""
    return f'<div class="topbar">{l}<div class="title{" center" if center else ""}">{title}</div>{right}</div>'


def checkbox(on):
    return f'<button class="cbox{" on" if on else ""}" role="checkbox" aria-checked="{str(on).lower()}" aria-label="Select">{icon("check", 16) if on else ""}</button>'


def switch(on):
    return f'<button class="switch{" on" if on else ""}" role="switch" aria-checked="{str(on).lower()}" aria-label="Toggle"></button>'


def seg(options, active):
    b = "".join(f'<button class="{"on" if o == active else ""}">{o}</button>' for o in options)
    return f'<div class="seg" role="radiogroup">{b}</div>'


def dropdown(value, open_=False, options=None):
    menu = ""
    if open_:
        items = "".join(
            f'<div class="dd-opt{" sel" if o == value else ""}" role="option"><span>{o}</span>'
            f'{icon("check", 18) if o == value else ""}</div>' for o in options)
        menu = f'<div class="dd-menu solid" role="listbox">{items}</div>'
    return (f'<div class="dd"><button class="dd-btn{" open" if open_ else ""}" aria-haspopup="listbox">'
            f'<span>{value}</span>{icon("chev-down", 18)}</button>{menu}</div>')


def search(ph):
    return f'<div class="searchf" role="search">{icon("search", 20)}<span>{ph}</span></div>'


def row(title, sub=None, trailing=""):
    s = f'<div class="t-cap c2">{sub}</div>' if sub else ""
    return f'<div class="row"><div class="main"><div class="t-body">{title}</div>{s}</div>{trailing}</div>'


def group(h, rows):
    return f'<div class="group-h">{h}</div><div class="group"><div class="card">{"".join(rows)}</div></div>'


def thumb(dur, v2=False, parts=None, sel=None, bad=False):
    p = f'<span class="parts">{parts} parts</span>' if parts else ""
    s = f'<span class="sel">{checkbox(sel)}</span>' if sel is not None else ""
    b = f'<div class="bad">{icon("alert", 20)}<span>Damaged</span></div>' if bad else ""
    picked = " picked" if sel else ""
    d = "" if bad else f'<span class="dur">{dur}</span>'
    return f'<div class="thumb{" v2" if v2 else ""}{picked}">{p}{d}{s}{b}</div>'


def skel_thumb():
    return '<div class="thumb skel"></div>'


def progress(pct):
    return f'<div class="progress" role="progressbar" aria-valuenow="{pct}"><i style="width:{pct}%"></i></div>'


def sheet(inner):
    return f'<div class="scrim"></div><div class="sheet solid"><div class="grab"></div>{inner}</div>'


def page(name, body, cam=False):
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>OOS Cam · {name}</title>
<link rel="stylesheet" href="{TOKENS}">
<style>{CSS}</style></head>
<body><main class="phone{" cam" if cam else ""}" data-screen="{name}">{body}</main></body></html>
"""


# ------------------------------------------------------------------ screens
def camera(recording=False):
    top = (f'<div class="cam-top"><div class="pillgroup glass">{ibtn("torch", "Torch")}'
           f'{ibtn("mic", "Microphone on", on=True)}</div>'
           f'<div class="pillgroup glass"><span class="chip">1080p · 30</span></div>'
           f'{ibtn("settings", "Settings", glass=True)}</div>')
    rec = ""
    if recording:
        rec = ('<div class="rec-pill glass"><span class="dot"></span><span class="t-timer">12:04</span>'
               '<span class="t-cap">Part 1 · 9.8 GB free</span></div>')
    above = btn("Screen off", "md", "glass", "moon") if recording else '<span class="hint">Auto-split every 30 min</span>'
    bottom = (f'<div class="cam-bottom">{above}<div class="cam-row"><div class="lib-thumb" aria-label="Library"></div>'
              f'<button class="shutter{" rec" if recording else ""}" aria-label="{"Stop" if recording else "Record"}"><i></i></button>'
              f'{ibtn("flip", "Switch camera", glass=True)}</div></div>')
    return f'<div class="scene"></div>{status()}{top}{rec}{bottom}'


def screen_off():
    return (f'{status()}<div class="empty" style="padding-top:280px">'
            f'<span class="dot"></span><div class="t-timer">Recording · 12:04</div>'
            f'<div class="t-label c2">The screen turns off in 3 s. Stop from the notification.</div>'
            f'{btn("Keep screen on", "md", "glass")}</div>')


def permissions():
    inner = (f'<div class="t-title">Allow OOS Cam to record</div>'
             f'<div class="card">'
             f'{row("Camera", "Needed to record video", icon("camera"))}'
             f'{row("Microphone", "For sound; you can record without it", icon("mic"))}'
             f'{row("Notifications", "Shows the Stop button while recording", icon("bell"))}</div>'
             f'<div class="t-cap c2">Recording always shows a notification. OOS Cam has no internet access.</div>'
             f'<div class="sheet-actions">{btn("Continue", "lg", "accent", full=True)}{btn("Not now", "lg", "plain", full=True)}</div>')
    return camera(False) + sheet(inner)


def library(select=False):
    sel = (lambda v: v) if select else (lambda v: None)
    right = btn("Cancel" if select else "Select", "md", "plain")
    head = topbar("", ibtn("back", "Back to camera"), right)
    title = '<div class="page-title">2 selected</div>' if select else '<div class="page-title">Library</div>'
    g1 = ('<div class="sess-h"><span class="t-strong">Today</span><span class="t-cap c2">3 videos · 1 h 07 min</span></div>'
          '<div class="grid">' + thumb("1:05:00", parts=3, sel=sel(False)) + thumb("1:35", True, sel=sel(True))
          + thumb("1:02", sel=sel(True)) + '</div>')
    g2 = ('<div class="sess-h"><span class="t-strong">Monday, 23 Nov</span><span class="t-cap c2">4 videos</span></div>'
          '<div class="grid">' + thumb("12:10", True, sel=sel(False)) + thumb("0:48", sel=sel(False))
          + thumb("4:22", True, sel=sel(False)) + thumb("", bad=True, sel=sel(False) if select else None) + '</div>')
    bar = ""
    if select:
        bar = (f'<div class="bottom-bar glass">{ibtn("trash", "Delete")}{ibtn("share", "Share")}'
               f'{btn("Edit selected (2)", "md", "accent", "scissors")}</div>')
    return f'{status()}{head}{title}{g1}{g2}{bar}'


def editor():
    head = topbar("Untitled project", ibtn("close", "Close editor"), btn("Export", "md", "accent"))
    tl = ('<div class="timeline"><div class="track">'
          '<div class="clip a sel"><div class="handle l">' + icon("grip", 12) + '</div>'
          '<div class="speed" style="left:37%;width:33%"><span>2x</span></div>'
          '<span>A · 1:15</span><div class="handle r">' + icon("grip", 12) + '</div></div>'
          '<div class="clip b"><span>B · 1:00</span></div></div>'
          '<div class="playhead" style="left:31%"></div></div>')
    tools = ('<div class="toolbar glass">'
             f'<button class="tool on">{icon("scissors")}<span>Trim</span></button>'
             f'<button class="tool">{icon("gauge")}<span>Speed</span></button>'
             f'<button class="tool on">{icon("volume-x")}<span>Muted</span></button>'
             f'<button class="tool">{icon("order")}<span>Order</span></button>'
             f'<button class="tool">{icon("plus")}<span>Add</span></button></div>')
    info = ('<div class="time-row"><span class="t-timer">00:42 / 02:15</span>'
            '<span class="badge">2 clips · muted</span></div>')
    trim = ('<div class="time-row"><span class="t-cap c2">Clip A trimmed 2.0 s – 92.0 s</span>'
            '<span class="t-cap c2">1080p</span></div>')
    pv = f'<div class="preview"><button class="ibtn glassb" aria-label="Play">{icon("play")}</button></div>'
    return f'{status()}{head}{pv}{info}{tl}{trim}{tools}'


def speed_sheet():
    inner = (f'<div class="t-title">Speed</div>'
             f'{seg(["0.25x", "0.5x", "1x", "1.5x", "2x", "4x"], "2x")}'
             f'<div class="panel"><div class="t-label">Range on clip A</div>'
             f'<div class="t-body">30.0 s – 60.0 s</div><div class="t-cap c2">Plays in 15.0 s · project 2:15</div></div>'
             f'<div class="sheet-actions">{btn("Apply", "lg", "accent", full=True)}{btn("Remove range", "lg", "plain-danger", full=True)}</div>')
    return editor() + sheet(inner)


def export_sheet():
    inner = (f'<div class="t-title">Exporting</div>'
             f'<div class="row" style="padding:0"><div class="main"><div class="t-body">Resolution</div></div>{dropdown("1080p")}</div>'
             f'<div class="t-cap c2">2:15 · no sound · about 270 MB · Movies/OOS Cam</div>'
             f'{progress(46)}<div class="t-label">46% · about 40 s left</div>'
             f'<div class="sheet-actions">{btn("Cancel export", "lg", "raised", full=True)}</div>')
    return editor() + sheet(inner)


def settings():
    head = topbar("", ibtn("back", "Back"))
    rec = group("Recording", [
        row("Segment length", "New file after this time", dropdown("30 min")),
        row("Default quality", "4K shown only if supported", dropdown("1080p · 30")),
        row("Microphone on by default", None, switch(True))])
    sto = group("Storage", [
        row("Save to", "SD card needs a folder pick", seg(["Device", "SD card"], "Device")),
        row("Stop when free space is below", None, '<span class="t-label c2">500 MB</span>')])
    dis = group("Display", [
        row("Reduce glass effects", "Solid panels, less battery", switch(False)),
        row("Language", None, dropdown("English"))])
    about = group("About", [
        row("Version", None, '<span class="t-label c2">0.1.0</span>'),
        row("License", None, '<span class="t-label c2">Apache-2.0</span>'),
        row("Source code", "github.com/…/oos-cam", "")])
    return f'{status()}{head}<div class="page-title">Settings</div>{rec}{sto}{dis}{about}'


def components():
    head = topbar("", ibtn("back", "Back"))
    buttons = ('<div class="panel"><div class="t-label c2">Buttons: 56 and 44 only</div>'
               f'<div class="hrow">{btn("Primary", "lg", "accent")}{btn("Glass", "lg", "glass")}</div>'
               f'<div class="hrow">{btn("Primary", "md", "accent")}{btn("Raised", "md", "raised")}{btn("Plain", "md", "plain")}</div>'
               f'<div class="hrow">{btn("Delete", "md", "danger", "trash")}{btn("Remove", "md", "plain-danger")}</div></div>')
    inputs = ('<div class="panel"><div class="t-label c2">Inputs</div>'
              f'{search("Search clips")}'
              f'<div class="hrow">{checkbox(False)}{checkbox(True)}{switch(False)}{switch(True)}</div>'
              f'{seg(["Device", "SD card"], "SD card")}</div>')
    dd = ('<div class="panel" style="padding-bottom:200px"><div class="t-label c2">Dropdown, open</div>'
          f'<div class="hrow" style="justify-content:space-between"><span class="t-body">Segment length</span>'
          f'{dropdown("30 min", True, ["5 min", "10 min", "30 min", "60 min"])}</div></div>')
    empty = ('<div class="panel"><div class="t-label c2">Empty library</div><div class="empty">'
             f'{icon("film", 40)}<div class="t-strong">No videos yet</div>'
             f'<div class="t-label c2">Videos you record appear here.</div>{btn("Open camera", "md", "raised", "camera")}</div></div>')
    error = ('<div class="panel"><div class="t-label c2">Error</div>'
             f'<div class="hrow"><span class="cd">{icon("alert")}</span><span class="t-strong">Export failed</span></div>'
             f'<div class="t-label c2">Clip B could not be read. Remove it or try again.</div>'
             f'<div class="hrow">{btn("Try again", "md", "raised")}{btn("Remove clip B", "md", "plain-danger")}</div></div>')
    prog = ('<div class="panel"><div class="t-label c2">Progress and toast</div>'
            f'{progress(72)}<div class="t-label">Exporting · 72%</div>'
            f'<div><span class="toast solid">{icon("check", 18)}<span>Saved to Movies/OOS Cam</span></span></div></div>')
    skel = ('<div class="panel"><div class="t-label c2">Loading skeleton (same sizes as content)</div>'
            f'<div class="grid">{skel_thumb()}{skel_thumb()}{skel_thumb()}</div></div>')
    confirm = ('<div class="panel"><div class="t-label c2">Confirm delete</div>'
               '<div class="dialog solid"><div class="t-title">Delete 2 videos?</div>'
               '<div class="t-label c2">They are removed from this phone. This cannot be undone.</div>'
               f'<div class="acts">{btn("Cancel", "md", "raised")}{btn("Delete", "md", "danger")}</div></div></div>')
    note = '<div class="t-cap c3">No date picker: the MVP has no date input.</div>'
    return (f'{status()}{head}<div class="page-title">Components</div><div class="stack">'
            f'{buttons}{inputs}{dd}{empty}{error}{prog}{skel}{confirm}{note}</div>')


SCREENS = [
    ("01-camera-idle", lambda: camera(False), True),
    ("02-camera-recording", lambda: camera(True), True),
    ("03-screen-off", screen_off, True),
    ("04-permissions", permissions, True),
    ("05-library", lambda: library(False), False),
    ("06-library-select", lambda: library(True), False),
    ("07-editor", editor, False),
    ("08-editor-speed", speed_sheet, False),
    ("09-export", export_sheet, False),
    ("10-settings", settings, False),
    ("11-components-states", components, False),
]


BODIES = {}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn, cam in SCREENS:
        (OUT / f"{name}.html").write_text(page(name, fn(), cam))
        BODIES[name] = (fn(), cam)
    print(f"wrote {len(SCREENS)} screens to {OUT}")


if __name__ == "__main__":
    main()

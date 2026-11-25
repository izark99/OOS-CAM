#!/usr/bin/env python3
"""Turn the generated screens into Design-canvas artboards (.dc.html).

Usage: python3 design/export_canvas.py <css_blob_url>
Writes design/canvas/project/*.dc.html, canvas.json and design/canvas/ooscam.css
(the stylesheet to upload: tokens.css + component CSS).
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import generate_screens as g  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "canvas"
PROJ = OUT / "project"
W = 412
FONT = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&amp;display=swap">'

TITLES = {
    "01-camera-idle": "Camera, idle", "02-camera-recording": "Camera, recording",
    "03-screen-off": "Screen-off countdown", "04-permissions": "Permissions sheet",
    "05-library": "Library", "06-library-select": "Library, select mode",
    "07-editor": "Editor", "08-editor-speed": "Editor, speed sheet",
    "09-export": "Export in progress", "10-settings": "Settings",
    "11-components-states": "Components and states",
}


def write_css():
    css = (HERE / "design-system/oos-cam/tokens.css").read_text() + "\n" + g.CSS
    (OUT / "ooscam.css").write_text(css)


def artboard(name, body, cam, h, css_url):
    file = "Main.dc.html" if name == "01-camera-idle" else f"{name}.dc.html"
    props = json.dumps({"$preview": {"width": W, "height": h}})
    html = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>OOS Cam · {TITLES[name]}</title>
<script src="./support.js"></script>
<link rel="stylesheet" href="{css_url}">
</head>
<body>
<x-dc>
<helmet>
{FONT}
<style>
body{{margin:0}}
</style>
</helmet>
<div style="width: {W}px; height: {h}px; overflow: hidden; font-family: Inter, system-ui, sans-serif">
<main class="phone{' cam' if cam else ''}" style="min-height: {h}px">{body}</main>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{props}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
"""
    (PROJ / file).write_text(html)
    return file


def main():
    css_url = sys.argv[1] if len(sys.argv) > 1 else "/_blob/PENDING"
    OUT.mkdir(exist_ok=True)
    PROJ.mkdir(parents=True, exist_ok=True)
    write_css()
    g.main()
    boards, order = {}, []
    x = y = 0
    rowh = 0
    for i, (name, _, _) in enumerate(g.SCREENS):
        body, cam = g.BODIES[name]
        png = HERE / "screens" / f"{name}.png"
        h = max(892, Image.open(png).height // 2) if png.exists() else 892
        f = artboard(name, body, cam, h, css_url)
        if i and i % 4 == 0:
            x, y = 0, y + rowh + 120
            rowh = 0
        boards[f] = {"x": x, "y": y, "w": W, "h": h, "title": TITLES[name]}
        order.append(f)
        x += W + 80
        rowh = max(rowh, h)
    canvas = {
        "v": 3,
        "createdOnFiles": {"v": 1, "at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
        "title": "OOS Cam UI Demo", "launch": {"view": "canvas"}, "pages": [],
        "boards": boards, "order": order,
        "notes": {"head": {"x": 0, "y": -300, "text": "OOS Cam · MVP screens", "kind": "title1", "maxW": 1736}},
        "designSystems": [],
    }
    (PROJ / "canvas.json").write_text(json.dumps(canvas, indent=1))
    print(css_url, order)


if __name__ == "__main__":
    main()

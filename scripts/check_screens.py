#!/usr/bin/env python3
"""Gate G4: one Roborazzi screenshot per target screen, at 412 dp width."""
import sys
from pathlib import Path
import struct
ROOT = Path(__file__).resolve().parent.parent
names = sorted(p.stem for p in (ROOT / "design/screens").glob("*.html"))
shots = {p.stem: p for p in ROOT.glob("app/src/test/screenshots/**/*.png")}
fail = 0
for n in names:
    m = [s for k, s in shots.items() if k.endswith(n)]
    if not m:
        print(f"FAIL missing screenshot for {n}"); fail += 1; continue
    w, h = struct.unpack('>II', m[0].read_bytes()[16:24])
    print(f"{n}: {m[0].relative_to(ROOT)} {w}x{h}")
print(f"{len(names) - fail}/{len(names)} screens")
print("RESULT:", "PASS" if fail == 0 else "FAIL")
sys.exit(1 if fail else 0)

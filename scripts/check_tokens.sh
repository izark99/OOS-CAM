#!/usr/bin/env bash
# Gate G4: colors only come from the generated token file.
cd "$(dirname "$0")/.."
TOKENS='app/src/main/java/io/github/ooscam/ui/theme/Tokens.kt'
[ -f "$TOKENS" ] || { echo "FAIL: $TOKENS missing"; exit 1; }
hits=$(grep -rnE 'Color\(0x|Color\.(Red|Blue|Green|Yellow|Black|White|Gray|Cyan|Magenta)|#[0-9A-Fa-f]{6}' app/src/main/java \
  | grep -v "$TOKENS" || true)
xml=$(grep -rnE '#[0-9A-Fa-f]{6,8}' app/src/main/res --include=*.xml | grep -v 'values/colors_launcher.xml' || true)
if [ -n "$hits$xml" ]; then echo "FAIL: raw colors outside Tokens.kt"; echo "$hits"; echo "$xml"; exit 1; fi
python3 - <<'PY' || exit 1
import json,re
t=json.load(open('design/design-system/oos-cam/tokens.json'))['tokens']
k=open('app/src/main/java/io/github/ooscam/ui/theme/Tokens.kt').read()
miss=[n for n in t if not n.startswith('demo-') and re.sub(r'-(.)',lambda m:m.group(1).upper(),n) not in k]
print('tokens in Tokens.kt:', len(t)-len(miss), '/', len([n for n in t if not n.startswith('demo-')]))
if miss: print('FAIL missing:', miss); raise SystemExit(1)
PY
echo "RESULT: PASS"

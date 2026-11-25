#!/usr/bin/env python3
"""Compare the app's planner output with the oracle's expected output.

Suites
  core  run :core:runFixtures on every variant and compare all cases
  all   core + the app-level acceptance plan written by AcceptanceUseCaseTest
        (app/build/acceptance/X01_plan.json, base variant)

Options
  --self-test   use the oracle output as "actual" (checks this script, no Gradle)

Contract for :core (see TEST_RULES.md):
  ./gradlew -q :core:runFixtures --args="<fixturesDir> <outDir>"
  writes <outDir>/export_actual.json and <outDir>/segment_actual.json
  with the same shape as testcase/expected/<variant>/*.json.

Exit code 0 only when every case matches exactly.
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
VARIANTS = ["base", "v2"]


def load(p):
    return json.loads(Path(p).read_text())


def compare(kind, variant, expected, actual):
    fails = 0
    act = {r.get("id"): r for r in actual}
    for exp in expected:
        got = act.get(exp["id"])
        if got != exp:
            fails += 1
            print(f"FAIL {variant}/{kind}/{exp['id']}")
            print(f"  expected: {json.dumps(exp)[:400]}")
            print(f"  actual:   {json.dumps(got)[:400]}")
    extra = set(act) - {e["id"] for e in expected}
    if extra:
        fails += 1
        print(f"FAIL {variant}/{kind}: unexpected ids {sorted(extra)}")
    print(f"{variant}/{kind}: {len(expected) - fails if fails <= len(expected) else 0}/{len(expected)} match")
    return fails


def run_core(variant, self_test):
    out = Path(tempfile.mkdtemp(prefix=f"ooscam-{variant}-"))
    if self_test:
        shutil.copy(HERE / "expected" / variant / "export_expected.json", out / "export_actual.json")
        shutil.copy(HERE / "expected" / variant / "segment_expected.json", out / "segment_actual.json")
    else:
        cmd = ["./gradlew", "-q", ":core:runFixtures",
               f"--args={HERE / 'fixtures' / variant} {out}"]
        r = subprocess.run(cmd, cwd=ROOT)
        if r.returncode != 0:
            print(f"FAIL {variant}: runFixtures exited {r.returncode}")
            return 1
    fails = 0
    for kind in ("export", "segment"):
        a = out / f"{kind}_actual.json"
        if not a.exists():
            print(f"FAIL {variant}: missing {a.name}")
            fails += 1
            continue
        fails += compare(kind, variant, load(HERE / "expected" / variant / f"{kind}_expected.json"), load(a))
    return fails


def run_app(self_test):
    exp = [r for r in load(HERE / "expected" / "base" / "export_expected.json") if r["id"] == "X01"]
    path = ROOT / "app" / "build" / "acceptance" / "X01_plan.json"
    if self_test:
        got = exp
    elif not path.exists():
        print(f"FAIL app: {path} not found (run ./gradlew :app:testDebugUnitTest first)")
        return 1
    else:
        got = [load(path)]
    return compare("app-acceptance", "base", exp, got)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--suite", choices=["core", "all"], default="core")
    ap.add_argument("--self-test", action="store_true")
    a = ap.parse_args()
    fails = sum(run_core(v, a.self_test) for v in VARIANTS)
    if a.suite == "all":
        fails += run_app(a.self_test)
    print("RESULT:", "PASS" if fails == 0 else f"FAIL ({fails})")
    sys.exit(0 if fails == 0 else 1)


if __name__ == "__main__":
    main()

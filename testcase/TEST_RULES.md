# OOS Cam acceptance tests

This folder is read-only for the build. The app must reproduce `expected/` exactly.

## Files

| File | Purpose |
| --- | --- |
| `make_fixtures.py` | Writes `fixtures/<variant>/export_cases.json` and `segment_cases.json` |
| `reference_oracle.py` | Independent Python implementation of the rules; writes `expected/<variant>/*.json`; asserts hand-computed numbers for `base` |
| `run_acceptance.py` | Runs the app's planners and compares output with `expected/` |
| `fixtures/`, `expected/` | Generated, committed. Variants: `base`, `v2` |

Regenerate (only when the owner changes a rule):

```bash
for v in base v2; do python3 testcase/make_fixtures.py --variant $v; python3 testcase/reference_oracle.py --variant $v; done
```

## Contract for the app

`:core` exposes a JVM entry point run by Gradle:

```bash
./gradlew -q :core:runFixtures --args="<fixturesDir> <outDir>"
```

It reads `export_cases.json` and `segment_cases.json` from `<fixturesDir>`, calls `planExport` and
`planSegments`, and writes `export_actual.json` and `segment_actual.json` to `<outDir>`.
JSON shape, key names and value types must match `expected/` exactly (`speed` is a string such as
`"1.5"`, `outputDurationMs` is `null` when `ok` is false, lists of codes are sorted).

`app` has a unit test `AcceptanceUseCaseTest` that builds the X01 project through the real
editor view model and writes the resulting plan to `app/build/acceptance/X01_plan.json`.

```bash
python3 testcase/run_acceptance.py --suite core   # gate G1
python3 testcase/run_acceptance.py --suite all    # gate G3
```

## Input layout

Export case: `id, rule, muted, exportHeight (720|1080), clips[], speedRanges[]`
Clip: `order, sourceUri, sourceDurationMs, sourceHeight (720|1080|2160), inMs, outMs`
Speed range: `clipOrder, startMs, endMs, speed` (times in that clip's source time)

Segment case: `id, rule, startLocal (ISO local time), segmentMinutes, target (device|sd),
quality (720p30|1080p30|2160p30), audio, stopAtMs (or null), freeSpaceSamples[{tMs, freeMb}]`

## Rules

All times are integer milliseconds. MB = 1,000,000 bytes.

| Rule | Text |
| --- | --- |
| R1 | Output order = clip `order` ascending, whatever the input order |
| R2 | Each clip needs `0 <= inMs < outMs <= sourceDurationMs` and `outMs - inMs >= 500`, else E6 |
| R3 | `trimmedSourceMs` = sum of `max(0, outMs - inMs)`, always reported |
| R4 | A range is clipped to its clip's [inMs, outMs). Nothing left (touching the edge counts) → dropped, W1. `startMs >= endMs` or unknown clip → E7 |
| R5 | Kept ranges of one clip must not overlap, else E3 |
| R5b | Parts without a range play at 1x. Adjacent parts of one clip with equal speed are merged (a 1x range changes nothing) |
| R6 | Speed must be 0.25, 0.5, 1, 1.5, 2 or 4, else E4 |
| R7 | Part output length = round half up of (length / speed). Output = sum of parts; `outStartMs` is cumulative |
| R8 | E1 if more than 10 clips; E2 if trimmed > 1,800,000; E5 if output > 1,800,000 (E5 is checked only when E3, E4, E6, E7 are absent). Exactly on a limit passes |
| R9 | `audio` = not `muted` |
| R10 | `exportHeight` 1080 drops to 720 with W2 when any source is below 1080. 720 stays 720 |
| R11 | Any error → `ok` false, `parts` empty, `outputDurationMs` null. Warnings never block |
| R12 | `bytesPerMs` = video Mbps × 125 (+16 with audio); presets 8 / 16 / 45 Mbps. Segment length = `segmentMinutes` × 60,000, and on `sd` at most ceil(3,900,000,000 / bytesPerMs). Segments run back to back; no empty last segment |
| R13 | End = earliest of `stopAtMs` and the first sample with `freeMb < 500` (500 itself continues). Low storage first → `low_storage`, else (or tie) `user`. End at 0 → no segments |
| R14 | File name `OOSCam_YYYYMMDD_HHMMSS_partNN.mp4` from `startLocal`; NN has at least 2 digits (part101 is fine) |

## Test cases

| Id | Rule | Base expectation |
| --- | --- | --- |
| X01 | Acceptance use case | 4 parts, 135,000 ms, no audio |
| X02 | R2 boundary | 500 ms clip passes |
| X03 | R2 | 499 ms clip → E6 |
| X04 | R8 boundary | 10 clips pass |
| X05 | R8 | 11 clips → E1 |
| X06 | R8 boundary | trimmed 1,800,000 passes |
| X07 | R8 | trimmed 1,800,001 → E2, E5 |
| X08 | R8 | 0.25x output 1,800,004 → E5 |
| X09 | R8 boundary | 0.25x output 1,800,000 passes |
| X10 | R7 | rounding: 667, 667, 2, 250 …; total 48,914 |
| X11 | R4 | range clipped to trim; 17,500 |
| X12 | R4 | two ranges outside trim → W1; 20,000 |
| X13 | R5 | overlap by 1 ms → E3 |
| X14 | R6 | speed 3 → E4 |
| X15 | R10 | 720p source → height 720, W2 |
| X16 | R5b | 1x range and equal neighbours merge; 3 parts, 35,000 |
| X17 | R2 | outMs past source → E6 |
| X18 | R4 | empty range → E7 |
| X19 | R1, R9 | input out of order; 5,001 ms; audio true |
| X20 | R4, R10 | ranges on 2 clips, 4K sources stay 1080; 60,001 |
| S01 | R12 | 65 min, 30-min parts → 30, 30, 5 min |
| S02 | R12 | ends on a boundary → 2 parts, no empty third |
| S03 | R13 | 500 MB continues, 496 MB at 30 s stops |
| S04 | R12 | 4K on SD → 691,367 ms parts ×3 + 385,899 |
| S05 | R13 | low at t=0 → no segments |
| S06 | R12 | 5-min parts over 1 h + 1,234 ms → 13 parts |
| S07 | R12 | no audio → 2,000 bytes/ms; one SD part under the cap |
| S08 | R14 | 101 parts, last `_part101.mp4` |
| S09 | R13 | user stop at 12,345 before low storage |

`v2` uses the same ids and rules with other numbers and dates; 10 of its 20 export results and
all of its segment file names differ from `base`.

## Assumptions the owner confirmed or can change

- Rounding is half up on milliseconds (R7).
- Adjacent equal-speed parts merge (R5b).
- On a tie between user stop and low storage, the reason is `user` (R13).
- Real media files are not part of these tests; the owner checks the device file at the end of Phase 3.

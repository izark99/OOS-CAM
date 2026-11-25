#!/usr/bin/env python3
"""Generate OOS Cam acceptance fixtures.

Each case covers one rule or edge case from testcase/TEST_RULES.md.
Run:  python3 testcase/make_fixtures.py --variant base
      python3 testcase/make_fixtures.py --variant v2

The two variants share case ids and rules but use different numbers,
so an implementation that hardcodes base results fails on v2.
Values that sit exactly on a limit stay on that limit in both variants.
"""
import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent

VARIANTS = {
    # k = scale for free durations, d = session date, a/b = acceptance clips
    "base": {"k": 1, "date": "2026-11-25T08:00:00", "a": 95_000, "b": 62_500,
             "a_in": 2_000, "a_out": 92_000, "b_in": 1_500, "b_out": 61_500,
             "sp_s": 30_000, "sp_e": 60_000, "stop_min": 65},
    "v2": {"k": 3, "date": "2027-02-03T21:15:09", "a": 140_250, "b": 88_000,
           "a_in": 3_250, "a_out": 131_750, "b_in": 4_000, "b_out": 80_000,
           "sp_s": 40_000, "sp_e": 101_500, "stop_min": 77},
}


def clip(order, dur, i, o, h=1080, src=None):
    return {"order": order, "sourceUri": src or f"fixture://clip{order}.mp4",
            "sourceDurationMs": dur, "sourceHeight": h, "inMs": i, "outMs": o}


def rng(order, s, e, speed):
    return {"clipOrder": order, "startMs": s, "endMs": e, "speed": speed}


def project(cid, rule, clips, ranges=(), muted=False, height=1080):
    return {"id": cid, "rule": rule, "muted": muted, "exportHeight": height,
            "clips": list(clips), "speedRanges": list(ranges)}


def export_cases(v):
    k = v["k"]
    c = []
    # X01 acceptance use case
    c.append(project("X01", "R1,R2,R7,R9", [
        clip(0, v["a"], v["a_in"], v["a_out"]),
        clip(1, v["b"], v["b_in"], v["b_out"])],
        [rng(0, v["sp_s"], v["sp_e"], 2)], muted=True))
    # X02 trimmed clip exactly 500 ms passes
    c.append(project("X02", "R2 boundary", [clip(0, 10_000 * k, 1_000 * k, 1_000 * k + 500)]))
    # X03 trimmed clip 499 ms fails
    c.append(project("X03", "R2 E6", [clip(0, 10_000 * k, 1_000 * k, 1_000 * k + 499)]))
    # X04 exactly 10 clips pass
    c.append(project("X04", "R8 boundary E1", [clip(i, 20_000 + 1_000 * k * i, 0, 10_000 + 500 * k * i) for i in range(10)]))
    # X05 11 clips fail
    c.append(project("X05", "R8 E1", [clip(i, 20_000, 0, 10_000) for i in range(11)]))
    # X06 trimmed source exactly 1,800,000 ms passes (3 clips)
    c.append(project("X06", "R8 boundary E2", [
        clip(0, 700_000, 100_000, 700_000), clip(1, 900_000, 0, 900_000),
        clip(2, 400_000 + k, k, 300_000 + k)]))
    # X07 trimmed source 1,800,001 ms fails
    c.append(project("X07", "R8 E2", [
        clip(0, 700_000, 100_000, 700_000), clip(1, 900_000, 0, 900_000),
        clip(2, 400_000 + k, k, 300_001 + k)]))
    # X08 output exceeds 1,800,000 ms at 0.25x
    c.append(project("X08", "R8 E5", [clip(0, 500_000, 10_000, 460_001)], [rng(0, 10_000, 460_001, 0.25)]))
    # X09 output exactly 1,800,000 ms at 0.25x
    c.append(project("X09", "R8 boundary E5", [clip(0, 500_000, 10_000, 460_000)], [rng(0, 10_000, 460_000, 0.25)]))
    # X10 rounding half up
    c.append(project("X10", "R7", [clip(0, 60_000, 0, 50_000)], [
        rng(0, 1_000, 2_000, 1.5),            # 1000/1.5 = 666.67 -> 667
        rng(0, 3_000, 4_001, 1.5),            # 1001/1.5 = 667.33 -> 667
        rng(0, 5_000, 5_003, 2),              # 3/2 = 1.5 -> 2
        rng(0, 6_000, 6_000 + 1_001 * k, 4),  # (1001k)/4
        rng(0, 10_000 * k, 10_000 * k + 333 * k, 0.5)]))
    # X11 range partly outside trim is clipped
    c.append(project("X11", "R4", [clip(0, 30_000, 5_000 * k, 25_000)], [rng(0, 0, 10_000 * k, 2)]))
    # X12 range fully outside trim is dropped with W1 (touching the edge counts as outside)
    c.append(project("X12", "R4 W1", [clip(0, 30_000, 5_000, 25_000)], [
        rng(0, 1_000, 5_000, 2), rng(0, 25_000, 29_000, 4)]))
    # X13 overlapping ranges
    c.append(project("X13", "R5 E3", [clip(0, 30_000, 0, 30_000)], [
        rng(0, 1_000, 6_000, 2), rng(0, 5_999, 9_000, 0.5)]))
    # X14 invalid speed
    c.append(project("X14", "R6 E4", [clip(0, 30_000, 0, 30_000)], [rng(0, 1_000, 6_000, 3)]))
    # X15 export height lowered by a 720p source
    c.append(project("X15", "R10 W2", [clip(0, 30_000, 0, 20_000), clip(1, 30_000, 0, 10_000 * k, h=720)]))
    # X16 adjacent equal speeds merge, a 1x range is a no-op
    c.append(project("X16", "R5b", [clip(0, 40_000, 0, 40_000)], [
        rng(0, 2_000, 4_000, 1), rng(0, 10_000, 15_000, 2), rng(0, 15_000, 20_000 + k, 2)]))
    # X17 outMs beyond the source
    c.append(project("X17", "R2 E6", [clip(0, 30_000, 0, 30_001)]))
    # X18 range with start >= end
    c.append(project("X18", "R4 E7", [clip(0, 30_000, 0, 30_000)], [rng(0, 8_000, 8_000, 2)]))
    # X19 clips given out of order, audio kept
    c.append(project("X19", "R1 R9", [
        clip(2, 20_000, 0, 3_000 * k), clip(0, 20_000, 1_000, 2_000 + k), clip(1, 20_000, 500, 1_500)]))
    # X20 4K sources, 1080 export stays 1080, ranges on two clips
    c.append(project("X20", "R10 R4", [clip(0, 50_000, 0, 40_000, h=2160), clip(1, 50_000, 10_000, 30_000 + k, h=2160)],
                     [rng(1, 12_000, 18_000, 0.5), rng(0, 0, 8_000, 4)], height=1080))
    return c


def seg_case(cid, rule, v, minutes, target, quality, audio, stop_ms=None, samples=None):
    return {"id": cid, "rule": rule, "startLocal": v["date"], "segmentMinutes": minutes,
            "target": target, "quality": quality, "audio": audio,
            "stopAtMs": stop_ms, "freeSpaceSamples": samples or []}


def free_ramp(start_mb, mb_per_5s, until_ms):
    return [{"tMs": t, "freeMb": start_mb - mb_per_5s * (t // 5000)} for t in range(0, until_ms + 1, 5_000)]


def segment_cases(v):
    k = v["k"]
    m = 60_000
    s = []
    s.append(seg_case("S01", "R12 time cut", v, 30, "device", "1080p30", True, stop_ms=v["stop_min"] * m))
    s.append(seg_case("S02", "R12 end on boundary", v, 30, "device", "1080p30", True, stop_ms=60 * m))
    # S03: free space exactly 500 continues, first 499 stops
    samples = [{"tMs": t, "freeMb": 520 - 4 * (t // 5000)} for t in range(0, 40_001, 5_000)]
    # 520,516,512,508,504,500,496 -> stop at t=30,000 (496 < 500)
    s.append(seg_case("S03", "R13 boundary", v, 10, "device", "720p30", True, stop_ms=None, samples=samples))
    s.append(seg_case("S04", "R12 SD size cut", v, 30, "sd", "2160p30", True, stop_ms=(40 + k) * m))
    s.append(seg_case("S05", "R13 at start", v, 30, "device", "1080p30", True,
                      samples=[{"tMs": 0, "freeMb": 499}, {"tMs": 5_000, "freeMb": 498}]))
    s.append(seg_case("S06", "R12 short segments", v, 5, "device", "1080p30", True, stop_ms=60 * m + 1_234 * k))
    s.append(seg_case("S07", "R12 no audio bitrate, SD under cap", v, 60, "sd", "1080p30", False, stop_ms=(25 + k) * m))
    s.append(seg_case("S08", "R14 3-digit part", v, 5, "device", "720p30", False, stop_ms=(500 + k) * m))
    # S09: user stop comes before low storage
    s.append(seg_case("S09", "R13 earliest wins", v, 10, "device", "1080p30", True,
                      stop_ms=12_345 * k, samples=free_ramp(900, 1, 60_000)))
    return s


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", choices=sorted(VARIANTS), default="base")
    args = ap.parse_args()
    v = VARIANTS[args.variant]
    out = HERE / "fixtures" / args.variant
    out.mkdir(parents=True, exist_ok=True)
    (out / "export_cases.json").write_text(json.dumps(export_cases(v), indent=2) + "\n")
    (out / "segment_cases.json").write_text(json.dumps(segment_cases(v), indent=2) + "\n")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

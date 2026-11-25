#!/usr/bin/env python3
"""Reference oracle for OOS Cam planners.

Independent of the app: reads testcase/fixtures/<variant>/*.json, applies the
rules in testcase/TEST_RULES.md, and writes testcase/expected/<variant>/*.json.
For the base variant it also asserts numbers computed by hand.

Run:  python3 testcase/reference_oracle.py --variant base
"""
import argparse
import json
from datetime import datetime
from fractions import Fraction
from pathlib import Path

HERE = Path(__file__).resolve().parent

MAX_CLIPS = 10
MAX_TRIMMED_MS = 1_800_000
MAX_OUTPUT_MS = 1_800_000
MIN_CLIP_MS = 500
SPEEDS = {Fraction(1, 4), Fraction(1, 2), Fraction(1), Fraction(3, 2), Fraction(2), Fraction(4)}

VIDEO_MBPS = {"720p30": 8, "1080p30": 16, "2160p30": 45}
AUDIO_BYTES_PER_MS = 16          # 128 kbps
SD_CAP_BYTES = 3_900_000_000     # 3,900 MB, decimal MB
LOW_STORAGE_MB = 500


def speed_str(f):
    s = f"{float(f):g}"
    return s


def out_len(length, speed):
    """round half up of length / speed, integer ms"""
    q = Fraction(length) / speed
    return int((q * 2 + 1) // 2)


# ---------------------------------------------------------------- export
def plan_export(p):
    errors, warnings = set(), set()
    clips = sorted(p["clips"], key=lambda c: c["order"])
    if len(clips) > MAX_CLIPS:
        errors.add("E1")
    trimmed = 0
    valid_clip = {}
    for c in clips:
        i, o, d = c["inMs"], c["outMs"], c["sourceDurationMs"]
        ok = 0 <= i < o <= d and (o - i) >= MIN_CLIP_MS
        valid_clip[c["order"]] = ok
        if not ok:
            errors.add("E6")
        trimmed += max(0, o - i)
    if trimmed > MAX_TRIMMED_MS:
        errors.add("E2")

    by_order = {c["order"]: c for c in clips}
    kept = {c["order"]: [] for c in clips}
    for r in p["speedRanges"]:
        sp = Fraction(str(r["speed"]))
        if r["startMs"] >= r["endMs"] or r["clipOrder"] not in by_order:
            errors.add("E7")
            continue
        if sp not in SPEEDS:
            errors.add("E4")
            continue
        c = by_order[r["clipOrder"]]
        s, e = max(r["startMs"], c["inMs"]), min(r["endMs"], c["outMs"])
        if s >= e:
            warnings.add("W1")
            continue
        kept[c["order"]].append((s, e, sp))
    for order, rs in kept.items():
        rs.sort()
        for a, b in zip(rs, rs[1:]):
            if b[0] < a[1]:
                errors.add("E3")

    min_h = min(c["sourceHeight"] for c in clips) if clips else 1080
    height = p["exportHeight"]
    if height == 1080 and min_h < 1080:
        height = 720
        warnings.add("W2")

    parts, total = [], 0
    if not errors & {"E3", "E4", "E6", "E7"}:
        for c in clips:
            pieces, cur = [], c["inMs"]
            for s, e, sp in kept[c["order"]]:
                if cur < s:
                    pieces.append([cur, s, Fraction(1)])
                pieces.append([s, e, sp])
                cur = e
            if cur < c["outMs"]:
                pieces.append([cur, c["outMs"], Fraction(1)])
            merged = []
            for pc in pieces:
                if merged and merged[-1][2] == pc[2] and merged[-1][1] == pc[0]:
                    merged[-1][1] = pc[1]
                else:
                    merged.append(pc)
            for s, e, sp in merged:
                L = out_len(e - s, sp)
                parts.append({"clipOrder": c["order"], "srcStartMs": s, "srcEndMs": e,
                              "speed": speed_str(sp), "outStartMs": total, "outDurationMs": L})
                total += L
        if total > MAX_OUTPUT_MS:
            errors.add("E5")

    ok = not errors
    return {"id": p["id"], "ok": ok, "errors": sorted(errors), "warnings": sorted(warnings),
            "trimmedSourceMs": trimmed, "outputDurationMs": total if ok else None,
            "parts": parts if ok else [], "audio": not p["muted"], "exportHeight": height}


# ---------------------------------------------------------------- segments
def plan_segments(sc):
    bpm = VIDEO_MBPS[sc["quality"]] * 125 + (AUDIO_BYTES_PER_MS if sc["audio"] else 0)
    low = next((s["tMs"] for s in sc["freeSpaceSamples"] if s["freeMb"] < LOW_STORAGE_MB), None)
    user = sc["stopAtMs"]
    if user is None and low is None:
        raise ValueError(f"{sc['id']}: no end")
    if low is not None and (user is None or low < user):
        end, reason = low, "low_storage"
    else:
        end, reason = user, "user"
    cap = sc["segmentMinutes"] * 60_000
    if sc["target"] == "sd":
        cap = min(cap, -(-SD_CAP_BYTES // bpm))
    stamp = datetime.fromisoformat(sc["startLocal"]).strftime("%Y%m%d_%H%M%S")
    segs, start, idx = [], 0, 1
    while start < end:
        d = min(cap, end - start)
        segs.append({"index": idx, "fileName": f"OOSCam_{stamp}_part{idx:02d}.mp4",
                     "startOffsetMs": start, "durationMs": d, "estimatedBytes": d * bpm})
        start += d
        idx += 1
    return {"id": sc["id"], "stopReason": reason, "endMs": end, "bytesPerMs": bpm, "segments": segs}


# ---------------------------------------------------------------- hand checks
def hand_checks(ex, sg):
    e = {r["id"]: r for r in ex}
    s = {r["id"]: r for r in sg}
    x1 = e["X01"]
    assert x1["ok"] and x1["outputDurationMs"] == 135_000 and x1["audio"] is False
    assert [(p["srcStartMs"], p["srcEndMs"], p["speed"], p["outDurationMs"]) for p in x1["parts"]] == [
        (2_000, 30_000, "1", 28_000), (30_000, 60_000, "2", 15_000),
        (60_000, 92_000, "1", 32_000), (1_500, 61_500, "1", 60_000)]
    assert e["X02"]["ok"] and e["X02"]["outputDurationMs"] == 500
    assert e["X03"]["errors"] == ["E6"]
    assert e["X04"]["ok"]
    assert e["X05"]["errors"] == ["E1"]
    assert e["X06"]["ok"] and e["X06"]["trimmedSourceMs"] == 1_800_000
    assert e["X07"]["errors"] == ["E2", "E5"] and e["X07"]["trimmedSourceMs"] == 1_800_001
    assert e["X08"]["errors"] == ["E5"]
    assert e["X09"]["ok"] and e["X09"]["outputDurationMs"] == 1_800_000
    assert e["X10"]["outputDurationMs"] == 48_914
    assert [p["outDurationMs"] for p in e["X10"]["parts"]][:6] == [1_000, 667, 1_000, 667, 999, 2]
    assert e["X11"]["outputDurationMs"] == 17_500
    assert e["X12"]["ok"] and e["X12"]["warnings"] == ["W1"] and e["X12"]["outputDurationMs"] == 20_000
    assert e["X13"]["errors"] == ["E3"]
    assert e["X14"]["errors"] == ["E4"]
    assert e["X15"]["exportHeight"] == 720 and e["X15"]["warnings"] == ["W2"]
    assert e["X16"]["outputDurationMs"] == 35_000 and len(e["X16"]["parts"]) == 3
    assert e["X17"]["errors"] == ["E6"]
    assert e["X18"]["errors"] == ["E7"]
    assert e["X19"]["outputDurationMs"] == 5_001 and e["X19"]["audio"] is True
    assert [p["clipOrder"] for p in e["X19"]["parts"]] == [0, 1, 2]
    assert e["X20"]["outputDurationMs"] == 60_001 and e["X20"]["exportHeight"] == 1080

    assert [g["durationMs"] for g in s["S01"]["segments"]] == [1_800_000, 1_800_000, 300_000]
    assert s["S01"]["segments"][0]["fileName"] == "OOSCam_20261125_080000_part01.mp4"
    assert len(s["S02"]["segments"]) == 2
    assert s["S03"]["stopReason"] == "low_storage" and s["S03"]["endMs"] == 30_000
    assert [g["durationMs"] for g in s["S04"]["segments"]] == [691_367, 691_367, 691_367, 385_899]
    assert s["S05"]["segments"] == [] and s["S05"]["stopReason"] == "low_storage"
    assert len(s["S06"]["segments"]) == 13 and s["S06"]["segments"][-1]["durationMs"] == 1_234
    assert len(s["S07"]["segments"]) == 1 and s["S07"]["bytesPerMs"] == 2_000
    assert s["S08"]["segments"][-1]["fileName"].endswith("_part101.mp4")
    assert s["S09"]["stopReason"] == "user" and s["S09"]["endMs"] == 12_345


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", default="base")
    args = ap.parse_args()
    src = HERE / "fixtures" / args.variant
    ex = [plan_export(p) for p in json.loads((src / "export_cases.json").read_text())]
    sg = [plan_segments(c) for c in json.loads((src / "segment_cases.json").read_text())]
    if args.variant == "base":
        hand_checks(ex, sg)
    out = HERE / "expected" / args.variant
    out.mkdir(parents=True, exist_ok=True)
    (out / "export_expected.json").write_text(json.dumps(ex, indent=2) + "\n")
    (out / "segment_expected.json").write_text(json.dumps(sg, indent=2) + "\n")
    print(f"{args.variant}: {len(ex)} export cases, {len(sg)} segment cases -> {out}")


if __name__ == "__main__":
    main()

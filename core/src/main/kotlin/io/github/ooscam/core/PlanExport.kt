package io.github.ooscam.core

import java.math.BigDecimal
import java.math.RoundingMode

private const val MAX_CLIPS = 10
private const val MIN_CLIP_MS = 500L
private const val MAX_TRIMMED_MS = 1_800_000L
private const val MAX_OUTPUT_MS = 1_800_000L
private const val FULL_HD = 1080
private const val HD = 720

private val ALLOWED_SPEEDS = listOf("0.25", "0.5", "1", "1.5", "2", "4").map { BigDecimal(it) }

/** A speed range clipped to its clip's trim window. */
private class KeptRange(val startMs: Long, val endMs: Long, val speed: BigDecimal)

/** One stretch of a clip at one speed, before rounding. */
private class Piece(val startMs: Long, var endMs: Long, val speed: BigDecimal)

/** Builds the export plan for a project. Pure function of its input. */
fun planExport(project: EditProject): ExportPlan {
    val errors = sortedSetOf<String>()
    val warnings = sortedSetOf<String>()
    val clips = project.clips.sortedBy { it.order }

    if (clips.size > MAX_CLIPS) errors.add("E1")

    var trimmed = 0L
    for (clip in clips) {
        val valid = clip.inMs >= 0 && clip.inMs < clip.outMs && clip.outMs <= clip.sourceDurationMs &&
            clip.outMs - clip.inMs >= MIN_CLIP_MS
        if (!valid) errors.add("E6")
        trimmed += maxOf(0L, clip.outMs - clip.inMs)
    }

    if (trimmed > MAX_TRIMMED_MS) errors.add("E2")

    val kept = keepRanges(clips, project.speedRanges, errors, warnings)
    for (ranges in kept.values) {
        val sorted = ranges.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        if (sorted.zipWithNext().any { (a, b) -> b.startMs < a.endMs }) errors.add("E3")
    }

    var height = project.exportHeight
    if (height == FULL_HD && clips.any { it.sourceHeight < FULL_HD }) {
        height = HD
        warnings.add("W2")
    }

    val parts = mutableListOf<PlanPart>()
    var total = 0L
    if (errors.none { it in BLOCKING }) {
        for (clip in clips) {
            val ranges = kept.getValue(clip.order).sortedWith(compareBy({ it.startMs }, { it.endMs }))
            for (piece in mergedPieces(clip, ranges)) {
                val length = roundedLength(piece.endMs - piece.startMs, piece.speed)
                parts.add(PlanPart(clip.order, piece.startMs, piece.endMs, speedText(piece.speed), total, length))
                total += length
            }
        }
        if (total > MAX_OUTPUT_MS) errors.add("E5")
    }

    val ok = errors.isEmpty()
    return ExportPlan(
        id = project.id,
        ok = ok,
        errors = errors.toList(),
        warnings = warnings.toList(),
        trimmedSourceMs = trimmed,
        outputDurationMs = if (ok) total else null,
        parts = if (ok) parts else emptyList(),
        audio = !project.muted,
        exportHeight = height,
    )
}

/** Validates ranges (E7, E4), clips them to the trim window and drops empty ones (W1). */
private fun keepRanges(
    clips: List<ProjectClip>,
    ranges: List<SpeedRange>,
    errors: MutableSet<String>,
    warnings: MutableSet<String>,
): Map<Int, MutableList<KeptRange>> {
    val byOrder = clips.associateBy { it.order }
    val kept = clips.associate { it.order to mutableListOf<KeptRange>() }
    for (r in ranges) {
        val clip = byOrder[r.clipOrder]
        if (clip == null || r.startMs >= r.endMs) {
            errors.add("E7")
            continue
        }
        if (ALLOWED_SPEEDS.none { it.compareTo(r.speed) == 0 }) {
            errors.add("E4")
            continue
        }
        val start = maxOf(r.startMs, clip.inMs)
        val end = minOf(r.endMs, clip.outMs)
        if (start >= end) {
            warnings.add("W1")
            continue
        }
        kept.getValue(clip.order).add(KeptRange(start, end, r.speed))
    }
    return kept
}

/** Fills the gaps between ranges with 1x pieces, then merges touching pieces of equal speed. */
private fun mergedPieces(clip: ProjectClip, ranges: List<KeptRange>): List<Piece> {
    val pieces = mutableListOf<Piece>()
    var cursor = clip.inMs
    for (r in ranges) {
        if (cursor < r.startMs) pieces.add(Piece(cursor, r.startMs, BigDecimal.ONE))
        pieces.add(Piece(r.startMs, r.endMs, r.speed))
        cursor = r.endMs
    }
    if (cursor < clip.outMs) pieces.add(Piece(cursor, clip.outMs, BigDecimal.ONE))

    val merged = mutableListOf<Piece>()
    for (p in pieces) {
        val last = merged.lastOrNull()
        if (last != null && last.speed.compareTo(p.speed) == 0 && last.endMs == p.startMs) {
            last.endMs = p.endMs
        } else {
            merged.add(p)
        }
    }
    return merged
}

/** Output length of a source stretch: round half up of length / speed, exact decimal math. */
private fun roundedLength(lengthMs: Long, speed: BigDecimal): Long =
    BigDecimal(lengthMs).divide(speed, 0, RoundingMode.HALF_UP).longValueExact()

private fun speedText(speed: BigDecimal): String = speed.stripTrailingZeros().toPlainString()

/** Errors that stop the plan from being built (E5 is computed from the built plan). */
private val BLOCKING = setOf("E3", "E4", "E6", "E7")

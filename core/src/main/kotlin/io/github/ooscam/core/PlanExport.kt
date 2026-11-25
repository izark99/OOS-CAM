package io.github.ooscam.core

private const val MAX_CLIPS = 10
private const val MIN_CLIP_MS = 500L

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

    val parts = mutableListOf<PlanPart>()
    var total = 0L
    if (errors.none { it in BLOCKING }) {
        for (clip in clips) {
            val length = clip.outMs - clip.inMs
            parts.add(PlanPart(clip.order, clip.inMs, clip.outMs, "1", total, length))
            total += length
        }
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
        exportHeight = project.exportHeight,
    )
}

/** Errors that stop the plan from being built (E5 is computed from the built plan). */
private val BLOCKING = setOf("E3", "E4", "E6", "E7")

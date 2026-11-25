package io.github.ooscam.editor

import io.github.ooscam.core.EditProject
import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import io.github.ooscam.core.planExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

/** A video the user picked for the project (from the library or the photo picker). */
data class ClipSource(val uri: String, val displayName: String, val durationMs: Long, val height: Int)

/** One validation line under the timeline: a planExport error (blocks export) or warning. */
data class EditorMessage(val code: String, val error: Boolean)

data class EditorUiState(val project: EditProject, val plan: ExportPlan, val messages: List<EditorMessage>) {
    val canExport: Boolean get() = plan.ok && project.clips.isNotEmpty()
}

/**
 * Holds the EditProject being edited. Every change re-runs :core planExport, so the messages
 * and the export button always reflect exactly what the export would do.
 */
class EditorViewModel(initial: EditProject) {
    constructor(projectId: String) : this(EditProject(projectId, muted = false, exportHeight = DEFAULT_HEIGHT, clips = emptyList(), speedRanges = emptyList()))

    private val _state = MutableStateFlow(stateFor(initial))
    val state: StateFlow<EditorUiState> = _state

    private val project get() = _state.value.project

    fun addClips(sources: List<ClipSource>) {
        val start = project.clips.size
        val added = sources.mapIndexed { i, s -> ProjectClip(start + i, s.uri, s.durationMs, s.height, 0, s.durationMs) }
        update(project.copy(clips = sorted() + added))
    }

    /** Trim handles move in 0.1 s steps; the end of the source is always reachable. */
    fun setTrim(order: Int, inMs: Long, outMs: Long) = updateClip(order) { clip ->
        clip.copy(inMs = snap(inMs, clip.sourceDurationMs), outMs = snap(outMs, clip.sourceDurationMs))
    }

    fun nudgeIn(order: Int, steps: Int) = clip(order)?.let { setTrim(order, it.inMs + steps * STEP_MS, it.outMs) }

    fun nudgeOut(order: Int, steps: Int) = clip(order)?.let { setTrim(order, it.inMs, it.outMs + steps * STEP_MS) }

    /** Drag to reorder: the clip moves from position [from] to [to]; its speed ranges follow it. */
    fun move(from: Int, to: Int) {
        val clips = sorted().toMutableList()
        if (from !in clips.indices || to !in clips.indices || from == to) return
        clips.add(to, clips.removeAt(from))
        renumber(clips, project.speedRanges)
    }

    fun removeClip(order: Int) {
        val clips = sorted().filter { it.order != order }
        renumber(clips, project.speedRanges.filter { it.clipOrder != order })
    }

    fun addSpeedRange(order: Int, startMs: Long, endMs: Long, speed: BigDecimal) {
        val clip = clip(order) ?: return
        update(project.copy(speedRanges = project.speedRanges + range(clip, startMs, endMs, speed)))
    }

    fun updateSpeedRange(index: Int, startMs: Long, endMs: Long, speed: BigDecimal) {
        val old = project.speedRanges.getOrNull(index) ?: return
        val clip = clip(old.clipOrder) ?: return
        update(project.copy(speedRanges = project.speedRanges.toMutableList().also { it[index] = range(clip, startMs, endMs, speed) }))
    }

    fun removeSpeedRange(index: Int) {
        if (index !in project.speedRanges.indices) return
        update(project.copy(speedRanges = project.speedRanges.filterIndexed { i, _ -> i != index }))
    }

    fun setMuted(muted: Boolean) = update(project.copy(muted = muted))

    fun setExportHeight(height: Int) = update(project.copy(exportHeight = height))

    private fun range(clip: ProjectClip, startMs: Long, endMs: Long, speed: BigDecimal) =
        SpeedRange(clip.order, snap(startMs, clip.sourceDurationMs), snap(endMs, clip.sourceDurationMs), speed)

    private fun renumber(clips: List<ProjectClip>, ranges: List<SpeedRange>) {
        val newOrder = clips.mapIndexed { i, c -> c.order to i }.toMap()
        update(
            project.copy(
                clips = clips.mapIndexed { i, c -> c.copy(order = i) },
                speedRanges = ranges.mapNotNull { r -> newOrder[r.clipOrder]?.let { r.copy(clipOrder = it) } },
            ),
        )
    }

    private fun clip(order: Int) = project.clips.firstOrNull { it.order == order }

    private fun sorted() = project.clips.sortedBy { it.order }

    private fun updateClip(order: Int, change: (ProjectClip) -> ProjectClip) {
        if (clip(order) == null) return
        update(project.copy(clips = project.clips.map { if (it.order == order) change(it) else it }))
    }

    private fun update(next: EditProject) {
        _state.value = stateFor(next)
    }

    companion object {
        const val STEP_MS = 100L
        const val DEFAULT_HEIGHT = 1080

        /** Nearest 0.1 s (half up) or the exact source end, whichever is closer; kept inside [0, duration]. */
        fun snap(ms: Long, durationMs: Long): Long {
            if (ms >= durationMs) return durationMs
            if (ms <= 0) return 0
            val grid = minOf(durationMs, (ms + STEP_MS / 2) / STEP_MS * STEP_MS)
            return if (durationMs - ms < kotlin.math.abs(grid - ms)) durationMs else grid
        }

        private fun stateFor(project: EditProject): EditorUiState {
            val plan = planExport(project)
            val messages = plan.errors.map { EditorMessage(it, error = true) } + plan.warnings.map { EditorMessage(it, error = false) }
            return EditorUiState(project, plan, messages)
        }
    }
}

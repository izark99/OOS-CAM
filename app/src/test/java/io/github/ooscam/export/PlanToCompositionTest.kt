package io.github.ooscam.export

import androidx.media3.common.C
import io.github.ooscam.core.EditProject
import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.PlanPart
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import io.github.ooscam.core.planExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class PlanToCompositionTest {
    private fun project(vararg ranges: SpeedRange, muted: Boolean = false) = EditProject(
        "p", muted = muted, exportHeight = 1080,
        clips = listOf(ProjectClip(1, "content://m/b", 20_000, 1080, 0, 20_000), ProjectClip(0, "content://m/a", 30_000, 1080, 1_000, 29_000)),
        speedRanges = ranges.toList(),
    )

    @Test
    fun everyPartBoundaryOfTheCorePlan_isAnItemBoundary() {
        val project = project(SpeedRange(0, 5_000, 10_000, BigDecimal("4")), SpeedRange(1, 0, 3_000, BigDecimal("0.5")))
        val plan = planExport(project)
        val items = CompositionMapper.map(plan, CompositionMapper.sourceUris(project)).sequences.single().editedMediaItems
        assertEquals(plan.parts.map { Triple(it.srcStartMs, it.srcEndMs, it.speed.toFloat()) }, items.map {
            Triple(it.mediaItem.clippingConfiguration.startPositionMs, it.mediaItem.clippingConfiguration.endPositionMs, it.speedProvider.getSpeed(0))
        })
        assertEquals(plan.parts.map { CompositionMapper.sourceUris(project)[it.clipOrder] }, items.map { it.mediaItem.localConfiguration!!.uri.toString() })
    }

    @Test
    fun allSixSpeedTexts_mapToTheirExactFloat() {
        val speeds = listOf("0.25", "0.5", "1", "1.5", "2", "4")
        val plan = ExportPlan("p", true, emptyList(), emptyList(), 0, 0, speeds.mapIndexed { i, s -> PlanPart(0, i * 1_000L, i * 1_000L + 1_000, s, 0, 0) }, false, 720)
        val items = CompositionMapper.map(plan, mapOf(0 to "content://m/a")).sequences.single().editedMediaItems
        assertEquals(listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f), items.map { it.speedProvider.getSpeed(123_456) })
    }

    @Test
    fun unmutedProject_keepsAudio_mutedProjectDropsIt() {
        val loud = CompositionMapper.map(planExport(project()), CompositionMapper.sourceUris(project()))
        val quiet = CompositionMapper.map(planExport(project(muted = true)), CompositionMapper.sourceUris(project()))
        assertEquals(listOf(false, false), loud.sequences.single().editedMediaItems.map { it.removeAudio })
        assertEquals(listOf(true, true), quiet.sequences.single().editedMediaItems.map { it.removeAudio })
        assertEquals(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), loud.sequences.single().trackTypes)
        assertEquals(setOf(C.TRACK_TYPE_VIDEO), quiet.sequences.single().trackTypes)
    }

    @Test
    fun okPlanWithoutParts_isRefused() {
        val empty = ExportPlan("p", true, emptyList(), emptyList(), 0, 0, emptyList(), false, 720)
        assertThrows(IllegalArgumentException::class.java) { CompositionMapper.map(empty, emptyMap()) }
    }
}

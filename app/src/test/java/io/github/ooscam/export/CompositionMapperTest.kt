package io.github.ooscam.export

import androidx.media3.common.C
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import io.github.ooscam.core.EditProject
import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.PlanPart
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import io.github.ooscam.core.planExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class CompositionMapperTest {
    private val uris = mapOf(0 to "content://m/a", 1 to "content://m/b")

    private fun plan(audio: Boolean = false, height: Int = 1080, parts: List<PlanPart>) =
        ExportPlan("p", ok = true, errors = emptyList(), warnings = emptyList(), trimmedSourceMs = 0, outputDurationMs = parts.sumOf { it.outDurationMs }, parts = parts, audio = audio, exportHeight = height)

    private fun items(c: Composition) = c.sequences.single().editedMediaItems

    private fun outputHeight(c: Composition): Int {
        val presentation = c.effects.videoEffects.filterIsInstance<Presentation>().single()
        return presentation.configure(3840, 2160).height
    }

    @Test
    fun eachPlanPart_becomesOneClippedItem_inPlanOrder() {
        val c = CompositionMapper.map(
            plan(parts = listOf(PlanPart(1, 1_500, 61_500, "1", 0, 60_000), PlanPart(0, 30_000, 60_000, "2", 60_000, 15_000))),
            uris,
        )
        val items = items(c)
        assertEquals(listOf("content://m/b", "content://m/a"), items.map { it.mediaItem.localConfiguration!!.uri.toString() })
        assertEquals(listOf(1_500L to 61_500L, 30_000L to 60_000L), items.map { it.mediaItem.clippingConfiguration.startPositionMs to it.mediaItem.clippingConfiguration.endPositionMs })
    }

    @Test
    fun partSpeed_isTheItemSpeedForAudioAndVideo() {
        val c = CompositionMapper.map(
            plan(parts = listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000), PlanPart(0, 1_000, 2_000, "0.25", 1_000, 4_000), PlanPart(0, 2_000, 3_000, "1.5", 5_000, 667))),
            uris,
        )
        assertEquals(listOf(1f, 0.25f, 1.5f), items(c).map { it.speedProvider.getSpeed(0) })
        assertEquals(C.TIME_UNSET, items(c)[1].speedProvider.getNextSpeedChangeTimeUs(0))
    }

    @Test
    fun mutedPlan_removesAudioEverywhere_andHasAVideoOnlySequence() {
        val c = CompositionMapper.map(plan(audio = false, parts = listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000))), uris)
        assertTrue(items(c).all { it.removeAudio })
        assertEquals(setOf(C.TRACK_TYPE_VIDEO), c.sequences.single().trackTypes)
    }

    @Test
    fun planWithAudio_keepsAudio_andDeclaresAnAudioTrackForTheWholeSequence() {
        val c = CompositionMapper.map(plan(audio = true, parts = listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000))), uris)
        assertFalse(items(c).single().removeAudio)
        assertEquals(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), c.sequences.single().trackTypes)
    }

    @Test
    fun exportHeight_comesFromThePlan() {
        assertEquals(1080, outputHeight(CompositionMapper.map(plan(height = 1080, parts = listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000))), uris)))
        assertEquals(720, outputHeight(CompositionMapper.map(plan(height = 720, parts = listOf(PlanPart(0, 0, 1_000, "1", 0, 1_000))), uris)))
    }

    @Test
    fun compositionFollowsThePlan_notTheProject() {
        // The project says 2x on 30-60 s and unmuted 1080p; the plan given to the mapper says otherwise.
        val project = EditProject(
            "p", muted = false, exportHeight = 1080,
            clips = listOf(ProjectClip(0, "content://m/a", 95_000, 1080, 2_000, 92_000)),
            speedRanges = listOf(SpeedRange(0, 30_000, 60_000, BigDecimal("2"))),
        )
        val other = plan(audio = false, height = 720, parts = listOf(PlanPart(0, 10_000, 20_000, "4", 0, 2_500)))
        val c = CompositionMapper.map(other, CompositionMapper.sourceUris(project))
        val item = items(c).single()
        assertEquals(10_000L to 20_000L, item.mediaItem.clippingConfiguration.startPositionMs to item.mediaItem.clippingConfiguration.endPositionMs)
        assertEquals(4f, item.speedProvider.getSpeed(0))
        assertTrue(item.removeAudio)
        assertEquals(720, outputHeight(c))
    }

    @Test
    fun x01ShapedProject_mapsToFourItems() {
        val project = EditProject(
            "p", muted = true, exportHeight = 1080,
            clips = listOf(ProjectClip(0, "content://m/a", 95_000, 1080, 2_000, 92_000), ProjectClip(1, "content://m/b", 62_500, 1080, 1_500, 61_500)),
            speedRanges = listOf(SpeedRange(0, 30_000, 60_000, BigDecimal("2"))),
        )
        val c = CompositionMapper.map(planExport(project), CompositionMapper.sourceUris(project))
        assertEquals(listOf(1f, 2f, 1f, 1f), items(c).map { it.speedProvider.getSpeed(0) })
    }

    @Test
    fun planWithErrors_isRefused() {
        val bad = plan(parts = emptyList()).copy(ok = false, errors = listOf("E6"), outputDurationMs = null)
        assertThrows(IllegalArgumentException::class.java) { CompositionMapper.map(bad, uris) }
    }

    @Test
    fun partWithoutASourceUri_isRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            CompositionMapper.map(plan(parts = listOf(PlanPart(7, 0, 1_000, "1", 0, 1_000))), uris)
        }
    }
}

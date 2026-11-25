package io.github.ooscam.editor

import io.github.ooscam.core.planExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EditorViewModelTest {
    private val vm = EditorViewModel(projectId = "p1")

    private fun source(name: String, durationMs: Long, height: Int = 1080) = ClipSource("content://m/$name", name, durationMs, height)

    private val project get() = vm.state.value.project

    @Test
    fun newProject_isEmpty_unmuted_1080_andCannotExport() {
        assertEquals("p1", project.id)
        assertTrue(project.clips.isEmpty())
        assertFalse(project.muted)
        assertEquals(1080, project.exportHeight)
        assertFalse(vm.state.value.canExport)
    }

    @Test
    fun addedClips_takeNextOrders_andFullTrim() {
        vm.addClips(listOf(source("a", 95_000), source("b", 62_537, height = 720)))
        assertEquals(listOf(0, 1), project.clips.map { it.order })
        assertEquals(listOf(0L to 95_000L, 0L to 62_537L), project.clips.map { it.inMs to it.outMs })
        assertEquals(720, project.clips[1].sourceHeight)
        assertTrue(vm.state.value.canExport)
    }

    @Test
    fun trim_snapsToTenthsOfASecond_andStaysInsideTheSource() {
        vm.addClips(listOf(source("a", 62_537)))
        vm.setTrim(0, inMs = 1_549, outMs = 61_551)
        assertEquals(1_500L to 61_600L, project.clips[0].inMs to project.clips[0].outMs)
        vm.setTrim(0, inMs = -300, outMs = 99_999)
        assertEquals(0L to 62_537L, project.clips[0].inMs to project.clips[0].outMs)
    }

    @Test
    fun nudge_movesTrimHandlesBySteps() {
        vm.addClips(listOf(source("a", 10_000)))
        vm.nudgeIn(0, +3)
        vm.nudgeOut(0, -12)
        assertEquals(300L to 8_800L, project.clips[0].inMs to project.clips[0].outMs)
        vm.nudgeOut(0, +50)
        assertEquals(10_000L, project.clips[0].outMs)
    }

    @Test
    fun reorder_movesClipAndItsSpeedRanges() {
        vm.addClips(listOf(source("a", 10_000), source("b", 10_000), source("c", 10_000)))
        vm.addSpeedRange(0, 1_000, 2_000, BigDecimal("2"))
        vm.addSpeedRange(2, 3_000, 4_000, BigDecimal("0.5"))
        vm.move(from = 0, to = 2)
        assertEquals(listOf("b", "c", "a"), project.clips.sortedBy { it.order }.map { it.sourceUri.substringAfterLast('/') })
        assertEquals(setOf(2 to "2", 1 to "0.5"), project.speedRanges.map { it.clipOrder to it.speed.toPlainString() }.toSet())
    }

    @Test
    fun removeClip_dropsItsRanges_andRenumbersTheRest() {
        vm.addClips(listOf(source("a", 10_000), source("b", 10_000)))
        vm.addSpeedRange(0, 1_000, 2_000, BigDecimal("2"))
        vm.addSpeedRange(1, 1_000, 2_000, BigDecimal("4"))
        vm.removeClip(0)
        assertEquals(listOf(0), project.clips.map { it.order })
        assertEquals(listOf(0 to "4"), project.speedRanges.map { it.clipOrder to it.speed.toPlainString() })
    }

    @Test
    fun speedRange_isSnapped_andCanBeChangedOrRemoved() {
        vm.addClips(listOf(source("a", 95_000)))
        vm.addSpeedRange(0, 30_040, 59_960, BigDecimal("2"))
        assertEquals(30_000L to 60_000L, project.speedRanges[0].startMs to project.speedRanges[0].endMs)
        vm.updateSpeedRange(0, startMs = 31_000, endMs = 61_000, speed = BigDecimal("1.5"))
        assertEquals("1.5", project.speedRanges[0].speed.toPlainString())
        assertEquals(31_000L, project.speedRanges[0].startMs)
        vm.removeSpeedRange(0)
        assertTrue(project.speedRanges.isEmpty())
    }

    @Test
    fun muteAndExportHeight_goIntoTheProject() {
        vm.setMuted(true)
        vm.setExportHeight(720)
        assertTrue(project.muted)
        assertEquals(720, project.exportHeight)
    }

    @Test
    fun plan_isPlanExportOfTheProject() {
        vm.addClips(listOf(source("a", 95_000)))
        vm.addSpeedRange(0, 30_000, 60_000, BigDecimal("2"))
        assertEquals(planExport(project), vm.state.value.plan)
        assertEquals(80_000L, vm.state.value.plan.outputDurationMs)
    }

    @Test
    fun shortTrim_showsE6_andBlocksExport() {
        vm.addClips(listOf(source("a", 10_000)))
        vm.setTrim(0, 1_000, 1_400)
        assertEquals(listOf(EditorMessage("E6", error = true)), vm.state.value.messages)
        assertFalse(vm.state.value.canExport)
    }

    @Test
    fun rangeOutsideTrim_warnsW1_butExportStaysAllowed() {
        vm.addClips(listOf(source("a", 10_000)))
        vm.setTrim(0, 5_000, 10_000)
        vm.addSpeedRange(0, 1_000, 2_000, BigDecimal("2"))
        assertEquals(listOf(EditorMessage("W1", error = false)), vm.state.value.messages)
        assertTrue(vm.state.value.canExport)
    }

    @Test
    fun overlappingRanges_showE3_and720pSourceWarnsW2() {
        vm.addClips(listOf(source("a", 10_000, height = 720)))
        vm.addSpeedRange(0, 1_000, 3_000, BigDecimal("2"))
        vm.addSpeedRange(0, 2_000, 4_000, BigDecimal("4"))
        assertEquals(listOf(EditorMessage("E3", true), EditorMessage("W2", false)), vm.state.value.messages)
    }

    @Test
    fun elevenClips_showE1() {
        vm.addClips((0..10).map { source("c$it", 1_000) })
        assertTrue(EditorMessage("E1", true) in vm.state.value.messages)
    }

    @Test
    fun restoredProject_isUsedAsIs() {
        val other = EditorViewModel(projectId = "p2")
        other.addClips(listOf(source("a", 5_000)))
        val copy = EditorViewModel(other.state.value.project)
        assertEquals(other.state.value.project, copy.state.value.project)
    }
}

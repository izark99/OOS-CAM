package io.github.ooscam.editor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.core.planExport
import io.github.ooscam.data.OOSCamDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class EditorEdgeCasesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(app, OOSCamDatabase::class.java).allowMainThreadQueries().build()
    private val vm = EditorViewModel(projectId = "p")
    private val project get() = vm.state.value.project

    @After
    fun tearDown() = db.close()

    private fun source(name: String, durationMs: Long) = ClipSource("content://m/$name", name, durationMs, 1080)

    @Test
    fun outOfRangeOrUnknownTargets_changeNothing() {
        vm.addClips(listOf(source("a", 5_000), source("b", 5_000)))
        val before = project
        vm.move(from = 0, to = 5)
        vm.move(from = -1, to = 0)
        vm.move(from = 1, to = 1)
        vm.nudgeIn(order = 9, steps = 1)
        vm.setTrim(order = 9, inMs = 0, outMs = 1_000)
        vm.addSpeedRange(order = 9, startMs = 0, endMs = 1_000, speed = BigDecimal("2"))
        vm.updateSpeedRange(index = 3, startMs = 0, endMs = 1_000, speed = BigDecimal("2"))
        vm.removeSpeedRange(index = 0)
        assertEquals(before, project)
    }

    @Test
    fun addingAfterRemove_keepsOrdersContiguous() {
        vm.addClips(listOf(source("a", 5_000), source("b", 5_000), source("c", 5_000)))
        vm.removeClip(1)
        vm.addClips(listOf(source("d", 5_000)))
        assertEquals(listOf(0, 1, 2), project.clips.map { it.order })
        assertEquals(listOf("a", "c", "d"), project.clips.map { it.sourceUri.substringAfterLast('/') })
    }

    @Test
    fun reorderingKeepsTheTrimOfEachClip() {
        vm.addClips(listOf(source("a", 5_000), source("b", 8_000)))
        vm.setTrim(0, 1_000, 4_000)
        vm.move(0, 1)
        val a = project.clips.single { it.sourceUri.endsWith("/a") }
        assertEquals(Triple(1, 1_000L, 4_000L), Triple(a.order, a.inMs, a.outMs))
    }

    @Test
    fun everyOfferedSpeed_isAcceptedByPlanExport() {
        vm.addClips(listOf(source("a", 60_000)))
        EDITOR_SPEEDS.forEachIndexed { i, s -> vm.addSpeedRange(0, i * 5_000L, i * 5_000L + 4_000, s) }
        assertTrue(vm.state.value.messages.toString(), vm.state.value.plan.ok)
        assertEquals(listOf("0.25", "0.5", "1", "1.5", "2", "4"), EDITOR_SPEEDS.map { it.toPlainString() })
    }

    @Test
    fun savedAndRestoredProject_givesTheSamePlan() {
        vm.addClips(listOf(source("a", 95_000), source("b", 62_500)))
        vm.setTrim(0, 2_000, 92_000)
        vm.addSpeedRange(0, 30_000, 60_000, BigDecimal("1.5"))
        vm.setMuted(true)
        db.projectDao().save(project, name = "n", now = 1)
        val restored = EditorViewModel(db.projectDao().load("p")!!)
        assertEquals(vm.state.value.plan, restored.state.value.plan)
        assertEquals(planExport(project), restored.state.value.plan)
    }

    @Test
    fun snap_roundsHalfUpToTenths_andClampsToTheSource() {
        assertEquals(100L, EditorViewModel.snap(50, 1_000))
        assertEquals(0L, EditorViewModel.snap(49, 1_000))
        assertEquals(1_000L, EditorViewModel.snap(999, 1_000))
        assertEquals(950L, EditorViewModel.snap(951, 950))
        assertEquals(900L, EditorViewModel.snap(920, 950))
        assertEquals(950L, EditorViewModel.snap(946, 950))
    }
}

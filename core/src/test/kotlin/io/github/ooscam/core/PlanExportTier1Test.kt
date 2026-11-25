package io.github.ooscam.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanExportTier1Test {
    @Test
    fun planExport_clipsOutOfOrder_partsFollowClipOrderAscending() {
        val plan = planExport(
            project(listOf(clip(2, outMs = 3_000), clip(0, outMs = 1_000), clip(1, outMs = 2_000))),
        )
        assertTrue(plan.ok)
        assertEquals(listOf(0, 1, 2), plan.parts.map { it.clipOrder })
        assertEquals(listOf(0L, 1_000L, 3_000L), plan.parts.map { it.outStartMs })
        assertEquals(6_000L, plan.outputDurationMs)
    }

    @Test
    fun planExport_trimmedClips_reportSumOfTrimmedLengthsAndCopySourceRange() {
        val plan = planExport(project(listOf(clip(0, inMs = 700, outMs = 2_700), clip(1, inMs = 50, outMs = 1_050))))
        assertEquals(3_000L, plan.trimmedSourceMs)
        assertEquals(700L, plan.parts[0].srcStartMs)
        assertEquals(2_700L, plan.parts[0].srcEndMs)
        assertEquals("1", plan.parts[0].speed)
        assertEquals(3_000L, plan.outputDurationMs)
    }

    @Test
    fun planExport_clipOfExactly500ms_isAccepted() {
        val plan = planExport(project(listOf(clip(0, inMs = 10, outMs = 510))))
        assertTrue(plan.ok)
        assertEquals(500L, plan.outputDurationMs)
    }

    @Test
    fun planExport_clipOf499ms_isE6() {
        val plan = planExport(project(listOf(clip(0, inMs = 10, outMs = 509))))
        assertEquals(listOf("E6"), plan.errors)
    }

    @Test
    fun planExport_negativeIn_isE6() {
        assertEquals(listOf("E6"), planExport(project(listOf(clip(0, inMs = -1, outMs = 900)))).errors)
    }

    @Test
    fun planExport_outPastSource_isE6() {
        assertEquals(listOf("E6"), planExport(project(listOf(clip(0, durationMs = 1_000, inMs = 0, outMs = 1_001)))).errors)
    }

    @Test
    fun planExport_inEqualsOut_isE6() {
        assertEquals(listOf("E6"), planExport(project(listOf(clip(0, inMs = 800, outMs = 800)))).errors)
    }

    @Test
    fun planExport_errorResult_hasNoPartsNullDurationButReportsTrimmedSum() {
        val plan = planExport(project(listOf(clip(0, inMs = 0, outMs = 400), clip(1, inMs = 0, outMs = 2_000))))
        assertFalse(plan.ok)
        assertTrue(plan.parts.isEmpty())
        assertNull(plan.outputDurationMs)
        assertEquals(2_400L, plan.trimmedSourceMs)
    }

    @Test
    fun planExport_invertedClip_countsZeroInTrimmedSum() {
        val plan = planExport(project(listOf(clip(0, inMs = 900, outMs = 100), clip(1, outMs = 1_000))))
        assertEquals(1_000L, plan.trimmedSourceMs)
        assertEquals(listOf("E6"), plan.errors)
    }

    @Test
    fun planExport_tenClips_pass() {
        val plan = planExport(project((0..9).map { clip(it, outMs = 1_000) }))
        assertTrue(plan.ok)
        assertEquals(10, plan.parts.size)
    }

    @Test
    fun planExport_elevenClips_isE1() {
        val plan = planExport(project((0..10).map { clip(it, outMs = 1_000) }))
        assertEquals(listOf("E1"), plan.errors)
        assertFalse(plan.ok)
    }

    @Test
    fun planExport_audioFollowsMuted() {
        assertTrue(planExport(project(listOf(clip(0)), muted = false)).audio)
        assertFalse(planExport(project(listOf(clip(0)), muted = true)).audio)
    }
}

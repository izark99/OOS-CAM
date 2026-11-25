package io.github.ooscam.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanExportTier3Test {
    private val limit = 1_800_000L

    @Test
    fun planExport_trimmedExactlyAtLimit_passes() {
        val p = planExport(project(listOf(clip(0, durationMs = 900_000), clip(1, durationMs = 900_000))))
        assertTrue(p.ok)
        assertEquals(limit, p.trimmedSourceMs)
        assertEquals(limit, p.outputDurationMs)
    }

    @Test
    fun planExport_trimmedOneOverLimit_isE2AndE5() {
        val p = planExport(project(listOf(clip(0, durationMs = 900_000), clip(1, durationMs = 900_001))))
        assertEquals(listOf("E2", "E5"), p.errors)
        assertEquals(limit + 1, p.trimmedSourceMs)
        assertNull(p.outputDurationMs)
    }

    @Test
    fun planExport_slowRangeMakesOutputOneOverLimit_isE5Only() {
        val c = clip(0, durationMs = 500_000, inMs = 0, outMs = 450_001)
        val p = planExport(project(listOf(c), listOf(range(0, 0, 450_001, "0.25"))))
        assertEquals(listOf("E5"), p.errors)
        assertEquals(450_001L, p.trimmedSourceMs)
    }

    @Test
    fun planExport_slowRangeMakesOutputExactlyLimit_passes() {
        val c = clip(0, durationMs = 500_000, inMs = 0, outMs = 450_000)
        val p = planExport(project(listOf(c), listOf(range(0, 0, 450_000, "0.25"))))
        assertTrue(p.ok)
        assertEquals(limit, p.outputDurationMs)
    }

    @Test
    fun planExport_e5IsNotCheckedWhenAnotherErrorBlocksThePlan() {
        val c = clip(0, durationMs = 500_000, inMs = 0, outMs = 450_001)
        val p = planExport(project(listOf(c), listOf(range(0, 0, 450_001, "0.25"), range(0, 10, 20, "3"))))
        assertEquals(listOf("E4"), p.errors)
    }

    @Test
    fun planExport_fastRangeBringsOutputUnderLimitButTrimmedStillTooLong() {
        val c = clip(0, durationMs = 2_000_000, inMs = 0, outMs = 1_800_001)
        val p = planExport(project(listOf(c), listOf(range(0, 0, 1_800_001, "2"))))
        assertEquals(listOf("E2"), p.errors)
    }

    @Test
    fun planExport_sourceBelow1080_dropsHeightTo720WithW2() {
        val p = planExport(project(listOf(clip(0, height = 1080), clip(1, height = 720)), height = 1080))
        assertEquals(720, p.exportHeight)
        assertEquals(listOf("W2"), p.warnings)
    }

    @Test
    fun planExport_requested720_staysWithoutWarning() {
        val p = planExport(project(listOf(clip(0, height = 720)), height = 720))
        assertEquals(720, p.exportHeight)
        assertEquals(emptyList<String>(), p.warnings)
    }

    @Test
    fun planExport_4kSources_stayAt1080WithoutWarning() {
        val p = planExport(project(listOf(clip(0, height = 2160), clip(1, height = 1080)), height = 1080))
        assertEquals(1080, p.exportHeight)
        assertEquals(emptyList<String>(), p.warnings)
    }

    @Test
    fun planExport_heightAndAudioAreReportedEvenWhenThereAreErrors() {
        val p = planExport(project(listOf(clip(0, height = 720, inMs = 0, outMs = 10)), muted = true, height = 1080))
        assertFalse(p.ok)
        assertEquals(720, p.exportHeight)
        assertEquals(listOf("W2"), p.warnings)
        assertFalse(p.audio)
    }

    @Test
    fun planExport_warningsDoNotBlock() {
        val c = clip(0, height = 720, inMs = 1_000, outMs = 5_000)
        val p = planExport(project(listOf(c), listOf(range(0, 6_000, 7_000, "2"))))
        assertTrue(p.ok)
        assertEquals(listOf("W1", "W2"), p.warnings)
    }

    @Test
    fun planExport_anyError_givesNotOkEmptyPartsNullOutput() {
        val p = planExport(project((0..10).map { clip(it, outMs = 1_000) }))
        assertFalse(p.ok)
        assertTrue(p.parts.isEmpty())
        assertNull(p.outputDurationMs)
        assertEquals(11_000L, p.trimmedSourceMs)
    }

    @Test
    fun planExport_noClips_isOkWithEmptyOutput() {
        val p = planExport(project(emptyList()))
        assertTrue(p.ok)
        assertEquals(0L, p.outputDurationMs)
        assertEquals(1080, p.exportHeight)
    }
}

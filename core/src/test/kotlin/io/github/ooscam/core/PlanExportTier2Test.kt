package io.github.ooscam.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanExportTier2Test {
    private fun plan(clips: List<ProjectClip>, vararg ranges: SpeedRange) = planExport(project(clips, ranges.toList()))

    @Test
    fun planExport_rangeInsideClip_splitsIntoBeforeRangeAfter() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 9_000)), range(0, 3_000, 5_000, "2"))
        assertEquals(
            listOf(
                PlanPart(0, 1_000, 3_000, "1", 0, 2_000),
                PlanPart(0, 3_000, 5_000, "2", 2_000, 1_000),
                PlanPart(0, 5_000, 9_000, "1", 3_000, 4_000),
            ),
            p.parts,
        )
        assertEquals(7_000L, p.outputDurationMs)
    }

    @Test
    fun planExport_rangeSticksOutOfTrimWindow_isClippedToWindow() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 5_000)), range(0, 0, 2_000, "4"), range(0, 4_000, 90_000, "0.5"))
        assertEquals(
            listOf(
                PlanPart(0, 1_000, 2_000, "4", 0, 250),
                PlanPart(0, 2_000, 4_000, "1", 250, 2_000),
                PlanPart(0, 4_000, 5_000, "0.5", 2_250, 2_000),
            ),
            p.parts,
        )
        assertEquals(emptyList<String>(), p.warnings)
    }

    @Test
    fun planExport_rangeEntirelyOutsideWindow_isDroppedWithW1() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 5_000)), range(0, 5_000, 8_000, "2"), range(0, 0, 400, "2"))
        assertTrue(p.ok)
        assertEquals(listOf("W1"), p.warnings)
        assertEquals(1, p.parts.size)
    }

    @Test
    fun planExport_rangeTouchingTrimEdge_isDroppedWithW1() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 5_000)), range(0, 0, 1_000, "2"))
        assertEquals(listOf("W1"), p.warnings)
        assertEquals(1, p.parts.size)
    }

    @Test
    fun planExport_emptyRange_isE7() {
        assertEquals(listOf("E7"), plan(listOf(clip(0)), range(0, 500, 500, "2")).errors)
        assertEquals(listOf("E7"), plan(listOf(clip(0)), range(0, 600, 500, "2")).errors)
    }

    @Test
    fun planExport_rangeOfUnknownClip_isE7() {
        assertEquals(listOf("E7"), plan(listOf(clip(0)), range(5, 0, 500, "2")).errors)
    }

    @Test
    fun planExport_emptyRangeWithBadSpeed_isE7NotE4() {
        assertEquals(listOf("E7"), plan(listOf(clip(0)), range(0, 500, 500, "3")).errors)
    }

    @Test
    fun planExport_unsupportedSpeed_isE4() {
        assertEquals(listOf("E4"), plan(listOf(clip(0)), range(0, 0, 500, "3")).errors)
        assertEquals(listOf("E4"), plan(listOf(clip(0)), range(0, 0, 500, "0.3")).errors)
    }

    @Test
    fun planExport_unsupportedSpeedOnRangeOutsideWindow_isStillE4() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 5_000)), range(0, 6_000, 7_000, "3"))
        assertEquals(listOf("E4"), p.errors)
    }

    @Test
    fun planExport_overlapByOneMs_isE3() {
        val p = plan(listOf(clip(0)), range(0, 1_000, 2_001, "2"), range(0, 2_000, 3_000, "4"))
        assertEquals(listOf("E3"), p.errors)
    }

    @Test
    fun planExport_rangesTouching_isNotAnOverlap() {
        val p = plan(listOf(clip(0)), range(0, 1_000, 2_000, "2"), range(0, 2_000, 3_000, "4"))
        assertTrue(p.ok)
    }

    @Test
    fun planExport_overlapOnlyOutsideWindowAfterDropping_isNotE3() {
        val p = plan(listOf(clip(0, inMs = 1_000, outMs = 5_000)), range(0, 0, 500, "2"), range(0, 100, 900, "4"))
        assertTrue(p.ok)
        assertEquals(listOf("W1"), p.warnings)
    }

    @Test
    fun planExport_overlapAndDroppedRange_reportsBothCodes() {
        val p = plan(
            listOf(clip(0, inMs = 1_000, outMs = 5_000)),
            range(0, 0, 500, "2"), range(0, 2_000, 3_000, "2"), range(0, 2_500, 3_500, "4"),
        )
        assertEquals(listOf("E3"), p.errors)
        assertEquals(listOf("W1"), p.warnings)
    }

    @Test
    fun planExport_adjacentRangesWithEqualSpeed_mergeBeforeRounding() {
        val p = plan(listOf(clip(0, inMs = 0, outMs = 2_002)), range(0, 0, 1_001, "2"), range(0, 1_001, 2_002, "2"))
        assertEquals(listOf(PlanPart(0, 0, 2_002, "2", 0, 1_001)), p.parts)
    }

    @Test
    fun planExport_rangeAtOneX_changesNothing() {
        val p = plan(listOf(clip(0, inMs = 0, outMs = 3_000)), range(0, 1_000, 2_000, "1.0"))
        assertEquals(listOf(PlanPart(0, 0, 3_000, "1", 0, 3_000)), p.parts)
    }

    @Test
    fun planExport_partsOfDifferentClipsAreNeverMerged() {
        val p = plan(listOf(clip(0, outMs = 1_000), clip(1, outMs = 1_000)), range(0, 0, 1_000, "2"), range(1, 0, 1_000, "2"))
        assertEquals(listOf(0, 1), p.parts.map { it.clipOrder })
        assertEquals(listOf(500L, 500L), p.parts.map { it.outDurationMs })
        assertEquals(listOf(0L, 500L), p.parts.map { it.outStartMs })
    }

    @Test
    fun planExport_partLength_isRoundedHalfUp() {
        // 3 ms at 2x = 1.5 -> 2; 1000 ms at 1.5x = 666.67 -> 667; 1001 ms at 4x = 250.25 -> 250; 1002 ms at 4x = 250.5 -> 251
        val p = plan(
            listOf(clip(0, inMs = 0, outMs = 3_006)),
            range(0, 0, 3, "2"), range(0, 3, 1_003, "1.5"), range(0, 1_003, 2_004, "4"), range(0, 2_004, 3_006, "4"),
        )
        // the last two ranges have equal speed and merge: 2003 ms at 4x = 500.75 -> 501
        assertEquals(listOf(2L, 667L, 501L), p.parts.map { it.outDurationMs })
        assertEquals(listOf(0L, 2L, 669L), p.parts.map { it.outStartMs })
    }

    @Test
    fun planExport_speedLiteralsAreWrittenWithoutTrailingZeros() {
        val p = plan(
            listOf(clip(0, outMs = 5_000)),
            range(0, 0, 1_000, "0.250"), range(0, 1_000, 2_000, "0.5"), range(0, 2_000, 3_000, "2.0"), range(0, 3_000, 4_000, "4"),
        )
        assertEquals(listOf("0.25", "0.5", "2", "4", "1"), p.parts.map { it.speed })
    }

    @Test
    fun planExport_errorsAreSortedAndPartsEmpty() {
        val p = plan(listOf(clip(0, inMs = 0, outMs = 100)), range(0, 5, 5, "2"), range(0, 0, 50, "3"))
        assertEquals(listOf("E4", "E6", "E7"), p.errors)
        assertTrue(p.parts.isEmpty())
    }
}

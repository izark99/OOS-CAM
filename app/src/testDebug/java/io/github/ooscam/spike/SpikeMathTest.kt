package io.github.ooscam.spike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikeMathTest {
    private val ms = 1_000_000L

    @Test
    fun segmentGapMs_nextStartsAfterPreviousEnd_isPositive() {
        // previous started at 1000 ms, recorded 20000 ms; next started at 21350 ms
        assertEquals(350L, segmentGapMs(1_000 * ms, 20_000 * ms, 21_350 * ms))
    }

    @Test
    fun segmentGapMs_nextStartsBeforePreviousEnd_isNegativeOverlap() {
        assertEquals(-40L, segmentGapMs(0, 20_000 * ms, 19_960 * ms))
    }

    @Test
    fun segmentLoop_belowTarget_keepsRecording() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        assertEquals(LoopAction.Continue, loop.onStatus(19_999 * ms))
    }

    @Test
    fun segmentLoop_atTarget_stopsCurrentOnlyOnce() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        assertEquals(LoopAction.StopCurrent, loop.onStatus(20_000 * ms))
        assertEquals(LoopAction.Continue, loop.onStatus(20_100 * ms))
    }

    @Test
    fun segmentLoop_finalizeOfFirstSegment_startsSecond() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        loop.onStatus(20_000 * ms)
        assertEquals(LoopAction.StartNext(index = 2), loop.onFinalize(errorCode = 0))
    }

    @Test
    fun segmentLoop_finalizeOfLastSegment_isDone() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        loop.onStatus(20_000 * ms)
        loop.onFinalize(0)
        loop.onStatus(20_000 * ms)
        assertEquals(LoopAction.Done, loop.onFinalize(0))
    }

    @Test
    fun segmentLoop_finalizeWithError_failsAndKeepsTheCode() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        assertEquals(LoopAction.Failed(errorCode = 5), loop.onFinalize(errorCode = 5))
    }

    @Test
    fun segmentLoop_userStop_isDoneAfterFinalizeEvenOnFirstSegment() {
        val loop = SegmentLoop(targetNs = 20_000 * ms, count = 2)
        loop.requestStop()
        assertEquals(LoopAction.Done, loop.onFinalize(0))
    }

    @Test
    fun spikeJobParts_twoClips_trimsAndSpeedsOneRange() {
        val parts = spikeJobParts(listOf(20_000, 20_000))
        assertEquals(
            listOf(
                PartSpec(clip = 0, startMs = 2_000, endMs = 6_000, speed = 1f),
                PartSpec(clip = 0, startMs = 6_000, endMs = 10_000, speed = 2f),
                PartSpec(clip = 0, startMs = 10_000, endMs = 12_000, speed = 1f),
                PartSpec(clip = 1, startMs = 1_000, endMs = 9_000, speed = 1f),
            ),
            parts,
        )
    }

    @Test
    fun spikeJobParts_expectedOutput_isSumOfPartLengthsDividedBySpeed() {
        val parts = spikeJobParts(listOf(20_000, 20_000))
        assertEquals(16_000L, expectedOutputMs(parts))
    }

    @Test
    fun spikeJobParts_clipTooShort_returnsNoParts() {
        assertTrue(spikeJobParts(listOf(20_000, 5_000)).isEmpty())
        assertTrue(spikeJobParts(listOf(20_000)).isEmpty())
    }

    @Test
    fun spikeJobParts_alwaysRemovesAudio() {
        assertTrue(SPIKE_REMOVE_AUDIO)
    }

    @Test
    fun logLine_hasTagPrefixAndKeyValues() {
        assertEquals("gap_ms=350 segment=2", logLine("gap_ms" to 350, "segment" to 2))
    }
}

package io.github.ooscam.core

import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlanSegmentsTest {
    private val minute = 60_000L

    private fun input(
        minutes: Int = 20,
        target: StorageTarget = StorageTarget.DEVICE,
        quality: Quality = Quality.Q1080P30,
        audio: Boolean = true,
        stopAtMs: Long? = 10 * minute,
        samples: List<FreeSpaceSample> = emptyList(),
        start: LocalDateTime = LocalDateTime.of(2031, 3, 9, 7, 5, 3),
    ) = RecordingInput("R", start, minutes, target, quality, audio, stopAtMs, samples)

    private fun sample(t: Long, mb: Long) = FreeSpaceSample(t, mb)

    @Test
    fun planSegments_bytesPerMs_followsPresetsAndAudio() {
        assertEquals(1_016L, planSegments(input(quality = Quality.Q720P30, audio = true)).bytesPerMs)
        assertEquals(2_000L, planSegments(input(quality = Quality.Q1080P30, audio = false)).bytesPerMs)
        assertEquals(5_641L, planSegments(input(quality = Quality.Q2160P30, audio = true)).bytesPerMs)
    }

    @Test
    fun planSegments_timeCap_cutsBackToBackWithShortLastPart() {
        val plan = planSegments(input(minutes = 20, stopAtMs = 70 * minute))
        assertEquals(listOf(20 * minute, 20 * minute, 20 * minute, 10 * minute), plan.segments.map { it.durationMs })
        assertEquals(listOf(0L, 20 * minute, 40 * minute, 60 * minute), plan.segments.map { it.startOffsetMs })
        assertEquals(listOf(1, 2, 3, 4), plan.segments.map { it.index })
        assertEquals(70 * minute, plan.endMs)
    }

    @Test
    fun planSegments_endOnBoundary_hasNoEmptyLastSegment() {
        val plan = planSegments(input(minutes = 20, stopAtMs = 40 * minute))
        assertEquals(2, plan.segments.size)
    }

    @Test
    fun planSegments_estimatedBytes_isDurationTimesBytesPerMsInLongMath() {
        val plan = planSegments(input(minutes = 60, quality = Quality.Q2160P30, stopAtMs = 60 * minute))
        assertEquals(3_600_000L * 5_641L, plan.segments.single().estimatedBytes)
        assertTrue(plan.segments.single().estimatedBytes > Int.MAX_VALUE)
    }

    @Test
    fun planSegments_sdTarget_capsPartByBytesWithCeiling() {
        val plan = planSegments(
            input(minutes = 60, target = StorageTarget.SD, quality = Quality.Q2160P30, audio = false, stopAtMs = 1_500_000),
        )
        assertEquals(listOf(693_334L, 693_334L, 113_332L), plan.segments.map { it.durationMs })
    }

    @Test
    fun planSegments_deviceTarget_ignoresTheByteCap() {
        val plan = planSegments(
            input(minutes = 60, target = StorageTarget.DEVICE, quality = Quality.Q2160P30, audio = false, stopAtMs = 1_500_000),
        )
        assertEquals(listOf(1_500_000L), plan.segments.map { it.durationMs })
    }

    @Test
    fun planSegments_sdTarget_timeCapWinsWhenShorter() {
        val plan = planSegments(input(minutes = 5, target = StorageTarget.SD, quality = Quality.Q720P30, stopAtMs = 12 * minute))
        assertEquals(listOf(5 * minute, 5 * minute, 2 * minute), plan.segments.map { it.durationMs })
    }

    @Test
    fun planSegments_exactly500Mb_continues() {
        val plan = planSegments(input(stopAtMs = 50_000, samples = listOf(sample(0, 600), sample(5_000, 500), sample(10_000, 500))))
        assertEquals(StopReason.USER, plan.stopReason)
        assertEquals(50_000L, plan.endMs)
    }

    @Test
    fun planSegments_499Mb_stopsAtThatSampleWithLowStorage() {
        val plan = planSegments(input(stopAtMs = 50_000, samples = listOf(sample(0, 600), sample(5_000, 501), sample(10_000, 499), sample(15_000, 100))))
        assertEquals(StopReason.LOW_STORAGE, plan.stopReason)
        assertEquals(10_000L, plan.endMs)
        assertEquals(10_000L, plan.segments.sumOf { it.durationMs })
    }

    @Test
    fun planSegments_lowStorageWithoutUserStop_endsAtSample() {
        val plan = planSegments(input(stopAtMs = null, samples = listOf(sample(0, 900), sample(7_000, 10))))
        assertEquals(7_000L, plan.endMs)
        assertEquals(StopReason.LOW_STORAGE, plan.stopReason)
    }

    @Test
    fun planSegments_tieBetweenUserStopAndLowStorage_isUser() {
        val plan = planSegments(input(stopAtMs = 9_000, samples = listOf(sample(9_000, 1))))
        assertEquals(StopReason.USER, plan.stopReason)
        assertEquals(9_000L, plan.endMs)
    }

    @Test
    fun planSegments_userStopBeforeLowStorage_isUser() {
        val plan = planSegments(input(stopAtMs = 8_000, samples = listOf(sample(9_000, 1))))
        assertEquals(StopReason.USER, plan.stopReason)
    }

    @Test
    fun planSegments_unsortedSamples_useTheEarliestLowOne() {
        val plan = planSegments(input(stopAtMs = null, samples = listOf(sample(20_000, 1), sample(5_000, 2), sample(0, 900))))
        assertEquals(5_000L, plan.endMs)
    }

    @Test
    fun planSegments_lowStorageAtZero_givesNoSegments() {
        val plan = planSegments(input(stopAtMs = 10_000, samples = listOf(sample(0, 10))))
        assertTrue(plan.segments.isEmpty())
        assertEquals(0L, plan.endMs)
        assertEquals(StopReason.LOW_STORAGE, plan.stopReason)
    }

    @Test
    fun planSegments_userStopAtZero_givesNoSegments() {
        val plan = planSegments(input(stopAtMs = 0))
        assertTrue(plan.segments.isEmpty())
        assertEquals(StopReason.USER, plan.stopReason)
    }

    @Test
    fun planSegments_noStopAndNoLowSample_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            planSegments(input(stopAtMs = null, samples = listOf(sample(0, 900))))
        }
    }

    @Test
    fun planSegments_fileNames_useSessionStartAndTwoDigitPartNumbers() {
        val plan = planSegments(input(minutes = 1, stopAtMs = 10 * minute))
        assertEquals("OOSCam_20310309_070503_part01.mp4", plan.segments[0].fileName)
        assertEquals("OOSCam_20310309_070503_part09.mp4", plan.segments[8].fileName)
        assertEquals("OOSCam_20310309_070503_part10.mp4", plan.segments[9].fileName)
    }

    @Test
    fun planSegments_partNumberAbove99_keepsAllDigits() {
        val plan = planSegments(input(minutes = 1, quality = Quality.Q720P30, audio = false, stopAtMs = 101 * minute))
        assertEquals(101, plan.segments.size)
        assertEquals("OOSCam_20310309_070503_part101.mp4", plan.segments.last().fileName)
    }

    @Test
    fun planSegments_midnightStart_formatsZeroPaddedFields() {
        val plan = planSegments(input(start = LocalDateTime.of(2032, 1, 2, 0, 0, 0)))
        assertEquals("OOSCam_20320102_000000_part01.mp4", plan.segments.first().fileName)
    }

    @Test
    fun planSegments_idIsCopied() {
        assertEquals("R", planSegments(input()).id)
    }
}

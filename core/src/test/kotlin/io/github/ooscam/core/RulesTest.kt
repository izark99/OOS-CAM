package io.github.ooscam.core

import java.time.LocalDateTime
import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Rule-level tests written from the rule table. They use their own numbers, never fixture files. */
class RulesTest {
    // ------------------------------------------------------------ R6, R8

    @Test
    fun r6_allSixAllowedSpeeds_areAccepted() {
        for (speed in listOf("0.25", "0.5", "1", "1.5", "2", "4")) {
            val p = planExport(project(listOf(clip(0, outMs = 4_000)), listOf(range(0, 0, 2_000, speed))))
            assertTrue(p.ok, "speed $speed should be accepted")
        }
    }

    @Test
    fun r6_neighbouringValuesAreRejected() {
        for (speed in listOf("0", "0.24", "0.26", "0.75", "1.25", "3", "5", "-1")) {
            val p = planExport(project(listOf(clip(0, outMs = 4_000)), listOf(range(0, 0, 2_000, speed))))
            assertEquals(listOf("E4"), p.errors, "speed $speed should be rejected")
        }
    }

    @Test
    fun r8_e5IsNotComputedWhenE3E6OrE7IsPresent() {
        val long = clip(0, durationMs = 500_000, outMs = 450_001)
        val slow = range(0, 0, 450_001, "0.25")
        val e3 = listOf(slow, range(0, 100, 200, "2"))
        assertEquals(listOf("E3"), planExport(project(listOf(long), e3)).errors)
        val e7 = listOf(slow, range(0, 100, 100, "2"))
        assertEquals(listOf("E7"), planExport(project(listOf(long), e7)).errors)
        val badClip = clip(1, durationMs = 100, outMs = 50)
        assertEquals(listOf("E6"), planExport(project(listOf(long, badClip), listOf(slow))).errors)
    }

    @Test
    fun r8_e1IsIndependentOfTheOtherErrors() {
        val clips = (0..10).map { clip(it, durationMs = 2_000, outMs = 1_000) }
        val p = planExport(project(clips, listOf(range(0, 0, 500, "3"))))
        assertEquals(listOf("E1", "E4"), p.errors)
    }

    // ------------------------------------------------------------ export invariants

    @Test
    fun exportInvariants_holdForManySeededProjects() {
        val random = Random(20_261_125L)
        val speeds = listOf("0.25", "0.5", "1", "1.5", "2", "4")
        repeat(300) { round ->
            val clips = (0 until 1 + random.nextInt(4)).map { i ->
                val inMs = random.nextInt(2_000).toLong()
                val outMs = inMs + 500 + random.nextInt(20_000)
                clip(i, durationMs = outMs + random.nextInt(1_000), inMs = inMs, outMs = outMs)
            }
            val ranges = clips.flatMap { c ->
                var cursor = c.inMs - random.nextInt(300)
                (0 until random.nextInt(4)).map {
                    val start = cursor + random.nextInt(2_000)
                    val end = start + 1 + random.nextInt(4_000)
                    cursor = end
                    range(c.order, start, end, speeds[random.nextInt(speeds.size)])
                }
            }
            val p = planExport(project(clips.shuffled(random), ranges.shuffled(random)))
            assertTrue(p.ok, "round $round: ${p.errors}")

            assertEquals(p.parts.sumOf { it.outDurationMs }, p.outputDurationMs, "round $round")
            var expectedStart = 0L
            for (part in p.parts) {
                assertEquals(expectedStart, part.outStartMs, "round $round")
                expectedStart += part.outDurationMs
            }
            for (c in clips) {
                val mine = p.parts.filter { it.clipOrder == c.order }
                assertEquals(c.inMs, mine.first().srcStartMs, "round $round")
                assertEquals(c.outMs, mine.last().srcEndMs, "round $round")
                mine.zipWithNext().forEach { (a, b) ->
                    assertEquals(a.srcEndMs, b.srcStartMs, "round $round: parts must touch")
                    assertTrue(a.speed != b.speed, "round $round: equal-speed neighbours must merge")
                }
            }
            assertEquals(clips.sumOf { it.outMs - it.inMs }, p.trimmedSourceMs)
            assertEquals(clips.sortedBy { it.order }.map { it.order }, p.parts.map { it.clipOrder }.distinct())
        }
    }

    // ------------------------------------------------------------ segments

    private fun recording(
        minutes: Int,
        target: StorageTarget,
        quality: Quality,
        audio: Boolean,
        stopAtMs: Long?,
        samples: List<FreeSpaceSample>,
    ) = RecordingInput("S", LocalDateTime.of(2034, 5, 6, 7, 8, 9), minutes, target, quality, audio, stopAtMs, samples)

    @Test
    fun segmentInvariants_holdForManySeededRecordings() {
        val random = Random(77L)
        repeat(300) { round ->
            val quality = Quality.entries[random.nextInt(Quality.entries.size)]
            val target = if (random.nextBoolean()) StorageTarget.SD else StorageTarget.DEVICE
            val audio = random.nextBoolean()
            val minutes = 1 + random.nextInt(60)
            val stop = random.nextInt(200 * 60_000).toLong()
            val samples = (0 until random.nextInt(5)).map { FreeSpaceSample(random.nextInt(200 * 60_000).toLong(), random.nextInt(1_000).toLong()) }
            val plan = planSegments(recording(minutes, target, quality, audio, stop, samples))

            val bytesPerMs = quality.videoMbps * 125L + if (audio) 16L else 0L
            assertEquals(bytesPerMs, plan.bytesPerMs)
            assertEquals(plan.endMs, plan.segments.sumOf { it.durationMs }, "round $round")
            var offset = 0L
            plan.segments.forEachIndexed { i, s ->
                assertEquals(i + 1, s.index)
                assertEquals(offset, s.startOffsetMs)
                assertTrue(s.durationMs > 0)
                assertTrue(s.durationMs <= minutes * 60_000L)
                if (target == StorageTarget.SD) assertTrue(s.durationMs * bytesPerMs <= 3_900_000_000L + bytesPerMs)
                assertEquals(s.durationMs * bytesPerMs, s.estimatedBytes)
                if (i < plan.segments.size - 1) assertTrue(s.durationMs == plan.segments[0].durationMs, "only the last part may be short")
                offset += s.durationMs
            }
            val lowAt = samples.filter { it.freeMb < 500 }.minOfOrNull { it.tMs }
            val expectLow = lowAt != null && lowAt < stop
            assertEquals(if (expectLow) StopReason.LOW_STORAGE else StopReason.USER, plan.stopReason)
            assertEquals(if (expectLow) lowAt else stop, plan.endMs)
        }
    }

    @Test
    fun r14_fileNamePadsToTwoDigitsOnly() {
        val plan = planSegments(recording(1, StorageTarget.DEVICE, Quality.Q720P30, false, 120 * 60_000L, emptyList()))
        val numbers = plan.segments.map { it.fileName.substringAfter("_part").removeSuffix(".mp4") }
        assertEquals("01", numbers.first())
        assertEquals("99", numbers[98])
        assertEquals("100", numbers[99])
        assertFalse(numbers.any { it.length < 2 })
    }
}

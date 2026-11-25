package io.github.ooscam.core

import java.time.format.DateTimeFormatter

private const val MS_PER_MINUTE = 60_000L
private const val BYTES_PER_MS_PER_MBPS = 125L
private const val AUDIO_BYTES_PER_MS = 16L
private const val SD_CAP_BYTES = 3_900_000_000L
private const val LOW_STORAGE_MB = 500L
private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

/** Plans the segment files of one recording. Pure function of its input. */
fun planSegments(recording: RecordingInput): SegmentPlan {
    require(recording.segmentMinutes > 0) { "segmentMinutes must be positive" }
    require(recording.stopAtMs == null || recording.stopAtMs >= 0) { "stopAtMs must not be negative" }

    val bytesPerMs = recording.quality.videoMbps * BYTES_PER_MS_PER_MBPS +
        (if (recording.audio) AUDIO_BYTES_PER_MS else 0L)

    val lowStorageAt = recording.freeSpaceSamples
        .filter { it.freeMb < LOW_STORAGE_MB }
        .minOfOrNull { it.tMs }
    val userStopAt = recording.stopAtMs
    require(userStopAt != null || lowStorageAt != null) { "recording has no end: no stop time and no low-storage sample" }

    // Low storage wins only when strictly earlier than the user stop; a tie is a user stop.
    val lowFirst = lowStorageAt != null && (userStopAt == null || lowStorageAt < userStopAt)
    val endMs = if (lowFirst) lowStorageAt else userStopAt!!
    val reason = if (lowFirst) StopReason.LOW_STORAGE else StopReason.USER

    var capMs = recording.segmentMinutes * MS_PER_MINUTE
    if (recording.target == StorageTarget.SD) capMs = minOf(capMs, ceilDiv(SD_CAP_BYTES, bytesPerMs))

    val stamp = recording.startLocal.format(STAMP_FORMAT)
    val segments = mutableListOf<SegmentPart>()
    var start = 0L
    var index = 1
    while (start < endMs) {
        val duration = minOf(capMs, endMs - start)
        segments.add(
            SegmentPart(
                index = index,
                fileName = "OOSCam_${stamp}_part${index.toString().padStart(2, '0')}.mp4",
                startOffsetMs = start,
                durationMs = duration,
                estimatedBytes = duration * bytesPerMs,
            ),
        )
        start += duration
        index += 1
    }
    return SegmentPlan(recording.id, reason, endMs, bytesPerMs, segments)
}

private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

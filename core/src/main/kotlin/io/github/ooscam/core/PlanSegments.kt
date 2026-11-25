package io.github.ooscam.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val MS_PER_MINUTE = 60_000L
private const val BYTES_PER_MS_PER_MBPS = 125L
private const val AUDIO_BYTES_PER_MS = 16L
private const val SD_CAP_BYTES = 3_900_000_000L
private const val LOW_STORAGE_MB = 500L
private val STAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

/** Bytes written per ms of recording for the planning bitrate presets (video + optional audio). */
fun segmentBytesPerMs(quality: Quality, audio: Boolean): Long =
    quality.videoMbps * BYTES_PER_MS_PER_MBPS + (if (audio) AUDIO_BYTES_PER_MS else 0L)

/** Longest segment in ms: the time cap, and on an SD target also the 3,900 MB size cap. */
fun segmentCapMs(segmentMinutes: Int, target: StorageTarget, quality: Quality, audio: Boolean): Long {
    require(segmentMinutes > 0) { "segmentMinutes must be positive" }
    val timeCap = segmentMinutes * MS_PER_MINUTE
    if (target != StorageTarget.SD) return timeCap
    return minOf(timeCap, ceilDiv(SD_CAP_BYTES, segmentBytesPerMs(quality, audio)))
}

/** `OOSCam_YYYYMMDD_HHMMSS_partNN.mp4`: session start in local time, index with at least 2 digits. */
fun segmentFileName(sessionStartLocal: LocalDateTime, index: Int): String =
    "OOSCam_${sessionStartLocal.format(STAMP_FORMAT)}_part${index.toString().padStart(2, '0')}.mp4"

/** True when free space is below the 500 MB low-storage threshold. */
fun isLowStorage(freeMb: Long): Boolean = freeMb < LOW_STORAGE_MB

/** Plans the segment files of one recording. Pure function of its input. */
fun planSegments(recording: RecordingInput): SegmentPlan {
    require(recording.stopAtMs == null || recording.stopAtMs >= 0) { "stopAtMs must not be negative" }

    val bytesPerMs = segmentBytesPerMs(recording.quality, recording.audio)

    val lowStorageAt = recording.freeSpaceSamples
        .filter { isLowStorage(it.freeMb) }
        .minOfOrNull { it.tMs }
    val userStopAt = recording.stopAtMs
    require(userStopAt != null || lowStorageAt != null) { "recording has no end: no stop time and no low-storage sample" }

    // Low storage wins only when strictly earlier than the user stop; a tie is a user stop.
    val lowFirst = lowStorageAt != null && (userStopAt == null || lowStorageAt < userStopAt)
    val endMs = if (lowFirst) lowStorageAt else userStopAt!!
    val reason = if (lowFirst) StopReason.LOW_STORAGE else StopReason.USER

    val capMs = segmentCapMs(recording.segmentMinutes, recording.target, recording.quality, recording.audio)

    val segments = mutableListOf<SegmentPart>()
    var start = 0L
    var index = 1
    while (start < endMs) {
        val duration = minOf(capMs, endMs - start)
        segments.add(
            SegmentPart(
                index = index,
                fileName = segmentFileName(recording.startLocal, index),
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

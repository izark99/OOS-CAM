package io.github.ooscam.spike

/** Pure helpers for the Phase 0 spike. No Android imports, so they run on the JVM. */

const val SPIKE_LOG_TAG = "OOSCamSpike"
const val SEGMENT_TARGET_MS = 20_000L
const val SEGMENT_COUNT = 2
const val SPIKE_REMOVE_AUDIO = true

private const val NS_PER_MS = 1_000_000L

/** Lost footage between two segments, in ms (negative means they overlap). */
fun segmentGapMs(prevStartNs: Long, prevRecordedNs: Long, nextStartNs: Long): Long =
    (nextStartNs - (prevStartNs + prevRecordedNs)) / NS_PER_MS

sealed interface LoopAction {
    data object Continue : LoopAction
    data object StopCurrent : LoopAction
    data class StartNext(val index: Int) : LoopAction
    data object Done : LoopAction
    data class Failed(val errorCode: Int) : LoopAction
}

/**
 * Back-to-back segment state machine. The caller feeds it recorder events and does what it says.
 * Segments are numbered from 1. Each segment is cut when the recorded footage reaches [targetNs].
 */
class SegmentLoop(private val targetNs: Long, private val count: Int) {
    private var current = 1
    private var stopSent = false
    private var userStop = false

    fun onStatus(recordedNs: Long): LoopAction {
        if (!stopSent && recordedNs >= targetNs) {
            stopSent = true
            return LoopAction.StopCurrent
        }
        return LoopAction.Continue
    }

    fun requestStop() {
        userStop = true
        stopSent = true
    }

    fun onFinalize(errorCode: Int): LoopAction {
        if (errorCode != 0 && !userStop) return LoopAction.Failed(errorCode)
        if (userStop || current >= count) return LoopAction.Done
        current += 1
        stopSent = false
        return LoopAction.StartNext(current)
    }
}

/** One piece of the Transformer job: a source range of one clip played at one speed. */
data class PartSpec(val clip: Int, val startMs: Long, val endMs: Long, val speed: Float)

private const val CLIP0_IN_MS = 2_000L
private const val CLIP0_RANGE_START_MS = 6_000L
private const val CLIP0_RANGE_END_MS = 10_000L
private const val CLIP0_OUT_MS = 12_000L
private const val CLIP1_IN_MS = 1_000L
private const val CLIP1_OUT_MS = 9_000L
private const val RANGE_SPEED = 2f

/**
 * Job shape the spike measures: 2 clips, trim both, 2x on one range of clip 0, audio removed.
 * Returns no parts when fewer than 2 clips are given or a clip is shorter than its trim end.
 */
fun spikeJobParts(clipDurationsMs: List<Long>): List<PartSpec> {
    if (clipDurationsMs.size < 2) return emptyList()
    if (clipDurationsMs[0] < CLIP0_OUT_MS || clipDurationsMs[1] < CLIP1_OUT_MS) return emptyList()
    return listOf(
        PartSpec(0, CLIP0_IN_MS, CLIP0_RANGE_START_MS, 1f),
        PartSpec(0, CLIP0_RANGE_START_MS, CLIP0_RANGE_END_MS, RANGE_SPEED),
        PartSpec(0, CLIP0_RANGE_END_MS, CLIP0_OUT_MS, 1f),
        PartSpec(1, CLIP1_IN_MS, CLIP1_OUT_MS, 1f),
    )
}

fun expectedOutputMs(parts: List<PartSpec>): Long =
    parts.sumOf { Math.round((it.endMs - it.startMs) / it.speed.toDouble()) }

fun logLine(vararg pairs: Pair<String, Any>): String =
    pairs.joinToString(" ") { "${it.first}=${it.second}" }

package io.github.ooscam.capture

import io.github.ooscam.core.segmentFileName
import java.time.LocalDateTime

/** Where one segment is written: a content or file URI plus the platform output options. */
class SegmentOutput(val uri: String, val handle: Any?)

/** Creates segment outputs on the chosen storage target and closes them after finalize. */
interface SegmentSink {
    fun open(fileName: String): SegmentOutput

    /** Called once per opened output after the recorder finalized it ([ok] = MP4 finalized). */
    fun commit(output: SegmentOutput, ok: Boolean)
}

sealed interface RecorderEvent {
    data object Started : RecorderEvent

    data class Progress(val recordedMs: Long) : RecorderEvent

    data class Finalized(val ok: Boolean, val recordedMs: Long, val bytes: Long) : RecorderEvent
}

/** One platform recording at a time; CameraX in the app, a fake in tests. */
interface SegmentRecorder {
    fun startSegment(output: SegmentOutput, audio: Boolean, onEvent: (RecorderEvent) -> Unit)

    fun stopSegment()
}

data class SegmentRecord(
    val index: Int,
    val fileName: String,
    val uri: String,
    val startOffsetMs: Long,
    val durationMs: Long,
    val bytes: Long,
    val ok: Boolean,
)

/**
 * Runs the segment loop of one session with the rules from `:core`: a new recording starts when
 * the open one reaches [capMs] (from `segmentCapMs`), files are named with `segmentFileName`, and
 * stop finalizes the open segment without starting another.
 */
class RecorderController(
    private val capMs: Long,
    private val sessionStartLocal: LocalDateTime,
    private val audio: Boolean,
    private val recorder: SegmentRecorder,
    private val sink: SegmentSink,
    private val listener: Listener,
) {
    interface Listener {
        fun onSegmentStarted(index: Int)

        fun onSegmentFinalized(record: SegmentRecord)

        fun onFinished(error: Boolean)
    }

    private var index = 0
    private var offsetMs = 0L
    private var open: Pair<String, SegmentOutput>? = null
    private var stopPending = false
    private var stopRequested = false
    private var finished = false

    fun start() {
        check(index == 0) { "start once" }
        startNext()
    }

    /** Finalize the open segment and finish. Safe to call more than once. */
    fun stop() {
        if (stopRequested || finished) return
        stopRequested = true
        if (open == null) {
            finish(error = false)
        } else if (!stopPending) {
            stopPending = true
            recorder.stopSegment()
        }
    }

    private fun startNext() {
        index += 1
        val name = segmentFileName(sessionStartLocal, index)
        // A removed SD card or a full volume must end the session, not crash the service.
        val output = runCatching { sink.open(name) }.getOrElse {
            finish(error = true)
            return
        }
        open = name to output
        stopPending = false
        val segment = index
        recorder.startSegment(output, audio) { event -> onEvent(segment, event) }
    }

    private fun onEvent(segment: Int, event: RecorderEvent) {
        if (segment != index || finished) return
        when (event) {
            RecorderEvent.Started -> listener.onSegmentStarted(segment)
            is RecorderEvent.Progress -> if (event.recordedMs >= capMs && !stopPending) {
                stopPending = true
                recorder.stopSegment()
            }
            is RecorderEvent.Finalized -> onFinalized(segment, event)
        }
    }

    private fun onFinalized(segment: Int, event: RecorderEvent.Finalized) {
        val (name, output) = open ?: return
        open = null
        sink.commit(output, event.ok)
        listener.onSegmentFinalized(
            SegmentRecord(segment, name, output.uri, offsetMs, event.recordedMs, event.bytes, event.ok),
        )
        offsetMs += event.recordedMs
        when {
            stopRequested -> finish(error = !event.ok)
            !event.ok -> finish(error = true)
            else -> startNext()
        }
    }

    private fun finish(error: Boolean) {
        if (finished) return
        finished = true
        listener.onFinished(error)
    }
}

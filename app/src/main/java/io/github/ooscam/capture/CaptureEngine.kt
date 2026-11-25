package io.github.ooscam.capture

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import java.time.LocalDateTime

/** Records the segments of one session. The service drives it; tests replace it. */
interface CaptureEngine {
    fun start(config: CaptureConfig, sessionStartLocal: LocalDateTime, listener: Listener)

    /** Finalize the open segment; [Listener.onStopped] follows. */
    fun stop()

    interface Listener {
        fun onSegmentStarted(index: Int)

        fun onSegmentFinalized(record: SegmentRecord) = Unit

        fun onStopped(error: Boolean)
    }
}

/** Seams the service reads at start. Robolectric builds the service itself, so tests swap these. */
object CaptureDeps {
    private val defaultEngineFactory: (Context, LifecycleOwner) -> CaptureEngine = { c, o -> CameraXCaptureEngine(c, o) }
    private val defaultWallClock: () -> Long = { System.currentTimeMillis() }

    var engineFactory: (Context, LifecycleOwner) -> CaptureEngine = defaultEngineFactory
    var wallClockMs: () -> Long = defaultWallClock

    fun reset() {
        engineFactory = defaultEngineFactory
        wallClockMs = defaultWallClock
    }
}

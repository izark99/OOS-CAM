package io.github.ooscam.capture

import android.content.Context
import io.github.ooscam.core.Quality
import io.github.ooscam.data.OOSCamDatabase
import io.github.ooscam.data.RecordingSessionEntity
import io.github.ooscam.data.SegmentEntity
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Session metadata writes; the Room implementation runs them off the main thread in order. */
interface CaptureSessionStore {
    fun begin(session: RecordingSessionEntity)

    fun addSegment(segment: SegmentEntity)

    fun finish(sessionId: String, stopReason: String)
}

class RoomCaptureSessionStore(
    context: Context,
    private val io: Executor = SERIAL_IO,
) : CaptureSessionStore {
    private val dao = OOSCamDatabase.get(context).captureDao()

    override fun begin(session: RecordingSessionEntity) = io.execute { dao.insertSession(session) }

    override fun addSegment(segment: SegmentEntity) = io.execute { dao.insertSegment(segment) }

    override fun finish(sessionId: String, stopReason: String) = io.execute { dao.finishSession(sessionId, stopReason) }

    private companion object {
        val SERIAL_IO: Executor = Executors.newSingleThreadExecutor()
    }
}

/** Planned frame size for a quality preset (16:9, 30 fps); CameraX may fall back lower on some devices. */
fun Quality.frameSize(): Pair<Int, Int> = when (this) {
    Quality.Q720P30 -> 1280 to 720
    Quality.Q1080P30 -> 1920 to 1080
    Quality.Q2160P30 -> 3840 to 2160
}

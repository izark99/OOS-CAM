package io.github.ooscam.capture

import io.github.ooscam.core.Quality
import io.github.ooscam.core.StorageTarget
import io.github.ooscam.core.segmentCapMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CaptureRecorderControllerTest {
    private val start = LocalDateTime.of(2031, 7, 8, 9, 10, 11)
    private val recorder = FakeSegmentRecorder()
    private val sink = FakeSegmentSink()
    private val events = mutableListOf<String>()
    private var finishedError: Boolean? = null
    private val records = mutableListOf<SegmentRecord>()

    private val listener = object : RecorderController.Listener {
        override fun onSegmentStarted(index: Int) { events += "start $index" }
        override fun onSegmentFinalized(record: SegmentRecord) { records += record }
        override fun onFinished(error: Boolean) { finishedError = error }
    }

    private fun controller(minutes: Int = 5, target: StorageTarget = StorageTarget.DEVICE, quality: Quality = Quality.Q1080P30) =
        RecorderController(
            capMs = segmentCapMs(minutes, target, quality, audio = true),
            sessionStartLocal = start,
            audio = true,
            recorder = recorder,
            sink = sink,
            listener = listener,
        )

    @Test
    fun start_opensPart01NamedByCoreAndStartsRecordingWithAudio() {
        controller().start()
        assertEquals(listOf("OOSCam_20310708_091011_part01.mp4"), sink.opened)
        assertEquals(1, recorder.startCount)
        assertTrue(recorder.lastAudio)
        recorder.emit(RecorderEvent.Started)
        assertEquals(listOf("start 1"), events)
    }

    @Test
    fun progressBelowCap_keepsRecording() {
        controller(minutes = 5).start()
        recorder.emit(RecorderEvent.Started)
        recorder.emit(RecorderEvent.Progress(recordedMs = 5 * 60_000L - 1))
        assertEquals(0, recorder.stopCount)
    }

    @Test
    fun progressReachingCap_finalizesThenStartsNextPart() {
        controller(minutes = 5).start()
        recorder.emit(RecorderEvent.Started)
        recorder.emit(RecorderEvent.Progress(recordedMs = 5 * 60_000L))
        assertEquals(1, recorder.stopCount)
        recorder.emit(RecorderEvent.Progress(recordedMs = 5 * 60_000L + 30))
        assertEquals("one stop per rollover", 1, recorder.stopCount)
        recorder.emit(RecorderEvent.Finalized(ok = true, recordedMs = 300_020L, bytes = 600L))

        assertEquals(listOf("OOSCam_20310708_091011_part01.mp4", "OOSCam_20310708_091011_part02.mp4"), sink.opened)
        assertEquals(2, recorder.startCount)
        assertEquals(listOf(true), sink.committed)
        assertNull(finishedError)
    }

    @Test
    fun segmentRecords_carryIndexUriAndAccumulatedStartOffsets() {
        controller(minutes = 5).start()
        recorder.emit(RecorderEvent.Progress(300_000L))
        recorder.emit(RecorderEvent.Finalized(ok = true, recordedMs = 300_040L, bytes = 10L))
        recorder.emit(RecorderEvent.Progress(300_000L))
        recorder.emit(RecorderEvent.Finalized(ok = true, recordedMs = 300_010L, bytes = 20L))

        assertEquals(listOf(1, 2), records.map { it.index })
        assertEquals(listOf(0L, 300_040L), records.map { it.startOffsetMs })
        assertEquals(listOf("uri:1", "uri:2"), records.map { it.uri })
        assertEquals(listOf(300_040L, 300_010L), records.map { it.durationMs })
    }

    @Test
    fun stop_finalizesOpenSegmentAndFinishesWithoutNewPart() {
        val c = controller()
        c.start()
        recorder.emit(RecorderEvent.Progress(10_000L))
        c.stop()
        assertEquals(1, recorder.stopCount)
        recorder.emit(RecorderEvent.Finalized(ok = true, recordedMs = 10_050L, bytes = 5L))

        assertEquals(1, recorder.startCount)
        assertEquals(false, finishedError)
        assertEquals(1, records.size)
    }

    @Test
    fun stopDuringRollover_finishesWithoutStartingNextPart() {
        val c = controller(minutes = 5)
        c.start()
        recorder.emit(RecorderEvent.Progress(300_000L))
        c.stop()
        assertEquals("no second stop call while one is pending", 1, recorder.stopCount)
        recorder.emit(RecorderEvent.Finalized(ok = true, recordedMs = 300_000L, bytes = 5L))

        assertEquals(1, recorder.startCount)
        assertEquals(false, finishedError)
    }

    @Test
    fun finalizeErrorWithoutStop_finishesWithErrorAndMarksSegmentNotOk() {
        controller().start()
        recorder.emit(RecorderEvent.Finalized(ok = false, recordedMs = 1_000L, bytes = 1L))

        assertEquals(true, finishedError)
        assertEquals(listOf(false), sink.committed)
        assertEquals(false, records.single().ok)
    }

    @Test
    fun sdTarget_rollsAtCoreSizeCapBeforeTimeCap() {
        val cap = segmentCapMs(60, StorageTarget.SD, Quality.Q2160P30, audio = true)
        assertTrue(cap < 60 * 60_000L)
        controller(minutes = 60, target = StorageTarget.SD, quality = Quality.Q2160P30).start()
        recorder.emit(RecorderEvent.Progress(cap - 1))
        assertEquals(0, recorder.stopCount)
        recorder.emit(RecorderEvent.Progress(cap))
        assertEquals(1, recorder.stopCount)
    }
}

class FakeSegmentRecorder : SegmentRecorder {
    var startCount = 0
    var stopCount = 0
    var lastAudio = false
    private var onEvent: ((RecorderEvent) -> Unit)? = null

    override fun startSegment(output: SegmentOutput, audio: Boolean, onEvent: (RecorderEvent) -> Unit) {
        startCount++
        lastAudio = audio
        this.onEvent = onEvent
    }

    override fun stopSegment() {
        stopCount++
    }

    fun emit(event: RecorderEvent) = onEvent!!.invoke(event)
}

class FakeSegmentSink : SegmentSink {
    val opened = mutableListOf<String>()
    val committed = mutableListOf<Boolean>()

    override fun open(fileName: String): SegmentOutput {
        opened += fileName
        return SegmentOutput(uri = "uri:${opened.size}", handle = null)
    }

    override fun commit(output: SegmentOutput, ok: Boolean) {
        committed += ok
    }
}

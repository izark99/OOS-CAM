package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.core.StopReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class CaptureServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val engine = FakeCaptureEngine()
    private lateinit var controller: ServiceController<CaptureService>

    @Before
    fun setUp() {
        CaptureDeps.engineFactory = { _, _ -> engine }
        CaptureDeps.wallClockMs = { 1_700_000_000_000L }
        CaptureDeps.freeBytes = { _, _ -> 50_000_000_000L }
        CaptureDeps.sessionStore = { FakeSessionStore() }
        controller = Robolectric.buildService(CaptureService::class.java).create()
    }

    @After
    fun tearDown() {
        CaptureDeps.reset()
    }

    private fun startIntent() = CaptureService.startIntent(app, CaptureConfig())

    @Test
    fun startCommand_withCamera_postsOngoingForegroundNotificationWithStopAction() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller.withIntent(startIntent()).startCommand(0, 1)

        val n: Notification? = shadowOf(controller.get()).lastForegroundNotification
        assertNotNull(n)
        assertTrue(n!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(1, n.actions.size)
        assertEquals("Stop", n.actions[0].title.toString())
        assertTrue(engine.started)
        assertTrue(CaptureService.state.value is CaptureState.Recording)
    }

    @Test
    fun notification_showsElapsedTimeAsChronometerFromSessionStart() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller.withIntent(startIntent()).startCommand(0, 1)

        val n = shadowOf(controller.get()).lastForegroundNotification!!
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertEquals(1_700_000_000_000L, n.`when`)
        assertEquals("Recording", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun startCommand_withoutCameraPermission_stopsWithoutRecording() {
        controller.withIntent(startIntent()).startCommand(0, 1)

        assertNull(shadowOf(controller.get()).lastForegroundNotification)
        assertFalse(engine.started)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun stopAction_finalizesThenRemovesNotificationAndStops() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller.withIntent(startIntent()).startCommand(0, 1)
        controller.withIntent(Intent(app, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)).startCommand(0, 2)

        assertTrue(engine.stopRequested)
        assertEquals(CaptureState.Stopping(StopReason.USER), CaptureService.state.value)
        engine.listener!!.onStopped(error = false)

        assertEquals(CaptureState.Stopped(StopReason.USER), CaptureService.state.value)
        assertTrue(shadowOf(controller.get()).isForegroundStopped)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun lowFreeSpaceWhileRecording_stopsWithLowStorageReason() {
        CaptureDeps.freeBytes = { _, _ -> 100_000_000L }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller.withIntent(startIntent()).startCommand(0, 1)

        assertEquals(CaptureState.Stopping(StopReason.LOW_STORAGE), CaptureService.state.value)
        assertTrue(engine.stopRequested)
        engine.listener!!.onStopped(error = false)
        assertEquals(CaptureState.Stopped(StopReason.LOW_STORAGE), CaptureService.state.value)
    }

    @Test
    fun session_isStoredWithSegmentsAndStopReasonInRoomStore() {
        val store = FakeSessionStore()
        CaptureDeps.sessionStore = { store }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller.withIntent(CaptureService.startIntent(app, CaptureConfig(segmentMinutes = 10))).startCommand(0, 1)
        engine.listener!!.onSegmentFinalized(SegmentRecord(1, "OOSCam_x_part01.mp4", "content://v/1", 0L, 600_000L, 42L, ok = true))
        controller.withIntent(Intent(app, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)).startCommand(0, 2)
        engine.listener!!.onStopped(error = false)

        val s = store.sessions.single()
        assertEquals(1_700_000_000_000L, s.startedAt)
        assertEquals(1080, s.height)
        assertEquals(10, s.segmentMinutes)
        assertEquals("device", s.target)
        assertEquals("content://v/1", store.segments.single().uri)
        assertEquals(s.id, store.segments.single().sessionId)
        assertEquals(listOf(s.id to "user"), store.finished)
    }

    @Test
    fun foregroundType_includesMicrophoneOnlyWhenMicGranted() {
        val cam = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        val mic = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        assertEquals(cam, captureForegroundType(micGranted = false))
        assertEquals(cam or mic, captureForegroundType(micGranted = true))
    }
}

class FakeCaptureEngine : CaptureEngine {
    var started = false
    var stopRequested = false
    var config: CaptureConfig? = null
    var listener: CaptureEngine.Listener? = null

    override fun start(config: CaptureConfig, sessionStartLocal: java.time.LocalDateTime, listener: CaptureEngine.Listener) {
        started = true
        this.config = config
        this.listener = listener
    }

    override fun stop() {
        stopRequested = true
    }
}

class FakeSessionStore : CaptureSessionStore {
    val sessions = mutableListOf<io.github.ooscam.data.RecordingSessionEntity>()
    val segments = mutableListOf<io.github.ooscam.data.SegmentEntity>()
    val finished = mutableListOf<Pair<String, String>>()

    override fun begin(session: io.github.ooscam.data.RecordingSessionEntity) { sessions += session }
    override fun addSegment(segment: io.github.ooscam.data.SegmentEntity) { segments += segment }
    override fun finish(sessionId: String, stopReason: String) { finished += sessionId to stopReason }
}

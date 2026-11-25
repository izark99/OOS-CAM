package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.core.StopReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class CaptureServiceStopReasonsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val engine = FakeCaptureEngine()
    private val store = FakeSessionStore()
    private lateinit var controller: ServiceController<CaptureService>

    @Before
    fun setUp() {
        CaptureDeps.engineFactory = { _, _ -> engine }
        CaptureDeps.freeBytes = { _, _ -> 50_000_000_000L }
        CaptureDeps.sessionStore = { store }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        controller = Robolectric.buildService(CaptureService::class.java).create()
    }

    @After
    fun tearDown() = CaptureDeps.reset()

    private fun start() = controller.withIntent(CaptureService.startIntent(app, CaptureConfig())).startCommand(0, 1)

    private fun stopIntent() = Intent(app, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)

    @Test
    fun notification_afterRollover_showsTheNewSegmentNumber() {
        start()
        engine.listener!!.onSegmentStarted(2)

        val posted = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(CaptureNotifications.NOTIFICATION_ID)
        assertEquals("Segment 2", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(CaptureState.Recording(CaptureService.state.value.let { (it as CaptureState.Recording).startedAtWallMs }, 2), CaptureService.state.value)
    }

    @Test
    fun notificationStopAction_sendsStopToTheCaptureService() {
        start()
        val action = shadowOf(controller.get()).lastForegroundNotification!!.actions.single()
        val sent = shadowOf(action.actionIntent).savedIntent
        assertEquals(CaptureService.ACTION_STOP, sent.action)
        assertEquals(CaptureService::class.java.name, sent.component?.className)
        assertTrue(shadowOf(action.actionIntent).isServiceIntent)
    }

    @Test
    fun engineFailure_stopsWithErrorRecordsItAndRemovesTheNotification() {
        start()
        engine.listener!!.onStopped(error = true)

        assertEquals(CaptureState.Stopped(StopReason.ERROR), CaptureService.state.value)
        assertEquals("error", store.finished.single().second)
        assertTrue(shadowOf(controller.get()).isForegroundStopped)
    }

    @Test
    fun userStopWhileLowStorageStopIsFinalizing_keepsLowStorage() {
        CaptureDeps.freeBytes = { _, _ -> 1_000_000L }
        start()
        controller.withIntent(stopIntent()).startCommand(0, 2)
        engine.listener!!.onStopped(error = false)

        assertEquals(CaptureState.Stopped(StopReason.LOW_STORAGE), CaptureService.state.value)
        assertEquals("low_storage", store.finished.single().second)
    }

    @Test
    fun callAlreadyActiveAtStart_stopsWithPhoneCall() {
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        start()

        assertEquals(CaptureState.Stopping(StopReason.PHONE_CALL), CaptureService.state.value)
        assertTrue(engine.stopRequested)
    }

    @Test
    fun stopWithoutASession_doesNothing() {
        controller.withIntent(stopIntent()).startCommand(0, 1)

        assertEquals(false, engine.started)
        assertEquals(false, engine.stopRequested)
        assertTrue(store.finished.isEmpty())
    }

    @Test
    fun secondStartWhileRecording_isIgnoredAndKeepsOneSession() {
        start()
        controller.withIntent(CaptureService.startIntent(app, CaptureConfig())).startCommand(0, 2)

        assertEquals(1, store.sessions.size)
    }
}

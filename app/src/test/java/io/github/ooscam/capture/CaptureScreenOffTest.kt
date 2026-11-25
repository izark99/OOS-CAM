package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
class CaptureScreenOffTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() = CaptureDeps.reset()

    @Test
    fun service_holdsPartialWakeLockWhileRecordingAndReleasesAfterStop() {
        val engine = FakeCaptureEngine()
        CaptureDeps.engineFactory = { _, _ -> engine }
        CaptureDeps.freeBytes = { _, _ -> 50_000_000_000L }
        CaptureDeps.sessionStore = { FakeSessionStore() }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        assertNull(ShadowPowerManager.getLatestWakeLock())

        controller.withIntent(CaptureService.startIntent(app, CaptureConfig())).startCommand(0, 1)
        val lock = ShadowPowerManager.getLatestWakeLock()!!
        assertTrue(lock.isHeld)

        controller.withIntent(Intent(app, CaptureService::class.java).setAction(CaptureService.ACTION_STOP)).startCommand(0, 2)
        assertTrue("held until the last segment is finalized", lock.isHeld)
        engine.listener!!.onStopped(error = false)
        assertFalse(lock.isHeld)
    }

    @Test
    fun service_destroyedWhileRecording_releasesWakeLock() {
        CaptureDeps.engineFactory = { _, _ -> FakeCaptureEngine() }
        CaptureDeps.freeBytes = { _, _ -> 50_000_000_000L }
        CaptureDeps.sessionStore = { FakeSessionStore() }
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val controller = Robolectric.buildService(CaptureService::class.java).create()
        controller.withIntent(CaptureService.startIntent(app, CaptureConfig())).startCommand(0, 1)

        controller.destroy()
        assertFalse(ShadowPowerManager.getLatestWakeLock()!!.isHeld)
    }

    @Test
    fun screenOff_enter_dimsToBlackAndLetsTheSystemTurnTheScreenOff() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val screen = ScreenOffController(activity.window)
        screen.keepScreenOnWhileVisible()
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)

        screen.enter()

        assertTrue(screen.isDimmed)
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF, activity.window.attributes.screenBrightness)
        assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @Test
    fun screenOff_exit_restoresSystemBrightnessAndKeepScreenOn() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val screen = ScreenOffController(activity.window)
        screen.enter()

        screen.exit()

        assertFalse(screen.isDimmed)
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, activity.window.attributes.screenBrightness)
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
    }
}

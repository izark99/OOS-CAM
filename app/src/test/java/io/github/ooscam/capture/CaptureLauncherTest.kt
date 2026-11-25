package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class CaptureLauncherTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun start_fromResumedActivity_startsCaptureService() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertTrue(CaptureLauncher.start(activity, CaptureConfig()))
        val started = shadowOf(app).nextStartedService
        assertEquals(CaptureService::class.java.name, started.component?.className)
    }

    @Test
    fun start_fromActivityInBackground_isRefused() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().pause().stop().get()

        assertFalse(CaptureLauncher.start(activity, CaptureConfig()))
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun start_withoutCameraPermission_isRefused() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertFalse(CaptureLauncher.start(activity, CaptureConfig()))
        assertNull(shadowOf(app).nextStartedService)
    }
}

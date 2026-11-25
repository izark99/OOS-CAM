package io.github.ooscam.capture

import android.Manifest
import android.app.Application
import android.media.AudioManager
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.github.ooscam.core.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

private const val MB = 1_000_000L

@RunWith(RobolectricTestRunner::class)
class CaptureMonitorsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val stops = mutableListOf<StopReason>()

    private fun idle(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    @Test
    fun freeSpaceSampler_samplesEvery5sAndStopsOnFirstSampleBelow500Mb() {
        val free = ArrayDeque(listOf(900 * MB, 700 * MB, 499 * MB, 100 * MB))
        var reads = 0
        val sampler = FreeSpaceSampler(freeBytes = { reads++; free.removeFirst() }) { stops += it }
        sampler.start()
        assertEquals("first sample right away", 1, reads)
        idle(4)
        assertEquals(1, reads)
        idle(1)
        assertEquals(2, reads)
        idle(5)
        assertEquals(listOf(StopReason.LOW_STORAGE), stops)
        idle(30)
        assertEquals("no samples after the stop", 3, reads)
    }

    @Test
    fun freeSpaceSampler_atExactly500Mb_keepsRecording() {
        val sampler = FreeSpaceSampler(freeBytes = { 500 * MB }) { stops += it }
        sampler.start()
        idle(60)
        assertTrue(stops.isEmpty())
        sampler.stop()
    }

    @Test
    fun freeSpaceSampler_stop_endsSampling() {
        var reads = 0
        val sampler = FreeSpaceSampler(freeBytes = { reads++; 10_000 * MB }) { stops += it }
        sampler.start()
        sampler.stop()
        idle(60)
        assertEquals(1, reads)
    }

    @Test
    fun thermal_stopsAtSevereOrHigherOnly() {
        assertFalse(isThermalStop(PowerManager.THERMAL_STATUS_MODERATE))
        assertTrue(isThermalStop(PowerManager.THERMAL_STATUS_SEVERE))
        assertTrue(isThermalStop(PowerManager.THERMAL_STATUS_CRITICAL))
        assertTrue(isThermalStop(PowerManager.THERMAL_STATUS_SHUTDOWN))
    }

    @Test
    fun thermalMonitor_whenAlreadySevereAtStart_stopsWithThermal() {
        shadowOf(app.getSystemService(PowerManager::class.java)).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_SEVERE)
        ThermalMonitor(app) { stops += it }.start()
        assertEquals(listOf(StopReason.THERMAL), stops)
    }

    @Test
    fun callModes_inCallOrCommunication_stopRingingDoesNot() {
        assertTrue(isCallMode(AudioManager.MODE_IN_CALL))
        assertTrue(isCallMode(AudioManager.MODE_IN_COMMUNICATION))
        assertFalse(isCallMode(AudioManager.MODE_NORMAL))
        assertFalse(isCallMode(AudioManager.MODE_RINGTONE))
    }

    @Test
    fun callMonitor_withoutPhoneStatePermission_skipsTelephonyCallback() {
        assertFalse(CallMonitor(app) { stops += it }.usesTelephony())
        shadowOf(app).grantPermissions(Manifest.permission.READ_PHONE_STATE)
        assertTrue(CallMonitor(app) { stops += it }.usesTelephony())
    }

    @Test
    fun callMonitor_whenAlreadyInCallAtStart_stopsWithPhoneCall() {
        app.getSystemService(AudioManager::class.java).mode = AudioManager.MODE_IN_CALL
        CallMonitor(app) { stops += it }.start()
        assertEquals(listOf(StopReason.PHONE_CALL), stops)
    }
}

package io.github.ooscam.capture

import android.content.Context
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Partial wake lock held for the whole session so capture continues with the screen off. */
class CaptureWakeLock(context: Context) {
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
        .apply { setReferenceCounted(false) }

    fun acquire() {
        // Bounded so a crash can never keep the CPU awake for good; longer than any session.
        lock.acquire(MAX_HOLD_MS)
    }

    fun release() {
        if (lock.isHeld) lock.release()
    }

    private companion object {
        const val TAG = "OOSCam:capture"
        const val MAX_HOLD_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * "Screen off" (C4): dims the window to black and drops FLAG_KEEP_SCREEN_ON so the system screen
 * timeout turns the display off. Recording continues in the foreground service.
 */
class ScreenOffController(private val window: Window) {
    var isDimmed: Boolean = false
        private set

    /** While the camera screen is visible and not dimmed, keep the display on. */
    fun keepScreenOnWhileVisible() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun enter() {
        isDimmed = true
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF)
    }

    fun exit() {
        isDimmed = false
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        keepScreenOnWhileVisible()
    }

    private fun setBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }
}

/** Full-screen black layer shown while dimmed; a tap wakes the camera screen again. */
@Composable
fun ScreenOffOverlay(onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onTap),
    )
}

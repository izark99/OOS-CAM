package io.github.ooscam.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import io.github.ooscam.core.StopReason
import io.github.ooscam.core.isLowStorage

/** Something that can end a recording session on its own. */
interface StopMonitor {
    fun start()

    fun stop()
}

private const val BYTES_PER_MB = 1_000_000L
const val FREE_SPACE_INTERVAL_MS = 5_000L

/** Samples free space at start and then every 5 s; the first sample below 500 MB stops the session. */
class FreeSpaceSampler(
    private val freeBytes: () -> Long,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onStop: (StopReason) -> Unit,
) : StopMonitor {
    private var running = false
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (isLowStorage(freeBytes() / BYTES_PER_MB)) {
                running = false
                onStop(StopReason.LOW_STORAGE)
            } else {
                handler.postDelayed(this, FREE_SPACE_INTERVAL_MS)
            }
        }
    }

    override fun start() {
        if (running) return
        running = true
        tick.run()
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }
}

fun isThermalStop(status: Int): Boolean = status >= PowerManager.THERMAL_STATUS_SEVERE

/** Stops the session with reason thermal when the device reports SEVERE or worse. */
class ThermalMonitor(context: Context, private val onStop: (StopReason) -> Unit) : StopMonitor {
    private val power = context.getSystemService(PowerManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private var fired = false
    private val listener = PowerManager.OnThermalStatusChangedListener { status -> check(status) }

    private var registered = false

    override fun start() {
        // Some devices have no thermal service; the platform then throws. Keep recording.
        registered = runCatching { power.addThermalStatusListener(executor, listener) }.isSuccess
        check(power.currentThermalStatus)
    }

    override fun stop() {
        if (registered) runCatching { power.removeThermalStatusListener(listener) }
        registered = false
    }

    private fun check(status: Int) {
        if (!fired && isThermalStop(status)) {
            fired = true
            onStop(StopReason.THERMAL)
        }
    }
}

fun isCallMode(mode: Int): Boolean =
    mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
        mode == AudioManager.MODE_CALL_SCREENING

/**
 * Stops the session with reason phone_call. The audio mode listener needs no permission; the
 * TelephonyCallback is used only when READ_PHONE_STATE is already granted, otherwise skipped.
 */
class CallMonitor(private val context: Context, private val onStop: (StopReason) -> Unit) : StopMonitor {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private var fired = false
    private val modeListener = AudioManager.OnModeChangedListener { mode -> if (isCallMode(mode)) fire() }
    private var telephonyCallback: TelephonyCallback? = null
    private var modeRegistered = false

    fun usesTelephony(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Registered only after the READ_PHONE_STATE check above.
    override fun start() {
        audio.addOnModeChangedListener(executor, modeListener)
        modeRegistered = true
        if (usesTelephony()) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    if (state == TelephonyManager.CALL_STATE_OFFHOOK) fire()
                }
            }
            runCatching { context.getSystemService(TelephonyManager::class.java).registerTelephonyCallback(executor, callback) }
                .onSuccess { telephonyCallback = callback }
        }
        if (isCallMode(audio.mode)) fire()
    }

    override fun stop() {
        if (modeRegistered) audio.removeOnModeChangedListener(modeListener)
        modeRegistered = false
        telephonyCallback?.let { context.getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(it) }
        telephonyCallback = null
    }

    private fun fire() {
        if (!fired) {
            fired = true
            onStop(StopReason.PHONE_CALL)
        }
    }
}

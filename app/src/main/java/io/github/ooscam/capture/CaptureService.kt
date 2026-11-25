package io.github.ooscam.capture

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import io.github.ooscam.core.StopReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Foreground service type for this session: microphone only when RECORD_AUDIO is granted now. */
fun captureForegroundType(micGranted: Boolean): Int {
    var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    if (micGranted) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    return type
}

/**
 * Records video in a camera|microphone foreground service so capture continues with the screen
 * off. Start it only through [CaptureLauncher] (a visible activity). The ongoing notification
 * with a Stop action is shown for the whole session.
 */
class CaptureService : LifecycleService(), CaptureEngine.Listener {
    private val machine = CaptureStateMachine()
    private var engine: CaptureEngine? = null
    private var monitors: List<StopMonitor> = emptyList()
    private val wakeLock by lazy { CaptureWakeLock(this) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> onStartRequested(CaptureConfig.readFrom(intent))
            ACTION_STOP -> requestStop(StopReason.USER)
        }
        return START_NOT_STICKY
    }

    private fun onStartRequested(config: CaptureConfig) {
        if (machine.isActive) return
        if (!isGranted(Manifest.permission.CAMERA)) {
            stopSelf()
            return
        }
        val micGranted = isGranted(Manifest.permission.RECORD_AUDIO)
        val startedAt = CaptureDeps.wallClockMs()
        machine.start(startedAt)
        publish()
        CaptureNotifications.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            CaptureNotifications.NOTIFICATION_ID,
            CaptureNotifications.build(this, machine.state as CaptureState.Recording),
            captureForegroundType(micGranted),
        )
        wakeLock.acquire()
        val engine = CaptureDeps.engineFactory(this, this)
        this.engine = engine
        val startLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAt), ZoneId.systemDefault())
        engine.start(config.copy(micWanted = config.micWanted && micGranted), startLocal, this)
        monitors = listOf(
            FreeSpaceSampler(freeBytes = { CaptureDeps.freeBytes(this, config) }, onStop = ::requestStop),
            ThermalMonitor(this, ::requestStop),
            CallMonitor(this, ::requestStop),
        )
        monitors.forEach { if (machine.state is CaptureState.Recording) it.start() }
    }

    fun requestStop(reason: StopReason) {
        if (machine.requestStop(reason)) {
            publish()
            engine?.stop()
        }
    }

    override fun onSegmentStarted(index: Int) {
        machine.segmentStarted(index)
        publish()
        val recording = machine.state as? CaptureState.Recording ?: return
        getSystemService(NotificationManager::class.java)
            .notify(CaptureNotifications.NOTIFICATION_ID, CaptureNotifications.build(this, recording))
    }

    override fun onStopped(error: Boolean) {
        stopMonitors()
        machine.finished(error)
        publish()
        engine = null
        wakeLock.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopMonitors() {
        monitors.forEach { it.stop() }
        monitors = emptyList()
    }

    override fun onDestroy() {
        stopMonitors()
        if (machine.isActive) {
            machine.requestStop(StopReason.ERROR)
            engine?.stop()
            machine.finished(error = true)
            publish()
        }
        wakeLock.release()
        super.onDestroy()
    }

    private fun publish() {
        mutableState.value = machine.state
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "io.github.ooscam.capture.START"
        const val ACTION_STOP = "io.github.ooscam.capture.STOP"

        private val mutableState = MutableStateFlow<CaptureState>(CaptureState.Idle)

        /** Latest state of the current (or last) session, for the camera screen. */
        val state: StateFlow<CaptureState> = mutableState.asStateFlow()

        fun startIntent(context: Context, config: CaptureConfig): Intent =
            config.writeTo(Intent(context, CaptureService::class.java).setAction(ACTION_START))
    }
}

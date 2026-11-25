package io.github.ooscam.spike

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.PowerManager
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import io.github.ooscam.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 0 spike, throwaway. Records [SEGMENT_COUNT] segments of [SEGMENT_TARGET_MS] back to back
 * in a camera|microphone foreground service and logs the gap between them.
 * Start it only from a visible activity, with CAMERA granted.
 */
class SpikeCaptureService : LifecycleService() {
    private val callbackExecutor by lazy { ContextCompat.getMainExecutor(this) }
    private val loop = SegmentLoop(SEGMENT_TARGET_MS * NS_PER_MS, SEGMENT_COUNT)
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false
    private var stamp = ""

    private var segmentStartNs = 0L
    private var prevStartNs = 0L
    private var prevRecordedNs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            SpikeLog.i(logLine("event" to "stop_requested"))
            loop.requestStop()
            val active = recording
            if (active != null) active.stop() else finishService()
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        if (!isGranted(Manifest.permission.CAMERA)) {
            SpikeLog.e(logLine("event" to "no_camera_permission"))
            stopSelf()
            return START_NOT_STICKY
        }
        started = true
        // The foreground type must only name permissions that are granted right now.
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (isGranted(Manifest.permission.RECORD_AUDIO)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        acquireWakeLock()
        stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        SpikeLog.i(logLine("event" to "service_started", "fgs_type" to type))
        bindCameraThenRecord()
        return START_NOT_STICKY
    }

    private fun bindCameraThenRecord() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, capture)
            startSegment(1)
        }, callbackExecutor)
    }

    private fun startSegment(index: Int) {
        val capture = videoCapture ?: return
        val dir = getExternalFilesDir(SPIKE_DIR) ?: filesDir
        val file = File(dir, "spike_${stamp}_part%02d.mp4".format(index))
        var pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(callbackExecutor) { event -> onRecordEvent(index, file, event) }
    }

    private fun onRecordEvent(index: Int, file: File, event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                segmentStartNs = SystemClock.elapsedRealtimeNanos()
                val gap = if (index > 1) segmentGapMs(prevStartNs, prevRecordedNs, segmentStartNs) else null
                SpikeLog.i(logLine("event" to "segment_start", "segment" to index, "gap_ms" to (gap ?: "n/a")))
            }
            is VideoRecordEvent.Status -> {
                if (loop.onStatus(event.recordingStats.recordedDurationNanos) == LoopAction.StopCurrent) {
                    recording?.stop()
                }
            }
            is VideoRecordEvent.Finalize -> {
                val recordedNs = event.recordingStats.recordedDurationNanos
                SpikeLog.i(
                    logLine(
                        "event" to "segment_finalize",
                        "segment" to index,
                        "recorded_ms" to recordedNs / NS_PER_MS,
                        "error" to event.error,
                        "bytes" to event.recordingStats.numBytesRecorded,
                    ),
                )
                prevStartNs = segmentStartNs
                prevRecordedNs = recordedNs
                recording = null
                if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) SpikeLog.segments.add(file)
                when (val action = loop.onFinalize(event.error)) {
                    is LoopAction.StartNext -> startSegment(action.index)
                    LoopAction.Done -> finishService()
                    is LoopAction.Failed -> {
                        SpikeLog.e(logLine("event" to "segment_failed", "error" to action.errorCode))
                        finishService()
                    }
                    else -> Unit
                }
            }
            else -> Unit
        }
    }

    private fun finishService() {
        SpikeLog.i(logLine("event" to "service_finished", "segments" to SpikeLog.segments.size))
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        cameraProvider?.unbindAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OOSCamSpike:record").apply {
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.spike_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, SpikeCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_spike_record)
            .setContentTitle(getString(R.string.spike_notification_title))
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .addAction(0, getString(R.string.spike_notification_stop), stopIntent)
            .build()
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "io.github.ooscam.spike.STOP"
        const val SPIKE_DIR = "spike"
        private const val CHANNEL_ID = "spike_recording"
        private const val NOTIFICATION_ID = 1001
        private const val NS_PER_MS = 1_000_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 5 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpikeCaptureService::class.java))
        }
    }
}

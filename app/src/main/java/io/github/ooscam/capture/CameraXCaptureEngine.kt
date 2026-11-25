package io.github.ooscam.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.ooscam.core.Quality
import io.github.ooscam.core.segmentCapMs
import java.time.LocalDateTime
import java.util.concurrent.Executor
import androidx.camera.video.Quality as CxQuality

/** CameraX engine: binds VideoCapture to the service lifecycle and runs a [RecorderController]. */
class CameraXCaptureEngine(
    private val context: Context,
    private val owner: LifecycleOwner,
    private val sinkFactory: (CaptureConfig) -> SegmentSink = {
        CaptureStorage.sinkFor(context, CaptureStorageChooser.choose(context, it.target))
    },
) : CaptureEngine {
    private val executor: Executor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var controller: RecorderController? = null
    private var stopBeforeBind = false

    override fun start(config: CaptureConfig, sessionStartLocal: LocalDateTime, listener: CaptureEngine.Listener) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull()
            if (provider == null || stopBeforeBind) {
                listener.onStopped(error = provider == null)
                return@addListener
            }
            this.provider = provider
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(config.quality.toCameraX(), FallbackStrategy.lowerQualityOrHigherThan(CxQuality.SD)))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            val selector = if (config.lens == Lens.FRONT) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val bound = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, capture)
            }
            if (bound.isFailure) {
                listener.onStopped(error = true)
                return@addListener
            }
            val controller = RecorderController(
                capMs = segmentCapMs(config.segmentMinutes, config.target, config.quality, config.micWanted),
                sessionStartLocal = sessionStartLocal,
                audio = config.micWanted,
                recorder = CameraXSegmentRecorder(context, capture, executor),
                sink = sinkFactory(config),
                listener = object : RecorderController.Listener {
                    override fun onSegmentStarted(index: Int) = listener.onSegmentStarted(index)

                    override fun onSegmentFinalized(record: SegmentRecord) = listener.onSegmentFinalized(record)

                    override fun onFinished(error: Boolean) {
                        provider.unbindAll()
                        listener.onStopped(error)
                    }
                },
            )
            this.controller = controller
            controller.start()
        }, executor)
    }

    override fun stop() {
        val c = controller
        if (c == null) stopBeforeBind = true else c.stop()
    }
}

private fun Quality.toCameraX(): CxQuality = when (this) {
    Quality.Q720P30 -> CxQuality.HD
    Quality.Q1080P30 -> CxQuality.FHD
    Quality.Q2160P30 -> CxQuality.UHD
}

/** One CameraX [Recording] per segment. */
class CameraXSegmentRecorder(
    private val context: Context,
    private val capture: VideoCapture<Recorder>,
    private val executor: Executor,
) : SegmentRecorder {
    private var recording: Recording? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked right before withAudioEnabled().
    override fun startSegment(output: SegmentOutput, audio: Boolean, onEvent: (RecorderEvent) -> Unit) {
        var pending: PendingRecording = when (val h = output.handle) {
            is MediaStoreOutputOptions -> capture.output.prepareRecording(context, h)
            is FileDescriptorOutputOptions -> capture.output.prepareRecording(context, h)
            is FileOutputOptions -> capture.output.prepareRecording(context, h)
            else -> error("unsupported segment output")
        }
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (audio && micGranted) pending = pending.withAudioEnabled()
        recording = pending.start(executor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onEvent(RecorderEvent.Started)
                is VideoRecordEvent.Status -> onEvent(RecorderEvent.Progress(event.recordingStats.recordedDurationNanos / NS_PER_MS))
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    onEvent(
                        RecorderEvent.Finalized(
                            ok = event.error == VideoRecordEvent.Finalize.ERROR_NONE,
                            recordedMs = event.recordingStats.recordedDurationNanos / NS_PER_MS,
                            bytes = event.recordingStats.numBytesRecorded,
                        ),
                    )
                }
                else -> Unit
            }
        }
    }

    override fun stopSegment() {
        recording?.stop()
    }

    private companion object {
        const val NS_PER_MS = 1_000_000L
    }
}

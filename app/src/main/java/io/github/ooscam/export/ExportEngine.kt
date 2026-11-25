package io.github.ooscam.export

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import io.github.ooscam.core.ExportPlan
import io.github.ooscam.core.exportFileName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDateTime

/** Runs a Composition into a file (Media3 Transformer on the device, a fake in tests). */
@OptIn(UnstableApi::class)
interface ExportRunner {
    interface Listener {
        fun onCompleted()

        fun onError(code: String)
    }

    fun start(composition: Composition, outputPath: String, listener: Listener)

    /** Percent done, or null when not known right now. */
    fun progress(): Int?

    fun cancel()
}

/** Moves a finished export into the library folder; returns its URI. */
fun interface ExportPublisher {
    fun publish(file: File, displayName: String): String
}

sealed interface ExportState {
    data object Idle : ExportState

    data class Running(val percent: Int) : ExportState

    data class Done(val uri: String) : ExportState

    data class Failed(val code: String) : ExportState

    data object Cancelled : ExportState
}

/** Export: plan → Composition → temp file → MediaStore Movies/OOS Cam as OOSCam_edit_YYYYMMDD_HHMMSS.mp4. */
@OptIn(UnstableApi::class)
class ExportEngine(private val runner: ExportRunner, private val publisher: ExportPublisher, private val workDir: File) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state
    private var temp: File? = null

    /** [plan] is the ExportPlan :core returned; the export is built from it alone. */
    fun start(plan: ExportPlan, now: LocalDateTime) {
        if (_state.value is ExportState.Running) return
        if (!plan.ok) {
            _state.value = ExportState.Failed(plan.errors.joinToString(","))
            return
        }
        val name = exportFileName(now)
        val file = File(workDir, name).also { it.parentFile?.mkdirs(); it.delete() }
        val composition = CompositionMapper.map(plan)
        temp = file
        _state.value = ExportState.Running(0)
        runner.start(
            composition,
            file.absolutePath,
            object : ExportRunner.Listener {
                override fun onCompleted() {
                    if (_state.value !is ExportState.Running) return
                    _state.value = try {
                        ExportState.Done(publisher.publish(file, name))
                    } catch (e: Exception) {
                        ExportState.Failed(PUBLISH_FAILED)
                    } finally {
                        file.delete()
                    }
                }

                override fun onError(code: String) {
                    if (_state.value !is ExportState.Running) return
                    file.delete()
                    _state.value = ExportState.Failed(code)
                }
            },
        )
    }

    fun pollProgress() {
        if (_state.value !is ExportState.Running) return
        runner.progress()?.let { _state.value = ExportState.Running(it.coerceIn(0, 100)) }
    }

    fun cancel() {
        if (_state.value !is ExportState.Running) return
        runner.cancel()
        temp?.delete()
        _state.value = ExportState.Cancelled
    }

    /** Back to Idle after Done, Failed or Cancelled. */
    fun reset() {
        if (_state.value !is ExportState.Running) _state.value = ExportState.Idle
    }

    companion object {
        const val PUBLISH_FAILED = "publish_failed"
    }
}

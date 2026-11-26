package io.github.ooscam.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ooscam.R
import io.github.ooscam.camera.formatElapsed
import io.github.ooscam.core.ProjectClip
import io.github.ooscam.core.SpeedRange
import io.github.ooscam.export.ExportState
import java.math.BigDecimal

/** Speeds offered in the editor (SPEC E3); planExport rejects anything else with E4. */
val EDITOR_SPEEDS: List<BigDecimal> = listOf("0.25", "0.5", "1", "1.5", "2", "4").map(::BigDecimal)

private val STEPS = listOf(-100, -10, -1, 1, 10, 100)

@StringRes
fun messageText(code: String): Int = when (code) {
    "E1" -> R.string.editor_msg_e1
    "E2" -> R.string.editor_msg_e2
    "E3" -> R.string.editor_msg_e3
    "E4" -> R.string.editor_msg_e4
    "E5" -> R.string.editor_msg_e5
    "E6" -> R.string.editor_msg_e6
    "E7" -> R.string.editor_msg_e7
    "W1" -> R.string.editor_msg_w1
    "W2" -> R.string.editor_msg_w2
    else -> R.string.editor_msg_unknown
}

fun formatSeconds(ms: Long): String = "%d.%d".format(ms / 1_000, (ms % 1_000) / 100)

/** Interim editor (Phase 3): reorder, trim in 0.1 s steps, speed ranges, mute, export height. Phase 4 restyles it. */
@Composable
fun EditorScreen(
    state: EditorUiState,
    vm: EditorViewModel,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    exportState: ExportState = ExportState.Idle,
    onCancelExport: () -> Unit = {},
    onOpenResult: (String) -> Unit = {},
    onDismissExport: () -> Unit = {},
) {
    val project = state.project
    val clips = project.clips.sortedBy { it.order }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.library_back)) }
                Text(stringResource(R.string.editor_untitled), style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.editor_tool_muted))
                Switch(checked = project.muted, onCheckedChange = vm::setMuted)
                listOf(720, 1080).forEach { h ->
                    val label = stringResource(R.string.editor_height, h)
                    if (project.exportHeight == h) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { vm.setExportHeight(h) }) { Text(label) }
                }
            }
        }
        itemsIndexed(clips, key = { _, c -> c.order.toString() + c.sourceUri }) { position, clip ->
            ClipCard(clip, position, clips.size, project.speedRanges, vm)
        }
        item { OutlinedButton(onClick = onImport) { Text(stringResource(R.string.library_import)) } }
        items(state.messages.size) { i ->
            val m = state.messages[i]
            Text(stringResource(messageText(m.code)), color = if (m.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.plan.outputDurationMs?.let { Text(stringResource(R.string.editor_output, formatElapsed(it))) }
                Button(onClick = onExport, enabled = state.canExport && exportState !is ExportState.Running) {
                    Text(stringResource(R.string.editor_export))
                }
            }
        }
        if (exportState != ExportState.Idle) item { ExportPanel(exportState, onExport, onCancelExport, onOpenResult, onDismissExport) }
    }
}

/** Interim export sheet (SPEC E5): progress with cancel, open the result, retry after an error. */
@Composable
private fun ExportPanel(state: ExportState, onRetry: () -> Unit, onCancel: () -> Unit, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is ExportState.Running -> {
                    Text(stringResource(R.string.export_progress, state.percent))
                    LinearProgressIndicator(progress = { state.percent / 100f })
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.export_cancel)) }
                }
                is ExportState.Done -> {
                    Text(stringResource(R.string.export_done))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onOpen(state.uri) }) { Text(stringResource(R.string.export_open)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
                    }
                }
                is ExportState.Failed -> {
                    Text(stringResource(R.string.export_failed, state.code), color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.export_retry)) }
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
                    }
                }
                ExportState.Cancelled -> {
                    Text(stringResource(R.string.export_cancelled))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_close)) }
                }
                ExportState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ClipCard(clip: ProjectClip, position: Int, count: Int, ranges: List<SpeedRange>, vm: EditorViewModel) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.editor_clip_desc, (position + 1).toString(), formatSeconds(clip.sourceDurationMs) + " s"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.move(position, position - 1) }, enabled = position > 0) { Text(stringResource(R.string.editor_move_up, (position + 1).toString())) }
                TextButton(onClick = { vm.move(position, position + 1) }, enabled = position < count - 1) { Text(stringResource(R.string.editor_move_down, (position + 1).toString())) }
                TextButton(onClick = { vm.removeClip(clip.order) }) { Text(stringResource(R.string.editor_remove)) }
            }
            Text(stringResource(R.string.editor_trim_in, formatSeconds(clip.inMs)))
            Stepper { vm.nudgeIn(clip.order, it) }
            Text(stringResource(R.string.editor_trim_out, formatSeconds(clip.outMs)))
            Stepper { vm.nudgeOut(clip.order, it) }
            ranges.forEachIndexed { index, r ->
                if (r.clipOrder != clip.order) return@forEachIndexed
                Text(stringResource(R.string.editor_range, formatSeconds(r.startMs), formatSeconds(r.endMs)) + " · " + stringResource(R.string.editor_speed, r.speed.toPlainString()))
                Stepper { vm.updateSpeedRange(index, r.startMs + it * EditorViewModel.STEP_MS, r.endMs, r.speed) }
                Stepper { vm.updateSpeedRange(index, r.startMs, r.endMs + it * EditorViewModel.STEP_MS, r.speed) }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    EDITOR_SPEEDS.forEach { s ->
                        TextButton(onClick = { vm.updateSpeedRange(index, r.startMs, r.endMs, s) }) {
                            Text(stringResource(R.string.editor_speed, s.toPlainString()))
                        }
                    }
                }
                TextButton(onClick = { vm.removeSpeedRange(index) }) { Text(stringResource(R.string.editor_remove_range)) }
            }
            TextButton(onClick = {
                val third = (clip.outMs - clip.inMs) / 3
                vm.addSpeedRange(clip.order, clip.inMs + third, clip.outMs - third, BigDecimal("2"))
            }) { Text(stringResource(R.string.editor_new_range)) }
        }
    }
}

/** Buttons for -10 s, -1 s, -0.1 s, +0.1 s, +1 s, +10 s; the lambda gets the number of 0.1 s steps. */
@Composable
private fun Stepper(onSteps: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        STEPS.forEach { steps ->
            TextButton(onClick = { onSteps(steps) }) {
                Text((if (steps > 0) "+" else "−") + formatSeconds(kotlin.math.abs(steps) * EditorViewModel.STEP_MS))
            }
        }
    }
}

package io.github.ooscam.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ooscam.AppTitle
import io.github.ooscam.R
import io.github.ooscam.capture.CaptureState
import io.github.ooscam.capture.ScreenOffOverlay
import kotlinx.coroutines.delay

data class CameraUiState(
    val capture: CaptureState = CaptureState.Idle,
    val micWanted: Boolean = true,
    val showPermissionSheet: Boolean = false,
    val showNotificationBanner: Boolean = false,
    val dimmed: Boolean = false,
    val sdFolderSet: Boolean = false,
)

/** Interim camera screen (Phase 2): Record/Stop, mic toggle, Screen off, permission sheet. Phase 4 restyles it. */
@Composable
fun CameraScreen(
    state: CameraUiState,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onMicChange: (Boolean) -> Unit,
    onScreenOff: () -> Unit,
    onWake: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickSdFolder: () -> Unit,
) {
    val recording = state.capture as? CaptureState.Recording
    val active = recording != null || state.capture is CaptureState.Stopping
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppTitle()
            if (recording != null) {
                Text(stringResource(R.string.camera_elapsed, formatElapsed(elapsedSince(recording.startedAtWallMs))))
                Text(stringResource(R.string.capture_notification_segment, recording.segmentIndex))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.camera_mic))
                Switch(checked = state.micWanted, onCheckedChange = onMicChange, enabled = !active)
            }
            if (active) {
                Button(onClick = onStop, enabled = recording != null) { Text(stringResource(R.string.capture_notification_stop)) }
                OutlinedButton(onClick = onScreenOff, enabled = recording != null) { Text(stringResource(R.string.camera_screen_off)) }
            } else {
                Button(onClick = onRecord, enabled = !state.showPermissionSheet) { Text(stringResource(R.string.camera_record)) }
                OutlinedButton(onClick = onPickSdFolder) {
                    Text(stringResource(if (state.sdFolderSet) R.string.camera_storage_sd else R.string.camera_storage_pick_sd))
                }
            }
            if (state.showNotificationBanner) {
                Text(stringResource(R.string.camera_notification_banner), style = MaterialTheme.typography.bodySmall)
            }
            if (state.showPermissionSheet) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.permission_sheet_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.permission_reason_camera))
                        Text(stringResource(R.string.permission_reason_mic))
                        Text(stringResource(R.string.permission_reason_notifications))
                        Button(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
                    }
                }
            }
        }
        if (state.dimmed) ScreenOffOverlay(onTap = onWake)
    }
}

@Composable
private fun elapsedSince(startedAtWallMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtWallMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    return (now - startedAtWallMs).coerceAtLeast(0L)
}

fun formatElapsed(ms: Long): String {
    val total = ms / 1_000L
    return "%d:%02d:%02d".format(total / 3_600L, (total / 60L) % 60L, total % 60L)
}

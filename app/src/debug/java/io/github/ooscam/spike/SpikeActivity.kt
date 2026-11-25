package io.github.ooscam.spike

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ooscam.R

/** Phase 0 spike screen (debug builds only). Throwaway. */
class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SpikeScreen() } }
    }

    private fun hasCamera() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun SpikeScreen() {
        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasCamera()) SpikeCaptureService.start(this) else SpikeLog.e("event=no_camera_permission")
        }
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (hasCamera()) {
                        SpikeCaptureService.start(this@SpikeActivity)
                    } else {
                        permissions.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ),
                        )
                    }
                }) { Text(stringResource(R.string.spike_button_record)) }
                Button(onClick = {
                    startService(
                        Intent(this@SpikeActivity, SpikeCaptureService::class.java)
                            .setAction(SpikeCaptureService.ACTION_STOP),
                    )
                }) { Text(stringResource(R.string.spike_button_stop)) }
            }
            Button(onClick = { SpikeTransformerJob.run(applicationContext) }) {
                Text(stringResource(R.string.spike_button_transform))
            }
            Text(stringResource(R.string.spike_log_title))
            LazyColumn { items(SpikeLog.lines) { Text(it) } }
        }
    }
}

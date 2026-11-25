package io.github.ooscam

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.ooscam.data.OOSCamDatabase
import io.github.ooscam.library.LibraryIntents
import io.github.ooscam.library.LibraryRepository
import io.github.ooscam.library.LibraryScreen
import io.github.ooscam.library.LibraryViewModel
import io.github.ooscam.library.MediaStoreMediaSource
import java.util.concurrent.Executors
import io.github.ooscam.camera.CameraScreen
import io.github.ooscam.camera.CameraUiState
import io.github.ooscam.capture.CaptureConfig
import io.github.ooscam.capture.CaptureLauncher
import io.github.ooscam.capture.CapturePermissionPolicy
import io.github.ooscam.capture.CaptureService
import io.github.ooscam.capture.CaptureStorageChooser
import io.github.ooscam.capture.CaptureStoragePrefs
import io.github.ooscam.capture.StorageChoice
import io.github.ooscam.core.StorageTarget
import io.github.ooscam.capture.CaptureState
import io.github.ooscam.capture.PermissionSnapshot
import io.github.ooscam.capture.RecordDecision
import io.github.ooscam.capture.ScreenOffController

class MainActivity : ComponentActivity() {
    private lateinit var screenOff: ScreenOffController
    private var ui by mutableStateOf(CameraUiState())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var screen by mutableStateOf(Screen.CAMERA)
    private val io = Executors.newSingleThreadExecutor()
    private val library by lazy {
        LibraryViewModel(
            LibraryRepository(OOSCamDatabase.get(this).captureDao(), MediaStoreMediaSource(this)),
            io = io,
            main = ContextCompat.getMainExecutor(this),
        )
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startIfAllowed()
    }

    private val pickSdFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            CaptureStoragePrefs.rememberPicked(this, tree)
            ui = ui.copy(sdFolderSet = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenOff = ScreenOffController(window)
        screenOff.keepScreenOnWhileVisible()
        ui = ui.copy(sdFolderSet = CaptureStorageChooser.choose(this, StorageTarget.SD) is StorageChoice.Sd)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val capture by CaptureService.state.collectAsState()
                LaunchedEffect(capture) {
                    // Session ended (for example from the notification): leave the black screen.
                    if (capture !is CaptureState.Recording && ui.dimmed) wake()
                }
                BackHandler(enabled = screen != Screen.CAMERA) { screen = Screen.CAMERA }
                when (screen) {
                    Screen.CAMERA -> Camera(capture)
                    Screen.LIBRARY -> Library()
                }
            }
        }
    }

    @Composable
    private fun Camera(capture: CaptureState) {
                CameraScreen(
                    state = ui.copy(capture = capture),
                    onRecord = ::onRecordTap,
                    onStop = { CaptureLauncher.stop(this) },
                    onMicChange = { ui = ui.copy(micWanted = it) },
                    onScreenOff = {
                        screenOff.enter()
                        ui = ui.copy(dimmed = true)
                    },
                    onWake = ::wake,
                    onOpenSettings = ::openAppSettings,
                    onPickSdFolder = { pickSdFolder.launch(null) },
                    onOpenLibrary = ::openLibrary,
                )
    }

    @Composable
    private fun Library() {
        val state by library.state.collectAsState()
        LibraryScreen(
            state = state,
            onBack = { screen = Screen.CAMERA },
            onTap = { tile -> if (state.selecting) library.toggle(tile.key) else startActivity(LibraryIntents.play(tile.items.first().uri)) },
            onLongPress = { tile -> library.toggle(tile.key) },
            onShare = { startActivity(Intent.createChooser(LibraryIntents.share(library.selectedUris()), null)) },
            onDelete = library::requestDeleteSelected,
            onDeleteDamaged = library::requestDeleteDamaged,
            onEditSelected = {},
            onImport = {},
            onClearSelection = library::clearSelection,
            onConfirmDelete = library::confirmDelete,
            onCancelDelete = library::cancelDelete,
        )
    }

    private fun openLibrary() {
        screen = Screen.LIBRARY
        library.refresh()
    }

    override fun onResume() {
        super.onResume()
        // The user may have granted the camera in system settings.
        if (ui.showPermissionSheet && PermissionSnapshot.read(this).camera) ui = ui.copy(showPermissionSheet = false)
    }

    private fun wake() {
        screenOff.exit()
        ui = ui.copy(dimmed = false)
    }

    private fun onRecordTap() {
        val missing = CapturePermissionPolicy.toRequest(PermissionSnapshot.read(this), ui.micWanted, Build.VERSION.SDK_INT)
        if (missing.isEmpty()) startIfAllowed() else permissionRequest.launch(missing.toTypedArray())
    }

    private fun startIfAllowed() {
        val decision = CapturePermissionPolicy.decide(
            PermissionSnapshot.read(this),
            ui.micWanted,
            micToastShown = prefs.getBoolean(KEY_MIC_TOAST, false),
            sdkInt = Build.VERSION.SDK_INT,
        )
        when (decision) {
            RecordDecision.Blocked -> ui = ui.copy(showPermissionSheet = true)
            is RecordDecision.Start -> {
                if (decision.micOffToast) {
                    Toast.makeText(this, R.string.camera_mic_off_toast, Toast.LENGTH_LONG).show()
                    prefs.edit { putBoolean(KEY_MIC_TOAST, true) }
                }
                ui = ui.copy(showNotificationBanner = decision.notificationBanner, showPermissionSheet = false)
                // Without a usable SD folder this falls back to device storage (SPEC permissions matrix).
                val target = if (CaptureStorageChooser.choose(this, StorageTarget.SD) is StorageChoice.Sd) StorageTarget.SD else StorageTarget.DEVICE
                CaptureLauncher.start(this, CaptureConfig(micWanted = decision.micOn, target = target))
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private enum class Screen { CAMERA, LIBRARY }

    private companion object {
        const val PREFS = "camera"
        const val KEY_MIC_TOAST = "mic_off_toast_shown"
    }
}

@Composable
fun AppTitle() {
    Text(text = APP_TITLE, style = MaterialTheme.typography.headlineSmall)
}

const val APP_TITLE = "OOS Cam"

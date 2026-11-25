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
import io.github.ooscam.core.EditProject
import io.github.ooscam.core.exportFileName
import io.github.ooscam.data.OOSCamDatabase
import io.github.ooscam.editor.ClipSource
import io.github.ooscam.editor.EditorScreen
import io.github.ooscam.editor.EditorViewModel
import io.github.ooscam.editor.VideoImport
import io.github.ooscam.export.ExportEngine
import io.github.ooscam.export.ExportState
import io.github.ooscam.export.MediaStoreExportPublisher
import io.github.ooscam.export.TransformerExportRunner
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import java.util.UUID
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
    private var editor by mutableStateOf<EditorViewModel?>(null)
    private val export by lazy {
        ExportEngine(TransformerExportRunner(this, io), MediaStoreExportPublisher(this), File(cacheDir, "export"))
    }
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

    private val pickVideos = registerForActivityResult(VideoImport.contract()) { uris ->
        if (uris.isNotEmpty()) importPicked(uris)
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
                BackHandler(enabled = screen != Screen.CAMERA) {
                    if (screen == Screen.EDITOR) editor?.let { saveProject(it.state.value.project) }
                    screen = if (screen == Screen.EDITOR) Screen.LIBRARY else Screen.CAMERA
                }
                when (screen) {
                    Screen.CAMERA -> Camera(capture)
                    Screen.LIBRARY -> Library()
                    Screen.EDITOR -> Editor()
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
            onEditSelected = ::editSelected,
            onImport = { startImport(newProject = true) },
            onClearSelection = library::clearSelection,
            onConfirmDelete = library::confirmDelete,
            onCancelDelete = library::cancelDelete,
        )
    }

    @Composable
    private fun Editor() {
        val vm = editor ?: return
        val state by vm.state.collectAsState()
        val exportState by export.state.collectAsState()
        LaunchedEffect(exportState is ExportState.Running) {
            while (export.state.value is ExportState.Running) {
                export.pollProgress()
                delay(PROGRESS_POLL_MS)
            }
        }
        EditorScreen(
            state = state,
            vm = vm,
            onBack = {
                saveProject(state.project)
                screen = Screen.LIBRARY
            },
            onImport = { startImport(newProject = false) },
            // The plan is the one :core computed for this project; the export is built from it alone.
            onExport = {
                saveProject(state.project)
                export.start(state.plan, LocalDateTime.now())
            },
            exportState = exportState,
            onCancelExport = export::cancel,
            onOpenResult = { startActivity(LibraryIntents.play(it)) },
            onDismissExport = export::reset,
        )
    }

    /** Room keeps the project (metadata only); the name is the save time until projects can be renamed. */
    private fun saveProject(project: EditProject) {
        if (project.clips.isEmpty()) return
        val dao = OOSCamDatabase.get(this).projectDao()
        val now = System.currentTimeMillis()
        io.execute { dao.save(project, name = exportFileName(LocalDateTime.now()).removeSuffix(".mp4"), now = now) }
    }

    private fun editSelected() {
        val clips = library.editSelection().map { ClipSource(it.uri, it.displayName, it.durationMs, it.height) }
        openEditor(EditorViewModel(UUID.randomUUID().toString()).also { it.addClips(clips) })
        library.clearSelection()
    }

    private fun openEditor(vm: EditorViewModel) {
        editor = vm
        screen = Screen.EDITOR
    }

    private var importIntoNewProject = true

    private fun startImport(newProject: Boolean) {
        importIntoNewProject = newProject
        pickVideos.launch(VideoImport.request())
    }

    private fun importPicked(uris: List<Uri>) {
        VideoImport.keepAccess(this, uris)
        val media = MediaStoreMediaSource(this)
        val newProject = importIntoNewProject
        io.execute {
            val result = VideoImport.toClips(uris.map(Uri::toString), media::describe)
            ContextCompat.getMainExecutor(this).execute {
                if (result.skipped > 0) Toast.makeText(this, R.string.editor_import_skipped, Toast.LENGTH_LONG).show()
                if (result.clips.isEmpty()) return@execute
                val target = editor.takeIf { !newProject } ?: EditorViewModel(UUID.randomUUID().toString())
                target.addClips(result.clips)
                openEditor(target)
            }
        }
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

    private enum class Screen { CAMERA, LIBRARY, EDITOR }

    private companion object {
        const val PREFS = "camera"
        const val PROGRESS_POLL_MS = 250L
        const val KEY_MIC_TOAST = "mic_off_toast_shown"
    }
}

@Composable
fun AppTitle() {
    Text(text = APP_TITLE, style = MaterialTheme.typography.headlineSmall)
}

const val APP_TITLE = "OOS Cam"

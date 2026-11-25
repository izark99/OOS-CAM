package io.github.ooscam.capture

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.system.Os
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.core.content.edit
import io.github.ooscam.core.StorageTarget

data class PersistedGrant(val uri: Uri, val read: Boolean, val write: Boolean)

sealed interface StorageChoice {
    data object Device : StorageChoice

    data class Sd(val tree: Uri) : StorageChoice
}

/** Device storage, or the SD card folder the user picked while its write grant is still held. */
object CaptureStorageChooser {
    fun choose(requested: StorageTarget, sdTree: Uri?, grants: List<PersistedGrant>): StorageChoice {
        if (requested != StorageTarget.SD || sdTree == null) return StorageChoice.Device
        val writable = grants.any { it.uri == sdTree && it.write }
        return if (writable) StorageChoice.Sd(sdTree) else StorageChoice.Device
    }

    fun choose(context: Context, requested: StorageTarget): StorageChoice = choose(
        requested,
        CaptureStoragePrefs.sdTree(context),
        context.contentResolver.persistedUriPermissions.map { PersistedGrant(it.uri, it.isReadPermission, it.isWritePermission) },
    )
}

/** The SD card folder (SAF tree URI) the user picked. */
object CaptureStoragePrefs {
    private const val PREFS = "storage"
    private const val KEY_SD_TREE = "sd_tree_uri"

    fun sdTree(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SD_TREE, null)?.let(Uri::parse)

    fun setSdTree(context: Context, tree: Uri?) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SD_TREE, tree?.toString()) }

    /** Keep access to a folder picked with ACTION_OPEN_DOCUMENT_TREE across reboots. */
    fun rememberPicked(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        setSdTree(context, tree)
    }
}

object CaptureMediaStore {
    const val RELATIVE_PATH = "Movies/OOS Cam"
    const val MIME = "video/mp4"

    fun pendingValues(fileName: String) = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
        put(MediaStore.Video.Media.MIME_TYPE, MIME)
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    /** Published after finalize; an unfinalized MP4 stays visible so the library can mark it Damaged. */
    fun publishValues() = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
}

/** Writes segments to MediaStore Movies/OOS Cam through a file descriptor (URI known before start). */
class MediaStoreSegmentSink(private val context: Context) : SegmentSink {
    private val open = mutableMapOf<String, ParcelFileDescriptor>()

    override fun open(fileName: String): SegmentOutput {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = checkNotNull(resolver.insert(collection, CaptureMediaStore.pendingValues(fileName))) { "MediaStore insert failed" }
        val pfd = checkNotNull(resolver.openFileDescriptor(uri, "rw")) { "cannot open segment" }
        open[uri.toString()] = pfd
        return SegmentOutput(uri.toString(), FileDescriptorOutputOptions.Builder(pfd).build())
    }

    override fun commit(output: SegmentOutput, ok: Boolean) {
        open.remove(output.uri)?.close()
        context.contentResolver.update(Uri.parse(output.uri), CaptureMediaStore.publishValues(), null, null)
    }
}

/** Writes segments as documents inside the picked SD card folder. */
class SafSegmentSink(private val context: Context, private val tree: Uri) : SegmentSink {
    private val open = mutableMapOf<String, ParcelFileDescriptor>()

    override fun open(fileName: String): SegmentOutput {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val doc = checkNotNull(DocumentsContract.createDocument(resolver, parent, CaptureMediaStore.MIME, fileName)) {
            "cannot create document in SD folder"
        }
        val pfd = checkNotNull(resolver.openFileDescriptor(doc, "rw")) { "cannot open segment" }
        open[doc.toString()] = pfd
        return SegmentOutput(doc.toString(), FileDescriptorOutputOptions.Builder(pfd).build())
    }

    override fun commit(output: SegmentOutput, ok: Boolean) {
        open.remove(output.uri)?.close()
    }
}

object CaptureStorage {
    fun sinkFor(context: Context, choice: StorageChoice): SegmentSink = when (choice) {
        StorageChoice.Device -> MediaStoreSegmentSink(context)
        is StorageChoice.Sd -> SafSegmentSink(context, choice.tree)
    }

    /** Free bytes on the volume of the target; the SD folder is measured through its document. */
    fun freeBytes(context: Context, choice: StorageChoice): Long {
        if (choice is StorageChoice.Sd) {
            val doc = DocumentsContract.buildDocumentUriUsingTree(choice.tree, DocumentsContract.getTreeDocumentId(choice.tree))
            val sd = runCatching {
                context.contentResolver.openFileDescriptor(doc, "r")!!.use { pfd ->
                    val st = Os.fstatvfs(pfd.fileDescriptor)
                    st.f_bavail * st.f_frsize
                }
            }.getOrNull()
            if (sd != null) return sd
        }
        return android.os.StatFs((context.getExternalFilesDir(null) ?: context.filesDir).path).availableBytes
    }
}

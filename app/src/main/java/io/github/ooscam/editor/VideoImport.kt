package io.github.ooscam.editor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import io.github.ooscam.library.MediaFile

data class ImportResult(val clips: List<ClipSource>, val skipped: Int)

/**
 * Import from the system photo picker. PickMultipleVisualMedia falls back to ACTION_OPEN_DOCUMENT
 * where the picker is missing, so no storage permission (READ_MEDIA_VIDEO) is ever needed.
 */
object VideoImport {
    /** The editor's clip limit (planExport E1). */
    const val MAX_ITEMS = 10

    fun contract() = ActivityResultContracts.PickMultipleVisualMedia(MAX_ITEMS)

    fun request(): PickVisualMediaRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)

    /** Picked URIs to clips in pick order; unreadable or empty videos are skipped and counted. */
    fun toClips(uris: List<String>, describe: (String) -> MediaFile?): ImportResult {
        val clips = uris.mapNotNull { uri ->
            describe(uri)?.takeIf { it.durationMs > 0 }?.let { ClipSource(uri, it.displayName, it.durationMs, it.height) }
        }
        return ImportResult(clips, uris.size - clips.size)
    }

    /** Keeps read access for later exports when the provider allows it (document fallback); picker grants last for the session. */
    fun keepAccess(context: Context, uris: List<Uri>) {
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
}

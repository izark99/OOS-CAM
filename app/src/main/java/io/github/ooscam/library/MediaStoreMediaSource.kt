package io.github.ooscam.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import io.github.ooscam.capture.CaptureMediaStore
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** MediaStore Movies/OOS Cam plus SAF documents (SD card segments). */
class MediaStoreMediaSource(private val context: Context) : MediaSource {
    private val resolver get() = context.contentResolver
    private val collection: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    override fun listFolder(): List<MediaFile> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("${CaptureMediaStore.RELATIVE_PATH}%")
        val files = mutableListOf<MediaFile>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                files += MediaFile(
                    uri = ContentUris.withAppendedId(collection, c.getLong(0)).toString(),
                    displayName = c.getString(1) ?: "",
                    durationMs = c.getLong(2),
                    height = c.getInt(3),
                    bytes = c.getLong(4),
                    addedAt = c.getLong(5) * 1000,
                    inOosCamFolder = true,
                )
            }
        }
        return files
    }

    override fun describe(uri: String): MediaFile? {
        val parsed = Uri.parse(uri)
        val (name, size) = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "") to c.getLong(1) else null
            }
        }.getOrNull() ?: return null
        val (duration, height) = metadata(parsed)
        return MediaFile(uri, name, duration, height, size, 0, inOosCamFolder = false)
    }

    override fun isFinalized(uri: String): Boolean = runCatching {
        resolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { Mp4Probe.isFinalized(ChannelSource(it)) }
        } ?: false
    }.getOrDefault(false)

    override fun delete(uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        if (DocumentsContract.isDocumentUri(context, parsed)) {
            DocumentsContract.deleteDocument(resolver, parsed)
        } else {
            resolver.delete(parsed, null, null) > 0
        }
    }.getOrDefault(false)

    private fun metadata(uri: Uri): Pair<Long, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            duration to height
        } catch (_: RuntimeException) {
            0L to 0
        } finally {
            retriever.release()
        }
    }
}

/** Reads a file channel at absolute positions (the probe only reads box headers). */
class ChannelSource(private val channel: FileChannel) : ByteSource {
    override val size: Long get() = channel.size()

    override fun read(position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        var read = 0
        while (read < length) {
            val n = channel.read(buffer, position + read)
            if (n <= 0) break
            read += n
        }
        return buffer.array().copyOf(read)
    }
}

package io.github.ooscam.export

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import io.github.ooscam.capture.CaptureMediaStore
import java.io.File
import java.util.concurrent.Executor

/** Media3 Transformer, H.264 video and AAC audio (SPEC editor row). Call from the main thread. */
@OptIn(UnstableApi::class)
class TransformerExportRunner(
    private val context: Context,
    /** Where completion runs: publishing copies the file, so not on the main thread. */
    private val completion: Executor,
) : ExportRunner {
    private var transformer: Transformer? = null
    private val holder = ProgressHolder()

    override fun start(composition: Composition, outputPath: String, listener: ExportRunner.Listener) {
        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) = completion.execute { listener.onCompleted() }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                    listener.onError(exportException.errorCodeName)
            })
            .build()
            .also { it.start(composition, outputPath) }
    }

    override fun progress(): Int? {
        val t = transformer ?: return null
        return if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null
    }

    override fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}

/** Copies the finished file into MediaStore Movies/OOS Cam (pending until fully written). */
class MediaStoreExportPublisher(private val context: Context) : ExportPublisher {
    override fun publish(file: File, displayName: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri = checkNotNull(resolver.insert(collection, CaptureMediaStore.pendingValues(displayName))) { "MediaStore insert failed" }
        try {
            checkNotNull(resolver.openOutputStream(uri)) { "cannot open export output" }.use { out -> file.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, CaptureMediaStore.publishValues(), null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }
}

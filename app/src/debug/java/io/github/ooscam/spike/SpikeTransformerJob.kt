package io.github.ooscam.spike

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/** Phase 0 spike, throwaway. Runs the join + trim + 2x + remove-audio job and logs its time. Main thread only. */
@OptIn(UnstableApi::class)
object SpikeTransformerJob {
    fun run(context: Context) {
        val files = SpikeLog.segments.toList()
        if (files.size < 2) {
            SpikeLog.i(logLine("event" to "transform_skipped", "reason" to "record first", "segments" to files.size))
            return
        }
        val durations = files.map { durationMs(it) }
        val parts = spikeJobParts(durations)
        if (parts.isEmpty()) {
            SpikeLog.i(logLine("event" to "transform_skipped", "reason" to "clips too short", "durations_ms" to durations))
            return
        }
        val items = parts.map { part ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(part.startMs)
                .setEndPositionMs(part.endMs)
                .build()
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(files[part.clip]))
                .setClippingConfiguration(clipping)
                .build()
            val effects = if (part.speed == 1f) {
                Effects(emptyList(), emptyList())
            } else {
                Effects(emptyList(), listOf(SpeedChangeEffect(part.speed)))
            }
            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(SPIKE_REMOVE_AUDIO)
                .setEffects(effects)
                .build()
        }
        val composition = Composition.Builder(EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO)).addItems(items).build()).build()
        val output = File(files[0].parentFile, "spike_export.mp4").also { it.delete() }
        val startedAt = SystemClock.elapsedRealtime()
        SpikeLog.i(logLine("event" to "transform_start", "parts" to parts.size, "expected_out_ms" to expectedOutputMs(parts)))
        Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    SpikeLog.i(
                        logLine(
                            "event" to "transform_done",
                            "elapsed_ms" to SystemClock.elapsedRealtime() - startedAt,
                            "out_bytes" to output.length(),
                        ),
                    )
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    SpikeLog.e(
                        logLine(
                            "event" to "transform_error",
                            "elapsed_ms" to SystemClock.elapsedRealtime() - startedAt,
                            "code" to exportException.errorCodeName,
                        ),
                    )
                }
            })
            .build()
            .start(composition, output.absolutePath)
    }

    private fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }
}

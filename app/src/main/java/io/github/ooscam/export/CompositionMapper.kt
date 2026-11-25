package io.github.ooscam.export

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import io.github.ooscam.core.ExportPlan

/**
 * Builds the Media3 Composition from an ExportPlan computed by :core, and from nothing else:
 * sources, order, trim, speed, audio and height all come from the plan.
 */
@OptIn(UnstableApi::class)
object CompositionMapper {
    fun map(plan: ExportPlan): Composition {
        require(plan.ok) { "plan has errors ${plan.errors}" }
        require(plan.parts.isNotEmpty()) { "plan has no parts" }
        val items = plan.parts.map { part ->
            val uri = requireNotNull(plan.sourceUris[part.clipOrder]) { "no source for clip ${part.clipOrder}" }
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(part.srcStartMs)
                .setEndPositionMs(part.srcEndMs)
                .build()
            val mediaItem = MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(clipping).build()
            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(!plan.audio)
                .setSpeed(ConstantSpeed(part.speed.toFloat()))
                .build()
        }
        // Declaring the audio track type makes the sequence output audio for every item; clips recorded
        // without the mic get silence instead of breaking the join.
        val tracks = if (plan.audio) setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO) else setOf(C.TRACK_TYPE_VIDEO)
        val sequence = EditedMediaItemSequence.Builder(tracks).addItems(items).build()
        return Composition.Builder(sequence)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(plan.exportHeight))))
            .build()
    }
}

/** One speed for the whole item (a plan part has exactly one speed). */
@OptIn(UnstableApi::class)
class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

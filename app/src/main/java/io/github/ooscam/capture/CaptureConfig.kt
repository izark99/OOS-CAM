package io.github.ooscam.capture

import android.content.Intent
import io.github.ooscam.core.Quality
import io.github.ooscam.core.StorageTarget

enum class Lens { BACK, FRONT }

/** What one recording session uses. Defaults follow docs/SPEC.md (1080p30, mic on, 30 min). */
data class CaptureConfig(
    val lens: Lens = Lens.BACK,
    val quality: Quality = Quality.Q1080P30,
    val micWanted: Boolean = true,
    val segmentMinutes: Int = 30,
    val target: StorageTarget = StorageTarget.DEVICE,
) {
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_LENS, lens.name)
        .putExtra(EXTRA_QUALITY, quality.name)
        .putExtra(EXTRA_MIC, micWanted)
        .putExtra(EXTRA_SEGMENT_MINUTES, segmentMinutes)
        .putExtra(EXTRA_TARGET, target.name)

    companion object {
        private const val EXTRA_LENS = "io.github.ooscam.capture.LENS"
        private const val EXTRA_QUALITY = "io.github.ooscam.capture.QUALITY"
        private const val EXTRA_MIC = "io.github.ooscam.capture.MIC"
        private const val EXTRA_SEGMENT_MINUTES = "io.github.ooscam.capture.SEGMENT_MINUTES"
        private const val EXTRA_TARGET = "io.github.ooscam.capture.TARGET"

        fun readFrom(intent: Intent): CaptureConfig {
            val d = CaptureConfig()
            return CaptureConfig(
                lens = intent.getStringExtra(EXTRA_LENS)?.let(Lens::valueOf) ?: d.lens,
                quality = intent.getStringExtra(EXTRA_QUALITY)?.let(Quality::valueOf) ?: d.quality,
                micWanted = intent.getBooleanExtra(EXTRA_MIC, d.micWanted),
                segmentMinutes = intent.getIntExtra(EXTRA_SEGMENT_MINUTES, d.segmentMinutes),
                target = intent.getStringExtra(EXTRA_TARGET)?.let(StorageTarget::valueOf) ?: d.target,
            )
        }
    }
}

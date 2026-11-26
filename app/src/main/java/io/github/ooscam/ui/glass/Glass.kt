package io.github.ooscam.ui.glass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.ooscam.ui.theme.Dimens
import io.github.ooscam.ui.theme.Tokens

/** How glass controls are painted: blurred backdrop, or the opaque `glass-solid` fallback. */
enum class GlassMode { BLUR, SOLID }

object GlassPolicy {
    /** MASTER.md: solid when "Reduce glass effects" is on or the battery saver is active. */
    fun mode(reduceGlass: Boolean, powerSave: Boolean): GlassMode =
        if (reduceGlass || powerSave) GlassMode.SOLID else GlassMode.BLUR
}

val LocalGlassMode = staticCompositionLocalOf { GlassMode.BLUR }

/** The backdrop glass controls blur, provided by the screen whose content sits behind them. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Glass mode from the user's setting and the live battery-saver state. */
@Composable
fun rememberGlassMode(reduceGlass: Boolean): GlassMode {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                powerSave = power?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        powerSave = power?.isPowerSaveMode == true
        onDispose { context.unregisterReceiver(receiver) }
    }
    return GlassPolicy.mode(reduceGlass, powerSave)
}

/** Holds the recorded content behind glass controls. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var originInRoot: Offset = Offset.Zero
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Records this content (drawn normally as well) so glass controls above it can blur it. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.originInRoot = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Glass fill for controls and bars, never content (DESIGN.md): the content behind, blurred by `glass-blur`,
 * under the translucent `glass` tint, with a 1 dp `glass-border`. In [GlassMode.SOLID] it is `glass-solid`.
 * Without a recorded backdrop (for example over the live camera surface) only the tint is drawn.
 */
fun Modifier.glass(shape: Shape, backdrop: GlassBackdrop? = null): Modifier = composed {
    val source = backdrop ?: LocalGlassBackdrop.current
    val bordered = Modifier.border(Dimens.hairline, Tokens.colorGlassBorder, shape)
    when {
        LocalGlassMode.current == GlassMode.SOLID -> background(Tokens.colorGlassSolid, shape).then(bordered)
        source == null -> background(Tokens.colorGlass, shape).then(bordered)
        else -> {
            val blurLayer = rememberGraphicsLayer()
            val position = remember { PositionHolder() }
            onGloballyPositioned { position.value = it.positionInRoot() }
                .drawBehind {
                    val radius = Tokens.glassBlur.toPx()
                    blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    val offset = source.originInRoot - position.value
                    blurLayer.record { translate(offset.x, offset.y) { drawLayer(source.layer) } }
                    val outline = shape.createOutline(size, layoutDirection, this)
                    clipPath(Path().apply { addOutline(outline) }) { drawLayer(blurLayer) }
                    drawOutline(outline, Tokens.colorGlass)
                }
                .then(bordered)
        }
    }
}

private class PositionHolder {
    var value: Offset = Offset.Zero
}

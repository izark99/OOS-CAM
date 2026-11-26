package io.github.ooscam.ui.glass

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ooscam.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val power = context.getSystemService(PowerManager::class.java)

    @Test
    fun glassMode_isBlurOnlyWhenNeitherReduceGlassNorPowerSaverIsOn() {
        assertEquals(GlassMode.BLUR, GlassPolicy.mode(reduceGlass = false, powerSave = false))
        assertEquals(GlassMode.SOLID, GlassPolicy.mode(reduceGlass = true, powerSave = false))
        assertEquals(GlassMode.SOLID, GlassPolicy.mode(reduceGlass = false, powerSave = true))
        assertEquals(GlassMode.SOLID, GlassPolicy.mode(reduceGlass = true, powerSave = true))
    }

    @Test
    fun rememberGlassMode_followsPowerSaverBroadcasts() {
        var seen: GlassMode? = null
        compose.setContent { seen = rememberGlassMode(reduceGlass = false) }
        compose.waitForIdle()
        assertEquals(GlassMode.BLUR, seen)

        shadowOf(power).setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        compose.waitForIdle()
        assertEquals(GlassMode.SOLID, seen)
    }

    @Test
    fun rememberGlassMode_reduceGlassSetting_forcesSolid() {
        val reduce = mutableStateOf(false)
        var seen: GlassMode? = null
        compose.setContent { seen = rememberGlassMode(reduceGlass = reduce.value) }
        compose.waitForIdle()
        assertEquals(GlassMode.BLUR, seen)
        reduce.value = true
        compose.waitForIdle()
        assertEquals(GlassMode.SOLID, seen)
    }

    @Test
    fun solidGlass_paintsGlassSolidInsideTheBorder() {
        val center = renderGlassCenter(GlassMode.SOLID)
        assertEquals(Tokens.colorGlassSolid.toArgb(), center.toArgb())
    }

    @Test
    fun blurGlass_showsTheContentBehindThroughTheTint() {
        val center = renderGlassCenter(GlassMode.BLUR)
        assertNotEquals(Tokens.colorGlassSolid.toArgb(), center.toArgb())
        // The backdrop is record red; the translucent glass keeps red dominant over blue.
        assertTrue("center $center", center.red > center.blue + 0.15f)
    }

    private fun renderGlassCenter(mode: GlassMode): Color {
        compose.setContent {
            CompositionLocalProvider(LocalGlassMode provides mode) {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.size(200.dp)) {
                    Box(Modifier.fillMaxSize().glassSource(backdrop).background(Tokens.colorRecord))
                    Box(
                        Modifier.size(120.dp).align(Alignment.Center).testTag("glass")
                            .glass(RoundedCornerShape(Tokens.radiusLg), backdrop),
                    )
                }
            }
        }
        val pixels = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }
}

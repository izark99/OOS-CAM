package io.github.ooscam.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import java.io.File
import java.security.MessageDigest
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tokens.kt is generated from design/design-system/oos-cam/tokens.json. Expected values are derived from the
 * JSON at test time, never typed here, so a stale or hand-edited Tokens.kt fails.
 */
class TokensTest {
    private val jsonFile = File(System.getProperty("ooscam.tokensJson") ?: error("ooscam.tokensJson not set"))
    private val tokens: Map<String, String> =
        Json.parseToJsonElement(jsonFile.readText()).jsonObject.getValue("tokens").jsonObject
            .mapValues { it.value.jsonPrimitive.content }
            .filterKeys { !it.startsWith("demo-") }

    private fun camel(name: String) = Regex("-(.)").replace(name) { it.groupValues[1].uppercase() }

    @Test
    fun tokensKt_isGeneratedFromTheCurrentJson() {
        val sha = MessageDigest.getInstance("SHA-256").digest(jsonFile.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(sha, Tokens.SOURCE_SHA256)
    }

    @Test
    fun everyNonDemoToken_hasAKotlinValue_andNoExtraNames() {
        assertEquals(tokens.keys.map(::camel).toSet(), Tokens.all.keys)
    }

    @Test
    fun colors_matchHexAndRgbaIncludingAlpha() {
        val colors = tokens.filterKeys { it.startsWith("color-") }
        assertTrue(colors.size > 20)
        for ((name, css) in colors) {
            val expected = parseCssColor(css)
            val actual = (Tokens.all.getValue(camel(name)) as Color).toArgb()
            assertEquals(name, expected, actual)
        }
    }

    @Test
    fun pixelSizes_becomeDpOneToOne() {
        for ((name, css) in tokens.filter { it.value.matches(Regex("""\d+px""")) }) {
            assertEquals(name, css.removeSuffix("px").toFloat(), (Tokens.all.getValue(camel(name)) as Dp).value)
        }
    }

    @Test
    fun fonts_carryWeightSizeAndLineHeight_inInter() {
        val fonts = tokens.filterKeys { it.startsWith("font-") && it != "font-family" }
        assertEquals(7, fonts.size)
        for ((name, css) in fonts) {
            val m = Regex("""(\d+) (\d+)px/(\d+)px""").find(css)!!.groupValues
            val style = Tokens.all.getValue(camel(name)) as TextStyle
            assertEquals(name, m[1].toInt(), style.fontWeight!!.weight)
            assertEquals(name, m[2].toFloat(), style.fontSize.value)
            assertEquals(name, m[3].toFloat(), style.lineHeight.value)
            assertEquals(name, Tokens.fontFamily, style.fontFamily)
        }
        assertTrue(Tokens.all.getValue("fontFamily") is FontFamily)
    }

    @Test
    fun motion_isMillisAndEasingIsTheCubicBezier() {
        for ((name, css) in tokens.filter { it.value.endsWith("ms") }) {
            assertEquals(name, css.removeSuffix("ms").toInt(), Tokens.all.getValue(camel(name)))
        }
        val p = Regex("""cubic-bezier\(([^)]*)\)""").find(tokens.getValue("easing"))!!.groupValues[1]
            .split(",").map { it.trim().toFloat() }
        val expected = CubicBezierEasing(p[0], p[1], p[2], p[3])
        val actual = Tokens.all.getValue("easing") as CubicBezierEasing
        for (t in listOf(0.1f, 0.3f, 0.5f, 0.9f)) assertEquals(expected.transform(t), actual.transform(t), 1e-6f)
    }

    private fun parseCssColor(css: String): Int {
        Regex("""#([0-9A-Fa-f]{6})""").matchEntire(css)?.let { return (0xFF000000 or it.groupValues[1].toLong(16)).toInt() }
        val p = Regex("""rgba\(([^)]*)\)""").matchEntire(css)!!.groupValues[1].split(",").map { it.trim() }
        val a = (p[3].toDouble() * 255).roundToInt()
        return (a shl 24) or (p[0].toInt() shl 16) or (p[1].toInt() shl 8) or p[2].toInt()
    }
}

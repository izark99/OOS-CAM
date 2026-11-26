package io.github.ooscam.ui.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import io.github.ooscam.ui.theme.Tokens

/**
 * Stroke icons drawn for OOS Cam (24x24 viewport, stroke 2, round caps) in the Lucide manner, from the same
 * path data as design/generate_screens.py. Circles and rectangles are written as arcs. No Apple glyphs.
 */
object OosIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},${y}h${w - 2 * rx}a$rx,$rx 0 0 1 $rx,${rx}v${h - 2 * rx}a$rx,$rx 0 0 1 ${-rx},${rx}" +
            "h${-(w - 2 * rx)}a$rx,$rx 0 0 1 ${-rx},${-rx}v${-(h - 2 * rx)}a$rx,$rx 0 0 1 $rx,${-rx}z"

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, Tokens.icon, Tokens.icon, VIEWPORT, VIEWPORT).apply {
            for (d in paths) {
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Tokens.colorText),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val settings = icon("settings", circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val mic = icon("mic", rect(9f, 2f, 6f, 12f, 3f), "M5 10a7 7 0 0 0 14 0M12 17v5")
    val micOff = icon("mic-off", "M3 3l18 18M9 9v1a3 3 0 0 0 5 2M15 9V5a3 3 0 0 0-6 0M5 10a7 7 0 0 0 11 5.7M19 10a7 7 0 0 1-.5 2.6M12 17v5")
    val moon = icon("moon", "M20 14A8 8 0 1 1 10 4a6 6 0 0 0 10 10z")
    val back = icon("back", "M15 5l-7 7 7 7")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val check = icon("check", "M5 12l5 5 9-10")
    val chevDown = icon("chev-down", "M6 9l6 6 6-6")
    val chevUp = icon("chev-up", "M6 15l6-6 6 6")
    val play = icon("play", "M8 5l11 7-11 7z")
    val scissors = icon("scissors", circle(6f, 6f, 3f), circle(6f, 18f, 3f), "M8.5 7.5L20 18M8.5 16.5L20 6")
    val gauge = icon("gauge", "M4 16a8 8 0 1 1 16 0", "M12 16l4-5")
    val volumeX = icon("volume-x", "M4 9h4l5-4v14l-5-4H4zM16 9l5 6M21 9l-5 6")
    val volume = icon("volume", "M4 9h4l5-4v14l-5-4H4zM16 9a4 4 0 0 1 0 6M19 6a8 8 0 0 1 0 12")
    val share = icon("share", "M12 3v12M7 8l5-5 5 5M5 14v6h14v-6")
    val trash = icon("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val grip = icon("grip", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01")
    val sd = icon("sd", "M7 2h8l4 4v16H7zM10 6v3M13 6v3")
    val alert = icon("alert", "M12 3l10 18H2z", "M12 10v5M12 18h.01")
    val search = icon("search", circle(11f, 11f, 7f), "M20 20l-4-4")
    val film = icon("film", rect(3f, 4f, 18f, 16f, 2f), "M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4")
    val camera = icon("camera", "M3 8h4l2-3h6l2 3h4v12H3z", circle(12f, 13f, 4f))
    val bell = icon("bell", "M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4")
    val plus = icon("plus", "M12 5v14M5 12h14")
    val minus = icon("minus", "M5 12h14")
    val order = icon("order", "M7 4v16M3 8l4-4 4 4M17 20V4M13 16l4 4 4-4")

    private const val VIEWPORT = 24f
    private const val STROKE = 2f
}

package io.github.ooscam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import io.github.ooscam.ui.glass.GlassMode
import io.github.ooscam.ui.glass.LocalGlassMode

/** Type scale from tokens; the timer uses tabular figures (MASTER.md). */
object OosType {
    val display: TextStyle get() = Tokens.fontDisplay
    val title: TextStyle get() = Tokens.fontTitle
    val body: TextStyle get() = Tokens.fontBody
    val bodyStrong: TextStyle get() = Tokens.fontBodyStrong
    val label: TextStyle get() = Tokens.fontLabel
    val labelStrong: TextStyle get() = Tokens.fontLabel.copy(fontWeight = Tokens.fontBodyStrong.fontWeight)
    val caption: TextStyle get() = Tokens.fontCaption
    val timer: TextStyle get() = Tokens.fontTimer.copy(fontFeatureSettings = "tnum")
}

/** Reduced motion: animations become fades only (DESIGN.md Motion). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Dark-only OOS Cam theme. Material 3 is only a base; its scheme is filled from tokens so no default shows. */
@Composable
fun OosTheme(glassMode: GlassMode = GlassMode.BLUR, reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Tokens.colorAccent,
        onPrimary = Tokens.colorTextOnAccent,
        secondary = Tokens.colorAccent,
        onSecondary = Tokens.colorTextOnAccent,
        background = Tokens.colorBg,
        onBackground = Tokens.colorText,
        surface = Tokens.colorSurface,
        onSurface = Tokens.colorText,
        surfaceVariant = Tokens.colorSurfaceRaised,
        onSurfaceVariant = Tokens.colorTextSecondary,
        surfaceContainer = Tokens.colorSurface,
        surfaceContainerHigh = Tokens.colorSurfaceRaised,
        outline = Tokens.colorGlassBorder,
        outlineVariant = Tokens.colorDivider,
        error = Tokens.colorDanger,
        onError = Tokens.colorText,
        scrim = Tokens.colorScrim,
    )
    val typography = Typography(
        displaySmall = OosType.display,
        titleLarge = OosType.title,
        bodyLarge = OosType.body,
        bodyMedium = OosType.label,
        labelLarge = OosType.label,
        labelSmall = OosType.caption,
    )
    CompositionLocalProvider(LocalGlassMode provides glassMode, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

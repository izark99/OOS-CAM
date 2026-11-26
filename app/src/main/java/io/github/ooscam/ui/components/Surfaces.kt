package io.github.ooscam.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.ooscam.ui.glass.glass
import io.github.ooscam.ui.theme.Dimens
import io.github.ooscam.ui.theme.OosType
import io.github.ooscam.ui.theme.Tokens

/** Top inset (status bar area) that the mock-ups reserve: 24 dp, or the real inset when larger. */
@Composable
fun StatusInset() {
    Spacer(Modifier.fillMaxWidth().height(Dimens.statusInset))
}

/** 56 dp top bar, side padding 8. */
@Composable
fun OosTopBar(
    modifier: Modifier = Modifier,
    title: String = "",
    navigation: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(Tokens.topbar).padding(horizontal = Tokens.space2),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        Text(
            title,
            style = OosType.title,
            color = Tokens.colorText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/** Large page title: display 28/34, padding 8 / 16 / 16 (DESIGN.md). */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = OosType.display,
        color = Tokens.colorText,
        modifier = modifier
            .padding(start = Tokens.space4, end = Tokens.space4, top = Tokens.space2, bottom = Tokens.space4)
            .semantics { heading() },
    )
}

/** Surface panel: radius 16, padding 16, gap 12. */
@Composable
fun OosPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.radiusMd)).background(Tokens.colorSurface).padding(Tokens.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.space3),
        content = content,
    )
}

/**
 * Bottom sheet over a scrim: 8 from the sides and bottom, radius 24, padding 24 / 16 / 16, gap 16, a grab
 * handle. Drawn in the screen's own layer (no extra window). Back and a scrim tap dismiss it.
 */
@Composable
fun BoxScope.OosSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .matchParentSize()
            .background(Tokens.colorScrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    Column(
        modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Tokens.space2)
            .widthIn(max = Dimens.maxSheetWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.radiusLg))
            .background(Tokens.colorGlassSolid)
            .border(Dimens.hairline, Tokens.colorGlassBorder, RoundedCornerShape(Tokens.radiusLg))
            // Swallow taps so they do not reach the scrim.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .semantics { paneTitle = title; isTraversalGroup = true }
            .padding(start = Tokens.space4, end = Tokens.space4, top = Tokens.space6, bottom = Tokens.space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.space4),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .offset(y = -Tokens.space3)
                .size(Dimens.sheetGrabWidth, Dimens.sheetGrabHeight)
                .clip(RoundedCornerShape(Tokens.radiusPill))
                .background(Tokens.colorTrack),
        )
        Text(title, style = OosType.title, color = Tokens.colorText, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Floating glass bar: 64 high, 8 from the sides and bottom, pill radius (library select, editor toolbar). */
@Composable
fun BoxScope.FloatingBar(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Tokens.space2)
            .fillMaxWidth()
            .height(Tokens.tabbar)
            .glass(RoundedCornerShape(Tokens.radiusPill))
            .semantics { isTraversalGroup = true }
            .padding(start = Tokens.space2, end = Tokens.space1),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Full-screen page background (bg, not camera black). */
@Composable
fun PageSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Tokens.colorBg), content = content)
}

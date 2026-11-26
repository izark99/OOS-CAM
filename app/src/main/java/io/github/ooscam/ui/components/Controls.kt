package io.github.ooscam.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.github.ooscam.ui.glass.glass
import io.github.ooscam.ui.icons.OosIcons
import io.github.ooscam.ui.theme.Dimens
import io.github.ooscam.ui.theme.OosType
import io.github.ooscam.ui.theme.Tokens

private val Pill = RoundedCornerShape(Tokens.radiusPill)

/** A 24 dp stroke icon; decorative unless [contentDescription] is given. */
@Composable
fun OosIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Tokens.colorText,
    size: Dp = Tokens.icon,
    contentDescription: String? = null,
) {
    Image(icon, contentDescription, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

enum class ButtonSize { MD, LG }

enum class ButtonKind { ACCENT, GLASS, RAISED, PLAIN, PLAIN_DANGER, DANGER }

/** Buttons come in two heights only: 44 (md) and 56 (lg), pill radius (DESIGN.md). */
@Composable
fun OosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.MD,
    kind: ButtonKind = ButtonKind.ACCENT,
    icon: ImageVector? = null,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    val content = when (kind) {
        ButtonKind.ACCENT -> Tokens.colorTextOnAccent
        ButtonKind.PLAIN -> Tokens.colorAccent
        ButtonKind.PLAIN_DANGER -> Tokens.colorDangerText
        else -> Tokens.colorText
    }
    val fill: Modifier = when (kind) {
        ButtonKind.ACCENT -> Modifier.background(Tokens.colorAccent, Pill)
        ButtonKind.GLASS -> Modifier.glass(Pill)
        ButtonKind.RAISED -> Modifier.background(Tokens.colorSurfaceRaised, Pill)
        ButtonKind.DANGER -> Modifier.background(Tokens.colorDanger, Pill)
        ButtonKind.PLAIN, ButtonKind.PLAIN_DANGER -> Modifier
    }
    val height = if (size == ButtonSize.LG) Tokens.buttonLg else Tokens.buttonMd
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .then(modifier)
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(Pill)
            .then(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (size == ButtonSize.LG) Tokens.space6 else Tokens.space4),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) OosIcon(icon, tint = content, size = Dimens.buttonIcon)
        Text(text, style = if (size == ButtonSize.LG) OosType.bodyStrong else OosType.labelStrong, color = content, maxLines = 1)
    }
}

/** 44 x 44 icon button with a 48 dp touch target; [contentDescription] is required (TalkBack). */
@Composable
fun OosIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = false,
    on: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .then(modifier)
            .size(Tokens.buttonMd)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(CircleShape)
            .then(if (glass) Modifier.glass(CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        OosIcon(icon, tint = if (on) Tokens.colorAccent else Tokens.colorText)
    }
}

/** Pill segmented control (custom, no Material default). */
@Composable
fun OosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(Pill)
            .background(Tokens.colorSurfaceRaised)
            .padding(Tokens.space1)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space1),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(Dimens.segmentHeight)
                    .clip(Pill)
                    .background(if (on) Tokens.colorText else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(i) })
                    .padding(horizontal = Tokens.space3),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = OosType.labelStrong, color = if (on) Tokens.colorBg else Tokens.colorTextSecondary, maxLines = 1)
            }
        }
    }
}

/** Round 24 dp checkbox; accent fill with a check when on. */
@Composable
fun OosCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CheckboxMark(checked)
    }
}

/** The checkbox drawing alone, for tiles whose whole area is the toggle. */
@Composable
fun CheckboxMark(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Dimens.checkbox)
            .clip(CircleShape)
            .background(if (checked) Tokens.colorAccent else Color.Transparent)
            .border(Dimens.checkboxBorder, if (checked) Tokens.colorAccent else Tokens.colorText, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) OosIcon(OosIcons.check, tint = Tokens.colorTextOnAccent, size = Dimens.checkIcon)
    }
}

/** 52 x 32 switch; `success` track when on. */
@Composable
fun OosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val thumbX by animateDpAsState(
        if (checked) Dimens.switchWidth - Dimens.switchThumb - Dimens.switchInset else Dimens.switchInset,
        tween(Tokens.motionFast, easing = Tokens.easing),
        label = "switch",
    )
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(Dimens.switchWidth, Dimens.switchHeight)
                .clip(Pill)
                .background(if (checked) Tokens.colorSuccess else Tokens.colorTrack),
        ) {
            Box(
                Modifier
                    .offset(x = thumbX, y = Dimens.switchInset)
                    .size(Dimens.switchThumb)
                    .clip(CircleShape)
                    .background(Tokens.colorText),
            )
        }
    }
}

/** 44 dp search field. */
@Composable
fun OosSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Tokens.buttonMd)
            .clip(RoundedCornerShape(Tokens.radiusSm))
            .background(Tokens.colorSurfaceRaised)
            .padding(horizontal = Tokens.space3),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OosIcon(OosIcons.search, tint = Tokens.colorTextTertiary, size = Dimens.buttonIcon)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = OosType.body, color = Tokens.colorTextTertiary, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = OosType.body.copy(color = Tokens.colorText),
                cursorBrush = SolidColor(Tokens.colorAccent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
    }
}

/** 6 dp progress bar; [fraction] in 0..1. */
@Composable
fun OosProgress(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(Dimens.progressHeight)
            .clip(Pill)
            .background(Tokens.colorTrack)
            .semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(f, 0f..1f) },
    ) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).height(Dimens.progressHeight).background(Tokens.colorAccent))
    }
}

/** 24 dp badge. */
@Composable
fun OosBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(Dimens.badgeHeight)
            .clip(Pill)
            .background(Tokens.colorSurfaceRaised)
            .padding(horizontal = Tokens.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = OosType.caption, color = Tokens.colorText, maxLines = 1)
    }
}

/** 44 dp toast pill on glass-solid. */
@Composable
fun OosToast(text: String, modifier: Modifier = Modifier, icon: ImageVector? = OosIcons.check) {
    Row(
        modifier
            .height(Tokens.buttonMd)
            .clip(Pill)
            .background(Tokens.colorGlassSolid)
            .border(Dimens.hairline, Tokens.colorGlassBorder, Pill)
            .padding(horizontal = Tokens.space4)
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) OosIcon(icon, size = Dimens.dropdownIcon)
        Text(text, style = OosType.label, color = Tokens.colorText, maxLines = 1)
    }
}

/** Loading placeholder that keeps the size of what it stands for (no infinite animation). */
@Composable
fun OosSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Tokens.colorSurfaceRaised)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Transparent, Tokens.colorTrack, Color.Transparent),
                ),
            ),
    )
}

/** List row: min 56, padding 16, title ellipsized, optional caption and trailing control. */
@Composable
fun OosListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Tokens.listRow)
            .padding(horizontal = Tokens.space4),
        horizontalArrangement = Arrangement.spacedBy(Tokens.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = OosType.body, color = Tokens.colorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = OosType.caption, color = Tokens.colorTextSecondary)
        }
        trailing()
    }
}

/** Grouped card of rows with 1 dp dividers and an uppercase caption header. */
@Composable
fun OosGroup(header: String?, modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    Column(modifier) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = OosType.caption,
                color = Tokens.colorTextSecondary,
                modifier = Modifier.padding(start = Tokens.space8, end = Tokens.space8, bottom = Tokens.space2)
                    .semantics { heading() },
            )
        }
        Column(
            Modifier
                .padding(horizontal = Tokens.space4)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Tokens.radiusMd))
                .background(Tokens.colorSurface),
        ) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(Dimens.hairline).background(Tokens.colorDivider))
                row()
            }
        }
    }
}

/** Dropdown anchor (44 high, radius sm); the menu is [OosDropdownMenu]. */
@Composable
fun OosDropdown(
    value: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Row(
            Modifier
                .minimumInteractiveComponentSize()
                .height(Tokens.buttonMd)
                .clip(RoundedCornerShape(Tokens.radiusSm))
                .background(Tokens.colorSurfaceRaised)
                .then(
                    if (expanded) Modifier.border(Dimens.checkboxBorder, Tokens.colorFocus, RoundedCornerShape(Tokens.radiusSm))
                    else Modifier,
                )
                .clickable(role = Role.DropdownList, onClick = { onExpandedChange(!expanded) })
                .semantics { this.contentDescription = "$contentDescription, $value" }
                .padding(horizontal = Tokens.space3),
            horizontalArrangement = Arrangement.spacedBy(Tokens.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = OosType.label, color = Tokens.colorText, maxLines = 1)
            OosIcon(OosIcons.chevDown, size = Dimens.dropdownIcon)
        }
        if (expanded) {
            OosDropdownMenu(options, selected, onSelect = { onSelect(it); onExpandedChange(false) }, onDismiss = { onExpandedChange(false) })
        }
    }
}

/** Anchored menu: opens below the anchor, aligned to its end edge, rows 44 high. */
@Composable
fun OosDropdownMenu(options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { Tokens.space1.roundToPx() }
    androidx.compose.ui.window.Popup(
        popupPositionProvider = EndBelowPosition(gap),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .widthIn(min = Dimens.dropdownMenuMinWidth)
                .clip(RoundedCornerShape(Tokens.radiusMd))
                .background(Tokens.colorGlassSolid)
                .border(Dimens.hairline, Tokens.colorGlassBorder, RoundedCornerShape(Tokens.radiusMd))
                .padding(Tokens.space1)
                .selectableGroup(),
        ) {
            options.forEachIndexed { i, label ->
                Row(
                    Modifier
                        .widthIn(min = Dimens.dropdownMenuMinWidth - Tokens.space2)
                        .height(Tokens.buttonMd)
                        .clip(RoundedCornerShape(Tokens.radiusSm))
                        .background(if (i == selected) Tokens.colorTrack else Color.Transparent)
                        .selectable(selected = i == selected, role = Role.RadioButton, onClick = { onSelect(i) })
                        .padding(horizontal = Tokens.space3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = OosType.label, color = Tokens.colorText, maxLines = 1)
                    if (i == selected) OosIcon(OosIcons.check, size = Dimens.dropdownIcon)
                }
            }
        }
    }
}

private class EndBelowPosition(private val gap: Int) : androidx.compose.ui.window.PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize,
    ): androidx.compose.ui.unit.IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return androidx.compose.ui.unit.IntOffset(x, anchorBounds.bottom + gap)
    }
}

const val DISABLED_ALPHA = 0.4f

package io.github.ooscam.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Component sizes that docs/DESIGN.md and design/generate_screens.py fix but tokens.json has no token for.
 * They live here, in one place, so composables never type a size literal. Everything else comes from [Tokens].
 */
object Dimens {
    /** Minimum touch target in Compose (DESIGN.md: icon buttons 44, touch targets >= 48). */
    val touchTarget = 48.dp
    val hairline = 1.dp
    val shutterRing = 4.dp
    val shutterDot = 60.dp
    val shutterStop = 28.dp
    val shutterStopRadius = 8.dp
    val recordDot = 10.dp
    val chipHeight = 32.dp
    val checkbox = 24.dp
    val checkboxBorder = 2.dp
    val checkIcon = 16.dp
    val switchWidth = 52.dp
    val switchHeight = 32.dp
    val switchThumb = 28.dp
    val switchInset = 2.dp
    val segmentHeight = 36.dp
    val dropdownMenuMinWidth = 200.dp
    val dropdownIcon = 18.dp
    val progressHeight = 6.dp
    val badgeHeight = 24.dp
    val sheetGrabWidth = 36.dp
    val sheetGrabHeight = 4.dp
    val pickedOutline = 3.dp
    val tileLabelRadius = 6.dp
    val timelineHeight = 88.dp
    val timelineClip = 56.dp
    val timelineTop = 16.dp
    val trimHandle = 14.dp
    val trimHandleIcon = 12.dp
    val selectedClipPaddingStart = 22.dp
    val speedRangeEdge = 2.dp
    val playhead = 2.dp
    val toolWidth = 64.dp
    val toolGap = 2.dp
    val buttonIcon = 20.dp
    val emptyIcon = 40.dp
    val cameraBottom = 200.dp
    val cameraTopY = 32.dp
    val recordPillY = 88.dp
    val statusInset = 24.dp
    val pageTitleTop = 8.dp
    val libraryThumbBorder = 2.dp
    val maxSheetWidth = 560.dp
}

package io.github.ooscam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ooscam.ui.icons.OosIcons
import io.github.ooscam.ui.theme.Dimens
import io.github.ooscam.ui.theme.OosType
import io.github.ooscam.ui.theme.Tokens

/** A speed range drawn on a clip, as fractions of that clip's trimmed length. */
data class TimelineRange(val start: Float, val end: Float, val label: String)

/** One clip block: width follows [weight] (its trimmed duration). */
data class TimelineClip(
    val label: String,
    val weight: Float,
    val description: String,
    val ranges: List<TimelineRange> = emptyList(),
)

/** Clip colors cycle through the three clip tokens. */
fun clipColor(index: Int): Color = when (index % 3) {
    0 -> Tokens.colorClip1
    1 -> Tokens.colorClip2
    else -> Tokens.colorClip3
}

/**
 * Timeline: 88 high, clip blocks 56 with 4 gaps; the selected clip gets an accent outline, a trim handle
 * (14 wide) on each end and 22 dp start padding (DESIGN.md).
 */
@Composable
fun Timeline(
    clips: List<TimelineClip>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playhead: Float? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(Dimens.timelineHeight)) {
        Row(
            Modifier.fillMaxWidth().padding(top = Dimens.timelineTop).height(Dimens.timelineClip),
            horizontalArrangement = Arrangement.spacedBy(Tokens.space1),
        ) {
            clips.forEachIndexed { i, clip ->
                ClipBlock(
                    clip = clip,
                    color = clipColor(i),
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(clip.weight.coerceAtLeast(MIN_WEIGHT)),
                )
            }
        }
        if (playhead != null) {
            Box(
                Modifier
                    .offset(x = maxWidth * playhead.coerceIn(0f, 1f))
                    .width(Dimens.playhead)
                    .fillMaxHeight()
                    .background(Tokens.colorText),
            )
        }
    }
}

@Composable
private fun ClipBlock(clip: TimelineClip, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(Tokens.radiusSm)
    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(color)
            .then(if (selected) Modifier.border(Dimens.pickedOutline, Tokens.colorAccent, shape) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = clip.description
                this.selected = selected
            },
    ) {
        val inner = if (selected) maxWidth - Dimens.trimHandle * 2 else maxWidth
        val left = if (selected) Dimens.trimHandle else 0.dp
        for (r in clip.ranges) {
            Box(
                Modifier
                    .offset(x = left + inner * r.start)
                    .width(inner * (r.end - r.start).coerceAtLeast(0f))
                    .fillMaxHeight()
                    .background(Tokens.colorSpeedRange)
                    .border(Dimens.speedRangeEdge, Tokens.colorAccent),
            ) {
                Text(
                    r.label,
                    style = OosType.caption,
                    color = Tokens.colorText,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = Tokens.space1),
                )
            }
        }
        Text(
            clip.label,
            style = OosType.caption,
            color = Tokens.colorText,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = if (selected) Dimens.selectedClipPaddingStart else Tokens.space2,
                    end = Tokens.space2,
                    bottom = Tokens.space1,
                ),
        )
        if (selected) {
            TrimHandle(Modifier.align(Alignment.CenterStart), start = true)
            TrimHandle(Modifier.align(Alignment.CenterEnd), start = false)
        }
    }
}

/** 14 x 56 accent handle with a grip, rounded on the outer side. */
@Composable
fun TrimHandle(modifier: Modifier = Modifier, start: Boolean = true) {
    val r = Tokens.radiusSm
    val shape = if (start) RoundedCornerShape(r, 0.dp, 0.dp, r) else RoundedCornerShape(0.dp, r, r, 0.dp)
    Box(
        modifier.width(Dimens.trimHandle).height(Dimens.timelineClip).clip(shape).background(Tokens.colorAccent),
        contentAlignment = Alignment.Center,
    ) {
        OosIcon(OosIcons.grip, tint = Tokens.colorTextOnAccent, size = Dimens.trimHandleIcon)
    }
}

private const val MIN_WEIGHT = 0.01f

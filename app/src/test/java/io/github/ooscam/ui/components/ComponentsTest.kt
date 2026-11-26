package io.github.ooscam.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ooscam.ui.icons.OosIcons
import io.github.ooscam.ui.theme.Dimens
import io.github.ooscam.ui.theme.OosTheme
import io.github.ooscam.ui.theme.Tokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun buttons_comeInTwoHeightsOnly_44And56() {
        compose.setContent {
            OosTheme {
                androidx.compose.foundation.layout.Column {
                    OosButton("Md", {}, Modifier.testTag("md"), size = ButtonSize.MD)
                    OosButton("Lg", {}, Modifier.testTag("lg"), size = ButtonSize.LG, kind = ButtonKind.GLASS)
                }
            }
        }
        compose.onNodeWithTag("md").assertHeightIsEqualTo(Tokens.buttonMd)
        compose.onNodeWithTag("lg").assertHeightIsEqualTo(Tokens.buttonLg)
        // The 44 dp button still has a 48 dp touch target.
        val touch = compose.onNodeWithTag("md").fetchSemanticsNode().touchBoundsInRoot
        with(compose.density) { assertTrue(touch.height >= Dimens.touchTarget.toPx() - 0.5f) }
    }

    @Test
    fun iconButton_is44Drawn_with48TouchTarget_andAnnouncesItsLabel() {
        var clicks = 0
        compose.setContent { OosTheme { OosIconButton(OosIcons.settings, "Settings", { clicks++ }) } }
        val node = compose.onNodeWithContentDescription("Settings")
        node.assertWidthIsEqualTo(Tokens.buttonMd).assertHeightIsEqualTo(Tokens.buttonMd)
        val touch = node.fetchSemanticsNode().touchBoundsInRoot
        with(compose.density) { assertTrue(touch.width >= Dimens.touchTarget.toPx() - 0.5f) }
        node.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun segmented_selectsTheTappedOption() {
        var sel by mutableIntStateOf(0)
        compose.setContent { OosTheme { OosSegmented(listOf("Device", "SD card"), sel, { sel = it }) } }
        compose.onNodeWithText("SD card").performClick()
        assertEquals(1, sel)
        compose.onNodeWithText("SD card").assertIsSelected()
    }

    @Test
    fun checkbox_and_switch_toggle_andKeepTheirSizes() {
        var box by mutableStateOf(false)
        var sw by mutableStateOf(true)
        compose.setContent {
            OosTheme {
                androidx.compose.foundation.layout.Row {
                    OosCheckbox(box, { box = it }, "Select")
                    OosSwitch(sw, { sw = it }, "Mic")
                }
            }
        }
        compose.onNodeWithContentDescription("Select").assertIsOff().performClick()
        compose.onNodeWithContentDescription("Select").assertIsOn()
        compose.onNodeWithContentDescription("Mic").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Mic").assertIsOff()
        assertTrue(box && !sw)
        with(compose.density) {
            val mic = compose.onNodeWithContentDescription("Mic").fetchSemanticsNode().boundsInRoot
            assertTrue(mic.width >= Dimens.switchWidth.toPx() - 0.5f)
        }
    }

    @Test
    fun dropdown_opensAnchoredMenu_andSelectingCloses() {
        var open by mutableStateOf(false)
        var sel by mutableIntStateOf(2)
        val options = listOf("5 min", "10 min", "30 min", "60 min")
        compose.setContent {
            OosTheme { OosDropdown(options[sel], options, sel, { sel = it }, open, { open = it }, "Segment length") }
        }
        compose.onNodeWithContentDescription("Segment length, 30 min").performClick()
        assertTrue(open)
        compose.onNodeWithText("10 min").performClick()
        assertEquals(1, sel)
        assertTrue(!open)
    }

    @Test
    fun progress_reportsItsFraction() {
        compose.setContent { OosTheme { OosProgress(0.46f, Modifier.testTag("p")) } }
        compose.onNodeWithTag("p").assertHeightIsEqualTo(Dimens.progressHeight)
        val info = compose.onNodeWithTag("p").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.46f, info.current, 1e-4f)
    }

    @Test
    fun skeleton_keepsTheSizeItStandsFor() {
        compose.setContent { OosTheme { OosSkeleton(Modifier.size(Tokens.listRow).testTag("s")) } }
        compose.onNodeWithTag("s").assertWidthIsEqualTo(Tokens.listRow).assertHeightIsEqualTo(Tokens.listRow)
    }

    @Test
    fun badge_toast_listRow_haveTheirTokenHeights() {
        compose.setContent {
            OosTheme {
                androidx.compose.foundation.layout.Column {
                    OosBadge("2 clips", Modifier.testTag("badge"))
                    OosToast("Saved", Modifier.testTag("toast"))
                    OosListRow("Version", Modifier.testTag("row"))
                }
            }
        }
        compose.onNodeWithTag("badge").assertHeightIsEqualTo(Dimens.badgeHeight)
        compose.onNodeWithTag("toast").assertHeightIsEqualTo(Tokens.buttonMd)
        compose.onNodeWithTag("row").assertHeightIsEqualTo(Tokens.listRow)
    }

    @Test
    fun timeline_widthsFollowTrimmedLength_andOnlyTheSelectedClipHasTrimHandles() {
        var selected by mutableIntStateOf(0)
        val clips = listOf(
            TimelineClip("A · 1:30", 90f, "Clip A", listOf(TimelineRange(0.3f, 0.6f, "2x"))),
            TimelineClip("B · 1:00", 60f, "Clip B"),
        )
        compose.setContent {
            OosTheme { Box(Modifier.width(380.dp)) { Timeline(clips, selected, { selected = it }, Modifier.testTag("tl")) } }
        }
        compose.onNodeWithTag("tl").assertHeightIsEqualTo(Dimens.timelineHeight)
        val a = compose.onNodeWithContentDescription("Clip A").assertIsSelected().getBoundsInRoot()
        val b = compose.onNodeWithContentDescription("Clip B").getBoundsInRoot()
        assertEquals(1.5f, a.width.value / b.width.value, 0.05f)
        assertEquals(Dimens.timelineClip, a.height)
        compose.onNodeWithContentDescription("Clip B").performClick()
        assertEquals(1, selected)
        compose.onNodeWithContentDescription("Clip B").assertIsSelected()
    }

    @Test
    fun sheet_scrimTapDismisses() {
        var dismissed = 0
        compose.setContent {
            OosTheme { Box(Modifier.fillMaxSize()) { OosSheet("Speed", onDismiss = { dismissed++ }) { OosBadge("x") } } }
        }
        compose.onNodeWithText("Speed").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assertExists()
        assertEquals(0, dismissed)
    }
}

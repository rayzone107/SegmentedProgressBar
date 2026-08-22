package com.rachitgoyal.segmentedprogressbar.demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives the Playground's controls the way a user would. The library features
 * themselves are covered in the library modules; these tests cover the demo's
 * wiring from control to bar.
 *
 * The partial-fill tests drive segments through their accessibility nodes,
 * which exercises the per-segment accessibility path and the tap handler in
 * one go: an activated node goes through exactly the code a tap does.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class PlaygroundScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Opens the playground in quarter-fill mode with per-segment nodes on. */
    private fun openInQuartersMode() {
        composeRule.setContent { PlaygroundScreen() }
        composeRule.onNodeWithText("Each segment is its own accessibility node")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("adds 25%").performScrollTo().performClick()
    }

    private fun tapSegment(index: Int, times: Int = 1) {
        repeat(times) {
            composeRule.onNodeWithContentDescription("Segment ${index + 1} of 10")
                .performClick()
        }
    }

    @Test
    fun `quarter taps build partial fills on any number of segments`() {
        openInQuartersMode()

        tapSegment(3, times = 2)
        tapSegment(0, times = 1)

        // The readout shows exactly the map the bar receives.
        composeRule.onNodeWithText("segmentProgress = {0=0.25, 3=0.5}").assertExists()
    }

    @Test
    fun `the fourth quarter completes the segment`() {
        openInQuartersMode()

        tapSegment(3, times = 4)

        composeRule.onNodeWithText("enabledDivisions = [1, 2, 3, 5, 6, 9]").assertExists()
        // Full means enabled, not a stale 1.0 entry left in the map.
        composeRule.onNodeWithText("segmentProgress = {}").assertDoesNotExist()
    }

    @Test
    fun `a full segment wraps back to empty`() {
        openInQuartersMode()

        // Segment 2 starts lit, so one tap in quarter mode empties it.
        tapSegment(1)

        composeRule.onNodeWithText("enabledDivisions = [2, 5, 6, 9]").assertExists()
    }

    @Test
    fun `toggling a partially filled segment discards its fill`() {
        openInQuartersMode()
        tapSegment(3, times = 2)

        composeRule.onNodeWithText("toggles it").performScrollTo().performClick()
        tapSegment(3)

        composeRule.onNodeWithText("enabledDivisions = [1, 2, 3, 5, 6, 9]").assertExists()
        composeRule.onNodeWithText("segmentProgress = {3=0.5}").assertDoesNotExist()
    }

    @Test
    fun `the accessibility toggle exposes one node per segment`() {
        // Also the regression test for ToggleRow being row-level toggleable:
        // the click lands on the label, not the switch.
        composeRule.setContent { PlaygroundScreen() }

        composeRule.onNodeWithText("Each segment is its own accessibility node")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithContentDescription("Segment 1 of 10").assertExists()
    }
}

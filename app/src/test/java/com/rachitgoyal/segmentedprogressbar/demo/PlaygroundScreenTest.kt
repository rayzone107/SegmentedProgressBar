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
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class PlaygroundScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun openPartialControls() {
        composeRule.setContent { PlaygroundScreen() }
        composeRule.onNodeWithText("One segment is partially filled")
            .performScrollTo()
            .performClick()
    }

    @Test
    fun `the partial fill controls stay hidden until their toggle is on`() {
        composeRule.setContent { PlaygroundScreen() }

        composeRule.onNodeWithText("Which segment").assertDoesNotExist()
        composeRule.onNodeWithText("Fill").assertDoesNotExist()
    }

    @Test
    fun `enabling the partial fill drives the bar and the readout`() {
        openPartialControls()

        composeRule.onNodeWithText("Which segment").assertExists()
        composeRule.onNodeWithText("Fill").assertExists()
        // The readout shows exactly the map the bar receives.
        composeRule.onNodeWithText("segmentProgress = {3=0.4}").assertExists()
    }

    @Test
    fun `a lit segment reports that full supersedes its partial`() {
        openPartialControls()

        // "All" lights every segment, including the partially filled one.
        composeRule.onNodeWithText("All").performClick()

        composeRule.onNodeWithText(
            "Segment 3 is on, so the partial is ignored: a full segment supersedes its entry.",
        ).assertExists()
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

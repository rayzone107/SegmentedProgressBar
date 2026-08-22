package com.rachitgoyal.segmented.compose

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for [perSegmentAccessibility]: each segment as its own semantics node,
 * checked state and click action included.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class SegmentedProgressBarComposeA11yTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `off by default, exposing only the summary node`() {
        composeRule.setContent {
            SegmentedProgressBar(divisions = 4, enabledSegments = setOf(1))
        }

        composeRule.onNodeWithContentDescription("1 of 4 segments complete").assertExists()
        composeRule.onNodeWithContentDescription("Segment 1 of 4").assertDoesNotExist()
    }

    @Test
    fun `each segment becomes a node carrying its checked state`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = setOf(1, 3),
                perSegmentAccessibility = true,
            )
        }

        composeRule.onNodeWithContentDescription("Segment 1 of 4").assertIsOff()
        composeRule.onNodeWithContentDescription("Segment 2 of 4").assertIsOn()
        composeRule.onNodeWithContentDescription("Segment 4 of 4").assertIsOn()
        // The summary node is still there for context.
        composeRule.onNodeWithContentDescription("2 of 4 segments complete").assertExists()
    }

    @Test
    fun `activating a node routes through the click handler`() {
        var on by mutableStateOf(setOf(1))
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = on,
                perSegmentAccessibility = true,
                onSegmentClick = { index -> on = if (index in on) on - index else on + index },
            )
        }

        composeRule.onNodeWithContentDescription("Segment 3 of 4").performClick()
        composeRule.waitForIdle()

        assertThat(on).containsExactly(1, 2)
        composeRule.onNodeWithContentDescription("Segment 3 of 4").assertIsOn()
    }

    @Test
    fun `under RTL the first segment's node sits at the right-hand end`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SegmentedProgressBar(
                    divisions = 4,
                    enabledSegments = setOf(0),
                    modifier = Modifier.width(100.dp).height(10.dp),
                    perSegmentAccessibility = true,
                )
            }
        }

        val first = composeRule.onNodeWithContentDescription("Segment 1 of 4")
            .fetchSemanticsNode().boundsInRoot
        val last = composeRule.onNodeWithContentDescription("Segment 4 of 4")
            .fetchSemanticsNode().boundsInRoot

        assertThat(first.left).isGreaterThan(last.left)
    }

    @Test
    fun `nodes without a click handler are read-only`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = setOf(1),
                perSegmentAccessibility = true,
            )
        }

        val node = composeRule.onNodeWithContentDescription("Segment 1 of 4")
            .fetchSemanticsNode()

        assertThat(
            node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick),
        ).isFalse()
    }
}

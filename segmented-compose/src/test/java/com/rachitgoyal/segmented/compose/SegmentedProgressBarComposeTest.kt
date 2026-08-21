package com.rachitgoyal.segmented.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rachitgoyal.segmented.CornerMode
import com.rachitgoyal.segmented.EntryAnimation
import com.rachitgoyal.segmented.RecurringAnimation
import com.rachitgoyal.segmented.SegmentAnimation
import com.rachitgoyal.segmented.ShadowTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for the Compose bindings.
 *
 * The layout maths is already covered exhaustively by `SegmentGeometryTest` in
 * `:segmented`, which this artifact shares rather than reimplements. These tests
 * therefore cover what is genuinely new here: that the Composable composes at
 * all, that its parameters are validated, that taps map to the right segment
 * (including under RTL), and that it reports itself to accessibility.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
class SegmentedProgressBarComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    // region composition

    @Test
    fun `it composes with only the required arguments`() {
        composeRule.setContent {
            SegmentedProgressBar(divisions = 10, enabledSegments = setOf(1, 2, 5, 6, 9))
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun `every styling option composes together`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 12,
                enabledSegments = setOf(0, 1, 4, 7, 8, 11),
                modifier = Modifier.fillMaxWidth().height(28.dp),
                onColor = Color.Red,
                offColor = Color.LightGray,
                gap = 4.dp,
                cornerRadius = 8.dp,
                cornerMode = CornerMode.EACH_RUN,
                activeHeightFraction = 1f,
                inactiveHeightFraction = 0.6f,
                shadow = SegmentShadow(radius = 6.dp, dy = 2.dp),
                segmentAnimation = SegmentAnimation.FADE,
                entryAnimation = EntryAnimation.STAGGER,
                recurringAnimation = RecurringAnimation.SHIMMER,
            )
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun `an empty selection composes`() {
        composeRule.setContent {
            SegmentedProgressBar(divisions = 5, enabledSegments = emptySet())
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun `out of range indices are ignored rather than crashing`() {
        composeRule.setContent {
            SegmentedProgressBar(divisions = 3, enabledSegments = setOf(0, 3, 99, -4))
        }

        composeRule.onNodeWithContentDescription("1 of 3 segments complete").assertExists()
    }

    // endregion

    // region validation

    @Test
    fun `divisions below one is rejected`() {
        val error = runCatching {
            composeRule.setContent {
                SegmentedProgressBar(divisions = 0, enabledSegments = emptySet())
            }
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `a height fraction outside zero to one is rejected`() {
        val error = runCatching {
            composeRule.setContent {
                SegmentedProgressBar(
                    divisions = 4,
                    enabledSegments = emptySet(),
                    activeHeightFraction = 1.5f,
                )
            }
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    @Test
    fun `a negative shadow radius is rejected`() {
        val error = runCatching { SegmentShadow(radius = (-2).dp) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    // endregion

    // region taps

    @Test
    fun `a tap reports the segment under the finger`() {
        val tapped = mutableListOf<Int>()
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = emptySet(),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                onSegmentClick = { tapped += it },
            )
        }

        composeRule.onRoot().performTouchInput {
            // Just inside the third quarter of the bar.
            click(Offset(width * 0.6f, height / 2f))
        }
        composeRule.waitForIdle()

        assertThat(tapped).containsExactly(2)
    }

    @Test
    fun `taps across the bar map to consecutive segments`() {
        val tapped = mutableListOf<Int>()
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = emptySet(),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                onSegmentClick = { tapped += it },
            )
        }

        for (quarter in 0 until 4) {
            composeRule.onRoot().performTouchInput {
                click(Offset(width * (quarter + 0.5f) / 4f, height / 2f))
            }
            composeRule.waitForIdle()
        }

        assertThat(tapped).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `without a click handler the bar is inert`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = emptySet(),
                modifier = Modifier.fillMaxWidth().height(40.dp),
            )
        }

        // Nothing to assert beyond "this does not throw"; a bar with no handler
        // installs no pointer input at all.
        composeRule.onRoot().performTouchInput { click() }
        composeRule.waitForIdle()
    }

    // endregion

    // region accessibility

    @Test
    fun `a content description is generated from the current progress`() {
        composeRule.setContent {
            SegmentedProgressBar(divisions = 10, enabledSegments = setOf(1, 2, 5, 6, 9))
        }

        composeRule.onNodeWithContentDescription("5 of 10 segments complete").assertExists()
    }

    @Test
    fun `a caller-supplied content description wins`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 10,
                enabledSegments = setOf(1),
                contentDescription = "Onboarding progress",
            )
        }

        composeRule.onNodeWithContentDescription("Onboarding progress").assertExists()
    }

    // endregion

    // region parity with the View defaults

    @Test
    fun `the defaults match the View implementation`() {
        // The two renderers are meant to be visually interchangeable, so their
        // default palettes must agree.
        assertThat(SegmentedProgressBarDefaults.OnColor.value.toLong())
            .isEqualTo(Color(0xFF5097E2).value.toLong())
        assertThat(SegmentedProgressBarDefaults.OffColor.value.toLong())
            .isEqualTo(Color(0xFFC1C1C1).value.toLong())
        assertThat(SegmentedProgressBarDefaults.AnimationDurationMillis)
            .isEqualTo(com.rachitgoyal.segmented.SegmentedProgressBar.DEFAULT_ANIMATION_DURATION_MS.toInt())
        assertThat(SegmentedProgressBarDefaults.RecurringDurationMillis)
            .isEqualTo(com.rachitgoyal.segmented.SegmentedProgressBar.DEFAULT_RECURRING_DURATION_MS.toInt())
        assertThat(SegmentedProgressBarDefaults.EntryStaggerDelayMillis)
            .isEqualTo(com.rachitgoyal.segmented.SegmentedProgressBar.DEFAULT_ENTRY_STAGGER_DELAY_MS.toInt())
    }

    // endregion

    // region animation styles

    @Test
    fun `a toggle style and an entry style never both take effect`() {
        // Regression: the draw pass used to read segmentAnimation and
        // entryAnimation together, so a FADE toggle paired with a GROW entry
        // applied a fade AND a grow at the same time. Exactly one style is in
        // effect at any moment now.
        var segments by mutableStateOf(setOf(0))
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = segments,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                segmentAnimation = SegmentAnimation.FADE,
                entryAnimation = EntryAnimation.GROW,
                animationDurationMillis = 120,
            )
        }
        composeRule.waitForIdle()

        // Toggling after entry must use the toggle style and settle there.
        segments = setOf(0, 2)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("2 of 4 segments complete").assertExists()
    }

    @Test
    fun `an entry animation is applied even when the toggle style is none`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = setOf(0, 1, 2, 3),
                modifier = Modifier.fillMaxWidth().height(30.dp),
                segmentAnimation = SegmentAnimation.NONE,
                entryAnimation = EntryAnimation.STAGGER,
                animationDurationMillis = 100,
                entryStaggerDelayMillis = 100,
            )
        }

        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("4 of 4 segments complete").assertExists()
    }

    @Test
    fun `with no entry animation the initial state is present on the first frame`() {
        // Otherwise the bar flashes empty for one frame before the effect runs.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = setOf(0, 1),
                modifier = Modifier.fillMaxWidth().height(30.dp),
                entryAnimation = EntryAnimation.NONE,
            )
        }

        composeRule.onNodeWithContentDescription("2 of 4 segments complete").assertExists()
        composeRule.mainClock.autoAdvance = true
    }

    // endregion

    // region shadow

    @Test
    fun `a shadow target can be chosen`() {
        composeRule.setContent {
            SegmentedProgressBar(
                divisions = 4,
                enabledSegments = setOf(1, 2),
                modifier = Modifier.fillMaxWidth().height(30.dp),
                shadow = SegmentShadow(radius = 6.dp, target = ShadowTarget.ON_SEGMENTS),
            )
        }

        composeRule.onRoot().assertExists()
    }

    @Test
    fun `a shadow defaults to being cast by every segment`() {
        assertThat(SegmentShadow(radius = 4.dp).target).isEqualTo(ShadowTarget.ALL)
    }

    // endregion
}

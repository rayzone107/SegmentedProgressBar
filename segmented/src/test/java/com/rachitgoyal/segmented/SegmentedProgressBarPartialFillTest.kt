package com.rachitgoyal.segmented

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.os.Parcelable
import android.util.SparseArray
import android.view.AbsSavedState
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Tests for partial fills: [SegmentedProgressBar.setDivisionProgress] and
 * [SegmentedProgressBar.getDivisionProgress].
 *
 * The fixture matches the other drawing tests: a 300 x 40 bar with 4 divisions
 * and no divider, so each cell is exactly 75px wide and the arithmetic stays
 * checkable by eye. Division 1 spans 75..150.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarPartialFillTest {

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 40
        const val DIVISIONS = 4
        const val CELL = 75f

        const val PROGRESS = Color.RED
        const val TRACK = Color.GREEN
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newBar(configure: SegmentedProgressBar.() -> Unit = {}) =
        SegmentedProgressBar(context).apply {
            divisions = DIVISIONS
            dividerWidth = 0f
            cornerRadius = 0f
            progressBarColor = PROGRESS
            progressBarBackgroundColor = TRACK
            configure()
        }

    private fun render(bar: SegmentedProgressBar) =
        renderToRecordingCanvas(bar, WIDTH, HEIGHT)

    // region model

    @Test
    fun `progress round-trips and reports through the general getter`() {
        val bar = newBar()

        bar.setDivisionProgress(1, 0.4f)

        assertThat(bar.getDivisionProgress(1)).isEqualTo(0.4f)
        assertThat(bar.getDivisionProgress(0)).isEqualTo(0f)
    }

    @Test
    fun `a partial division is not enabled and not counted as complete`() {
        val bar = newBar()

        bar.setDivisionProgress(1, 0.99f)

        assertThat(bar.isDivisionEnabled(1)).isFalse()
        assertThat(bar.enabledDivisions).isEmpty()
        assertThat(bar.completedSegmentCount).isEqualTo(0)
    }

    @Test
    fun `progress one is exactly enableDivision`() {
        val bar = newBar()

        bar.setDivisionProgress(1, 1f)

        assertThat(bar.isDivisionEnabled(1)).isTrue()
        assertThat(bar.getDivisionProgress(1)).isEqualTo(1f)
        assertThat(bar.enabledDivisions).containsExactly(1)
    }

    @Test
    fun `progress zero is exactly disableDivision`() {
        val bar = newBar { enabledDivisions = listOf(1) }

        bar.setDivisionProgress(1, 0f)

        assertThat(bar.isDivisionEnabled(1)).isFalse()
        assertThat(bar.getDivisionProgress(1)).isEqualTo(0f)
    }

    @Test
    fun `an enabled division reports one whatever was set before`() {
        val bar = newBar()

        bar.setDivisionProgress(2, 0.3f)
        bar.enableDivision(2)

        assertThat(bar.getDivisionProgress(2)).isEqualTo(1f)
        // And the stale partial is gone: disabling drops straight to zero.
        bar.disableDivision(2)
        assertThat(bar.getDivisionProgress(2)).isEqualTo(0f)
    }

    @Test
    fun `values are clamped and indices tolerated, but NaN throws`() {
        val bar = newBar()

        bar.setDivisionProgress(1, 1.5f)
        assertThat(bar.isDivisionEnabled(1)).isTrue()

        bar.setDivisionProgress(2, -0.5f)
        assertThat(bar.getDivisionProgress(2)).isEqualTo(0f)

        bar.setDivisionProgress(-3, 0.5f) // ignored, like enableDivision(-3)
        assertThat(bar.getDivisionProgress(-3)).isEqualTo(0f)

        assertThat(
            runCatching { bar.setDivisionProgress(1, Float.NaN) }.exceptionOrNull(),
        ).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an out of range partial is retained and revealed when divisions grow`() {
        val bar = newBar()

        bar.setDivisionProgress(7, 0.5f)
        assertThat(render(bar).ops.ofColor(PROGRESS)).isEmpty()

        bar.divisions = 10
        assertThat(bar.getDivisionProgress(7)).isEqualTo(0.5f)
        assertThat(render(bar).ops.ofColor(PROGRESS)).isNotEmpty()
    }

    @Test
    fun `reset clears partial fills too`() {
        val bar = newBar()
        bar.setDivisionProgress(1, 0.4f)

        bar.reset()

        assertThat(bar.getDivisionProgress(1)).isEqualTo(0f)
        assertThat(render(bar).ops.ofColor(PROGRESS)).isEmpty()
    }

    // endregion

    // region drawing

    @Test
    fun `the fill covers the leading fraction of the cell`() {
        val bar = newBar()
        bar.setDivisionProgress(1, 0.4f)

        val fill = render(bar).ops.ofColor(PROGRESS).single()

        assertThat(fill.left).isWithin(0.01f).of(CELL)
        assertThat(fill.right).isWithin(0.01f).of(CELL + 0.4f * CELL)
    }

    @Test
    fun `under RTL the fill hugs the right edge of its cell`() {
        val bar = newBar()
        forceRtl(bar)
        bar.setDivisionProgress(1, 0.4f)

        val fill = render(bar).ops.ofColor(PROGRESS).single()

        // Logical cell 1 mirrors to 150..225; its leading 40% is the right side.
        assertThat(fill.right).isWithin(0.01f).of(225f)
        assertThat(fill.left).isWithin(0.01f).of(225f - 0.4f * CELL)
    }

    @Test
    fun `the track is still drawn in full underneath a partial cell`() {
        val bar = newBar()
        bar.setDivisionProgress(1, 0.4f)

        val trackCells = render(bar).ops.ofColor(TRACK)

        assertThat(trackCells).hasSize(DIVISIONS)
        val underPartial = trackCells.single { it.left == CELL }
        assertThat(underPartial.right).isWithin(0.01f).of(2 * CELL)
    }

    @Test
    fun `downgrading a full division to partial draws only the fill`() {
        // Regression guard: the lit-to-off transition must not fade out a
        // full-width segment underneath the newly partial fill.
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 300
            enabledDivisions = listOf(1)
        }
        layOut(bar)

        bar.setDivisionProgress(1, 0.5f)

        val fills = render(bar).ops.ofRgb(PROGRESS)
        assertThat(fills).hasSize(1)
        assertThat(fills.single().width).isWithin(0.01f).of(0.5f * CELL)
        assertThat(fills.single().alphaFraction).isEqualTo(1f)
    }

    @Test
    fun `enabling a partial division grows on from its fill`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.GROW
            animationDurationMs = 200
        }
        layOut(bar)
        bar.setDivisionProgress(1, 0.4f)

        bar.enableDivision(1)

        // At the very start of the transition the segment must already be at
        // least as wide as the fill it replaces; anything less means the grow
        // restarted from zero and visibly jumped backwards.
        val atStart = render(bar).ops.ofRgb(PROGRESS).single()
        assertThat(atStart.width).isAtLeast(0.4f * CELL - 0.01f)
        assertThat(atStart.width).isLessThan(CELL)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        val settled = render(bar).ops.ofRgb(PROGRESS).single()
        assertThat(settled.width).isWithin(0.01f).of(CELL)
        assertThat(bar.getDivisionProgress(1)).isEqualTo(1f)
    }

    // endregion

    // region instance state

    @Test
    fun `partial fills survive a save and restore cycle`() {
        val bar = newBar { id = 42 }
        bar.enabledDivisions = listOf(0)
        bar.setDivisionProgress(1, 0.35f)

        val container = SparseArray<Parcelable>()
        bar.saveHierarchyState(container)

        val restored = SegmentedProgressBar(context).apply { id = 42 }
        restored.restoreHierarchyState(container)

        assertThat(restored.getDivisionProgress(1)).isEqualTo(0.35f)
        assertThat(restored.enabledDivisions).containsExactly(0)
    }

    @Test
    fun `state written by some other view under the same id is still ignored`() {
        val bar = newBar { id = 42 }
        bar.setDivisionProgress(1, 0.35f)

        val container = SparseArray<Parcelable>()
        container.put(42, AbsSavedState.EMPTY_STATE)
        bar.restoreHierarchyState(container)

        assertThat(bar.getDivisionProgress(1)).isEqualTo(0.35f)
    }

    // endregion

    private fun layOut(bar: SegmentedProgressBar) {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, WIDTH, HEIGHT)
    }

    private fun forceRtl(bar: SegmentedProgressBar) {
        bar.context.applicationInfo.flags =
            bar.context.applicationInfo.flags or
            android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        check(bar.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            "RTL did not take; the assertions below would test LTR twice"
        }
    }
}

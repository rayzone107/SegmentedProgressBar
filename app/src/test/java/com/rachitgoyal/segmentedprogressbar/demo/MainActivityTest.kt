package com.rachitgoyal.segmentedprogressbar.demo

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rachitgoyal.segmented.SegmentedProgressBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * Drives the Gallery tab the way a user would, which doubles as an integration
 * test of the library's public API under real UI churn.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private fun launch(): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java).setup()

    private fun ActivityController<MainActivity>.bar(id: Int): SegmentedProgressBar =
        get().findViewById(id)

    private fun ActivityController<MainActivity>.label(id: Int): TextView = get().findViewById(id)

    private fun ActivityController<MainActivity>.click(id: Int) {
        get().findViewById<Button>(id).performClick()
    }

    /**
     * Lays the bar out and taps the horizontal centre of segment [index].
     *
     * A real touch is used rather than calling `toggleDivision` directly, so the
     * demo's own coordinate handling is under test too.
     */
    private fun ActivityController<MainActivity>.tapSegment(id: Int, index: Int) {
        val bar = bar(id)
        val width = 600
        val height = 40
        bar.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, width, height)

        val segmentWidth = width.toFloat() / bar.divisions
        val x = segmentWidth * index + segmentWidth / 2f
        val now = SystemClock.uptimeMillis()
        bar.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, height / 2f, 0),
        )
        bar.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, height / 2f, 0),
        )
        // View.onTouchEvent posts the click rather than invoking it inline, and
        // Robolectric's default paused looper will not run it until idled.
        shadowOf(Looper.getMainLooper()).idle()
    }

    // region selections

    @Test
    fun `the screen opens on the configured selection`() {
        val controller = launch()

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    @Test
    fun `the declarative bar turns on exactly the requested set`() {
        val controller = launch()

        val sparse = controller.bar(R.id.sparse_bar)

        assertThat(sparse.enabledDivisions).containsExactly(1, 2, 5, 6, 9).inOrder()
        assertThat(sparse.completedSegmentCount).isEqualTo(5)
        // Assert the off segments explicitly, not just the on ones.
        listOf(0, 3, 4, 7, 8).forEach { gap ->
            assertThat(sparse.isDivisionEnabled(gap)).isFalse()
        }
    }

    @Test
    fun `the habit tracker leaves the missed days unlit`() {
        val controller = launch()

        val habit = controller.bar(R.id.habit_bar)

        assertThat(habit.divisions).isEqualTo(7)
        assertThat(habit.enabledDivisions).containsExactly(0, 2, 3, 6).inOrder()
        assertThat(habit.isDivisionEnabled(1)).isFalse()
        assertThat(habit.isDivisionEnabled(4)).isFalse()
        assertThat(habit.isDivisionEnabled(5)).isFalse()
    }

    @Test
    fun `the dividerless bar still holds a set with off segments between`() {
        val controller = launch()

        val pill = controller.bar(R.id.pill_bar)

        assertThat(pill.isDividerEnabled).isFalse()
        assertThat(pill.enabledDivisions).containsExactly(0, 1, 2, 5, 8, 9, 10).inOrder()
        assertThat(pill.isDivisionEnabled(3)).isFalse()
    }

    @Test
    fun `the rtl bar holds the same set as its ltr twin`() {
        val controller = launch()

        assertThat(controller.bar(R.id.rtl_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    // endregion

    // region tapping individual segments

    @Test
    fun `tapping an unlit segment lights only that segment`() {
        val controller = launch()

        controller.tapSegment(R.id.toggle_bar, 4)

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 4, 5, 6, 9).inOrder()
    }

    @Test
    fun `tapping a lit segment clears only that segment`() {
        val controller = launch()

        controller.tapSegment(R.id.toggle_bar, 5)

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 6, 9).inOrder()
    }

    @Test
    fun `tapping the same segment twice returns it to its original state`() {
        val controller = launch()

        controller.tapSegment(R.id.toggle_bar, 7)
        controller.tapSegment(R.id.toggle_bar, 7)

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    @Test
    fun `segments can be built up in any order`() {
        val controller = launch()
        controller.click(R.id.toggle_reset)

        listOf(9, 1, 6, 2, 5).forEach { controller.tapSegment(R.id.toggle_bar, it) }

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    @Test
    fun `the label reports the current set`() {
        val controller = launch()
        controller.click(R.id.toggle_reset)

        controller.tapSegment(R.id.toggle_bar, 3)
        controller.tapSegment(R.id.toggle_bar, 8)

        assertThat(controller.label(R.id.toggle_label).text.toString())
            .isEqualTo("enabledDivisions = [3, 8]")
    }

    @Test
    fun `clear all empties the bar without changing its configuration`() {
        val controller = launch()

        controller.click(R.id.toggle_reset)

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions).isEmpty()
        assertThat(controller.bar(R.id.toggle_bar).divisions).isEqualTo(10)
        assertThat(controller.label(R.id.toggle_label).text.toString())
            .isEqualTo("enabledDivisions = []")
    }

    @Test
    fun `a click with no pointer position changes nothing and does not crash`() {
        // The path an accessibility service or keyboard takes. There is no segment
        // to infer, so the documented behaviour is to do nothing; the "Clear all"
        // button is what such users reach for instead.
        val controller = launch()

        controller.bar(R.id.toggle_bar).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    @Test
    fun `registering a division listener makes the bar clickable and focusable`() {
        val controller = launch()

        assertThat(controller.bar(R.id.toggle_bar).isClickable).isTrue()
        assertThat(controller.bar(R.id.toggle_bar).isFocusable).isTrue()
        // Bars without a listener stay non-interactive.
        assertThat(controller.bar(R.id.sparse_bar).isClickable).isFalse()
    }

    @Test
    fun `the stories bar mixes full divisions with a partial one`() {
        val controller = launch()
        val bar = controller.bar(R.id.stories_bar)

        assertThat(bar.enabledDivisions).containsExactly(0, 1).inOrder()
        assertThat(bar.getDivisionProgress(2)).isEqualTo(0.4f)
        // Underway is not done: only the full chapters count.
        assertThat(bar.completedSegmentCount).isEqualTo(2)
    }

    @Test
    fun `the heatmap bar gives every division its own colour`() {
        val controller = launch()
        val bar = controller.bar(R.id.heatmap_bar)

        assertThat(bar.completedSegmentCount).isEqualTo(bar.divisions)
        assertThat((0 until bar.divisions).all { bar.hasDivisionColor(it) }).isTrue()
        // Levels differ, so at least two distinct shades must be in play.
        assertThat((0 until bar.divisions).map { bar.getDivisionColor(it) }.distinct().size)
            .isAtLeast(2)
    }

    @Test
    fun `the heatmap bar exposes per-division accessibility from xml`() {
        val controller = launch()
        val bar = controller.bar(R.id.heatmap_bar)

        assertThat(bar.isPerDivisionAccessibilityEnabled).isTrue()
        val node = bar.accessibilityNodeProvider!!.createAccessibilityNodeInfo(0)!!
        assertThat(node.contentDescription.toString()).isEqualTo("Segment 1 of 14")
        assertThat(node.isChecked).isTrue()
    }

    @Test
    fun `the animated bars toggle from the layout alone`() {
        // They carry app:spb_tapToToggle rather than a listener, so this is the
        // whole path a consumer gets for free from XML: no activity code at all.
        val controller = launch()
        val bar = controller.bar(R.id.fade_bar)

        controller.tapSegment(R.id.fade_bar, 3)
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 3, 5).inOrder()

        controller.tapSegment(R.id.fade_bar, 1)
        assertThat(bar.enabledDivisions).containsExactly(2, 3, 5).inOrder()
    }

    // endregion

    @Test
    fun `the selection survives a configuration change`() {
        val controller = launch()
        controller.tapSegment(R.id.toggle_bar, 4)
        assertThat(controller.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 4, 5, 6, 9).inOrder()

        val recreated = controller.recreate()

        // The view saves and restores its own state, so nothing in the activity
        // has to remember which segments were on.
        assertThat(recreated.bar(R.id.toggle_bar).enabledDivisions)
            .containsExactly(1, 2, 4, 5, 6, 9).inOrder()
        assertThat(recreated.bar(R.id.sparse_bar).enabledDivisions)
            .containsExactly(1, 2, 5, 6, 9).inOrder()
    }
}

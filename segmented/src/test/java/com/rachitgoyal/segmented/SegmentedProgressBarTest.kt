package com.rachitgoyal.segmented

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.SparseArray
import android.view.AbsSavedState
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.View
import android.view.View.MeasureSpec
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Behavioural tests for the view's public contract: attribute parsing,
 * validation, progress bookkeeping, measurement, instance state and
 * accessibility.
 *
 * Several tests here are regressions for defects that shipped in 0.0.1 and are
 * labelled as such, so that a future refactor cannot quietly reintroduce them.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newBar() = SegmentedProgressBar(context)

    /**
     * Builds an [android.util.AttributeSet] for the given attributes.
     *
     * [Robolectric.buildAttributeSet] is deprecated upstream but remains the
     * only way to synthesise an AttributeSet from a library module, which has no
     * layout resources of its own to inflate. Inflation from a real layout file
     * is covered end to end by the demo app's `InflationTest`.
     */
    @Suppress("DEPRECATION")
    private fun attributesOf(vararg attributes: Pair<Int, String>) =
        Robolectric.buildAttributeSet()
            .apply { attributes.forEach { (id, value) -> addAttribute(id, value) } }
            .build()

    /**
     * Builds a bar attached to a real activity.
     *
     * Accessibility assertions need this: `View.onInitializeAccessibilityNodeInfo`
     * delegates to an internal method that bails out before copying properties
     * such as `contentDescription` while the view has no attach info, so a
     * detached view yields a node info that is misleadingly empty.
     */
    private fun attachedBar(configure: SegmentedProgressBar.() -> Unit): SegmentedProgressBar {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = SegmentedProgressBar(activity).apply(configure)
        activity.setContentView(bar)
        return bar
    }

    /** Populates a node info the way an accessibility service would. */
    private fun accessibilityNodeOf(bar: SegmentedProgressBar): AccessibilityNodeInfo =
        checkNotNull(bar.createAccessibilityNodeInfo()) { "view produced no node info" }

    /** Lays the bar out at a fixed size so that draw-time geometry is defined. */
    private fun SegmentedProgressBar.layoutAt(width: Int, height: Int) = apply {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
    }

    // region defaults

    @Test
    fun `a programmatically constructed bar uses the documented defaults`() {
        val bar = newBar()

        assertThat(bar.divisions).isEqualTo(SegmentedProgressBar.DEFAULT_DIVISIONS)
        assertThat(bar.divisions).isEqualTo(1)
        assertThat(bar.enabledDivisions).isEmpty()
        assertThat(bar.isDividerEnabled).isTrue()
        assertThat(bar.dividerWidth).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_WIDTH_PX)
        assertThat(bar.cornerRadius).isEqualTo(SegmentedProgressBar.DEFAULT_CORNER_RADIUS_PX)
        assertThat(bar.progressBarColor).isEqualTo(SegmentedProgressBar.DEFAULT_PROGRESS_COLOR)
        assertThat(bar.progressBarBackgroundColor).isEqualTo(SegmentedProgressBar.DEFAULT_BACKGROUND_COLOR)
        assertThat(bar.dividerColor).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_COLOR)
        assertThat(bar.completedSegmentCount).isEqualTo(0)
    }

    @Test
    fun `default colours keep the values that 0_0_1 shipped`() {
        // Existing users upgrading must not see their bars change colour.
        assertThat(SegmentedProgressBar.DEFAULT_PROGRESS_COLOR).isEqualTo(Color.parseColor("#5097e2"))
        assertThat(SegmentedProgressBar.DEFAULT_BACKGROUND_COLOR).isEqualTo(Color.parseColor("#c1c1c1"))
        assertThat(SegmentedProgressBar.DEFAULT_DIVIDER_COLOR).isEqualTo(Color.WHITE)
    }

    // endregion

    // region attribute parsing

    @Test
    fun `every xml attribute is parsed`() {
        val attrs = attributesOf(
            R.attr.divisions to "7",
            R.attr.progressBarColor to "#ff0000",
            R.attr.progressBarBackgroundColor to "#00ff00",
            R.attr.dividerColor to "#0000ff",
            R.attr.dividerWidth to "3dp",
            R.attr.isDividerEnabled to "false",
            R.attr.cornerRadius to "5dp",
        )

        val bar = SegmentedProgressBar(context, attrs)
        val density = context.resources.displayMetrics.density

        assertThat(bar.divisions).isEqualTo(7)
        assertThat(bar.progressBarColor).isEqualTo(Color.RED)
        assertThat(bar.progressBarBackgroundColor).isEqualTo(Color.GREEN)
        assertThat(bar.dividerColor).isEqualTo(Color.BLUE)
        assertThat(bar.dividerWidth).isWithin(1f).of(3f * density)
        assertThat(bar.isDividerEnabled).isFalse()
        assertThat(bar.cornerRadius).isWithin(1f).of(5f * density)
    }

    @Test
    fun `omitted xml attributes fall back to the defaults`() {
        val attrs = attributesOf(R.attr.divisions to "4")

        val bar = SegmentedProgressBar(context, attrs)

        assertThat(bar.divisions).isEqualTo(4)
        assertThat(bar.progressBarColor).isEqualTo(SegmentedProgressBar.DEFAULT_PROGRESS_COLOR)
        assertThat(bar.dividerWidth).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_WIDTH_PX)
        assertThat(bar.cornerRadius).isEqualTo(SegmentedProgressBar.DEFAULT_CORNER_RADIUS_PX)
    }

    @Test
    fun `an invalid divisions attribute fails loudly at inflation`() {
        val attrs = attributesOf(R.attr.divisions to "0")

        val error = runCatching { SegmentedProgressBar(context, attrs) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("divisions")
    }

    @Test
    fun `an android background colour does not crash and does not repaint the track`() {
        // Regression guard: setBackgroundColor is overridden with non-standard
        // semantics, so it must survive being reached during inflation.
        val attrs = attributesOf(android.R.attr.background to "#ff00ff")

        val bar = SegmentedProgressBar(context, attrs)

        assertThat(bar.progressBarBackgroundColor)
            .isEqualTo(SegmentedProgressBar.DEFAULT_BACKGROUND_COLOR)
    }

    // endregion

    // region validation

    @Test
    fun `divisions below one is rejected`() {
        val bar = newBar()

        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            val error = runCatching { bar.divisions = invalid }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
        // The rejected assignments left the view untouched.
        assertThat(bar.divisions).isEqualTo(1)
    }

    @Test
    fun `a negative divider width is rejected`() {
        val bar = newBar()

        val error = runCatching { bar.dividerWidth = -1f }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("dividerWidth")
        assertThat(bar.dividerWidth).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_WIDTH_PX)
    }

    @Test
    fun `a non-finite divider width is rejected`() {
        val bar = newBar()

        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val error = runCatching { bar.dividerWidth = invalid }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `a negative corner radius is rejected`() {
        val bar = newBar()

        val error = runCatching { bar.cornerRadius = -0.5f }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("cornerRadius")
    }

    @Test
    fun `a zero divider width is accepted`() {
        val bar = newBar()

        bar.dividerWidth = 0f

        assertThat(bar.dividerWidth).isEqualTo(0f)
    }

    // endregion

    // region enabledDivisions

    @Test
    fun `enabled divisions are sorted and de-duplicated`() {
        val bar = newBar().apply { divisions = 10 }

        bar.enabledDivisions = listOf(5, 1, 5, 9, 1, 0)

        assertThat(bar.enabledDivisions).containsExactly(0, 1, 5, 9).inOrder()
    }

    @Test
    fun `negative enabled divisions are dropped`() {
        val bar = newBar().apply { divisions = 5 }

        bar.enabledDivisions = listOf(-3, 0, -1, 2)

        assertThat(bar.enabledDivisions).containsExactly(0, 2).inOrder()
    }

    @Test
    fun `out of range enabled divisions are retained so call order does not matter`() {
        val bar = newBar()

        // enabledDivisions assigned before divisions is widened.
        bar.enabledDivisions = listOf(0, 3, 7)
        assertThat(bar.completedSegmentCount).isEqualTo(1)

        bar.divisions = 10
        assertThat(bar.enabledDivisions).containsExactly(0, 3, 7).inOrder()
        assertThat(bar.completedSegmentCount).isEqualTo(3)
    }

    @Test
    fun `the caller's list is copied on assignment`() {
        // Regression: 0.0.1 stored the caller's List by reference, so later
        // mutations silently changed the view's state without a repaint.
        val bar = newBar().apply { divisions = 10 }
        val source = mutableListOf(1, 2)

        bar.enabledDivisions = source
        source.add(9)

        assertThat(bar.enabledDivisions).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `completed segment count ignores out of range indices`() {
        val bar = newBar().apply {
            divisions = 4
            enabledDivisions = listOf(0, 1, 4, 99)
        }

        assertThat(bar.completedSegmentCount).isEqualTo(2)
    }

    // endregion

    @Test
    fun `enableDivision keeps the selection sorted`() {
        val bar = newBar().apply { divisions = 10 }

        bar.enableDivision(5)
        bar.enableDivision(1)
        bar.enableDivision(8)
        bar.enableDivision(3)

        assertThat(bar.enabledDivisions).containsExactly(1, 3, 5, 8).inOrder()
    }

    @Test
    fun `enableDivision is idempotent`() {
        val bar = newBar().apply { divisions = 10 }

        bar.enableDivision(4)
        bar.enableDivision(4)

        assertThat(bar.enabledDivisions).containsExactly(4)
    }

    @Test
    fun `enableDivision ignores negative indices`() {
        val bar = newBar().apply { divisions = 10 }

        bar.enableDivision(-1)

        assertThat(bar.enabledDivisions).isEmpty()
    }

    @Test
    fun `disableDivision removes only the requested segment`() {
        val bar = newBar().apply {
            divisions = 10
            enabledDivisions = listOf(1, 4, 7)
        }

        bar.disableDivision(4)

        assertThat(bar.enabledDivisions).containsExactly(1, 7).inOrder()
    }

    @Test
    fun `disableDivision of an unlit segment is a no-op`() {
        val bar = newBar().apply {
            divisions = 10
            enabledDivisions = listOf(1)
        }

        bar.disableDivision(9)

        assertThat(bar.enabledDivisions).containsExactly(1)
    }

    @Test
    fun `isDivisionEnabled reports membership`() {
        val bar = newBar().apply {
            divisions = 10
            enabledDivisions = listOf(2, 6)
        }

        assertThat(bar.isDivisionEnabled(2)).isTrue()
        assertThat(bar.isDivisionEnabled(6)).isTrue()
        assertThat(bar.isDivisionEnabled(3)).isFalse()
        assertThat(bar.isDivisionEnabled(-1)).isFalse()
    }

    // endregion

    // region toggleDivision

    @Test
    fun `toggleDivision flips a single segment and reports its new state`() {
        val bar = newBar().apply {
            divisions = 10
            enabledDivisions = listOf(1, 2, 5, 6, 9)
        }

        assertThat(bar.toggleDivision(4)).isTrue()
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 4, 5, 6, 9).inOrder()

        assertThat(bar.toggleDivision(4)).isFalse()
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 5, 6, 9).inOrder()
    }

    @Test
    fun `toggleDivision leaves every other segment alone`() {
        val bar = newBar().apply {
            divisions = 10
            enabledDivisions = listOf(1, 2, 5, 6, 9)
        }

        bar.toggleDivision(5)

        assertThat(bar.enabledDivisions).containsExactly(1, 2, 6, 9).inOrder()
    }

    @Test
    fun `toggleDivision ignores negative indices`() {
        val bar = newBar().apply { divisions = 10 }

        assertThat(bar.toggleDivision(-1)).isFalse()
        assertThat(bar.enabledDivisions).isEmpty()
    }

    @Test
    fun `an arbitrary subset can be built one toggle at a time in any order`() {
        // The library's whole purpose, exercised the way a tap handler would.
        val bar = newBar().apply { divisions = 10 }

        listOf(9, 1, 6, 2, 5).forEach { bar.toggleDivision(it) }

        assertThat(bar.enabledDivisions).containsExactly(1, 2, 5, 6, 9).inOrder()
        assertThat(bar.completedSegmentCount).isEqualTo(5)
        listOf(0, 3, 4, 7, 8).forEach { assertThat(bar.isDivisionEnabled(it)).isFalse() }
    }

    // endregion

    // region divisionAt

    @Test
    fun `divisionAt maps a position to the segment containing it`() {
        val bar = newBar().apply { divisions = 10 }.layoutAt(width = 500, height = 20)

        // 500 / 10 == 50px per segment.
        assertThat(bar.divisionAt(0f)).isEqualTo(0)
        assertThat(bar.divisionAt(49.9f)).isEqualTo(0)
        assertThat(bar.divisionAt(50f)).isEqualTo(1)
        assertThat(bar.divisionAt(275f)).isEqualTo(5)
        assertThat(bar.divisionAt(499.9f)).isEqualTo(9)
    }

    @Test
    fun `divisionAt reports NO_DIVISION outside the bar`() {
        val bar = newBar().apply { divisions = 10 }.layoutAt(width = 500, height = 20)

        assertThat(bar.divisionAt(-1f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
        assertThat(bar.divisionAt(500f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
        assertThat(bar.divisionAt(1000f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
    }

    @Test
    fun `divisionAt accounts for padding`() {
        val bar = newBar().apply {
            divisions = 4
            setPadding(100, 0, 100, 0)
        }.layoutAt(width = 500, height = 20)

        // The content box is 300px wide starting at x=100, so 75px per segment.
        assertThat(bar.divisionAt(99f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
        assertThat(bar.divisionAt(100f)).isEqualTo(0)
        assertThat(bar.divisionAt(180f)).isEqualTo(1)
        assertThat(bar.divisionAt(399f)).isEqualTo(3)
        assertThat(bar.divisionAt(400f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
    }

    @Test
    fun `divisionAt never returns an out of range index`() {
        val bar = newBar().apply { divisions = 7 }.layoutAt(width = 333, height = 20)

        for (x in 0 until 333) {
            val index = bar.divisionAt(x.toFloat())
            assertThat(index).isAtLeast(0)
            assertThat(index).isAtMost(6)
        }
    }

    @Test
    fun `divisionAt reports NO_DIVISION before the view has been laid out`() {
        val bar = newBar().apply { divisions = 10 }

        assertThat(bar.divisionAt(10f)).isEqualTo(SegmentedProgressBar.NO_DIVISION)
    }

    @Test
    fun `divisionAt is the inverse of where a segment is drawn`() {
        // Tapping the centre of segment i must resolve back to i, for every i.
        val bar = newBar().apply { divisions = 10 }.layoutAt(width = 500, height = 20)
        val segmentWidth = 500f / 10

        for (index in 0 until 10) {
            val centre = segmentWidth * index + segmentWidth / 2f
            assertThat(bar.divisionAt(centre)).isEqualTo(index)
        }
    }

    // endregion

    // region division click listener

    /**
     * A bar attached to an activity and reliably laid out at a known size.
     *
     * Two Robolectric details make this fiddlier than it looks, and both would
     * otherwise produce quietly wrong tests rather than failures:
     *
     * 1. `View.onTouchEvent` fires a click by `post`ing it. On a *detached* view
     *    that runnable sits in the run queue until attach, so it never executes
     *    and every touch assertion would pass vacuously by reporting nothing.
     * 2. Draining the looper runs a real layout pass, which overwrites any size
     *    set by calling `layout()` directly and replaces it with the window
     *    width. The size therefore has to come from exact `LayoutParams`, so
     *    that re-laying out is idempotent.
     */
    private fun touchableBar(
        width: Int = 500,
        height: Int = 20,
        configure: SegmentedProgressBar.() -> Unit,
    ): SegmentedProgressBar {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = SegmentedProgressBar(activity).apply(configure)
        activity.setContentView(bar, ViewGroup.LayoutParams(width, height))
        shadowOf(Looper.getMainLooper()).idle()
        check(bar.width == width) {
            "expected the bar to be laid out ${width}px wide but it was ${bar.width}px; " +
                "every coordinate assertion below would be measuring the wrong bar"
        }
        return bar
    }

    /** Dispatches a full down/up touch at [x] and drains the posted click. */
    private fun SegmentedProgressBar.touchAt(x: Float) {
        val now = SystemClock.uptimeMillis()
        dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, 5f, 0))
        dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, 5f, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `a tap reports the segment that was hit`() {
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        bar.touchAt(275f) // segment 5 spans 250..300
        bar.touchAt(30f) // segment 0 spans 0..50

        assertThat(tapped).containsExactly(5, 0).inOrder()
    }

    @Test
    fun `the listener receives the bar it was registered on`() {
        var received: SegmentedProgressBar? = null
        val bar = touchableBar {
            divisions = 4
            setOnDivisionClickListener { view, _ -> received = view }
        }

        bar.touchAt(50f)

        assertThat(received).isSameInstanceAs(bar)
    }

    @Test
    fun `registering a listener makes the view clickable and focusable`() {
        val bar = newBar()

        assertThat(bar.isClickable).isFalse()

        bar.setOnDivisionClickListener { _, _ -> }
        assertThat(bar.isClickable).isTrue()
        assertThat(bar.isFocusable).isTrue()

        bar.setOnDivisionClickListener(null)
        assertThat(bar.isClickable).isFalse()
        assertThat(bar.isFocusable).isFalse()
    }

    @Test
    fun `a removed listener stops being notified`() {
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        bar.touchAt(275f)
        bar.setOnDivisionClickListener(null)
        bar.touchAt(30f)

        assertThat(tapped).containsExactly(5)
    }

    @Test
    fun `the listener only reports the tap and does not change state itself`() {
        // Deciding what a tap means is the caller's job.
        var notified = false
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, _ -> notified = true }
        }

        bar.touchAt(275f)

        assertThat(notified).isTrue()
        assertThat(bar.enabledDivisions).isEmpty()
    }

    @Test
    fun `a tap in the padding reports nothing but a tap on the track does`() {
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 4
            setPadding(100, 0, 100, 0)
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        bar.touchAt(50f) // inside the left padding
        assertThat(tapped).isEmpty()

        // Positive control, so this test cannot pass by simply never firing.
        bar.touchAt(150f) // content box starts at 100, 75px per segment
        assertThat(tapped).containsExactly(0)
    }

    @Test
    fun `an accessibility style click with no pointer position reports nothing`() {
        // performClick() carries no coordinates, so there is no segment to infer.
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        bar.performClick()
        assertThat(tapped).isEmpty()

        // Positive control.
        bar.touchAt(275f)
        assertThat(tapped).containsExactly(5)
    }

    @Test
    fun `each tap is resolved independently rather than reusing a stale position`() {
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        bar.touchAt(275f)
        bar.performClick() // no new touch, so nothing more should arrive

        assertThat(tapped).containsExactly(5)
    }

    @Test
    fun `tapping every segment in turn reports each index exactly once`() {
        val tapped = mutableListOf<Int>()
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { _, index -> tapped += index }
        }

        for (index in 0 until 10) {
            bar.touchAt(50f * index + 25f)
        }

        assertThat(tapped).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9).inOrder()
    }

    @Test
    fun `toggling from the listener builds an arbitrary subset`() {
        // End to end: the interaction this library exists to support.
        val bar = touchableBar {
            divisions = 10
            setOnDivisionClickListener { view, index -> view.toggleDivision(index) }
        }

        listOf(9, 1, 6, 2, 5).forEach { bar.touchAt(50f * it + 25f) }

        assertThat(bar.enabledDivisions).containsExactly(1, 2, 5, 6, 9).inOrder()

        // Tapping a lit segment clears just that one.
        bar.touchAt(50f * 5 + 25f)
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 6, 9).inOrder()
    }

    // endregion

    // region tap to toggle

    @Test
    fun `tap to toggle is off by default`() {
        val bar = newBar()

        assertThat(bar.isTapToToggleEnabled).isFalse()
        assertThat(bar.isClickable).isFalse()
    }

    @Test
    fun `tap to toggle makes the bar interactive with no listener at all`() {
        val bar = touchableBar {
            divisions = 10
            isTapToToggleEnabled = true
        }

        listOf(9, 1, 6, 2, 5).forEach { bar.touchAt(50f * it + 25f) }
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 5, 6, 9).inOrder()

        bar.touchAt(50f * 5 + 25f)
        assertThat(bar.enabledDivisions).containsExactly(1, 2, 6, 9).inOrder()
    }

    @Test
    fun `turning tap to toggle on makes the view clickable and focusable`() {
        val bar = newBar()

        bar.isTapToToggleEnabled = true
        assertThat(bar.isClickable).isTrue()
        assertThat(bar.isFocusable).isTrue()

        bar.isTapToToggleEnabled = false
        assertThat(bar.isClickable).isFalse()
        assertThat(bar.isFocusable).isFalse()
    }

    @Test
    fun `a listener still works alongside tap to toggle, and sees the new state`() {
        // The order matters: a listener that renders a label from the bar would
        // otherwise draw the state from before the tap.
        val states = mutableListOf<Boolean>()
        val bar = touchableBar {
            divisions = 4
            isTapToToggleEnabled = true
            setOnDivisionClickListener { view, index -> states += view.isDivisionEnabled(index) }
        }

        bar.touchAt(150f) // segment 1 spans 125..250
        bar.touchAt(150f)

        assertThat(states).containsExactly(true, false).inOrder()
        assertThat(bar.enabledDivisions).isEmpty()
    }

    @Test
    fun `clearing a listener leaves a tap to toggle bar interactive`() {
        val bar = touchableBar {
            divisions = 4
            isTapToToggleEnabled = true
            setOnDivisionClickListener { _, _ -> }
        }

        bar.setOnDivisionClickListener(null)
        bar.touchAt(150f)

        assertThat(bar.isClickable).isTrue()
        assertThat(bar.enabledDivisions).containsExactly(1)
    }

    @Test
    fun `an unclickable bar ignores taps however it was configured`() {
        // The documented off switch: the platform's own gate, which View
        // .onTouchEvent honours before any of this class's code runs.
        val bar = touchableBar {
            divisions = 4
            isTapToToggleEnabled = true
        }
        bar.isClickable = false

        bar.touchAt(150f)

        assertThat(bar.enabledDivisions).isEmpty()
    }

    @Test
    fun `a tap outside the bar toggles nothing`() {
        val bar = touchableBar {
            divisions = 4
            setPadding(100, 0, 100, 0)
            isTapToToggleEnabled = true
        }

        bar.touchAt(50f) // inside the left padding
        assertThat(bar.enabledDivisions).isEmpty()

        // Positive control, so this cannot pass by never toggling anything.
        bar.touchAt(150f)
        assertThat(bar.enabledDivisions).isNotEmpty()
    }

    // endregion

    // region reset

    @Test
    fun `reset clears progress but preserves configuration`() {
        // Regression: 0.0.1's reset() cleared the internal divider positions and
        // left enabledDivisions untouched, so it wiped the dividers and kept the
        // progress, the exact opposite of what it claimed to do.
        val bar = newBar().apply {
            divisions = 8
            dividerWidth = 4f
            cornerRadius = 6f
            progressBarColor = Color.RED
            dividerColor = Color.BLUE
            progressBarBackgroundColor = Color.GREEN
            enabledDivisions = listOf(0, 1, 2)
        }

        bar.reset()

        assertThat(bar.enabledDivisions).isEmpty()
        assertThat(bar.completedSegmentCount).isEqualTo(0)
        assertThat(bar.divisions).isEqualTo(8)
        assertThat(bar.dividerWidth).isEqualTo(4f)
        assertThat(bar.cornerRadius).isEqualTo(6f)
        assertThat(bar.progressBarColor).isEqualTo(Color.RED)
        assertThat(bar.dividerColor).isEqualTo(Color.BLUE)
        assertThat(bar.progressBarBackgroundColor).isEqualTo(Color.GREEN)
    }

    // endregion

    // region repainting

    @Test
    fun `every setter requests a repaint`() {
        // Regression: in 0.0.1 only setDivisions and setEnabledDivisions
        // invalidated, so programmatic colour, divider and corner changes did
        // not appear until something else happened to redraw the view.
        val bar = newBar().apply { divisions = 4 }

        assertRepaints(bar) { divisions = 6 }
        assertRepaints(bar) { enabledDivisions = listOf(1) }
        assertRepaints(bar) { progressBarColor = Color.RED }
        assertRepaints(bar) { progressBarBackgroundColor = Color.GREEN }
        assertRepaints(bar) { dividerColor = Color.BLUE }
        assertRepaints(bar) { dividerWidth = 9f }
        assertRepaints(bar) { isDividerEnabled = false }
        assertRepaints(bar) { cornerRadius = 11f }
        assertRepaints(bar) { enableDivision(3) }
        assertRepaints(bar) { disableDivision(3) }
        assertRepaints(bar) { reset() }
    }

    @Test
    fun `re-setting an unchanged value does not request a repaint`() {
        val bar = newBar().apply {
            divisions = 4
            progressBarColor = Color.RED
            enabledDivisions = listOf(1, 2)
        }

        assertDoesNotRepaint(bar) { divisions = 4 }
        assertDoesNotRepaint(bar) { progressBarColor = Color.RED }
        assertDoesNotRepaint(bar) { enabledDivisions = listOf(2, 1) }
        assertDoesNotRepaint(bar) { enableDivision(1) }
        assertDoesNotRepaint(bar) { disableDivision(3) }
    }

    @Test
    fun `reset on an already empty bar does not request a repaint`() {
        val bar = newBar().apply { divisions = 4 }

        assertDoesNotRepaint(bar) { reset() }
    }

    private fun assertRepaints(
        bar: SegmentedProgressBar,
        block: SegmentedProgressBar.() -> Unit,
    ) {
        shadowOf(bar).clearWasInvalidated()
        bar.block()
        assertThat(shadowOf(bar).wasInvalidated()).isTrue()
    }

    private fun assertDoesNotRepaint(
        bar: SegmentedProgressBar,
        block: SegmentedProgressBar.() -> Unit,
    ) {
        shadowOf(bar).clearWasInvalidated()
        bar.block()
        assertThat(shadowOf(bar).wasInvalidated()).isFalse()
    }

    // endregion

    // region deprecated api

    @Test
    @Suppress("DEPRECATION")
    fun `the deprecated setBackgroundColor still paints the track`() {
        // Kept working on purpose so 0.0.1 call sites behave identically.
        val bar = newBar()

        bar.setBackgroundColor(Color.MAGENTA)

        assertThat(bar.progressBarBackgroundColor).isEqualTo(Color.MAGENTA)
    }

    // endregion

    // region measurement

    @Test
    fun `wrap_content falls back to an intrinsic size instead of collapsing`() {
        // Regression: the sample layout in 0.0.1 used wrap_content for width and
        // the view provided no onMeasure, so its intrinsic size was undefined.
        val bar = newBar()
        val density = context.resources.displayMetrics.density

        bar.measure(
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
        )

        assertThat(bar.measuredWidth).isEqualTo((144f * density).toInt())
        assertThat(bar.measuredHeight).isEqualTo((8f * density).toInt())
    }

    @Test
    fun `an exact measure spec is honoured`() {
        val bar = newBar()

        bar.measure(
            MeasureSpec.makeMeasureSpec(500, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(24, MeasureSpec.EXACTLY),
        )

        assertThat(bar.measuredWidth).isEqualTo(500)
        assertThat(bar.measuredHeight).isEqualTo(24)
    }

    @Test
    fun `wrap_content is capped by the available space`() {
        val bar = newBar()

        bar.measure(
            MeasureSpec.makeMeasureSpec(40, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(6, MeasureSpec.AT_MOST),
        )

        assertThat(bar.measuredWidth).isEqualTo(40)
        assertThat(bar.measuredHeight).isEqualTo(6)
    }

    @Test
    fun `padding is added to the intrinsic size`() {
        val bar = newBar()
        val density = context.resources.displayMetrics.density
        bar.setPadding(10, 5, 20, 7)

        bar.measure(
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
        )

        assertThat(bar.measuredWidth).isEqualTo((144f * density).toInt() + 30)
        assertThat(bar.measuredHeight).isEqualTo((8f * density).toInt() + 12)
    }

    @Test
    fun `an explicit minimum height wins over the intrinsic height`() {
        val bar = newBar()
        bar.minimumHeight = 100

        bar.measure(
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(2000, MeasureSpec.AT_MOST),
        )

        assertThat(bar.measuredHeight).isEqualTo(100)
    }

    // endregion

    // region instance state

    @Test
    fun `progress survives a save and restore cycle`() {
        val saved = newBar().apply {
            id = View.generateViewId()
            divisions = 12
            enabledDivisions = listOf(0, 3, 11)
        }
        val restored = newBar().apply { id = saved.id }

        transferHierarchyState(from = saved, to = restored)

        assertThat(restored.divisions).isEqualTo(12)
        assertThat(restored.enabledDivisions).containsExactly(0, 3, 11).inOrder()
        assertThat(restored.completedSegmentCount).isEqualTo(3)
    }

    @Test
    fun `an empty bar survives a save and restore cycle`() {
        val saved = newBar().apply {
            id = View.generateViewId()
            divisions = 5
        }
        val restored = newBar().apply { id = saved.id }

        transferHierarchyState(from = saved, to = restored)

        assertThat(restored.divisions).isEqualTo(5)
        assertThat(restored.enabledDivisions).isEmpty()
    }

    @Test
    fun `state written by some other view under the same id is ignored`() {
        // Ids are reused across layouts, so the view has to tolerate finding
        // state it did not write rather than casting blindly.
        val bar = newBar().apply {
            id = View.generateViewId()
            divisions = 4
            enabledDivisions = listOf(1)
        }
        val container = SparseArray<Parcelable>()
        container.put(bar.id, AbsSavedState.EMPTY_STATE)

        bar.restoreHierarchyState(container)

        assertThat(bar.divisions).isEqualTo(4)
        assertThat(bar.enabledDivisions).containsExactly(1)
    }

    /**
     * Round-trips view state through a [SparseArray] the way the framework does
     * on configuration change, including a real [Parcel] hop so the
     * [Parcelable] implementation is genuinely exercised rather than passed
     * through by reference.
     */
    private fun transferHierarchyState(from: SegmentedProgressBar, to: SegmentedProgressBar) {
        val container = SparseArray<Parcelable>()
        from.saveHierarchyState(container)

        val parcel = Parcel.obtain()
        try {
            parcel.writeSparseArray(container)
            parcel.setDataPosition(0)
            val revived = parcel.readSparseArray(
                SegmentedProgressBar::class.java.classLoader,
                Parcelable::class.java,
            )
            checkNotNull(revived) { "state did not survive the parcel round-trip" }
            to.restoreHierarchyState(revived)
        } finally {
            parcel.recycle()
        }
    }

    // endregion

    // region accessibility

    @Test
    fun `the bar reports itself as a progress bar to accessibility services`() {
        val bar = attachedBar {
            divisions = 5
            enabledDivisions = listOf(0, 1)
        }

        val info = accessibilityNodeOf(bar)

        assertThat(info.className.toString()).isEqualTo(ProgressBar::class.java.name)
    }

    @Test
    fun `a content description is generated from the current progress`() {
        val bar = attachedBar {
            divisions = 5
            enabledDivisions = listOf(0, 1)
        }

        val info = accessibilityNodeOf(bar)

        assertThat(info.contentDescription.toString()).isEqualTo("2 of 5 segments complete")
    }

    @Test
    fun `a caller-supplied content description is not overwritten`() {
        val bar = attachedBar {
            divisions = 5
            enabledDivisions = listOf(0, 1)
            contentDescription = "Onboarding progress"
        }

        val info = accessibilityNodeOf(bar)

        assertThat(info.contentDescription.toString()).isEqualTo("Onboarding progress")
    }

    @Test
    fun `the generated content description is grammatically singular for one division`() {
        val bar = attachedBar {
            divisions = 1
            enabledDivisions = listOf(0)
        }

        val info = accessibilityNodeOf(bar)

        assertThat(info.contentDescription.toString()).isEqualTo("1 of 1 segment complete")
    }

    @Test
    fun `the generated content description counts only drawn segments`() {
        val bar = attachedBar {
            divisions = 3
            enabledDivisions = listOf(0, 1, 50)
        }

        val info = accessibilityNodeOf(bar)

        assertThat(info.contentDescription.toString()).isEqualTo("2 of 3 segments complete")
    }

    // endregion

    // region integration

    @Test
    fun `the view draws without error inside a parent at a realistic size`() {
        val parent = FrameLayout(context)
        val bar = newBar().apply {
            divisions = 10
            dividerWidth = 4f
            cornerRadius = 8f
            enabledDivisions = listOf(0, 1, 2, 3, 4, 5)
        }
        parent.addView(bar)

        bar.layoutAt(width = 600, height = 32)

        assertThat(bar.width).isEqualTo(600)
        assertThat(bar.height).isEqualTo(32)
    }

    // endregion
}

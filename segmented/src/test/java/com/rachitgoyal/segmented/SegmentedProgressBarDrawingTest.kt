package com.rachitgoyal.segmented

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * Tests what the view actually puts on the canvas.
 *
 * Drawing is where every one of 0.0.1's visible defects lived, so rather than
 * assert on internal state these tests record the real draw calls through a
 * [RecordingCanvas] and assert on their geometry and colour. Expected
 * coordinates are written out by hand rather than derived from
 * [SegmentGeometry], so a mistake in the geometry cannot make a drawing test
 * pass.
 *
 * The fixture throughout is a 300 x 30 bar with 3 divisions and a 10px divider,
 * which lays out as:
 * ```
 * segment 0: 0   .. 95      divider 1:  95 .. 105
 * segment 1: 105 .. 195     divider 2: 195 .. 205
 * segment 2: 205 .. 300
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarDrawingTest {

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 30
        const val DIVISIONS = 3
        const val DIVIDER_WIDTH = 10f

        const val PROGRESS = Color.RED
        const val TRACK = Color.GREEN
        const val DIVIDER = Color.BLUE
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // region harness

    private fun newBar(
        cornerRadius: Float = 0f,
        configure: SegmentedProgressBar.() -> Unit = {},
    ) = SegmentedProgressBar(context).apply {
        divisions = DIVISIONS
        dividerWidth = DIVIDER_WIDTH
        progressBarColor = PROGRESS
        progressBarBackgroundColor = TRACK
        dividerColor = DIVIDER
        this.cornerRadius = cornerRadius
        configure()
    }

    private fun render(
        bar: SegmentedProgressBar,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): RecordingCanvas = renderToRecordingCanvas(bar, width, height)

    private fun assertSpan(op: DrawOp, left: Float, right: Float) {
        assertThat(op.left).isWithin(0.01f).of(left)
        assertThat(op.right).isWithin(0.01f).of(right)
        assertThat(op.top).isWithin(0.01f).of(0f)
        assertThat(op.bottom).isWithin(0.01f).of(HEIGHT.toFloat())
    }

    // endregion

    // region the track

    @Test
    fun `the track is drawn across the full width even when nothing is lit`() {
        val canvas = render(newBar())

        // One cell per division rather than a single span: that is what makes a
        // transparent divider a true gap instead of a hole showing the track.
        val track = canvas.ops.ofColor(TRACK)
        assertThat(track).hasSize(DIVISIONS)
        assertSpan(track.union(), left = 0f, right = WIDTH.toFloat())
    }

    @Test
    fun `the track leaves the divider space empty`() {
        val canvas = render(newBar())

        val cells = canvas.ops.ofColor(TRACK).sortedBy { it.left }
        // Cells stop either side of each divider rather than running under it.
        assertSpan(cells[0], left = 0f, right = 95f)
        assertSpan(cells[1], left = 105f, right = 195f)
        assertSpan(cells[2], left = 205f, right = 300f)
    }

    // endregion

    // region dividers

    @Test
    fun `dividers are drawn even when no segment is lit`() {
        // Regression: 0.0.1 nested the divider loop inside the loop over enabled
        // segments, so an empty bar had no dividers at all.
        val canvas = render(newBar())

        val dividers = canvas.ops.ofColor(DIVIDER)
        assertThat(dividers).hasSize(DIVISIONS - 1)
        assertSpan(dividers[0], left = 95f, right = 105f)
        assertSpan(dividers[1], left = 195f, right = 205f)
    }

    @Test
    fun `each divider is drawn exactly once regardless of how many segments are lit`() {
        // Regression: the same nesting bug in 0.0.1 redrew every divider once per
        // lit segment, six divider rects here instead of two.
        val canvas = render(newBar { enabledDivisions = listOf(0, 1, 2) })

        assertThat(canvas.ops.ofColor(DIVIDER)).hasSize(DIVISIONS - 1)
    }

    @Test
    fun `no dividers are drawn when they are disabled`() {
        val canvas = render(newBar { isDividerEnabled = false })

        assertThat(canvas.ops.ofColor(DIVIDER)).isEmpty()
    }

    @Test
    fun `no dividers are drawn for a zero divider width`() {
        val canvas = render(newBar { dividerWidth = 0f })

        assertThat(canvas.ops.ofColor(DIVIDER)).isEmpty()
    }

    @Test
    fun `no dividers are drawn for a single division`() {
        val canvas = render(newBar { divisions = 1 })

        assertThat(canvas.ops.ofColor(DIVIDER)).isEmpty()
    }

    @Test
    fun `disabling dividers makes segments span their full cell`() {
        val canvas = render(newBar { isDividerEnabled = false; enabledDivisions = listOf(1) })

        val segments = canvas.ops.ofColor(PROGRESS)
        assertThat(segments).hasSize(1)
        assertSpan(segments.single(), left = 100f, right = 200f)
    }

    // endregion

    // region segments

    @Test
    fun `a lit segment is drawn inset by half a divider on its interior edges`() {
        val canvas = render(newBar { enabledDivisions = listOf(1) })

        val segments = canvas.ops.ofColor(PROGRESS)
        assertThat(segments).hasSize(1)
        assertSpan(segments.single(), left = 105f, right = 195f)
    }

    @Test
    fun `the first and last segments run flush to the ends of the bar`() {
        val canvas = render(newBar { enabledDivisions = listOf(0, 2) })

        val segments = canvas.ops.ofColor(PROGRESS)
        assertThat(segments).hasSize(2)
        assertSpan(segments[0], left = 0f, right = 95f)
        assertSpan(segments[1], left = 205f, right = 300f)
    }

    @Test
    fun `only lit segments are drawn`() {
        val canvas = render(newBar { enabledDivisions = listOf(2) })

        val segments = canvas.ops.ofColor(PROGRESS)
        assertThat(segments).hasSize(1)
        assertSpan(segments.single(), left = 205f, right = 300f)
    }

    @Test
    fun `every lit segment is drawn exactly once`() {
        val canvas = render(newBar { enabledDivisions = listOf(0, 1, 2) })

        assertThat(canvas.ops.ofColor(PROGRESS)).hasSize(3)
    }

    @Test
    fun `out of range indices are skipped without crashing`() {
        val canvas = render(
            newBar {
                divisions = 3
                enabledDivisions = listOf(0, 3, 4, 1000)
            },
        )

        val segments = canvas.ops.ofColor(PROGRESS)
        assertThat(segments).hasSize(1)
        assertSpan(segments.single(), left = 0f, right = 95f)
    }

    @Test
    fun `segments never overlap the dividers`() {
        // The 0.0.1 geometry added the full divider width to a segment's left
        // edge without removing it from the right, so each segment bled under
        // the divider to its right.
        val canvas = render(newBar { enabledDivisions = listOf(0, 1, 2) })

        val spans = (canvas.ops.ofColor(PROGRESS) + canvas.ops.ofColor(DIVIDER))
            .sortedBy { it.left }

        for ((previous, next) in spans.zipWithNext()) {
            assertThat(next.left).isAtLeast(previous.right - 0.01f)
        }
    }

    @Test
    fun `lit segments and dividers together cover the whole bar`() {
        val canvas = render(newBar { enabledDivisions = listOf(0, 1, 2) })

        val covered = (canvas.ops.ofColor(PROGRESS) + canvas.ops.ofColor(DIVIDER))
            .sumOf { (it.right - it.left).toDouble() }

        assertThat(covered).isWithin(0.05).of(WIDTH.toDouble())
    }

    // endregion

    // region corner rounding

    @Test
    fun `with a corner radius only the end segments are drawn as rounded paths`() {
        val canvas = render(newBar(cornerRadius = 8f) { enabledDivisions = listOf(0, 1, 2) })

        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(3)
        assertThat(segments[0].rounded).isTrue() // touches the left end
        assertThat(segments[1].rounded).isFalse() // interior, square on both sides
        assertThat(segments[2].rounded).isTrue() // touches the right end
    }

    @Test
    fun `the track rounds only the ends of the bar by default`() {
        val canvas = render(newBar(cornerRadius = 8f))

        val cells = canvas.ops.ofColor(TRACK).sortedBy { it.left }
        assertThat(cells.map { it.rounded }).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun `a zero corner radius draws everything as plain rectangles`() {
        val canvas = render(newBar(cornerRadius = 0f) { enabledDivisions = listOf(0, 1, 2) })

        assertThat(canvas.ops.map { it.rounded }).doesNotContain(true)
    }

    @Test
    fun `rounded end segments still occupy their exact span`() {
        // Rounding must change the corners, not the extent.
        val canvas = render(newBar(cornerRadius = 8f) { enabledDivisions = listOf(0, 2) })

        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertSpan(segments[0], left = 0f, right = 95f)
        assertSpan(segments[1], left = 205f, right = 300f)
    }

    @Test
    fun `an oversized corner radius is clamped to half the height`() {
        // radius 500 on a 30px-tall bar must clamp to 15, leaving the span intact.
        val canvas = render(newBar(cornerRadius = 500f) { enabledDivisions = listOf(0) })

        val segment = canvas.ops.ofColor(PROGRESS).single()
        assertSpan(segment, left = 0f, right = 95f)
    }

    // endregion

    // region divider clamping

    @Test
    fun `a divider wider than a segment is clamped instead of inverting segments`() {
        val canvas = render(
            newBar {
                divisions = 3
                dividerWidth = 5000f
                enabledDivisions = listOf(0, 1, 2)
            },
        )

        // Clamped to width / divisions == 100, so the two end segments are 50px
        // wide and the middle one collapses to nothing and is skipped.
        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(2)
        assertSpan(segments[0], left = 0f, right = 50f)
        assertSpan(segments[1], left = 250f, right = 300f)

        for (op in canvas.ops) {
            assertThat(op.right).isAtLeast(op.left)
        }
    }

    // endregion

    // region padding

    @Test
    fun `the track is inset by the view's padding`() {
        val bar = newBar { setPadding(20, 4, 10, 6) }

        val canvas = render(bar)

        // The canvas is translated by the top-left padding...
        assertThat(canvas.translations).contains(20f to 4f)
        // ...and the track spans the remaining content width, not the view width.
        val track = canvas.ops.ofColor(TRACK).union()
        assertThat(track.left).isWithin(0.01f).of(0f)
        assertThat(track.right).isWithin(0.01f).of((WIDTH - 20 - 10).toFloat())
        assertThat(track.bottom).isWithin(0.01f).of((HEIGHT - 4 - 6).toFloat())
    }

    @Test
    fun `nothing is drawn when padding leaves no content box`() {
        val bar = newBar { setPadding(WIDTH, HEIGHT, WIDTH, HEIGHT) }

        val canvas = render(bar)

        assertThat(canvas.ops).isEmpty()
    }

    // endregion

    // region layout direction

    /**
     * Builds a bar whose layout direction genuinely resolves to right-to-left.
     *
     * Two things are needed, and both are easy to get wrong in a way that leaves
     * the test quietly asserting left-to-right behaviour:
     *
     * 1. `View.resolveLayoutDirection` short-circuits unless the *application*
     *    declares `android:supportsRtl="true"`. That is how the platform gates
     *    RTL for every view, but a library module's synthetic test manifest
     *    declares nothing, so the flag has to be set here by hand.
     * 2. `layoutDirection` is resolved inside the setter, so the flag must
     *    already be in place before the assignment happens.
     *
     * The precondition at the end makes a regression in either of those a hard
     * failure rather than a silently weakened test.
     */
    private fun attachedRtlBar(configure: SegmentedProgressBar.() -> Unit): SegmentedProgressBar {
        val application = ApplicationProvider.getApplicationContext<Context>()
        application.applicationInfo.flags =
            application.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = SegmentedProgressBar(activity).apply {
            divisions = DIVISIONS
            dividerWidth = DIVIDER_WIDTH
            progressBarColor = PROGRESS
            progressBarBackgroundColor = TRACK
            dividerColor = DIVIDER
            cornerRadius = 0f
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            configure()
        }
        activity.setContentView(bar)
        check(bar.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            "layout direction did not resolve to RTL, so this test would have " +
                "asserted left-to-right behaviour instead"
        }
        return bar
    }

    @Test
    fun `under rtl segment zero is drawn at the right hand end`() {
        val bar = attachedRtlBar { enabledDivisions = listOf(0) }

        val canvas = render(bar)

        // The mirror image of the LTR span 0..95.
        assertSpan(canvas.ops.ofColor(PROGRESS).single(), left = 205f, right = 300f)
    }

    @Test
    fun `under rtl the last segment is drawn at the left hand end`() {
        val bar = attachedRtlBar { enabledDivisions = listOf(2) }

        val canvas = render(bar)

        assertSpan(canvas.ops.ofColor(PROGRESS).single(), left = 0f, right = 95f)
    }

    @Test
    fun `under rtl the middle segment is unmoved`() {
        val bar = attachedRtlBar { enabledDivisions = listOf(1) }

        val canvas = render(bar)

        assertSpan(canvas.ops.ofColor(PROGRESS).single(), left = 105f, right = 195f)
    }

    @Test
    fun `under rtl the divider positions are unchanged`() {
        val bar = attachedRtlBar {}

        val canvas = render(bar)

        val dividers = canvas.ops.ofColor(DIVIDER).sortedBy { it.left }
        assertThat(dividers).hasSize(DIVISIONS - 1)
        assertSpan(dividers[0], left = 95f, right = 105f)
        assertSpan(dividers[1], left = 195f, right = 205f)
    }

    @Test
    fun `under rtl the rounded end follows segment zero to the right`() {
        val bar = attachedRtlBar {
            cornerRadius = 8f
            enabledDivisions = listOf(0, 1)
        }

        val canvas = render(bar)

        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(2)
        // The interior segment stays square; segment 0, now at the right end,
        // is the one that carries a rounded corner.
        assertThat(segments[0].rounded).isFalse()
        assertThat(segments[1].rounded).isTrue()
        assertSpan(segments[1], left = 205f, right = 300f)
    }

    @Test
    fun `under rtl divisionAt maps a touch to the segment actually drawn there`() {
        // The reason divisionAt lives on the view: under RTL the leftmost pixel
        // belongs to the LAST segment. A caller doing `x / width * divisions`
        // would toggle the mirror image of what the user touched.
        val bar = attachedRtlBar {}
        render(bar)

        assertThat(bar.divisionAt(10f)).isEqualTo(DIVISIONS - 1)
        assertThat(bar.divisionAt((WIDTH - 10).toFloat())).isEqualTo(0)
    }

    @Test
    fun `under rtl a tapped segment is the one that lights up`() {
        val bar = attachedRtlBar {}
        render(bar)

        // Tap near the right-hand end, which under RTL is segment 0.
        val index = bar.divisionAt(290f)
        bar.toggleDivision(index)
        val canvas = render(bar)

        assertThat(index).isEqualTo(0)
        // And it is drawn at the right-hand end, where the finger was.
        assertSpan(canvas.ops.ofColor(PROGRESS).single(), left = 205f, right = 300f)
    }

    @Test
    fun `under rtl a tap lights the segment the finger was actually on`() {
        // The end-to-end version of the trap: touch the right-hand end, which in
        // RTL is segment 0, and check that the paint lands under the finger
        // rather than at the mirrored position.
        val bar = attachedRtlBar {
            setOnDivisionClickListener { view, index -> view.toggleDivision(index) }
        }
        render(bar)

        val now = android.os.SystemClock.uptimeMillis()
        val touchX = 290f
        bar.dispatchTouchEvent(
            android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, touchX, 5f, 0),
        )
        bar.dispatchTouchEvent(
            android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_UP, touchX, 5f, 0),
        )
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val canvas = render(bar)

        assertThat(bar.enabledDivisions).containsExactly(0)
        assertSpan(canvas.ops.ofColor(PROGRESS).single(), left = 205f, right = 300f)
    }

    @Test
    fun `a non-contiguous set draws exactly the requested segments`() {
        // The library's headline case, asserted at the pixel level: five of ten
        // lit, with gaps, and nothing drawn in the gaps.
        val bar = newBar {
            divisions = 10
            dividerWidth = 0f
            enabledDivisions = listOf(1, 2, 5, 6, 9)
        }

        val canvas = render(bar, width = 1000, height = HEIGHT)

        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(5)
        // 1000 / 10 == 100px per segment.
        val expected = listOf(100f to 200f, 200f to 300f, 500f to 600f, 600f to 700f, 900f to 1000f)
        expected.forEachIndexed { i, (left, right) ->
            assertThat(segments[i].left).isWithin(0.01f).of(left)
            assertThat(segments[i].right).isWithin(0.01f).of(right)
        }
    }

    // endregion

    // region degenerate sizes

    @Test
    fun `a zero width bar draws nothing and does not crash`() {
        val canvas = render(newBar(), width = 0, height = HEIGHT)

        assertThat(canvas.ops).isEmpty()
    }

    @Test
    fun `a one pixel bar with many divisions does not crash`() {
        val canvas = render(
            newBar {
                divisions = 50
                dividerWidth = 4f
                enabledDivisions = (0 until 50).toList()
            },
            width = 1,
            height = 1,
        )

        for (op in canvas.ops) {
            assertThat(op.right).isAtLeast(op.left)
        }
    }

    @Test
    fun `a bar with many divisions draws every divider`() {
        val canvas = render(newBar { divisions = 40; dividerWidth = 1f })

        assertThat(canvas.ops.ofColor(DIVIDER)).hasSize(39)
    }

    // endregion
}

package com.rachitgoyal.segmented

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for how the drop shadow is drawn, at the pixel level.
 *
 * Pixels rather than recorded draw calls, unusually for this suite, because every
 * bug these cover was about how shadows *accumulate*: two of them landing on the
 * same spot, or one landing on top of something already drawn. Neither shows up in
 * a list of draw calls, and both were plainly visible on a screen.
 *
 * That needs [GraphicsMode.Mode.NATIVE]. Robolectric's default graphics are stubs:
 * `getPixel` returns whatever a shadow implementation chose to record, which here
 * was a handful of cell boundaries and no blur at all, so every assertion below
 * passed or failed for reasons unrelated to the code. Native mode is scoped to this
 * class rather than set for the whole module, so the other tests keep running
 * against the faster stubs they were written against.
 *
 * The fixture is deliberately *not* pixel aligned, because anti-aliased edges are
 * where a shadow shows up in places it should not: 158px of content over 4
 * divisions puts every cell boundary on a half pixel.
 * ```
 * padding 20 | cell 0    | cell 1   | cell 2    | cell 3     | padding 20
 *            | 20..59.5  | ..99     | ..138.5   | ..178      |
 *                          (lit)
 * ```
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedProgressBarShadowTest {

    private companion object {
        const val WIDTH = 198
        const val HEIGHT = 61
        const val PADDING = 20
        const val DIVISIONS = 4

        const val PROGRESS = Color.RED
        const val TRACK = Color.GREEN

        /** Centre of the lit cell, and of an unlit one. */
        const val LIT_X = 79
        const val UNLIT_X = 158

        /** Cell boundaries land on 59.5, 99 and 138.5. */
        const val GAP_X = 99
        const val FIRST_GAP_X = 59
        const val FIRST_CELL_X = 40

        /** The row just above the bar: outside it, well inside the blur. */
        const val ABOVE_BAR_Y = PADDING - 2

        /** A row through the middle of the bar itself. */
        const val MID_BAR_Y = HEIGHT / 2

        const val BLUR = 8f

        /**
         * Slack for comparing one part of the blur against another.
         *
         * Two cells at different places along the bar have slightly different
         * neighbours contributing to the blur above them, so their shadows are
         * equal only to within a shade. Anything the tests here care about is off
         * by tens of levels, not by one.
         */
        const val SHADE = 2
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
            cornerRadius = 7f
            progressBarColor = PROGRESS
            progressBarBackgroundColor = TRACK
            setPadding(PADDING, PADDING, PADDING, PADDING)
            enabledDivisions = listOf(1)
            // Opaque black, so that outside the bar a pixel's alpha *is* how much
            // shadow landed on it, with no compositing to reason about.
            shadowColor = Color.BLACK
            shadowRadius = BLUR
            configure()
        }

    private fun render(bar: SegmentedProgressBar): Bitmap =
        renderToRecordingCanvas(bar, WIDTH, HEIGHT).bitmap

    /** How much shadow reached ([x], [y]). Only meaningful outside the bar. */
    private fun shadowAt(bar: SegmentedProgressBar, x: Int, y: Int): Int =
        Color.alpha(render(bar).getPixel(x, y))

    @Test
    fun `a lit segment casts no more shadow than an unlit one`() {
        // Regression: the track cell and the lit segment on top of it each cast
        // their own shadow, and since the two coincide, every lit segment came out
        // twice as dark as its neighbours.
        val bar = newBar()

        val aboveLit = shadowAt(bar, LIT_X, ABOVE_BAR_Y)
        val aboveUnlit = shadowAt(bar, UNLIT_X, ABOVE_BAR_Y)

        assertThat(aboveLit).isGreaterThan(0)
        assertThat(aboveLit).isWithin(SHADE).of(aboveUnlit)
    }

    @Test
    fun `the shadow never darkens the bar itself`() {
        // Locks in the pass order: every shadow is drawn before any fill. Drawn
        // cell by cell instead, as the first implementation did, each cell's
        // shadow landed on the neighbour that had already been painted and drew a
        // dark line down every shared edge.
        assertPaintedPixelsUnchangedByShadow { }
    }

    @Test
    fun `the shadow never darkens a run of adjacent segments`() {
        // The same, at the one place a seam is most obvious: inside a run, where
        // EACH_RUN means there is no divider to hide it.
        assertPaintedPixelsUnchangedByShadow {
            enabledDivisions = listOf(1, 2)
            cornerMode = CornerMode.EACH_RUN
        }
    }

    /**
     * Asserts that turning the shadow on leaves every pixel the bar paints alone.
     *
     * Restricted to pixels the bar covers completely, because a partly covered edge
     * pixel *should* pick up some shadow: whatever is behind it shows through in
     * proportion, and behind it is the blur.
     */
    private fun assertPaintedPixelsUnchangedByShadow(
        configure: SegmentedProgressBar.() -> Unit,
    ) {
        val plain = render(newBar { configure(); shadowRadius = 0f })
        val shadowed = render(newBar(configure))
        var compared = 0

        for (x in PADDING until WIDTH - PADDING) {
            for (y in PADDING until HEIGHT - PADDING) {
                if (Color.alpha(plain.getPixel(x, y)) != 255) continue
                compared++
                assertThat(shadowed.getPixel(x, y)).isEqualTo(plain.getPixel(x, y))
            }
        }
        // Guards the assertion itself: an all-transparent render would pass the
        // loop above without comparing anything.
        assertThat(compared).isGreaterThan(1000)
    }

    @Test
    fun `a translucent segment is not muddied by the shadow beneath it`() {
        // The shadow is a filled shape, so without clipping the silhouette out of
        // it, its own footprint sits at full shadow alpha directly under the
        // segment. Anything you can see through, a fading segment or simply a
        // translucent colour, then looks dirty.
        val translucent: SegmentedProgressBar.() -> Unit = {
            progressBarColor = 0x80FF0000.toInt()
            progressBarBackgroundColor = Color.TRANSPARENT
        }
        val plain = render(newBar { translucent(); shadowRadius = 0f })
        val shadowed = render(newBar(translucent))

        // Well inside the lit cell, clear of its anti-aliased edges.
        for (x in LIT_X - 8..LIT_X + 8) {
            assertThat(shadowed.getPixel(x, MID_BAR_Y)).isEqualTo(plain.getPixel(x, MID_BAR_Y))
        }
    }

    // region gaps

    /**
     * The same bar with a real gap between its cells.
     *
     * The gap is transparent, the default, so anything visible inside it came from
     * the shadow. Cell boundaries sit at 59.5, 99 and 138.5.
     */
    private fun newGappedBar(configure: SegmentedProgressBar.() -> Unit = {}) = newBar {
        dividerWidth = 8f
        dividerColor = Color.TRANSPARENT
        configure()
    }

    @Test
    fun `a gap grows no dark tick above the bar`() {
        // Regression: with one blur per cell, the two neighbouring blurs met inside
        // the gap and added up, which read as a dark tick poking out above and
        // below the bar at every gap. One blur of one shape cannot do that.
        val bar = newGappedBar()
        val image = render(bar)

        val aboveGap = Color.alpha(image.getPixel(GAP_X, ABOVE_BAR_Y))
        val aboveCell = Color.alpha(image.getPixel(LIT_X, ABOVE_BAR_Y))

        assertThat(aboveCell).isGreaterThan(0)
        assertThat(aboveGap).isWithin(SHADE).of(aboveCell)
    }

    @Test
    fun `no shadow is drawn inside a gap`() {
        // Otherwise a narrow gap fills in with blur from both sides and becomes
        // exactly the divider line that leaving it transparent asked to be rid of.
        val plain = render(newGappedBar { shadowRadius = 0f })
        val shadowed = render(newGappedBar())

        for (x in GAP_X - 1..GAP_X + 1) {
            assertThat(shadowed.getPixel(x, MID_BAR_Y)).isEqualTo(plain.getPixel(x, MID_BAR_Y))
        }
    }

    @Test
    fun `a gap between two cells the target skips is not bridged`() {
        // With only the lit cells casting, the shadow has to stop at the edge of
        // the run rather than running on behind the unlit ones.
        val bar = newGappedBar {
            enabledDivisions = listOf(0)
            shadowTarget = ShadowTarget.ON_SEGMENTS
        }
        val image = render(bar)

        // The gap after cell 0 borders an unlit cell, so nothing bridges it.
        assertThat(Color.alpha(image.getPixel(FIRST_GAP_X, ABOVE_BAR_Y)))
            .isLessThan(Color.alpha(image.getPixel(FIRST_CELL_X, ABOVE_BAR_Y)))
    }

    // endregion

    @Test
    fun `on segments shadows only the lit cells`() {
        val bar = newBar { shadowTarget = ShadowTarget.ON_SEGMENTS }

        assertThat(shadowAt(bar, LIT_X, ABOVE_BAR_Y)).isGreaterThan(0)
        assertThat(shadowAt(bar, UNLIT_X, ABOVE_BAR_Y)).isEqualTo(0)
    }

    @Test
    fun `off segments shadows only the unlit cells`() {
        val bar = newBar { shadowTarget = ShadowTarget.OFF_SEGMENTS }

        assertThat(shadowAt(bar, LIT_X, ABOVE_BAR_Y)).isEqualTo(0)
        assertThat(shadowAt(bar, UNLIT_X, ABOVE_BAR_Y)).isGreaterThan(0)
    }

    @Test
    fun `with all, the shadow is the same whichever segments are lit`() {
        // The silhouette is the same either way, so the shadow has to be too.
        val someLit = render(newBar { enabledDivisions = listOf(0, 2) })
        val noneLit = render(newBar { enabledDivisions = emptyList() })

        for (x in 0 until WIDTH) {
            assertThat(Color.alpha(someLit.getPixel(x, ABOVE_BAR_Y)))
                .isWithin(SHADE).of(Color.alpha(noneLit.getPixel(x, ABOVE_BAR_Y)))
        }
    }

    @Test
    fun `an invisible track casts no shadow of its own`() {
        // Nothing is drawn where the off colour is transparent, so nothing there
        // may cast a shadow either.
        val bar = newBar { progressBarBackgroundColor = Color.TRANSPARENT }

        assertThat(shadowAt(bar, UNLIT_X, ABOVE_BAR_Y)).isEqualTo(0)
        assertThat(shadowAt(bar, LIT_X, ABOVE_BAR_Y)).isGreaterThan(0)
    }

    @Test
    fun `the shadow fades outwards from the edge of the bar`() {
        val bar = newBar()

        val strengths = listOf(PADDING - 1, PADDING - 4, PADDING - 7, PADDING - 10)
            .map { y -> shadowAt(bar, LIT_X, y) }

        assertThat(strengths.first()).isGreaterThan(0)
        strengths.zipWithNext { nearer, further ->
            assertThat(nearer).isGreaterThan(further)
        }
        assertThat(shadowAt(bar, LIT_X, 0)).isEqualTo(0)
    }

    @Test
    fun `the offset moves the shadow without moving the bar`() {
        val centred = newBar()
        val pushedDown = newBar { shadowDy = 6f }

        assertThat(shadowAt(pushedDown, LIT_X, ABOVE_BAR_Y))
            .isLessThan(shadowAt(centred, LIT_X, ABOVE_BAR_Y))
        assertThat(shadowAt(pushedDown, LIT_X, HEIGHT - PADDING + 1))
            .isGreaterThan(shadowAt(centred, LIT_X, HEIGHT - PADDING + 1))
        assertThat(render(pushedDown).getPixel(LIT_X, MID_BAR_Y)).isEqualTo(PROGRESS)
    }
}

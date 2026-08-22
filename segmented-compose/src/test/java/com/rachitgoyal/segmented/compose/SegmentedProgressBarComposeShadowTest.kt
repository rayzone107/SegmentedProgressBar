package com.rachitgoyal.segmented.compose

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rachitgoyal.segmented.CornerMode
import com.rachitgoyal.segmented.ShadowTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel-level tests for the Compose renderer's drop shadow.
 *
 * The View has its own copy of these, in `SegmentedProgressBarShadowTest`, and
 * they exist twice on purpose: the two renderers share their geometry but not
 * their shadow implementation, since Compose has no `Paint` shadow layer to hang
 * a blur on. A fix in one is no evidence at all about the other.
 *
 * At xhdpi, 1dp is 2px. The bar is 79dp wide inside a 100dp box, so:
 * ```
 * box   0..200px
 * bar  21..179px, 4 cells of 39.5px
 * cell  1: 60.5..100px   (lit)
 * cell  3: 139.5..179px
 * ```
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedProgressBarComposeShadowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val LIT_X = 80
        const val UNLIT_X = 159

        /** Just above the bar's top edge at 20px: outside it, inside the blur. */
        const val ABOVE_BAR_Y = 18
        const val MID_BAR_Y = 30

        val ON_COLOR = Color(0xFFFF0000)
        val OFF_COLOR = Color(0xFF00FF00)

        const val SHADE = 3
    }

    /**
     * The bar's inputs, as state.
     *
     * State rather than parameters to a render function, because a Compose test
     * rule allows exactly one `setContent`, and half of these tests need to
     * compare two renders of the same bar.
     */
    private var lit by mutableStateOf(setOf(1))
    private var blur by mutableStateOf(4)
    private var target by mutableStateOf(ShadowTarget.ALL)
    private var gap by mutableStateOf(0)
    private var gapColor by mutableStateOf(Color.Transparent)
    private var corners by mutableStateOf(CornerMode.BAR_ENDS)
    private var progress by mutableStateOf(mapOf<Int, Float>())

    private fun render(
        target: ShadowTarget = ShadowTarget.ALL,
        blur: Int = 4,
        lit: Set<Int> = setOf(1),
        gap: Int = 0,
        gapColor: Color = Color.Transparent,
        corners: CornerMode = CornerMode.BAR_ENDS,
        progress: Map<Int, Float> = emptyMap(),
    ): Bitmap {
        this.target = target
        this.blur = blur
        this.lit = lit
        this.gap = gap
        this.gapColor = gapColor
        this.corners = corners
        this.progress = progress
        if (!composed) {
            composed = true
            composeRule.setContent {
                Box(
                    modifier = Modifier.size(100.dp, 30.dp).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    SegmentedProgressBar(
                        divisions = 4,
                        enabledSegments = this@SegmentedProgressBarComposeShadowTest.lit,
                        modifier = Modifier.width(79.dp).height(10.dp),
                        onColor = ON_COLOR,
                        offColor = OFF_COLOR,
                        gap = this@SegmentedProgressBarComposeShadowTest.gap.dp,
                        gapColor = this@SegmentedProgressBarComposeShadowTest.gapColor,
                        cornerRadius = 0.dp,
                        cornerMode = this@SegmentedProgressBarComposeShadowTest.corners,
                        segmentProgress = this@SegmentedProgressBarComposeShadowTest.progress,
                        shadow = this@SegmentedProgressBarComposeShadowTest.blur
                            .takeIf { it > 0 }
                            ?.let {
                                SegmentShadow(
                                    radius = it.dp,
                                    color = Color.Black,
                                    target = this@SegmentedProgressBarComposeShadowTest.target,
                                )
                            },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    private var composed = false

    /** How dark ([x], [y]) is, `0` for the white background and `255` for black. */
    private fun Bitmap.shadowAt(x: Int, y: Int): Int = 255 - android.graphics.Color.red(getPixel(x, y))

    @Test
    fun `a lit segment casts no more shadow than an unlit one`() {
        // Regression: the track cell and the lit segment over it each cast their
        // own shadow, so every lit segment came out twice as dark as its
        // neighbours.
        val image = render()

        val aboveLit = image.shadowAt(LIT_X, ABOVE_BAR_Y)
        val aboveUnlit = image.shadowAt(UNLIT_X, ABOVE_BAR_Y)

        assertThat(aboveLit).isGreaterThan(0)
        assertThat(aboveLit).isWithin(SHADE).of(aboveUnlit)
    }

    @Test
    fun `the shadow never darkens the bar itself`() {
        // Regression: the shadow is a stack of filled shapes, so its footprint sat
        // at full shadow alpha under the fill and showed through every
        // anti-aliased edge as a dark outline.
        val image = render()

        assertThat(image.getPixel(LIT_X, MID_BAR_Y)).isEqualTo(ON_COLOR.toArgb())
        assertThat(image.getPixel(UNLIT_X, MID_BAR_Y)).isEqualTo(OFF_COLOR.toArgb())
    }

    @Test
    fun `no pixel of the bar changes when the shadow is turned on`() {
        val plain = render(blur = 0)
        val shadowed = render()

        // Positive control: the second render really is the shadowed one.
        assertThat(shadowed.shadowAt(LIT_X, ABOVE_BAR_Y))
            .isGreaterThan(plain.shadowAt(LIT_X, ABOVE_BAR_Y))

        // Clear of the bar's own anti-aliased ends, where the blur behind a partly
        // covered pixel legitimately shows through.
        for (x in 25..175) {
            assertThat(shadowed.getPixel(x, MID_BAR_Y)).isEqualTo(plain.getPixel(x, MID_BAR_Y))
        }
    }

    @Test
    fun `a gap grows no dark tick above the bar`() {
        // Regression: with one blur per cell, the two neighbouring blurs met inside
        // the gap and added up, which read as a dark tick poking out above and
        // below the bar at every gap.
        val image = render(gap = 4)

        // Cell boundaries land on 60.5, 100 and 139.5, so 100 is over a gap and
        // LIT_X, at 80, is over the middle of a cell.
        val aboveGap = image.shadowAt(100, ABOVE_BAR_Y)
        val aboveCell = image.shadowAt(LIT_X, ABOVE_BAR_Y)

        assertThat(aboveCell).isGreaterThan(0)
        // An open gap may dip lighter above the slit, but never darker.
        assertThat(aboveGap).isAtMost(aboveCell + SHADE)
    }

    @Test
    fun `an unpainted gap is an opening the shadow spills into`() {
        // The gap borders two separate pieces, so it must read as space between
        // them: their blur falls in, exactly as it falls beside the bar's outer
        // ends. Sealing it left a bright page-coloured slit that read as a
        // painted divider line the moment a shadow surrounded it.
        val plain = render(blur = 0, gap = 4)
        val shadowed = render(gap = 4)

        assertThat(plain.shadowAt(100, MID_BAR_Y)).isEqualTo(0)
        assertThat(shadowed.shadowAt(100, MID_BAR_Y)).isGreaterThan(0)
    }

    @Test
    fun `a gap inside an each_run run stays sealed`() {
        // Within a run the squared corners paint one pill, and blur falling
        // into its slit would draw exactly the divider line EACH_RUN exists to
        // get rid of.
        val plain = render(blur = 0, gap = 4, lit = setOf(1, 2), corners = CornerMode.EACH_RUN)
        val shadowed = render(gap = 4, lit = setOf(1, 2), corners = CornerMode.EACH_RUN)

        for (x in 99..101) {
            assertThat(shadowed.getPixel(x, MID_BAR_Y)).isEqualTo(plain.getPixel(x, MID_BAR_Y))
        }
    }

    @Test
    fun `the gap where a run flows into its partial stays sealed too`() {
        val plain = render(
            blur = 0,
            gap = 4,
            corners = CornerMode.EACH_RUN,
            progress = mapOf(2 to 0.5f),
        )
        val shadowed = render(gap = 4, corners = CornerMode.EACH_RUN, progress = mapOf(2 to 0.5f))

        assertThat(shadowed.getPixel(100, MID_BAR_Y)).isEqualTo(plain.getPixel(100, MID_BAR_Y))
    }

    @Test
    fun `a painted divider seals every gap`() {
        // An opaque divider makes the bar one slab; shadow on the painted line
        // would read as dirt.
        val plain = render(blur = 0, gap = 4, gapColor = Color.White)
        val shadowed = render(gap = 4, gapColor = Color.White)

        for (x in 99..101) {
            assertThat(shadowed.getPixel(x, MID_BAR_Y)).isEqualTo(plain.getPixel(x, MID_BAR_Y))
        }
    }

    @Test
    fun `on segments shadows only the lit cells`() {
        val image = render(target = ShadowTarget.ON_SEGMENTS)

        assertThat(image.shadowAt(LIT_X, ABOVE_BAR_Y)).isGreaterThan(0)
        assertThat(image.shadowAt(UNLIT_X, ABOVE_BAR_Y)).isEqualTo(0)
    }

    @Test
    fun `off segments shadows only the unlit cells`() {
        val image = render(target = ShadowTarget.OFF_SEGMENTS)

        assertThat(image.shadowAt(LIT_X, ABOVE_BAR_Y)).isEqualTo(0)
        assertThat(image.shadowAt(UNLIT_X, ABOVE_BAR_Y)).isGreaterThan(0)
    }

    @Test
    fun `with all, the shadow is the same whichever segments are lit`() {
        val someLit = render(lit = setOf(0, 2))
        val noneLit = render(lit = emptySet())

        // Positive control: the two renders differ where it matters, inside the
        // bar, so an unapplied state change cannot make this pass by accident.
        assertThat(someLit.getPixel(LIT_X - 40, MID_BAR_Y))
            .isNotEqualTo(noneLit.getPixel(LIT_X - 40, MID_BAR_Y))

        for (x in 0 until 200) {
            assertThat(someLit.shadowAt(x, ABOVE_BAR_Y))
                .isWithin(SHADE).of(noneLit.shadowAt(x, ABOVE_BAR_Y))
        }
    }
}

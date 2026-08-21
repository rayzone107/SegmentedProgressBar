package com.rachitgoyal.segmented

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the layout maths.
 *
 * These are deliberately free of Android and Robolectric so the geometry
 * contract can be pinned down exhaustively and cheaply. The invariant that
 * matters most is *tiling*: segments and dividers together must cover the bar
 * exactly, with no gaps and no overlap, for every combination of inputs.
 */
class SegmentGeometryTest {

    private val tolerance = 0.0001f

    // region effectiveDividerWidth

    @Test
    fun `effective divider width is zero when dividers are disabled`() {
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 300f,
                divisions = 5,
                requested = 10f,
                enabled = false,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun `effective divider width is zero for a single division`() {
        // With one division there are no interior boundaries to divide.
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 300f,
                divisions = 1,
                requested = 10f,
                enabled = true,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun `effective divider width is zero for a zero-width bar`() {
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 0f,
                divisions = 5,
                requested = 10f,
                enabled = true,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun `effective divider width is zero when nothing was requested`() {
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 300f,
                divisions = 5,
                requested = 0f,
                enabled = true,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun `effective divider width passes through a reasonable request`() {
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 300f,
                divisions = 5,
                requested = 8f,
                enabled = true,
            ),
        ).isWithin(tolerance).of(8f)
    }

    @Test
    fun `effective divider width clamps a request wider than one segment`() {
        // 300 / 3 == 100, so a 500px divider collapses to 100px rather than
        // driving segment widths negative.
        assertThat(
            SegmentGeometry.effectiveDividerWidth(
                width = 300f,
                divisions = 3,
                requested = 500f,
                enabled = true,
            ),
        ).isWithin(tolerance).of(100f)
    }

    // endregion

    // region boundary

    @Test
    fun `boundaries are evenly spaced and span the full width`() {
        val width = 300f
        val divisions = 3

        assertThat(SegmentGeometry.boundary(width, divisions, 0)).isWithin(tolerance).of(0f)
        assertThat(SegmentGeometry.boundary(width, divisions, 1)).isWithin(tolerance).of(100f)
        assertThat(SegmentGeometry.boundary(width, divisions, 2)).isWithin(tolerance).of(200f)
        assertThat(SegmentGeometry.boundary(width, divisions, divisions)).isWithin(tolerance).of(width)
    }

    // endregion

    // region segments

    @Test
    fun `a single division fills the whole bar`() {
        assertThat(SegmentGeometry.segmentLeft(300f, 1, 0f, 0)).isWithin(tolerance).of(0f)
        assertThat(SegmentGeometry.segmentRight(300f, 1, 0f, 0)).isWithin(tolerance).of(300f)
    }

    @Test
    fun `segments are inset by half a divider on interior edges only`() {
        val width = 300f
        val divisions = 3
        val divider = 10f

        // First segment: flush against the left edge, inset on its right.
        assertThat(SegmentGeometry.segmentLeft(width, divisions, divider, 0)).isWithin(tolerance).of(0f)
        assertThat(SegmentGeometry.segmentRight(width, divisions, divider, 0)).isWithin(tolerance).of(95f)

        // Middle segment: inset on both sides.
        assertThat(SegmentGeometry.segmentLeft(width, divisions, divider, 1)).isWithin(tolerance).of(105f)
        assertThat(SegmentGeometry.segmentRight(width, divisions, divider, 1)).isWithin(tolerance).of(195f)

        // Last segment: inset on its left, flush against the right edge.
        assertThat(SegmentGeometry.segmentLeft(width, divisions, divider, 2)).isWithin(tolerance).of(205f)
        assertThat(SegmentGeometry.segmentRight(width, divisions, divider, 2)).isWithin(tolerance).of(width)
    }

    @Test
    fun `segments tile the bar exactly when there is no divider`() {
        val width = 250f
        val divisions = 7

        for (index in 0 until divisions) {
            val left = SegmentGeometry.segmentLeft(width, divisions, 0f, index)
            val right = SegmentGeometry.segmentRight(width, divisions, 0f, index)
            // Each segment starts exactly where the previous one ended.
            assertThat(left).isWithin(tolerance).of(SegmentGeometry.boundary(width, divisions, index))
            assertThat(right).isWithin(tolerance).of(SegmentGeometry.boundary(width, divisions, index + 1))
        }
    }

    @Test
    fun `a divider clamped to a full segment collapses that segment to zero width`() {
        // The pathological case: divider == segment width. Interior segments
        // degenerate to zero width but must never go negative.
        val width = 300f
        val divisions = 3
        val divider = SegmentGeometry.effectiveDividerWidth(width, divisions, 500f, enabled = true)

        for (index in 0 until divisions) {
            val left = SegmentGeometry.segmentLeft(width, divisions, divider, index)
            val right = SegmentGeometry.segmentRight(width, divisions, divider, index)
            assertThat(right - left).isAtLeast(0f)
        }

        assertThat(SegmentGeometry.segmentRight(width, divisions, divider, 1) -
            SegmentGeometry.segmentLeft(width, divisions, divider, 1))
            .isWithin(tolerance).of(0f)
    }

    // endregion

    // region dividers

    @Test
    fun `dividers are centred on interior boundaries`() {
        val width = 300f
        val divisions = 3
        val divider = 10f

        assertThat(SegmentGeometry.dividerLeft(width, divisions, divider, 1)).isWithin(tolerance).of(95f)
        assertThat(SegmentGeometry.dividerRight(width, divisions, divider, 1)).isWithin(tolerance).of(105f)
        assertThat(SegmentGeometry.dividerLeft(width, divisions, divider, 2)).isWithin(tolerance).of(195f)
        assertThat(SegmentGeometry.dividerRight(width, divisions, divider, 2)).isWithin(tolerance).of(205f)
    }

    @Test
    fun `each divider abuts its neighbouring segments with no gap or overlap`() {
        val width = 480f
        val divisions = 6
        val divider = 7f

        for (index in 1 until divisions) {
            // The segment to the left ends exactly where the divider begins...
            assertThat(SegmentGeometry.segmentRight(width, divisions, divider, index - 1))
                .isWithin(tolerance)
                .of(SegmentGeometry.dividerLeft(width, divisions, divider, index))
            // ...and the segment to the right begins exactly where it ends.
            assertThat(SegmentGeometry.segmentLeft(width, divisions, divider, index))
                .isWithin(tolerance)
                .of(SegmentGeometry.dividerRight(width, divisions, divider, index))
        }
    }

    // endregion

    // region tiling invariant

    @Test
    fun `segments plus dividers always cover the bar exactly`() {
        val widths = listOf(1f, 17f, 100f, 299.5f, 1080f, 4096f)
        val divisionCounts = listOf(1, 2, 3, 5, 7, 30, 100)
        val requestedDividers = listOf(0f, 0.5f, 1f, 6f, 40f, 10_000f)

        for (width in widths) {
            for (divisions in divisionCounts) {
                for (requested in requestedDividers) {
                    val divider = SegmentGeometry.effectiveDividerWidth(
                        width = width,
                        divisions = divisions,
                        requested = requested,
                        enabled = true,
                    )

                    var covered = 0f
                    for (index in 0 until divisions) {
                        val segment = SegmentGeometry.segmentRight(width, divisions, divider, index) -
                            SegmentGeometry.segmentLeft(width, divisions, divider, index)
                        // Never inverted, for any input, see segmentRight's
                        // lower bound.
                        assertThat(segment).isAtLeast(0f)
                        covered += segment
                    }
                    for (index in 1 until divisions) {
                        covered += SegmentGeometry.dividerRight(width, divisions, divider, index) -
                            SegmentGeometry.dividerLeft(width, divisions, divider, index)
                    }

                    assertThat(covered)
                        .isWithin(width * 0.0005f + tolerance)
                        .of(width)
                }
            }
        }
    }

    @Test
    fun `segments never escape the bounds of the bar`() {
        val width = 333f
        val divisions = 9
        val divider = SegmentGeometry.effectiveDividerWidth(width, divisions, 12f, enabled = true)

        for (index in 0 until divisions) {
            assertThat(SegmentGeometry.segmentLeft(width, divisions, divider, index)).isAtLeast(0f)
            assertThat(SegmentGeometry.segmentRight(width, divisions, divider, index)).isAtMost(width)
        }
    }

    // endregion

    // region mirror

    @Test
    fun `mirroring reflects about the centre of the bar`() {
        assertThat(SegmentGeometry.mirror(300f, 0f)).isWithin(tolerance).of(300f)
        assertThat(SegmentGeometry.mirror(300f, 300f)).isWithin(tolerance).of(0f)
        assertThat(SegmentGeometry.mirror(300f, 150f)).isWithin(tolerance).of(150f)
        assertThat(SegmentGeometry.mirror(300f, 100f)).isWithin(tolerance).of(200f)
    }

    @Test
    fun `mirroring a span preserves its width`() {
        val width = 300f
        val divisions = 4
        val divider = 8f

        for (index in 0 until divisions) {
            val left = SegmentGeometry.segmentLeft(width, divisions, divider, index)
            val right = SegmentGeometry.segmentRight(width, divisions, divider, index)
            val mirroredLeft = SegmentGeometry.mirror(width, right)
            val mirroredRight = SegmentGeometry.mirror(width, left)

            assertThat(mirroredRight - mirroredLeft).isWithin(tolerance).of(right - left)
            assertThat(mirroredLeft).isAtLeast(-tolerance)
            assertThat(mirroredRight).isAtMost(width + tolerance)
        }
    }

    @Test
    fun `mirroring segment zero puts it at the far end of the bar`() {
        val width = 300f
        val divisions = 3

        val mirroredRight = SegmentGeometry.mirror(
            width,
            SegmentGeometry.segmentLeft(width, divisions, 0f, 0),
        )
        assertThat(mirroredRight).isWithin(tolerance).of(width)
    }

    @Test
    fun `the set of divider positions is symmetric so rtl needs no mirroring`() {
        val width = 300f
        val divisions = 5
        val divider = 6f

        val positions = (1 until divisions).map {
            SegmentGeometry.dividerLeft(width, divisions, divider, it)
        }
        val mirroredPositions = (1 until divisions).map {
            SegmentGeometry.mirror(width, SegmentGeometry.dividerRight(width, divisions, divider, it))
        }.sorted()

        positions.forEachIndexed { i, position ->
            assertThat(mirroredPositions[i]).isWithin(tolerance).of(position)
        }
    }

    // endregion

    // region clampCornerRadius

    @Test
    fun `corner radius is clamped to half the smaller dimension`() {
        assertThat(SegmentGeometry.clampCornerRadius(100f, 300f, 20f)).isWithin(tolerance).of(10f)
        assertThat(SegmentGeometry.clampCornerRadius(100f, 12f, 300f)).isWithin(tolerance).of(6f)
    }

    @Test
    fun `a reasonable corner radius passes through untouched`() {
        assertThat(SegmentGeometry.clampCornerRadius(4f, 300f, 20f)).isWithin(tolerance).of(4f)
    }

    @Test
    fun `a negative corner radius clamps to zero`() {
        assertThat(SegmentGeometry.clampCornerRadius(-8f, 300f, 20f)).isEqualTo(0f)
    }

    // endregion
}

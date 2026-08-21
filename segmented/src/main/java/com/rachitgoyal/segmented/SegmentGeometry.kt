package com.rachitgoyal.segmented

/**
 * Pure geometry for a segmented bar.
 *
 * Public because the Compose artifact draws from exactly the same maths as the
 * View does; sharing this is what keeps the two implementations pixel-identical
 * and covered by one set of tests.
 *
 * Every function here is a total function of its arguments with no Android
 * dependencies and no allocation, which keeps [SegmentedProgressBar.onDraw]
 * allocation-free and lets the layout maths be unit-tested without a device or
 * a Robolectric environment.
 *
 * ### The model
 *
 * A bar of `width` is divided into `divisions` equal *cells*, so cell `i`
 * spans `[width * i / divisions, width * (i + 1) / divisions]`. Interior cell
 * boundaries (there are `divisions - 1` of them) each carry a divider of
 * `dividerWidth`, **centred** on the boundary. A segment is therefore drawn
 * inset by half a divider on every side that touches an interior boundary, so
 * segments and dividers never overlap.
 *
 * ```
 * divisions = 3, dividerWidth = d
 *
 * 0        w/3        2w/3        w
 * |---------|----------|----------|
 * [ seg 0  ]d[ seg 1  ]d[  seg 2  ]
 *          ^            ^
 *          dividers, centred on the boundary
 * ```
 */
public object SegmentGeometry {

    /**
     * The divider width that will actually be used for drawing.
     *
     * Returns `0` when dividers cannot apply at all (disabled, a single
     * division, or a zero-width bar), and otherwise clamps [requested] to
     * `width / divisions` so that an oversized divider can never produce
     * negative-width segments.
     */
    public fun effectiveDividerWidth(
        width: Float,
        divisions: Int,
        requested: Float,
        enabled: Boolean,
    ): Float {
        if (!enabled || divisions <= 1 || width <= 0f || requested <= 0f) return 0f
        return requested.coerceAtMost(width / divisions)
    }

    /** The un-mirrored position of cell boundary [index], for `index` in `0..divisions`. */
    public fun boundary(width: Float, divisions: Int, index: Int): Float =
        width * index / divisions

    /**
     * Left edge of the drawable area of segment [index] in a left-to-right bar.
     *
     * [dividerWidth] must already have been passed through
     * [effectiveDividerWidth].
     */
    public fun segmentLeft(width: Float, divisions: Int, dividerWidth: Float, index: Int): Float {
        val boundary = boundary(width, divisions, index)
        return if (index == 0) boundary else boundary + dividerWidth / 2f
    }

    /**
     * Right edge of the drawable area of segment [index] in a left-to-right bar.
     *
     * [dividerWidth] must already have been passed through
     * [effectiveDividerWidth].
     *
     * Never returns less than [segmentLeft]. When the divider has been clamped
     * to exactly one segment's width, computing the two edges from separate
     * boundaries can round to an inverted span of a fraction of a pixel; the
     * lower bound collapses that to an empty span instead, so callers never have
     * to reason about negative widths.
     */
    public fun segmentRight(width: Float, divisions: Int, dividerWidth: Float, index: Int): Float {
        val boundary = boundary(width, divisions, index + 1)
        val right = if (index == divisions - 1) boundary else boundary - dividerWidth / 2f
        return maxOf(right, segmentLeft(width, divisions, dividerWidth, index))
    }

    /**
     * Left edge of the divider sitting on interior boundary [index], for
     * `index` in `1..divisions - 1`.
     */
    public fun dividerLeft(width: Float, divisions: Int, dividerWidth: Float, index: Int): Float =
        boundary(width, divisions, index) - dividerWidth / 2f

    /**
     * Right edge of the divider sitting on interior boundary [index], for
     * `index` in `1..divisions - 1`.
     */
    public fun dividerRight(width: Float, divisions: Int, dividerWidth: Float, index: Int): Float =
        boundary(width, divisions, index) + dividerWidth / 2f

    /**
     * Reflects [x] about the centre of a bar of [width].
     *
     * Applied to both edges of a span (and swapping them) this converts a
     * left-to-right span into its right-to-left equivalent, which is how
     * `layoutDirection=rtl` support is implemented: segment 0 renders at the
     * right-hand end of the bar.
     */
    public fun mirror(width: Float, x: Float): Float = width - x

    /**
     * The largest corner radius that can be applied to a [width] x [height]
     * box without the arcs overlapping.
     */
    public fun clampCornerRadius(radius: Float, width: Float, height: Float): Float =
        radius.coerceIn(0f, minOf(width, height) / 2f)
}

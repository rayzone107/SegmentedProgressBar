package com.rachitgoyal.segmented

import android.content.Context
import android.graphics.Color
import android.os.Looper
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
 * Tests for the visual variants added in 2.0.0: height bands, corner modes, drop
 * shadow and segment animation.
 *
 * The fixture is a 300 x 40 bar with 4 divisions and no divider, so each cell is
 * exactly 75px wide and the arithmetic in the assertions stays checkable by eye:
 * ```
 * segment 0:   0 ..  75
 * segment 1:  75 .. 150
 * segment 2: 150 .. 225
 * segment 3: 225 .. 300
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarVariantsTest {

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 40
        const val DIVISIONS = 4

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

    private fun advance(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    // region 1. height difference

    @Test
    fun `by default the track and segments fill the full height`() {
        val bar = newBar { enabledDivisions = listOf(0) }

        val canvas = render(bar)

        val track = canvas.ops.ofColor(TRACK).union()
        assertThat(track.top).isWithin(0.01f).of(0f)
        assertThat(track.bottom).isWithin(0.01f).of(HEIGHT.toFloat())

        val segment = canvas.ops.ofColor(PROGRESS).single()
        assertThat(segment.top).isWithin(0.01f).of(0f)
        assertThat(segment.bottom).isWithin(0.01f).of(HEIGHT.toFloat())
    }

    @Test
    fun `a thinner track is centred vertically`() {
        val bar = newBar { inactiveHeightRatio = 0.5f }

        val track = render(bar).ops.ofColor(TRACK).union()

        // Half of 40 is 20, centred leaves 10px above and below.
        assertThat(track.top).isWithin(0.01f).of(10f)
        assertThat(track.bottom).isWithin(0.01f).of(30f)
        assertThat(track.height).isWithin(0.01f).of(20f)
    }

    @Test
    fun `lit segments can stand proud of a thinner track`() {
        // The headline use of this: a slim rail with chunky completed segments.
        val bar = newBar {
            inactiveHeightRatio = 0.4f
            activeHeightRatio = 1f
            enabledDivisions = listOf(1)
        }

        val canvas = render(bar)

        val track = canvas.ops.ofColor(TRACK).union()
        val segment = canvas.ops.ofColor(PROGRESS).single()

        assertThat(track.height).isWithin(0.01f).of(16f)
        assertThat(segment.height).isWithin(0.01f).of(40f)
        // Both centred on the same axis.
        assertThat(track.top + track.bottom).isWithin(0.01f).of(segment.top + segment.bottom)
    }

    @Test
    fun `lit segments can also be thinner than the track`() {
        val bar = newBar {
            inactiveHeightRatio = 1f
            activeHeightRatio = 0.5f
            enabledDivisions = listOf(1)
        }

        val canvas = render(bar)

        assertThat(canvas.ops.ofColor(TRACK).union().height).isWithin(0.01f).of(40f)
        assertThat(canvas.ops.ofColor(PROGRESS).single().height).isWithin(0.01f).of(20f)
    }

    @Test
    fun `dividers span the taller of the two bands`() {
        val bar = newBar {
            dividerWidth = 4f
            dividerColor = Color.BLUE
            inactiveHeightRatio = 0.25f
            activeHeightRatio = 1f
        }

        val dividers = render(bar).ops.ofColor(Color.BLUE)

        assertThat(dividers).hasSize(DIVISIONS - 1)
        // Otherwise a tall lit segment would be visually unseparated from its
        // neighbour wherever the thin track's divider stopped short.
        dividers.forEach {
            assertThat(it.top).isWithin(0.01f).of(0f)
            assertThat(it.bottom).isWithin(0.01f).of(HEIGHT.toFloat())
        }
    }

    @Test
    fun `height ratios outside zero to one are rejected`() {
        val bar = newBar()

        for (invalid in listOf(-0.1f, 1.1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThat(runCatching { bar.activeHeightRatio = invalid }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(runCatching { bar.inactiveHeightRatio = invalid }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(bar.activeHeightRatio).isEqualTo(1f)
        assertThat(bar.inactiveHeightRatio).isEqualTo(1f)
    }

    @Test
    fun `a zero height ratio draws nothing for that band`() {
        val bar = newBar {
            inactiveHeightRatio = 0f
            enabledDivisions = listOf(0)
        }

        val canvas = render(bar)

        assertThat(canvas.ops.ofColor(TRACK)).isEmpty()
        assertThat(canvas.ops.ofColor(PROGRESS)).hasSize(1)
    }

    // endregion

    // region 2 & 3. corner modes

    @Test
    fun `bar_ends is the default and rounds only the outer ends`() {
        val bar = newBar {
            cornerRadius = 8f
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        assertThat(bar.cornerMode).isEqualTo(CornerMode.BAR_ENDS)
        val segments = render(bar).ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments.map { it.rounded }).containsExactly(true, false, false, true).inOrder()
    }

    @Test
    fun `each_segment rounds every lit segment`() {
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_SEGMENT
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        val segments = render(bar).ops.ofColor(PROGRESS)

        assertThat(segments).hasSize(4)
        assertThat(segments.map { it.rounded }).doesNotContain(false)
    }

    @Test
    fun `each_segment rounds every track cell, not just the ends`() {
        // Otherwise rounded segments would sit on a squared-off strip.
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_SEGMENT
        }

        val track = render(bar).ops.ofColor(TRACK)

        assertThat(track).hasSize(DIVISIONS)
        assertThat(track.map { it.rounded }).doesNotContain(false)
        val sorted = track.sortedBy { it.left }
        assertThat(sorted[0].left).isWithin(0.01f).of(0f)
        assertThat(sorted[0].right).isWithin(0.01f).of(75f)
        assertThat(sorted[3].right).isWithin(0.01f).of(300f)
    }

    @Test
    fun `each_run rounds only the outer ends of a contiguous run`() {
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_RUN
            // A run of two, then a gap, then a lone segment.
            enabledDivisions = listOf(0, 1, 3)
        }

        val segments = render(bar).ops.ofColor(PROGRESS).sortedBy { it.left }

        assertThat(segments).hasSize(3)
        // Segment 0 opens the run: rounded on the left, square where it meets 1.
        assertThat(segments[0].rounded).isTrue()
        // Segment 1 closes the run: square on the left, rounded on the right.
        assertThat(segments[1].rounded).isTrue()
        // Segment 3 is isolated, so both its ends are rounded.
        assertThat(segments[2].rounded).isTrue()
    }

    @Test
    fun `each_run leaves interior edges of a run square`() {
        // Asserted on geometry rather than the rounded flag: a fully-square
        // middle segment falls back to drawRect, which is the observable
        // difference between "in a run" and "isolated".
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_RUN
            enabledDivisions = listOf(0, 1, 2)
        }

        val segments = render(bar).ops.ofColor(PROGRESS).sortedBy { it.left }

        assertThat(segments).hasSize(3)
        assertThat(segments[0].rounded).isTrue() // opens the run
        assertThat(segments[1].rounded).isFalse() // fully interior, so square
        assertThat(segments[2].rounded).isTrue() // closes the run
    }

    @Test
    fun `each_run treats every isolated segment as its own pill`() {
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_RUN
            enabledDivisions = listOf(0, 2)
        }

        val segments = render(bar).ops.ofColor(PROGRESS)

        assertThat(segments).hasSize(2)
        assertThat(segments.map { it.rounded }).doesNotContain(false)
    }

    @Test
    fun `each_run rounds the track under a segment to match it`() {
        // Regression: the track cell kept its square interior corner while the
        // segment above it was rounded at the end of a run, so the square corner
        // showed through as an off-coloured wedge under the rounded edge.
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_RUN
            // A run of two at the start, then two off.
            enabledDivisions = listOf(0, 1)
        }

        val canvas = render(bar)

        val track = canvas.ops.ofColor(TRACK).sortedBy { it.left }
        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }

        assertThat(track).hasSize(DIVISIONS)
        assertThat(segments).hasSize(2)

        // Cell 1 closes the run, so its segment is rounded on the right; the
        // track beneath it has to be rounded too.
        assertThat(segments[1].rounded).isTrue()
        assertThat(track[1].rounded).isTrue()

        // Cell 2 is off and interior, so it stays square, as the rail should.
        assertThat(track[2].rounded).isFalse()
    }

    @Test
    fun `each_run leaves the track as a plain rail where no segment covers it`() {
        val bar = newBar {
            cornerRadius = 8f
            cornerMode = CornerMode.EACH_RUN
            enabledDivisions = emptyList()
        }

        val track = render(bar).ops.ofColor(TRACK).sortedBy { it.left }

        // Only the two ends of the bar are rounded.
        assertThat(track.map { it.rounded }).containsExactly(true, false, false, true).inOrder()
    }

    @Test
    fun `changing the corner mode requests a repaint`() {
        val bar = newBar()
        shadowOf(bar).clearWasInvalidated()

        bar.cornerMode = CornerMode.EACH_SEGMENT

        assertThat(shadowOf(bar).wasInvalidated()).isTrue()
    }

    // endregion

    // region 4. drop shadow

    @Test
    fun `a shadow is off by default`() {
        val bar = newBar()

        assertThat(bar.shadowRadius).isEqualTo(0f)
        assertThat(bar.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    }

    @Test
    fun `enabling a shadow no longer forces a software layer`() {
        // Regression, inverted from 2.0.0: the shadow used to switch the whole
        // view to LAYER_TYPE_SOFTWARE, which re-rasterised every frame of a
        // shimmer in software. It renders through a cached bitmap now, so the
        // view stays on the hardware pipeline.
        val bar = newBar { shadowRadius = 6f }

        assertThat(bar.layerType).isEqualTo(View.LAYER_TYPE_NONE)
    }

    @Test
    fun `shadow properties round-trip`() {
        val bar = newBar {
            shadowRadius = 5f
            shadowDx = 2f
            shadowDy = 3f
            shadowColor = Color.BLUE
        }

        assertThat(bar.shadowRadius).isEqualTo(5f)
        assertThat(bar.shadowDx).isEqualTo(2f)
        assertThat(bar.shadowDy).isEqualTo(3f)
        assertThat(bar.shadowColor).isEqualTo(Color.BLUE)
    }

    @Test
    fun `a negative shadow radius is rejected`() {
        val bar = newBar()

        val error = runCatching { bar.shadowRadius = -1f }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(error).hasMessageThat().contains("shadowRadius")
    }

    @Test
    fun `a non-finite shadow offset is rejected`() {
        val bar = newBar()

        assertThat(runCatching { bar.shadowDx = Float.NaN }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { bar.shadowDy = Float.POSITIVE_INFINITY }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a shadow does not change the bar's geometry at all`() {
        // Regression: the bar used to inset itself to make room for the blur,
        // which meant enabling a shadow, or changing its blur or offset, visibly
        // shrank and shifted the bar.
        val plain = newBar { enabledDivisions = listOf(0, 1, 2, 3) }
        val shadowed = newBar {
            shadowRadius = 8f
            shadowDy = 5f
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        val a = render(plain).ops.ofColor(PROGRESS).sortedBy { it.left }
        val b = render(shadowed).ops.ofColor(PROGRESS).sortedBy { it.left }

        assertThat(b).hasSize(a.size)
        a.indices.forEach { i ->
            assertThat(b[i].left).isWithin(0.01f).of(a[i].left)
            assertThat(b[i].right).isWithin(0.01f).of(a[i].right)
            assertThat(b[i].top).isWithin(0.01f).of(a[i].top)
            assertThat(b[i].bottom).isWithin(0.01f).of(a[i].bottom)
        }
    }

    @Test
    fun `changing the blur or offset never moves the bar`() {
        val bar = newBar { enabledDivisions = listOf(1) }
        val baseline = render(bar).ops.ofColor(PROGRESS).single()

        bar.shadowRadius = 12f
        bar.shadowDx = 6f
        bar.shadowDy = 9f
        val shifted = render(bar).ops.ofColor(PROGRESS).single()

        assertThat(shifted.left).isWithin(0.01f).of(baseline.left)
        assertThat(shifted.right).isWithin(0.01f).of(baseline.right)
        assertThat(shifted.top).isWithin(0.01f).of(baseline.top)
        assertThat(shifted.bottom).isWithin(0.01f).of(baseline.bottom)
    }

    @Test
    fun `a shadow does not change the measured size`() {
        val plain = newBar()
        val shadowed = newBar { shadowRadius = 8f; shadowDy = 4f }

        val spec = View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST)
        plain.measure(spec, spec)
        shadowed.measure(spec, spec)

        assertThat(shadowed.measuredWidth).isEqualTo(plain.measuredWidth)
        assertThat(shadowed.measuredHeight).isEqualTo(plain.measuredHeight)
    }

    @Test
    fun `by default both on and off segments cast the shadow`() {
        val bar = newBar()

        assertThat(bar.shadowTarget).isEqualTo(ShadowTarget.ALL)
    }

    @Test
    fun `the shadow target round-trips through every value`() {
        val bar = newBar { shadowRadius = 6f }
        assertThat(bar.shadowTarget).isEqualTo(ShadowTarget.ALL)

        bar.shadowTarget = ShadowTarget.ON_SEGMENTS
        assertThat(bar.shadowTarget).isEqualTo(ShadowTarget.ON_SEGMENTS)

        bar.shadowTarget = ShadowTarget.OFF_SEGMENTS
        assertThat(bar.shadowTarget).isEqualTo(ShadowTarget.OFF_SEGMENTS)
    }

    @Test
    fun `changing the shadow target requests a repaint`() {
        val bar = newBar { shadowRadius = 6f }
        shadowOf(bar).clearWasInvalidated()

        bar.shadowTarget = ShadowTarget.ON_SEGMENTS

        assertThat(shadowOf(bar).wasInvalidated()).isTrue()
    }

    @Test
    fun `the view supplies a rounded outline matching the track for elevation`() {
        val bar = newBar {
            cornerRadius = 9f
            inactiveHeightRatio = 0.5f
        }
        render(bar)

        val outline = android.graphics.Outline()
        bar.outlineProvider.getOutline(bar, outline)

        assertThat(outline.isEmpty).isFalse()
        val rect = android.graphics.Rect()
        assertThat(outline.getRect(rect)).isTrue()
        // The 20px-tall centred track band, not the full 40px view.
        assertThat(rect.top).isEqualTo(10)
        assertThat(rect.bottom).isEqualTo(30)
        assertThat(rect.left).isEqualTo(0)
        assertThat(rect.right).isEqualTo(WIDTH)
        assertThat(outline.radius).isWithin(0.01f).of(9f)
    }

    // endregion

    // region 5. animation

    @Test
    fun `animation is off by default`() {
        val bar = newBar()

        assertThat(bar.segmentAnimation).isEqualTo(SegmentAnimation.NONE)
        assertThat(bar.animationDurationMs)
            .isEqualTo(SegmentedProgressBar.DEFAULT_ANIMATION_DURATION_MS)
    }

    @Test
    fun `with animation off a toggled segment is drawn fully at once`() {
        val bar = newBar()
        render(bar)

        bar.toggleDivision(1)
        val segment = render(bar).ops.ofColor(PROGRESS).single()

        assertThat(segment.width).isWithin(0.01f).of(75f)
        assertThat(segment.alphaFraction).isWithin(0.01f).of(1f)
    }

    @Test
    fun `the initial state does not animate in`() {
        // Otherwise every screen visibly assembles itself on first appearance.
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            enabledDivisions = listOf(0, 1)
        }

        val segments = render(bar).ops.ofRgb(PROGRESS)

        assertThat(segments).hasSize(2)
        segments.forEach { assertThat(it.alphaFraction).isWithin(0.01f).of(1f) }
    }

    @Test
    fun `fade ramps a segment's alpha up over the duration`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 200L
        }
        render(bar) // lay out first, so the change afterwards animates

        bar.toggleDivision(1)

        // A segment at zero opacity is not drawn at all, which is why this
        // samples just after the start rather than exactly at it.
        advance(20)
        val nearStart = render(bar).ops.ofRgb(PROGRESS).single()
        assertThat(nearStart.alphaFraction).isLessThan(0.3f)

        advance(80)
        val midway = render(bar).ops.ofRgb(PROGRESS).single()
        assertThat(midway.alphaFraction).isGreaterThan(0.2f)
        assertThat(midway.alphaFraction).isLessThan(0.9f)
        // Fading changes opacity, not size.
        assertThat(midway.width).isWithin(0.01f).of(75f)

        advance(150)
        val settled = render(bar).ops.ofRgb(PROGRESS).single()
        assertThat(settled.alphaFraction).isWithin(0.01f).of(1f)
    }

    @Test
    fun `grow extends a segment from its leading edge over the duration`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.GROW
            animationDurationMs = 200L
        }
        render(bar)

        bar.toggleDivision(1)

        advance(100)
        val midway = render(bar).ops.ofColor(PROGRESS).single()
        // Segment 1 spans 75..150; it grows rightwards from 75.
        assertThat(midway.left).isWithin(0.01f).of(75f)
        assertThat(midway.width).isGreaterThan(0f)
        assertThat(midway.width).isLessThan(75f)
        // Growing changes size, not opacity.
        assertThat(midway.alphaFraction).isWithin(0.01f).of(1f)

        advance(150)
        val settled = render(bar).ops.ofColor(PROGRESS).single()
        assertThat(settled.left).isWithin(0.01f).of(75f)
        assertThat(settled.right).isWithin(0.01f).of(150f)
    }

    @Test
    fun `a segment animates back out when cleared`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 200L
            enabledDivisions = listOf(1)
        }
        render(bar)

        bar.disableDivision(1)

        // Still drawn while it fades away.
        advance(100)
        val fading = render(bar).ops.ofRgb(PROGRESS)
        assertThat(fading).hasSize(1)
        assertThat(fading.single().alphaFraction).isLessThan(0.9f)

        advance(150)
        assertThat(render(bar).ops.ofRgb(PROGRESS)).isEmpty()
    }

    @Test
    fun `an interrupted transition continues from where it had got to`() {
        // Toggling twice in quick succession must not snap back to the start.
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 200L
        }
        render(bar)

        bar.toggleDivision(1)
        advance(100)
        val partway = render(bar).ops.ofRgb(PROGRESS).single().alphaFraction

        // Reverse direction mid-flight.
        bar.toggleDivision(1)
        val justAfterReversal = render(bar).ops.ofRgb(PROGRESS).single().alphaFraction

        assertThat(partway).isGreaterThan(0.2f)
        // It resumes from roughly where it was rather than jumping to full.
        assertThat(justAfterReversal).isWithin(0.05f).of(partway)
    }

    @Test
    fun `a zero duration disables animation`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 0L
        }
        render(bar)

        bar.toggleDivision(1)

        assertThat(render(bar).ops.ofRgb(PROGRESS).single().alphaFraction)
            .isWithin(0.01f).of(1f)
    }

    @Test
    fun `switching animation off mid-flight snaps to the final state`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 200L
        }
        render(bar)
        bar.toggleDivision(1)
        advance(50)

        bar.segmentAnimation = SegmentAnimation.NONE

        assertThat(render(bar).ops.ofRgb(PROGRESS).single().alphaFraction)
            .isWithin(0.01f).of(1f)
    }

    @Test
    fun `a negative animation duration is rejected`() {
        val bar = newBar()

        val error = runCatching { bar.animationDurationMs = -1L }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `animating one segment leaves the others untouched`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 200L
            enabledDivisions = listOf(0)
        }
        render(bar)

        bar.toggleDivision(2)
        advance(100)

        val segments = render(bar).ops.ofRgb(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(2)
        // The already-lit segment is still fully opaque.
        assertThat(segments[0].alphaFraction).isWithin(0.01f).of(1f)
        assertThat(segments[1].alphaFraction).isLessThan(0.9f)
    }

    @Test
    fun `growing under rtl extends from the right hand edge`() {
        val bar = newBar {
            segmentAnimation = SegmentAnimation.GROW
            animationDurationMs = 200L
        }
        // Force RTL the same way the drawing tests do.
        context.applicationInfo.flags =
            context.applicationInfo.flags or android.content.pm.ApplicationInfo.FLAG_SUPPORTS_RTL
        bar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        render(bar)
        assertThat(bar.layoutDirection).isEqualTo(View.LAYOUT_DIRECTION_RTL)

        bar.toggleDivision(0)
        advance(100)

        val segment = render(bar).ops.ofColor(PROGRESS).single()
        // Segment 0 sits at 225..300 under RTL and grows leftwards from 300.
        assertThat(segment.right).isWithin(0.01f).of(300f)
        assertThat(segment.width).isLessThan(75f)
        assertThat(segment.width).isGreaterThan(0f)
    }

    // endregion

    // region gaps

    @Test
    fun `a transparent divider leaves a real gap rather than a painted line`() {
        val bar = newBar {
            dividerWidth = 12f
            dividerColor = Color.TRANSPARENT
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        val canvas = render(bar)

        // Nothing is painted in the gap at all, not a transparent rectangle, and
        // not a strip of track showing through.
        assertThat(canvas.ops.ofRgb(Color.TRANSPARENT).filter { it.color == Color.TRANSPARENT })
            .isEmpty()
        val segments = canvas.ops.ofColor(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(4)
        // 6px of clear space either side of each interior boundary.
        assertThat(segments[0].right).isWithin(0.01f).of(69f)
        assertThat(segments[1].left).isWithin(0.01f).of(81f)
        assertThat(segments[1].left - segments[0].right).isWithin(0.01f).of(12f)
    }

    @Test
    fun `the track is absent in the gap too`() {
        val bar = newBar {
            dividerWidth = 12f
            dividerColor = Color.TRANSPARENT
        }

        val cells = render(bar).ops.ofColor(TRACK).sortedBy { it.left }

        assertThat(cells).hasSize(DIVISIONS)
        assertThat(cells[0].right).isWithin(0.01f).of(69f)
        assertThat(cells[1].left).isWithin(0.01f).of(81f)
    }

    @Test
    fun `a zero gap makes segments contiguous`() {
        val bar = newBar {
            dividerWidth = 0f
            enabledDivisions = listOf(0, 1)
        }

        val segments = render(bar).ops.ofColor(PROGRESS).sortedBy { it.left }

        assertThat(segments[0].right).isWithin(0.01f).of(segments[1].left)
    }

    @Test
    fun `an opaque divider is still painted over the gap`() {
        // The legacy look: a visible line between cells.
        val bar = newBar {
            dividerWidth = 8f
            dividerColor = Color.BLUE
        }

        val dividers = render(bar).ops.ofColor(Color.BLUE)

        assertThat(dividers).hasSize(DIVISIONS - 1)
    }

    // endregion

    // region size constraints

    @Test
    fun `maxWidth caps a match_parent bar`() {
        val bar = newBar { maxWidth = 200 }

        bar.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )

        assertThat(bar.measuredWidth).isEqualTo(200)
    }

    @Test
    fun `maxHeight caps the measured height`() {
        val bar = newBar { maxHeight = 12 }

        bar.measure(
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )

        assertThat(bar.measuredHeight).isEqualTo(12)
    }

    @Test
    fun `an unset maximum leaves measurement alone`() {
        val bar = newBar()

        assertThat(bar.maxWidth).isEqualTo(SegmentedProgressBar.NO_MAX_SIZE)
        assertThat(bar.maxHeight).isEqualTo(SegmentedProgressBar.NO_MAX_SIZE)

        bar.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
        )

        assertThat(bar.measuredWidth).isEqualTo(1000)
        assertThat(bar.measuredHeight).isEqualTo(400)
    }

    @Test
    fun `a minimum wins over a smaller maximum`() {
        // The framework treats a minimum as the harder constraint; matching that
        // avoids a view that measures smaller than it declared it can be.
        val bar = newBar {
            minimumWidth = 250
            maxWidth = 100
        }

        bar.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY),
        )

        assertThat(bar.measuredWidth).isEqualTo(250)
    }

    @Test
    fun `a negative maximum is rejected but the sentinel is accepted`() {
        val bar = newBar()

        assertThat(runCatching { bar.maxWidth = -5 }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
        bar.maxWidth = SegmentedProgressBar.NO_MAX_SIZE
        assertThat(bar.maxWidth).isEqualTo(SegmentedProgressBar.NO_MAX_SIZE)
    }

    // endregion

    // region entry animation

    /** Lays the bar out, which is what triggers the entry animation. */
    private fun layOut(bar: SegmentedProgressBar) = render(bar)

    @Test
    fun `no entry animation means the bar arrives fully formed`() {
        val bar = newBar { enabledDivisions = listOf(0, 1) }

        val segments = layOut(bar).ops.ofRgb(PROGRESS)

        assertThat(segments).hasSize(2)
        segments.forEach { assertThat(it.alphaFraction).isWithin(0.01f).of(1f) }
    }

    @Test
    fun `a fade entry animates the initial state in`() {
        // Note this happens even though segmentAnimation is NONE: entry and
        // change animations are independent opt-ins.
        val bar = newBar {
            entryAnimation = EntryAnimation.FADE
            animationDurationMs = 200L
            enabledDivisions = listOf(0, 1)
        }

        layOut(bar)
        advance(20)
        val early = render(bar).ops.ofRgb(PROGRESS)
        assertThat(early).hasSize(2)
        early.forEach { assertThat(it.alphaFraction).isLessThan(0.3f) }

        advance(250)
        render(bar).ops.ofRgb(PROGRESS).forEach {
            assertThat(it.alphaFraction).isWithin(0.01f).of(1f)
        }
    }

    @Test
    fun `a grow entry extends the initial segments`() {
        val bar = newBar {
            entryAnimation = EntryAnimation.GROW
            animationDurationMs = 200L
            enabledDivisions = listOf(1)
        }

        layOut(bar)
        advance(100)

        val segment = render(bar).ops.ofColor(PROGRESS).single()
        assertThat(segment.left).isWithin(0.01f).of(75f)
        assertThat(segment.width).isLessThan(75f)
        assertThat(segment.width).isGreaterThan(0f)
    }

    @Test
    fun `a staggered entry reveals segments in order`() {
        val bar = newBar {
            entryAnimation = EntryAnimation.STAGGER
            animationDurationMs = 100L
            entryStaggerDelayMs = 100L
            enabledDivisions = listOf(0, 1, 2)
        }

        layOut(bar)

        // First segment is under way while the later ones have not started.
        advance(50)
        assertThat(render(bar).ops.ofRgb(PROGRESS)).hasSize(1)

        // Second joins in.
        advance(100)
        assertThat(render(bar).ops.ofRgb(PROGRESS)).hasSize(2)

        // Everything has arrived.
        advance(300)
        val settled = render(bar).ops.ofRgb(PROGRESS)
        assertThat(settled).hasSize(3)
        settled.forEach { assertThat(it.alphaFraction).isWithin(0.01f).of(1f) }
    }

    @Test
    fun `the entry animation runs only once`() {
        val bar = newBar {
            entryAnimation = EntryAnimation.FADE
            animationDurationMs = 200L
            enabledDivisions = listOf(0)
        }
        layOut(bar)
        advance(300)

        // A second layout pass, as a scroll or re-measure would cause.
        val canvas = render(bar)

        assertThat(canvas.ops.ofRgb(PROGRESS).single().alphaFraction).isWithin(0.01f).of(1f)
    }

    @Test
    fun `a change after entry uses the change animation, not the entry one`() {
        val bar = newBar {
            entryAnimation = EntryAnimation.FADE
            segmentAnimation = SegmentAnimation.NONE
            animationDurationMs = 200L
            enabledDivisions = listOf(0)
        }
        layOut(bar)
        advance(300)

        bar.toggleDivision(2)

        // segmentAnimation is NONE, so the new segment appears immediately.
        val segments = render(bar).ops.ofRgb(PROGRESS).sortedBy { it.left }
        assertThat(segments).hasSize(2)
        segments.forEach { assertThat(it.alphaFraction).isWithin(0.01f).of(1f) }
    }

    @Test
    fun `a negative stagger delay is rejected`() {
        val bar = newBar()

        assertThat(runCatching { bar.entryStaggerDelayMs = -1L }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    // endregion

    // region recurring animation

    @Test
    fun `recurring animation is off by default`() {
        val bar = newBar()

        assertThat(bar.recurringAnimation).isEqualTo(RecurringAnimation.NONE)
        assertThat(bar.recurringDurationMs)
            .isEqualTo(SegmentedProgressBar.DEFAULT_RECURRING_DURATION_MS)
    }

    @Test
    fun `a pulse varies the lit segments' alpha over time`() {
        val bar = attachedBar {
            recurringAnimation = RecurringAnimation.PULSE
            recurringDurationMs = 400L
            enabledDivisions = listOf(1)
        }

        val samples = (0 until 4).map {
            advance(100)
            renderToRecordingCanvas(bar, WIDTH, HEIGHT).ops.ofRgb(PROGRESS).single().alphaFraction
        }

        // It moves, and it never disappears entirely.
        assertThat(samples.distinct().size).isGreaterThan(1)
        samples.forEach { assertThat(it).isGreaterThan(0.3f) }
    }

    @Test
    fun `a shimmer tints different segments at different times`() {
        val bar = attachedBar {
            recurringAnimation = RecurringAnimation.SHIMMER
            recurringDurationMs = 800L
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        // Filtered by "not the track" rather than by colour: a tinted segment no
        // longer matches the progress colour exactly, which is the whole point.
        fun sample() = renderToRecordingCanvas(bar, WIDTH, HEIGHT)
            .ops.filter { it.color != TRACK }
            .sortedBy { it.left }
            .map { it.color }

        advance(100)
        val early = sample()
        advance(400)
        val later = sample()

        assertThat(early).hasSize(4)
        assertThat(later).hasSize(4)
        // The sweep moved, so the pattern of brightness across the bar changed.
        assertThat(early).isNotEqualTo(later)
        // And at least one segment is genuinely tinted at some point.
        assertThat((early + later).any { it != PROGRESS }).isTrue()
    }

    @Test
    fun `a shimmer only ever lightens towards the shimmer colour`() {
        val bar = attachedBar {
            recurringAnimation = RecurringAnimation.SHIMMER
            recurringDurationMs = 800L
            enabledDivisions = listOf(0, 1, 2, 3)
        }

        advance(200)
        val colors = renderToRecordingCanvas(bar, WIDTH, HEIGHT)
            .ops.filter { it.color != TRACK }
            .map { it.color }

        // Base colour is pure red, and the shimmer is white, so tinting can only
        // raise the green and blue channels and must leave alpha alone.
        colors.forEach {
            assertThat(Color.red(it)).isEqualTo(255)
            assertThat(Color.green(it)).isAtLeast(0)
            assertThat(Color.green(it)).isEqualTo(Color.blue(it))
            assertThat(Color.alpha(it)).isEqualTo(255)
        }
    }

    @Test
    fun `a detached bar runs no recurring animation`() {
        // The loop is driven by postInvalidateOnAnimation, which does nothing
        // while detached; the colour must stay put rather than freezing mid-tint.
        val bar = newBar {
            recurringAnimation = RecurringAnimation.SHIMMER
            enabledDivisions = listOf(1)
        }

        advance(200)
        val a = render(bar).ops.ofColor(PROGRESS).single().color
        advance(200)
        val b = render(bar).ops.ofColor(PROGRESS).single().color

        assertThat(a).isEqualTo(PROGRESS)
        assertThat(b).isEqualTo(PROGRESS)
    }

    @Test
    fun `turning the recurring animation off restores the plain colour`() {
        val bar = attachedBar {
            recurringAnimation = RecurringAnimation.SHIMMER
            enabledDivisions = listOf(0, 1, 2, 3)
        }
        advance(200)

        bar.recurringAnimation = RecurringAnimation.NONE

        renderToRecordingCanvas(bar, WIDTH, HEIGHT).ops.ofRgb(PROGRESS).forEach {
            assertThat(it.color).isEqualTo(PROGRESS)
        }
    }

    @Test
    fun `a non-positive recurring duration is rejected`() {
        val bar = newBar()

        assertThat(runCatching { bar.recurringDurationMs = 0L }.exceptionOrNull())
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    /** A bar attached to an activity, which the recurring loop requires. */
    private fun attachedBar(configure: SegmentedProgressBar.() -> Unit): SegmentedProgressBar {
        val activity = org.robolectric.Robolectric
            .buildActivity(android.app.Activity::class.java).setup().get()
        val bar = SegmentedProgressBar(activity).apply {
            divisions = DIVISIONS
            dividerWidth = 0f
            cornerRadius = 0f
            progressBarColor = PROGRESS
            progressBarBackgroundColor = TRACK
            configure()
        }
        activity.setContentView(bar, android.view.ViewGroup.LayoutParams(WIDTH, HEIGHT))
        shadowOf(Looper.getMainLooper()).idle()
        return bar
    }

    // endregion
}

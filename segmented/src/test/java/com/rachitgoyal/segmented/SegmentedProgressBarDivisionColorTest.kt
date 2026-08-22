package com.rachitgoyal.segmented

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Tests for per-division colours and their one-sentence supersedes rule: a
 * colour set for a division wins over [SegmentedProgressBar.progressBarColor]
 * for that division; every division without one keeps the global colour.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarDivisionColorTest {

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 40
        const val CELL = 75f

        const val GLOBAL = Color.RED
        const val OVERRIDE = Color.BLUE
        const val TRACK = Color.GREEN
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newBar(configure: SegmentedProgressBar.() -> Unit = {}) =
        SegmentedProgressBar(context).apply {
            divisions = 4
            dividerWidth = 0f
            cornerRadius = 0f
            progressBarColor = GLOBAL
            progressBarBackgroundColor = TRACK
            enabledDivisions = listOf(0, 1, 2, 3)
            configure()
        }

    private fun render(bar: SegmentedProgressBar) =
        renderToRecordingCanvas(bar, WIDTH, HEIGHT)

    @Test
    fun `an override colours its division and only its division`() {
        val bar = newBar()
        bar.setDivisionColor(1, OVERRIDE)

        val ops = render(bar).ops

        val overridden = ops.ofColor(OVERRIDE).single()
        assertThat(overridden.left).isWithin(0.01f).of(CELL)
        assertThat(overridden.right).isWithin(0.01f).of(2 * CELL)
        assertThat(ops.ofColor(GLOBAL)).hasSize(3)
    }

    @Test
    fun `changing the global colour repaints everything except the overrides`() {
        val bar = newBar()
        bar.setDivisionColor(1, OVERRIDE)

        bar.progressBarColor = Color.MAGENTA

        val ops = render(bar).ops
        assertThat(ops.ofColor(OVERRIDE)).hasSize(1)
        assertThat(ops.ofColor(Color.MAGENTA)).hasSize(3)
    }

    @Test
    fun `clearing one override returns that division to the global colour`() {
        val bar = newBar()
        bar.setDivisionColor(1, OVERRIDE)
        bar.setDivisionColor(2, Color.CYAN)

        bar.clearDivisionColor(1)

        val ops = render(bar).ops
        assertThat(ops.ofColor(OVERRIDE)).isEmpty()
        assertThat(ops.ofColor(Color.CYAN)).hasSize(1)
        assertThat(ops.ofColor(GLOBAL)).hasSize(3)
    }

    @Test
    fun `clearing all overrides returns to the single colour path`() {
        val bar = newBar()
        bar.setDivisionColor(1, OVERRIDE)
        bar.setDivisionColor(2, Color.CYAN)

        bar.clearDivisionColors()

        assertThat(render(bar).ops.ofColor(GLOBAL)).hasSize(4)
    }

    @Test
    fun `the getter reports the effective colour and has reports the override`() {
        val bar = newBar()
        bar.setDivisionColor(1, OVERRIDE)

        assertThat(bar.getDivisionColor(1)).isEqualTo(OVERRIDE)
        assertThat(bar.getDivisionColor(0)).isEqualTo(GLOBAL)
        assertThat(bar.hasDivisionColor(1)).isTrue()
        assertThat(bar.hasDivisionColor(0)).isFalse()

        // Setting the override to the global colour is still an override.
        bar.setDivisionColor(2, GLOBAL)
        assertThat(bar.hasDivisionColor(2)).isTrue()
    }

    @Test
    fun `an override colours a partial fill too`() {
        val bar = newBar { enabledDivisions = emptyList() }
        bar.setDivisionColor(1, OVERRIDE)
        bar.setDivisionProgress(1, 0.5f)

        val fill = render(bar).ops.ofColor(OVERRIDE).single()

        assertThat(fill.left).isWithin(0.01f).of(CELL)
        assertThat(fill.right).isWithin(0.01f).of(CELL + 0.5f * CELL)
    }

    @Test
    fun `a fading segment fades in its own colour`() {
        val bar = newBar {
            enabledDivisions = emptyList()
            segmentAnimation = SegmentAnimation.FADE
            animationDurationMs = 300
        }
        bar.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(WIDTH, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(HEIGHT, android.view.View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, WIDTH, HEIGHT)
        bar.setDivisionColor(1, OVERRIDE)

        bar.enableDivision(1)
        // At elapsed zero a fade-in is still at fraction zero, so step into the
        // middle of the transition before looking.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(150))

        // Mid-flight the alpha is scaled but the RGB must be the override's.
        val op = render(bar).ops.ofRgb(OVERRIDE).single()
        assertThat(op.alphaFraction).isLessThan(1f)
        assertThat(op.alphaFraction).isGreaterThan(0f)
        assertThat(render(bar).ops.ofRgb(GLOBAL)).isEmpty()
    }

    @Test
    fun `negative indices are ignored and out of range ones are retained`() {
        val bar = newBar()

        bar.setDivisionColor(-1, OVERRIDE)
        assertThat(bar.hasDivisionColor(-1)).isFalse()

        bar.setDivisionColor(7, OVERRIDE)
        bar.divisions = 10
        bar.enableDivision(7)
        assertThat(render(bar).ops.ofColor(OVERRIDE)).hasSize(1)
    }

    @Test
    fun `setting and clearing overrides requests a repaint`() {
        val bar = newBar()
        shadowOf(bar).clearWasInvalidated()

        bar.setDivisionColor(1, OVERRIDE)
        assertThat(shadowOf(bar).wasInvalidated()).isTrue()

        shadowOf(bar).clearWasInvalidated()
        bar.setDivisionColor(1, OVERRIDE) // unchanged value
        assertThat(shadowOf(bar).wasInvalidated()).isFalse()

        shadowOf(bar).clearWasInvalidated()
        bar.clearDivisionColor(1)
        assertThat(shadowOf(bar).wasInvalidated()).isTrue()

        shadowOf(bar).clearWasInvalidated()
        bar.clearDivisionColor(1) // already clear
        assertThat(shadowOf(bar).wasInvalidated()).isFalse()
    }
}

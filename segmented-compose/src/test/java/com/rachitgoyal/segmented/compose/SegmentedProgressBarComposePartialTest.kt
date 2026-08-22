package com.rachitgoyal.segmented.compose

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for the Compose renderer's partial fills ([segmentProgress]).
 *
 * Pixel-level, like the shadow tests, because the thing under test is where
 * paint lands inside a cell. At xhdpi 1dp is 2px; the bar is 80dp wide inside a
 * 100dp box with no gap, so each of 4 cells is exactly 40px:
 * ```
 * box   0..200px
 * bar  20..180px
 * cell 1:  60..100    cell 2: 100..140
 * ```
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedProgressBarComposePartialTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        val ON = Color(0xFFFF0000)
        val OFF = Color(0xFF00FF00)

        const val MID_Y = 30
    }

    private var lit by mutableStateOf(setOf(0))
    private var progress by mutableStateOf(mapOf(1 to 0.5f))
    private var rtl by mutableStateOf(false)
    private var composed = false

    private fun render(
        lit: Set<Int> = setOf(0),
        progress: Map<Int, Float> = mapOf(1 to 0.5f),
        rtl: Boolean = false,
    ): Bitmap {
        this.lit = lit
        this.progress = progress
        this.rtl = rtl
        if (!composed) {
            composed = true
            composeRule.setContent {
                val direction = if (this.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box(
                        modifier = Modifier.size(100.dp, 30.dp).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        SegmentedProgressBar(
                            divisions = 4,
                            enabledSegments = this@SegmentedProgressBarComposePartialTest.lit,
                            modifier = Modifier.width(80.dp).height(10.dp),
                            onColor = ON,
                            offColor = OFF,
                            gap = 0.dp,
                            cornerRadius = 0.dp,
                            segmentProgress =
                                this@SegmentedProgressBarComposePartialTest.progress,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    @Test
    fun `the fill covers the leading fraction of the cell`() {
        val image = render()

        // Cell 1 spans 60..100; a 0.5 fill covers 60..80.
        assertThat(image.getPixel(70, MID_Y)).isEqualTo(android.graphics.Color.RED)
        assertThat(image.getPixel(90, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }

    @Test
    fun `an enabled segment ignores its progress entry`() {
        val image = render(lit = setOf(0, 1), progress = mapOf(1 to 0.5f))

        // Fully lit despite the 0.5 entry.
        assertThat(image.getPixel(90, MID_Y)).isEqualTo(android.graphics.Color.RED)
    }

    @Test
    fun `under RTL the fill hugs the right edge of its cell`() {
        val image = render(rtl = true)

        // Logical cell 1 mirrors to 100..140; its leading half is 120..140.
        assertThat(image.getPixel(130, MID_Y)).isEqualTo(android.graphics.Color.RED)
        assertThat(image.getPixel(110, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }

    @Test
    fun `values are clamped rather than misdrawn`() {
        val image = render(progress = mapOf(1 to 5f, 2 to -3f))

        // 5 clamps to a full-looking fill; -3 clamps to nothing.
        assertThat(image.getPixel(90, MID_Y)).isEqualTo(android.graphics.Color.RED)
        assertThat(image.getPixel(120, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }

    @Test
    fun `a partial segment is not counted in the accessibility description`() {
        render(lit = setOf(0), progress = mapOf(1 to 0.9f))

        composeRule.onNodeWithContentDescription("1 of 4 segments complete").assertExists()
    }

    @Test
    fun `a NaN progress value is rejected`() {
        var thrown: Throwable? = null
        try {
            render(progress = mapOf(1 to Float.NaN))
        } catch (expected: Throwable) {
            thrown = expected
        }

        // The rule surfaces composition failures as wrapped exceptions, so
        // match on the message rather than the concrete type.
        assertThat(thrown).isNotNull()
        assertThat(generateSequence(thrown) { it.cause }.map { it.message }.toList().toString())
            .contains("finite")
    }
}

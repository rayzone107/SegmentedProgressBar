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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tests for per-segment colours in the Compose renderer.
 *
 * Same fixture as the partial fill tests: 4 gapless 40px cells from 20..180
 * inside a 200px box, sampled through the vertical centre.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w400dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SegmentedProgressBarComposeColorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        val ON = Color(0xFFFF0000)
        val OFF = Color(0xFF00FF00)
        val OVERRIDE = Color(0xFF0000FF)

        const val MID_Y = 30
    }

    private var lit by mutableStateOf(setOf(0, 1))
    private var colors by mutableStateOf(mapOf(1 to OVERRIDE))
    private var progress by mutableStateOf(emptyMap<Int, Float>())
    private var composed = false

    private fun render(
        lit: Set<Int> = setOf(0, 1),
        colors: Map<Int, Color> = mapOf(1 to OVERRIDE),
        progress: Map<Int, Float> = emptyMap(),
    ): Bitmap {
        this.lit = lit
        this.colors = colors
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
                        enabledSegments = this@SegmentedProgressBarComposeColorsTest.lit,
                        modifier = Modifier.width(80.dp).height(10.dp),
                        onColor = ON,
                        offColor = OFF,
                        gap = 0.dp,
                        cornerRadius = 0.dp,
                        segmentColors = this@SegmentedProgressBarComposeColorsTest.colors,
                        segmentProgress = this@SegmentedProgressBarComposeColorsTest.progress,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        return composeRule.onRoot().captureToImage().asAndroidBitmap()
    }

    @Test
    fun `an override colours its segment and only its segment`() {
        val image = render()

        // Cell 0 spans 20..60 and keeps the global colour; cell 1 spans 60..100
        // and takes the override; cell 2 is off.
        assertThat(image.getPixel(40, MID_Y)).isEqualTo(android.graphics.Color.RED)
        assertThat(image.getPixel(80, MID_Y)).isEqualTo(android.graphics.Color.BLUE)
        assertThat(image.getPixel(120, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }

    @Test
    fun `an override colours a partial fill too`() {
        val image = render(
            lit = setOf(0),
            colors = mapOf(2 to OVERRIDE),
            progress = mapOf(2 to 0.5f),
        )

        // Cell 2 spans 100..140; its leading half carries the override.
        assertThat(image.getPixel(110, MID_Y)).isEqualTo(android.graphics.Color.BLUE)
        assertThat(image.getPixel(130, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }

    @Test
    fun `removing the override returns the segment to the global colour`() {
        render()
        val image = render(colors = emptyMap())

        assertThat(image.getPixel(80, MID_Y)).isEqualTo(android.graphics.Color.RED)
    }

    @Test
    fun `an override on an off segment changes nothing`() {
        val image = render(lit = setOf(0), colors = mapOf(2 to OVERRIDE))

        assertThat(image.getPixel(120, MID_Y)).isEqualTo(android.graphics.Color.GREEN)
    }
}

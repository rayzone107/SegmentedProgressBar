package com.rachitgoyal.segmentedprogressbar.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rachitgoyal.segmented.CornerMode
import com.rachitgoyal.segmented.SegmentedProgressBar
import com.rachitgoyal.segmented.ShadowTarget
import java.io.File
import java.io.FileOutputStream
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every configuration the README documents, and writes the images the
 * README shows.
 *
 * Two jobs in one class, on purpose. As a test it proves that each documented
 * configuration renders, and renders something other than an empty bar, which is
 * the failure mode a screenshot would quietly hide. As a generator it keeps the
 * README's images honest: they come out of this library at a known version rather
 * than out of a phone at some forgotten point in the past, and regenerating the
 * whole set after a rendering change is one command:
 *
 * ```
 * ./gradlew :app:testDebugUnitTest --tests '*DocsScreenshotTest*' -Pdocs
 * ```
 *
 * Without `-Pdocs` nothing is written and the assertions still run.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DocsScreenshotTest {

    private companion object {
        /** xhdpi, so one dp is exactly two pixels and the images are retina. */
        const val DENSITY = 2

        const val WIDTH_DP = 300
        const val BAR_HEIGHT_DP = 26

        /** Room for a shadow to land in, on every side. */
        const val PAD_DP = 12

        /**
         * A near-white plate rather than transparency, so a shadow is visible and
         * the images read the same way in GitHub's light and dark themes.
         */
        val PLATE = 0xFFFAFAFC.toInt()

        val ON = 0xFF2F6FED.toInt()
        val OFF = 0xFFE4E7EB.toInt()

        /** The selection this library exists for: a subset, with holes in it. */
        val SPARSE = listOf(1, 2, 5, 6, 9)
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun dp(value: Int) = value * DENSITY

    /** Every image in the README, by file name. */
    private val shots: Map<String, SegmentedProgressBar.() -> Unit> = linkedMapOf(
        "basic" to {},
        "colours" to {
            progressBarColor = 0xFF12A150.toInt()
            progressBarBackgroundColor = 0xFFD7F0E0.toInt()
        },
        "gap-none" to {
            isDividerEnabled = false
            cornerRadius = 0f
        },
        "gap-wide" to { dividerWidth = dp(8).toFloat() },
        // A dark line, not the white one the library defaults to: on the
        // near-white plate these images use, a white divider and a transparent
        // gap look exactly the same, so the image would demonstrate nothing.
        "divider-line" to {
            dividerColor = 0xFF1B2733.toInt()
            dividerWidth = dp(3).toFloat()
            cornerRadius = 0f
            progressBarBackgroundColor = 0xFFB9C2CC.toInt()
        },
        "corners-bar-ends" to {
            cornerMode = CornerMode.BAR_ENDS
            cornerRadius = dp(13).toFloat()
            isDividerEnabled = false
        },
        "corners-each-segment" to {
            cornerMode = CornerMode.EACH_SEGMENT
            cornerRadius = dp(8).toFloat()
        },
        "corners-each-run" to {
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
        },
        "heights" to {
            activeHeightRatio = 1f
            inactiveHeightRatio = 0.45f
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
        },
        "shadow" to {
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
            shadowRadius = dp(5).toFloat()
            shadowDy = dp(3).toFloat()
        },
        "shadow-on-segments" to {
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
            shadowRadius = dp(5).toFloat()
            shadowDy = dp(3).toFloat()
            shadowTarget = ShadowTarget.ON_SEGMENTS
        },
        "divisions-few" to {
            divisions = 4
            enabledDivisions = listOf(0, 2)
            cornerRadius = dp(6).toFloat()
        },
        "divisions-many" to {
            divisions = 24
            enabledDivisions = (0 until 24).filter { it % 3 != 1 }
            cornerRadius = dp(2).toFloat()
        },
        "state-all" to { enabledDivisions = (0 until 10).toList() },
        "state-none" to { enabledDivisions = emptyList() },
        "rtl" to { layoutDirection = View.LAYOUT_DIRECTION_RTL },
        // The stories pattern: two chapters done, the third 40% through.
        "partial" to {
            divisions = 5
            enabledDivisions = listOf(0, 1)
            setDivisionProgress(2, 0.4f)
            dividerWidth = dp(4).toFloat()
            cornerRadius = dp(4).toFloat()
        },
        // A heatmap: every division on, each with its own colour.
        "heatmap" to {
            divisions = 14
            enabledDivisions = (0 until 14).toList()
            cornerMode = CornerMode.EACH_SEGMENT
            cornerRadius = dp(5).toFloat()
            progressBarBackgroundColor = 0xFFEDEFF2.toInt()
            val shades = listOf(0xFFDDF2E4, 0xFF9BDFB2, 0xFF3FB868, 0xFF12813C).map { it.toInt() }
            listOf(1, 3, 0, 2, 3, 1, 0, 0, 2, 3, 3, 1, 2, 0).forEachIndexed { index, level ->
                setDivisionColor(index, shades[level])
            }
        },
    )

    /** The three bars stacked in the README's header image. */
    private val hero: List<SegmentedProgressBar.() -> Unit> = listOf(
        {
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
            shadowRadius = dp(4).toFloat()
            shadowDy = dp(2).toFloat()
        },
        {
            divisions = 20
            enabledDivisions = (0 until 20).filter { it % 4 != 2 }
            cornerRadius = dp(3).toFloat()
            dividerWidth = dp(2).toFloat()
        },
        {
            cornerMode = CornerMode.EACH_RUN
            cornerRadius = dp(13).toFloat()
            inactiveHeightRatio = 0.45f
            progressBarColor = 0xFF12A150.toInt()
            progressBarBackgroundColor = 0xFFD7F0E0.toInt()
        },
    )

    @Test
    fun `every documented configuration renders`() {
        val target = System.getProperty("spb.docs.dir")?.let(::File)?.also { it.mkdirs() }

        shots.forEach { (name, configure) ->
            val image = render(configure)

            // A blank plate is exactly what a broken configuration would produce,
            // and exactly what nobody would notice in a committed PNG.
            assertThat(distinctColours(image)).isGreaterThan(2)

            target?.write(name, image)
        }

        val header = renderStack(hero)
        assertThat(distinctColours(header)).isGreaterThan(2)
        target?.write("hero", header)
    }

    private fun File.write(name: String, image: Bitmap) {
        FileOutputStream(File(this, "$name.png")).use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Draws several bars onto one plate, for a header image. */
    private fun renderStack(bars: List<SegmentedProgressBar.() -> Unit>): Bitmap {
        val rows = bars.map { render(it) }
        val image = Bitmap.createBitmap(
            rows.first().width,
            rows.sumOf { it.height },
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(image)
        canvas.drawColor(PLATE)
        var y = 0f
        rows.forEach { row ->
            canvas.drawBitmap(row, 0f, y, null)
            y += row.height
        }
        return image
    }

    @Test
    fun `the sparse selection is what the images show`() {
        // Guards the fixture rather than the rendering: every image is meant to
        // show a set with holes in it, because that is the whole point.
        val bar = newBar {}

        assertThat(bar.enabledDivisions).containsExactlyElementsIn(SPARSE).inOrder()
        assertThat(bar.isDivisionEnabled(3)).isFalse()
        assertThat(bar.isDivisionEnabled(4)).isFalse()
    }

    private fun newBar(configure: SegmentedProgressBar.() -> Unit) =
        SegmentedProgressBar(context).apply {
            divisions = 10
            enabledDivisions = SPARSE
            progressBarColor = ON
            progressBarBackgroundColor = OFF
            dividerColor = Color.TRANSPARENT
            dividerWidth = dp(3).toFloat()
            cornerRadius = dp(4).toFloat()
            configure()
        }

    private fun render(configure: SegmentedProgressBar.() -> Unit): Bitmap {
        val bar = newBar(configure)
        val pad = dp(PAD_DP)
        val width = dp(WIDTH_DP)
        val height = dp(BAR_HEIGHT_DP) + pad * 2
        // Padding on the view, not an inset on the canvas: a View shadow is drawn
        // into a software layer the size of the view, so without padding the blur
        // would be clipped at the bar's own edges. Every image carries the same
        // padding, shadow or not, so the bars are all the same size.
        bar.setPadding(pad, pad, pad, pad)
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, width, height)

        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(PLATE)
        bar.draw(canvas)
        return image
    }

    private fun distinctColours(image: Bitmap): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until image.width step 4) {
            for (y in 0 until image.height step 4) {
                seen += image.getPixel(x, y)
            }
        }
        return seen.size
    }
}

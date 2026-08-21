package com.rachitgoyal.segmentedprogressbar.demo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.rachitgoyal.segmented.SegmentedProgressBar
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Consumer-side coverage: inflates the library's view from a real XML layout in
 * a real application module, which is how everybody actually uses it.
 *
 * The library module has no layouts of its own, so its own tests have to
 * synthesise an `AttributeSet`. These tests close that gap, they would catch a
 * broken `declare-styleable`, a namespace or `R` class regression, or an
 * attribute that stopped being read during inflation.
 */
@RunWith(AndroidJUnit4::class)
class InflationTest {

    private fun inflate(): View {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.setTheme(R.style.AppTheme)
        return LayoutInflater.from(context).inflate(R.layout.activity_main, null)
    }

    @Test
    fun `the view inflates from a layout and reads its attributes`() {
        val root = inflate()

        val bar = root.findViewById<SegmentedProgressBar>(R.id.toggle_bar)
        val density = bar.resources.displayMetrics.density

        assertThat(bar.divisions).isEqualTo(10)
        assertThat(bar.isDividerEnabled).isTrue()
        assertThat(bar.dividerColor).isEqualTo(Color.WHITE)
        assertThat(bar.progressBarColor).isEqualTo(Color.parseColor("#2F6FED"))
        assertThat(bar.progressBarBackgroundColor).isEqualTo(Color.parseColor("#E4E7EB"))
        assertThat(bar.dividerWidth).isWithin(1f).of(3f * density)
        assertThat(bar.cornerRadius).isWithin(1f).of(6f * density)
    }

    @Test
    fun `isDividerEnabled false survives inflation`() {
        val root = inflate()

        val bar = root.findViewById<SegmentedProgressBar>(R.id.pill_bar)

        assertThat(bar.isDividerEnabled).isFalse()
        assertThat(bar.divisions).isEqualTo(12)
    }

    @Test
    fun `an inflated view with no divider attributes uses the library defaults`() {
        val root = inflate()

        val bar = root.findViewById<SegmentedProgressBar>(R.id.pill_bar)

        assertThat(bar.dividerWidth).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_WIDTH_PX)
        assertThat(bar.dividerColor).isEqualTo(SegmentedProgressBar.DEFAULT_DIVIDER_COLOR)
    }

    @Test
    fun `padding declared in xml reaches the view`() {
        val root = inflate()

        val bar = root.findViewById<SegmentedProgressBar>(R.id.padded_bar)
        val density = bar.resources.displayMetrics.density

        assertThat(bar.paddingLeft).isWithin(1).of((24f * density).toInt())
        assertThat(bar.paddingTop).isWithin(1).of((14f * density).toInt())
    }

    @Test
    fun `a wrap_content bar measures to the library's intrinsic size`() {
        val root = inflate()
        val bar = root.findViewById<SegmentedProgressBar>(R.id.wrap_bar)
        val density = bar.resources.displayMetrics.density

        bar.measure(
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.AT_MOST),
        )

        assertThat(bar.measuredWidth).isEqualTo((144f * density).toInt())
        assertThat(bar.measuredHeight).isEqualTo((8f * density).toInt())
    }

    @Test
    fun `spb_tapToToggle in xml makes the bar interactive with no code at all`() {
        val root = inflate()

        val tappable = root.findViewById<SegmentedProgressBar>(R.id.fade_bar)
        assertThat(tappable.isTapToToggleEnabled).isTrue()
        assertThat(tappable.isClickable).isTrue()
        assertThat(tappable.isFocusable).isTrue()

        // A bar without the attribute is left alone, including its clickability.
        val plain = root.findViewById<SegmentedProgressBar>(R.id.pill_bar)
        assertThat(plain.isTapToToggleEnabled).isFalse()
        assertThat(plain.isClickable).isFalse()
    }

    @Test
    fun `the shadow attributes reach the view`() {
        val root = inflate()

        val bar = root.findViewById<SegmentedProgressBar>(R.id.shadow_bar)
        val density = bar.resources.displayMetrics.density

        assertThat(bar.shadowRadius).isWithin(1f).of(5f * density)
        assertThat(bar.shadowDy).isWithin(1f).of(3f * density)
        assertThat(bar.shadowColor).isEqualTo(Color.parseColor("#73000000"))
        // Padding, because the shadow draws outside the bar and the view's own
        // software layer is the only thing it can draw into.
        assertThat(bar.paddingLeft).isGreaterThan(0)
        assertThat(bar.paddingTop).isGreaterThan(0)
    }

    @Test
    fun `every bar in the demo layout inflates`() {
        val root = inflate()

        val ids = listOf(
            R.id.toggle_bar,
            R.id.sparse_bar,
            R.id.habit_bar,
            R.id.pill_bar,
            R.id.rtl_bar,
            R.id.padded_bar,
            R.id.wrap_bar,
        )

        ids.forEach { id ->
            assertThat(root.findViewById<SegmentedProgressBar>(id)).isNotNull()
        }
    }
}

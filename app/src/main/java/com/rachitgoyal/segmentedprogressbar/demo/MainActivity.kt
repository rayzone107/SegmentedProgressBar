package com.rachitgoyal.segmentedprogressbar.demo

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.tabs.TabLayout
import com.rachitgoyal.segmented.SegmentedProgressBar
import com.rachitgoyal.segmentedprogressbar.demo.databinding.ActivityMainBinding

/**
 * Demo screen for [SegmentedProgressBar].
 *
 * The ordering of the sections is deliberate. This library's reason to exist is
 * that **any arbitrary subset of segments can be lit at once**: something
 * [android.widget.ProgressBar] cannot express, so the screen leads with
 * independent per-segment toggling and sparse sets, and treats the contiguous
 * "first N segments" case as the special case it actually is.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        applyWindowInsets()
        setUpTabs()

        setUpToggleBar()
        setUpSparseBars()
        setUpHabitTracker()
        setUpStylingVariants()
        setUpAnimatedBars()
    }

    /**
     * Insets the app bar and the scrolling content by the system bars.
     *
     * Since targetSdk 35 Android enforces edge-to-edge, so the window extends
     * behind the status and navigation bars and nothing is inset automatically.
     * The top inset goes on the toolbar (which grows past its `minHeight`
     * accordingly) and the bottom inset on the scroll view, which keeps
     * `clipToPadding=false` so content still scrolls *through* the gesture area
     * rather than stopping short of it.
     */
    private fun applyWindowInsets() {
        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            binding.scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Wires the two tab pages.
     *
     * Both pages stay inflated and are shown or hidden, rather than living in a
     * ViewPager2: a pager recycles its pages, and the Gallery's views are the
     * library's consumer-side test surface, so they need to exist for the whole
     * lifetime of the activity.
     */
    private fun setUpTabs() {
        binding.playgroundPage.setContent { PlaygroundScreen() }

        binding.tabs.apply {
            addTab(newTab().setText(R.string.tab_playground))
            addTab(newTab().setText(R.string.tab_gallery))
            addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) = showPage(tab.position)
                    override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                    override fun onTabReselected(tab: TabLayout.Tab) = Unit
                },
            )
        }
        showPage(0)
    }

    private fun showPage(position: Int) {
        binding.playgroundPage.isVisible = position == 0
        binding.scroll.isVisible = position == 1
    }

    // region tappable bar

    /**
     * Turns the bar into a row of independently tappable segments.
     *
     * All the coordinate work lives in the library:
     * [SegmentedProgressBar.setOnDivisionClickListener] reports which segment was
     * hit, so this stays correct under padding and RTL and the demo duplicates no
     * geometry. The bar reports the tap; deciding that a tap means "toggle" is
     * the app's call.
     */
    private fun setUpToggleBar() {
        val bar = binding.toggleBar
        bar.enabledDivisions = SPARSE_SELECTION

        bar.setOnDivisionClickListener { view, index ->
            view.toggleDivision(index)
            renderToggleLabel()
        }

        // A non-positional control, so the bar is still usable by anyone who
        // cannot aim at an individual segment.
        binding.toggleReset.setOnClickListener {
            bar.reset()
            renderToggleLabel()
        }

        renderToggleLabel()
    }

    private fun renderToggleLabel() {
        binding.toggleLabel.text = getString(
            R.string.toggle_label,
            binding.toggleBar.enabledDivisions.toString(),
        )
    }

    // endregion

    // region declarative selections

    private fun setUpSparseBars() {
        binding.sparseBar.enabledDivisions = SPARSE_SELECTION

        // With dividers off, adjacent segments that are on merge into one run.
        binding.pillBar.enabledDivisions = listOf(0, 1, 2, 5, 8, 9, 10)

        // Identical API under RTL. The pattern mirrors; the code does not change.
        binding.rtlBar.enabledDivisions = SPARSE_SELECTION

        binding.paddedBar.enabledDivisions = listOf(0, 2, 5)
        binding.wrapBar.enabledDivisions = listOf(0, 3)
    }

    // endregion

    // region habit tracker

    /**
     * A concrete use case for gaps: days of the week, where the days you missed
     * are exactly the point.
     */
    private fun setUpHabitTracker() {
        binding.habitBar.enabledDivisions = DAYS_COMPLETED

        val dayLabels = listOf(
            R.string.day_monday,
            R.string.day_tuesday,
            R.string.day_wednesday,
            R.string.day_thursday,
            R.string.day_friday,
            R.string.day_saturday,
            R.string.day_sunday,
        )
        dayLabels.forEachIndexed { index, label ->
            binding.habitDays.addView(
                TextView(this).apply {
                    text = getString(label)
                    gravity = Gravity.CENTER
                    textSize = 12f
                    alpha = if (index in DAYS_COMPLETED) 1f else 0.35f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
        }
    }

    // endregion

    // region styling variants

    /**
     * The purely visual variants. Every one is configured in XML; this only
     * supplies the data, and deliberately supplies *sparse* data so the styling
     * is shown doing the thing the library is for.
     */
    private fun setUpStylingVariants() {
        // Height difference: a slim track with full-height lit segments.
        binding.heightBar.enabledDivisions = listOf(0, 1, 2, 5, 7)

        // Every cell rounded, so unlit cells read as pills too.
        binding.eachSegmentBar.enabledDivisions = listOf(0, 2, 3, 6)

        // Runs of adjacent segments merge into single pills.
        binding.eachRunBar.enabledDivisions = listOf(0, 1, 2, 4, 7, 8, 11)

        binding.shadowBar.enabledDivisions = listOf(0, 1, 3, 5)
    }

    // endregion

    // region animation

    /**
     * The two animated bars are tappable, because an animation you cannot
     * trigger is not much of a demo.
     *
     * No click listener here on purpose: both bars carry
     * `app:spb_tapToToggle="true"` in the layout, which is all a bar needs to
     * become interactive when nothing else has to happen on a tap. Compare
     * [setUpToggleBar], which does have something else to do.
     */
    private fun setUpAnimatedBars() {
        listOf(binding.fadeBar, binding.growBar).forEach { bar ->
            bar.enabledDivisions = listOf(1, 2, 5)
        }
    }

    // endregion

    private companion object {
        /** An arbitrary selection, which is all this library ever asks for. */
        val SPARSE_SELECTION = listOf(1, 2, 5, 6, 9)

        /** Mon, Wed, Thu, Sun, a week with two gaps in it. */
        val DAYS_COMPLETED = listOf(0, 2, 3, 6)
    }
}

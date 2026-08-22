package com.rachitgoyal.segmented

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * Tests for [SegmentedProgressBar.isPerDivisionAccessibilityEnabled], driven
 * through the same [android.view.accessibility.AccessibilityNodeProvider] an
 * accessibility service uses, so what passes here is what TalkBack sees.
 *
 * The bar is laid out 400px wide with 4 divisions, so each virtual node's cell
 * is exactly 100px.
 */
@RunWith(AndroidJUnit4::class)
class SegmentedProgressBarAccessibilityTest {

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 20
        const val DIVISIONS = 4
    }

    private fun attachedBar(
        configure: SegmentedProgressBar.() -> Unit = {},
    ): SegmentedProgressBar {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bar = SegmentedProgressBar(activity).apply {
            divisions = DIVISIONS
            configure()
        }
        activity.setContentView(bar, ViewGroup.LayoutParams(WIDTH, HEIGHT))
        shadowOf(Looper.getMainLooper()).idle()
        check(bar.width == WIDTH) { "expected ${WIDTH}px, was ${bar.width}px" }
        return bar
    }

    private fun SegmentedProgressBar.node(index: Int): AccessibilityNodeInfo =
        checkNotNull(accessibilityNodeProvider) { "no node provider installed" }
            .createAccessibilityNodeInfo(index)!!

    @Test
    fun `off by default, leaving the single summary node`() {
        val bar = attachedBar()

        assertThat(bar.isPerDivisionAccessibilityEnabled).isFalse()
        assertThat(bar.accessibilityNodeProvider).isNull()
    }

    @Test
    fun `each division becomes a checkable node with its own description`() {
        val bar = attachedBar {
            isPerDivisionAccessibilityEnabled = true
            enabledDivisions = listOf(1, 3)
        }

        val first = bar.node(0)
        assertThat(first.contentDescription.toString()).isEqualTo("Segment 1 of 4")
        assertThat(first.isCheckable).isTrue()
        assertThat(first.isChecked).isFalse()

        val second = bar.node(1)
        assertThat(second.contentDescription.toString()).isEqualTo("Segment 2 of 4")
        assertThat(second.isChecked).isTrue()
    }

    @Test
    fun `node bounds are the full-height cell, with no dead zones`() {
        val bar = attachedBar { isPerDivisionAccessibilityEnabled = true }

        val bounds = Rect()
        @Suppress("DEPRECATION")
        bar.node(1).getBoundsInParent(bounds)

        assertThat(bounds).isEqualTo(Rect(100, 0, 200, HEIGHT))
    }

    @Test
    fun `under RTL the first division's node sits at the right-hand end`() {
        val bar = attachedBar {
            context.applicationInfo.flags =
                context.applicationInfo.flags or ApplicationInfo.FLAG_SUPPORTS_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            isPerDivisionAccessibilityEnabled = true
        }
        check(bar.layoutDirection == View.LAYOUT_DIRECTION_RTL)

        val bounds = Rect()
        @Suppress("DEPRECATION")
        bar.node(0).getBoundsInParent(bounds)

        assertThat(bounds).isEqualTo(Rect(300, 0, WIDTH, HEIGHT))
    }

    @Test
    fun `activating a node toggles the division and then notifies the listener`() {
        val seen = mutableListOf<Pair<Int, Boolean>>()
        val bar = attachedBar {
            isPerDivisionAccessibilityEnabled = true
            isTapToToggleEnabled = true
            setOnDivisionClickListener { view, index ->
                seen += index to view.isDivisionEnabled(index)
            }
        }

        val handled = bar.accessibilityNodeProvider!!
            .performAction(2, AccessibilityNodeInfo.ACTION_CLICK, null)

        assertThat(handled).isTrue()
        assertThat(bar.enabledDivisions).containsExactly(2)
        // The listener ran after the toggle, seeing the state the user now sees.
        assertThat(seen).containsExactly(2 to true)
    }

    @Test
    fun `nodes on a non-interactive bar are read-only`() {
        val bar = attachedBar {
            isPerDivisionAccessibilityEnabled = true
            enabledDivisions = listOf(0)
        }

        val node = bar.node(0)
        assertThat(node.isClickable).isFalse()

        val handled = bar.accessibilityNodeProvider!!
            .performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null)

        assertThat(handled).isFalse()
        assertThat(bar.enabledDivisions).containsExactly(0)
    }

    @Test
    fun `an interactive bar advertises the click action`() {
        val bar = attachedBar {
            isPerDivisionAccessibilityEnabled = true
            isTapToToggleEnabled = true
        }

        val node = bar.node(0)

        assertThat(node.isClickable).isTrue()
        assertThat(node.actionList.map { it.id })
            .contains(AccessibilityNodeInfo.ACTION_CLICK)
    }

    @Test
    fun `turning the flag off restores the summary-only behaviour`() {
        val bar = attachedBar { isPerDivisionAccessibilityEnabled = true }
        assertThat(bar.accessibilityNodeProvider).isNotNull()

        bar.isPerDivisionAccessibilityEnabled = false

        assertThat(bar.accessibilityNodeProvider).isNull()
    }

    @Test
    fun `growing the divisions grows the virtual tree`() {
        val bar = attachedBar { isPerDivisionAccessibilityEnabled = true }

        bar.divisions = 6

        assertThat(bar.node(5).contentDescription.toString()).isEqualTo("Segment 6 of 6")
    }
}

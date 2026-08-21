package com.rachitgoyal.segmented;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Deliberately written in Java, to hold two promises that Kotlin tests cannot
 * check.
 *
 * <p>First, that the library stays comfortable to use from Java: the Kotlin
 * properties have to surface as ordinary {@code getX}/{@code setX} methods with
 * the parameter types a Java caller expects.
 *
 * <p>Second, and more importantly, that every method 0.0.1 exposed still exists
 * with the same signature. This file is essentially the 0.0.1 public API written
 * out longhand. If a refactor renames or re-types any of it, this stops
 * compiling, which is exactly the point. A Kotlin test would silently follow the
 * rename.
 */
@RunWith(AndroidJUnit4.class)
public class JavaApiCompatibilityTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    // region the 0.0.1 API surface

    @Test
    public void everyMethodFrom_0_0_1_stillCompilesAndWorks() {
        SegmentedProgressBar bar = new SegmentedProgressBar(context);

        bar.setDivisions(10);
        bar.setProgressBarColor(Color.RED);
        bar.setDividerColor(Color.BLUE);
        bar.setDividerWidth(4f);
        bar.setDividerEnabled(false);
        bar.setCornerRadius(6f);

        List<Integer> enabled = Arrays.asList(1, 4, 5, 8, 9);
        bar.setEnabledDivisions(enabled);

        assertThat(bar.getDivisions()).isEqualTo(10);
        assertThat(bar.getProgressBarColor()).isEqualTo(Color.RED);
        assertThat(bar.getDividerColor()).isEqualTo(Color.BLUE);
        assertThat(bar.getDividerWidth()).isEqualTo(4f);
        assertThat(bar.isDividerEnabled()).isFalse();
        assertThat(bar.getCornerRadius()).isEqualTo(6f);
        assertThat(bar.getEnabledDivisions()).containsExactly(1, 4, 5, 8, 9).inOrder();

        bar.reset();
        assertThat(bar.getEnabledDivisions()).isEmpty();
        // reset() must not have disturbed the configuration.
        assertThat(bar.getDivisions()).isEqualTo(10);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void theDeprecatedSetBackgroundColorStillCompilesAndPaintsTheTrack() {
        SegmentedProgressBar bar = new SegmentedProgressBar(context);

        bar.setBackgroundColor(Color.MAGENTA);

        assertThat(bar.getProgressBarBackgroundColor()).isEqualTo(Color.MAGENTA);
    }

    @Test
    public void theSampleCodeFromThe_0_0_1_readmeStillWorks() {
        // Lifted from the 0.0.1 demo activity verbatim.
        SegmentedProgressBar bar = new SegmentedProgressBar(context);
        bar.setDivisions(10);

        Integer[] enabled = new Integer[] {1, 4, 5, 8, 9};
        bar.setEnabledDivisions(Arrays.asList(enabled));

        assertThat(bar.getCompletedSegmentCount()).isEqualTo(5);
    }

    // endregion

    // region constructors

    @Test
    public void allFourViewConstructorsAreAvailableToJava() {
        assertThat(new SegmentedProgressBar(context)).isNotNull();
        assertThat(new SegmentedProgressBar(context, null)).isNotNull();
        assertThat(new SegmentedProgressBar(context, null, 0)).isNotNull();
        assertThat(new SegmentedProgressBar(context, null, 0, 0)).isNotNull();
    }

    // endregion

    // region subclassing

    /**
     * The 0.0.1 class was a plain Java class and therefore subclassable. Kotlin
     * classes are final by default, so this exists to keep the class
     * {@code open}. If someone marks it final, this file stops compiling.
     */
    private static class CustomBar extends SegmentedProgressBar {

        boolean drawn;

        CustomBar(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            drawn = true;
        }
    }

    @Test
    public void theViewCanStillBeSubclassedFromJava() {
        CustomBar bar = new CustomBar(context);
        bar.setDivisions(4);
        bar.setEnabledDivisions(Arrays.asList(0, 1));

        bar.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(20, android.view.View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, 200, 20);
        bar.draw(new android.graphics.Canvas(
                android.graphics.Bitmap.createBitmap(200, 20, android.graphics.Bitmap.Config.ARGB_8888)));

        assertThat(bar.drawn).isTrue();
        assertThat(bar.getCompletedSegmentCount()).isEqualTo(2);
    }

    // endregion

    // region the 2.0.0 additions

    @Test
    public void theNewApiIsUsableFromJava() {
        SegmentedProgressBar bar = new SegmentedProgressBar(context);
        bar.setDivisions(8);

        bar.setProgressBarBackgroundColor(Color.GREEN);
        bar.setEnabledDivisions(Arrays.asList(0, 1, 2, 3, 4));

        assertThat(bar.getProgressBarBackgroundColor()).isEqualTo(Color.GREEN);
        assertThat(bar.getCompletedSegmentCount()).isEqualTo(5);

        bar.enableDivision(7);
        bar.disableDivision(0);

        assertThat(bar.isDivisionEnabled(7)).isTrue();
        assertThat(bar.isDivisionEnabled(0)).isFalse();
        assertThat(bar.getEnabledDivisions()).containsExactly(1, 2, 3, 4, 7).inOrder();
    }

    @Test
    public void theDefaultConstantsAreReadableFromJava() {
        assertThat(SegmentedProgressBar.DEFAULT_DIVISIONS).isEqualTo(1);
        assertThat(SegmentedProgressBar.DEFAULT_PROGRESS_COLOR).isEqualTo(0xFF5097E2);
        assertThat(SegmentedProgressBar.DEFAULT_BACKGROUND_COLOR).isEqualTo(0xFFC1C1C1);
        assertThat(SegmentedProgressBar.DEFAULT_DIVIDER_COLOR).isEqualTo(Color.WHITE);
        assertThat(SegmentedProgressBar.DEFAULT_DIVIDER_WIDTH_PX).isEqualTo(1f);
        assertThat(SegmentedProgressBar.DEFAULT_CORNER_RADIUS_PX).isEqualTo(2f);
    }

    // endregion

    // region validation as seen from Java

    @Test
    public void invalidConfigurationThrowsForJavaCallersToo() {
        SegmentedProgressBar bar = new SegmentedProgressBar(context);

        try {
            bar.setDivisions(0);
            throw new AssertionError("expected setDivisions(0) to be rejected");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("divisions");
        }

        try {
            bar.setDividerWidth(-1f);
            throw new AssertionError("expected a negative divider width to be rejected");
        } catch (IllegalArgumentException expected) {
            assertThat(expected).hasMessageThat().contains("dividerWidth");
        }
    }

    // endregion
}

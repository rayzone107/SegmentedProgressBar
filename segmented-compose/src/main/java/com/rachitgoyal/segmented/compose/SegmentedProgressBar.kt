package com.rachitgoyal.segmented.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rachitgoyal.segmented.CornerMode
import com.rachitgoyal.segmented.EntryAnimation
import com.rachitgoyal.segmented.RecurringAnimation
import com.rachitgoyal.segmented.SegmentAnimation
import com.rachitgoyal.segmented.SegmentGeometry
import com.rachitgoyal.segmented.ShadowTarget
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A progress bar split into a fixed number of equal segments, where each segment
 * can be turned on and off independently.
 *
 * The Compose counterpart of
 * [com.rachitgoyal.segmented.SegmentedProgressBar]. Both draw from the same
 * [SegmentGeometry], so the two render identically; this one is a real
 * Composable rather than an `AndroidView` wrapper, so it composes with modifiers,
 * previews and animation as you would expect.
 *
 * ```kotlin
 * var on by remember { mutableStateOf(setOf(1, 2, 5, 6, 9)) }
 *
 * SegmentedProgressBar(
 *     divisions = 10,
 *     enabledSegments = on,
 *     modifier = Modifier.fillMaxWidth().height(20.dp),
 *     onSegmentClick = { index ->
 *         on = if (index in on) on - index else on + index
 *     },
 * )
 * ```
 *
 * @param divisions number of equal segments; must be at least `1`.
 * @param enabledSegments zero-based indices of the segments that are on. Any
 *   subset, in any order. Indices outside `0 until divisions` are ignored.
 * @param modifier standard Compose modifier. Size the bar here; it falls back to
 *   an intrinsic 144dp x 8dp if you don't.
 * @param onColor colour of segments that are on.
 * @param offColor colour of segments that are off.
 * @param gap space between segments. Transparent by default, so it reads as a
 *   genuine gap; give [gapColor] a colour to paint a divider line instead.
 * @param gapColor colour painted in the gap. [Color.Transparent] leaves it empty.
 * @param cornerRadius radius applied per [cornerMode].
 * @param cornerMode which edges [cornerRadius] applies to.
 * @param activeHeightFraction height of on segments as a fraction of the bar.
 * @param inactiveHeightFraction height of off segments as a fraction of the bar.
 * @param shadow drop shadow, or `null` for none. Drawn outside the bar, so it
 *   never changes the bar's size or position.
 * @param segmentAnimation how a segment transitions when it is toggled after the
 *   bar is already on screen.
 * @param entryAnimation how the initial state arrives on first composition.
 * @param recurringAnimation a continuous animation while the bar is composed.
 * @param animationDurationMillis duration of segment and entry transitions.
 * @param entryStaggerDelayMillis delay between segments in a staggered entry.
 * @param recurringDurationMillis period of one recurring cycle.
 * @param shimmerColor colour blended in at the peak of a shimmer sweep.
 * @param contentDescription accessibility label; a sensible one is generated when
 *   `null`.
 * @param perSegmentAccessibility whether each segment is exposed to
 *   accessibility services as its own focusable, checkable node ("Segment 3 of
 *   10", on or off), which a screen reader steps through and, when
 *   [onSegmentClick] is set, toggles in place. Off by default: one summary
 *   node reads better for a passive indicator, per-segment nodes for a control
 *   the user operates.
 * @param segmentColors per-segment overrides of [onColor], by index. The rule
 *   is one sentence: a colour here wins over [onColor] for its segment; every
 *   segment without one keeps using [onColor]. The override covers the
 *   segment's full fill, its partial fill, and the base a shimmer or pulse
 *   tints; off segments always use [offColor]. The classic use is a heatmap,
 *   every segment on and each carrying an intensity.
 * @param segmentProgress fractional fills by segment index, `0` to `1`, for
 *   segments that are underway rather than done: the stories or chapters
 *   pattern, where finished segments are in [enabledSegments] and the current
 *   one advances through a fraction. A segment that is already in
 *   [enabledSegments] ignores its entry here, and a partial segment never joins
 *   a [CornerMode.EACH_RUN] run; it draws as its own in-progress pill, clipped
 *   to the cell's shape. Values are clamped to `0..1`, and change with no
 *   transition, since the callers that drive this update it continuously.
 * @param onSegmentClick invoked with the index of a tapped segment. Passing
 *   `null` leaves the bar non-interactive.
 */
@Composable
public fun SegmentedProgressBar(
    divisions: Int,
    enabledSegments: Set<Int>,
    modifier: Modifier = Modifier,
    onColor: Color = SegmentedProgressBarDefaults.OnColor,
    offColor: Color = SegmentedProgressBarDefaults.OffColor,
    gap: Dp = SegmentedProgressBarDefaults.Gap,
    gapColor: Color = Color.Transparent,
    cornerRadius: Dp = SegmentedProgressBarDefaults.CornerRadius,
    cornerMode: CornerMode = CornerMode.BAR_ENDS,
    activeHeightFraction: Float = 1f,
    inactiveHeightFraction: Float = 1f,
    shadow: SegmentShadow? = null,
    segmentAnimation: SegmentAnimation = SegmentAnimation.NONE,
    entryAnimation: EntryAnimation = EntryAnimation.NONE,
    recurringAnimation: RecurringAnimation = RecurringAnimation.NONE,
    animationDurationMillis: Int = SegmentedProgressBarDefaults.AnimationDurationMillis,
    entryStaggerDelayMillis: Int = SegmentedProgressBarDefaults.EntryStaggerDelayMillis,
    recurringDurationMillis: Int = SegmentedProgressBarDefaults.RecurringDurationMillis,
    shimmerColor: Color = SegmentedProgressBarDefaults.ShimmerColor,
    contentDescription: String? = null,
    segmentColors: Map<Int, Color> = emptyMap(),
    segmentProgress: Map<Int, Float> = emptyMap(),
    perSegmentAccessibility: Boolean = false,
    onSegmentClick: ((Int) -> Unit)? = null,
) {
    require(divisions >= 1) { "divisions must be >= 1 but was $divisions" }
    require(segmentProgress.values.all { it.isFinite() }) {
        "segmentProgress values must be finite but were $segmentProgress"
    }
    require(activeHeightFraction in 0f..1f) {
        "activeHeightFraction must be between 0 and 1 but was $activeHeightFraction"
    }
    require(inactiveHeightFraction in 0f..1f) {
        "inactiveHeightFraction must be between 0 and 1 but was $inactiveHeightFraction"
    }
    require(animationDurationMillis >= 0) {
        "animationDurationMillis must be >= 0 but was $animationDurationMillis"
    }
    require(recurringDurationMillis > 0) {
        "recurringDurationMillis must be > 0 but was $recurringDurationMillis"
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val animation = rememberSegmentAnimation(
        divisions = divisions,
        enabledSegments = enabledSegments,
        segmentAnimation = segmentAnimation,
        entryAnimation = entryAnimation,
        animationDurationMillis = animationDurationMillis,
        entryStaggerDelayMillis = entryStaggerDelayMillis,
        segmentProgress = segmentProgress,
    )

    val recurringPhase = rememberRecurringPhase(recurringAnimation, recurringDurationMillis)

    val onCount = enabledSegments.count { it in 0 until divisions }
    val description = contentDescription ?: "$onCount of $divisions segments complete"

    val clickModifier = if (onSegmentClick == null) {
        Modifier
    } else {
        Modifier.pointerInput(divisions, isRtl) {
            detectTapGestures { offset ->
                val index = segmentIndexAt(
                    x = offset.x,
                    width = size.width.toFloat(),
                    divisions = divisions,
                    isRtl = isRtl,
                )
                if (index != NoSegment) onSegmentClick(index)
            }
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = SegmentedProgressBarDefaults.IntrinsicWidth,
                minHeight = SegmentedProgressBarDefaults.IntrinsicHeight,
            )
            .semantics {
                this.contentDescription = description
                progressBarRangeInfo =
                    ProgressBarRangeInfo(onCount.toFloat(), 0f..divisions.toFloat(), divisions)
            }
            .then(clickModifier)
            .drawBehind {
                drawSegmentedBar(
                    divisions = divisions,
                    fractionOf = { index -> animation.fractions[index] ?: 0f },
                    partialOf = { index ->
                        if (index in enabledSegments) 0f
                        else (segmentProgress[index] ?: 0f).coerceIn(0f, 1f)
                    },
                    transitionStyle = animation.style,
                    onColorOf = { index -> segmentColors[index] ?: onColor },
                    offColor = offColor,
                    gapPx = gap.toPx(),
                    gapColor = gapColor,
                    cornerRadiusPx = cornerRadius.toPx(),
                    cornerMode = cornerMode,
                    onSegments = enabledSegments,
                    activeHeightFraction = activeHeightFraction,
                    inactiveHeightFraction = inactiveHeightFraction,
                    shadow = shadow,
                    recurringAnimation = recurringAnimation,
                    recurringPhase = recurringPhase,
                    shimmerColor = shimmerColor,
                    isRtl = isRtl,
                )
            },
    ) {
        if (perSegmentAccessibility) {
            SegmentSemanticsOverlay(
                divisions = divisions,
                enabledSegments = enabledSegments,
                onSegmentClick = onSegmentClick,
            )
        }
    }
}

/**
 * One invisible, zero-drawing child per segment, carrying that segment's
 * semantics: the Compose counterpart of the View's virtual accessibility
 * hierarchy.
 *
 * A [Layout] rather than offset boxes so each node's bounds are exactly its
 * cell, gaps included, with no dead zones while exploring by touch. Placement
 * uses `placeRelative`, so RTL mirroring matches the drawing for free.
 */
@Composable
private fun BoxScope.SegmentSemanticsOverlay(
    divisions: Int,
    enabledSegments: Set<Int>,
    onSegmentClick: ((Int) -> Unit)?,
) {
    Layout(
        modifier = Modifier.matchParentSize(),
        content = {
            repeat(divisions) { index ->
                Box(
                    Modifier.semantics {
                        contentDescription = "Segment ${index + 1} of $divisions"
                        role = Role.Switch
                        toggleableState = if (index in enabledSegments) {
                            ToggleableState.On
                        } else {
                            ToggleableState.Off
                        }
                        if (onSegmentClick != null) {
                            onClick {
                                onSegmentClick(index)
                                true
                            }
                        }
                    },
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val placeables = measurables.mapIndexed { index, measurable ->
            val start = SegmentGeometry.boundary(width.toFloat(), divisions, index).toInt()
            val end = SegmentGeometry.boundary(width.toFloat(), divisions, index + 1).toInt()
            measurable.measure(
                androidx.compose.ui.unit.Constraints.fixed(
                    (end - start).coerceAtLeast(0),
                    height,
                ),
            ) to start
        }
        layout(width, height) {
            placeables.forEach { (placeable, start) -> placeable.placeRelative(start, 0) }
        }
    }
}

/**
 * The exact 2.0.0 signature, kept so code compiled against 2.0.0 still links.
 *
 * Adding a parameter to a Kotlin function with default arguments changes its
 * JVM signature, which silently breaks binary compatibility even though every
 * call site still compiles. Hidden rather than merely deprecated so the
 * compiler never resolves new code to it; it exists only for old binaries.
 */
@Deprecated("Binary compatibility bridge for 2.0.0 callers", level = DeprecationLevel.HIDDEN)
@Composable
@Suppress("LongParameterList")
public fun SegmentedProgressBar(
    divisions: Int,
    enabledSegments: Set<Int>,
    modifier: Modifier = Modifier,
    onColor: Color = SegmentedProgressBarDefaults.OnColor,
    offColor: Color = SegmentedProgressBarDefaults.OffColor,
    gap: Dp = SegmentedProgressBarDefaults.Gap,
    gapColor: Color = Color.Transparent,
    cornerRadius: Dp = SegmentedProgressBarDefaults.CornerRadius,
    cornerMode: CornerMode = CornerMode.BAR_ENDS,
    activeHeightFraction: Float = 1f,
    inactiveHeightFraction: Float = 1f,
    shadow: SegmentShadow? = null,
    segmentAnimation: SegmentAnimation = SegmentAnimation.NONE,
    entryAnimation: EntryAnimation = EntryAnimation.NONE,
    recurringAnimation: RecurringAnimation = RecurringAnimation.NONE,
    animationDurationMillis: Int = SegmentedProgressBarDefaults.AnimationDurationMillis,
    entryStaggerDelayMillis: Int = SegmentedProgressBarDefaults.EntryStaggerDelayMillis,
    recurringDurationMillis: Int = SegmentedProgressBarDefaults.RecurringDurationMillis,
    shimmerColor: Color = SegmentedProgressBarDefaults.ShimmerColor,
    contentDescription: String? = null,
    onSegmentClick: ((Int) -> Unit)? = null,
) {
    SegmentedProgressBar(
        divisions = divisions,
        enabledSegments = enabledSegments,
        modifier = modifier,
        onColor = onColor,
        offColor = offColor,
        gap = gap,
        gapColor = gapColor,
        cornerRadius = cornerRadius,
        cornerMode = cornerMode,
        activeHeightFraction = activeHeightFraction,
        inactiveHeightFraction = inactiveHeightFraction,
        shadow = shadow,
        segmentAnimation = segmentAnimation,
        entryAnimation = entryAnimation,
        recurringAnimation = recurringAnimation,
        animationDurationMillis = animationDurationMillis,
        entryStaggerDelayMillis = entryStaggerDelayMillis,
        recurringDurationMillis = recurringDurationMillis,
        shimmerColor = shimmerColor,
        contentDescription = contentDescription,
        onSegmentClick = onSegmentClick,
    )
}

/**
 * A drop shadow cast by the bar.
 *
 * Drawn outside the bar, so it never changes the bar's size or position. Compose
 * does not clip drawing to a composable's bounds, so the shadow simply overflows;
 * only an ancestor that clips will cut it off.
 *
 * It follows the bar's silhouette: one shadow per segment, drawn behind
 * everything and never inside the bar, so no segment comes out darker than its
 * neighbour and no cell is outlined by the shadow of the cell next to it.
 * [target] chooses which segments contribute.
 */
public data class SegmentShadow(
    val radius: Dp,
    val dx: Dp = 0.dp,
    val dy: Dp = 0.dp,
    val color: Color = SegmentedProgressBarDefaults.ShadowColor,
    val target: ShadowTarget = ShadowTarget.ALL,
) {
    init {
        require(radius.value >= 0f) { "shadow radius must be >= 0 but was $radius" }
    }
}

/** Defaults matching the View implementation, so the two look the same. */
public object SegmentedProgressBarDefaults {
    /** `#5097E2`, the View's default on colour. */
    public val OnColor: Color = Color(0xFF5097E2)

    /** `#C1C1C1`, the View's default off colour. */
    public val OffColor: Color = Color(0xFFC1C1C1)

    /** 25% black. */
    public val ShadowColor: Color = Color(0x40000000)

    /** 45% white. */
    public val ShimmerColor: Color = Color(0x73FFFFFF)

    /** Compose defaults to a real gap rather than the View's 1px divider line. */
    public val Gap: Dp = 2.dp

    public val CornerRadius: Dp = 2.dp

    public val IntrinsicWidth: Dp = 144.dp

    public val IntrinsicHeight: Dp = 8.dp

    public const val AnimationDurationMillis: Int = 200

    public const val EntryStaggerDelayMillis: Int = 60

    public const val RecurringDurationMillis: Int = 1600
}

/** Returned by the internal hit test when a tap misses the bar. */
private const val NoSegment = -1

/**
 * Maps a tap position to a segment index, or [NoSegment] outside the bar.
 *
 * Mirrors under RTL for the same reason the View does: segment `0` is at the
 * right-hand end there, and a naive `x / width * divisions` would toggle the
 * mirror image of what the user touched.
 */
private fun segmentIndexAt(x: Float, width: Float, divisions: Int, isRtl: Boolean): Int {
    if (width <= 0f) return NoSegment
    if (x < 0f || x >= width) return NoSegment
    val fromStart = if (isRtl) SegmentGeometry.mirror(width, x) else x
    return (fromStart / width * divisions).toInt().coerceIn(0, divisions - 1)
}

// region animation

/**
 * Per-segment progress towards being on, plus the style currently driving it.
 *
 * Holding the style here rather than deriving it at draw time is what keeps the
 * entry animation and the toggle animation from applying at the same time.
 * Reading both parameters in the draw pass, as an earlier version did, made a
 * FADE toggle and a GROW entry both take effect at once.
 */
private class SegmentAnimationState(initialStyle: SegmentAnimation) {
    val fractions: SnapshotStateMap<Int, Float> = mutableStateMapOf()
    var style: SegmentAnimation by mutableStateOf(initialStyle)
}

/** The transition style an entry animation implies. */
private fun EntryAnimation.transitionStyle(): SegmentAnimation = when (this) {
    EntryAnimation.NONE -> SegmentAnimation.NONE
    EntryAnimation.GROW -> SegmentAnimation.GROW
    EntryAnimation.FADE, EntryAnimation.STAGGER -> SegmentAnimation.FADE
}

@Composable
private fun rememberSegmentAnimation(
    divisions: Int,
    enabledSegments: Set<Int>,
    segmentAnimation: SegmentAnimation,
    entryAnimation: EntryAnimation,
    animationDurationMillis: Int,
    entryStaggerDelayMillis: Int,
    segmentProgress: Map<Int, Float>,
): SegmentAnimationState {
    val state = remember {
        SegmentAnimationState(entryAnimation.transitionStyle()).also { fresh ->
            // With no entry animation the initial state has to be on screen from
            // the very first frame, not one frame later once the effect runs.
            if (entryAnimation == EntryAnimation.NONE || animationDurationMillis <= 0) {
                for (index in 0 until divisions) {
                    fresh.fractions[index] = if (index in enabledSegments) 1f else 0f
                }
            }
        }
    }
    val isFirstPass = remember { booleanArrayOf(true) }

    // Read at launch time rather than keyed: partial fills change continuously
    // (a playing story updates every frame) and must not restart in-flight
    // transitions. The value only matters at the moment a segment becomes
    // enabled, to seed its transition from wherever its fill had got to.
    val latestProgress by rememberUpdatedState(segmentProgress)

    LaunchedEffect(
        divisions,
        enabledSegments,
        segmentAnimation,
        entryAnimation,
        animationDurationMillis,
        entryStaggerDelayMillis,
    ) {
        val isEntry = isFirstPass[0]
        isFirstPass[0] = false

        // Exactly one style is ever in effect: the entry style on the first pass,
        // the toggle style for every change after that.
        val style = if (isEntry) entryAnimation.transitionStyle() else segmentAnimation
        state.style = style

        if (style == SegmentAnimation.NONE || animationDurationMillis <= 0) {
            for (index in 0 until divisions) {
                state.fractions[index] = if (index in enabledSegments) 1f else 0f
            }
            return@LaunchedEffect
        }

        animateSegments(
            divisions = divisions,
            enabledSegments = enabledSegments,
            fractions = state.fractions,
            durationMillis = animationDurationMillis,
            staggerMillis = if (isEntry && entryAnimation == EntryAnimation.STAGGER) {
                entryStaggerDelayMillis
            } else {
                0
            },
            // A segment that was partially filled grows on from its fill rather
            // than restarting at zero.
            floorOf = { index -> (latestProgress[index] ?: 0f).coerceIn(0f, 1f) },
        )
    }

    return state
}

/**
 * Drives every segment towards its target.
 *
 * Each segment gets its own coroutine so a stagger is expressed as a plain delay,
 * and so an interrupted transition is cancelled and restarted by [LaunchedEffect]
 * rather than needing manual bookkeeping.
 */
private suspend fun animateSegments(
    divisions: Int,
    enabledSegments: Set<Int>,
    fractions: SnapshotStateMap<Int, Float>,
    durationMillis: Int,
    staggerMillis: Int,
    floorOf: (Int) -> Float = { 0f },
) {
    coroutineScope {
        var staggerIndex = 0
        for (index in 0 until divisions) {
            val target = if (index in enabledSegments) 1f else 0f
            val from = if (target == 1f) {
                maxOf(fractions[index] ?: 0f, floorOf(index))
            } else {
                fractions[index] ?: 0f
            }
            if (from == target) {
                fractions[index] = target
                continue
            }
            val delayMillis = if (target == 1f) staggerMillis * staggerIndex++ else 0
            launch {
                if (delayMillis > 0) delay(delayMillis.toLong())
                Animatable(from).animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis),
                ) {
                    fractions[index] = value
                }
            }
        }
    }
}

/** Phase of the recurring loop, `0` to `1`, or `0` when nothing is running. */
@Composable
private fun rememberRecurringPhase(
    recurringAnimation: RecurringAnimation,
    recurringDurationMillis: Int,
): Float {
    if (recurringAnimation == RecurringAnimation.NONE) return 0f
    val transition = rememberInfiniteTransition(label = "spb-recurring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(recurringDurationMillis, easing = LinearEasing),
        ),
        label = "spb-recurring-phase",
    )
    return phase
}

// endregion

private const val ShimmerBand = 0.45f
private const val PulseMinAlpha = 0.45f

/** Passes used to approximate a blur, see [drawSoftShadow]. */
private const val ShadowSteps = 8

/**
 * Draws the bar.
 *
 * Deliberately a mirror of the View's `onDraw`, using the same [SegmentGeometry]
 * calls in the same order, so a change to the layout maths lands in both
 * renderers at once.
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun DrawScope.drawSegmentedBar(
    divisions: Int,
    fractionOf: (Int) -> Float,
    partialOf: (Int) -> Float,
    transitionStyle: SegmentAnimation,
    onColorOf: (Int) -> Color,
    offColor: Color,
    gapPx: Float,
    gapColor: Color,
    cornerRadiusPx: Float,
    cornerMode: CornerMode,
    onSegments: Set<Int>,
    activeHeightFraction: Float,
    inactiveHeightFraction: Float,
    shadow: SegmentShadow?,
    recurringAnimation: RecurringAnimation,
    recurringPhase: Float,
    shimmerColor: Color,
    isRtl: Boolean,
) {
    // The bar always occupies the whole box. A shadow is drawn outside it and
    // never insets it, so enabling one, or changing its blur or offset, cannot
    // move or resize the bar.
    val barWidth = size.width
    val barHeight = size.height
    if (barWidth <= 0f || barHeight <= 0f) return

    val gapSpan = SegmentGeometry.effectiveDividerWidth(
        width = barWidth,
        divisions = divisions,
        requested = gapPx,
        enabled = gapPx > 0f,
    )

    val trackHeight = barHeight * inactiveHeightFraction
    val trackTop = (barHeight - trackHeight) / 2f
    val segmentHeight = barHeight * activeHeightFraction
    val segmentTop = (barHeight - segmentHeight) / 2f

    val trackRadius = SegmentGeometry.clampCornerRadius(cornerRadiusPx, barWidth, trackHeight)
    val segmentRadius = SegmentGeometry.clampCornerRadius(cornerRadiusPx, barWidth, segmentHeight)

    fun spanOf(index: Int): Pair<Float, Float> {
        var left = SegmentGeometry.segmentLeft(barWidth, divisions, gapSpan, index)
        var right = SegmentGeometry.segmentRight(barWidth, divisions, gapSpan, index)
        if (isRtl) {
            val mirrored = SegmentGeometry.mirror(barWidth, right)
            right = SegmentGeometry.mirror(barWidth, left)
            left = mirrored
        }
        return left to right
    }

    /** Applies a GROW transition, shortening the span from its trailing edge. */
    fun grownSpan(span: Pair<Float, Float>, fraction: Float): Pair<Float, Float> {
        if (transitionStyle != SegmentAnimation.GROW || fraction >= 1f) return span
        val (left, right) = span
        val width = (right - left) * fraction
        return if (isRtl) (right - width) to right else left to (left + width)
    }

    fun cornersOf(index: Int, forSegment: Boolean): Pair<Boolean, Boolean> {
        val roundsStart: Boolean
        val roundsEnd: Boolean
        // A track cell with a segment over it takes that segment's rounding.
        // Otherwise, under EACH_RUN, the square corner of the rail showed through
        // the rounded corner at the end of a run.
        val followsRun = cornerMode == CornerMode.EACH_RUN &&
            (forSegment || index in onSegments)
        when {
            cornerMode == CornerMode.EACH_SEGMENT -> {
                roundsStart = true
                roundsEnd = true
            }
            followsRun -> {
                roundsStart = index == 0 || (index - 1) !in onSegments
                roundsEnd = index == divisions - 1 || (index + 1) !in onSegments
            }
            else -> {
                roundsStart = index == 0
                roundsEnd = index == divisions - 1
            }
        }
        return if (isRtl) roundsEnd to roundsStart else roundsStart to roundsEnd
    }

    val hasTrack = trackHeight > 0f && offColor.alpha > 0f
    val hasSegments = segmentHeight > 0f

    /**
     * Which sides of segment [index] would be rounded if it stood alone.
     *
     * A partial fill uses this rather than [cornersOf]: an in-progress segment
     * is not part of the run beside it, so it draws as its own pill.
     */
    fun standaloneCornersOf(index: Int): Pair<Boolean, Boolean> {
        val roundsStart: Boolean
        val roundsEnd: Boolean
        when (cornerMode) {
            CornerMode.EACH_SEGMENT, CornerMode.EACH_RUN -> {
                roundsStart = true
                roundsEnd = true
            }
            CornerMode.BAR_ENDS -> {
                roundsStart = index == 0
                roundsEnd = index == divisions - 1
            }
        }
        return if (isRtl) roundsEnd to roundsStart else roundsStart to roundsEnd
    }

    /** The on colour for [index], tinted by whatever recurring animation runs. */
    fun animatedOnColor(index: Int): Color {
        var color = onColorOf(index)
        when (recurringAnimation) {
            RecurringAnimation.SHIMMER -> {
                val head = -ShimmerBand + recurringPhase * (1f + 2f * ShimmerBand)
                val centre = (index + 0.5f) / divisions
                val intensity = (1f - abs(centre - head) / ShimmerBand).coerceIn(0f, 1f)
                color = lerp(color, shimmerColor.copy(alpha = 1f), intensity * shimmerColor.alpha)
            }
            RecurringAnimation.PULSE -> {
                val wave = (1f + sin(recurringPhase * 2f * Math.PI.toFloat())) / 2f
                color = color.copy(
                    alpha = color.alpha * (PulseMinAlpha + (1f - PulseMinAlpha) * wave),
                )
            }
            RecurringAnimation.NONE -> Unit
        }
        return color
    }

    // The bar casts one shadow, shaped like its silhouette, drawn before any fill.
    // See drawSoftShadow for why it is built as a single shape per pass rather than
    // one per cell, and why the outline is clipped out of it.
    if (shadow != null && shadow.radius.value > 0f) {
        val wantsOff = shadow.target == ShadowTarget.OFF_SEGMENTS ||
            shadow.target == ShadowTarget.ALL
        val wantsOn = shadow.target == ShadowTarget.ON_SEGMENTS ||
            shadow.target == ShadowTarget.ALL

        // For gap bridging: a partial cell follows the off target, because only
        // its rail spans the whole cell; its fill never reaches the gap.
        fun castsShadow(index: Int): Boolean =
            if (fractionOf(index) > 0f) wantsOn else wantsOff && hasTrack

        /**
         * Builds the shadow's caster into [path], grown by [spread] on every side.
         *
         * At `spread` 0 this is the bar's outline; the blur is approximated by
         * stacking larger copies of it. Passing `casters = false` builds the whole
         * outline instead of only the part the target selects, which is what the
         * clip needs.
         *
         * Fills a path the caller owns, and adds shapes straight to it rather than
         * building one per span, so that stacking [ShadowSteps] copies of a
         * ten-segment bar does not allocate a hundred paths a frame.
         */
        fun outlineInto(path: Path, spread: Float, casters: Boolean): Path {
            path.reset()
            var bandTop = Float.MAX_VALUE
            var bandBottom = -Float.MAX_VALUE
            for (index in 0 until divisions) {
                val fraction = fractionOf(index)
                val isLit = fraction > 0f
                val partial = if (isLit) 0f else partialOf(index)
                val (cellLeft, cellRight) = spanOf(index)
                if (cellRight - cellLeft <= 0f) continue

                // A lit cell's rail is covered by on-coloured content, so it
                // follows the on target; the rail of a partial or off cell is
                // off-coloured and follows the off target.
                if (hasTrack && (!casters || (if (isLit) wantsOn else wantsOff))) {
                    val (roundLeft, roundRight) = cornersOf(index, forSegment = false)
                    path.addRoundRect(
                        roundRectOf(
                            cellLeft - spread, cellRight + spread,
                            trackTop - spread, trackHeight + spread * 2f,
                            roundLeft, roundRight, grownRadius(trackRadius, spread),
                        ),
                    )
                    bandTop = minOf(bandTop, trackTop)
                    bandBottom = maxOf(bandBottom, trackTop + trackHeight)
                }
                if (hasSegments && isLit && (!casters || wantsOn)) {
                    val (left, right) = grownSpan(cellLeft to cellRight, fraction)
                    if (right - left > 0f) {
                        val (roundLeft, roundRight) = cornersOf(index, forSegment = true)
                        path.addRoundRect(
                            roundRectOf(
                                left - spread, right + spread,
                                segmentTop - spread, segmentHeight + spread * 2f,
                                roundLeft, roundRight, grownRadius(segmentRadius, spread),
                            ),
                        )
                        bandTop = minOf(bandTop, segmentTop)
                        bandBottom = maxOf(bandBottom, segmentTop + segmentHeight)
                    }
                }
                // A partial fill contributes the shape it actually draws, so a
                // shadow can neither land beneath a translucent fill nor go
                // missing over a transparent track. As on-coloured content it
                // follows the on target.
                if (hasSegments && partial > 0f && (!casters || wantsOn)) {
                    val width = (cellRight - cellLeft) * partial
                    val left = if (isRtl) cellRight - width else cellLeft
                    val right = left + width
                    val (roundLeft, roundRight) = standaloneCornersOf(index)
                    path.addRoundRect(
                        roundRectOf(
                            left - spread, right + spread,
                            segmentTop - spread, segmentHeight + spread * 2f,
                            roundLeft, roundRight, grownRadius(segmentRadius, spread),
                        ),
                    )
                    bandTop = minOf(bandTop, segmentTop)
                    bandBottom = maxOf(bandBottom, segmentTop + segmentHeight)
                }
            }

            // The gaps belong to the outline too, so that no shadow is drawn
            // between two segments: a narrow gap otherwise fills in with blur from
            // both sides and becomes the divider line EACH_RUN exists to remove. In
            // the caster, only gaps between two contributing cells are bridged, so
            // a run reads as one shape while a cell the target leaves out still
            // breaks it.
            if (gapSpan > 0f && bandBottom > bandTop) {
                for (index in 1 until divisions) {
                    if (casters && !(castsShadow(index - 1) && castsShadow(index))) continue
                    path.addRect(
                        Rect(
                            SegmentGeometry.dividerLeft(barWidth, divisions, gapSpan, index),
                            bandTop - spread,
                            SegmentGeometry.dividerRight(barWidth, divisions, gapSpan, index),
                            bandBottom + spread,
                        ),
                    )
                }
            }
            return path
        }

        val caster = Path()
        clipPath(outlineInto(Path(), spread = 0f, casters = false), ClipOp.Difference) {
            drawSoftShadow(shadow) { spread -> outlineInto(caster, spread, casters = true) }
        }
    }

    // Off segments, cell by cell, so the gap is genuinely empty.
    if (hasTrack) {
        for (index in 0 until divisions) {
            val (left, right) = spanOf(index)
            if (right - left <= 0f) continue
            val (roundLeft, roundRight) = cornersOf(index, forSegment = false)
            drawSpan(
                left, right, trackTop, trackHeight,
                roundLeft, roundRight, trackRadius, offColor,
            )
        }
    }

    // On segments, and the partial fill of any segment that is underway.
    if (hasSegments) {
        for (index in 0 until divisions) {
            val fraction = fractionOf(index)
            if (fraction <= 0f) {
                val fill = partialOf(index)
                if (fill <= 0f) continue

                val (cellLeft, cellRight) = spanOf(index)
                if (cellRight - cellLeft <= 0f) continue

                // The fill covers the leading part of the cell, clipped to the
                // shape the cell would have as a standalone lit segment. The
                // clip is what keeps every corner mode honest: without it, a
                // fill approaching 1 under a large radius pokes its square cut
                // edge out past the cell's rounded silhouette.
                val (roundLeft, roundRight) = standaloneCornersOf(index)
                val cellShape = Path().apply {
                    addRoundRect(
                        roundRectOf(
                            cellLeft, cellRight, segmentTop, segmentHeight,
                            roundLeft, roundRight, segmentRadius,
                        ),
                    )
                }
                val width = (cellRight - cellLeft) * fill
                val fillLeft = if (isRtl) cellRight - width else cellLeft
                clipPath(cellShape) {
                    drawRect(
                        color = animatedOnColor(index),
                        topLeft = Offset(fillLeft, segmentTop),
                        size = Size(width, segmentHeight),
                    )
                }
                continue
            }

            val (left, right) = grownSpan(spanOf(index), fraction)
            if (right - left <= 0f) continue

            var color = animatedOnColor(index)
            if (transitionStyle == SegmentAnimation.FADE && fraction < 1f) {
                color = color.copy(alpha = color.alpha * fraction)
            }

            val (roundLeft, roundRight) = cornersOf(index, forSegment = true)
            drawSpan(
                left, right, segmentTop, segmentHeight,
                roundLeft, roundRight, segmentRadius, color,
            )
        }
    }

    // A painted divider, only when the gap has been given a colour.
    if (gapSpan > 0f && gapColor.alpha > 0f) {
        val top = minOf(trackTop, segmentTop)
        val bottom = maxOf(trackTop + trackHeight, segmentTop + segmentHeight)
        for (index in 1 until divisions) {
            val left = SegmentGeometry.dividerLeft(barWidth, divisions, gapSpan, index)
            val right = SegmentGeometry.dividerRight(barWidth, divisions, gapSpan, index)
            drawRect(
                color = gapColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
            )
        }
    }
}

/**
 * Approximates a blurred shadow by stacking grown copies of [outlineAt].
 *
 * Compose has no `Paint` shadow layer, and the obvious alternative, a
 * `graphicsLayer` carrying a `BlurEffect`, clips the blur to the layer's bounds.
 * That both forced the bar to be inset and left a visible pale seam where the blur
 * faded out against the layer edge. Stacked outsets have neither problem, need no
 * API-level gate, and can overflow the composable freely.
 *
 * Each of the [ShadowSteps] passes carries `1 / ShadowSteps` of the shadow's alpha,
 * so where all of them overlap, right at the shape, the accumulated opacity is the
 * shadow colour's own, falling off to nothing at the full blur distance.
 *
 * Two rules the caller has to hold up, both of which were visible bugs before they
 * were understood:
 *
 * 1. [outlineAt] must return the *whole* caster as one path, not one shape per
 *    call. Within a pass, overlapping shapes in a single path fill as their union,
 *    so nothing can accumulate; drawn as separate paths, they add, which made a lit
 *    segment twice as dark as an unlit one and grew a dark tick above and below
 *    every gap where two neighbouring blurs met.
 * 2. This has to run inside a clip that excludes the bar's outline, since the
 *    passes are filled shapes. The stack covers its own footprint at full shadow
 *    alpha, which showed through every anti-aliased fill edge as a dark outline,
 *    and through anything translucent as dirt.
 */
private fun DrawScope.drawSoftShadow(
    shadow: SegmentShadow,
    outlineAt: (spread: Float) -> Path,
) {
    val blur = shadow.radius.toPx()
    if (blur <= 0f) return
    val stepAlpha = shadow.color.alpha / ShadowSteps
    if (stepAlpha <= 0f) return
    val color = shadow.color.copy(alpha = stepAlpha)

    translate(left = shadow.dx.toPx(), top = shadow.dy.toPx()) {
        for (step in ShadowSteps downTo 1) {
            drawPath(outlineAt(blur * step / ShadowSteps), color)
        }
    }
}

/** A corner radius grown along with the shape it belongs to. */
private fun grownRadius(radius: Float, spread: Float): Float =
    if (radius > 0f) radius + spread else 0f

/** Draws one span, rounding only the requested sides. */
@Suppress("LongParameterList")
private fun DrawScope.drawSpan(
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    roundLeft: Boolean,
    roundRight: Boolean,
    radius: Float,
    color: Color,
) {
    val shape = roundRectOf(left, right, top, height, roundLeft, roundRight, radius)
    if (shape.topLeftCornerRadius == CornerRadius.Zero &&
        shape.topRightCornerRadius == CornerRadius.Zero
    ) {
        drawRect(color = color, topLeft = Offset(left, top), size = Size(right - left, height))
        return
    }
    drawPath(Path().apply { addRoundRect(shape) }, color)
}

/**
 * The rounded rectangle for a span, rounding only the requested sides.
 *
 * Shared with the shadow pass so that the silhouette it clips out is exactly the
 * shape that gets drawn. Rounding the clip any other way would leave a sliver of
 * the bar unclipped, and a shadow inside the bar is what the clip exists to
 * prevent.
 *
 * A single-sided radius may be as large as the span is wide, but never more than
 * half the height, or the arcs would overlap vertically.
 */
@Suppress("LongParameterList")
private fun roundRectOf(
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    roundLeft: Boolean,
    roundRight: Boolean,
    radius: Float,
): RoundRect {
    val effectiveRadius = if (radius <= 0f) 0f else minOf(radius, right - left, height / 2f)
    val leftCorner = if (roundLeft) CornerRadius(effectiveRadius) else CornerRadius.Zero
    val rightCorner = if (roundRight) CornerRadius(effectiveRadius) else CornerRadius.Zero
    return RoundRect(
        rect = Rect(left, top, right, top + height),
        topLeft = leftCorner,
        topRight = rightCorner,
        bottomRight = rightCorner,
        bottomLeft = leftCorner,
    )
}

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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
 *   one advances through a fraction. Any number of segments can carry one. A
 *   segment that is already in [enabledSegments] ignores its entry here. Every
 *   [cornerMode] shapes the fill correctly: under [CornerMode.EACH_RUN] it
 *   continues the run beside it, joining it squarely while the fill's moving
 *   edge carries the run's rounded end; the other modes clip the fill to the
 *   cell's own shape. Values are clamped to `0..1`, and change with no
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
            // drawWithCache, not drawBehind: the geometry and the shadow's
            // paths are built here, at cache-build time, and survive across
            // frames. State read during the build (the animation fractions)
            // invalidates the cache exactly when the silhouette changes; the
            // recurring phase is read only inside the draw pass below, so a
            // shimmer redraws without rebuilding a single path.
            .drawWithCache {
                val renderer = BarRenderer(
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
                    recurringAnimation = recurringAnimation,
                    shimmerColor = shimmerColor,
                    isRtl = isRtl,
                    size = size,
                )
                val shadowRender = renderer.buildShadow(
                    shadow = shadow,
                    blurPx = shadow?.radius?.toPx() ?: 0f,
                    dxPx = shadow?.dx?.toPx() ?: 0f,
                    dyPx = shadow?.dy?.toPx() ?: 0f,
                )
                onDrawBehind {
                    shadowRender?.drawInto(this)
                    with(renderer) { drawBar(recurringPhase.value) }
                }
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

/**
 * Phase of the recurring loop, `0` to `1`, or a constant `0` when nothing runs.
 *
 * Returned as [State] rather than a value on purpose: reading the phase during
 * composition would recompose the whole bar sixty times a second for as long
 * as a shimmer runs. Read only inside the draw pass, a phase change costs a
 * redraw and nothing else.
 */
@Composable
private fun rememberRecurringPhase(
    recurringAnimation: RecurringAnimation,
    recurringDurationMillis: Int,
): State<Float> {
    if (recurringAnimation == RecurringAnimation.NONE) {
        return remember { mutableFloatStateOf(0f) }
    }
    val transition = rememberInfiniteTransition(label = "spb-recurring")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(recurringDurationMillis, easing = LinearEasing),
        ),
        label = "spb-recurring-phase",
    )
}

// endregion

private const val ShimmerBand = 0.45f
private const val PulseMinAlpha = 0.45f

/** Passes used to approximate a blur, see [drawSoftShadow]. */
private const val ShadowSteps = 8

/**
 * Everything needed to draw the bar at one size, built once per draw cache.
 *
 * Deliberately a mirror of the View's `onDraw`, using the same [SegmentGeometry]
 * calls in the same order, so a change to the layout maths lands in both
 * renderers at once.
 *
 * A class rather than one long draw function so that [buildShadow] can run at
 * `drawWithCache` build time, where its paths survive across frames, while
 * [drawBar] runs per frame. The split is what lets a shimmer redraw sixty times
 * a second without rebuilding a single shadow path: the phase only tints fill
 * colours, never shapes.
 */
@Suppress("LongParameterList")
private class BarRenderer(
    private val divisions: Int,
    private val fractionOf: (Int) -> Float,
    private val partialOf: (Int) -> Float,
    private val transitionStyle: SegmentAnimation,
    private val onColorOf: (Int) -> Color,
    private val offColor: Color,
    gapPx: Float,
    private val gapColor: Color,
    cornerRadiusPx: Float,
    private val cornerMode: CornerMode,
    private val onSegments: Set<Int>,
    activeHeightFraction: Float,
    inactiveHeightFraction: Float,
    private val recurringAnimation: RecurringAnimation,
    private val shimmerColor: Color,
    private val isRtl: Boolean,
    size: Size,
) {
    // The bar always occupies the whole box. A shadow is drawn outside it and
    // never insets it, so enabling one, or changing its blur or offset, cannot
    // move or resize the bar.
    private val barWidth = size.width
    private val barHeight = size.height

    private val gapSpan = SegmentGeometry.effectiveDividerWidth(
        width = barWidth,
        divisions = divisions,
        requested = gapPx,
        enabled = gapPx > 0f,
    )

    private val trackHeight = barHeight * inactiveHeightFraction
    private val trackTop = (barHeight - trackHeight) / 2f
    private val segmentHeight = barHeight * activeHeightFraction
    private val segmentTop = (barHeight - segmentHeight) / 2f

    private val trackRadius =
        SegmentGeometry.clampCornerRadius(cornerRadiusPx, barWidth, trackHeight)
    private val segmentRadius =
        SegmentGeometry.clampCornerRadius(cornerRadiusPx, barWidth, segmentHeight)

    private val hasTrack = trackHeight > 0f && offColor.alpha > 0f
    private val hasSegments = segmentHeight > 0f

    private fun spanOf(index: Int): Pair<Float, Float> {
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
    private fun grownSpan(span: Pair<Float, Float>, fraction: Float): Pair<Float, Float> {
        if (transitionStyle != SegmentAnimation.GROW || fraction >= 1f) return span
        val (left, right) = span
        val width = (right - left) * fraction
        return if (isRtl) (right - width) to right else left to (left + width)
    }

    private fun cornersOf(index: Int, forSegment: Boolean): Pair<Boolean, Boolean> {
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
                // A partial fill counts on the trailing side: the run flows
                // squarely into the segment that continues it, and the fill's
                // moving edge carries the run's rounded end instead. It does
                // not count on the leading side, because a fill below 1 never
                // reaches the boundary.
                roundsEnd = index == divisions - 1 ||
                    ((index + 1) !in onSegments && partialOf(index + 1) <= 0f)
            }
            cornerMode == CornerMode.EACH_RUN && partialOf(index) > 0f -> {
                // The rail under a partial fill gets the run exception on its
                // leading side too, where the fill's shape starts; the trailing
                // side keeps the rail rule, because the visible remainder of
                // the cell is plain track.
                roundsStart = index == 0 || (index - 1) !in onSegments
                roundsEnd = index == divisions - 1
            }
            else -> {
                roundsStart = index == 0
                roundsEnd = index == divisions - 1
            }
        }
        return if (isRtl) roundsEnd to roundsStart else roundsStart to roundsEnd
    }

    /**
     * Which sides of the partial fill at [index] are rounded.
     *
     * Under [CornerMode.EACH_RUN] the fill continues the run beside it: the
     * joint with a full segment before it is square, and the moving edge
     * always carries the run's rounded end. With nothing full before it, the
     * fill is its own in-progress pill. The other modes use the standalone
     * cell shape the fill is clipped to.
     */
    private fun partialCornersOf(index: Int): Pair<Boolean, Boolean> {
        if (cornerMode != CornerMode.EACH_RUN) return standaloneCornersOf(index)
        val roundsStart = index == 0 || (index - 1) !in onSegments
        return if (isRtl) true to roundsStart else roundsStart to true
    }

    /**
     * Which sides of segment [index] would be rounded if it stood alone.
     *
     * The clipped partial fills of [CornerMode.BAR_ENDS] and
     * [CornerMode.EACH_SEGMENT] take the cell shape from here;
     * [CornerMode.EACH_RUN] fills use [partialCornersOf] instead.
     */
    private fun standaloneCornersOf(index: Int): Pair<Boolean, Boolean> {
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
    private fun animatedOnColor(index: Int, recurringPhase: Float): Color {
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

    /**
     * Builds the bar's shadow as retained paths: the outline to clip out, and
     * one grown copy of the caster per blur step.
     *
     * The bar casts one shadow, shaped like its silhouette. Within a pass,
     * overlapping shapes in one path fill as their union, so nothing can
     * accumulate; drawn as separate paths they would add, which made a lit
     * segment twice as dark as an unlit one and grew a dark tick over every
     * gap. The outline is clipped out at draw time because the steps are
     * filled shapes: unclipped, the stack covers its own footprint at full
     * shadow alpha, which showed through anti-aliased fill edges as a dark
     * outline and through anything translucent as dirt.
     *
     * Each of the [ShadowSteps] copies carries `1 / ShadowSteps` of the
     * shadow's alpha, so at the shape the accumulated opacity is the colour's
     * own, falling off to nothing at the full blur distance. Stacked outsets
     * rather than a `BlurEffect` layer, which clips the blur at the layer's
     * bounds; these overflow the composable freely and need no API gate.
     */
    fun buildShadow(shadow: SegmentShadow?, blurPx: Float, dxPx: Float, dyPx: Float): ShadowRender? {
        if (shadow == null || blurPx <= 0f) return null
        if (barWidth <= 0f || barHeight <= 0f) return null
        val stepAlpha = shadow.color.alpha / ShadowSteps
        if (stepAlpha <= 0f) return null

        val wantsOff = shadow.target == ShadowTarget.OFF_SEGMENTS ||
            shadow.target == ShadowTarget.ALL
        val wantsOn = shadow.target == ShadowTarget.ON_SEGMENTS ||
            shadow.target == ShadowTarget.ALL

        // For gap bridging: a lit cell follows the on target. A partial cell
        // contributes both its on-coloured fill and its off-coloured rail, so
        // it casts for either target, which is what lets a run's shadow flow
        // across the gap into the partial segment that continues it.
        fun castsShadow(index: Int): Boolean = when {
            fractionOf(index) > 0f -> wantsOn
            partialOf(index) > 0f -> wantsOn || (wantsOff && hasTrack)
            else -> wantsOff && hasTrack
        }

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
                    val (roundLeft, roundRight) = partialCornersOf(index)
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

        return ShadowRender(
            clip = outlineInto(Path(), spread = 0f, casters = false),
            steps = (ShadowSteps downTo 1).map { step ->
                outlineInto(Path(), spread = blurPx * step / ShadowSteps, casters = true)
            },
            color = shadow.color.copy(alpha = stepAlpha),
            dx = dxPx,
            dy = dyPx,
        )
    }

    /** Draws everything except the shadow, which [ShadowRender.drawInto] blits. */
    fun DrawScope.drawBar(recurringPhase: Float) {
        if (barWidth <= 0f || barHeight <= 0f) return

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

                    val width = (cellRight - cellLeft) * fill
                    val fillLeft = if (isRtl) cellRight - width else cellLeft

                    if (cornerMode == CornerMode.EACH_RUN) {
                        // The exact shape, drawn directly: the moving edge
                        // carries the run's rounded end and the joint with a
                        // full segment before it is square, so the fill
                        // continues the run beside it. At 1 the shape lands
                        // exactly on the cell's silhouette, so no clip is
                        // needed.
                        val (roundLeft, roundRight) = partialCornersOf(index)
                        drawSpan(
                            fillLeft, fillLeft + width, segmentTop, segmentHeight,
                            roundLeft, roundRight, segmentRadius,
                            animatedOnColor(index, recurringPhase),
                        )
                        continue
                    }

                    // BAR_ENDS and EACH_SEGMENT: a straight-edged sweep clipped
                    // to the shape the cell would have as a standalone lit
                    // segment. The clip is what keeps them honest: without it,
                    // a fill approaching 1 under a large radius pokes its
                    // square cut edge out past the cell's rounded silhouette.
                    val (roundLeft, roundRight) = standaloneCornersOf(index)
                    val cellShape = Path().apply {
                        addRoundRect(
                            roundRectOf(
                                cellLeft, cellRight, segmentTop, segmentHeight,
                                roundLeft, roundRight, segmentRadius,
                            ),
                        )
                    }
                    clipPath(cellShape) {
                        drawRect(
                            color = animatedOnColor(index, recurringPhase),
                            topLeft = Offset(fillLeft, segmentTop),
                            size = Size(width, segmentHeight),
                        )
                    }
                    continue
                }

                val (left, right) = grownSpan(spanOf(index), fraction)
                if (right - left <= 0f) continue

                var color = animatedOnColor(index, recurringPhase)
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
}

/**
 * A fully built shadow: paths that live as long as the draw cache, blitted each
 * frame.
 */
private class ShadowRender(
    private val clip: Path,
    private val steps: List<Path>,
    private val color: Color,
    private val dx: Float,
    private val dy: Float,
) {
    fun drawInto(scope: DrawScope): Unit = with(scope) {
        clipPath(clip, ClipOp.Difference) {
            translate(left = dx, top = dy) {
                steps.forEach { drawPath(it, color) }
            }
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

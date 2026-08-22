package com.rachitgoyal.segmented

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.graphics.Rect
import android.view.AbsSavedState
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import androidx.annotation.ColorInt
import androidx.annotation.Px
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A progress bar split into a fixed number of equal segments, where each segment
 * can be turned on and off independently.
 *
 * Unlike [android.widget.ProgressBar], progress here is not a single scalar: any
 * arbitrary subset of segments may be lit at once, which makes the view useful
 * for step indicators, checklist completion, streak calendars and similar
 * "which of these are done?" displays.
 *
 * ### XML usage
 *
 * ```xml
 * <com.rachitgoyal.segmented.SegmentedProgressBar
 *     android:id="@+id/progress"
 *     android:layout_width="match_parent"
 *     android:layout_height="12dp"
 *     app:cornerRadius="6dp"
 *     app:dividerColor="#ffffff"
 *     app:dividerWidth="2dp"
 *     app:divisions="10"
 *     app:isDividerEnabled="true"
 *     app:progressBarBackgroundColor="#dadada"
 *     app:progressBarColor="#ff2d2d" />
 * ```
 *
 * ### Programmatic usage
 *
 * ```kotlin
 * progress.divisions = 10
 *
 * // Any subset, in any order, the reason this view exists.
 * progress.enabledDivisions = listOf(1, 2, 5, 6, 9)
 * progress.toggleDivision(4)
 *
 * // Let the user build the set themselves.
 * progress.setOnDivisionClickListener { bar, index -> bar.toggleDivision(index) }
 * ```
 *
 * ### Validation policy
 *
 * Invalid *configuration*, a [divisions] count below one, a negative
 * [dividerWidth] or [cornerRadius], throws [IllegalArgumentException], because
 * it can only ever be a programming error and silently ignoring it produces a
 * bar that is quietly wrong. Invalid *progress data*, indices in
 * [enabledDivisions] that fall outside the current division count, is tolerated
 * and simply not drawn, because that list is usually derived from live
 * application state.
 *
 * ### Layout direction
 *
 * The view honours `layoutDirection`. Under RTL, segment `0` is drawn at the
 * right-hand end of the bar.
 *
 * ### Thread confinement
 *
 * Like every [View], this class must only be touched from the main thread.
 *
 * ### Extending
 *
 * The class is `open` because the 0.0.1 Java class was implicitly subclassable
 * and making it final would break anyone who relied on that. The framework
 * callbacks it overrides, [onDraw], [onMeasure], [onSaveInstanceState] and so
 * on, stay overridable, but the properties above are deliberately final: a
 * subclass is expected to extend how the bar is drawn or measured, not to
 * redefine what its configuration means.
 */
public open class SegmentedProgressBar @JvmOverloads public constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Carries the drop shadow, and nothing else.
     *
     * A paint of its own rather than a shadow layer on the two fill paints: the
     * shadow is drawn in a pass of its own, before any fill, so that no part of
     * the bar can ever be painted over a shadow. See [drawShadows].
     */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Reused across [onDraw] calls so that drawing allocates nothing. */
    private val scratchRect = RectF()
    private val scratchPath = Path()
    private val scratchRadii = FloatArray(8)

    /** The bar's outline, which the shadow is never drawn inside. */
    private val shadowClipPath = Path()

    /** The part of that outline currently casting the shadow. */
    private val shadowCastPath = Path()

    /**
     * The two vertical bands the bar is drawn in, recomputed at the top of every
     * [onDraw].
     *
     * Long-lived holders rather than parameters, because three of the four draw
     * passes need a band's top, height and radius together and threading nine
     * floats through each signature buries the drawing itself.
     */
    private val trackBand = Band()
    private val segmentBand = Band()

    /** Output of [computeSpan], read by the caller straight afterwards. */
    private var spanLeft = 0f
    private var spanRight = 0f


    /** Sorted, de-duplicated, non-negative. The source of truth for progress. */
    private val enabled = ArrayList<Int>()

    /**
     * Fractional fills for divisions that are partially complete, by index.
     *
     * Kept disjoint from [enabled]: a division is either fully lit (in
     * [enabled]), partially filled (in here, strictly between `0` and `1`), or
     * off (in neither). [setDivisionProgress] maintains the invariant, and
     * [syncAnimationTargets] clears an entry the moment its division becomes
     * fully lit, seeding the transition from the old partial value so a GROW
     * continues the fill rather than restarting it.
     */
    private val partialFills = android.util.SparseArray<Float>()

    /**
     * Per-division overrides of [progressBarColor], by index.
     *
     * Consulted at draw time through [effectiveDivisionColor], so the single
     * global colour stays the primary API and this map only ever narrows it.
     */
    private val divisionColors = android.util.SparseIntArray()

    private var divisionClickListener: OnDivisionClickListener? = null

    /** Installed while [isPerDivisionAccessibilityEnabled]; `null` otherwise. */
    private var divisionTouchHelper: DivisionTouchHelper? = null

    /** Where the in-flight touch went down; `NaN` when there isn't one. */
    private var pendingTouchX = Float.NaN

    private var _divisions: Int = DEFAULT_DIVISIONS
    private var _dividerWidth: Float = DEFAULT_DIVIDER_WIDTH_PX
    private var _cornerRadius: Float = DEFAULT_CORNER_RADIUS_PX
    private var _isDividerEnabled: Boolean = true
    private var _cornerMode: CornerMode = CornerMode.BAR_ENDS
    private var _activeHeightRatio: Float = 1f
    private var _inactiveHeightRatio: Float = 1f
    private var _shadowRadius: Float = 0f
    private var _shadowDx: Float = 0f
    private var _shadowDy: Float = 0f
    private var _shadowColor: Int = DEFAULT_SHADOW_COLOR
    private var _shadowTarget: ShadowTarget = ShadowTarget.ALL
    private var _segmentAnimation: SegmentAnimation = SegmentAnimation.NONE
    private var _animationDurationMs: Long = DEFAULT_ANIMATION_DURATION_MS
    private var _entryAnimation: EntryAnimation = EntryAnimation.NONE
    private var _entryStaggerDelayMs: Long = DEFAULT_ENTRY_STAGGER_DELAY_MS
    private var _recurringAnimation: RecurringAnimation = RecurringAnimation.NONE
    private var _recurringDurationMs: Long = DEFAULT_RECURRING_DURATION_MS
    private var _shimmerColor: Int = DEFAULT_SHIMMER_COLOR
    private var _maxWidth: Int = NO_MAX_SIZE
    private var _maxHeight: Int = NO_MAX_SIZE
    private var _isTapToToggleEnabled: Boolean = false
    private var _isPerDivisionAccessibilityEnabled: Boolean = false

    /**
     * The transition style currently governing segment fractions.
     *
     * Normally [segmentAnimation], but while the entry animation plays it is
     * derived from [entryAnimation] instead, that is what lets a bar animate
     * itself in without also opting into animating every later change.
     */
    private var activeTransitionStyle: SegmentAnimation = SegmentAnimation.NONE

    /** When the recurring loop started; `0` while it is not running. */
    private var recurringStart: Long = 0L

    /**
     * Whether the entry animation has been played.
     *
     * Guarded so it runs once per view rather than on every layout pass, a
     * re-measure or a scroll must not restart it.
     */
    private var hasPlayedEntryAnimation = false

    /**
     * Per-segment animation bookkeeping, indexed by segment.
     *
     * `animTargetLit` is the state each segment is heading towards, `animFrom`
     * the fraction it was at when the current transition began and `animStart`
     * when that was (`0` meaning "settled, no transition"). The current fraction
     * is derived from these on demand rather than cached, so there is no risk of
     * a stale value surviving a configuration change.
     */
    private var animTargetLit = BooleanArray(0)
    private var animFrom = FloatArray(0)
    private var animStart = LongArray(0)

    /** Extra delay before a segment's transition begins, for staggered entry. */
    private var animDelay = LongArray(0)

    /** Scratch space for recomputing lit flags without allocating. */
    private var litScratch = BooleanArray(0)

    private val animationInterpolator = AccelerateDecelerateInterpolator()

    /**
     * The number of equal segments the bar is split into. Must be at least `1`.
     *
     * Defaults to `1`, or to the `divisions` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is less than `1`.
     */
    public var divisions: Int
        get() = _divisions
        set(value) {
            requireDivisions(value)
            if (_divisions == value) return
            _divisions = value
            ensureAnimationCapacity()
            invalidateOutline()
            onSegmentsChanged()
        }

    /**
     * The indices of the segments that are currently lit, as a sorted copy.
     *
     * Indices are zero-based. Assigning sorts the input, drops duplicates and
     * drops negative values; the list is copied, so later mutations of the
     * caller's list have no effect on the view.
     *
     * Indices greater than or equal to [divisions] are *retained but not drawn*.
     * That makes the order in which you assign [divisions] and
     * [enabledDivisions] irrelevant, growing [divisions] later reveals the
     * segments that were previously out of range.
     */
    public var enabledDivisions: List<Int>
        get() = enabled.toList()
        set(value) {
            val sanitized = value.asSequence()
                .filter { it >= 0 }
                .distinct()
                .sorted()
                .toList()
            if (sanitized == enabled) return
            enabled.clear()
            enabled.addAll(sanitized)
            onSegmentsChanged()
        }

    /**
     * Colour of the lit segments.
     *
     * Defaults to `#5097E2`, or to the `progressBarColor` XML attribute.
     */
    @get:ColorInt
    public var progressBarColor: Int
        get() = progressPaint.color
        set(@ColorInt value) {
            if (progressPaint.color == value) return
            progressPaint.color = value
            invalidate()
        }

    /**
     * Colour of the unlit track behind the segments.
     *
     * Defaults to `#C1C1C1`, or to the `progressBarBackgroundColor` XML
     * attribute.
     *
     * This is *not* the view's [View.setBackgroundColor]; it paints only the
     * progress track, inset by the view's padding.
     */
    @get:ColorInt
    public var progressBarBackgroundColor: Int
        get() = backgroundPaint.color
        set(@ColorInt value) {
            if (backgroundPaint.color == value) return
            backgroundPaint.color = value
            invalidate()
        }

    /**
     * Colour of the dividers drawn between segments.
     *
     * Defaults to `#FFFFFF`, or to the `dividerColor` XML attribute. Has no
     * visible effect while [isDividerEnabled] is `false`, [dividerWidth] is `0`
     * or [divisions] is `1`.
     */
    @get:ColorInt
    public var dividerColor: Int
        get() = dividerPaint.color
        set(@ColorInt value) {
            if (dividerPaint.color == value) return
            dividerPaint.color = value
            invalidate()
        }

    /**
     * Width of each divider, in pixels. Must not be negative.
     *
     * Defaults to `1px`, or to the `dividerWidth` XML attribute. Dividers are
     * centred on segment boundaries and segments are inset to make room for
     * them, so segments and dividers never overlap. A value wider than a single
     * segment is clamped at draw time so that segments can never collapse to a
     * negative width.
     *
     * Prefer setting this from XML where you can use `dp`; when setting it in
     * code the value is raw pixels, so convert first:
     * ```kotlin
     * bar.dividerWidth = 2 * resources.displayMetrics.density
     * ```
     *
     * @throws IllegalArgumentException if [value] is negative or not finite.
     */
    @get:Px
    public var dividerWidth: Float
        get() = _dividerWidth
        set(@Px value) {
            requireNonNegativeDimension(value, "dividerWidth")
            if (_dividerWidth == value) return
            _dividerWidth = value
            invalidate()
        }

    /**
     * Whether dividers are drawn between segments.
     *
     * Defaults to `true`, or to the `isDividerEnabled` XML attribute.
     */
    public var isDividerEnabled: Boolean
        get() = _isDividerEnabled
        set(value) {
            if (_isDividerEnabled == value) return
            _isDividerEnabled = value
            invalidate()
        }

    /**
     * Corner radius applied to the two ends of the bar, in pixels. Must not be
     * negative.
     *
     * Defaults to `2px`, or to the `cornerRadius` XML attribute. Only the outer
     * corners of the bar are rounded, interior segment edges stay square, and
     * the radius is clamped to half the bar's smaller dimension so the arcs
     * cannot overlap.
     *
     * @throws IllegalArgumentException if [value] is negative or not finite.
     */
    @get:Px
    public var cornerRadius: Float
        get() = _cornerRadius
        set(@Px value) {
            requireNonNegativeDimension(value, "cornerRadius")
            if (_cornerRadius == value) return
            _cornerRadius = value
            invalidate()
        }

    /**
     * How many segments are currently lit *and* within range of [divisions],
     * that is, how many are actually drawn.
     */
    public val completedSegmentCount: Int
        get() = enabled.count { it < _divisions }

    /**
     * Which edges [cornerRadius] is applied to.
     *
     * Defaults to [CornerMode.BAR_ENDS], or to the `spb_cornerMode` XML
     * attribute.
     */
    public var cornerMode: CornerMode
        get() = _cornerMode
        set(value) {
            if (_cornerMode == value) return
            _cornerMode = value
            invalidateOutline()
            invalidate()
        }

    /**
     * Height of the lit segments as a fraction of the bar's content height,
     * between `0` and `1`. The band is centred vertically.
     *
     * Defaults to `1`, or to the `spb_activeHeightRatio` XML attribute. Pair a
     * value of `1` here with a smaller [inactiveHeightRatio] for the common look
     * where completed segments stand proud of a thinner track.
     *
     * @throws IllegalArgumentException if [value] is outside `0..1`.
     */
    public var activeHeightRatio: Float
        get() = _activeHeightRatio
        set(value) {
            requireRatio(value, "activeHeightRatio")
            if (_activeHeightRatio == value) return
            _activeHeightRatio = value
            invalidate()
        }

    /**
     * Height of the unlit track as a fraction of the bar's content height,
     * between `0` and `1`. The band is centred vertically.
     *
     * Defaults to `1`, or to the `spb_inactiveHeightRatio` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is outside `0..1`.
     */
    public var inactiveHeightRatio: Float
        get() = _inactiveHeightRatio
        set(value) {
            requireRatio(value, "inactiveHeightRatio")
            if (_inactiveHeightRatio == value) return
            _inactiveHeightRatio = value
            invalidateOutline()
            invalidate()
        }

    /**
     * Blur radius of the drop shadow, in pixels. `0` disables the shadow.
     *
     * Defaults to `0`, or to the `spb_shadowRadius` XML attribute.
     *
     * The shadow follows the bar's silhouette: it is drawn once, behind
     * everything, and never inside the bar, so no segment is ever twice as dark
     * as its neighbour and no cell is outlined by the shadow of the cell next to
     * it. [shadowTarget] chooses which segments contribute.
     *
     * Enabling a shadow switches the view to a software layer, because Android
     * ignores [Paint.setShadowLayer] for shapes on a hardware-accelerated
     * canvas. That costs an off-screen bitmap the size of the view, which is
     * cheap for a bar but is the reason this is off by default. For a shadow
     * under the bar as a whole, prefer `android:elevation`: the view supplies a
     * correctly rounded outline for it.
     *
     * The shadow is drawn *outside* the bar and never changes its size, so it
     * needs somewhere to go: give the view padding. The software layer is the
     * size of the view, so unlike an elevation shadow this one cannot escape the
     * view's own bounds, and `android:clipChildren="false"` on the parent will
     * not help. Changing [shadowRadius], [shadowDx] or [shadowDy] never moves or
     * resizes the bar.
     *
     * @throws IllegalArgumentException if [value] is negative or not finite.
     */
    @get:Px
    public var shadowRadius: Float
        get() = _shadowRadius
        set(@Px value) {
            requireNonNegativeDimension(value, "shadowRadius")
            if (_shadowRadius == value) return
            _shadowRadius = value
            applyShadow()
            invalidate()
        }

    /** Horizontal offset of the drop shadow, in pixels. Defaults to `0`. */
    @get:Px
    public var shadowDx: Float
        get() = _shadowDx
        set(@Px value) {
            require(value.isFinite()) { "shadowDx must be finite but was $value" }
            if (_shadowDx == value) return
            _shadowDx = value
            applyShadow()
            invalidate()
        }

    /** Vertical offset of the drop shadow, in pixels. Defaults to `0`. */
    @get:Px
    public var shadowDy: Float
        get() = _shadowDy
        set(@Px value) {
            require(value.isFinite()) { "shadowDy must be finite but was $value" }
            if (_shadowDy == value) return
            _shadowDy = value
            applyShadow()
            invalidate()
        }

    /**
     * Which parts of the bar cast a drop shadow.
     *
     * Defaults to [ShadowTarget.ALL], or to the `spb_shadowTarget` XML
     * attribute. Has no effect while [shadowRadius] is `0`.
     */
    public var shadowTarget: ShadowTarget
        get() = _shadowTarget
        set(value) {
            if (_shadowTarget == value) return
            _shadowTarget = value
            applyShadow()
            invalidate()
        }

    /**
     * Colour of the drop shadow. Defaults to 25% black.
     *
     * Has no effect while [shadowRadius] is `0`.
     */
    @get:ColorInt
    public var shadowColor: Int
        get() = _shadowColor
        set(@ColorInt value) {
            if (_shadowColor == value) return
            _shadowColor = value
            applyShadow()
            invalidate()
        }

    /**
     * How a segment transitions when it is lit or cleared.
     *
     * Defaults to [SegmentAnimation.NONE], or to the `spb_segmentAnimation` XML
     * attribute. Off by default so that upgrading from 0.0.1 changes nothing;
     * set it explicitly to opt in.
     *
     * The bar's initial state never animates, only changes made after the view
     * has been laid out do, so a screen does not visibly assemble itself.
     */
    public var segmentAnimation: SegmentAnimation
        get() = _segmentAnimation
        set(value) {
            if (_segmentAnimation == value) return
            _segmentAnimation = value
            activeTransitionStyle = value
            if (value == SegmentAnimation.NONE) snapAnimationToTargets()
            invalidate()
        }

    /**
     * Duration of the [segmentAnimation] transition, in milliseconds. `0`
     * disables animation as surely as [SegmentAnimation.NONE] does.
     *
     * Defaults to `200`, or to the `spb_animationDuration` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is negative.
     */
    public var animationDurationMs: Long
        get() = _animationDurationMs
        set(value) {
            require(value >= 0L) { "animationDurationMs must be >= 0 but was $value" }
            if (_animationDurationMs == value) return
            _animationDurationMs = value
            if (value == 0L) snapAnimationToTargets()
            invalidate()
        }

    /**
     * How the bar's initial state arrives the first time it is shown.
     *
     * Defaults to [EntryAnimation.NONE], or to the `spb_entryAnimation` XML
     * attribute. Runs once, when the view is first laid out; changes made after
     * that are governed by [segmentAnimation] instead.
     *
     * [EntryAnimation.STAGGER] spreads the reveal across
     * [entryStaggerDelayMs] per segment, so the total is
     * `animationDurationMs + entryStaggerDelayMs * divisions`.
     */
    public var entryAnimation: EntryAnimation
        get() = _entryAnimation
        set(value) {
            if (_entryAnimation == value) return
            _entryAnimation = value
            invalidate()
        }

    /**
     * Delay between consecutive segments in an [EntryAnimation.STAGGER] reveal,
     * in milliseconds.
     *
     * Defaults to `60`, or to the `spb_entryStaggerDelay` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is negative.
     */
    public var entryStaggerDelayMs: Long
        get() = _entryStaggerDelayMs
        set(value) {
            require(value >= 0L) { "entryStaggerDelayMs must be >= 0 but was $value" }
            if (_entryStaggerDelayMs == value) return
            _entryStaggerDelayMs = value
            invalidate()
        }

    /**
     * A continuous animation that plays for as long as the bar is visible.
     *
     * Defaults to [RecurringAnimation.NONE], or to the `spb_recurringAnimation`
     * XML attribute. Off by default on purpose: a bar that never settles is
     * tiring to look at, and it costs a frame callback for as long as it runs.
     *
     * The loop stops on its own while the view is detached or not
     * [visible][View.getVisibility], and resumes when it comes back.
     */
    public var recurringAnimation: RecurringAnimation
        get() = _recurringAnimation
        set(value) {
            if (_recurringAnimation == value) return
            _recurringAnimation = value
            recurringStart = if (value == RecurringAnimation.NONE) {
                0L
            } else {
                AnimationUtils.currentAnimationTimeMillis()
            }
            invalidate()
        }

    /**
     * Period of one [recurringAnimation] cycle, in milliseconds.
     *
     * Defaults to `1600`, or to the `spb_recurringDuration` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is not greater than zero.
     */
    public var recurringDurationMs: Long
        get() = _recurringDurationMs
        set(value) {
            require(value > 0L) { "recurringDurationMs must be > 0 but was $value" }
            if (_recurringDurationMs == value) return
            _recurringDurationMs = value
            invalidate()
        }

    /**
     * Colour blended into lit segments at the peak of a
     * [RecurringAnimation.SHIMMER] sweep. Defaults to 45% white.
     */
    @get:ColorInt
    public var shimmerColor: Int
        get() = _shimmerColor
        set(@ColorInt value) {
            if (_shimmerColor == value) return
            _shimmerColor = value
            invalidate()
        }

    /**
     * Upper bound on the bar's measured width, in pixels, or [NO_MAX_SIZE] for
     * none.
     *
     * Useful for a bar declared `match_parent` that should not stretch across a
     * tablet. `android:minWidth` and the standard layout params work as they do
     * on any view; this fills the gap Android leaves for a maximum.
     *
     * Defaults to [NO_MAX_SIZE], or to the `spb_maxWidth` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is negative and not
     *   [NO_MAX_SIZE].
     */
    @get:Px
    public var maxWidth: Int
        get() = _maxWidth
        set(@Px value) {
            requireMaxSize(value, "maxWidth")
            if (_maxWidth == value) return
            _maxWidth = value
            requestLayout()
        }

    /**
     * Upper bound on the bar's measured height, in pixels, or [NO_MAX_SIZE] for
     * none.
     *
     * Defaults to [NO_MAX_SIZE], or to the `spb_maxHeight` XML attribute.
     *
     * @throws IllegalArgumentException if [value] is negative and not
     *   [NO_MAX_SIZE].
     */
    @get:Px
    public var maxHeight: Int
        get() = _maxHeight
        set(@Px value) {
            requireMaxSize(value, "maxHeight")
            if (_maxHeight == value) return
            _maxHeight = value
            requestLayout()
        }

    init {
        backgroundPaint.color = DEFAULT_BACKGROUND_COLOR
        progressPaint.color = DEFAULT_PROGRESS_COLOR
        dividerPaint.color = DEFAULT_DIVIDER_COLOR

        if (attrs != null) {
            readAttributes(context, attrs, defStyleAttr, defStyleRes)
        }

        // So android:elevation casts a shadow shaped like the bar rather than a
        // rectangle. Independent of the spb_shadow* properties, which shadow the
        // lit segments themselves.
        outlineProvider = TrackOutlineProvider()
        applyShadow()
        activeTransitionStyle = _segmentAnimation
        ensureAnimationCapacity()
    }

    private fun readAttributes(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.SegmentedProgressBar,
            defStyleAttr,
            defStyleRes,
        )
        try {
            dividerPaint.color = typedArray.getColor(
                R.styleable.SegmentedProgressBar_dividerColor,
                DEFAULT_DIVIDER_COLOR,
            )
            backgroundPaint.color = typedArray.getColor(
                R.styleable.SegmentedProgressBar_progressBarBackgroundColor,
                DEFAULT_BACKGROUND_COLOR,
            )
            progressPaint.color = typedArray.getColor(
                R.styleable.SegmentedProgressBar_progressBarColor,
                DEFAULT_PROGRESS_COLOR,
            )
            _isDividerEnabled = typedArray.getBoolean(
                R.styleable.SegmentedProgressBar_isDividerEnabled,
                true,
            )

            val divisionsAttr = typedArray.getInteger(
                R.styleable.SegmentedProgressBar_divisions,
                DEFAULT_DIVISIONS,
            )
            requireDivisions(divisionsAttr)
            _divisions = divisionsAttr

            val dividerWidthAttr = typedArray.getDimension(
                R.styleable.SegmentedProgressBar_dividerWidth,
                DEFAULT_DIVIDER_WIDTH_PX,
            )
            requireNonNegativeDimension(dividerWidthAttr, "dividerWidth")
            _dividerWidth = dividerWidthAttr

            val cornerRadiusAttr = typedArray.getDimension(
                R.styleable.SegmentedProgressBar_cornerRadius,
                DEFAULT_CORNER_RADIUS_PX,
            )
            requireNonNegativeDimension(cornerRadiusAttr, "cornerRadius")
            _cornerRadius = cornerRadiusAttr

            _cornerMode = CornerMode.entries[
                typedArray.getInt(
                    R.styleable.SegmentedProgressBar_spb_cornerMode,
                    CornerMode.BAR_ENDS.ordinal,
                ).coerceIn(0, CornerMode.entries.lastIndex),
            ]

            val activeRatioAttr = typedArray.getFloat(
                R.styleable.SegmentedProgressBar_spb_activeHeightRatio,
                1f,
            )
            requireRatio(activeRatioAttr, "activeHeightRatio")
            _activeHeightRatio = activeRatioAttr

            val inactiveRatioAttr = typedArray.getFloat(
                R.styleable.SegmentedProgressBar_spb_inactiveHeightRatio,
                1f,
            )
            requireRatio(inactiveRatioAttr, "inactiveHeightRatio")
            _inactiveHeightRatio = inactiveRatioAttr

            val shadowRadiusAttr = typedArray.getDimension(
                R.styleable.SegmentedProgressBar_spb_shadowRadius,
                0f,
            )
            requireNonNegativeDimension(shadowRadiusAttr, "shadowRadius")
            _shadowRadius = shadowRadiusAttr
            _shadowDx = typedArray.getDimension(R.styleable.SegmentedProgressBar_spb_shadowDx, 0f)
            _shadowDy = typedArray.getDimension(R.styleable.SegmentedProgressBar_spb_shadowDy, 0f)
            _shadowColor = typedArray.getColor(
                R.styleable.SegmentedProgressBar_spb_shadowColor,
                DEFAULT_SHADOW_COLOR,
            )
            _shadowTarget = ShadowTarget.entries[
                typedArray.getInt(
                    R.styleable.SegmentedProgressBar_spb_shadowTarget,
                    ShadowTarget.ALL.ordinal,
                ).coerceIn(0, ShadowTarget.entries.lastIndex),
            ]

            _segmentAnimation = SegmentAnimation.entries[
                typedArray.getInt(
                    R.styleable.SegmentedProgressBar_spb_segmentAnimation,
                    SegmentAnimation.NONE.ordinal,
                ).coerceIn(0, SegmentAnimation.entries.lastIndex),
            ]

            val durationAttr = typedArray.getInt(
                R.styleable.SegmentedProgressBar_spb_animationDuration,
                DEFAULT_ANIMATION_DURATION_MS.toInt(),
            )
            require(durationAttr >= 0) {
                "spb_animationDuration must be >= 0 but was $durationAttr"
            }
            _animationDurationMs = durationAttr.toLong()

            _entryAnimation = EntryAnimation.entries[
                typedArray.getInt(
                    R.styleable.SegmentedProgressBar_spb_entryAnimation,
                    EntryAnimation.NONE.ordinal,
                ).coerceIn(0, EntryAnimation.entries.lastIndex),
            ]

            val staggerAttr = typedArray.getInt(
                R.styleable.SegmentedProgressBar_spb_entryStaggerDelay,
                DEFAULT_ENTRY_STAGGER_DELAY_MS.toInt(),
            )
            require(staggerAttr >= 0) {
                "spb_entryStaggerDelay must be >= 0 but was $staggerAttr"
            }
            _entryStaggerDelayMs = staggerAttr.toLong()

            _recurringAnimation = RecurringAnimation.entries[
                typedArray.getInt(
                    R.styleable.SegmentedProgressBar_spb_recurringAnimation,
                    RecurringAnimation.NONE.ordinal,
                ).coerceIn(0, RecurringAnimation.entries.lastIndex),
            ]
            if (_recurringAnimation != RecurringAnimation.NONE) {
                recurringStart = AnimationUtils.currentAnimationTimeMillis()
            }

            val recurringAttr = typedArray.getInt(
                R.styleable.SegmentedProgressBar_spb_recurringDuration,
                DEFAULT_RECURRING_DURATION_MS.toInt(),
            )
            require(recurringAttr > 0) {
                "spb_recurringDuration must be > 0 but was $recurringAttr"
            }
            _recurringDurationMs = recurringAttr.toLong()

            _shimmerColor = typedArray.getColor(
                R.styleable.SegmentedProgressBar_spb_shimmerColor,
                DEFAULT_SHIMMER_COLOR,
            )

            _maxWidth = typedArray
                .getDimensionPixelSize(R.styleable.SegmentedProgressBar_spb_maxWidth, NO_MAX_SIZE)
            requireMaxSize(_maxWidth, "maxWidth")
            _maxHeight = typedArray
                .getDimensionPixelSize(R.styleable.SegmentedProgressBar_spb_maxHeight, NO_MAX_SIZE)
            requireMaxSize(_maxHeight, "maxHeight")

            _isTapToToggleEnabled = typedArray.getBoolean(
                R.styleable.SegmentedProgressBar_spb_tapToToggle,
                false,
            )

            _isPerDivisionAccessibilityEnabled = typedArray.getBoolean(
                R.styleable.SegmentedProgressBar_spb_perDivisionAccessibility,
                false,
            )
            if (_isPerDivisionAccessibilityEnabled) applyPerDivisionAccessibility()
            // Set directly rather than through updateInteractivity(), which also
            // clears both flags when nothing is interactive and would undo an
            // android:clickable in the same tag.
            if (_isTapToToggleEnabled) {
                isClickable = true
                isFocusable = true
            }
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * Lights the segment at [index], leaving every other segment untouched.
     *
     * Does nothing if the segment is already lit. Negative indices are ignored.
     */
    public fun enableDivision(index: Int) {
        if (index < 0 || enabled.contains(index)) return
        val insertAt = enabled.binarySearch(index).let { if (it < 0) -(it + 1) else it }
        enabled.add(insertAt, index)
        onSegmentsChanged()
    }

    /**
     * Clears the segment at [index], leaving every other segment untouched.
     *
     * Does nothing if the segment is not currently lit.
     */
    public fun disableDivision(index: Int) {
        if (enabled.remove(index)) onSegmentsChanged()
    }

    /** Whether the segment at [index] is currently lit. */
    public fun isDivisionEnabled(index: Int): Boolean = enabled.contains(index)

    /**
     * Flips the segment at [index] and returns its new state.
     *
     * Negative indices are ignored and report `false`.
     */
    public fun toggleDivision(index: Int): Boolean {
        if (index < 0) return false
        return if (isDivisionEnabled(index)) {
            disableDivision(index)
            false
        } else {
            enableDivision(index)
            true
        }
    }

    /**
     * Sets how much of the division at [index] is filled, from `0` to `1`.
     *
     * This is the general form of the on/off API: `1` is exactly
     * [enableDivision], `0` is exactly [disableDivision], and anything between
     * draws that division's fill over the leading part of its cell, mirrored
     * under RTL. The classic use is a stories or chapters bar, where finished
     * segments are full and the current one advances:
     *
     * ```kotlin
     * bar.enabledDivisions = listOf(0, 1)      // chapters already read
     * bar.setDivisionProgress(2, 0.4f)         // 40% through chapter 3
     * ```
     *
     * The fill is clipped to the shape the cell would have as a standalone lit
     * segment, so every [cornerMode] renders it correctly; under
     * [CornerMode.EACH_RUN] a partial division does not join the run beside it.
     * A partially filled division reports `false` from [isDivisionEnabled] and
     * is not counted by [completedSegmentCount]; it becomes part of the lit set
     * only when its progress reaches `1`.
     *
     * Changes between partial values apply on the next frame with no
     * transition, because the callers that drive this, playback positions and
     * download progress, update it continuously and a built-in animation would
     * fight them. Reaching `1` (or being enabled directly) hands over to
     * [segmentAnimation] as usual, with a GROW transition continuing from the
     * partial fill rather than restarting at zero.
     *
     * Values outside `0..1` are clamped, and negative indices are ignored,
     * matching how progress data is treated everywhere else in this class.
     *
     * @throws IllegalArgumentException if [progress] is NaN or infinite, which
     *   can only be a programming error.
     */
    public fun setDivisionProgress(index: Int, progress: Float) {
        require(progress.isFinite()) { "progress must be finite but was $progress" }
        if (index < 0) return
        val fraction = progress.coerceIn(0f, 1f)

        when {
            fraction >= 1f -> {
                // enableDivision syncs the animation targets, which both seeds
                // the transition from the old partial value and clears it.
                if (isDivisionEnabled(index)) {
                    partialFills.delete(index)
                } else {
                    enableDivision(index)
                }
            }
            fraction <= 0f -> {
                partialFills.delete(index)
                if (isDivisionEnabled(index)) disableDivision(index) else invalidate()
            }
            else -> {
                partialFills.put(index, fraction)
                if (enabled.remove(index)) {
                    // Downgrading full to partial must not play the lit-to-off
                    // transition underneath the partial fill, so this division's
                    // animation state is snapped rather than routed through
                    // onSegmentsChanged.
                    ensureAnimationCapacity()
                    if (index < animTargetLit.size) {
                        animTargetLit[index] = false
                        animStart[index] = 0L
                        animFrom[index] = 0f
                    }
                }
                invalidate()
            }
        }
    }

    /**
     * How much of the division at [index] is filled, from `0` to `1`.
     *
     * `1` for a division in [enabledDivisions], the value last given to
     * [setDivisionProgress] for a partial one, `0` otherwise. A division that is
     * somehow both enabled and partial, which the setters never produce,
     * reports `1`: enabled wins.
     */
    public fun getDivisionProgress(index: Int): Float = when {
        index < 0 -> 0f
        isDivisionEnabled(index) -> 1f
        else -> partialFills[index] ?: 0f
    }

    /**
     * Gives the division at [index] its own on-colour, superseding
     * [progressBarColor] for that division only.
     *
     * The rule is exactly one sentence: **a colour set here wins over
     * [progressBarColor] for its division; every division without one keeps
     * using [progressBarColor].** Changing [progressBarColor] later never
     * clears these overrides; [clearDivisionColor] and [clearDivisionColors]
     * are the way back to the single-colour path.
     *
     * The override colours everything on-coloured in that division: its full
     * fill, its partial fill from [setDivisionProgress], and the base a shimmer
     * or pulse tints. Off segments always use [progressBarBackgroundColor].
     * The classic use is a heatmap or streak calendar, where every cell is on
     * and each carries an intensity:
     *
     * ```kotlin
     * bar.enabledDivisions = (0 until bar.divisions).toList()
     * intensities.forEachIndexed { index, level ->
     *     bar.setDivisionColor(index, shadeFor(level))
     * }
     * ```
     *
     * Like every other colour on this view, overrides are not part of saved
     * instance state. Negative indices are ignored; indices beyond [divisions]
     * are retained and take effect if the bar later grows.
     */
    public fun setDivisionColor(index: Int, @ColorInt color: Int) {
        if (index < 0) return
        if (divisionColors.indexOfKey(index) >= 0 && divisionColors[index] == color) return
        divisionColors.put(index, color)
        invalidate()
    }

    /** Removes the [setDivisionColor] override at [index], if there is one. */
    public fun clearDivisionColor(index: Int) {
        val at = divisionColors.indexOfKey(index)
        if (at < 0) return
        divisionColors.removeAt(at)
        invalidate()
    }

    /** Removes every [setDivisionColor] override, returning to one colour. */
    public fun clearDivisionColors() {
        if (divisionColors.size() == 0) return
        divisionColors.clear()
        invalidate()
    }

    /**
     * The on-colour the division at [index] actually draws with: its
     * [setDivisionColor] override if it has one, [progressBarColor] otherwise.
     */
    @ColorInt
    public fun getDivisionColor(index: Int): Int = effectiveDivisionColor(index)

    /** Whether the division at [index] has a [setDivisionColor] override. */
    public fun hasDivisionColor(index: Int): Boolean = divisionColors.indexOfKey(index) >= 0

    private fun effectiveDivisionColor(index: Int): Int =
        if (divisionColors.indexOfKey(index) >= 0) {
            divisionColors[index]
        } else {
            progressPaint.color
        }

    /**
     * The index of the segment at horizontal position [x], or [NO_DIVISION] if
     * [x] falls outside the bar's content box.
     *
     * [x] is in this view's own coordinate space, so a
     * [android.view.MotionEvent]'s `x` can be passed straight in. Padding and
     * layout direction are both accounted for, which is the point of having this
     * on the view rather than recomputing it at the call site: under RTL,
     * segment `0` is at the right-hand end, and getting that wrong silently
     * toggles the mirror image of the segment the user touched.
     *
     * ```kotlin
     * bar.setOnTouchListener { view, event ->
     *     if (event.actionMasked == MotionEvent.ACTION_UP) {
     *         val index = bar.divisionAt(event.x)
     *         if (index != SegmentedProgressBar.NO_DIVISION) bar.toggleDivision(index)
     *         view.performClick()
     *     }
     *     true
     * }
     * ```
     */
    public fun divisionAt(x: Float): Int {
        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        if (contentWidth <= 0f) return NO_DIVISION

        val local = x - paddingLeft
        if (local < 0f || local >= contentWidth) return NO_DIVISION

        val fromStartOfBar = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            SegmentGeometry.mirror(contentWidth, local)
        } else {
            local
        }
        // The RTL mirror of local == 0 lands exactly on contentWidth, which would
        // index one past the end, so the result is clamped rather than trusted.
        return (fromStartOfBar / contentWidth * _divisions).toInt().coerceIn(0, _divisions - 1)
    }

    /**
     * Whether tapping a segment toggles it.
     *
     * Off by default, or set from the `spb_tapToToggle` XML attribute. Turning it
     * on is all an XML-only bar needs to become interactive:
     *
     * ```xml
     * <com.rachitgoyal.segmented.SegmentedProgressBar
     *     android:layout_width="match_parent"
     *     android:layout_height="20dp"
     *     app:spb_tapToToggle="true" />
     * ```
     *
     * The bar toggles the segment first and notifies any
     * [OnDivisionClickListener] afterwards, so the listener always sees the state
     * the user is looking at. Leave this off if you hold the lit set yourself and
     * toggle it in the listener, or the two will cancel out.
     *
     * Turning it on makes the view [clickable][isClickable] and
     * [focusable][isFocusable]. Setting `isClickable = false` afterwards, or
     * `android:clickable="false"` in XML, switches all touch handling back off,
     * this property included.
     */
    public var isTapToToggleEnabled: Boolean
        get() = _isTapToToggleEnabled
        set(value) {
            if (_isTapToToggleEnabled == value) return
            _isTapToToggleEnabled = value
            updateInteractivity()
        }

    /**
     * Whether each division is exposed to accessibility services as its own
     * node.
     *
     * Off by default, or set from the `spb_perDivisionAccessibility` XML
     * attribute; the default keeps the 2.0.0 behaviour, where the bar is a
     * single node announcing "6 of 10 segments complete". Turning it on gives
     * every division its own focusable, checkable node ("Segment 3 of 10",
     * checked or not checked), which a screen reader can step through and,
     * when the bar is interactive, toggle in place. It also enables keyboard
     * use on an interactive bar: arrow keys move between divisions and Enter
     * or the d-pad centre activates one, which is exactly the path that taps
     * cannot serve, since an accessibility activation carries no coordinates.
     *
     * Activating a division behaves like tapping it: the division toggles if
     * [isTapToToggleEnabled], and any [OnDivisionClickListener] is notified
     * after. On a non-interactive bar the nodes are read-only state.
     *
     * Opt-in rather than always-on because it changes how the bar reads: one
     * summary node is the right experience for a passive progress indicator,
     * per-division nodes are the right experience for a control the user is
     * expected to operate.
     */
    public var isPerDivisionAccessibilityEnabled: Boolean
        get() = _isPerDivisionAccessibilityEnabled
        set(value) {
            if (_isPerDivisionAccessibilityEnabled == value) return
            _isPerDivisionAccessibilityEnabled = value
            applyPerDivisionAccessibility()
        }

    private fun applyPerDivisionAccessibility() {
        if (_isPerDivisionAccessibilityEnabled) {
            val helper = DivisionTouchHelper()
            divisionTouchHelper = helper
            ViewCompat.setAccessibilityDelegate(this, helper)
        } else {
            divisionTouchHelper = null
            ViewCompat.setAccessibilityDelegate(this, null)
        }
    }

    /** Notified when the user taps an individual segment. */
    public fun interface OnDivisionClickListener {
        /**
         * @param bar the bar that was tapped.
         * @param index the zero-based index of the tapped segment, always within
         *   `0 until bar.divisions`.
         */
        public fun onDivisionClick(bar: SegmentedProgressBar, index: Int)
    }

    /**
     * Makes individual segments tappable, notifying [listener] with the index of
     * the segment the user hit. Pass `null` to stop listening.
     *
     * The listener only reports the tap; it does not change any state, so the
     * caller decides what a tap means. The usual implementation is a toggle:
     *
     * ```kotlin
     * bar.setOnDivisionClickListener { view, index -> view.toggleDivision(index) }
     * ```
     *
     * Setting a listener makes the view [clickable][isClickable] and
     * [focusable][isFocusable]; clearing it makes it neither again, unless
     * [isTapToToggleEnabled] is on.
     *
     * Known limitation: an accessibility service or keyboard activates a view
     * without pointer coordinates, so there is no segment to report and the
     * listener is not called in that case. Give such users a separate,
     * non-positional control, the demo app pairs the bar with a "Clear all"
     * button. Exposing each segment as its own accessibility node would need a
     * virtual view hierarchy, which this library does not currently ship.
     */
    public fun setOnDivisionClickListener(listener: OnDivisionClickListener?) {
        divisionClickListener = listener
        updateInteractivity()
    }

    /**
     * Keeps the platform's own touch gate in step with the two things that make
     * this bar interactive.
     *
     * [View.onTouchEvent] only dispatches a click while the view is clickable, so
     * this is also what makes `isClickable = false` a complete off switch.
     */
    private fun updateInteractivity() {
        val interactive = divisionClickListener != null || _isTapToToggleEnabled
        isClickable = interactive
        isFocusable = interactive
    }

    // ClickableViewAccessibility wants onTouchEvent to call performClick() itself.
    // Here that would be wrong: View.onTouchEvent already dispatches the click on
    // ACTION_UP, so calling it again would fire every tap twice. The accessibility
    // requirement the check exists to enforce is satisfied, the tap handling
    // lives in the performClick() override below. Lint cannot see through the
    // super call, so this one site is suppressed rather than the whole rule.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Recorded on the way down because performClick() carries no coordinates.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pendingTouchX = event.x
        }
        return super.onTouchEvent(event)
    }

    /**
     * Dispatches the segment tap.
     *
     * The work lives here rather than in an `OnTouchListener` because some
     * accessibility services trigger a click by calling this method directly,
     * and handling touches without routing through it leaves the view
     * inaccessible.
     */
    override fun performClick(): Boolean {
        val touchX = pendingTouchX
        pendingTouchX = Float.NaN

        val index = if (touchX.isNaN()) NO_DIVISION else divisionAt(touchX)
        if (index != NO_DIVISION) activateDivision(index)
        return super.performClick()
    }

    /**
     * What activating a division means, shared by taps and accessibility
     * actions so the two can never drift apart.
     */
    private fun activateDivision(index: Int) {
        // Toggle first, so a listener sees the state the user is looking at.
        if (_isTapToToggleEnabled) toggleDivision(index)
        divisionClickListener?.onDivisionClick(this, index)
    }

    // The three hooks ExploreByTouchHelper needs to see the world: hover for
    // touch exploration, key events for arrow navigation, focus for keeping the
    // virtual focus in step with input focus. All no-ops while the helper is
    // absent, which is the default.

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        divisionTouchHelper?.dispatchHoverEvent(event) == true || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        divisionTouchHelper?.dispatchKeyEvent(event) == true || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        divisionTouchHelper?.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    /**
     * The virtual accessibility hierarchy behind
     * [isPerDivisionAccessibilityEnabled]: one checkable node per division.
     *
     * Bounds are the full-height cell, gaps included, so there are no dead
     * zones between nodes while exploring by touch, matching how [divisionAt]
     * maps taps. RTL is resolved to physical coordinates here because
     * accessibility bounds are physical.
     */
    private inner class DivisionTouchHelper :
        ExploreByTouchHelper(this@SegmentedProgressBar) {

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val index = divisionAt(x)
            return if (index == NO_DIVISION) HOST_ID else index
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (index in 0 until _divisions) virtualViewIds.add(index)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat,
        ) {
            node.className = "android.widget.ToggleButton"
            node.contentDescription = resources.getString(
                R.string.segmented_progress_bar_division_description,
                virtualViewId + 1,
                _divisions,
            )
            node.isCheckable = true
            node.isChecked = isDivisionEnabled(virtualViewId)

            if (isClickable && isEnabled) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                node.isClickable = true
            }

            node.setBoundsInParent(divisionBounds(virtualViewId))
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: android.os.Bundle?,
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            if (!isClickable || !isEnabled) return false

            activateDivision(virtualViewId)
            invalidateVirtualView(virtualViewId)
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }

        private fun divisionBounds(index: Int): Rect {
            val contentWidth = (width - paddingLeft - paddingRight).toFloat()
            if (contentWidth <= 0f) return Rect(0, 0, 1, 1)

            var start = SegmentGeometry.boundary(contentWidth, _divisions, index)
            var end = SegmentGeometry.boundary(contentWidth, _divisions, index + 1)
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                val mirrored = SegmentGeometry.mirror(contentWidth, end)
                end = SegmentGeometry.mirror(contentWidth, start)
                start = mirrored
            }
            return Rect(
                paddingLeft + start.toInt(),
                0,
                paddingLeft + end.toInt(),
                height,
            )
        }
    }

    /**
     * Clears every lit segment and every partial fill, returning the bar to
     * empty.
     *
     * [divisions] and all colour, divider and corner settings are preserved.
     */
    public fun reset() {
        if (enabled.isEmpty() && partialFills.size() == 0) return
        enabled.clear()
        partialFills.clear()
        onSegmentsChanged()
    }

    /**
     * Sets the colour of the progress track.
     *
     * @deprecated This shadows [View.setBackgroundColor] with a different
     *   meaning, which is a long-standing wart in this library's API: calling it
     *   paints the progress track rather than the view's background. It is kept
     *   only so that code written against 0.0.1 keeps compiling and behaving the
     *   same way. Use [progressBarBackgroundColor] instead, and use
     *   [View.setBackground] if you genuinely want a view background.
     */
    @Deprecated(
        message = "Shadows View.setBackgroundColor with different semantics. " +
            "Use progressBarBackgroundColor instead.",
        replaceWith = ReplaceWith("progressBarBackgroundColor = color"),
    )
    override fun setBackgroundColor(@ColorInt color: Int) {
        // Defensive: if a future platform release ever routes an
        // `android:background` colour through this override, it would run before
        // this class's property initialisers and dereference a null Paint.
        @Suppress("SENSELESS_COMPARISON")
        if (backgroundPaint == null) {
            super.setBackgroundColor(color)
            return
        }
        progressBarBackgroundColor = color
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        // Deliberately ignores the shadow: enabling one, or changing its blur or
        // offset, must never change the size of the bar itself. The shadow is
        // drawn outside the bar and needs room from padding, or from a parent
        // that does not clip its children.
        val intrinsicWidth = (DEFAULT_WIDTH_DP * density).toInt() + paddingLeft + paddingRight
        val intrinsicHeight = (DEFAULT_HEIGHT_DP * density).toInt() + paddingTop + paddingBottom

        var width = resolveSize(maxOf(suggestedMinimumWidth, intrinsicWidth), widthMeasureSpec)
        var height = resolveSize(maxOf(suggestedMinimumHeight, intrinsicHeight), heightMeasureSpec)

        // Applied after resolveSize so a maximum wins over match_parent, but
        // never below the minimum, which the framework treats as the harder
        // constraint.
        if (_maxWidth != NO_MAX_SIZE) width = width.coerceAtMost(_maxWidth)
        if (_maxHeight != NO_MAX_SIZE) height = height.coerceAtMost(_maxHeight)
        width = maxOf(width, suggestedMinimumWidth)
        height = maxOf(height, suggestedMinimumHeight)

        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        playEntryAnimationIfNeeded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The recurring loop drives itself from onDraw, and stops when detached
        // because postInvalidateOnAnimation does nothing then. Kick it again.
        if (_recurringAnimation != RecurringAnimation.NONE) invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && _recurringAnimation != RecurringAnimation.NONE) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentWidth = (width - paddingLeft - paddingRight).toFloat()
        val contentHeight = (height - paddingTop - paddingBottom).toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) return

        ensureAnimationCapacity()
        val now = AnimationUtils.currentAnimationTimeMillis()

        val dividerSpan = SegmentGeometry.effectiveDividerWidth(
            width = contentWidth,
            divisions = _divisions,
            requested = _dividerWidth,
            enabled = _isDividerEnabled,
        )
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        // Both bands are centred vertically, so a thinner track sits inside a
        // taller lit segment rather than sinking to the bottom.
        trackBand.height = contentHeight * _inactiveHeightRatio
        trackBand.top = (contentHeight - trackBand.height) / 2f
        trackBand.radius =
            SegmentGeometry.clampCornerRadius(_cornerRadius, contentWidth, trackBand.height)

        segmentBand.height = contentHeight * _activeHeightRatio
        segmentBand.top = (contentHeight - segmentBand.height) / 2f
        segmentBand.radius =
            SegmentGeometry.clampCornerRadius(_cornerRadius, contentWidth, segmentBand.height)

        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

        drawShadows(canvas, contentWidth, dividerSpan, isRtl, now)
        drawTrack(canvas, contentWidth, dividerSpan, isRtl)
        drawSegments(canvas, contentWidth, dividerSpan, isRtl, now)

        if (dividerSpan > 0f && Color.alpha(dividerPaint.color) > 0) {
            // Dividers span the taller of the two bands so they always visually
            // separate whatever is drawn, and sit on uniformly spaced boundaries,
            // so the set of positions is symmetric and needs no RTL mirroring.
            val dividerTop = minOf(trackBand.top, segmentBand.top)
            val dividerBottom = maxOf(
                trackBand.top + trackBand.height,
                segmentBand.top + segmentBand.height,
            )
            for (index in 1 until _divisions) {
                canvas.drawRect(
                    SegmentGeometry.dividerLeft(contentWidth, _divisions, dividerSpan, index),
                    dividerTop,
                    SegmentGeometry.dividerRight(contentWidth, _divisions, dividerSpan, index),
                    dividerBottom,
                    dividerPaint,
                )
            }
        }

        canvas.restoreToCount(saveCount)

        if (hasRunningAnimation(now) || isRecurringRunning) postInvalidateOnAnimation()
    }

    /**
     * Draws the drop shadow, once, behind everything else.
     *
     * The bar casts *one* shadow, shaped like its silhouette. Everything below
     * follows from that, and each point was a visible bug in the first
     * implementation, which simply hung a [Paint.setShadowLayer] on both fill
     * paints and let it fall where it may:
     *
     * 1. One shape, one blur. Blurring cell by cell adds neighbouring blurs
     *    together wherever they overlap: a lit segment came out twice as dark as an
     *    unlit one, because a lit cell was blurred twice, and every gap grew a dark
     *    tick above and below the bar where its two neighbours met. Skia blurs the
     *    union of a path as a single mask, so a shadow built as one path cannot
     *    accumulate anywhere.
     * 2. Drawn before any fill, so a shadow can never land on top of a neighbouring
     *    cell that has already been painted, which used to put a dark rim down one
     *    side of every cell.
     * 3. Clipped to the outside of the silhouette, so no shadow is drawn inside the
     *    bar. A shape at full shadow alpha under an anti-aliased fill edge showed
     *    through as a dark outline around every segment, and through anything
     *    translucent as dirt.
     * 4. The gaps count as part of the silhouette, so a shadow is never drawn
     *    *between* segments either. Otherwise a narrow gap fills in with blur from
     *    both sides and becomes exactly the divider line that [CornerMode.EACH_RUN]
     *    exists to get rid of.
     *
     * [ShadowTarget] then chooses which cells contribute to the caster; a cell that
     * contributes nothing simply leaves that stretch of the outline unshadowed.
     */
    private fun drawShadows(
        canvas: Canvas,
        contentWidth: Float,
        dividerSpan: Float,
        isRtl: Boolean,
        now: Long,
    ) {
        if (_shadowRadius <= 0f) return

        val onWanted =
            _shadowTarget == ShadowTarget.ON_SEGMENTS || _shadowTarget == ShadowTarget.ALL
        val offWanted =
            _shadowTarget == ShadowTarget.OFF_SEGMENTS || _shadowTarget == ShadowTarget.ALL

        // An invisible track casts nothing, and must not block the shadow of a
        // segment sitting in it either, so it stays out of the silhouette too.
        val hasTrack = trackBand.height > 0f && Color.alpha(backgroundPaint.color) > 0
        val hasSegments = segmentBand.height > 0f

        var bandTop = Float.MAX_VALUE
        var bandBottom = -Float.MAX_VALUE
        shadowClipPath.rewind()
        shadowCastPath.rewind()

        for (index in 0 until _divisions) {
            val isLit = fractionOf(index, now) > 0f
            val partial = if (isLit) 0f else partialFills[index] ?: 0f

            if (hasTrack && computeSpan(contentWidth, dividerSpan, index, isRtl)) {
                addSpanToPath(shadowClipPath, trackBand, trackCornersFor(index, isRtl))
                // A lit cell's rail is covered by on-coloured content, so it
                // follows the on target; the rail of a partial or off cell is
                // off-coloured and follows the off target. Both shapes of a lit
                // cell going into the caster is safe: it is one path, so the
                // union cannot darken anything.
                if (if (isLit) onWanted else offWanted) {
                    addSpanToPath(shadowCastPath, trackBand, trackCornersFor(index, isRtl))
                }
                bandTop = minOf(bandTop, trackBand.top)
                bandBottom = maxOf(bandBottom, trackBand.top + trackBand.height)
            }
            if (hasSegments && isLit) {
                val growth = growthOf(fractionOf(index, now))
                if (computeSpan(contentWidth, dividerSpan, index, isRtl, growth)) {
                    addSpanToPath(shadowClipPath, segmentBand, cornersFor(index, isRtl))
                    if (onWanted) {
                        addSpanToPath(shadowCastPath, segmentBand, cornersFor(index, isRtl))
                    }
                    bandTop = minOf(bandTop, segmentBand.top)
                    bandBottom = maxOf(bandBottom, segmentBand.top + segmentBand.height)
                }
            }
            // A partial fill contributes the shape it actually draws, standalone
            // corners and all, so a shadow can neither land beneath a translucent
            // fill nor go missing over a transparent track. As on-coloured
            // content, it follows the on target.
            if (hasSegments && partial > 0f &&
                computeSpan(contentWidth, dividerSpan, index, isRtl, growFraction = partial)
            ) {
                val corners = standaloneCornersFor(index, isRtl)
                addSpanToPath(shadowClipPath, segmentBand, corners)
                if (onWanted) addSpanToPath(shadowCastPath, segmentBand, corners)
                bandTop = minOf(bandTop, segmentBand.top)
                bandBottom = maxOf(bandBottom, segmentBand.top + segmentBand.height)
            }
        }
        if (shadowClipPath.isEmpty || shadowCastPath.isEmpty) return

        if (dividerSpan > 0f) {
            for (index in 1 until _divisions) {
                scratchRect.set(
                    SegmentGeometry.dividerLeft(contentWidth, _divisions, dividerSpan, index),
                    bandTop,
                    SegmentGeometry.dividerRight(contentWidth, _divisions, dividerSpan, index),
                    bandBottom,
                )
                shadowClipPath.addRect(scratchRect, Path.Direction.CW)
                // Bridged in the caster too, but only between two cells that both
                // contribute, so that a run casts a single unbroken shadow while a
                // cell left out by the target still breaks it.
                if (castsShadow(index - 1, now, onWanted, offWanted, hasTrack) &&
                    castsShadow(index, now, onWanted, offWanted, hasTrack)
                ) {
                    shadowCastPath.addRect(scratchRect, Path.Direction.CW)
                }
            }
        }

        val saveCount = canvas.save()
        canvas.clipOutPath(shadowClipPath)
        canvas.drawPath(shadowCastPath, shadowPaint)
        canvas.restoreToCount(saveCount)
    }

    /** Whether cell [index] contributes to the shadow, given the current target. */
    private fun castsShadow(
        index: Int,
        now: Long,
        onWanted: Boolean,
        offWanted: Boolean,
        hasTrack: Boolean,
    ): Boolean = if (fractionOf(index, now) > 0f) onWanted else offWanted && hasTrack

    /**
     * Adds the span currently in [spanLeft] and [spanRight] to [path], rounded the
     * way that cell will actually be drawn.
     *
     * The clip has to match the drawn shape exactly; rounding it any other way
     * would leave a sliver of the silhouette unclipped, and a shadow inside the bar
     * is exactly what this pass exists to avoid.
     */
    private fun addSpanToPath(
        path: Path,
        band: Band,
        corners: Int,
    ) {
        setRadii(
            roundLeft = corners and ROUND_LEFT != 0,
            roundRight = corners and ROUND_RIGHT != 0,
            radius = band.radius,
            width = spanRight - spanLeft,
            height = band.height,
        )
        scratchRect.set(spanLeft, band.top, spanRight, band.top + band.height)
        path.addRoundRect(scratchRect, scratchRadii, Path.Direction.CW)
    }

    /**
     * Draws the unlit track.
     *
     * Under [CornerMode.EACH_SEGMENT] the track is drawn as one rounded cell per
     * division rather than a single bar, so unlit cells read as separate pills
     * too, otherwise the rounded lit segments would sit on a squared-off strip.
     */
    private fun drawTrack(
        canvas: Canvas,
        contentWidth: Float,
        dividerSpan: Float,
        isRtl: Boolean,
    ) {
        if (trackBand.height <= 0f) return

        // Drawn cell by cell rather than as one continuous bar, so the space
        // between segments is genuinely empty. That is what makes a transparent
        // dividerColor read as a *gap*, the page behind shows through, while an
        // opaque one still looks exactly like a divider line painted over a
        // continuous track.
        for (index in 0 until _divisions) {
            if (!computeSpan(contentWidth, dividerSpan, index, isRtl)) continue

            val corners = trackCornersFor(index, isRtl)
            drawSpan(
                canvas = canvas,
                left = spanLeft,
                right = spanRight,
                top = trackBand.top,
                height = trackBand.height,
                roundLeft = corners and ROUND_LEFT != 0,
                roundRight = corners and ROUND_RIGHT != 0,
                radius = trackBand.radius,
                paint = backgroundPaint,
            )
        }
    }

    /**
     * Puts the physical left and right edge of cell [index] into [spanLeft] and
     * [spanRight], and reports whether there is anything left to draw.
     *
     * [growFraction] below `1` shortens the span from its trailing edge, which is
     * what makes a [SegmentAnimation.GROW] transition read as extending from the
     * leading edge. It is applied before the RTL mirror, so the segment grows away
     * from whichever end of the bar the user reads from first.
     *
     * Writes to fields rather than returning a pair because [onDraw] allocates
     * nothing.
     */
    private fun computeSpan(
        contentWidth: Float,
        dividerSpan: Float,
        index: Int,
        isRtl: Boolean,
        growFraction: Float = 1f,
    ): Boolean {
        var left = SegmentGeometry.segmentLeft(contentWidth, _divisions, dividerSpan, index)
        var right = SegmentGeometry.segmentRight(contentWidth, _divisions, dividerSpan, index)
        if (growFraction < 1f) right = left + (right - left) * growFraction
        if (isRtl) {
            val mirrored = SegmentGeometry.mirror(contentWidth, right)
            right = SegmentGeometry.mirror(contentWidth, left)
            left = mirrored
        }
        spanLeft = left
        spanRight = right
        return right - left > 0f
    }

    /**
     * Which sides of track cell [index] are rounded.
     *
     * [CornerMode.EACH_SEGMENT] rounds every cell; the other modes round only the
     * two ends of the bar, so the track keeps reading as one continuous rail.
     *
     * The exception matters: a cell that has a segment drawn over it takes that
     * segment's rounding. Otherwise, under [CornerMode.EACH_RUN], the square
     * corner of the rail showed through the rounded corner at the end of a run.
     */
    private fun trackCornersFor(index: Int, isRtl: Boolean): Int {
        if (_cornerMode == CornerMode.EACH_RUN && isDivisionEnabled(index)) {
            return cornersFor(index, isRtl)
        }
        val roundsStart = _cornerMode == CornerMode.EACH_SEGMENT || index == 0
        val roundsEnd = _cornerMode == CornerMode.EACH_SEGMENT || index == _divisions - 1
        val left = if (isRtl) roundsEnd else roundsStart
        val right = if (isRtl) roundsStart else roundsEnd
        return (if (left) ROUND_LEFT else 0) or (if (right) ROUND_RIGHT else 0)
    }

    private fun drawSegments(
        canvas: Canvas,
        contentWidth: Float,
        dividerSpan: Float,
        isRtl: Boolean,
        now: Long,
    ) {
        if (segmentBand.height <= 0f) return

        val baseColor = progressPaint.color
        // Iterating divisions rather than the lit set on purpose: a segment that
        // has just been cleared is no longer in `enabled` but still has to be
        // drawn while it animates out. The fraction is the single source of truth
        // for "is there anything to draw here", and it is zero for every segment
        // that is neither lit nor in flight.
        for (index in 0 until _divisions) {
            val fraction = fractionOf(index, now)
            if (fraction <= 0f) {
                drawPartialFill(canvas, contentWidth, dividerSpan, index, isRtl, now)
                continue
            }

            if (!computeSpan(contentWidth, dividerSpan, index, isRtl, growthOf(fraction))) continue

            // The division's own colour if it has one, then the recurring tint,
            // then the transition fade on top, so a segment arriving during a
            // shimmer both tints and fades correctly.
            val divisionBase = if (divisionColors.indexOfKey(index) >= 0) {
                divisionColors[index]
            } else {
                baseColor
            }
            var paintColor = recurringColorFor(index, divisionBase, now)
            if (activeTransitionStyle == SegmentAnimation.FADE && fraction < 1f) {
                // Scaling the colour's alpha channel rather than calling
                // Paint.setAlpha: the two are equivalent for a solid paint, but
                // keeping the alpha inside the colour value means every renderer
                // reports it consistently through Paint.getColor.
                val alpha = (Color.alpha(paintColor) * fraction).toInt().coerceIn(0, 255)
                paintColor = (alpha shl 24) or (paintColor and 0x00FFFFFF)
            }
            if (progressPaint.color != paintColor) progressPaint.color = paintColor

            val corners = cornersFor(index, isRtl)
            drawSpan(
                canvas = canvas,
                left = spanLeft,
                right = spanRight,
                top = segmentBand.top,
                height = segmentBand.height,
                roundLeft = corners and ROUND_LEFT != 0,
                roundRight = corners and ROUND_RIGHT != 0,
                radius = segmentBand.radius,
                paint = progressPaint,
            )

            if (progressPaint.color != baseColor) progressPaint.color = baseColor
        }
    }

    /** How much of a segment's span is drawn at [fraction] of its transition. */
    private fun growthOf(fraction: Float): Float =
        if (activeTransitionStyle == SegmentAnimation.GROW) fraction else 1f

    /**
     * Draws the partial fill for division [index], if it has one.
     *
     * The fill covers the leading [getDivisionProgress] of the cell, RTL
     * mirrored, and is clipped to the shape the cell would have as a standalone
     * lit segment. The clip is what keeps every corner mode honest: without it,
     * a fill approaching `1` under a large radius pokes its square cut edge out
     * past the cell's rounded silhouette.
     */
    private fun drawPartialFill(
        canvas: Canvas,
        contentWidth: Float,
        dividerSpan: Float,
        index: Int,
        isRtl: Boolean,
        now: Long,
    ) {
        if (partialFills.size() == 0) return
        val fill = partialFills[index] ?: return

        // The clip: the whole cell, rounded as a standalone segment. Standalone
        // rather than run-joined on purpose: a partial division is not part of
        // the run beside it, so its fill reads as its own in-progress pill.
        if (!computeSpan(contentWidth, dividerSpan, index, isRtl)) return
        val corners = standaloneCornersFor(index, isRtl)
        setRadii(
            roundLeft = corners and ROUND_LEFT != 0,
            roundRight = corners and ROUND_RIGHT != 0,
            radius = segmentBand.radius,
            width = spanRight - spanLeft,
            height = segmentBand.height,
        )
        scratchRect.set(
            spanLeft,
            segmentBand.top,
            spanRight,
            segmentBand.top + segmentBand.height,
        )
        scratchPath.rewind()
        scratchPath.addRoundRect(scratchRect, scratchRadii, Path.Direction.CW)

        // The filled leading portion, reusing the GROW span maths so RTL comes
        // out right for free.
        if (!computeSpan(contentWidth, dividerSpan, index, isRtl, growFraction = fill)) return

        val baseColor = progressPaint.color
        val divisionBase = if (divisionColors.indexOfKey(index) >= 0) {
            divisionColors[index]
        } else {
            baseColor
        }
        val paintColor = recurringColorFor(index, divisionBase, now)
        if (progressPaint.color != paintColor) progressPaint.color = paintColor

        val saveCount = canvas.save()
        canvas.clipPath(scratchPath)
        canvas.drawRect(
            spanLeft,
            segmentBand.top,
            spanRight,
            segmentBand.top + segmentBand.height,
            progressPaint,
        )
        canvas.restoreToCount(saveCount)

        if (progressPaint.color != baseColor) progressPaint.color = baseColor
    }

    /**
     * Which sides of division [index] would be rounded if it stood alone,
     * ignoring any run it might otherwise join.
     */
    private fun standaloneCornersFor(index: Int, isRtl: Boolean): Int {
        val roundsStart: Boolean
        val roundsEnd: Boolean
        when (_cornerMode) {
            CornerMode.EACH_SEGMENT, CornerMode.EACH_RUN -> {
                roundsStart = true
                roundsEnd = true
            }
            CornerMode.BAR_ENDS -> {
                roundsStart = index == 0
                roundsEnd = index == _divisions - 1
            }
        }
        val left = if (isRtl) roundsEnd else roundsStart
        val right = if (isRtl) roundsStart else roundsEnd
        return (if (left) ROUND_LEFT else 0) or (if (right) ROUND_RIGHT else 0)
    }

    /**
     * Which sides of segment [index] are rounded, as a [ROUND_LEFT]/[ROUND_RIGHT]
     * bitmask already mapped from bar-relative start/end onto physical
     * left/right.
     *
     * A bitmask rather than a `Pair` so that [onDraw] stays allocation-free.
     */
    private fun cornersFor(index: Int, isRtl: Boolean): Int {
        val roundsStart: Boolean
        val roundsEnd: Boolean
        when (_cornerMode) {
            CornerMode.EACH_SEGMENT -> {
                roundsStart = true
                roundsEnd = true
            }
            CornerMode.EACH_RUN -> {
                // An edge is rounded unless it butts up against another lit
                // segment, which is what makes a run read as a single pill.
                roundsStart = index == 0 || !isDivisionEnabled(index - 1)
                roundsEnd = index == _divisions - 1 || !isDivisionEnabled(index + 1)
            }
            CornerMode.BAR_ENDS -> {
                roundsStart = index == 0
                roundsEnd = index == _divisions - 1
            }
        }
        val left = if (isRtl) roundsEnd else roundsStart
        val right = if (isRtl) roundsStart else roundsEnd
        return (if (left) ROUND_LEFT else 0) or (if (right) ROUND_RIGHT else 0)
    }

    /**
     * Draws a span of the given vertical band, rounding only the requested sides.
     *
     * Falls back to [Canvas.drawRect] when nothing needs rounding, which is the
     * common case for interior segments and avoids building a [Path].
     */
    @Suppress("LongParameterList")
    private fun drawSpan(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        roundLeft: Boolean,
        roundRight: Boolean,
        radius: Float,
        paint: Paint,
    ) {
        val bottom = top + height
        if (radius <= 0f || (!roundLeft && !roundRight)) {
            canvas.drawRect(left, top, right, bottom, paint)
            return
        }

        setRadii(roundLeft, roundRight, radius, right - left, height)
        scratchRect.set(left, top, right, bottom)
        scratchPath.rewind()
        scratchPath.addRoundRect(scratchRect, scratchRadii, Path.Direction.CW)
        canvas.drawPath(scratchPath, paint)
    }

    /**
     * Fills [scratchRadii] with the eight corner radii for a span.
     *
     * A single-sided radius may be as large as the span is wide, but never more
     * than half the height, or the arcs would overlap vertically.
     */
    private fun setRadii(
        roundLeft: Boolean,
        roundRight: Boolean,
        radius: Float,
        width: Float,
        height: Float,
    ) {
        val r = if (radius <= 0f) 0f else minOf(radius, width, height / 2f)
        val leftRadius = if (roundLeft) r else 0f
        val rightRadius = if (roundRight) r else 0f

        scratchRadii[0] = leftRadius // top-left
        scratchRadii[1] = leftRadius
        scratchRadii[2] = rightRadius // top-right
        scratchRadii[3] = rightRadius
        scratchRadii[4] = rightRadius // bottom-right
        scratchRadii[5] = rightRadius
        scratchRadii[6] = leftRadius // bottom-left
        scratchRadii[7] = leftRadius
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = ProgressBar::class.java.name
        if (contentDescription == null) {
            info.contentDescription = resources.getQuantityString(
                R.plurals.segmented_progress_bar_content_description,
                _divisions,
                completedSegmentCount,
                _divisions,
            )
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState() ?: AbsSavedState.EMPTY_STATE
        val partialIndices = IntArray(partialFills.size()) { partialFills.keyAt(it) }
        val partialValues = FloatArray(partialFills.size()) { partialFills.valueAt(it) }
        return SavedState(superState, _divisions, enabled.toIntArray(), partialIndices, partialValues)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        _divisions = state.divisions
        enabled.clear()
        for (index in state.enabledDivisions) {
            enabled.add(index)
        }
        partialFills.clear()
        for (i in state.partialIndices.indices) {
            partialFills.put(state.partialIndices[i], state.partialValues[i])
        }
        // Restored state is the starting state, so it must not animate in.
        ensureAnimationCapacity()
        syncAnimationTargets()
        snapAnimationToTargets()
        invalidate()
    }

    private fun requireDivisions(value: Int) {
        require(value >= 1) { "divisions must be >= 1 but was $value" }
    }

    private fun requireNonNegativeDimension(value: Float, name: String) {
        require(value.isFinite() && value >= 0f) { "$name must be a finite value >= 0 but was $value" }
    }

    private fun requireMaxSize(value: Int, name: String) {
        require(value == NO_MAX_SIZE || value >= 0) {
            "$name must be >= 0 or NO_MAX_SIZE but was $value"
        }
    }

    private fun requireRatio(value: Float, name: String) {
        require(value.isFinite() && value in 0f..1f) {
            "$name must be a finite value between 0 and 1 but was $value"
        }
    }

    // region shadow

    private fun applyShadow() {
        if (_shadowRadius > 0f) {
            // The paint's own colour is the shadow colour forced opaque, never the
            // requested alpha: the shape is clipped away in [drawShadows] so the
            // fill is invisible either way, but a translucent paint risks the
            // platform modulating the blur's alpha by it and quietly halving it.
            shadowPaint.color = _shadowColor or ALPHA_MASK
            shadowPaint.setShadowLayer(_shadowRadius, _shadowDx, _shadowDy, _shadowColor)
        } else {
            shadowPaint.clearShadowLayer()
        }

        // Android drops Paint shadow layers for shapes on a hardware accelerated
        // canvas, so the view has to render into a software layer for the shadow
        // to appear at all. The layer also bounds the shadow: it is the size of
        // the view, so a shadow needs room from padding rather than from the
        // parent.
        val wanted = if (_shadowRadius > 0f) LAYER_TYPE_SOFTWARE else LAYER_TYPE_NONE
        if (layerType != wanted) setLayerType(wanted, null)
    }

    /**
     * One of the two vertical bands the bar is drawn in: the track that shows
     * through where nothing is lit, and the lit segments themselves.
     *
     * Mutable, and reused frame to frame, because [onDraw] allocates nothing.
     */
    private class Band {
        var top: Float = 0f
        var height: Float = 0f
        var radius: Float = 0f
    }

    /**
     * Supplies an outline matching the drawn track, so `android:elevation` casts
     * a correctly rounded shadow instead of a rectangular one.
     */
    private inner class TrackOutlineProvider : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val contentWidth = view.width - view.paddingLeft - view.paddingRight
            val contentHeight = view.height - view.paddingTop - view.paddingBottom
            if (contentWidth <= 0 || contentHeight <= 0) return

            val bandHeight = (contentHeight * _inactiveHeightRatio).toInt().coerceAtLeast(1)
            val top = view.paddingTop + (contentHeight - bandHeight) / 2
            val radius = SegmentGeometry.clampCornerRadius(
                _cornerRadius,
                contentWidth.toFloat(),
                bandHeight.toFloat(),
            )
            outline.setRoundRect(
                view.paddingLeft,
                top,
                view.paddingLeft + contentWidth,
                top + bandHeight,
                radius,
            )
        }
    }

    // endregion

    // region animation

    /**
     * Brings the animation bookkeeping in line with the current lit set,
     * starting transitions for any segment whose target changed.
     */
    private fun syncAnimationTargets() {
        ensureAnimationCapacity()
        val now = AnimationUtils.currentAnimationTimeMillis()
        // The initial state must not animate, or every screen visibly assembles
        // itself the first time it appears.
        val animate = isAnimationEnabled && isLaidOut

        for (index in 0 until _divisions) litScratch[index] = false
        for (i in enabled.indices) {
            val index = enabled[i]
            if (index < _divisions) litScratch[index] = true
        }

        for (index in 0 until _divisions) {
            val lit = litScratch[index]

            // A division that becomes fully lit retires its partial fill; the
            // fill is folded into the transition's starting point below, so a
            // GROW continues from where the partial had got to.
            val partial = if (lit) (partialFills[index] ?: 0f) else 0f
            if (lit && partial > 0f) partialFills.delete(index)

            if (lit == animTargetLit[index]) continue

            // Capture where the segment is *now*, before the target moves, so an
            // interrupted transition continues from its current position rather
            // than snapping back.
            animFrom[index] = when {
                animate -> maxOf(fractionOf(index, now), partial)
                lit -> 0f
                else -> 1f
            }
            animTargetLit[index] = lit
            animStart[index] = if (animate) now else 0L
        }
    }

    /** Ends every in-flight transition immediately at its target. */
    private fun snapAnimationToTargets() {
        for (index in animStart.indices) {
            animStart[index] = 0L
            animFrom[index] = if (animTargetLit[index]) 1f else 0f
        }
    }

    private val isAnimationEnabled: Boolean
        get() = activeTransitionStyle != SegmentAnimation.NONE && _animationDurationMs > 0L

    private fun ensureAnimationCapacity() {
        if (animTargetLit.size >= _divisions) return
        animTargetLit = animTargetLit.copyOf(_divisions)
        animFrom = animFrom.copyOf(_divisions)
        animStart = animStart.copyOf(_divisions)
        animDelay = animDelay.copyOf(_divisions)
        litScratch = litScratch.copyOf(_divisions)
    }

    /** How far segment [index] is towards being lit, from `0` to `1`. */
    private fun fractionOf(index: Int, now: Long): Float {
        val target = if (animTargetLit[index]) 1f else 0f
        if (!isAnimationEnabled) return target
        val start = animStart[index]
        if (start == 0L) return target

        val elapsed = now - start - animDelay[index]
        if (elapsed <= 0L) return animFrom[index]
        if (elapsed >= _animationDurationMs) return target

        val linear = elapsed.toFloat() / _animationDurationMs
        val eased = animationInterpolator.getInterpolation(linear)
        return animFrom[index] + (target - animFrom[index]) * eased
    }

    private fun hasRunningAnimation(now: Long): Boolean {
        if (!isAnimationEnabled) return false
        for (index in 0 until _divisions) {
            val start = animStart[index]
            if (start != 0L && now - start - animDelay[index] < _animationDurationMs) return true
        }
        return false
    }

    private val isRecurringRunning: Boolean
        get() = _recurringAnimation != RecurringAnimation.NONE &&
            isAttachedToWindow &&
            visibility == VISIBLE

    /**
     * Runs the entry animation the first time the bar is laid out.
     *
     * Deliberately driven from layout rather than construction: the animation
     * needs a known width, and a bar configured before it is measured would
     * otherwise animate against a zero-size canvas and appear to do nothing.
     */
    private fun playEntryAnimationIfNeeded() {
        if (hasPlayedEntryAnimation) return
        hasPlayedEntryAnimation = true

        if (_entryAnimation == EntryAnimation.NONE || _animationDurationMs <= 0L) return
        if (enabled.isEmpty()) return

        ensureAnimationCapacity()
        val now = AnimationUtils.currentAnimationTimeMillis()
        var staggerIndex = 0
        for (index in 0 until _divisions) {
            if (!animTargetLit[index]) continue
            animFrom[index] = 0f
            animStart[index] = now
            animDelay[index] = if (_entryAnimation == EntryAnimation.STAGGER) {
                _entryStaggerDelayMs * staggerIndex++
            } else {
                0L
            }
        }
        activeTransitionStyle = when (_entryAnimation) {
            EntryAnimation.GROW -> SegmentAnimation.GROW
            EntryAnimation.FADE, EntryAnimation.STAGGER -> SegmentAnimation.FADE
            EntryAnimation.NONE -> _segmentAnimation
        }
        invalidate()
    }

    // region recurring

    /**
     * Phase of the recurring loop, `0` to `1`.
     *
     * Derived from the clock rather than accumulated, so a dropped frame cannot
     * make the animation drift.
     */
    private fun recurringPhase(now: Long): Float {
        if (recurringStart == 0L) return 0f
        val elapsed = now - recurringStart
        if (elapsed <= 0L) return 0f
        return (elapsed % _recurringDurationMs).toFloat() / _recurringDurationMs
    }

    /**
     * The colour to paint segment [index] with, once the recurring animation has
     * had its say. Returns [baseColor] unchanged when nothing is running.
     */
    private fun recurringColorFor(index: Int, baseColor: Int, now: Long): Int {
        if (!isRecurringRunning) return baseColor
        val phase = recurringPhase(now)

        return when (_recurringAnimation) {
            RecurringAnimation.NONE -> baseColor

            RecurringAnimation.SHIMMER -> {
                // The highlight travels from just off one end to just off the
                // other, so the sweep enters and leaves cleanly instead of
                // popping into existence at the first segment.
                val head = -SHIMMER_BAND + phase * (1f + 2f * SHIMMER_BAND)
                val centre = (index + 0.5f) / _divisions
                val distance = abs(centre - head)
                val intensity = (1f - distance / SHIMMER_BAND).coerceIn(0f, 1f)
                blendTowards(
                    baseColor,
                    _shimmerColor,
                    intensity * Color.alpha(_shimmerColor) / 255f,
                )
            }

            RecurringAnimation.PULSE -> {
                // A full sine cycle per period, so it breathes rather than
                // stepping at the loop boundary.
                val wave = (1f + sin(phase * 2f * PI.toFloat())) / 2f
                val scale = PULSE_MIN_ALPHA + (1f - PULSE_MIN_ALPHA) * wave
                val alpha = (Color.alpha(baseColor) * scale).toInt().coerceIn(0, 255)
                (alpha shl 24) or (baseColor and 0x00FFFFFF)
            }
        }
    }

    /**
     * Blends [from] towards [to] by [amount], preserving [from]'s alpha.
     *
     * The target's own alpha is applied by the caller as part of [amount], so a
     * translucent shimmer colour tints rather than replacing.
     */
    @ColorInt
    private fun blendTowards(@ColorInt from: Int, @ColorInt to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return from
        fun mix(channel: Int) = when (channel) {
            0 -> Color.red(from) + ((Color.red(to) - Color.red(from)) * t).toInt()
            1 -> Color.green(from) + ((Color.green(to) - Color.green(from)) * t).toInt()
            else -> Color.blue(from) + ((Color.blue(to) - Color.blue(from)) * t).toInt()
        }
        return Color.argb(Color.alpha(from), mix(0), mix(1), mix(2))
    }

    // endregion

    /** Called whenever the lit set changes. */
    private fun onSegmentsChanged() {
        // A change after entry is a normal change, so hand control back to
        // segmentAnimation. Clear any stagger the entry animation left behind.
        activeTransitionStyle = _segmentAnimation
        animDelay.fill(0L)
        syncAnimationTargets()
        // The virtual accessibility tree mirrors divisions and their checked
        // state, so any change here invalidates it wholesale.
        divisionTouchHelper?.invalidateRoot()
        invalidate()
    }

    // endregion

    private class SavedState : BaseSavedState {

        val divisions: Int
        val enabledDivisions: IntArray
        val partialIndices: IntArray
        val partialValues: FloatArray

        constructor(
            superState: Parcelable,
            divisions: Int,
            enabledDivisions: IntArray,
            partialIndices: IntArray,
            partialValues: FloatArray,
        ) : super(superState) {
            this.divisions = divisions
            this.enabledDivisions = enabledDivisions
            this.partialIndices = partialIndices
            this.partialValues = partialValues
        }

        private constructor(source: Parcel) : super(source) {
            divisions = source.readInt()
            enabledDivisions = source.createIntArray() ?: IntArray(0)
            partialIndices = source.createIntArray() ?: IntArray(0)
            partialValues = source.createFloatArray() ?: FloatArray(0)
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(divisions)
            out.writeIntArray(enabledDivisions)
            out.writeIntArray(partialIndices)
            out.writeFloatArray(partialValues)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> =
                object : Parcelable.Creator<SavedState> {
                    override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
                    override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
                }
        }
    }

    public companion object {
        /** Returned by [divisionAt] when the position is outside the bar. */
        public const val NO_DIVISION: Int = -1

        /** Default [progressBarColor], `#5097E2`. */
        @ColorInt
        public const val DEFAULT_PROGRESS_COLOR: Int = 0xFF5097E2.toInt()

        /** Default [progressBarBackgroundColor], `#C1C1C1`. */
        @ColorInt
        public const val DEFAULT_BACKGROUND_COLOR: Int = 0xFFC1C1C1.toInt()

        /** Default [dividerColor], `#FFFFFF`. */
        @ColorInt
        public const val DEFAULT_DIVIDER_COLOR: Int = 0xFFFFFFFF.toInt()

        /** Default [divisions]. */
        public const val DEFAULT_DIVISIONS: Int = 1

        /** Default [dividerWidth], in pixels. */
        @Px
        public const val DEFAULT_DIVIDER_WIDTH_PX: Float = 1f

        /** Default [cornerRadius], in pixels. */
        @Px
        public const val DEFAULT_CORNER_RADIUS_PX: Float = 2f

        /** Default [shadowColor], 25% black. */
        @ColorInt
        public const val DEFAULT_SHADOW_COLOR: Int = 0x40000000

        /** Default [animationDurationMs]. */
        public const val DEFAULT_ANIMATION_DURATION_MS: Long = 200L

        /** Default [entryStaggerDelayMs]. */
        public const val DEFAULT_ENTRY_STAGGER_DELAY_MS: Long = 60L

        /** Default [recurringDurationMs]. */
        public const val DEFAULT_RECURRING_DURATION_MS: Long = 1600L

        /** Default [shimmerColor], 45% white. */
        @ColorInt
        public const val DEFAULT_SHIMMER_COLOR: Int = 0x73FFFFFF.toInt()

        /** Sentinel for [maxWidth] and [maxHeight] meaning "unbounded". */
        public const val NO_MAX_SIZE: Int = -1

        /**
         * How wide the shimmer highlight is, as a fraction of the bar. A little
         * under half so the sweep reads as a moving band, not a global flash.
         */
        private const val SHIMMER_BAND = 0.45f

        /** Peak alpha multiplier of a pulse, so it dims rather than vanishing. */
        private const val PULSE_MIN_ALPHA = 0.45f

        private const val ROUND_LEFT = 1
        private const val ROUND_RIGHT = 2

        /** Full alpha, for forcing a colour opaque. */
        private const val ALPHA_MASK = 0xFF000000.toInt()

        /** Intrinsic width used when the view is measured with `wrap_content`. */
        private const val DEFAULT_WIDTH_DP = 144f

        /** Intrinsic height used when the view is measured with `wrap_content`. */
        private const val DEFAULT_HEIGHT_DP = 8f
    }
}

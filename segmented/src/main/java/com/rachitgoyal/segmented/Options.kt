package com.rachitgoyal.segmented

/**
 * Shared configuration types.
 *
 * These are top-level rather than nested inside the View so that the Compose
 * artifact can use exactly the same types, and a caller can move between the two
 * implementations without translating enums.
 */

/** Which edges the corner radius is applied to. */
public enum class CornerMode {
    /** Round only the two ends of the whole bar. */
    BAR_ENDS,

    /** Round all four corners of every segment individually. */
    EACH_SEGMENT,

    /**
     * Round the outer ends of each contiguous run of lit segments, leaving edges
     * that touch another lit segment square. With a gap or no divider between
     * segments this makes each run read as one pill.
     */
    EACH_RUN,
}

/** How a segment transitions when it is lit or cleared after the bar is on screen. */
public enum class SegmentAnimation {
    /** Change instantly. */
    NONE,

    /** Fade the segment in and out. */
    FADE,

    /** Grow the segment from its leading edge, mirrored under RTL. */
    GROW,
}

/**
 * How the bar's initial state arrives when it is first shown.
 *
 * Distinct from [SegmentAnimation], which covers changes made once the bar is
 * already on screen. An entry animation runs once, when the view is first laid
 * out.
 */
public enum class EntryAnimation {
    /** Appear fully formed. */
    NONE,

    /** Fade every lit segment in together. */
    FADE,

    /** Grow every lit segment from its leading edge together. */
    GROW,

    /**
     * Reveal lit segments one after another in reading order, which reads as the
     * bar filling itself in.
     */
    STAGGER,
}

/** A continuous animation that plays for as long as the bar is visible. */
public enum class RecurringAnimation {
    /** Nothing. The default; a bar that never settles is tiring to look at. */
    NONE,

    /** A highlight sweeps repeatedly across the lit segments. */
    SHIMMER,

    /** The lit segments breathe in and out together. */
    PULSE,
}

/**
 * Which parts of the bar cast a drop shadow.
 *
 * Whichever is chosen, each segment casts at most one shadow and no shadow is
 * ever drawn inside the bar, so the choice changes which parts of the outline are
 * shadowed rather than how dark any of it is.
 */
public enum class ShadowTarget {
    /**
     * Only the segments that are on, which reads as the lit run floating over an
     * unshadowed track.
     */
    ON_SEGMENTS,

    /** Only the segments that are off. */
    OFF_SEGMENTS,

    /** Every segment, on or off, so the bar as a whole casts one shadow. The default. */
    ALL,
}

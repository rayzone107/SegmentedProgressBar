package com.rachitgoyal.segmented

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** A single recorded draw call, flattened to a bounding box and a colour. */
internal data class DrawOp(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val color: Int,
    val rounded: Boolean,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * A [Canvas] that records the primitives drawn into it and still rasterises
 * them, so the view under test is exercised exactly as it would be on a device.
 *
 * Recording the real draw calls rather than inspecting view state is what lets
 * the drawing tests assert on geometry and colour, the things a user actually
 * sees, instead of on implementation details.
 */
internal class RecordingCanvas(val bitmap: Bitmap) : Canvas(bitmap) {

    val ops = mutableListOf<DrawOp>()
    val translations = mutableListOf<Pair<Float, Float>>()

    override fun translate(dx: Float, dy: Float) {
        translations += dx to dy
        super.translate(dx, dy)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        ops += DrawOp(left, top, right, bottom, paint.color, rounded = false)
        super.drawRect(left, top, right, bottom, paint)
    }

    override fun drawPath(path: Path, paint: Paint) {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        ops += DrawOp(bounds.left, bounds.top, bounds.right, bounds.bottom, paint.color, true)
        super.drawPath(path, paint)
    }
}

/** Ops of exactly this colour, alpha included. */
internal fun List<DrawOp>.ofColor(color: Int): List<DrawOp> = filter { it.color == color }

/**
 * Ops of this colour ignoring alpha.
 *
 * Needed wherever a fade is in flight: the paint's alpha is scaled during the
 * transition, so an exact colour match would silently find nothing.
 */
internal fun List<DrawOp>.ofRgb(color: Int): List<DrawOp> =
    filter { it.color and 0x00FFFFFF == color and 0x00FFFFFF }

/** Renders [view] at the given size through a [RecordingCanvas]. */
internal fun renderToRecordingCanvas(view: View, width: Int, height: Int): RecordingCanvas {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
    )
    view.layout(0, 0, width, height)
    val canvas = RecordingCanvas(
        Bitmap.createBitmap(
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        ),
    )
    view.draw(canvas)
    return canvas
}

/**
 * Bounding box of a set of ops, as a single synthetic [DrawOp].
 *
 * The track is drawn cell by cell rather than as one span (so that gaps are
 * genuinely empty), so assertions about "the track" as a whole work on the union
 * of its cells.
 */
internal fun List<DrawOp>.union(): DrawOp {
    require(isNotEmpty()) { "no ops to union" }
    return DrawOp(
        left = minOf { it.left },
        top = minOf { it.top },
        right = maxOf { it.right },
        bottom = maxOf { it.bottom },
        color = first().color,
        rounded = any { it.rounded },
    )
}

/** Alpha channel of a recorded op's colour, as a 0..1 fraction. */
internal val DrawOp.alphaFraction: Float get() = Color.alpha(color) / 255f
